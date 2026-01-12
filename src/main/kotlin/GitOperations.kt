package com.khan366kos.git

import java.io.File
import java.io.IOException

class GitOperations(private val repositoryPath: String) {

    fun validateRepository(): String? {
        val repoDir = File(repositoryPath)
        if (!repoDir.exists()) {
            return "Repository path does not exist: $repositoryPath"
        }
        if (!repoDir.isDirectory) {
            return "Path is not a directory: $repositoryPath"
        }

        return try {
            val (_, exitCode) = executeGitCommand("rev-parse", "--git-dir")
            if (exitCode != 0) {
                "Not a git repository: $repositoryPath"
            } else {
                null
            }
        } catch (e: IOException) {
            "Git command not found. Please ensure git is installed."
        } catch (e: Exception) {
            "Error validating repository: ${e.message}"
        }
    }

    fun getCurrentBranch(): String {
        val (output, exitCode) = executeGitCommand("rev-parse", "--abbrev-ref", "HEAD")
        if (exitCode != 0) {
            throw GitException("Failed to get current branch")
        }
        return output.trim()
    }

    fun getFileChanges(includeUntracked: Boolean): Triple<List<FileChange>, List<FileChange>, List<String>> {
        val (output, exitCode) = executeGitCommand("status", "--porcelain=v1")
        if (exitCode != 0) {
            throw GitException("Failed to get git status")
        }

        val staged = mutableListOf<FileChange>()
        val unstaged = mutableListOf<FileChange>()
        val untracked = mutableListOf<String>()

        output.lines().filter { it.isNotBlank() }.forEach { line ->
            if (line.length < 3) return@forEach

            val statusCode = line.substring(0, 2)
            val filePath = line.substring(3)

            when {
                statusCode == "??" -> {
                    if (includeUntracked) {
                        untracked.add(filePath)
                    }
                }
                statusCode[0] != ' ' -> {
                    staged.add(FileChange(
                        path = filePath,
                        status = parseStatusCode(statusCode[0])
                    ))
                }
                statusCode[1] != ' ' -> {
                    unstaged.add(FileChange(
                        path = filePath,
                        status = parseStatusCode(statusCode[1])
                    ))
                }
            }
        }

        return Triple(staged, unstaged, untracked)
    }

    fun getDiff(contextLines: Int): String {
        val (output, exitCode) = executeGitCommand("diff", "HEAD", "-U$contextLines")
        return if (exitCode == 0 && output.isNotBlank()) {
            output
        } else {
            ""
        }
    }

    private fun parseStatusCode(code: Char): String {
        return when (code) {
            'M' -> "modified"
            'A' -> "added"
            'D' -> "deleted"
            'R' -> "renamed"
            'C' -> "copied"
            'U' -> "updated"
            '?' -> "untracked"
            else -> "unknown"
        }
    }

    private fun executeGitCommand(vararg command: String): Pair<String, Int> {
        val processBuilder = ProcessBuilder("git", *command)
            .directory(File(repositoryPath))
            .redirectErrorStream(false)

        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0 && stderr.isNotBlank()) {
            throw GitException("Git command failed: $stderr")
        }

        return stdout to exitCode
    }
}

class GitException(message: String) : Exception(message)
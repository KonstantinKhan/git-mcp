package com.khan366kos.git

import java.io.File
import java.io.IOException

/**
 * Handles Pull Request related git operations using ProcessBuilder.
 */
class PullRequestOperations(private val repositoryPath: String) {

    /**
     * Detects the target branch by checking for 'main' or 'master'.
     * @return "main" if it exists, otherwise "master"
     * @throws GitException if neither branch exists
     */
    fun detectTargetBranch(): String {
        val (output, exitCode) = executeGitCommand("branch", "--list", "main", "master")

        if (exitCode != 0) {
            throw GitException("Failed to list branches")
        }

        val branches = output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return when {
            branches.any { it.contains("main") } -> "main"
            branches.any { it.contains("master") } -> "master"
            else -> throw GitException("Neither 'main' nor 'master' branch exists. Please specify target_branch parameter.")
        }
    }

    /**
     * Gets the current branch name.
     * @return Current branch name
     * @throws GitException if unable to get current branch
     */
    fun getCurrentBranch(): String {
        val (output, exitCode) = executeGitCommand("rev-parse", "--abbrev-ref", "HEAD")

        if (exitCode != 0) {
            throw GitException("Failed to get current branch")
        }

        val branch = output.trim()

        if (branch == "HEAD") {
            throw GitException("Cannot create PR from detached HEAD state")
        }

        return branch
    }

    /**
     * Validates that the current branch is not the target branch.
     * @throws GitException if current branch equals target branch
     */
    fun validateNotOnTargetBranch(currentBranch: String, targetBranch: String) {
        if (currentBranch == targetBranch) {
            throw GitException("PR не открыт - вы находитесь на целевой ветке")
        }
    }

    /**
     * Gets the commit log between target branch and current branch.
     * @param targetBranch The target branch to compare against
     * @return List of CommitInfo objects
     * @throws GitException if unable to get commit log
     */
    fun getCommitLog(targetBranch: String): List<CommitInfo> {
        val format = "%H%n%an%n%ae%n%s%n%b%n---COMMIT---"
        val (output, exitCode) = executeGitCommand("log", "$targetBranch..HEAD", "--format=$format")

        if (exitCode != 0) {
            throw GitException("Failed to get commit log")
        }

        if (output.isBlank()) {
            return emptyList()
        }

        return parseCommitLog(output)
    }

    /**
     * Parses the commit log output into CommitInfo objects.
     */
    private fun parseCommitLog(output: String): List<CommitInfo> {
        val commits = mutableListOf<CommitInfo>()
        val commitBlocks = output.split("---COMMIT---")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (block in commitBlocks) {
            val lines = block.lines()
            if (lines.size < 4) continue

            val hash = lines[0].trim()
            val author = lines[1].trim()
            val email = lines[2].trim()
            val subject = lines[3].trim()
            val body = lines.drop(4).joinToString("\n").trim()

            commits.add(CommitInfo(
                hash = hash,
                author = author,
                email = email,
                subject = subject,
                body = body
            ))
        }

        return commits
    }

    /**
     * Gets file statistics (additions/deletions) for changed files.
     * @param targetBranch The target branch to compare against
     * @return List of FileStats objects
     * @throws GitException if unable to get file statistics
     */
    fun getFileStatistics(targetBranch: String): List<FileStats> {
        val (output, exitCode) = executeGitCommand("diff", "--numstat", "$targetBranch...HEAD")

        if (exitCode != 0) {
            throw GitException("Failed to get file statistics")
        }

        if (output.isBlank()) {
            return emptyList()
        }

        return parseFileStatistics(output)
    }

    /**
     * Parses file statistics from git diff --numstat output.
     */
    private fun parseFileStatistics(output: String): List<FileStats> {
        val stats = mutableListOf<FileStats>()

        for (line in output.lines()) {
            if (line.isBlank()) continue

            val parts = line.split("\t", limit = 3)
            if (parts.size < 3) continue

            val additions = if (parts[0] == "-") 0 else parts[0].toIntOrNull() ?: 0
            val deletions = if (parts[1] == "-") 0 else parts[1].toIntOrNull() ?: 0
            val path = parts[2]

            stats.add(FileStats(
                path = path,
                additions = additions,
                deletions = deletions
            ))
        }

        return stats
    }

    /**
     * Gets the full unified diff between target branch and current branch.
     * @param targetBranch The target branch to compare against
     * @param contextLines Number of context lines in diff output
     * @return Full unified diff as string
     * @throws GitException if unable to get diff
     */
    fun getDiff(targetBranch: String, contextLines: Int): String {
        val (output, exitCode) = executeGitCommand("diff", "$targetBranch...HEAD", "-U$contextLines")

        return if (exitCode == 0 && output.isNotBlank()) {
            output
        } else {
            ""
        }
    }

    /**
     * Builds a formatted PR description from commit messages.
     * @param commits List of commits in the PR
     * @return Formatted markdown description
     */
    fun buildPRDescription(commits: List<CommitInfo>): String {
        if (commits.isEmpty()) {
            return "No commits in this PR"
        }

        val sb = StringBuilder()

        // Summary section
        sb.appendLine("## Коммиты в этом PR")
        sb.appendLine()
        for (commit in commits.reversed()) {  // Oldest to newest
            val shortHash = commit.hash.take(7)
            sb.appendLine("- [$shortHash] ${commit.subject} (${commit.author})")
        }

        // Detailed section
        sb.appendLine()
        sb.appendLine("## Детали коммитов")
        sb.appendLine()
        for (commit in commits.reversed()) {
            val shortHash = commit.hash.take(7)
            sb.appendLine("### [$shortHash] ${commit.subject}")
            if (commit.body.isNotBlank()) {
                sb.appendLine(commit.body)
            }
            sb.appendLine()
        }

        return sb.toString().trim()
    }

    /**
     * Extracts unique author names from commits.
     * @param commits List of commits
     * @return Sorted list of unique author names
     */
    fun extractUniqueAuthors(commits: List<CommitInfo>): List<String> {
        return commits
            .map { it.author }
            .distinct()
            .sorted()
    }

    /**
     * Executes a git command and returns the output and exit code.
     * @param command Git command and arguments
     * @return Pair of (output, exitCode)
     * @throws GitException if command fails with error output
     * @throws IOException if git is not found
     */
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
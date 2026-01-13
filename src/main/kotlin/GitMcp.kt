package com.khan366kos.git

import io.ktor.server.application.Application
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

fun configureGitMcpServer(): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "GitMcpServer",
            version = "0.0.1",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        )
    )

    server.addTool(
        name = "git_status",
        description = """
            Get the complete status of a git repository including current branch,
            staged files, unstaged changes, untracked files, and full unified diff.
            Be sure to include the name of the current branch in the response.
            """
            .trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("repository_path", buildJsonObject {
                    put("type", "string")
                    put("description", "Absolute path to the git repository. Defaults to current working directory if not provided.")
                })
                put("include_untracked", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Include untracked files in the output. Defaults to true.")
                    put("default", true)
                })
                put("diff_context_lines", buildJsonObject {
                    put("type", "integer")
                    put("description", "Number of context lines to show in diff output. Defaults to 3.")
                    put("default", 3)
                })
            }
        )
    ) { request ->
        val repoPath = request.arguments?.get("repository_path")?.jsonPrimitive?.content
            ?: System.getProperty("user.dir")
        val includeUntracked = request.arguments?.get("include_untracked")?.jsonPrimitive?.boolean
            ?: true
        val contextLines = request.arguments?.get("diff_context_lines")?.jsonPrimitive?.int
            ?: 3

        val gitOps = GitOperations(repoPath)

        gitOps.validateRepository()?.let { error ->
            return@addTool CallToolResult(
                content = listOf(
                    TextContent(text = buildJsonObject {
                        put("error", error)
                    }.toString())
                )
            )
        }

        try {
            val branch = gitOps.getCurrentBranch()
            val (staged, unstaged, untracked) = gitOps.getFileChanges(includeUntracked)
            val diff = gitOps.getDiff(contextLines)

            val result = GitStatusResult(
                branch = branch,
                staged = staged,
                unstaged = unstaged,
                untracked = if (includeUntracked) untracked else emptyList(),
                diff = diff
            )

            CallToolResult(
                content = listOf(
                    TextContent(text = Json.encodeToString(result))
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(
                    TextContent(text = buildJsonObject {
                        put("error", e.message ?: "Unknown error occurred")
                    }.toString())
                )
            )
        }
    }

    server.addTool(
        name = "pr_info",
        description = """
            Get pull request information by comparing the current branch with the target branch.
            Returns PR title, description (commit messages), author info, file changes, and full diff.
            Automatically detects main/master as target branch or accepts custom target.
            If currently on the target branch, returns an error indicating no PR is open.
            """
            .trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("repository_path", buildJsonObject {
                    put("type", "string")
                    put("description", "Absolute path to the git repository. Defaults to current working directory if not provided.")
                })
                put("target_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "Target branch to compare against. Auto-detects main/master if not provided.")
                })
                put("diff_context_lines", buildJsonObject {
                    put("type", "integer")
                    put("description", "Number of context lines in diff output. Defaults to 3.")
                    put("default", 3)
                })
            }
        )
    ) { request ->
        val repoPath = request.arguments?.get("repository_path")?.jsonPrimitive?.content
            ?: System.getProperty("user.dir")
        val targetBranch = request.arguments?.get("target_branch")?.jsonPrimitive?.content
        val contextLines = request.arguments?.get("diff_context_lines")?.jsonPrimitive?.int
            ?: 3

        val prOps = PullRequestOperations(repoPath)

        // Validate repository first
        val gitOps = GitOperations(repoPath)
        gitOps.validateRepository()?.let { error ->
            return@addTool CallToolResult(
                content = listOf(
                    TextContent(text = buildJsonObject {
                        put("error", error)
                    }.toString())
                )
            )
        }

        try {
            // Get current branch
            val currentBranch = prOps.getCurrentBranch()

            // Detect or use provided target branch
            val target = targetBranch ?: prOps.detectTargetBranch()

            // Validate not on target branch
            prOps.validateNotOnTargetBranch(currentBranch, target)

            // Get commit information
            val commits = prOps.getCommitLog(target)

            // Check if there are any commits
            if (commits.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(text = buildJsonObject {
                            put("error", "Нет коммитов для создания PR - ветка актуальна с $target")
                        }.toString())
                    )
                )
            }

            // Get file statistics and diff
            val fileStats = prOps.getFileStatistics(target)
            val diff = prOps.getDiff(target, contextLines)

            // Build PR info
            val lastCommit = commits.first() // First in log is most recent
            val prInfo = PullRequestInfo(
                title = currentBranch,
                description = prOps.buildPRDescription(commits),
                sourceBranch = currentBranch,
                targetBranch = target,
                author = lastCommit.author,
                allAuthors = prOps.extractUniqueAuthors(commits),
                commits = commits,
                changedFiles = fileStats,
                diff = diff
            )

            CallToolResult(
                content = listOf(
                    TextContent(text = Json.encodeToString(prInfo))
                )
            )
        } catch (e: GitException) {
            CallToolResult(
                content = listOf(
                    TextContent(text = buildJsonObject {
                        put("error", e.message ?: "Git operation failed")
                    }.toString())
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(
                    TextContent(text = buildJsonObject {
                        put("error", "Unexpected error: ${e.message}")
                    }.toString())
                )
            )
        }
    }

    return server
}

fun Application.configureGitMcp() {
    mcp {
        return@mcp configureGitMcpServer()
    }
}
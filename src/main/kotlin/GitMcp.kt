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

    return server
}

fun Application.configureGitMcp() {
    mcp {
        return@mcp configureGitMcpServer()
    }
}
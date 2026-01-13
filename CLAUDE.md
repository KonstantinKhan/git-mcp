# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Location
/Users/khan/Projects/git-mcp

## Project Overview

This is a Kotlin-based MCP (Model Context Protocol) server implementation built with Ktor. It provides an HTTP server that integrates with the MCP Kotlin SDK to expose Git-related tools through the MCP protocol.

**Implemented Tools:**
- `git_status`: Repository status including branch, staged/unstaged changes, untracked files, and unified diffs
- `pr_info`: Pull Request analysis comparing current branch with target branch (main/master), providing full diff, commit messages, author info, and file statistics

## Commands

### Build and Run
```bash
./gradlew build                    # Build the project
./gradlew run                      # Run the server (starts on port 8085)
./gradlew test                     # Run all tests
./gradlew clean                    # Clean build artifacts
```

### Testing
```bash
./gradlew test --tests ClassName   # Run a specific test class
./gradlew test --tests ClassName.testName  # Run a single test method
```

## Architecture

### Core Components

**Application Entry Point** (Application.kt)
- Main entry point uses Ktor's `EngineMain` approach
- Application module configures four main aspects: Serialization, HTTP, Routing, and MCP integration
- Configuration is loaded from `application.conf`

**MCP Server Integration** (GitMcp.kt)
- `configureGitMcpServer()`: Creates and configures the MCP Server instance with all tools
- Server uses the Kotlin MCP SDK (version 0.8.1)
- Tools are registered via `server.addTool()` with name, description, and input schema
- Uses `ServerCapabilities.Tools(listChanged = true)` to enable tool change notifications
- `configureGitMcp()`: Ktor application extension that wires up the MCP server using the `mcp {}` DSL

**Implemented Tools:**
1. `git_status` - Repository status tool
   - `repository_path` (optional): Absolute path to git repository
   - `include_untracked` (optional, default true): Whether to include untracked files
   - `diff_context_lines` (optional, default 3): Number of context lines in diff output

2. `pr_info` - Pull Request analysis tool
   - `repository_path` (optional): Absolute path to git repository
   - `target_branch` (optional): Target branch to compare against, auto-detects main/master if not provided
   - `diff_context_lines` (optional, default 3): Number of context lines in diff output
   - Returns error if currently on target branch or no commits exist
   - Returns comprehensive PR data: title, description, commits, file statistics, full diff

**Git Operations Layers**

Two operation classes handle different git functionality:

1. **GitOperations.kt** - Repository status operations
   - `validateRepository()`: Checks if path is valid git repository
   - `getCurrentBranch()`: Returns current branch name
   - `getFileChanges()`: Parses `git status --porcelain=v1` output into staged, unstaged, and untracked lists
   - `getDiff()`: Executes `git diff HEAD` with configurable context lines
   - `executeGitCommand()`: Private helper that runs git commands and captures output/errors

2. **PullRequestOperations.kt** - Pull request analysis operations
   - `detectTargetBranch()`: Auto-detects main or master branch
   - `getCurrentBranch()`: Gets current branch with detached HEAD detection
   - `validateNotOnTargetBranch()`: Ensures current branch differs from target
   - `getCommitLog()`: Parses commit log between branches with custom delimiter
   - `getFileStatistics()`: Parses `git diff --numstat` output, handles binary files
   - `getDiff()`: Gets unified diff using three-dot merge base comparison
   - `buildPRDescription()`: Formats commit messages into markdown description
   - `extractUniqueAuthors()`: Collects unique commit authors

Both classes throw `GitException` for git command failures and follow the same ProcessBuilder execution pattern.

**Data Models**

1. **GitStatusResult.kt** - Repository status models
   - `GitStatusResult`: Serializable response with branch, staged, unstaged, untracked, and diff
   - `FileChange`: File change with path and status (modified, added, deleted, renamed, copied, updated)

2. **PullRequestInfo.kt** - Pull request models
   - `PullRequestInfo`: Complete PR data including title, description, branches, authors, commits, file stats, and diff
   - `CommitInfo`: Individual commit with hash, author, email, subject, and body
   - `FileStats`: File change statistics with path, additions, and deletions

**HTTP Configuration** (HTTP.kt)
- Configures CORS to allow all hosts and common HTTP methods (OPTIONS, PUT, DELETE, PATCH)
- Allows Authorization header

**Serialization** (Serialization.kt)
- Configures Ktor ContentNegotiation with Kotlinx JSON serialization

**Routing** (Routing.kt)
- Currently empty placeholder for future HTTP endpoints

### Key Dependencies

- **Ktor 3.3.3**: Web framework providing HTTP server and routing
- **MCP Kotlin SDK 0.8.1**: Model Context Protocol implementation
- **Kotlinx Serialization**: JSON serialization support
- **Logback 1.5.24**: Logging framework
- **Kotlin 2.3.0**: Language version

### Configuration

Server configuration is in `src/main/resources/application.conf`:
- Default port: 8085
- Can be overridden with PORT environment variable
- Module path: `com.khan366kos.git.ApplicationKt.module`

## Development Notes

### Adding New MCP Tools

Follow the established pattern for adding git-related tools:

1. **Create Data Models** (e.g., `NewFeatureInfo.kt`)
   - Define `@Serializable` data classes for request/response
   - Use clear, descriptive field names with KDoc comments

2. **Create Operations Class** (e.g., `NewFeatureOperations.kt`)
   - Constructor accepts `repositoryPath: String`
   - Implement private `executeGitCommand(vararg command: String): Pair<String, Int>` following the ProcessBuilder pattern
   - Parse git command output into structured data
   - Throw `GitException` for errors with descriptive messages
   - Handle edge cases (detached HEAD, missing branches, empty output, binary files, etc.)

3. **Register Tool in GitMcp.kt**
   - Call `server.addTool()` after existing tools (before `return server`)
   - Define input schema with `buildJsonObject` specifying all parameters
   - In the handler lambda:
     - Extract arguments using `request.arguments?.get("param")?.jsonPrimitive`
     - Validate repository first using `GitOperations.validateRepository()`
     - Create operations instance and execute methods
     - Handle all exceptions with appropriate error JSON responses
     - Return `CallToolResult` with `TextContent` containing `Json.encodeToString()` of result

4. **Write Tests**
   - Create test file in `src/test/kotlin/`
   - Test all operation methods individually
   - Test error cases (invalid repo, edge conditions)
   - Verify parsing logic with various git outputs

### Ktor Application Structure

The application follows Ktor's modular configuration pattern:
- Each feature (HTTP, Serialization, Routing, MCP) is configured via an extension function on `Application`
- Extension functions are prefixed with `configure*` by convention
- All configuration functions are called from `Application.module()` in a specific order

### Git Command Execution

All git operations execute shell commands via `ProcessBuilder`:
- Commands run in the context of the specified repository directory
- Both stdout and stderr are captured separately
- Non-zero exit codes with stderr output throw `GitException`
- The system must have git installed and available in PATH
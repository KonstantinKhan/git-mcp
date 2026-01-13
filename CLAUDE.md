# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Location
/Users/khan/Projects/git-mcp

## Project Overview

This is a Kotlin-based MCP (Model Context Protocol) server implementation built with Ktor. 
It provides an HTTP server that integrates with the MCP Kotlin SDK to expose Git-related tools through the MCP protocol. 
The server currently implements a `git_status` tool that provides comprehensive repository status information 
including branch name, staged/unstaged changes, untracked files, and unified diffs.

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
- `configureGitMcpServer()`: Creates and configures the MCP Server instance with git_status tool
- Server uses the Kotlin MCP SDK (version 0.8.1)
- Implements `git_status` tool that accepts:
  - `repository_path` (optional): Absolute path to git repository, defaults to current working directory
  - `include_untracked` (optional, default true): Whether to include untracked files
  - `diff_context_lines` (optional, default 3): Number of context lines in diff output
- Uses `ServerCapabilities.Tools(listChanged = true)` to enable tool change notifications
- `configureGitMcp()`: Ktor application extension that wires up the MCP server using the `mcp {}` DSL

**Git Operations Layer** (GitOperations.kt)
- Encapsulates all git command execution via ProcessBuilder
- Key methods:
  - `validateRepository()`: Checks if path is valid git repository
  - `getCurrentBranch()`: Returns current branch name
  - `getFileChanges()`: Parses `git status --porcelain=v1` output into staged, unstaged, and untracked lists
  - `getDiff()`: Executes `git diff HEAD` with configurable context lines
  - `executeGitCommand()`: Private helper that runs git commands and captures output/errors
- Throws `GitException` for git command failures

**Data Models** (GitStatusResult.kt)
- `GitStatusResult`: Serializable response containing branch, staged, unstaged, untracked, and diff
- `FileChange`: Represents a file change with path and status (modified, added, deleted, renamed, copied, updated)

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

To add a new MCP tool, edit `GitMcp.kt`:
1. Call `server.addTool()` with name, description, and input schema
2. Define the tool's JSON schema using `buildJsonObject` with properties, types, and descriptions
3. Implement the tool handler lambda that receives `request` and returns `CallToolResult`
4. Access tool arguments via `request.arguments?.get("param_name")?.jsonPrimitive`
5. Return `CallToolResult` with list of `TextContent` containing JSON-encoded result

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
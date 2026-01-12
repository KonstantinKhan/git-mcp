# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin-based MCP (Model Context Protocol) server implementation built with Ktor. It provides an HTTP server that integrates with the MCP Kotlin SDK to expose Git-related tools through the MCP protocol.

## Commands

### Build and Run
```bash
./gradlew build                    # Build the project
./gradlew run                      # Run the server (starts on port 8080)
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
- Application module configures three main aspects: Serialization, HTTP, and Routing
- Configuration is loaded from `application.conf`

**MCP Server Integration** (GitMcp.kt)
- `configureGitMcpServer()`: Creates and configures the MCP Server instance
- Server uses the Kotlin MCP SDK (version 0.8.1)
- Currently defines a "create document" tool (placeholder implementation)
- Uses `ServerCapabilities.Tools(listChanged = true)` to enable tool change notifications
- `configureGitMcp()`: Ktor application extension that wires up the MCP server using the `mcp {}` DSL

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

### Configuration

Server configuration is in `src/main/resources/application.conf`:
- Default port: 8080
- Can be overridden with PORT environment variable
- Module path: `com.khan366kos.git.ApplicationKt.module`

## Development Notes

### Adding New MCP Tools

To add a new MCP tool, edit `GitMcp.kt`:
1. Call `server.addTool()` with name, description, and input schema
2. Define the tool's JSON schema using `buildJsonObject`
3. Implement the tool handler that returns `CallToolResult`

### Ktor Application Structure

The application follows Ktor's modular configuration pattern:
- Each feature (HTTP, Serialization, Routing, MCP) is configured via an extension function on `Application`
- Extension functions are prefixed with `configure*` by convention
- All configuration functions are called from `Application.module()`
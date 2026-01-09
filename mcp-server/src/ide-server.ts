#!/usr/bin/env node

/**
 * @fileoverview IDE-Specific JetBrains MCP Server
 *
 * This module provides a Model Context Protocol (MCP) server that exposes
 * IDE-specific refactoring tools. Unlike the common server which auto-routes
 * requests, this server connects directly to a specific IDE instance.
 *
 * ## Architecture
 *
 * Each IDE-specific server is launched with command-line arguments specifying:
 * - The port number of the target IDE
 * - The IDE name (used for tool naming and descriptions)
 *
 * Tools are prefixed with the IDE name (e.g., `idea_move`, `webstorm_extract_method`)
 * to avoid conflicts when multiple IDE servers are running.
 *
 * ## Available Tools
 *
 * - **{ide}_move**: Move class/symbol to different package/module
 * - **{ide}_extract_method**: Extract code block into a new method/function
 *
 * ## Usage
 *
 * ```bash
 * node ide-server.js <port> <ide-name>
 * # Examples:
 * node ide-server.js 8765 idea
 * node ide-server.js 8766 webstorm
 * node ide-server.js 8767 pycharm
 * ```
 *
 * ## Language-Specific Behavior
 *
 * The move and extract_method tools behave differently depending on the IDE
 * and language. For example:
 * - Java: Move moves classes between packages
 * - TypeScript: Move relocates modules/symbols to different files
 * - Python: Move transfers functions/classes between modules
 * - Go: Move shifts types/functions between packages
 *
 * @module ide-server
 * @version 0.3.0
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

/**
 * The port number of the target IDE.
 * Parsed from command line argument, defaults to 8765 (IntelliJ IDEA).
 *
 * @constant
 * @type {number}
 */
const PORT = parseInt(process.argv[2], 10) || 8765;

/**
 * The name of the target IDE.
 * Used for tool prefixes and descriptions.
 * Parsed from command line argument, defaults to "idea".
 *
 * @constant
 * @type {string}
 */
const IDE_NAME = process.argv[3] || "idea";

/**
 * The host address for IDE connections.
 * Uses localhost for security.
 *
 * @constant
 * @type {string}
 */
const HOST = "127.0.0.1";

/**
 * The base URL for all IDE requests.
 *
 * @constant
 * @type {string}
 */
const BASE_URL = `http://${HOST}:${PORT}`;

/**
 * Prefix for tool names, derived from IDE name.
 * Hyphens are converted to underscores for valid tool names.
 *
 * @constant
 * @type {string}
 *
 * @example
 * // IDE_NAME = "android-studio" -> TOOL_PREFIX = "android_studio"
 * // IDE_NAME = "idea" -> TOOL_PREFIX = "idea"
 */
const TOOL_PREFIX = IDE_NAME.toLowerCase().replace(/-/g, "_");

/**
 * Result of a refactoring operation.
 *
 * @typedef {Object} RefactoringResult
 * @property {boolean} success - Whether the refactoring completed successfully
 * @property {string} message - Human-readable description or error message
 *
 * @example
 * // Successful move
 * const result: RefactoringResult = {
 *   success: true,
 *   message: "Moved 'MyClass' to package 'com.example.newpkg'"
 * };
 *
 * // Failed extract method
 * const errorResult: RefactoringResult = {
 *   success: false,
 *   message: "Cannot extract: selection contains return statement"
 * };
 */
type RefactoringResult = {
  success: boolean;
  message: string;
};

/**
 * Standard response format for MCP tool calls.
 *
 * @typedef {Object} ToolResponse
 * @property {Array<{type: "text", text: string}>} content - The response content
 * @property {boolean} [isError] - Whether this response represents an error
 */
type ToolResponse = {
  content: Array<{ type: "text"; text: string }>;
  isError?: boolean;
};

/**
 * Result of a status check to the IDE.
 *
 * @typedef {Object} StatusResult
 * @property {boolean} ok - Whether the IDE is running
 * @property {string} ideType - The IDE type (e.g., "IntelliJ IDEA")
 * @property {Record<string, string[]>} [implementedTools] - Map of tool names to supported languages
 */
type StatusResult = {
  ok: boolean;
  ideType: string;
  implementedTools?: Record<string, string[]>;
};

/**
 * Cache for implemented tools, populated on first status check.
 * Maps tool names (e.g., "move", "extract_method") to supported languages.
 */
let implementedToolsCache: Record<string, string[]> | null = null;

/**
 * Fetches the list of implemented tools from the IDE.
 *
 * Queries the /status endpoint and caches the result for subsequent calls.
 * If the IDE is not available, returns an empty object (no tools).
 *
 * @returns {Promise<Record<string, string[]>>} Map of tool names to supported languages
 */
async function getImplementedTools(): Promise<Record<string, string[]>> {
  if (implementedToolsCache !== null) {
    return implementedToolsCache;
  }

  try {
    const status = await callIde<StatusResult>("/status");
    implementedToolsCache = status.implementedTools || {};
    return implementedToolsCache;
  } catch {
    // IDE not available, return empty (no tools will be registered)
    implementedToolsCache = {};
    return implementedToolsCache;
  }
}

/**
 * Checks if a tool should be registered based on implementation status.
 *
 * A tool is registered if it has at least one implemented language.
 *
 * @param {string} toolName - The tool name (e.g., "move", "extract_method")
 * @param {Record<string, string[]>} implementedTools - Map of implemented tools
 * @returns {boolean} True if the tool should be registered
 */
function isToolImplemented(toolName: string, implementedTools: Record<string, string[]>): boolean {
  const languages = implementedTools[toolName];
  return Array.isArray(languages) && languages.length > 0;
}

/**
 * Builds a ToolResponse from a RefactoringResult.
 *
 * Formats the refactoring result into a standard MCP tool response,
 * with appropriate success/error messaging based on the operation type.
 *
 * @function buildRefactoringResponse
 * @param {RefactoringResult} result - The result of the refactoring operation
 * @param {string} operationName - The name of the operation (e.g., "Move", "Extract method")
 * @returns {ToolResponse} Formatted tool response
 *
 * @example
 * const result = await callIde<RefactoringResult>("/move", args);
 * return buildRefactoringResponse(result, "Move");
 */
function buildRefactoringResponse(result: RefactoringResult, operationName: string): ToolResponse {
  return {
    content: [{
      type: "text",
      text: result.success ? result.message : `${operationName} failed: ${result.message}`,
    }],
    isError: !result.success,
  };
}

/**
 * Makes an HTTP request to the IDE with a timeout.
 *
 * Supports both GET and POST requests. POST requests automatically
 * serialize the body as JSON. Includes a 30-second timeout for
 * long-running refactoring operations.
 *
 * @async
 * @function callIde
 * @template T - The expected response type
 * @param {string} endpoint - The API endpoint path (e.g., "/move")
 * @param {unknown} [body] - Optional request body for POST requests
 * @returns {Promise<T>} The parsed JSON response
 * @throws {Error} If the request times out (30 seconds)
 * @throws {Error} If the HTTP response is not OK (status >= 400)
 * @throws {Error} If the connection is refused (IDE not running)
 *
 * @example
 * // Move class to new package
 * const result = await callIde<RefactoringResult>("/move", {
 *   file: "/path/to/MyClass.java",
 *   line: 1,
 *   column: 14,
 *   targetPackage: "com.example.newpkg"
 * });
 *
 * @example
 * // Extract method
 * const result = await callIde<RefactoringResult>("/extractMethod", {
 *   file: "/path/to/Service.java",
 *   startLine: 10,
 *   startColumn: 5,
 *   endLine: 20,
 *   endColumn: 6,
 *   methodName: "processData"
 * });
 */
async function callIde<T>(endpoint: string, body?: unknown): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30000);

  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: body ? "POST" : "GET",
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error(`IDE returned HTTP ${response.status}`);
    }
    return await response.json() as T;
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Returns the description for the move tool, customized for the current IDE.
 *
 * The base description is augmented with IDE-specific notes about:
 * - Best supported languages
 * - How the targetPackage parameter is interpreted
 * - Special handling for that language/IDE combination
 *
 * @function getMoveDescription
 * @returns {string} The complete tool description including language-specific notes
 *
 * @example
 * // For IntelliJ IDEA
 * getMoveDescription();
 * // Returns description mentioning Java, Kotlin, Scala support
 *
 * @example
 * // For WebStorm
 * getMoveDescription();
 * // Returns description mentioning JavaScript, TypeScript support
 */
function getMoveDescription(): string {
  const baseDesc = `Move class/symbol to different package/module using IDE refactoring.

Automatically updates ALL imports and references across the project.`;

  /**
   * Language-specific notes for each IDE.
   * Maps IDE name to additional description text.
   */
  const langNotes: Record<string, string> = {
    idea: `
Best for: Java, Kotlin, Scala, Groovy

- Java: Move class to different package. Target package must exist.
- Kotlin: Move class/object/function. Supports top-level declarations.`,
    "android-studio": `
Best for: Java, Kotlin (Android)

- Same as IntelliJ IDEA. Move class to different package.
- Handles Android resource references.`,
    webstorm: `
Best for: JavaScript, TypeScript, CSS, HTML

- TypeScript/JS: Move symbol to different file/module.
- targetPackage is the relative path to target file (e.g., "./utils/helpers")`,
    pycharm: `
Best for: Python

- Move class/function to different module.
- targetPackage is the module path (e.g., "myapp.utils.helpers")`,
    goland: `
Best for: Go

- Move type/function to different package.
- targetPackage is the package path (e.g., "github.com/user/project/pkg")`,
    rustrover: `
Best for: Rust

- Move item to different module.
- targetPackage is the module path (e.g., "crate::utils::helpers")`,
    phpstorm: `
Best for: PHP

- Move class to different namespace/directory.
- targetPackage is the namespace (e.g., "App\\Services\\Auth")`,
    rubymine: `
Best for: Ruby

- Move class/module to different file/module.
- targetPackage is the module path (e.g., "MyApp::Services")`,
    clion: `
Best for: C, C++

- Move function/class to different file.
- Updates #include directives automatically.`,
    rider: `
Best for: C#, F#, VB.NET

- Move type to different namespace/file.
- targetPackage is the namespace (e.g., "MyApp.Services.Auth")`,
    datagrip: `
Best for: SQL, Database schemas

- Limited move support for SQL files`,
    aqua: `
Best for: Test automation (Java, Kotlin, Python)

- Move test class to different package`,
    dataspell: `
Best for: Python, Jupyter, Data Science

- Same as PyCharm for Python files`,
  };

  return baseDesc + (langNotes[IDE_NAME] || "");
}

/**
 * Returns the description for the extract_method tool, customized for the current IDE.
 *
 * The base description is augmented with IDE-specific notes about:
 * - Best supported languages
 * - Special patterns handled (async/await, multiple returns, etc.)
 * - Output options (arrow function, method, etc.)
 *
 * @function getExtractMethodDescription
 * @returns {string} The complete tool description including language-specific notes
 *
 * @example
 * // For GoLand
 * getExtractMethodDescription();
 * // Returns description mentioning multiple return values and receiver types
 *
 * @example
 * // For RustRover
 * getExtractMethodDescription();
 * // Returns description mentioning ownership, borrowing, and lifetimes
 */
function getExtractMethodDescription(): string {
  const baseDesc = `Extract code block into a new method/function.

Automatically detects parameters and return type.`;

  /**
   * Language-specific notes for each IDE.
   * Maps IDE name to additional description text.
   */
  const langNotes: Record<string, string> = {
    idea: `
Best for: Java, Kotlin

- Java: Extract to method in the same class
- Kotlin: Extract to function (top-level or member)`,
    "android-studio": `
Best for: Java, Kotlin (Android)

- Same as IntelliJ IDEA
- Handles Android-specific patterns (callbacks, listeners)`,
    webstorm: `
Best for: JavaScript, TypeScript

- Handles async/await
- Can extract to arrow function or regular function`,
    pycharm: `
Best for: Python

- Handles generators and async functions
- Preserves decorator context`,
    goland: `
Best for: Go

- Handles multiple return values
- Automatically determines receiver type for methods`,
    rustrover: `
Best for: Rust

- Handles ownership, borrowing, and lifetimes
- Handles Result/Option return types`,
    phpstorm: `
Best for: PHP

- Handles static and instance methods
- Preserves visibility modifiers`,
    rubymine: `
Best for: Ruby

- Handles blocks and procs
- Preserves method visibility`,
    clion: `
Best for: C, C++

- Handles pointers and references
- Can extract to inline or separate function`,
    rider: `
Best for: C#, F#, VB.NET

- Handles async/await and LINQ
- Can extract to local function or method`,
    datagrip: `
Best for: SQL

- Extract SQL query to stored procedure`,
    aqua: `
Best for: Test automation

- Extract test code to helper method`,
    dataspell: `
Best for: Python, Jupyter

- Same as PyCharm for Python code`,
  };

  return baseDesc + (langNotes[IDE_NAME] || "");
}

/**
 * The MCP server instance.
 * Configured with an IDE-specific name and version, and declares tool capabilities.
 */
const server = new Server(
  {
    name: `claude-${IDE_NAME}-tools`,
    version: "0.3.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

/**
 * Tool definition for the move refactoring operation.
 */
const moveToolDef = {
  name: `${TOOL_PREFIX}_move`,
  title: "Move Symbol",
  description: getMoveDescription(),
  inputSchema: {
    type: "object" as const,
    properties: {
      file: { type: "string", description: "Absolute path to the file containing the symbol" },
      line: { type: "number", description: "Line number (1-based)" },
      column: { type: "number", description: "Column number (1-based)" },
      targetPackage: { type: "string", description: "Target package/module path" },
      searchInComments: { type: "boolean", description: "Also update occurrences in comments (default: false)" },
      searchInNonJavaFiles: { type: "boolean", description: "Also update occurrences in non-Java files like XML, properties (default: false)" },
    },
    required: ["file", "line", "column", "targetPackage"],
  },
};

/**
 * Tool definition for the extract method refactoring operation.
 */
const extractMethodToolDef = {
  name: `${TOOL_PREFIX}_extract_method`,
  title: "Extract Method",
  description: getExtractMethodDescription(),
  inputSchema: {
    type: "object" as const,
    properties: {
      file: { type: "string", description: "Absolute path to the source file" },
      startLine: { type: "number", description: "Start line (1-based)" },
      startColumn: { type: "number", description: "Start column (1-based)" },
      endLine: { type: "number", description: "End line (1-based)" },
      endColumn: { type: "number", description: "End column (1-based)" },
      methodName: { type: "string", description: "Name for the new method/function" },
    },
    required: ["file", "startLine", "startColumn", "endLine", "endColumn", "methodName"],
  },
};

/**
 * Handler for listing available tools.
 *
 * Returns only tools that are actually implemented for the current IDE.
 * Tools are filtered based on the implementedTools field from /status.
 * If the IDE is not available, returns an empty tools list.
 *
 * Tool names are prefixed with the IDE name (e.g., idea_move, webstorm_move).
 * Descriptions are customized based on the IDE's language specialization.
 */
server.setRequestHandler(ListToolsRequestSchema, async () => {
  const implementedTools = await getImplementedTools();
  const tools = [];

  // Only register tools that have at least one implemented language
  if (isToolImplemented("move", implementedTools)) {
    tools.push(moveToolDef);
  }

  if (isToolImplemented("extract_method", implementedTools)) {
    tools.push(extractMethodToolDef);
  }

  return { tools };
});

/**
 * Handler for tool call requests.
 *
 * Routes incoming tool calls to the appropriate IDE endpoint:
 * - {prefix}_move: Calls /move endpoint
 * - {prefix}_extract_method: Calls /extractMethod endpoint
 *
 * Handles connection errors gracefully, providing helpful messages
 * when the IDE is not running or the plugin is not installed.
 *
 * @param {Object} request - The MCP request object
 * @param {Object} request.params - Request parameters
 * @param {string} request.params.name - The tool name being called
 * @param {Object} request.params.arguments - The tool arguments
 * @returns {Promise<ToolResponse>} The tool response
 */
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    /**
     * Move tool handler.
     * Moves a class/symbol to a different package/module.
     */
    if (name === `${TOOL_PREFIX}_move`) {
      const result = await callIde<RefactoringResult>("/move", args);
      return buildRefactoringResponse(result, "Move");
    }

    /**
     * Extract method tool handler.
     * Extracts a code block into a new method/function.
     */
    if (name === `${TOOL_PREFIX}_extract_method`) {
      const result = await callIde<RefactoringResult>("/extractMethod", args);
      return buildRefactoringResponse(result, "Extract method");
    }

    return {
      content: [{ type: "text", text: `Unknown tool: ${name}` }],
      isError: true,
    };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);

    // Provide helpful message for connection errors
    if (errorMessage.includes("ECONNREFUSED") || errorMessage.includes("fetch failed")) {
      return {
        content: [{
          type: "text",
          text: `Cannot connect to IDE at ${BASE_URL}.\n\nMake sure the IDE is running with Claude Tools plugin installed.`,
        }],
        isError: true,
      };
    }

    return {
      content: [{ type: "text", text: `Error: ${errorMessage}` }],
      isError: true,
    };
  }
});

/**
 * Entry point for the IDE-specific MCP server.
 *
 * Creates a stdio transport and connects the server to it.
 * Logs the IDE name and port for debugging purposes.
 *
 * @async
 * @function main
 * @throws {Error} If the server fails to start
 */
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error(`Claude ${IDE_NAME} Tools MCP server running (port ${PORT})`);
}

main().catch(console.error);

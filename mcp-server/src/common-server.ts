#!/usr/bin/env node

/**
 * @fileoverview Common JetBrains MCP Server
 *
 * This module provides a universal Model Context Protocol (MCP) server that works
 * across all JetBrains IDEs. It implements automatic IDE discovery and routing,
 * allowing Claude to interact with whichever IDE has the relevant project open.
 *
 * ## Architecture
 *
 * The server scans predefined ports (8765-8777) to discover running JetBrains IDEs.
 * Each IDE runs its own HTTP server via the Claude Tools plugin. When a refactoring
 * request comes in, this server:
 *
 * 1. Scans all known ports to find running IDEs
 * 2. Matches the target file path against open projects
 * 3. Routes the request to the appropriate IDE
 * 4. Returns the result to Claude
 *
 * ## Available Tools
 *
 * - **status**: Shows all running JetBrains IDEs and their open projects
 * - **rename**: Safe semantic rename using the IDE's refactoring engine
 * - **find_usages**: Semantic code search using the IDE's index
 * - **get_diagnostics**: Retrieve code analysis diagnostics (errors, warnings, etc.)
 *
 * ## Port Assignments
 *
 * Each JetBrains IDE is assigned a unique port:
 * - IntelliJ IDEA: 8765
 * - WebStorm: 8766
 * - PyCharm: 8767
 * - GoLand: 8768
 * - PhpStorm: 8769
 * - RubyMine: 8770
 * - CLion: 8771
 * - Rider: 8772
 * - DataGrip: 8773
 * - Android Studio: 8774
 * - RustRover: 8775
 * - Aqua: 8776
 * - DataSpell: 8777
 *
 * @module common-server
 * @version 0.3.0
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

/**
 * Configuration for known JetBrains IDE ports.
 * Each IDE listens on a unique port for refactoring requests.
 *
 * @constant
 * @type {Array<{port: number, name: string}>}
 */
const IDE_PORTS = [
  { port: 8765, name: "IntelliJ IDEA" },
  { port: 8766, name: "WebStorm" },
  { port: 8767, name: "PyCharm" },
  { port: 8768, name: "GoLand" },
  { port: 8769, name: "PhpStorm" },
  { port: 8770, name: "RubyMine" },
  { port: 8771, name: "CLion" },
  { port: 8772, name: "Rider" },
  { port: 8773, name: "DataGrip" },
  { port: 8774, name: "Android Studio" },
  { port: 8775, name: "RustRover" },
  { port: 8776, name: "Aqua" },
  { port: 8777, name: "DataSpell" },
];

/**
 * The host address for IDE connections.
 * Uses localhost for security.
 *
 * @constant
 * @type {string}
 */
const HOST = "127.0.0.1";

/**
 * Represents a project open in a JetBrains IDE.
 *
 * @typedef {Object} ProjectInfo
 * @property {string} name - The display name of the project
 * @property {string} path - The absolute file system path to the project root
 *
 * @example
 * const project: ProjectInfo = {
 *   name: "my-app",
 *   path: "/Users/dev/projects/my-app"
 * };
 */
type ProjectInfo = {
  name: string;
  path: string;
};

/**
 * Result of querying an IDE's status endpoint.
 * Contains comprehensive information about the IDE state.
 *
 * @typedef {Object} StatusResult
 * @property {boolean} ok - Whether the status request succeeded
 * @property {string} ideType - The type of IDE (e.g., "IntelliJ IDEA", "WebStorm")
 * @property {string} ideVersion - The version string of the IDE
 * @property {number} port - The port the IDE is listening on
 * @property {ProjectInfo[]} openProjects - List of currently open projects
 * @property {boolean} indexingInProgress - Whether the IDE is currently indexing
 * @property {Record<string, boolean>} languagePlugins - Map of language plugins and their enabled status
 *
 * @example
 * const status: StatusResult = {
 *   ok: true,
 *   ideType: "IntelliJ IDEA",
 *   ideVersion: "2024.1",
 *   port: 8765,
 *   openProjects: [{ name: "my-project", path: "/path/to/project" }],
 *   indexingInProgress: false,
 *   languagePlugins: { java: true, kotlin: true, python: false }
 * };
 */
type StatusResult = {
  ok: boolean;
  ideType: string;
  ideVersion: string;
  port: number;
  openProjects: ProjectInfo[];
  indexingInProgress: boolean;
  languagePlugins: Record<string, boolean>;
};

/**
 * Result of a refactoring operation (rename, move, etc.).
 *
 * @typedef {Object} RefactoringResult
 * @property {boolean} success - Whether the refactoring completed successfully
 * @property {string} message - Human-readable description of what happened or error message
 *
 * @example
 * // Successful refactoring
 * const result: RefactoringResult = {
 *   success: true,
 *   message: "Renamed 'oldName' to 'newName' (5 usages updated)"
 * };
 *
 * // Failed refactoring
 * const errorResult: RefactoringResult = {
 *   success: false,
 *   message: "Cannot rename: symbol not found at specified location"
 * };
 */
type RefactoringResult = {
  success: boolean;
  message: string;
};

/**
 * Represents a single usage of a symbol found by Find Usages.
 *
 * @typedef {Object} UsageItem
 * @property {string} file - Absolute path to the file containing the usage
 * @property {number} line - Line number (1-based) of the usage
 * @property {number} column - Column number (1-based) of the usage
 * @property {string} preview - Code snippet showing the usage context
 *
 * @example
 * const usage: UsageItem = {
 *   file: "/project/src/Service.java",
 *   line: 42,
 *   column: 15,
 *   preview: "    service.processData(input);"
 * };
 */
type UsageItem = {
  file: string;
  line: number;
  column: number;
  preview: string;
};

/**
 * Result of a Find Usages operation.
 *
 * @typedef {Object} FindUsagesResult
 * @property {boolean} success - Whether the search completed successfully
 * @property {string} message - Summary message (e.g., "Found 5 usages")
 * @property {UsageItem[]} usages - Array of all found usages
 *
 * @example
 * const result: FindUsagesResult = {
 *   success: true,
 *   message: "Found 3 usages of 'MyClass'",
 *   usages: [
 *     { file: "/src/Main.java", line: 10, column: 5, preview: "new MyClass()" },
 *     { file: "/src/Test.java", line: 20, column: 8, preview: "MyClass instance = null;" }
 *   ]
 * };
 */
type FindUsagesResult = {
  success: boolean;
  message: string;
  usages: UsageItem[];
};

/**
 * Represents a quick fix action available for a diagnostic.
 *
 * @typedef {Object} QuickFixItem
 * @property {number} id - Index of this fix (used when applying)
 * @property {string} name - Display name of the fix action
 * @property {string} [familyName] - Family/category of fixes
 * @property {string} [description] - Longer description of what the fix does
 */
type QuickFixItem = {
  id: number;
  name: string;
  familyName?: string;
  description?: string;
};

/**
 * Represents a single diagnostic (error, warning, etc.) from code analysis.
 *
 * @typedef {Object} DiagnosticItem
 * @property {string} file - Absolute path to the file containing the diagnostic
 * @property {number} line - Starting line number (1-based)
 * @property {number} column - Starting column number (1-based)
 * @property {number} endLine - Ending line number (1-based)
 * @property {number} endColumn - Ending column number (1-based)
 * @property {string} severity - Severity level (ERROR, WARNING, WEAK_WARNING, INFO, HINT)
 * @property {string} message - Human-readable description of the issue
 * @property {string} [source] - Origin of the diagnostic (e.g., "Java", "ESLint")
 * @property {QuickFixItem[]} [fixes] - Available quick fix actions
 */
type DiagnosticItem = {
  file: string;
  line: number;
  column: number;
  endLine: number;
  endColumn: number;
  severity: string;
  message: string;
  source?: string;
  fixes?: QuickFixItem[];
};

/**
 * Result of a diagnostics operation.
 *
 * @typedef {Object} DiagnosticsResult
 * @property {boolean} success - Whether the operation completed successfully
 * @property {string} message - Summary message
 * @property {DiagnosticItem[]} diagnostics - Array of diagnostics found
 * @property {number} totalCount - Total diagnostics before limit
 * @property {boolean} truncated - Whether results were truncated
 */
type DiagnosticsResult = {
  success: boolean;
  message: string;
  diagnostics: DiagnosticItem[];
  totalCount: number;
  truncated: boolean;
};

/**
 * Result of applying a quick fix.
 *
 * @typedef {Object} ApplyFixResult
 * @property {boolean} success - Whether the fix was applied successfully
 * @property {string} message - Description of result or error
 * @property {string} [fixName] - Name of the applied fix
 * @property {string[]} [affectedFiles] - Files modified by the fix
 */
type ApplyFixResult = {
  success: boolean;
  message: string;
  fixName?: string;
  affectedFiles?: string[];
};

/**
 * Information about a running IDE instance.
 * Combines the port number with the IDE's status information.
 *
 * @typedef {Object} IdeInfo
 * @property {number} port - The port the IDE is listening on
 * @property {StatusResult} status - The full status of the IDE
 */
type IdeInfo = {
  port: number;
  status: StatusResult;
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
 * Fetches status information from a single IDE port.
 *
 * Makes an HTTP GET request to the IDE's status endpoint with a 1-second timeout.
 * If the IDE is not running or the request fails, returns null.
 *
 * @async
 * @function fetchIdeStatus
 * @param {number} port - The port number to query
 * @returns {Promise<StatusResult | null>} The IDE status if available, null otherwise
 *
 * @example
 * const status = await fetchIdeStatus(8765);
 * if (status) {
 *   console.log(`IntelliJ IDEA ${status.ideVersion} is running`);
 * }
 */
async function fetchIdeStatus(port: number): Promise<StatusResult | null> {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 1000);

    const response = await fetch(`http://${HOST}:${port}/status`, {
      signal: controller.signal,
    });
    clearTimeout(timeout);

    if (response.ok) {
      return await response.json() as StatusResult;
    }
  } catch {
    // IDE not running on this port
  }
  return null;
}

/**
 * Scans all known IDE ports and returns information about running IDEs.
 *
 * Performs parallel requests to all known ports and collects responses
 * from IDEs that are currently running.
 *
 * @async
 * @function scanRunningIdes
 * @returns {Promise<IdeInfo[]>} Array of running IDE information, may be empty
 *
 * @example
 * const ides = await scanRunningIdes();
 * console.log(`Found ${ides.length} running IDEs`);
 * for (const ide of ides) {
 *   console.log(`${ide.status.ideType} on port ${ide.port}`);
 * }
 */
async function scanRunningIdes(): Promise<IdeInfo[]> {
  const results = await Promise.all(
    IDE_PORTS.map(async ({ port }) => {
      const status = await fetchIdeStatus(port);
      return status ? { port, status } : null;
    })
  );
  return results.filter((r): r is IdeInfo => r !== null);
}

/**
 * Normalizes a file path for cross-platform comparison.
 *
 * Converts backslashes to forward slashes, removes trailing slashes,
 * and on Windows, converts to lowercase for case-insensitive comparison.
 *
 * @function normalizePath
 * @param {string} p - The file path to normalize
 * @returns {string} The normalized path
 *
 * @example
 * // On Windows
 * normalizePath("C:\\Users\\Dev\\Project\\") // Returns "c:/users/dev/project"
 *
 * // On Unix
 * normalizePath("/home/dev/project/") // Returns "/home/dev/project"
 */
function normalizePath(p: string): string {
  let normalized = p.replace(/\\/g, "/").replace(/\/+$/, "");
  if (process.platform === "win32") {
    normalized = normalized.toLowerCase();
  }
  return normalized;
}

/**
 * Finds the IDE that has a project containing the given file path.
 *
 * Scans all running IDEs and matches the file path against open projects.
 * If multiple IDEs have projects that could contain the file, returns the
 * one with the longest matching path (most specific match).
 *
 * @async
 * @function findIdeForFile
 * @param {string} filePath - The absolute path to the file
 * @returns {Promise<IdeInfo | null>} The IDE info if found, null if no matching project
 *
 * @example
 * const ide = await findIdeForFile("/home/dev/my-project/src/Main.java");
 * if (ide) {
 *   console.log(`File belongs to project in ${ide.status.ideType}`);
 * } else {
 *   console.log("File not found in any open project");
 * }
 */
async function findIdeForFile(filePath: string): Promise<IdeInfo | null> {
  const normalizedPath = normalizePath(filePath);
  const runningIdes = await scanRunningIdes();

  let bestMatch: { ide: IdeInfo; pathLength: number } | null = null;

  for (const ide of runningIdes) {
    for (const project of ide.status.openProjects || []) {
      const projectPath = normalizePath(project.path);
      if (normalizedPath.startsWith(projectPath + "/") || normalizedPath === projectPath) {
        // Prefer longer path match (more specific project)
        if (!bestMatch || projectPath.length > bestMatch.pathLength) {
          bestMatch = { ide, pathLength: projectPath.length };
        }
      }
    }
  }

  return bestMatch?.ide ?? null;
}

/**
 * Result of validating file location parameters.
 *
 * @typedef {Object} FileLocationValidationResult
 * @property {boolean} valid - Whether all parameters are valid
 * @property {ToolResponse} [error] - Error response if validation failed
 * @property {string} [file] - Validated file path
 * @property {number} [line] - Validated line number
 * @property {number} [column] - Validated column number
 */
type FileLocationValidationResult =
  | { valid: true; file: string; line: number; column: number }
  | { valid: false; error: ToolResponse };

/**
 * Validates file, line, and column parameters for IDE operations.
 *
 * Checks that:
 * - file is a non-empty string
 * - line is a number >= 1
 * - column is a number >= 1
 *
 * @function validateFileLocationParams
 * @param {Record<string, unknown>} args - The arguments object containing file, line, column
 * @returns {FileLocationValidationResult} Validation result with either valid params or error response
 *
 * @example
 * const validation = validateFileLocationParams(args);
 * if (!validation.valid) {
 *   return validation.error;
 * }
 * // Use validation.file, validation.line, validation.column
 */
function validateFileLocationParams(args: Record<string, unknown>): FileLocationValidationResult {
  const { file, line, column } = args;

  if (typeof file !== "string" || !file) {
    return {
      valid: false,
      error: { content: [{ type: "text", text: "Error: file parameter is required" }], isError: true },
    };
  }
  if (typeof line !== "number" || line < 1) {
    return {
      valid: false,
      error: { content: [{ type: "text", text: "Error: line must be a positive number" }], isError: true },
    };
  }
  if (typeof column !== "number" || column < 1) {
    return {
      valid: false,
      error: { content: [{ type: "text", text: "Error: column must be a positive number" }], isError: true },
    };
  }

  return { valid: true, file, line, column };
}

/**
 * Handles the case when no IDE is found for a given file path.
 *
 * Scans for running IDEs and returns an appropriate error message:
 * - If no IDEs are running, prompts user to start an IDE
 * - If IDEs are running but file is not in any project, lists available IDEs
 *
 * @async
 * @function handleMissingIde
 * @param {string} filePath - The file path that was not found in any project
 * @returns {Promise<ToolResponse>} Error response with helpful information
 *
 * @example
 * const ide = await findIdeForFile(file);
 * if (!ide) {
 *   return await handleMissingIde(file);
 * }
 */
async function handleMissingIde(filePath: string): Promise<ToolResponse> {
  const runningIdes = await scanRunningIdes();

  if (runningIdes.length === 0) {
    return {
      content: [{
        type: "text",
        text: `No JetBrains IDEs are running.\n\nStart an IDE and open a project containing:\n${filePath}`,
      }],
      isError: true,
    };
  }

  const ideList = runningIdes
    .map(i => `  - ${i.status.ideType}: ${i.status.openProjects.map(p => p.path).join(", ") || "(no projects)"}`)
    .join("\n");

  return {
    content: [{
      type: "text",
      text: `File not found in any open project.\n\nFile: ${filePath}\n\nRunning IDEs:\n${ideList}\n\nOpen the project containing this file in one of the IDEs.`,
    }],
    isError: true,
  };
}

/**
 * Makes an HTTP request to a specific IDE's endpoint.
 *
 * Supports both GET and POST requests. POST requests automatically
 * serialize the body as JSON and set appropriate headers.
 *
 * @async
 * @function callIde
 * @template T - The expected response type
 * @param {number} port - The IDE's port number
 * @param {string} endpoint - The API endpoint path (e.g., "/rename")
 * @param {unknown} [body] - Optional request body for POST requests
 * @returns {Promise<T>} The parsed JSON response
 * @throws {Error} If the HTTP response is not OK (status >= 400)
 *
 * @example
 * // GET request
 * const status = await callIde<StatusResult>(8765, "/status");
 *
 * // POST request
 * const result = await callIde<RefactoringResult>(8765, "/rename", {
 *   file: "/path/to/file.java",
 *   line: 10,
 *   column: 5,
 *   newName: "newMethodName"
 * });
 */
async function callIde<T>(port: number, endpoint: string, body?: unknown): Promise<T> {
  const response = await fetch(`http://${HOST}:${port}${endpoint}`, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`IDE returned HTTP ${response.status}`);
  }
  return await response.json() as T;
}

/**
 * The MCP server instance.
 * Configured with the server name and version, and declares tool capabilities.
 */
const server = new Server(
  {
    name: "claude-ide-tools",
    version: "0.3.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

/**
 * Handler for listing available tools.
 *
 * Returns the list of tools provided by this server:
 * - status: Check running IDEs
 * - rename: Semantic rename refactoring
 * - find_usages: Find all usages of a symbol
 */
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "status",
        title: "Status",
        description: `Shows all running JetBrains IDEs and their open projects.

Use this to see:
- Which IDEs are currently running
- What projects are open in each IDE
- Indexing status
- Available language plugins

This helps you understand which IDE tools are available.`,
        inputSchema: {
          type: "object",
          properties: {},
          required: [],
        },
      },
      {
        name: "rename",
        title: "Rename Symbol",
        description: `PREFERRED: Safe semantic rename using IDE's refactoring engine.

Automatically detects which IDE has the file open and routes the request there.
Works across ALL JetBrains IDEs (IntelliJ, WebStorm, PyCharm, etc.)

Why use this:
- Automatically finds and updates ALL usages across the entire project
- Understands code semantics: won't rename unrelated text
- Handles inheritance, overrides, and interface implementations
- Zero risk of breaking code

Use for: classes, methods, functions, variables, fields, parameters.`,
        inputSchema: {
          type: "object",
          properties: {
            file: { type: "string", description: "Absolute path to the file containing the symbol" },
            line: { type: "number", description: "Line number (1-based)" },
            column: { type: "number", description: "Column number (1-based)" },
            newName: { type: "string", description: "New name for the symbol" },
            searchInComments: { type: "boolean", description: "Also rename occurrences in comments (default: false)" },
            searchTextOccurrences: { type: "boolean", description: "Also rename text occurrences in string literals (default: false)" },
          },
          required: ["file", "line", "column", "newName"],
        },
      },
      {
        name: "find_usages",
        title: "Find Usages",
        description: `PREFERRED: Semantic code search using IDE's index.

Automatically detects which IDE has the file open and routes the request there.
Works across ALL JetBrains IDEs.

Why use this instead of grep:
- Finds REAL usages, not just text matches
- Distinguishes between: definition, read, write, import, inheritance
- Won't return false positives from comments or strings
- Works instantly using IDE's pre-built index

IMPORTANT - Column positioning:
- Column must land EXACTLY on the symbol identifier (any character within it works)
- If off by 1-2 characters, IDE may find usages for an adjacent symbol instead
- Tip: Prefer using the import line where the symbol name is at the end and easier to locate
- Example: "class Foo : Bar {" - to find Bar usages, column must be >=11 (on "Bar"), not on ": "`,
        inputSchema: {
          type: "object",
          properties: {
            file: { type: "string", description: "Absolute path to the file containing the symbol" },
            line: { type: "number", description: "Line number (1-based)" },
            column: { type: "number", description: "Column number (1-based)" },
          },
          required: ["file", "line", "column"],
        },
      },
      {
        name: "get_diagnostics",
        title: "Get Diagnostics",
        description: `Retrieve code analysis diagnostics (errors, warnings, etc.) from the IDE.

Automatically detects which IDE has the file/project open and routes the request there.
Works across ALL JetBrains IDEs.

KEY PARAMETERS:
- file: Path to analyze ONE file. Omit for project-wide scan.
- runInspections: Set to TRUE for comprehensive analysis (default: false = cached only)
- severity: Filter by ["ERROR", "WARNING", "WEAK_WARNING", "INFO", "HINT"]
- limit: Max results (default: 100)

IMPORTANT - Analysis modes:
- DEFAULT (runInspections=false): Fast, but only shows diagnostics for files the IDE has already analyzed in background. May return empty for unopened files.
- COMPREHENSIVE (runInspections=true): Runs all inspections programmatically. Slower but analyzes ALL files including unopened ones.

For full project analysis, use: runInspections=true (without file parameter)

Returns diagnostics sorted by severity (errors first), then by file and line.`,
        inputSchema: {
          type: "object",
          properties: {
            file: { type: "string", description: "Absolute path to analyze (optional - omit for project-wide)" },
            project: { type: "string", description: "Project name or path hint for disambiguation (optional)" },
            severity: {
              type: "array",
              items: { type: "string", enum: ["ERROR", "WARNING", "WEAK_WARNING", "INFO", "HINT"] },
              description: "Filter by severity levels (default: all)",
            },
            limit: { type: "number", description: "Maximum diagnostics to return (default: 100)" },
            runInspections: { type: "boolean", description: "Run inspections programmatically instead of using cached highlights (default: false)" },
          },
          required: [],
        },
      },
      {
        name: "apply_fix",
        title: "Apply Quick Fix",
        description: `Apply a quick fix action to resolve a diagnostic issue.

Use this after get_diagnostics to automatically fix problems found by the IDE.

How it works:
1. Call get_diagnostics to find issues - each diagnostic includes available fixes
2. Choose a fix from the 'fixes' array in the diagnostic
3. Call apply_fix with the file location and fixId

Parameters:
- file: The file containing the diagnostic
- line, column: Position of the diagnostic
- fixId: Index of the fix to apply (from diagnostics response)
- diagnosticMessage: (optional) Match specific diagnostic by message
- runInspections: (optional) Set to true if you used runInspections=true in get_diagnostics

IMPORTANT - Analysis mode consistency:
- If you found the diagnostic with get_diagnostics (default mode), apply_fix works directly
- If you found the diagnostic with get_diagnostics(runInspections=true), you MUST also use apply_fix(runInspections=true)

This is the preferred way to fix code issues - uses IDE's built-in fixes which are safe and semantically correct.`,
        inputSchema: {
          type: "object",
          properties: {
            file: { type: "string", description: "Absolute path to the file containing the diagnostic" },
            line: { type: "number", description: "Line number (1-based) of the diagnostic" },
            column: { type: "number", description: "Column number (1-based) of the diagnostic" },
            fixId: { type: "number", description: "Index of the fix to apply (from the fixes array in diagnostics)" },
            diagnosticMessage: { type: "string", description: "The diagnostic message to match (optional, for disambiguation)" },
            runInspections: { type: "boolean", description: "Run inspections to find the diagnostic (use when get_diagnostics was called with runInspections=true)" },
          },
          required: ["file", "line", "column", "fixId"],
        },
      },
    ],
  };
});

/**
 * Handler for tool call requests.
 *
 * Routes incoming tool calls to the appropriate handler:
 * - status: Scans all ports and returns running IDEs
 * - rename: Finds the appropriate IDE and performs rename refactoring
 * - find_usages: Finds the appropriate IDE and searches for symbol usages
 * - get_diagnostics: Retrieves code analysis diagnostics from IDE
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
    switch (name) {
      /**
       * Status tool handler.
       * Scans all known IDE ports and returns information about running IDEs.
       */
      case "status": {
        const runningIdes = await scanRunningIdes();

        if (runningIdes.length === 0) {
          return {
            content: [{
              type: "text",
              text: "No JetBrains IDEs are currently running.\n\nMake sure:\n1. An IDE (IntelliJ, WebStorm, PyCharm, etc.) is running\n2. Claude Tools plugin is installed and enabled",
            }],
            isError: true,
          };
        }

        const lines: string[] = ["Running JetBrains IDEs:\n"];

        for (const { port, status } of runningIdes) {
          lines.push(`${status.ideType} (port ${port})`);
          lines.push(`  Version: ${status.ideVersion}`);

          if (status.openProjects.length > 0) {
            lines.push(`  Projects:`);
            for (const proj of status.openProjects) {
              lines.push(`    - ${proj.name}: ${proj.path}`);
            }
          } else {
            lines.push(`  Projects: (none)`);
          }

          lines.push(`  Indexing: ${status.indexingInProgress ? "in progress" : "complete"}`);

          const available = Object.entries(status.languagePlugins || {})
            .filter(([_, v]) => v).map(([k]) => k);
          if (available.length > 0) {
            lines.push(`  Languages: ${available.join(", ")}`);
          }

          lines.push("");
        }

        return { content: [{ type: "text", text: lines.join("\n") }] };
      }

      /**
       * Rename tool handler.
       * Validates parameters, finds the appropriate IDE, and performs the rename.
       */
      case "rename": {
        const typedArgs = (args ?? {}) as Record<string, unknown>;
        const validation = validateFileLocationParams(typedArgs);
        if (!validation.valid) {
          return validation.error;
        }

        const { newName } = typedArgs;
        if (typeof newName !== "string" || !newName) {
          return { content: [{ type: "text", text: "Error: newName parameter is required" }], isError: true };
        }

        const ide = await findIdeForFile(validation.file);
        if (!ide) {
          return await handleMissingIde(validation.file);
        }

        const result = await callIde<RefactoringResult>(ide.port, "/rename", args);
        return {
          content: [{
            type: "text",
            text: result.success
              ? result.message
              : `Rename failed: ${result.message}`,
          }],
          isError: !result.success,
        };
      }

      /**
       * Find usages tool handler.
       * Validates parameters, finds the appropriate IDE, and searches for usages.
       */
      case "find_usages": {
        const typedArgs = (args ?? {}) as Record<string, unknown>;
        const validation = validateFileLocationParams(typedArgs);
        if (!validation.valid) {
          return validation.error;
        }

        const ide = await findIdeForFile(validation.file);
        if (!ide) {
          return await handleMissingIde(validation.file);
        }

        const result = await callIde<FindUsagesResult>(ide.port, "/findUsages", args);

        if (!result.success) {
          return {
            content: [{ type: "text", text: `Failed: ${result.message}` }],
            isError: true,
          };
        }

        const usages = result.usages || [];
        const formatted = usages
          .map((u) => `${u.file}:${u.line}:${u.column}\n  ${u.preview}`)
          .join("\n\n");

        return {
          content: [{
            type: "text",
            text: usages.length > 0
              ? `${result.message}\n\n${formatted}`
              : result.message || "No usages found",
          }],
        };
      }

      /**
       * Get diagnostics tool handler.
       * Retrieves code analysis diagnostics from the IDE.
       */
      case "get_diagnostics": {
        const typedArgs = (args ?? {}) as Record<string, unknown>;
        const { file, project, severity, limit, runInspections } = typedArgs;

        // Find IDE - either by file path or use first available
        let ide: IdeInfo | null = null;

        if (typeof file === "string" && file) {
          ide = await findIdeForFile(file);
          if (!ide) {
            return await handleMissingIde(file);
          }
        } else {
          // No file specified - use first running IDE
          const runningIdes = await scanRunningIdes();
          if (runningIdes.length === 0) {
            return {
              content: [{
                type: "text",
                text: "No JetBrains IDEs are running.\n\nStart an IDE with a project open to get diagnostics.",
              }],
              isError: true,
            };
          }
          ide = runningIdes[0];
        }

        const result = await callIde<DiagnosticsResult>(ide.port, "/diagnostics", {
          file: typeof file === "string" ? file : undefined,
          project: typeof project === "string" ? project : undefined,
          severity: Array.isArray(severity) ? severity : undefined,
          limit: typeof limit === "number" ? limit : undefined,
          runInspections: typeof runInspections === "boolean" ? runInspections : undefined,
        });

        if (!result.success) {
          return {
            content: [{ type: "text", text: `Failed: ${result.message}` }],
            isError: true,
          };
        }

        const diagnostics = result.diagnostics || [];

        if (diagnostics.length === 0) {
          return {
            content: [{ type: "text", text: result.message || "No diagnostics found" }],
          };
        }

        // Format diagnostics for display
        const formatted = diagnostics
          .map((d) => {
            const location = `${d.file}:${d.line}:${d.column}`;
            const severityTag = `[${d.severity}]`;
            const source = d.source ? ` (${d.source})` : "";
            let result = `${severityTag} ${location}${source}\n  ${d.message}`;

            // Show available fixes if any
            if (d.fixes && d.fixes.length > 0) {
              const fixList = d.fixes
                .map((f) => `    [${f.id}] ${f.name}`)
                .join("\n");
              result += `\n  Fixes:\n${fixList}`;
            }

            return result;
          })
          .join("\n\n");

        const truncationNote = result.truncated
          ? `\n\n(Showing ${diagnostics.length} of ${result.totalCount} total)`
          : "";

        return {
          content: [{
            type: "text",
            text: `${result.message}\n\n${formatted}${truncationNote}`,
          }],
        };
      }

      /**
       * Apply fix tool handler.
       * Applies a quick fix action to resolve a diagnostic.
       */
      case "apply_fix": {
        const typedArgs = (args ?? {}) as Record<string, unknown>;
        const validation = validateFileLocationParams(typedArgs);
        if (!validation.valid) {
          return validation.error;
        }

        const { fixId, diagnosticMessage, runInspections } = typedArgs;
        if (typeof fixId !== "number" || fixId < 0) {
          return {
            content: [{ type: "text", text: "Error: fixId must be a non-negative number" }],
            isError: true,
          };
        }

        const ide = await findIdeForFile(validation.file);
        if (!ide) {
          return await handleMissingIde(validation.file);
        }

        const result = await callIde<ApplyFixResult>(ide.port, "/applyFix", {
          file: validation.file,
          line: validation.line,
          column: validation.column,
          fixId,
          diagnosticMessage: typeof diagnosticMessage === "string" ? diagnosticMessage : undefined,
          runInspections: typeof runInspections === "boolean" ? runInspections : undefined,
        });

        if (!result.success) {
          return {
            content: [{ type: "text", text: `Failed to apply fix: ${result.message}` }],
            isError: true,
          };
        }

        let responseText = result.message;
        if (result.affectedFiles && result.affectedFiles.length > 0) {
          responseText += `\n\nModified files:\n${result.affectedFiles.map(f => `  - ${f}`).join("\n")}`;
        }

        return {
          content: [{ type: "text", text: responseText }],
        };
      }

      default:
        return {
          content: [{ type: "text", text: `Unknown tool: ${name}` }],
          isError: true,
        };
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    return {
      content: [{ type: "text", text: `Error: ${errorMessage}` }],
      isError: true,
    };
  }
});

/**
 * Entry point for the MCP server.
 *
 * Creates a stdio transport and connects the server to it.
 * The server then listens for MCP requests from Claude.
 *
 * @async
 * @function main
 * @throws {Error} If the server fails to start
 */
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Claude IDE Tools (common) MCP server running");
}

main().catch(console.error);

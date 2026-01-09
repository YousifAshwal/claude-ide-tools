/**
 * Tests for ide-server.ts
 *
 * These tests cover:
 * - getMoveDescription() - language-specific descriptions
 * - getExtractMethodDescription() - language-specific descriptions
 * - Tool handlers for move and extract_method
 * - Error handling (connection refused, etc.)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Types
type RefactoringResult = {
  success: boolean;
  message: string;
};

// Configuration
const HOST = "127.0.0.1";

/**
 * Get language-specific descriptions for move tool.
 * Extracted from ide-server.ts for testing.
 */
function getMoveDescription(ideName: string): string {
  const baseDesc = `Move class/symbol to different package/module using IDE refactoring.

Automatically updates ALL imports and references across the project.`;

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
  };

  return baseDesc + (langNotes[ideName] || "");
}

/**
 * Get language-specific descriptions for extract_method tool.
 * Extracted from ide-server.ts for testing.
 */
function getExtractMethodDescription(ideName: string): string {
  const baseDesc = `Extract code block into a new method/function.

Automatically detects parameters and return type.`;

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
  };

  return baseDesc + (langNotes[ideName] || "");
}

/**
 * Makes a request to the IDE.
 */
async function callIde<T>(baseUrl: string, endpoint: string, body?: unknown, fetchFn = fetch): Promise<T> {
  const response = await fetchFn(`${baseUrl}${endpoint}`, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  return await response.json() as T;
}

/**
 * Simulates the tool handler logic from ide-server.ts
 */
async function handleMoveTool(
  toolPrefix: string,
  baseUrl: string,
  name: string,
  args: { file: string; line: number; column: number; targetPackage: string },
  fetchFn = fetch
): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean }> {
  if (name !== `${toolPrefix}_move`) {
    return {
      content: [{ type: "text", text: `Unknown tool: ${name}` }],
      isError: true,
    };
  }

  try {
    const result = await callIde<RefactoringResult>(baseUrl, "/move", args, fetchFn);
    return {
      content: [{
        type: "text",
        text: result.success ? result.message : `Move failed: ${result.message}`,
      }],
      isError: !result.success,
    };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);

    if (errorMessage.includes("ECONNREFUSED") || errorMessage.includes("fetch failed")) {
      return {
        content: [{
          type: "text",
          text: `Cannot connect to IDE at ${baseUrl}.\n\nMake sure the IDE is running with Claude Tools plugin installed.`,
        }],
        isError: true,
      };
    }

    return {
      content: [{ type: "text", text: `Error: ${errorMessage}` }],
      isError: true,
    };
  }
}

/**
 * Simulates the extract_method tool handler logic from ide-server.ts
 */
async function handleExtractMethodTool(
  toolPrefix: string,
  baseUrl: string,
  name: string,
  args: { file: string; startLine: number; startColumn: number; endLine: number; endColumn: number; methodName: string },
  fetchFn = fetch
): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean }> {
  if (name !== `${toolPrefix}_extract_method`) {
    return {
      content: [{ type: "text", text: `Unknown tool: ${name}` }],
      isError: true,
    };
  }

  try {
    const result = await callIde<RefactoringResult>(baseUrl, "/extractMethod", args, fetchFn);
    return {
      content: [{
        type: "text",
        text: result.success ? result.message : `Extract method failed: ${result.message}`,
      }],
      isError: !result.success,
    };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);

    if (errorMessage.includes("ECONNREFUSED") || errorMessage.includes("fetch failed")) {
      return {
        content: [{
          type: "text",
          text: `Cannot connect to IDE at ${baseUrl}.\n\nMake sure the IDE is running with Claude Tools plugin installed.`,
        }],
        isError: true,
      };
    }

    return {
      content: [{ type: "text", text: `Error: ${errorMessage}` }],
      isError: true,
    };
  }
}

// ============================================================================
// Tests
// ============================================================================

describe('getMoveDescription', () => {
  it('should return base description for unknown IDE', () => {
    const desc = getMoveDescription("unknown-ide");

    expect(desc).toContain("Move class/symbol to different package/module using IDE refactoring");
    expect(desc).toContain("Automatically updates ALL imports and references across the project");
    // Should not have language-specific notes
    expect(desc).not.toContain("Best for:");
  });

  it('should return IntelliJ IDEA specific description', () => {
    const desc = getMoveDescription("idea");

    expect(desc).toContain("Move class/symbol to different package/module");
    expect(desc).toContain("Best for: Java, Kotlin, Scala, Groovy");
    expect(desc).toContain("Java: Move class to different package");
    expect(desc).toContain("Kotlin: Move class/object/function");
  });

  it('should return WebStorm specific description', () => {
    const desc = getMoveDescription("webstorm");

    expect(desc).toContain("Best for: JavaScript, TypeScript, CSS, HTML");
    expect(desc).toContain("TypeScript/JS: Move symbol to different file/module");
    expect(desc).toContain("./utils/helpers");
  });

  it('should return PyCharm specific description', () => {
    const desc = getMoveDescription("pycharm");

    expect(desc).toContain("Best for: Python");
    expect(desc).toContain("Move class/function to different module");
    expect(desc).toContain("myapp.utils.helpers");
  });

  it('should return GoLand specific description', () => {
    const desc = getMoveDescription("goland");

    expect(desc).toContain("Best for: Go");
    expect(desc).toContain("Move type/function to different package");
    expect(desc).toContain("github.com/user/project/pkg");
  });

  it('should return RustRover specific description', () => {
    const desc = getMoveDescription("rustrover");

    expect(desc).toContain("Best for: Rust");
    expect(desc).toContain("Move item to different module");
    expect(desc).toContain("crate::utils::helpers");
  });

  it('should return PhpStorm specific description', () => {
    const desc = getMoveDescription("phpstorm");

    expect(desc).toContain("Best for: PHP");
    expect(desc).toContain("Move class to different namespace/directory");
    expect(desc).toContain("App\\Services\\Auth");
  });

  it('should return RubyMine specific description', () => {
    const desc = getMoveDescription("rubymine");

    expect(desc).toContain("Best for: Ruby");
    expect(desc).toContain("Move class/module to different file/module");
    expect(desc).toContain("MyApp::Services");
  });

  it('should return CLion specific description', () => {
    const desc = getMoveDescription("clion");

    expect(desc).toContain("Best for: C, C++");
    expect(desc).toContain("Move function/class to different file");
    expect(desc).toContain("#include directives");
  });

  it('should return Rider specific description', () => {
    const desc = getMoveDescription("rider");

    expect(desc).toContain("Best for: C#, F#, VB.NET");
    expect(desc).toContain("Move type to different namespace/file");
    expect(desc).toContain("MyApp.Services.Auth");
  });

  it('should return Android Studio specific description', () => {
    const desc = getMoveDescription("android-studio");

    expect(desc).toContain("Best for: Java, Kotlin (Android)");
    expect(desc).toContain("Same as IntelliJ IDEA");
    expect(desc).toContain("Android resource references");
  });
});

describe('getExtractMethodDescription', () => {
  it('should return base description for unknown IDE', () => {
    const desc = getExtractMethodDescription("unknown-ide");

    expect(desc).toContain("Extract code block into a new method/function");
    expect(desc).toContain("Automatically detects parameters and return type");
    expect(desc).not.toContain("Best for:");
  });

  it('should return IntelliJ IDEA specific description', () => {
    const desc = getExtractMethodDescription("idea");

    expect(desc).toContain("Best for: Java, Kotlin");
    expect(desc).toContain("Java: Extract to method in the same class");
    expect(desc).toContain("Kotlin: Extract to function (top-level or member)");
  });

  it('should return WebStorm specific description', () => {
    const desc = getExtractMethodDescription("webstorm");

    expect(desc).toContain("Best for: JavaScript, TypeScript");
    expect(desc).toContain("Handles async/await");
    expect(desc).toContain("arrow function or regular function");
  });

  it('should return PyCharm specific description', () => {
    const desc = getExtractMethodDescription("pycharm");

    expect(desc).toContain("Best for: Python");
    expect(desc).toContain("Handles generators and async functions");
    expect(desc).toContain("Preserves decorator context");
  });

  it('should return GoLand specific description', () => {
    const desc = getExtractMethodDescription("goland");

    expect(desc).toContain("Best for: Go");
    expect(desc).toContain("Handles multiple return values");
    expect(desc).toContain("receiver type for methods");
  });

  it('should return RustRover specific description', () => {
    const desc = getExtractMethodDescription("rustrover");

    expect(desc).toContain("Best for: Rust");
    expect(desc).toContain("ownership, borrowing, and lifetimes");
    expect(desc).toContain("Result/Option return types");
  });

  it('should return PhpStorm specific description', () => {
    const desc = getExtractMethodDescription("phpstorm");

    expect(desc).toContain("Best for: PHP");
    expect(desc).toContain("static and instance methods");
    expect(desc).toContain("visibility modifiers");
  });

  it('should return RubyMine specific description', () => {
    const desc = getExtractMethodDescription("rubymine");

    expect(desc).toContain("Best for: Ruby");
    expect(desc).toContain("Handles blocks and procs");
    expect(desc).toContain("method visibility");
  });

  it('should return CLion specific description', () => {
    const desc = getExtractMethodDescription("clion");

    expect(desc).toContain("Best for: C, C++");
    expect(desc).toContain("pointers and references");
    expect(desc).toContain("inline or separate function");
  });

  it('should return Rider specific description', () => {
    const desc = getExtractMethodDescription("rider");

    expect(desc).toContain("Best for: C#, F#, VB.NET");
    expect(desc).toContain("async/await and LINQ");
    expect(desc).toContain("local function or method");
  });

  it('should return Android Studio specific description', () => {
    const desc = getExtractMethodDescription("android-studio");

    expect(desc).toContain("Best for: Java, Kotlin (Android)");
    expect(desc).toContain("Same as IntelliJ IDEA");
    expect(desc).toContain("Android-specific patterns (callbacks, listeners)");
  });
});

describe('callIde', () => {
  const baseUrl = "http://127.0.0.1:8765";

  it('should make GET request when no body provided', async () => {
    const mockResponse = { success: true, message: "OK" };
    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await callIde<typeof mockResponse>(baseUrl, "/status", undefined, mockFetch);

    expect(result).toEqual(mockResponse);
    expect(mockFetch).toHaveBeenCalledWith(
      "http://127.0.0.1:8765/status",
      {
        method: "GET",
        headers: undefined,
        body: undefined,
      }
    );
  });

  it('should make POST request with JSON body when body provided', async () => {
    const mockResponse: RefactoringResult = { success: true, message: "Moved successfully" };
    const requestBody = { file: "/path/to/file.java", line: 10, column: 5, targetPackage: "com.example.new" };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await callIde<RefactoringResult>(baseUrl, "/move", requestBody, mockFetch);

    expect(result).toEqual(mockResponse);
    expect(mockFetch).toHaveBeenCalledWith(
      "http://127.0.0.1:8765/move",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody),
      }
    );
  });

  it('should throw on network error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    await expect(callIde(baseUrl, "/status", undefined, mockFetch)).rejects.toThrow("ECONNREFUSED");
  });
});

describe('Tool prefix generation', () => {
  it('should generate correct tool prefix from IDE name', () => {
    // Simulating the TOOL_PREFIX generation logic
    const getToolPrefix = (ideName: string) => ideName.toLowerCase().replace(/-/g, "_");

    expect(getToolPrefix("idea")).toBe("idea");
    expect(getToolPrefix("webstorm")).toBe("webstorm");
    expect(getToolPrefix("pycharm")).toBe("pycharm");
    expect(getToolPrefix("android-studio")).toBe("android_studio");
    expect(getToolPrefix("RustRover")).toBe("rustrover");
  });
});

describe('handleMoveTool', () => {
  const baseUrl = "http://127.0.0.1:8765";
  const toolPrefix = "idea";

  it('should return error for unknown tool name', async () => {
    const mockFetch = vi.fn();

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "wrong_tool",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Unknown tool: wrong_tool");
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('should successfully move class', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Moved 'MyClass' to 'com.example.new' package",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.example.new" },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toBe("Moved 'MyClass' to 'com.example.new' package");
  });

  it('should return error on failed move', async () => {
    const mockResponse: RefactoringResult = {
      success: false,
      message: "Target package does not exist",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.nonexistent" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Move failed: Target package does not exist");
  });

  it('should handle connection refused error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Cannot connect to IDE");
    expect(result.content[0].text).toContain(baseUrl);
    expect(result.content[0].text).toContain("Claude Tools plugin installed");
  });

  it('should handle fetch failed error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("fetch failed"));

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Cannot connect to IDE");
  });

  it('should handle generic error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("Unknown error occurred"));

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Error: Unknown error occurred");
  });

  it('should work with different IDE prefixes', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Moved successfully",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    // Test with webstorm prefix
    const result = await handleMoveTool(
      "webstorm",
      baseUrl,
      "webstorm_move",
      { file: "/path/file.ts", line: 10, column: 5, targetPackage: "./utils/helpers" },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toBe("Moved successfully");
  });

  it('should work with android_studio prefix (hyphen replaced)', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Moved class to new package",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      "android_studio",
      baseUrl,
      "android_studio_move",
      { file: "/path/MainActivity.kt", line: 5, column: 10, targetPackage: "com.example.ui" },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toBe("Moved class to new package");
  });
});

describe('handleExtractMethodTool', () => {
  const baseUrl = "http://127.0.0.1:8765";
  const toolPrefix = "idea";

  it('should return error for unknown tool name', async () => {
    const mockFetch = vi.fn();

    const result = await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "wrong_tool",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "newMethod" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Unknown tool: wrong_tool");
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('should successfully extract method', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Extracted method 'calculateTotal' with 2 parameters",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "calculateTotal" },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toBe("Extracted method 'calculateTotal' with 2 parameters");
  });

  it('should return error on failed extract', async () => {
    const mockResponse: RefactoringResult = {
      success: false,
      message: "Cannot extract: selection contains incomplete statements",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "newMethod" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Extract method failed: Cannot extract: selection contains incomplete statements");
  });

  it('should handle connection refused error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "newMethod" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Cannot connect to IDE");
    expect(result.content[0].text).toContain(baseUrl);
  });

  it('should handle fetch failed error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("fetch failed"));

    const result = await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "newMethod" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Cannot connect to IDE");
  });

  it('should handle generic error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("Timeout exceeded"));

    const result = await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "newMethod" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Error: Timeout exceeded");
  });

  it('should work with different IDE prefixes', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Extracted function 'processData'",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    // Test with pycharm prefix
    const result = await handleExtractMethodTool(
      "pycharm",
      baseUrl,
      "pycharm_extract_method",
      { file: "/path/script.py", startLine: 10, startColumn: 0, endLine: 25, endColumn: 0, methodName: "processData" },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toBe("Extracted function 'processData'");
  });

  it('should verify correct endpoint is called', async () => {
    const mockResponse: RefactoringResult = { success: true, message: "OK" };
    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      { file: "/path/file.java", startLine: 10, startColumn: 5, endLine: 20, endColumn: 10, methodName: "newMethod" },
      mockFetch
    );

    expect(mockFetch).toHaveBeenCalledWith(
      `${baseUrl}/extractMethod`,
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
      })
    );
  });

  it('should pass all parameters to the IDE', async () => {
    const mockResponse: RefactoringResult = { success: true, message: "OK" };
    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const args = {
      file: "/path/file.java",
      startLine: 10,
      startColumn: 5,
      endLine: 20,
      endColumn: 10,
      methodName: "newMethod"
    };

    await handleExtractMethodTool(
      toolPrefix,
      baseUrl,
      "idea_extract_method",
      args,
      mockFetch
    );

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        body: JSON.stringify(args),
      })
    );
  });
});

describe('Error message formatting', () => {
  const baseUrl = "http://127.0.0.1:8765";
  const toolPrefix = "idea";

  it('should format connection error with helpful message', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result.content[0].text).toMatch(/Cannot connect to IDE/);
    expect(result.content[0].text).toMatch(/Make sure the IDE is running/);
    expect(result.content[0].text).toMatch(/Claude Tools plugin installed/);
  });

  it('should include base URL in connection error message', async () => {
    const customBaseUrl = "http://127.0.0.1:8767";
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await handleMoveTool(
      "pycharm",
      customBaseUrl,
      "pycharm_move",
      { file: "/path/script.py", line: 10, column: 5, targetPackage: "myapp.utils" },
      mockFetch
    );

    expect(result.content[0].text).toContain(customBaseUrl);
  });

  it('should handle non-Error objects thrown', async () => {
    const mockFetch = vi.fn().mockRejectedValue("String error");

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Error: String error");
  });
});

describe('Tool response structure', () => {
  const baseUrl = "http://127.0.0.1:8765";
  const toolPrefix = "idea";

  it('should return proper response structure on success', async () => {
    const mockResponse: RefactoringResult = { success: true, message: "Done" };
    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result).toHaveProperty("content");
    expect(Array.isArray(result.content)).toBe(true);
    expect(result.content[0]).toHaveProperty("type", "text");
    expect(result.content[0]).toHaveProperty("text");
    expect(result.isError).toBeFalsy();
  });

  it('should return proper response structure on error', async () => {
    const mockResponse: RefactoringResult = { success: false, message: "Failed" };
    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      toolPrefix,
      baseUrl,
      "idea_move",
      { file: "/path/file.java", line: 10, column: 5, targetPackage: "com.new" },
      mockFetch
    );

    expect(result).toHaveProperty("content");
    expect(result).toHaveProperty("isError", true);
    expect(result.content[0].type).toBe("text");
  });
});

describe('Integration scenarios', () => {
  const baseUrl = "http://127.0.0.1:8765";

  it('should handle complete move workflow', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Moved 'UserService' from 'com.app.services' to 'com.app.domain.services'. Updated 15 imports.",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      "idea",
      baseUrl,
      "idea_move",
      {
        file: "/home/dev/project/src/main/java/com/app/services/UserService.java",
        line: 5,
        column: 14,
        targetPackage: "com.app.domain.services"
      },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toContain("Moved 'UserService'");
    expect(result.content[0].text).toContain("Updated 15 imports");
  });

  it('should handle complete extract method workflow', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Extracted method 'validateInput(String input, int maxLength)' returning 'boolean'. Updated 1 location.",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleExtractMethodTool(
      "idea",
      baseUrl,
      "idea_extract_method",
      {
        file: "/home/dev/project/src/main/java/com/app/Validator.java",
        startLine: 45,
        startColumn: 8,
        endLine: 55,
        endColumn: 9,
        methodName: "validateInput"
      },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toContain("Extracted method 'validateInput");
    expect(result.content[0].text).toContain("returning 'boolean'");
  });

  it('should handle TypeScript move in WebStorm', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Moved 'UserComponent' to './components/user'. Updated imports in 8 files.",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleMoveTool(
      "webstorm",
      "http://127.0.0.1:8766",
      "webstorm_move",
      {
        file: "/home/dev/webapp/src/UserComponent.tsx",
        line: 3,
        column: 7,
        targetPackage: "./components/user"
      },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toContain("Moved 'UserComponent'");
  });

  it('should handle Python extract method in PyCharm', async () => {
    const mockResponse: RefactoringResult = {
      success: true,
      message: "Extracted function 'process_data(items: List[dict]) -> List[Result]'. Parameters: items (List[dict]).",
    };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await handleExtractMethodTool(
      "pycharm",
      "http://127.0.0.1:8767",
      "pycharm_extract_method",
      {
        file: "/home/dev/project/src/data_processor.py",
        startLine: 100,
        startColumn: 4,
        endLine: 120,
        endColumn: 0,
        methodName: "process_data"
      },
      mockFetch
    );

    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toContain("Extracted function 'process_data");
  });
});

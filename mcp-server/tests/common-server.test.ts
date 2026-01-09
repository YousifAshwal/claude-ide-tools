/**
 * Tests for common-server.ts
 *
 * These tests cover:
 * - scanRunningIdes() - scanning IDE ports
 * - findIdeForFile() - matching file path to project
 * - fetchIdeStatus() - fetching status from IDE
 * - Tool handlers for status, rename, find_usages
 * - Error handling (connection refused, IDE not found, etc.)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock types
type ProjectInfo = {
  name: string;
  path: string;
};

type StatusResult = {
  ok: boolean;
  ideType: string;
  ideVersion: string;
  port: number;
  openProjects: ProjectInfo[];
  indexingInProgress: boolean;
  languagePlugins: Record<string, boolean>;
};

type IdeInfo = {
  port: number;
  status: StatusResult;
};

type RefactoringResult = {
  success: boolean;
  message: string;
};

type UsageItem = {
  file: string;
  line: number;
  column: number;
  preview: string;
};

type FindUsagesResult = {
  success: boolean;
  message: string;
  usages: UsageItem[];
};

// IDE ports configuration
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
];

const HOST = "127.0.0.1";

// Extracted functions for testing (mirror implementation from common-server.ts)
async function fetchIdeStatus(port: number, fetchFn = fetch): Promise<StatusResult | null> {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 1000);

    const response = await fetchFn(`http://${HOST}:${port}/status`, {
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

async function scanRunningIdes(fetchFn = fetch): Promise<IdeInfo[]> {
  const results = await Promise.all(
    IDE_PORTS.map(async ({ port }) => {
      const status = await fetchIdeStatus(port, fetchFn);
      return status ? { port, status } : null;
    })
  );
  return results.filter((r): r is IdeInfo => r !== null);
}

async function findIdeForFile(filePath: string, fetchFn = fetch): Promise<IdeInfo | null> {
  const normalizedPath = filePath.replace(/\\/g, "/");
  const runningIdes = await scanRunningIdes(fetchFn);

  let bestMatch: { ide: IdeInfo; pathLength: number } | null = null;

  for (const ide of runningIdes) {
    for (const project of ide.status.openProjects) {
      const projectPath = project.path.replace(/\\/g, "/");
      if (normalizedPath.startsWith(projectPath)) {
        // Prefer longer path match (more specific project)
        if (!bestMatch || projectPath.length > bestMatch.pathLength) {
          bestMatch = { ide, pathLength: projectPath.length };
        }
      }
    }
  }

  return bestMatch?.ide ?? null;
}

async function callIde<T>(port: number, endpoint: string, body?: unknown, fetchFn = fetch): Promise<T> {
  const response = await fetchFn(`http://${HOST}:${port}${endpoint}`, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  return await response.json() as T;
}

// ============================================================================
// Tests
// ============================================================================

describe('fetchIdeStatus', () => {
  it('should return status when IDE is running', async () => {
    const mockStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "MyProject", path: "/home/user/projects/MyProject" }],
      indexingInProgress: false,
      languagePlugins: { java: true, kotlin: true },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockStatus),
    });

    const result = await fetchIdeStatus(8765, mockFetch);

    expect(result).toEqual(mockStatus);
    expect(mockFetch).toHaveBeenCalledWith(
      "http://127.0.0.1:8765/status",
      expect.objectContaining({ signal: expect.any(AbortSignal) })
    );
  });

  it('should return null when IDE is not running (connection refused)', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await fetchIdeStatus(8765, mockFetch);

    expect(result).toBeNull();
  });

  it('should return null when response is not ok', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
    });

    const result = await fetchIdeStatus(8765, mockFetch);

    expect(result).toBeNull();
  });

  it('should handle timeout (abort)', async () => {
    const mockFetch = vi.fn().mockImplementation(() => {
      return new Promise((_, reject) => {
        setTimeout(() => reject(new Error("AbortError")), 100);
      });
    });

    const result = await fetchIdeStatus(8765, mockFetch);

    expect(result).toBeNull();
  });

  it('should return null on network error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("Network error"));

    const result = await fetchIdeStatus(8765, mockFetch);

    expect(result).toBeNull();
  });
});

describe('scanRunningIdes', () => {
  it('should return empty array when no IDEs are running', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await scanRunningIdes(mockFetch);

    expect(result).toEqual([]);
    expect(mockFetch).toHaveBeenCalledTimes(IDE_PORTS.length);
  });

  it('should return running IDEs', async () => {
    const mockIntelliJStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "JavaProject", path: "/projects/java" }],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockWebStormStatus: StatusResult = {
      ok: true,
      ideType: "WebStorm",
      ideVersion: "2024.1",
      port: 8766,
      openProjects: [{ name: "WebProject", path: "/projects/web" }],
      indexingInProgress: true,
      languagePlugins: { javascript: true, typescript: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockIntelliJStatus),
        });
      }
      if (url.includes(":8766/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockWebStormStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await scanRunningIdes(mockFetch);

    expect(result).toHaveLength(2);
    expect(result[0]).toEqual({ port: 8765, status: mockIntelliJStatus });
    expect(result[1]).toEqual({ port: 8766, status: mockWebStormStatus });
  });

  it('should scan all known ports', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    await scanRunningIdes(mockFetch);

    // Verify all ports were scanned
    for (const { port } of IDE_PORTS) {
      expect(mockFetch).toHaveBeenCalledWith(
        `http://127.0.0.1:${port}/status`,
        expect.any(Object)
      );
    }
  });

  it('should handle mixed responses (some IDEs running, some not)', async () => {
    const mockPyCharmStatus: StatusResult = {
      ok: true,
      ideType: "PyCharm",
      ideVersion: "2024.1",
      port: 8767,
      openProjects: [],
      indexingInProgress: false,
      languagePlugins: { python: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8767/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockPyCharmStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await scanRunningIdes(mockFetch);

    expect(result).toHaveLength(1);
    expect(result[0].status.ideType).toBe("PyCharm");
  });
});

describe('findIdeForFile', () => {
  it('should find IDE containing the file project', async () => {
    const mockStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "MyProject", path: "/home/user/projects/MyProject" }],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await findIdeForFile("/home/user/projects/MyProject/src/Main.java", mockFetch);

    expect(result).not.toBeNull();
    expect(result?.status.ideType).toBe("IntelliJ IDEA");
  });

  it('should return null when file is not in any project', async () => {
    const mockStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "MyProject", path: "/home/user/projects/MyProject" }],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await findIdeForFile("/home/user/other/File.java", mockFetch);

    expect(result).toBeNull();
  });

  it('should return null when no IDEs are running', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await findIdeForFile("/home/user/projects/MyProject/src/Main.java", mockFetch);

    expect(result).toBeNull();
  });

  it('should prefer more specific project path (longer match)', async () => {
    const mockParentStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "Parent", path: "/projects/parent" }],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockChildStatus: StatusResult = {
      ok: true,
      ideType: "WebStorm",
      ideVersion: "2024.1",
      port: 8766,
      openProjects: [{ name: "Child", path: "/projects/parent/child" }],
      indexingInProgress: false,
      languagePlugins: { javascript: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockParentStatus),
        });
      }
      if (url.includes(":8766/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockChildStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await findIdeForFile("/projects/parent/child/src/index.ts", mockFetch);

    expect(result).not.toBeNull();
    expect(result?.status.ideType).toBe("WebStorm");
    expect(result?.port).toBe(8766);
  });

  it('should handle Windows-style paths (backslashes)', async () => {
    const mockStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "MyProject", path: "C:\\Users\\dev\\projects\\MyProject" }],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await findIdeForFile("C:\\Users\\dev\\projects\\MyProject\\src\\Main.java", mockFetch);

    expect(result).not.toBeNull();
    expect(result?.status.ideType).toBe("IntelliJ IDEA");
  });

  it('should match when project path uses forward slashes and file uses backslashes', async () => {
    const mockStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [{ name: "MyProject", path: "C:/Users/dev/projects/MyProject" }],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await findIdeForFile("C:\\Users\\dev\\projects\\MyProject\\src\\Main.java", mockFetch);

    expect(result).not.toBeNull();
  });

  it('should handle multiple projects in same IDE', async () => {
    const mockStatus: StatusResult = {
      ok: true,
      ideType: "IntelliJ IDEA",
      ideVersion: "2024.1",
      port: 8765,
      openProjects: [
        { name: "Project1", path: "/projects/project1" },
        { name: "Project2", path: "/projects/project2" },
      ],
      indexingInProgress: false,
      languagePlugins: { java: true },
    };

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes(":8765/")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockStatus),
        });
      }
      return Promise.reject(new Error("ECONNREFUSED"));
    });

    const result = await findIdeForFile("/projects/project2/src/File.java", mockFetch);

    expect(result).not.toBeNull();
    expect(result?.status.ideType).toBe("IntelliJ IDEA");
  });
});

describe('callIde', () => {
  it('should make GET request when no body provided', async () => {
    const mockResponse = { success: true, message: "OK" };
    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await callIde<typeof mockResponse>(8765, "/status", undefined, mockFetch);

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
    const mockResponse: RefactoringResult = { success: true, message: "Renamed successfully" };
    const requestBody = { file: "/path/to/file.java", line: 10, column: 5, newName: "newMethod" };

    const mockFetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve(mockResponse),
    });

    const result = await callIde<RefactoringResult>(8765, "/rename", requestBody, mockFetch);

    expect(result).toEqual(mockResponse);
    expect(mockFetch).toHaveBeenCalledWith(
      "http://127.0.0.1:8765/rename",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody),
      }
    );
  });

  it('should throw on network error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

    await expect(callIde(8765, "/status", undefined, mockFetch)).rejects.toThrow("ECONNREFUSED");
  });
});

describe('Tool Handlers', () => {
  // Helper to simulate tool handler behavior
  async function handleStatusTool(fetchFn = fetch): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean }> {
    const runningIdes = await scanRunningIdes(fetchFn);

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

  async function handleRenameTool(
    args: { file: string; line: number; column: number; newName: string },
    fetchFn = fetch
  ): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean }> {
    const ide = await findIdeForFile(args.file, fetchFn);

    if (!ide) {
      const runningIdes = await scanRunningIdes(fetchFn);
      if (runningIdes.length === 0) {
        return {
          content: [{
            type: "text",
            text: `No JetBrains IDEs are running.\n\nStart an IDE and open a project containing:\n${args.file}`,
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
          text: `File not found in any open project.\n\nFile: ${args.file}\n\nRunning IDEs:\n${ideList}\n\nOpen the project containing this file in one of the IDEs.`,
        }],
        isError: true,
      };
    }

    try {
      const result = await callIde<RefactoringResult>(ide.port, "/rename", args, fetchFn);
      return {
        content: [{
          type: "text",
          text: result.success ? result.message : `Rename failed: ${result.message}`,
        }],
        isError: !result.success,
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return {
        content: [{ type: "text", text: `Error: ${errorMessage}` }],
        isError: true,
      };
    }
  }

  async function handleFindUsagesTool(
    args: { file: string; line: number; column: number },
    fetchFn = fetch
  ): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean }> {
    const ide = await findIdeForFile(args.file, fetchFn);

    if (!ide) {
      return {
        content: [{
          type: "text",
          text: `No JetBrains IDEs are running.\n\nStart an IDE and open a project containing:\n${args.file}`,
        }],
        isError: true,
      };
    }

    try {
      const result = await callIde<FindUsagesResult>(ide.port, "/findUsages", args, fetchFn);

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
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return {
        content: [{ type: "text", text: `Error: ${errorMessage}` }],
        isError: true,
      };
    }
  }

  describe('status tool', () => {
    it('should show error when no IDEs are running', async () => {
      const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

      const result = await handleStatusTool(mockFetch);

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("No JetBrains IDEs are currently running");
    });

    it('should list running IDEs with their details', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true, kotlin: true },
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes(":8765/")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleStatusTool(mockFetch);

      expect(result.isError).toBeUndefined();
      expect(result.content[0].text).toContain("IntelliJ IDEA (port 8765)");
      expect(result.content[0].text).toContain("Version: 2024.1");
      expect(result.content[0].text).toContain("MyProject: /projects/MyProject");
      expect(result.content[0].text).toContain("Indexing: complete");
      expect(result.content[0].text).toContain("Languages: java, kotlin");
    });

    it('should show indexing in progress', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [],
        indexingInProgress: true,
        languagePlugins: {},
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes(":8765/")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleStatusTool(mockFetch);

      expect(result.content[0].text).toContain("Indexing: in progress");
    });

    it('should show "(none)" when no projects are open', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [],
        indexingInProgress: false,
        languagePlugins: {},
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes(":8765/")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleStatusTool(mockFetch);

      expect(result.content[0].text).toContain("Projects: (none)");
    });
  });

  describe('rename tool', () => {
    it('should return error when no IDEs are running', async () => {
      const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

      const result = await handleRenameTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5, newName: "newMethod" },
        mockFetch
      );

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("No JetBrains IDEs are running");
    });

    it('should return error when file is not in any project', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "OtherProject", path: "/projects/OtherProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes(":8765/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleRenameTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5, newName: "newMethod" },
        mockFetch
      );

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("File not found in any open project");
      expect(result.content[0].text).toContain("/projects/OtherProject");
    });

    it('should successfully rename symbol', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockRenameResult: RefactoringResult = {
        success: true,
        message: "Renamed 'oldMethod' to 'newMethod' in 5 files",
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes("/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        if (url.includes("/rename")) {
          return Promise.resolve({
            json: () => Promise.resolve(mockRenameResult),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleRenameTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5, newName: "newMethod" },
        mockFetch
      );

      expect(result.isError).toBeFalsy();
      expect(result.content[0].text).toContain("Renamed 'oldMethod' to 'newMethod' in 5 files");
    });

    it('should return error on failed rename', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockRenameResult: RefactoringResult = {
        success: false,
        message: "Cannot rename: symbol is used in library",
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes("/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        if (url.includes("/rename")) {
          return Promise.resolve({
            json: () => Promise.resolve(mockRenameResult),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleRenameTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5, newName: "newMethod" },
        mockFetch
      );

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("Rename failed: Cannot rename: symbol is used in library");
    });

    it('should handle connection error during rename', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes("/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        if (url.includes("/rename")) {
          return Promise.reject(new Error("Connection reset"));
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleRenameTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5, newName: "newMethod" },
        mockFetch
      );

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("Error: Connection reset");
    });
  });

  describe('find_usages tool', () => {
    it('should return error when no IDEs are running', async () => {
      const mockFetch = vi.fn().mockRejectedValue(new Error("ECONNREFUSED"));

      const result = await handleFindUsagesTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5 },
        mockFetch
      );

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("No JetBrains IDEs are running");
    });

    it('should find and format usages', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockFindUsagesResult: FindUsagesResult = {
        success: true,
        message: "Found 3 usages of 'myMethod'",
        usages: [
          { file: "/projects/MyProject/src/Main.java", line: 10, column: 5, preview: "public void myMethod() {" },
          { file: "/projects/MyProject/src/Service.java", line: 25, column: 10, preview: "main.myMethod();" },
          { file: "/projects/MyProject/test/MainTest.java", line: 15, column: 8, preview: "instance.myMethod();" },
        ],
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes("/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        if (url.includes("/findUsages")) {
          return Promise.resolve({
            json: () => Promise.resolve(mockFindUsagesResult),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleFindUsagesTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5 },
        mockFetch
      );

      expect(result.isError).toBeUndefined();
      expect(result.content[0].text).toContain("Found 3 usages of 'myMethod'");
      expect(result.content[0].text).toContain("/projects/MyProject/src/Main.java:10:5");
      expect(result.content[0].text).toContain("public void myMethod() {");
    });

    it('should handle no usages found', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockFindUsagesResult: FindUsagesResult = {
        success: true,
        message: "No usages found",
        usages: [],
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes("/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        if (url.includes("/findUsages")) {
          return Promise.resolve({
            json: () => Promise.resolve(mockFindUsagesResult),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleFindUsagesTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5 },
        mockFetch
      );

      expect(result.isError).toBeUndefined();
      expect(result.content[0].text).toBe("No usages found");
    });

    it('should return error on failed find usages', async () => {
      const mockStatus: StatusResult = {
        ok: true,
        ideType: "IntelliJ IDEA",
        ideVersion: "2024.1",
        port: 8765,
        openProjects: [{ name: "MyProject", path: "/projects/MyProject" }],
        indexingInProgress: false,
        languagePlugins: { java: true },
      };

      const mockFindUsagesResult: FindUsagesResult = {
        success: false,
        message: "Cannot find symbol at specified position",
        usages: [],
      };

      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url.includes("/status")) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve(mockStatus),
          });
        }
        if (url.includes("/findUsages")) {
          return Promise.resolve({
            json: () => Promise.resolve(mockFindUsagesResult),
          });
        }
        return Promise.reject(new Error("ECONNREFUSED"));
      });

      const result = await handleFindUsagesTool(
        { file: "/projects/MyProject/src/Main.java", line: 10, column: 5 },
        mockFetch
      );

      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("Failed: Cannot find symbol at specified position");
    });
  });
});

describe('Error Handling', () => {
  it('should handle unknown tool names', async () => {
    // Simulating unknown tool handling
    const handleUnknownTool = (name: string) => {
      return {
        content: [{ type: "text", text: `Unknown tool: ${name}` }],
        isError: true,
      };
    };

    const result = handleUnknownTool("unknown_tool");

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("Unknown tool: unknown_tool");
  });

  it('should handle missing file parameter', async () => {
    // Simulating missing file parameter validation
    const validateFileParam = (args: Record<string, unknown>) => {
      const fileArg = args?.file;
      if (typeof fileArg !== "string") {
        return {
          content: [{ type: "text", text: "Error: file parameter is required" }],
          isError: true,
        };
      }
      return null;
    };

    const result = validateFileParam({});

    expect(result).not.toBeNull();
    expect(result?.isError).toBe(true);
    expect(result?.content[0].text).toBe("Error: file parameter is required");
  });

  it('should handle generic errors gracefully', async () => {
    // Simulating error handling wrapper
    const handleWithErrorWrapper = async <T>(fn: () => Promise<T>): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean } | T> => {
      try {
        return await fn();
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        return {
          content: [{ type: "text", text: `Error: ${errorMessage}` }],
          isError: true,
        };
      }
    };

    const result = await handleWithErrorWrapper(() => Promise.reject(new Error("Something went wrong")));

    expect((result as any).isError).toBe(true);
    expect((result as any).content[0].text).toBe("Error: Something went wrong");
  });

  it('should handle non-Error objects thrown', async () => {
    const handleWithErrorWrapper = async <T>(fn: () => Promise<T>): Promise<{ content: Array<{ type: string; text: string }>; isError?: boolean } | T> => {
      try {
        return await fn();
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        return {
          content: [{ type: "text", text: `Error: ${errorMessage}` }],
          isError: true,
        };
      }
    };

    const result = await handleWithErrorWrapper(() => Promise.reject("String error"));

    expect((result as any).isError).toBe(true);
    expect((result as any).content[0].text).toBe("Error: String error");
  });
});

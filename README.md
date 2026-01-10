# Claude IDE Tools

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A JetBrains IDE plugin that exposes powerful refactoring capabilities to Claude Code CLI via Model Context Protocol (MCP). Works with all major JetBrains IDEs.

## What It Does

This plugin allows Claude Code to use JetBrains IDEs' semantic code analysis instead of simple text-based grep/search. When you ask Claude to find usages of a class or rename a variable, it uses the IDE's understanding of code structure rather than plain text matching.

## Supported IDEs and Languages

| IDE | Port | Primary Languages |
|-----|------|-------------------|
| IntelliJ IDEA | 8765 | Java, Kotlin, Scala, Groovy |
| WebStorm | 8766 | JavaScript, TypeScript |
| PyCharm | 8767 | Python |
| GoLand | 8768 | Go |
| PhpStorm | 8769 | PHP |
| RubyMine | 8770 | Ruby |
| CLion | 8771 | C, C++ |
| Rider | 8772 | C#, F#, VB.NET |
| DataGrip | 8773 | SQL |
| Android Studio | 8774 | Java, Kotlin (Android) |
| RustRover | 8775 | Rust |
| Aqua | 8776 | Test automation (Java, Kotlin, Python) |
| DataSpell | 8777 | Python, Jupyter |

## Architecture

The plugin uses a **dual MCP server architecture** to provide optimal tool routing:

```
                                        ┌──────────────────────────────────────────────────────────────┐
                                        │                    JetBrains IDEs                            │
                                        │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐           │
┌─────────────────┐                     │  │   IntelliJ  │  │  WebStorm   │  │   PyCharm   │    ...    │
│   Claude Code   │                     │  │   :8765     │  │   :8766     │  │   :8767     │           │
│      CLI        │                     │  └──────▲──────┘  └──────▲──────┘  └──────▲──────┘           │
└────────┬────────┘                     └─────────│────────────────│────────────────│──────────────────┘
         │ stdio                                  │                │                │
         │                                        │ HTTP           │ HTTP           │ HTTP
         ▼                                        │                │                │
┌─────────────────────────────────────────────────┴────────────────┴────────────────┴──┐
│                               MCP Servers (Node.js)                                  │
│  ┌──────────────────────────────────────┐  ┌──────────────────────────────────────┐  │
│  │   Common Server                      │  │   IDE-Specific Servers               │  │
│  │   (claude-ide-tools)                 │  │(claude-{ide}-tools)                  │  │
│  │                                      │  │                                      │  │
│  │   Tools:                             │  │   Tools (per IDE):                   │  │
│  │   - status (all IDEs)                │  │   - {ide}_move                       │  │
│  │   - rename (auto-routes)             │  │   - {ide}_extract_method             │  │
│  │   - find_usages (auto-routes)        │  │                                      │  │
│  │                                      │  │   Examples:                          │  │
│  │   Auto-detects which IDE has         │  │   - idea_move, idea_extract_method   │  │
│  │   the file open based on project     │  │   - webstorm_move                    │  │
│  │   paths                              │  │   - pycharm_extract_method           │  │
│  └──────────────────────────────────────┘  └──────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

### Dual Server Design

**Common Server (`claude-ide-tools`)**
- Provides universal tools: `status`, `rename`, `find_usages`
- Scans all IDE ports (8765-8777) to discover running IDEs
- **Auto-routes requests** to the correct IDE based on file path
- No configuration needed - just works

**IDE-Specific Server (`claude-{ide}-tools`)**
- Provides language-specific tools: `{ide}_move`, `{ide}_extract_method`
- Tool names are prefixed with IDE name (e.g., `idea_move`, `webstorm_extract_method`)
- Contains IDE-specific documentation and behavior

### Why Two Servers?

1. **Rename/Find Usages are universal** - They work the same way across all languages, so a single tool that auto-routes is more convenient
2. **Move/Extract have language-specific semantics** - A Java "move to package" is different from a TypeScript "move to module", so separate tools with specific documentation help Claude understand the differences
3. **Multiple IDEs can run simultaneously** - You might have IntelliJ and WebStorm open on different projects

## Available Tools

### Universal Tools (auto-route to correct IDE)

| Tool | Description |
|------|-------------|
| `status` | Shows all running JetBrains IDEs, their open projects, indexing status, and available language plugins |
| `rename` | Safe semantic rename using IDE's refactoring engine. Finds and updates ALL usages across the project |
| `find_usages` | Semantic code search using IDE's index. Finds real usages, not just text matches |

### IDE-Specific Tools

| Tool Pattern | Description |
|--------------|-------------|
| `{ide}_move` | Move class/symbol to different package/module with automatic import updates |
| `{ide}_extract_method` | Extract code block into a new method/function with automatic parameter detection |

Examples of IDE-specific tool names:
- `idea_move`, `idea_extract_method` (IntelliJ IDEA)
- `webstorm_move`, `webstorm_extract_method` (WebStorm)
- `pycharm_move`, `pycharm_extract_method` (PyCharm)
- `android_studio_move`, `android_studio_extract_method` (Android Studio)

## Implementation Status

The following table shows which refactoring operations are fully implemented for each language:

| Operation | Java | Kotlin | JS/TS | Python | Go | Rust |
|-----------|:----:|:------:|:-----:|:------:|:--:|:----:|
| **rename** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **find_usages** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **move** | ✅ | ✅ | ⚠️ | ⚠️ | ⚠️ | ⚠️ |
| **extract_method** | ✅ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ |

**Legend:**
- ✅ Fully implemented - works programmatically via MCP
- ⚠️ Requires IDE UI - returns helpful error message directing user to use IDE

### Notes

**rename** and **find_usages** work universally across all languages because they use IntelliJ Platform's base PSI infrastructure.

**move** is fully implemented for:
- **Java**: Uses `MoveClassesOrPackagesProcessor` to move classes between packages
- **Kotlin**: Uses `MoveFilesOrDirectoriesProcessor` to move files, automatically updating package declarations and imports

**extract_method** is fully implemented for:
- **Java**: Uses `ExtractMethodProcessor` which supports headless operation

For other languages (JS/TS, Python, Go, Rust), these operations require editor context (text selection in the IDE) and cannot be performed programmatically. The plugin returns informative messages directing users to use the IDE's built-in refactoring UI (Ctrl+Alt+M for extract method, F6 for move).

### Optional Parameters

**rename** supports optional parameters:
- `searchInComments` (boolean, default: false) - Also rename occurrences in comments
- `searchTextOccurrences` (boolean, default: false) - Also rename text occurrences in string literals

**move** supports optional parameters:
- `searchInComments` (boolean, default: false) - Also update occurrences in comments
- `searchInNonJavaFiles` (boolean, default: false) - Also update occurrences in non-Java files (XML, properties, etc.)

## Installation

### From Disk

1. Download or build `claude-ide-tools-*.zip` from `build/distributions/`

2. In your JetBrains IDE:
   - Go to **Settings** -> **Plugins** -> **Gear icon** -> **Install Plugin from Disk...**
   - Select the `.zip` file
   - Restart IDE

3. After restart, the plugin will automatically:
   - Start HTTP server on the appropriate port for your IDE
   - Extract MCP servers to your home directory
   - Register both servers in Claude Code config

4. Restart Claude Code CLI to pick up the new MCP servers

### Verification

Run in Claude Code:
```
claude mcp list
```

Should show both servers:
```
claude-ide-tools: node ~/.claude-ide-tools/common-server.js - Connected
claude-idea-tools: node ~/.claude-idea-tools/ide-server.js 8765 idea - Connected
```

## Configuration

### Port Assignments

Each IDE uses a fixed port (see table in "Supported IDEs and Languages"). This allows multiple IDEs to run simultaneously.

### Config File Locations

The plugin auto-registers in Claude Code config. It searches these locations (in order):

1. `~/.claude.json`
2. `~/.claude/claude.json`
3. `~/AppData/Roaming/Claude/claude_desktop_config.json` (Windows)
4. `~/.config/claude-code/config.json` (Linux)

Example config after registration:
```json
{
  "mcpServers": {
    "claude-ide-tools": {
      "command": "node",
      "args": ["~/.claude-ide-tools/common-server.js"]
    },
    "claude-idea-tools": {
      "command": "node",
      "args": ["~/.claude-idea-tools/ide-server.js", "8765", "idea"]
    }
  }
}
```

### MCP Server Installation Directories

- Common server: `~/.claude-ide-tools/`
- IDE-specific servers: `~/.claude-{ide}-tools/` (e.g., `~/.claude-idea-tools/`, `~/.claude-webstorm-tools/`)

## Usage

Once installed, Claude Code will automatically prefer IDE tools for code operations.

**Examples:**

```
# Find all usages of a class (auto-routes to correct IDE)
"Find all usages of PsiLocatorService"

# Rename a method (auto-routes based on file path)
"Rename the handleRequest method to processRequest"

# Move class to different package (uses IDE-specific tool)
"Move UserService to com.example.services package"

# Extract method (uses IDE-specific tool)
"Extract lines 50-65 from processData into a new method called validateInput"
```

Claude will use `find_usages` instead of grep, `rename` instead of find-replace, etc.

## Development

### Prerequisites

- JDK 17+
- Node.js 18+
- Gradle (wrapper included)

### Build Commands

```bash
# Full build (includes MCP servers)
./gradlew buildPlugin

# Build MCP servers only
./gradlew buildMcpServer

# Run tests
./gradlew test

# Clean build
./gradlew clean buildPlugin
```

The built plugin will be at `build/distributions/claude-ide-tools-*.zip`

### Project Structure

```
├── src/main/kotlin/com/igorlink/claudeidetools/
│   ├── McpPluginStartup.kt              # Plugin entry point
│   ├── server/
│   │   └── McpHttpServer.kt             # HTTP server (Ktor)
│   ├── handlers/
│   │   ├── StatusHandler.kt             # Status/health check
│   │   ├── RenameHandler.kt             # Rename refactoring
│   │   ├── FindUsagesHandler.kt         # Find usages
│   │   ├── MoveHandler.kt               # Move class/symbol
│   │   ├── ExtractMethodHandler.kt      # Extract method
│   │   └── languages/                   # Language-specific handlers
│   │       ├── KotlinMoveHandler.kt
│   │       ├── JavaScriptMoveHandler.kt
│   │       └── ...
│   ├── services/
│   │   ├── PsiLocatorService.kt         # PSI element lookup
│   │   └── McpAutoRegistrationService.kt  # Auto-setup
│   └── util/
│       ├── IdeDetector.kt               # IDE type detection
│       ├── LanguageDetector.kt          # Language plugin detection
│       └── RefactoringExecutor.kt       # Safe refactoring execution
│
├── mcp-server/                          # Node.js MCP servers
│   ├── src/
│   │   ├── common-server.ts             # Universal tools (status, rename, find_usages)
│   │   └── ide-server.ts                # IDE-specific tools (move, extract_method)
│   └── dist/
│       ├── common-server.cjs            # Bundled output
│       └── ide-server.cjs               # Bundled output
│
├── src/main/resources/
│   ├── META-INF/plugin.xml              # Plugin descriptor
│   └── mcp-server/                      # Bundled MCP servers
│       ├── common-server.js
│       └── ide-server.js
│
└── build.gradle.kts                     # Build config with MCP server integration
```

### Development Workflow

1. Make changes to MCP servers (`mcp-server/src/`)
2. Make changes to IntelliJ plugin (`src/main/kotlin/`)
3. Run `./gradlew buildPlugin` - this will:
   - Build MCP servers (`npm run build`)
   - Copy bundled servers to plugin resources
   - Build IntelliJ plugin
4. Install plugin from `build/distributions/`
5. Restart IDE
6. Restart Claude Code CLI

### Updating MCP Server Version

When making changes to MCP servers, update the version in `McpAutoRegistrationService.kt`:

```kotlin
const val CURRENT_VERSION = "0.3.x" // Increment this
```

This ensures the plugin will overwrite the old server files on IDE restart.

## CI/CD

The project uses GitHub Actions for continuous integration and deployment.

### Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| **Verify Plugin Compatibility** | Pull requests | Tests plugin against all supported IDEs |
| **Auto Release** | PR merge to `master` with `release` label | Creates tag, builds, publishes to Marketplace |

### Release Process

The project follows a branching model with `dev` for development and `master` for releases.

#### 1. Development on `dev` branch

```bash
git checkout dev
# Make changes...
git add .
git commit -m "Description of changes"
git push origin dev
```

#### 2. Prepare release

Before creating a release PR:

1. **Update version** in `build.gradle.kts`:
   ```kotlin
   version = "0.3.20"
   ```

2. **Update CHANGELOG.md** with the new version:
   ```markdown
   ## [0.3.20] - 2025-01-11

   ### Added
   - New feature description

   ### Fixed
   - Bug fix description
   ```

> **Note:** Only add versions to CHANGELOG that will be released. Consolidate all dev changes into a single entry.

#### 3. Create Pull Request with `release` label

On GitHub, create a PR: `dev` → `master` and add the **`release`** label.

#### 4. Merge and Automatic Release

When the PR is merged, the `auto-release` workflow automatically:
1. Extracts version from `build.gradle.kts`
2. Creates git tag `v{version}`
3. Builds the plugin
4. Publishes to **JetBrains Marketplace**
5. Creates **GitHub Release** with changelog and plugin zip

#### 5. Increment version for next development

After release, immediately increment the version in `dev` branch for the next release cycle:

```bash
git checkout dev
git pull origin master
# Update version in build.gradle.kts to next version (e.g., 0.3.21)
git commit -am "Bump version for next development cycle"
git push origin dev
```

This prevents accidentally releasing the same version twice.

### Plugin Verification

A separate workflow verifies plugin compatibility against all supported IDEs:

| IDE | Code |
|-----|------|
| IntelliJ IDEA Community | IC |
| IntelliJ IDEA Ultimate | IU |
| WebStorm | WS |
| PhpStorm | PS |
| PyCharm Professional | PY |
| PyCharm Community | PC |
| GoLand | GO |
| RubyMine | RM |
| CLion | CL |
| Rider | RD |
| RustRover | RR |
| Android Studio | AS |

**When it runs:**
- Automatically on PRs to `master`
- Manually via Actions → "Verify Plugin Compatibility" → Run workflow

**Features:**
- Each IDE runs in a separate job (avoids disk space issues)
- All IDEs are checked even if some fail
- Summary table with results at the end

**Local verification:**
```bash
# Verify against specific IDE
./gradlew verifyPlugin -Pverify.ide.type=WS -Pverify.ide.version=2024.1
```

### Setup Requirements

To enable automatic releases:

1. **Create `release` label** in your GitHub repository:
   - Go to **Issues** → **Labels** → **New label**
   - Name: `release`, Color: any (e.g., green)

2. **Add JetBrains Marketplace token**:
   - Get your token from [JetBrains Marketplace](https://plugins.jetbrains.com/author/me/tokens)
   - Go to GitHub repo → **Settings** → **Secrets and variables** → **Actions**
   - Add new secret: `JETBRAINS_MARKETPLACE_TOKEN` with your token value

## Troubleshooting

### "Cannot connect to IDE"

1. Check if IDE is running
2. Check if plugin is installed: **Settings** -> **Plugins** -> search "Claude IDE Tools"
3. Check HTTP server: `curl http://localhost:8765/status` (use appropriate port for your IDE)

### "File not found in any open project"

1. Make sure the file is in a project that is open in an IDE
2. Check which projects are open: use the `status` tool
3. The file path must be an absolute path

### Tools not appearing in Claude Code

1. Restart Claude Code CLI
2. Check MCP servers: `claude mcp list`
3. Check config file: `cat ~/.claude.json`

### Old server version still in use

1. Delete the server directories:
   - `rm -rf ~/.claude-ide-tools`
   - `rm -rf ~/.claude-{ide}-tools`
2. Restart IDE to reinstall
3. Restart Claude Code CLI

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

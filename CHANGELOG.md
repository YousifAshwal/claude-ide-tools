# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.18] - 2025-01-10

### Fixed
- **MCP Server Cleanup on Uninstall**: Fixed issue where MCP servers remained registered in Claude Code after plugin uninstall
- Now uses `claude mcp remove` CLI command instead of direct JSON file editing
- Registration now uses `claude mcp add` CLI command for proper integration with Claude Code

### Changed
- Removed direct JSON config file manipulation in favor of Claude Code CLI
- Simplified `McpAutoRegistrationService` by removing unused JSON handling methods

## [0.3.16] - 2025-01-10

### Fixed
- **WebStorm/PhpStorm Compatibility**: Fixed 22 compatibility issues where Java-specific classes caused `ClassNotFoundException` in IDEs without Java plugin
- Extracted Java-specific code into separate handlers (`JavaMoveHandler`, `JavaExtractMethodHandler`)
- Java plugin is now truly optional - works in WebStorm, PhpStorm without Java support

### Changed
- `MoveHandler` and `ExtractMethodHandler` no longer have Java-specific imports
- `KotlinMoveHandler` uses reflection instead of direct `JavaPsiFacade` imports
- `PluginAvailability` now treats Java as optional plugin (requires class loading check)

### Added
- Design decision documentation explaining direct imports vs reflection approach
- `SourceRootDetector.determineSourceRootType(PsiElement)` overload to reduce code duplication

## [0.3.15] - 2025-01-09

### Fixed
- Made Java plugin optional for WebStorm compatibility
- Removed `untilBuild` constraint for future IDE compatibility

## [0.3.14] - 2025-01-09

### Fixed
- Added Kotlin K2 compatibility declaration (`supportsK2="true"`)

## [0.3.13] - 2025-01-09

### Changed
- Renamed from "JetBrains" to "IDE" in all public-facing names (trademark policy compliance)
- Plugin ID: `com.igorlink.claudeidetools`
- MCP servers: `claude-ide-tools`, `claude-idea-tools`, `claude-webstorm-tools`

## [0.3.12] - 2025-01-09

### Added
- GitHub Actions CI/CD pipeline
- Automatic release to JetBrains Marketplace on tag push
- Branch builds with test execution

## [0.3.10] - 2025-01-08

### Added
- Unified error handling with typed error codes
- `LanguageHandlerRegistry` for centralized routing
- `ClassLoadingStrategy` injection for testability

### Fixed
- Plugin availability detection (LinkageError handling)
- Thread safety improvements (`@Volatile` for cached values)

## [0.3.9] - 2025-01-07

### Added
- Conditional tool registration based on available language plugins
- `IdeDetector` and `LanguageDetector` tests

## [0.3.8] - 2025-01-06

### Added
- Initial public release
- Rename refactoring (classes, methods, variables)
- Find usages across project
- Move class/package refactoring
- Extract method refactoring
- Auto-registration in Claude Code config

[Unreleased]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.16...HEAD
[0.3.16]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.15...v0.3.16
[0.3.15]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.14...v0.3.15
[0.3.14]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.13...v0.3.14
[0.3.13]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.12...v0.3.13
[0.3.12]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.10...v0.3.12
[0.3.10]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.9...v0.3.10
[0.3.9]: https://github.com/AiryLark/claude-ide-tools/compare/v0.3.8...v0.3.9
[0.3.8]: https://github.com/AiryLark/claude-ide-tools/releases/tag/v0.3.8

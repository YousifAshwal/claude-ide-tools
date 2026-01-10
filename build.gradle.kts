plugins {
    id("org.jetbrains.intellij.platform") version "2.10.5"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
}

group = "com.igorlink"
version = "0.3.16"

/**
 * Extracts changelog entries from CHANGELOG.md and converts to HTML for JetBrains Marketplace.
 * Reads the last 3 versions from the changelog file.
 */
fun extractChangelogHtml(): String {
    val changelogFile = file("CHANGELOG.md")
    if (!changelogFile.exists()) {
        return "<p>See <a href=\"https://github.com/AiryLark/claude-ide-tools/blob/master/CHANGELOG.md\">CHANGELOG.md</a> for details.</p>"
    }

    val lines = changelogFile.readLines()
    val result = StringBuilder()
    var versionsFound = 0
    var inVersion = false
    var inList = false

    for (line in lines) {
        // Match version headers like "## [0.3.16] - 2025-01-10"
        if (line.startsWith("## [") && !line.contains("[Unreleased]")) {
            if (versionsFound >= 3) break

            // Close previous list if open
            if (inList) {
                result.append("</ul>\n")
                inList = false
            }

            inVersion = true
            versionsFound++
            val currentVersion = line.substringAfter("## [").substringBefore("]")
            result.append("<h3>$currentVersion</h3>\n")
        } else if (inVersion) {
            when {
                line.startsWith("### ") -> {
                    // Close previous list if open
                    if (inList) {
                        result.append("</ul>\n")
                        inList = false
                    }
                    val section = line.substringAfter("### ")
                    result.append("<b>$section</b>\n<ul>\n")
                    inList = true
                }
                line.startsWith("- ") -> {
                    val item = line.substringAfter("- ")
                        // Convert markdown to HTML using regex for proper pairing
                        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
                        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
                    result.append("  <li>$item</li>\n")
                }
            }
        }
    }

    // Close any open list tag
    if (inList) {
        result.append("</ul>\n")
    }

    return result.toString().ifEmpty {
        "<p>See <a href=\"https://github.com/AiryLark/claude-ide-tools/blob/master/CHANGELOG.md\">CHANGELOG.md</a> for details.</p>"
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
    }

    // Ktor HTTP server (excluding SLF4J to avoid classloader conflicts with IntelliJ)
    implementation(platform("io.ktor:ktor-bom:2.3.12"))
    implementation("io.ktor:ktor-server-core") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-server-netty") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-server-content-negotiation") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json") {
        exclude(group = "org.slf4j")
    }

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // JUnit Vintage for running JUnit 3/4 tests (IntelliJ Platform Test Framework)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.ktor:ktor-server-test-host") {
        exclude(group = "org.slf4j")
    }
    testImplementation("io.ktor:ktor-client-content-negotiation") {
        exclude(group = "org.slf4j")
    }
}

kotlin {
    jvmToolchain(17)
}

// Globally exclude SLF4J to prevent classloader conflicts with IntelliJ platform
configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-api")
    exclude(group = "org.slf4j", module = "slf4j-simple")
    exclude(group = "ch.qos.logback", module = "logback-classic")
}

intellijPlatform {
    pluginConfiguration {
        name = "Claude IDE Tools"
        version = project.version.toString()
        description = """
            <p>Exposes JetBrains IDE refactoring capabilities to Claude Code CLI via Model Context Protocol (MCP).</p>

            <h3>Features</h3>
            <ul>
                <li><b>Semantic Rename</b> - Safely rename classes, methods, variables with automatic usage updates</li>
                <li><b>Find Usages</b> - Find real code usages, not just text matches</li>
                <li><b>Move Refactoring</b> - Move classes/files between packages with import updates</li>
                <li><b>Extract Method</b> - Extract code blocks into new methods with parameter inference</li>
            </ul>

            <h3>How It Works</h3>
            <p>The plugin starts an HTTP server and registers MCP servers in Claude Code config.
            Claude then uses IDE's semantic understanding instead of text-based grep/search.</p>

            <h3>Supported Languages</h3>
            <ul>
                <li>Java, Kotlin (full support)</li>
                <li>JavaScript, TypeScript, Python, Go, Rust (rename/find usages)</li>
            </ul>

            <p><a href="https://github.com/AiryLark/claude-ide-tools">GitHub Repository</a></p>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "241"
        }

        vendor {
            name = "Igor Link"
            url = "https://github.com/AiryLark"
        }

        // Changelog is read from CHANGELOG.md file
        changeNotes = extractChangelogHtml()
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.1")
        }
    }
}

// MCP Server build configuration
val mcpServerDir = file("mcp-server")
val mcpServerResourcesDir = file("src/main/resources/mcp-server")
val commonServerOutput = file("mcp-server/dist/common-server.cjs")
val ideServerOutput = file("mcp-server/dist/ide-server.cjs")
val commonServerTarget = file("src/main/resources/mcp-server/common-server.js")
val ideServerTarget = file("src/main/resources/mcp-server/ide-server.js")

tasks {
    // Build MCP servers (npm build) and copy to resources
    val buildMcpServer by registering(Exec::class) {
        group = "build"
        description = "Build MCP servers and copy to plugin resources"

        workingDir = mcpServerDir

        // Use npm.cmd on Windows, npm on Unix
        val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
        commandLine(npmCommand, "run", "build")

        doLast {
            // Copy common server
            if (commonServerOutput.exists()) {
                commonServerOutput.copyTo(commonServerTarget, overwrite = true)
                logger.lifecycle("Common MCP server copied to ${commonServerTarget.relativeTo(projectDir)}")
            } else {
                throw GradleException("Common MCP server build failed: ${commonServerOutput} not found")
            }

            // Copy IDE-specific server
            if (ideServerOutput.exists()) {
                ideServerOutput.copyTo(ideServerTarget, overwrite = true)
                logger.lifecycle("IDE MCP server copied to ${ideServerTarget.relativeTo(projectDir)}")
            } else {
                throw GradleException("IDE MCP server build failed: ${ideServerOutput} not found")
            }
        }
    }

    // Copy MCP servers only (without rebuild)
    val copyMcpServer by registering {
        group = "build"
        description = "Copy pre-built MCP servers to plugin resources"

        doLast {
            if (!commonServerOutput.exists() || !ideServerOutput.exists()) {
                throw GradleException("MCP servers not built. Run 'buildMcpServer' first.")
            }
            commonServerOutput.copyTo(commonServerTarget, overwrite = true)
            ideServerOutput.copyTo(ideServerTarget, overwrite = true)
        }
    }

    // Generate version file for runtime access
    val generateVersionFile by registering {
        group = "build"
        description = "Generate version.properties file"

        val versionFile = file("src/main/resources/version.properties")
        outputs.file(versionFile)

        doLast {
            versionFile.writeText("version=${project.version}\n")
            logger.lifecycle("Generated version.properties with version ${project.version}")
        }
    }

    // Include MCP server build in processResources
    processResources {
        dependsOn(buildMcpServer)
        dependsOn(generateVersionFile)
    }

    buildSearchableOptions {
        enabled = false
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

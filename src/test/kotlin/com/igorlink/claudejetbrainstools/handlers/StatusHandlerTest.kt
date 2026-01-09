package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.ProjectInfo
import com.igorlink.claudejetbrainstools.util.IdeDetector
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for StatusHandler.
 *
 * Tests cover:
 * 1. IDE version extraction from ApplicationInfo
 * 2. Open projects list retrieval from ProjectManager
 * 3. Indexing status detection via DumbService
 * 4. Edge cases: empty projects, disposed projects, multiple projects
 */
class StatusHandlerTest {

    private lateinit var mockProjectManager: ProjectManager
    private lateinit var mockApplicationInfo: ApplicationInfo

    @BeforeEach
    fun setUp() {
        mockProjectManager = mockk()
        mockApplicationInfo = mockk()

        mockkStatic(ProjectManager::class)
        mockkStatic(ApplicationInfo::class)

        every { ProjectManager.getInstance() } returns mockProjectManager
        every { ApplicationInfo.getInstance() } returns mockApplicationInfo

        // Default mocks for IdeDetector (uses ApplicationInfo methods)
        every { mockApplicationInfo.fullApplicationName } returns "IntelliJ IDEA"
        every { mockApplicationInfo.versionName } returns "2024.1"

        // Clear IdeDetector cache before each test
        IdeDetector.clearCache()
    }

    @AfterEach
    fun tearDown() {
        IdeDetector.clearCache()
        unmockkAll()
    }

    // ==================== IDE Version Tests ====================

    @Nested
    inner class IdeVersionTest {

        @Test
        fun `extracts IDE version from ApplicationInfo`() {
            val expectedVersion = "IntelliJ IDEA 2024.1.4"
            every { mockApplicationInfo.fullVersion } returns expectedVersion
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertEquals(expectedVersion, response.ideVersion)
        }

        @Test
        fun `handles version with build number`() {
            val expectedVersion = "IntelliJ IDEA 2024.1.4 Build #IC-241.17890.1"
            every { mockApplicationInfo.fullVersion } returns expectedVersion
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertEquals(expectedVersion, response.ideVersion)
        }

        @Test
        fun `handles empty version string`() {
            every { mockApplicationInfo.fullVersion } returns ""
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertEquals("", response.ideVersion)
        }

        @Test
        fun `handles different IDE types`() {
            val pycharmVersion = "PyCharm 2024.1 Professional Edition"
            every { mockApplicationInfo.fullVersion } returns pycharmVersion
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertEquals(pycharmVersion, response.ideVersion)
        }
    }

    // ==================== Open Projects Tests ====================

    @Nested
    inner class OpenProjectsTest {

        @Test
        fun `returns empty list when no projects are open`() {
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertTrue(response.openProjects.isEmpty())
        }

        @Test
        fun `returns single project name when one project is open`() {
            val mockProject = createMockProject("TestProject", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = false)

            val response = StatusHandler.handle()

            assertEquals(listOf(ProjectInfo("TestProject", "/path/to/TestProject")), response.openProjects)
        }

        @Test
        fun `returns multiple project names when multiple projects are open`() {
            val project1 = createMockProject("Project1", isDisposed = false)
            val project2 = createMockProject("Project2", isDisposed = false)
            val project3 = createMockProject("Project3", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(project1, project2, project3)
            mockDumbService(project1, isDumb = false)
            mockDumbService(project2, isDumb = false)
            mockDumbService(project3, isDumb = false)

            val response = StatusHandler.handle()

            assertEquals(3, response.openProjects.size)
            assertEquals("Project1", response.openProjects[0].name)
            assertEquals("Project2", response.openProjects[1].name)
            assertEquals("Project3", response.openProjects[2].name)
        }

        @Test
        fun `filters out disposed projects from the list`() {
            val activeProject = createMockProject("ActiveProject", isDisposed = false)
            val disposedProject = createMockProject("DisposedProject", isDisposed = true)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(activeProject, disposedProject)
            mockDumbService(activeProject, isDumb = false)

            val response = StatusHandler.handle()

            assertEquals(listOf(ProjectInfo("ActiveProject", "/path/to/ActiveProject")), response.openProjects)
            assertFalse(response.openProjects.any { it.name == "DisposedProject" })
        }

        @Test
        fun `handles all projects being disposed`() {
            val disposed1 = createMockProject("Disposed1", isDisposed = true)
            val disposed2 = createMockProject("Disposed2", isDisposed = true)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(disposed1, disposed2)

            val response = StatusHandler.handle()

            assertTrue(response.openProjects.isEmpty())
        }

        @Test
        fun `preserves project names with special characters`() {
            val projectName = "My Project (v2.0) - Test"
            val mockProject = createMockProject(projectName, isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = false)

            val response = StatusHandler.handle()

            assertEquals(1, response.openProjects.size)
            assertEquals(projectName, response.openProjects[0].name)
        }

        @Test
        fun `handles project names with unicode characters`() {
            val projectName = "Проект_Тест_日本語"
            val mockProject = createMockProject(projectName, isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = false)

            val response = StatusHandler.handle()

            assertEquals(1, response.openProjects.size)
            assertEquals(projectName, response.openProjects[0].name)
        }
    }

    // ==================== Indexing Status Tests ====================

    @Nested
    inner class IndexingStatusTest {

        @Test
        fun `returns false when no projects are open`() {
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertFalse(response.indexingInProgress)
        }

        @Test
        fun `returns false when single project is not indexing`() {
            val mockProject = createMockProject("Project", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = false)

            val response = StatusHandler.handle()

            assertFalse(response.indexingInProgress)
        }

        @Test
        fun `returns true when single project is indexing`() {
            val mockProject = createMockProject("Project", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = true)

            val response = StatusHandler.handle()

            assertTrue(response.indexingInProgress)
        }

        @Test
        fun `returns true when at least one project is indexing`() {
            val project1 = createMockProject("Project1", isDisposed = false)
            val project2 = createMockProject("Project2", isDisposed = false)
            val project3 = createMockProject("Project3", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(project1, project2, project3)
            mockDumbService(project1, isDumb = false)
            mockDumbService(project2, isDumb = true)
            mockDumbService(project3, isDumb = false)

            val response = StatusHandler.handle()

            assertTrue(response.indexingInProgress)
        }

        @Test
        fun `returns false when all projects are not indexing`() {
            val project1 = createMockProject("Project1", isDisposed = false)
            val project2 = createMockProject("Project2", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(project1, project2)
            mockDumbService(project1, isDumb = false)
            mockDumbService(project2, isDumb = false)

            val response = StatusHandler.handle()

            assertFalse(response.indexingInProgress)
        }

        @Test
        fun `ignores indexing status of disposed projects`() {
            val activeProject = createMockProject("Active", isDisposed = false)
            val disposedProject = createMockProject("Disposed", isDisposed = true)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(activeProject, disposedProject)
            mockDumbService(activeProject, isDumb = false)
            // Note: DumbService should not be called for disposed project due to short-circuit evaluation

            val response = StatusHandler.handle()

            assertFalse(response.indexingInProgress)
        }

        @Test
        fun `disposed project with dumb mode does not affect result`() {
            // Edge case: disposed project that was indexing before disposal
            val disposedProject = createMockProject("Disposed", isDisposed = true)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(disposedProject)
            // DumbService should not be called for disposed project

            val response = StatusHandler.handle()

            assertFalse(response.indexingInProgress)
        }
    }

    // ==================== Response Structure Tests ====================

    @Nested
    inner class ResponseStructureTest {

        @Test
        fun `response always has ok set to true`() {
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns emptyArray()

            val response = StatusHandler.handle()

            assertTrue(response.ok)
        }

        @Test
        fun `response contains all required fields`() {
            val mockProject = createMockProject("TestProject", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1.4"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = true)

            val response = StatusHandler.handle()

            assertTrue(response.ok)
            assertEquals("2024.1.4", response.ideVersion)
            assertEquals(1, response.openProjects.size)
            assertEquals("TestProject", response.openProjects[0].name)
            assertTrue(response.indexingInProgress)
        }

        @Test
        fun `response is consistent across multiple calls`() {
            val mockProject = createMockProject("Project", isDisposed = false)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = false)

            val response1 = StatusHandler.handle()
            val response2 = StatusHandler.handle()

            assertEquals(response1, response2)
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCasesTest {

        @Test
        fun `handles large number of open projects`() {
            val projects = (1..100).map { createMockProject("Project$it", isDisposed = false) }
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns projects.toTypedArray()
            projects.forEach { mockDumbService(it, isDumb = false) }

            val response = StatusHandler.handle()

            assertEquals(100, response.openProjects.size)
            assertTrue(response.openProjects.any { it.name == "Project1" })
            assertTrue(response.openProjects.any { it.name == "Project100" })
        }

        @Test
        fun `handles projects with empty names`() {
            val mockProject = createMockProject("", isDisposed = false, basePath = "/path/to/empty")
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(mockProject)
            mockDumbService(mockProject, isDumb = false)

            val response = StatusHandler.handle()

            assertEquals(1, response.openProjects.size)
            assertEquals("", response.openProjects[0].name)
        }

        @Test
        fun `handles mixed disposed and active projects with indexing`() {
            val active1 = createMockProject("Active1", isDisposed = false)
            val disposed1 = createMockProject("Disposed1", isDisposed = true)
            val active2 = createMockProject("Active2", isDisposed = false)
            val disposed2 = createMockProject("Disposed2", isDisposed = true)
            every { mockApplicationInfo.fullVersion } returns "2024.1"
            every { mockProjectManager.openProjects } returns arrayOf(active1, disposed1, active2, disposed2)
            mockDumbService(active1, isDumb = false)
            mockDumbService(active2, isDumb = true)

            val response = StatusHandler.handle()

            assertEquals(2, response.openProjects.size)
            assertEquals("Active1", response.openProjects[0].name)
            assertEquals("Active2", response.openProjects[1].name)
            assertTrue(response.indexingInProgress)
        }
    }

    // ==================== Helper Functions ====================

    private fun createMockProject(name: String, isDisposed: Boolean, basePath: String = "/path/to/$name"): Project {
        return mockk<Project>().apply {
            every { this@apply.name } returns name
            every { this@apply.isDisposed } returns isDisposed
            every { this@apply.basePath } returns basePath
        }
    }

    private fun mockDumbService(project: Project, isDumb: Boolean) {
        val mockDumbService = mockk<DumbService>()
        every { mockDumbService.isDumb } returns isDumb
        mockkStatic(DumbService::class)
        every { DumbService.getInstance(project) } returns mockDumbService
    }
}

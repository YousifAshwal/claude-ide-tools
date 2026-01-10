package com.igorlink.claudeidetools.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for SourceRootDetector.
 * Tests the detection of source root types for files in IntelliJ projects.
 */
class SourceRootDetectorTest {

    private lateinit var mockProject: Project
    private lateinit var mockVirtualFile: VirtualFile
    private lateinit var mockProjectRootManager: ProjectRootManager
    private lateinit var mockFileIndex: ProjectFileIndex

    @BeforeEach
    fun setUp() {
        mockProject = mockk(relaxed = true)
        mockVirtualFile = mockk(relaxed = true)
        mockProjectRootManager = mockk(relaxed = true)
        mockFileIndex = mockk(relaxed = true)

        // Mock ProjectRootManager.getInstance to return our mock
        mockkStatic(ProjectRootManager::class)
        every { ProjectRootManager.getInstance(mockProject) } returns mockProjectRootManager
        every { mockProjectRootManager.fileIndex } returns mockFileIndex

        // Mock ReadAction.compute to execute the computable immediately
        mockkStatic(ReadAction::class)
        @Suppress("UNCHECKED_CAST")
        every { ReadAction.compute<SourceRootType, Throwable>(any()) } answers {
            // The argument is a ThrowableComputable, invoke compute() via reflection
            val computable = firstArg<Any>()
            val computeMethod = computable.javaClass.getMethod("compute")
            computeMethod.invoke(computable) as SourceRootType
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== SourceRootType Enum Tests ====================

    @Nested
    inner class SourceRootTypeEnumTests {

        @Test
        fun `SourceRootType enum has MAIN value`() {
            // Assert
            assertNotNull(SourceRootType.MAIN)
            assertEquals("MAIN", SourceRootType.MAIN.name)
        }

        @Test
        fun `SourceRootType enum has TEST value`() {
            // Assert
            assertNotNull(SourceRootType.TEST)
            assertEquals("TEST", SourceRootType.TEST.name)
        }

        @Test
        fun `SourceRootType enum has NONE value`() {
            // Assert
            assertNotNull(SourceRootType.NONE)
            assertEquals("NONE", SourceRootType.NONE.name)
        }

        @Test
        fun `SourceRootType enum has exactly three values`() {
            // Assert
            val values = SourceRootType.entries
            assertEquals(3, values.size)
            assertTrue(values.contains(SourceRootType.MAIN))
            assertTrue(values.contains(SourceRootType.TEST))
            assertTrue(values.contains(SourceRootType.NONE))
        }
    }

    // ==================== determineSourceRootType Tests ====================

    @Nested
    inner class DetermineSourceRootTypeTests {

        @Test
        fun `determineSourceRootType returns MAIN for main source content`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns false
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns true

            // Act
            val result = SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert
            assertEquals(SourceRootType.MAIN, result)
        }

        @Test
        fun `determineSourceRootType returns TEST for test source content`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns true
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns true

            // Act
            val result = SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert
            assertEquals(SourceRootType.TEST, result)
        }

        @Test
        fun `determineSourceRootType returns NONE for non-source files`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns false
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns false

            // Act
            val result = SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert
            assertEquals(SourceRootType.NONE, result)
        }

        @Test
        fun `determineSourceRootType prioritizes TEST over MAIN when file is in test source`() {
            // Arrange - file is both in source content and test source content
            // This tests that test check happens first
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns true
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns true

            // Act
            val result = SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert - TEST should take priority
            assertEquals(SourceRootType.TEST, result)
        }

        @Test
        fun `determineSourceRootType queries ProjectRootManager with correct project`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns false
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns false

            // Act
            SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert
            verify { ProjectRootManager.getInstance(mockProject) }
        }

        @Test
        fun `determineSourceRootType uses ReadAction for thread safety`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns false
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns true

            // Act
            SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert
            verify { ReadAction.compute<SourceRootType, Throwable>(any()) }
        }

        @Test
        fun `determineSourceRootType checks test source before main source`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns false
            every { mockFileIndex.isInSourceContent(mockVirtualFile) } returns true

            // Act
            SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert - Verify order of checks: test first, then source
            verifyOrder {
                mockFileIndex.isInTestSourceContent(mockVirtualFile)
                mockFileIndex.isInSourceContent(mockVirtualFile)
            }
        }

        @Test
        fun `determineSourceRootType does not check main source when test source returns true`() {
            // Arrange
            every { mockFileIndex.isInTestSourceContent(mockVirtualFile) } returns true

            // Act
            SourceRootDetector.determineSourceRootType(mockProject, mockVirtualFile)

            // Assert - isInSourceContent should not be called due to short-circuit
            verify { mockFileIndex.isInTestSourceContent(mockVirtualFile) }
            verify(exactly = 0) { mockFileIndex.isInSourceContent(mockVirtualFile) }
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `determineSourceRootType works with different project instances`() {
            // Arrange
            val anotherProject = mockk<Project>(relaxed = true)
            val anotherRootManager = mockk<ProjectRootManager>(relaxed = true)
            val anotherFileIndex = mockk<ProjectFileIndex>(relaxed = true)

            every { ProjectRootManager.getInstance(anotherProject) } returns anotherRootManager
            every { anotherRootManager.fileIndex } returns anotherFileIndex
            every { anotherFileIndex.isInTestSourceContent(mockVirtualFile) } returns true

            // Act
            val result = SourceRootDetector.determineSourceRootType(anotherProject, mockVirtualFile)

            // Assert
            assertEquals(SourceRootType.TEST, result)
            verify { ProjectRootManager.getInstance(anotherProject) }
        }

        @Test
        fun `determineSourceRootType works with different file instances`() {
            // Arrange
            val anotherFile = mockk<VirtualFile>(relaxed = true)
            every { mockFileIndex.isInTestSourceContent(anotherFile) } returns false
            every { mockFileIndex.isInSourceContent(anotherFile) } returns true

            // Act
            val result = SourceRootDetector.determineSourceRootType(mockProject, anotherFile)

            // Assert
            assertEquals(SourceRootType.MAIN, result)
            verify { mockFileIndex.isInSourceContent(anotherFile) }
        }
    }
}

package com.igorlink.claudeidetools.util

import com.igorlink.claudeidetools.model.RefactoringResponse
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Unit tests for RefactoringExecutor.
 * Tests the execution patterns for refactoring operations including:
 * - Direct execute() method returning RefactoringResponse
 * - executeWithCallback() method using callback pattern
 * - Error handling and propagation
 * - Timeout behavior
 */
class RefactoringExecutorTest {

    private lateinit var mockProject: Project
    private lateinit var mockApplication: Application
    private lateinit var mockLogger: Logger

    @BeforeEach
    fun setUp() {
        mockProject = mockk(relaxed = true)
        mockApplication = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)

        // Mock ApplicationManager to return our mock Application
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication

        // Mock WriteCommandAction
        mockkStatic(WriteCommandAction::class)

        // Mock Logger to prevent IntelliJ TestLoggerFactory from failing tests on logged errors
        mockkStatic(Logger::class)
        every { Logger.getInstance(any<Class<*>>()) } returns mockLogger
        every { Logger.getInstance(any<String>()) } returns mockLogger
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== execute() Tests ====================

    @Nested
    inner class ExecuteMethodTests {

        @Test
        fun `execute returns successful response when action succeeds`() {
            // Arrange
            val expectedResponse = RefactoringResponse(true, "Success", listOf("/file.kt"))

            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test Command"
            ) {
                expectedResponse
            }

            // Assert
            assertTrue(result.success)
            assertEquals("Success", result.message)
            assertEquals(listOf("/file.kt"), result.affectedFiles)
        }

        @Test
        fun `execute returns failure response when action returns failure`() {
            // Arrange
            val expectedResponse = RefactoringResponse(false, "Operation failed")

            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test Command"
            ) {
                expectedResponse
            }

            // Assert
            assertFalse(result.success)
            assertEquals("Operation failed", result.message)
        }

        @Test
        fun `execute catches exception in action and returns failure response`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test Command"
            ) {
                throw RuntimeException("Test exception")
            }

            // Assert
            assertFalse(result.success)
            assertTrue(result.message.contains("Refactoring failed"))
            assertTrue(result.message.contains("Test exception"))
        }

        @Test
        fun `execute catches exception in WriteCommandAction and returns failure response`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()

            every {
                WriteCommandAction.runWriteCommandAction(any<Project>(), any<String>(), any(), any<Runnable>())
            } throws RuntimeException("WriteCommand failed")

            // Act
            val result = RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test Command"
            ) {
                RefactoringResponse(true, "Should not reach here")
            }

            // Assert
            assertFalse(result.success)
            assertTrue(result.message.contains("Execution failed"))
            assertTrue(result.message.contains("WriteCommand failed"))
        }

        @Test
        fun `execute uses default timeout when not specified`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act - just verify it completes without timeout
            val result = RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test Command"
            ) {
                RefactoringResponse(true, "Done")
            }

            // Assert
            assertTrue(result.success)
        }

        @Test
        fun `execute uses custom timeout when specified`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test Command",
                timeoutSeconds = 60L
            ) {
                RefactoringResponse(true, "Done with custom timeout")
            }

            // Assert
            assertTrue(result.success)
            assertEquals("Done with custom timeout", result.message)
        }

        @Test
        fun `execute passes correct command name to WriteCommandAction`() {
            // Arrange
            val capturedCommandName = slot<String>()

            setupInvokeAndWaitToRunImmediately()
            every {
                WriteCommandAction.runWriteCommandAction(
                    any<Project>(),
                    capture(capturedCommandName),
                    any(),
                    any<Runnable>()
                )
            } answers {
                // Execute the runnable
                arg<Runnable>(3).run()
            }

            // Act
            RefactoringExecutor.execute(
                project = mockProject,
                commandName = "My Rename Command"
            ) {
                RefactoringResponse(true, "Done")
            }

            // Assert
            assertEquals("My Rename Command", capturedCommandName.captured)
        }

        @Test
        fun `execute invokes action on EDT via invokeAndWait`() {
            // Arrange
            var invokeAndWaitCalled = false

            every { mockApplication.invokeAndWait(any()) } answers {
                invokeAndWaitCalled = true
                arg<Runnable>(0).run()
            }
            setupWriteCommandActionToRunImmediately()

            // Act
            RefactoringExecutor.execute(
                project = mockProject,
                commandName = "Test"
            ) {
                RefactoringResponse(true, "Done")
            }

            // Assert
            assertTrue(invokeAndWaitCalled, "invokeAndWait should be called")
        }
    }

    // ==================== executeWithCallback() Tests ====================

    @Nested
    inner class ExecuteWithCallbackTests {

        @Test
        fun `executeWithCallback returns success when callback success is called`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test Callback Command"
            ) { callback ->
                callback.success("Callback succeeded")
            }

            // Assert
            assertTrue(result.success)
            assertEquals("Callback succeeded", result.message)
        }

        @Test
        fun `executeWithCallback returns failure when callback failure is called`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test Callback Command"
            ) { callback ->
                callback.failure("Callback failed")
            }

            // Assert
            assertFalse(result.success)
            assertEquals("Callback failed", result.message)
        }

        @Test
        fun `executeWithCallback returns response when callback complete is called`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            val customResponse = RefactoringResponse(true, "Custom message", listOf("/a.kt", "/b.kt"))

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test"
            ) { callback ->
                callback.complete(customResponse)
            }

            // Assert
            assertTrue(result.success)
            assertEquals("Custom message", result.message)
            assertEquals(listOf("/a.kt", "/b.kt"), result.affectedFiles)
        }

        @Test
        fun `executeWithCallback catches exception and returns failure`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test"
            ) { _ ->
                throw IllegalStateException("Callback threw exception")
            }

            // Assert
            assertFalse(result.success)
            assertTrue(result.message.contains("Refactoring failed"))
            assertTrue(result.message.contains("Callback threw exception"))
        }

        @Test
        fun `executeWithCallback ignores duplicate callback calls`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test"
            ) { callback ->
                callback.success("First call")
                callback.failure("Second call - should be ignored")
                callback.complete(RefactoringResponse(false, "Third call - should be ignored"))
            }

            // Assert - First call should win
            assertTrue(result.success)
            assertEquals("First call", result.message)
        }

        @Test
        fun `executeWithCallback catches WriteCommandAction exception after callback not called`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()

            every {
                WriteCommandAction.runWriteCommandAction(any<Project>(), any<String>(), any(), any<Runnable>())
            } throws RuntimeException("WriteCommand error")

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test"
            ) { callback ->
                // Callback never called before exception
                callback.success("Never executed")
            }

            // Assert
            assertFalse(result.success)
            assertTrue(result.message.contains("Execution failed"))
        }

        @Test
        fun `executeWithCallback uses custom timeout`() {
            // Arrange
            setupInvokeAndWaitToRunImmediately()
            setupWriteCommandActionToRunImmediately()

            // Act
            val result = RefactoringExecutor.executeWithCallback(
                project = mockProject,
                commandName = "Test",
                timeoutSeconds = 120L
            ) { callback ->
                callback.success("Done with custom timeout")
            }

            // Assert
            assertTrue(result.success)
        }
    }

    // ==================== ResultCallback Tests ====================

    @Nested
    inner class ResultCallbackTests {

        @Test
        fun `ResultCallback success completes future with success response`() {
            // Arrange
            val future = CompletableFuture<RefactoringResponse>()
            val callback = RefactoringExecutor.ResultCallback(future)

            // Act
            callback.success("Operation successful")

            // Assert
            val result = future.get(1, TimeUnit.SECONDS)
            assertTrue(result.success)
            assertEquals("Operation successful", result.message)
        }

        @Test
        fun `ResultCallback failure completes future with failure response`() {
            // Arrange
            val future = CompletableFuture<RefactoringResponse>()
            val callback = RefactoringExecutor.ResultCallback(future)

            // Act
            callback.failure("Operation failed")

            // Assert
            val result = future.get(1, TimeUnit.SECONDS)
            assertFalse(result.success)
            assertEquals("Operation failed", result.message)
        }

        @Test
        fun `ResultCallback complete passes through custom response`() {
            // Arrange
            val future = CompletableFuture<RefactoringResponse>()
            val callback = RefactoringExecutor.ResultCallback(future)
            val customResponse = RefactoringResponse(true, "Custom", listOf("/x.kt"))

            // Act
            callback.complete(customResponse)

            // Assert
            val result = future.get(1, TimeUnit.SECONDS)
            assertEquals(customResponse, result)
        }

        @Test
        fun `ResultCallback ignores calls after future is done`() {
            // Arrange
            val future = CompletableFuture<RefactoringResponse>()
            val callback = RefactoringExecutor.ResultCallback(future)

            // Act
            callback.success("First")
            callback.failure("Second - ignored")
            callback.complete(RefactoringResponse(false, "Third - ignored"))

            // Assert
            val result = future.get(1, TimeUnit.SECONDS)
            assertTrue(result.success)
            assertEquals("First", result.message)
        }

        @Test
        fun `ResultCallback does not complete already completed future`() {
            // Arrange
            val future = CompletableFuture<RefactoringResponse>()
            future.complete(RefactoringResponse(true, "Pre-completed"))
            val callback = RefactoringExecutor.ResultCallback(future)

            // Act
            callback.success("Should not replace")
            callback.failure("Should not replace")

            // Assert
            val result = future.get(1, TimeUnit.SECONDS)
            assertEquals("Pre-completed", result.message)
        }
    }

    // ==================== Timeout Tests ====================

    @Nested
    inner class TimeoutTests {

        @Test
        fun `execute throws TimeoutException when operation exceeds timeout`() {
            // Arrange - invokeAndWait blocks forever (simulated by not completing future)
            every { mockApplication.invokeAndWait(any()) } answers {
                // Do nothing - simulate blocking
                Thread.sleep(5000)
            }

            // Act & Assert
            assertThrows<TimeoutException> {
                RefactoringExecutor.execute(
                    project = mockProject,
                    commandName = "Slow Command",
                    timeoutSeconds = 1L
                ) {
                    RefactoringResponse(true, "Never reached")
                }
            }
        }

        @Test
        fun `executeWithCallback throws TimeoutException when callback is never called`() {
            // Arrange
            every { mockApplication.invokeAndWait(any()) } answers {
                Thread.sleep(5000)
            }

            // Act & Assert
            assertThrows<TimeoutException> {
                RefactoringExecutor.executeWithCallback(
                    project = mockProject,
                    commandName = "Slow Callback Command",
                    timeoutSeconds = 1L
                ) { _ ->
                    // Callback never called
                }
            }
        }

        @Test
        fun `default timeout is used from RefactoringTimeouts`() {
            // Assert that default constant exists and has expected value
            assertEquals(30L, RefactoringTimeouts.DEFAULT)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Sets up mockApplication.invokeAndWait to immediately execute the passed Runnable
     */
    private fun setupInvokeAndWaitToRunImmediately() {
        every { mockApplication.invokeAndWait(any()) } answers {
            arg<Runnable>(0).run()
        }
    }

    /**
     * Sets up WriteCommandAction.runWriteCommandAction to immediately execute the passed Runnable
     */
    private fun setupWriteCommandActionToRunImmediately() {
        every {
            WriteCommandAction.runWriteCommandAction(any<Project>(), any<String>(), any(), any<Runnable>())
        } answers {
            arg<Runnable>(3).run()
        }
    }
}

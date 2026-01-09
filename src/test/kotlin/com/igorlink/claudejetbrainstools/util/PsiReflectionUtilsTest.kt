package com.igorlink.claudejetbrainstools.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for PsiReflectionUtils.
 * Tests reflection-based PSI element operations including:
 * - Ancestor traversal and type matching
 * - Element name extraction via reflection
 * - Element type determination from class names
 * - File type checking
 * - Class availability checking
 */
class PsiReflectionUtilsTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== findAncestorOfType() Tests ====================

    @Nested
    inner class FindAncestorOfTypeTests {

        @Test
        fun `findAncestorOfType finds matching ancestor`() {
            // Arrange
            val grandparent = mockk<PsiElement>(relaxed = true)
            val parent = mockk<PsiElement>(relaxed = true)
            val element = mockk<PsiElement>(relaxed = true)

            every { element.parent } returns parent
            every { parent.parent } returns grandparent
            every { grandparent.parent } returns null

            // Use PsiFile as the target class since it's always available
            val matchingAncestor = mockk<PsiFile>(relaxed = true)
            every { element.parent } returns matchingAncestor
            every { matchingAncestor.parent } returns null

            // Act
            val result = PsiReflectionUtils.findAncestorOfType(
                element,
                "com.intellij.psi.PsiFile"
            )

            // Assert
            assertEquals(matchingAncestor, result)
        }

        @Test
        fun `findAncestorOfType returns null when no match`() {
            // Arrange
            val parent = mockk<PsiElement>(relaxed = true)
            val element = mockk<PsiElement>(relaxed = true)

            every { element.parent } returns parent
            every { parent.parent } returns null

            // Act - Looking for PsiFile but none in hierarchy
            val result = PsiReflectionUtils.findAncestorOfType(
                element,
                "com.intellij.psi.PsiFile"
            )

            // Assert
            assertNull(result)
        }

        @Test
        fun `findAncestorOfType returns null for unknown class`() {
            // Arrange
            val element = mockk<PsiElement>(relaxed = true)
            every { element.parent } returns null

            // Act - Looking for a non-existent class
            val result = PsiReflectionUtils.findAncestorOfType(
                element,
                "com.nonexistent.SomeClass"
            )

            // Assert
            assertNull(result)
        }

        @Test
        fun `findAncestorOfType returns element itself if it matches`() {
            // Arrange
            val element = mockk<PsiFile>(relaxed = true)
            every { element.parent } returns null

            // Act
            val result = PsiReflectionUtils.findAncestorOfType(
                element,
                "com.intellij.psi.PsiFile"
            )

            // Assert
            assertEquals(element, result)
        }

        @Test
        fun `findAncestorOfType traverses full hierarchy to find match`() {
            // Arrange
            val greatGrandparent = mockk<PsiFile>(relaxed = true)
            val grandparent = mockk<PsiElement>(relaxed = true)
            val parent = mockk<PsiElement>(relaxed = true)
            val element = mockk<PsiElement>(relaxed = true)

            every { element.parent } returns parent
            every { parent.parent } returns grandparent
            every { grandparent.parent } returns greatGrandparent
            every { greatGrandparent.parent } returns null

            // Act
            val result = PsiReflectionUtils.findAncestorOfType(
                element,
                "com.intellij.psi.PsiFile"
            )

            // Assert
            assertEquals(greatGrandparent, result)
        }
    }

    // ==================== findAncestorOfTypeWithPredicate() Tests ====================

    @Nested
    inner class FindAncestorOfTypeWithPredicateTests {

        @Test
        fun `findAncestorOfTypeWithPredicate with predicate matching`() {
            // Arrange - Create a custom class that extends PsiElement for testing
            val matchingElement = mockk<PsiFile>(relaxed = true)
            val element = mockk<PsiElement>(relaxed = true)

            every { element.parent } returns matchingElement
            every { matchingElement.parent } returns null

            // Act - Predicate checks if class name contains "File"
            val result = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
                element,
                "com.intellij.psi.PsiElement"
            ) { className -> className.contains("File") }

            // Assert
            assertEquals(matchingElement, result)
        }

        @Test
        fun `findAncestorOfTypeWithPredicate with predicate not matching`() {
            // Arrange
            val parent = mockk<PsiFile>(relaxed = true)
            val element = mockk<PsiElement>(relaxed = true)

            every { element.parent } returns parent
            every { parent.parent } returns null

            // Act - Predicate that never matches
            val result = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
                element,
                "com.intellij.psi.PsiElement"
            ) { className -> className.contains("NonExistentPattern") }

            // Assert
            assertNull(result)
        }

        @Test
        fun `findAncestorOfTypeWithPredicate returns first matching element`() {
            // Arrange - Use custom wrapper classes to ensure predictable class names
            val grandparent = PsiElementTestWrapper(mockk(relaxed = true), "GrandparentElement")
            val parent = PsiElementTestWrapper(mockk(relaxed = true), "ParentElement")
            val element = mockk<PsiElement>(relaxed = true)

            // Set up hierarchy using the wrapper's setParent method
            every { element.parent } returns parent
            parent.setParent(grandparent)
            grandparent.setParent(null)

            // Act - Predicate matches class names containing "Wrapper" (part of PsiElementTestWrapper)
            val result = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
                element,
                "com.intellij.psi.PsiElement"
            ) { className -> className.contains("Wrapper") }

            // Assert - Should return parent (first match traversing up)
            assertEquals(parent, result)
        }

        @Test
        fun `findAncestorOfTypeWithPredicate returns null for unknown class`() {
            // Arrange
            val element = mockk<PsiElement>(relaxed = true)
            every { element.parent } returns null

            // Act
            val result = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
                element,
                "com.nonexistent.SomeClass"
            ) { true }

            // Assert
            assertNull(result)
        }
    }

    // ==================== getElementName() Tests ====================

    @Nested
    inner class GetElementNameTests {

        @Test
        fun `getElementName returns name for named element`() {
            // Arrange - Create a mock that has getName method
            val element = mockk<PsiElement>(relaxed = true)

            // Use spyk to partially mock and add getName behavior
            val namedElement = object : PsiElement by element {
                fun getName(): String = "MyElement"
            }

            // Act
            val result = PsiReflectionUtils.getElementName(namedElement)

            // Assert
            assertEquals("MyElement", result)
        }

        @Test
        fun `getElementName returns fallback for element without name`() {
            // Arrange - PsiElement doesn't have getName by default
            val element = mockk<PsiElement>(relaxed = true)

            // Act
            val result = PsiReflectionUtils.getElementName(element)

            // Assert
            assertEquals("unknown", result)
        }

        @Test
        fun `getElementName returns custom default value`() {
            // Arrange
            val element = mockk<PsiElement>(relaxed = true)

            // Act
            val result = PsiReflectionUtils.getElementName(element, "custom_default")

            // Assert
            assertEquals("custom_default", result)
        }

        @Test
        fun `getElementName returns fallback when getName returns null`() {
            // Arrange
            val element = object : PsiElement by mockk(relaxed = true) {
                fun getName(): String? = null
            }

            // Act
            val result = PsiReflectionUtils.getElementName(element)

            // Assert
            assertEquals("unknown", result)
        }

        @Test
        fun `getElementName returns fallback when getName throws exception`() {
            // Arrange
            val element = object : PsiElement by mockk(relaxed = true) {
                fun getName(): String {
                    throw RuntimeException("getName failed")
                }
            }

            // Act
            val result = PsiReflectionUtils.getElementName(element, "fallback")

            // Assert
            assertEquals("fallback", result)
        }
    }

    // ==================== getElementTypeFromClassName() Tests ====================

    @Nested
    inner class GetElementTypeFromClassNameTests {

        @Test
        fun `getElementTypeFromClassName maps correctly`() {
            // Arrange
            val typeMappings = mapOf(
                "Class" to "class",
                "Function" to "function",
                "Variable" to "variable"
            )

            // Create mock elements with specific class names
            val classElement = createMockElementWithClassName("KtClass")
            val functionElement = createMockElementWithClassName("KtFunction")
            val variableElement = createMockElementWithClassName("KtVariable")

            // Act & Assert
            assertEquals("class", PsiReflectionUtils.getElementTypeFromClassName(classElement, typeMappings))
            assertEquals("function", PsiReflectionUtils.getElementTypeFromClassName(functionElement, typeMappings))
            assertEquals("variable", PsiReflectionUtils.getElementTypeFromClassName(variableElement, typeMappings))
        }

        @Test
        fun `getElementTypeFromClassName returns default for unknown type`() {
            // Arrange
            val typeMappings = mapOf(
                "Class" to "class",
                "Function" to "function"
            )
            val element = createMockElementWithClassName("SomeUnknownElement")

            // Act
            val result = PsiReflectionUtils.getElementTypeFromClassName(element, typeMappings)

            // Assert
            assertEquals("element", result)
        }

        @Test
        fun `getElementTypeFromClassName returns custom default type`() {
            // Arrange
            val typeMappings = mapOf("Class" to "class")
            val element = createMockElementWithClassName("UnmatchedType")

            // Act
            val result = PsiReflectionUtils.getElementTypeFromClassName(
                element,
                typeMappings,
                "declaration"
            )

            // Assert
            assertEquals("declaration", result)
        }

        @Test
        fun `getElementTypeFromClassName matches first pattern in order`() {
            // Arrange - LinkedHashMap to preserve order
            val typeMappings = linkedMapOf(
                "Named" to "named-element",
                "Function" to "function"
            )
            // Class name "KtNamedFunctionMock" contains both "Named" and "Function"
            val element = KtNamedFunctionMock()

            // Act
            val result = PsiReflectionUtils.getElementTypeFromClassName(element, typeMappings)

            // Assert - Should match "Named" first (it appears before "Function" in both the name and the map)
            assertEquals("named-element", result)
        }

        @Test
        fun `getElementTypeFromClassName with empty mappings returns default`() {
            // Arrange
            val element = createMockElementWithClassName("AnyElement")

            // Act
            val result = PsiReflectionUtils.getElementTypeFromClassName(element, emptyMap())

            // Assert
            assertEquals("element", result)
        }

        /**
         * Helper to create a mock PsiElement with a specific simple class name.
         * We need a real class with the desired simple name for reflection to work.
         */
        private fun createMockElementWithClassName(simpleName: String): PsiElement {
            // Create a dynamic proxy or use a naming convention
            // Since we can't dynamically create classes with specific names,
            // we use a wrapper approach
            return when {
                simpleName.contains("Class") -> KtClassMock()
                simpleName.contains("Function") -> KtFunctionMock()
                simpleName.contains("Variable") -> KtVariableMock()
                else -> mockk<PsiElement>(relaxed = true)
            }
        }
    }

    // ==================== isFileOfType() Tests ====================

    @Nested
    inner class IsFileOfTypeTests {

        @Test
        fun `isFileOfType returns true for matching file type`() {
            // Arrange
            val psiFile = mockk<PsiFile>(relaxed = true)

            // Act - Check if it's a PsiFile (which it is)
            val result = PsiReflectionUtils.isFileOfType(
                psiFile,
                "com.intellij.psi.PsiFile"
            )

            // Assert
            assertTrue(result)
        }

        @Test
        fun `isFileOfType returns false for non-matching file type`() {
            // Arrange
            val psiFile = mockk<PsiFile>(relaxed = true)

            // Act - Check against a non-existent type
            val result = PsiReflectionUtils.isFileOfType(
                psiFile,
                "com.nonexistent.SomeFileType"
            )

            // Assert
            assertFalse(result)
        }

        @Test
        fun `isFileOfType returns true for superclass match`() {
            // Arrange
            val psiFile = mockk<PsiFile>(relaxed = true)

            // Act - PsiFile extends PsiElement
            val result = PsiReflectionUtils.isFileOfType(
                psiFile,
                "com.intellij.psi.PsiElement"
            )

            // Assert
            assertTrue(result)
        }

        @Test
        fun `isFileOfType handles ClassNotFoundException gracefully`() {
            // Arrange
            val psiFile = mockk<PsiFile>(relaxed = true)

            // Act - Non-existent class should return false, not throw
            val result = PsiReflectionUtils.isFileOfType(
                psiFile,
                "this.class.definitely.does.not.Exist"
            )

            // Assert
            assertFalse(result)
        }
    }

    // ==================== isClassAvailable() Tests ====================

    @Nested
    inner class IsClassAvailableTests {

        @Test
        fun `isClassAvailable returns true for available class`() {
            // Act
            val result = PsiReflectionUtils.isClassAvailable("com.intellij.psi.PsiElement")

            // Assert
            assertTrue(result)
        }

        @Test
        fun `isClassAvailable returns false for unavailable class`() {
            // Act
            val result = PsiReflectionUtils.isClassAvailable("com.nonexistent.SomeClass")

            // Assert
            assertFalse(result)
        }

        @Test
        fun `isClassAvailable returns true for standard Java class`() {
            // Act
            val result = PsiReflectionUtils.isClassAvailable("java.lang.String")

            // Assert
            assertTrue(result)
        }

        @Test
        fun `isClassAvailable returns false for malformed class name`() {
            // Act
            val result = PsiReflectionUtils.isClassAvailable("not a valid class name")

            // Assert
            assertFalse(result)
        }

        @Test
        fun `isClassAvailable returns false for empty class name`() {
            // Act
            val result = PsiReflectionUtils.isClassAvailable("")

            // Assert
            assertFalse(result)
        }
    }

    // ==================== Mock Helper Classes ====================

    /**
     * Wrapper class that delegates to a PsiElement mock but allows configurable parent.
     * The class name contains "Element" for predicate-based tests.
     */
    private class PsiElementTestWrapper(
        private val delegate: PsiElement,
        @Suppress("unused") private val name: String
    ) : PsiElement by delegate {
        private var parentElement: PsiElement? = null

        override fun getParent(): PsiElement? = parentElement

        fun setParent(parent: PsiElement?) {
            parentElement = parent
        }
    }

    /**
     * Mock class for testing getElementTypeFromClassName with "Class" pattern
     */
    private class KtClassMock : PsiElement by mockk(relaxed = true)

    /**
     * Mock class for testing getElementTypeFromClassName with "Function" pattern
     */
    private class KtFunctionMock : PsiElement by mockk(relaxed = true)

    /**
     * Mock class for testing getElementTypeFromClassName with "Variable" pattern
     */
    private class KtVariableMock : PsiElement by mockk(relaxed = true)

    /**
     * Mock class for testing getElementTypeFromClassName with both "Named" and "Function" patterns.
     * This class name allows testing the order of pattern matching.
     */
    private class KtNamedFunctionMock : PsiElement by mockk(relaxed = true)
}

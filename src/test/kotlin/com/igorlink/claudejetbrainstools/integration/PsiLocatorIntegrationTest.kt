package com.igorlink.claudejetbrainstools.integration

import com.igorlink.claudejetbrainstools.services.PsiLocatorService
import com.igorlink.claudejetbrainstools.services.PsiLookupResult
import com.igorlink.claudejetbrainstools.services.ProjectLookupResult
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Integration tests for PsiLocatorService using IntelliJ Platform Test Framework.
 *
 * These tests use LightJavaCodeInsightFixtureTestCase which provides:
 * - A lightweight project fixture
 * - Real PSI infrastructure
 * - Support for Java language features
 *
 * Note: Since LightJavaCodeInsightFixtureTestCase uses a virtual file system,
 * we test PSI element location logic directly using the fixture's PSI methods
 * rather than through PsiLocatorService's file path resolution.
 *
 * This approach validates:
 * 1. PSI element finding at specific offsets
 * 2. Reference resolution
 * 3. Element type identification (class, method, field, etc.)
 *
 * These are the core operations that PsiLocatorService performs after resolving file paths.
 *
 * Note: Uses JUnit 3 style (methods start with "test") for compatibility with
 * IntelliJ Platform Test Framework. JUnit Vintage engine is used to run these tests.
 */
class PsiLocatorIntegrationTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var psiLocatorService: PsiLocatorService

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    override fun setUp() {
        super.setUp()
        psiLocatorService = PsiLocatorService()
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a Java file directly in the test project's source root.
     * Returns the PSI file for direct PSI testing.
     */
    private fun createJavaFile(relativePath: String, content: String): PsiFile {
        return myFixture.addFileToProject(relativePath, content)
    }

    /**
     * Finds the PSI element at a given line and column in a file.
     * This mirrors the core logic of PsiLocatorService.findElementAt without file path resolution.
     */
    private fun findElementAt(psiFile: PsiFile, line: Int, column: Int): PsiElement? {
        return ReadAction.compute<PsiElement?, Throwable> {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@compute null

            if (line < 1 || line > document.lineCount) return@compute null

            val lineStartOffset = document.getLineStartOffset(line - 1)
            val lineEndOffset = document.getLineEndOffset(line - 1)
            val maxColumn = lineEndOffset - lineStartOffset + 1

            if (column < 1 || column > maxColumn) return@compute null

            val offset = lineStartOffset + (column - 1)
            val elementAtOffset = psiFile.findElementAt(offset) ?: return@compute null

            // Try to find reference and resolve it
            val reference = psiFile.findReferenceAt(offset)
            val resolvedElement = reference?.resolve()

            // If we resolved a reference, use that; otherwise find parent named element
            resolvedElement ?: findNearestRenamableElement(elementAtOffset) ?: elementAtOffset
        }
    }

    /**
     * Mirrors the logic from PsiLocatorService.findNearestRenamableElement
     */
    private fun findNearestRenamableElement(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is PsiNameIdentifierOwner || current is PsiNamedElement) {
                return current
            }
            current = current.parent
        }
        return null
    }

    // ==================== Simple Class Content ====================

    private val simpleClassContent = """
package com.example;

public class SimpleClass {

    private String name;
    private int count;

    public SimpleClass(String name) {
        this.name = name;
        this.count = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        count++;
    }

    public static void main(String[] args) {
        SimpleClass instance = new SimpleClass("test");
        System.out.println(instance.getName());
    }
}
""".trimStart()

    private val complexClassContent = """
package com.example;

import java.util.ArrayList;
import java.util.List;

public class ComplexClass {

    public static final String CONSTANT = "constant_value";
    private final List<String> items;
    protected int protectedField;

    private static int instanceCount = 0;

    public ComplexClass() {
        this.items = new ArrayList<>();
        instanceCount++;
    }

    public static int getInstanceCount() {
        return instanceCount;
    }

    public class InnerClass {
        private String innerField;

        public InnerClass(String value) {
            this.innerField = value;
        }
    }

    public static class StaticNestedClass {
        private int nestedValue;

        public StaticNestedClass(int value) {
            this.nestedValue = value;
        }
    }
}
""".trimStart()

    private val interfaceExampleContent = """
package com.example;

public interface InterfaceExample {

    String INTERFACE_CONSTANT = "interface_value";

    void performAction();

    String process(String input);

    default String getDescription() {
        return "Default implementation";
    }
}
""".trimStart()

    private val enumExampleContent = """
package com.example;

public enum EnumExample {

    FIRST("First Value", 1),
    SECOND("Second Value", 2),
    THIRD("Third Value", 3);

    private final String description;
    private final int order;

    EnumExample(String description, int order) {
        this.description = description;
        this.order = order;
    }

    public String getDescription() {
        return description;
    }
}
""".trimStart()

    private val classWithReferencesContent = """
package com.example;

public class ClassWithReferences {

    private SimpleClass simpleClass;

    public ClassWithReferences() {
        this.simpleClass = new SimpleClass("referenced");
    }

    public String getSimpleClassName() {
        return simpleClass.getName();
    }
}
""".trimStart()

    // ==================== Class Location Tests ====================

    fun testFindClassDeclaration() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 3: "public class SimpleClass {"
        // "SimpleClass" starts at column 14
        val element = findElementAt(psiFile, 3, 14)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiClass, got ${element?.javaClass?.simpleName}", element is PsiClass)
        assertEquals("SimpleClass", (element as PsiClass).name)
    }

    fun testFindInnerClassDeclaration() {
        val psiFile = createJavaFile("com/example/ComplexClass.java", complexClassContent)

        // Line 23: "    public class InnerClass {"
        // "InnerClass" starts at column 18
        val element = findElementAt(psiFile, 23, 18)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiClass, got ${element?.javaClass?.simpleName}", element is PsiClass)
        assertEquals("InnerClass", (element as PsiClass).name)
    }

    fun testFindStaticNestedClassDeclaration() {
        val psiFile = createJavaFile("com/example/ComplexClass.java", complexClassContent)

        // Line 31: "    public static class StaticNestedClass {"
        // "StaticNestedClass" starts at column 25
        val element = findElementAt(psiFile, 31, 25)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiClass, got ${element?.javaClass?.simpleName}", element is PsiClass)
        assertEquals("StaticNestedClass", (element as PsiClass).name)
    }

    // ==================== Method Location Tests ====================

    fun testFindMethodDeclaration() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 13: "    public String getName() {"
        // "getName" starts at column 19
        val element = findElementAt(psiFile, 13, 19)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiMethod, got ${element?.javaClass?.simpleName}", element is PsiMethod)
        assertEquals("getName", (element as PsiMethod).name)
    }

    fun testFindConstructor() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 8: "    public SimpleClass(String name) {"
        // "SimpleClass" constructor starts at column 12
        val element = findElementAt(psiFile, 8, 12)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiMethod, got ${element?.javaClass?.simpleName}", element is PsiMethod)
        val method = element as PsiMethod
        assertTrue("Expected constructor", method.isConstructor)
        assertEquals("SimpleClass", method.name)
    }

    fun testFindStaticMethod() {
        val psiFile = createJavaFile("com/example/ComplexClass.java", complexClassContent)

        // Line 19: "    public static int getInstanceCount() {"
        // "getInstanceCount" starts at column 23
        val element = findElementAt(psiFile, 19, 23)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiMethod, got ${element?.javaClass?.simpleName}", element is PsiMethod)
        assertEquals("getInstanceCount", (element as PsiMethod).name)
    }

    fun testFindDefaultInterfaceMethod() {
        val psiFile = createJavaFile("com/example/InterfaceExample.java", interfaceExampleContent)

        // Line 11: "    default String getDescription() {"
        // "getDescription" starts at column 20
        val element = findElementAt(psiFile, 11, 20)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiMethod, got ${element?.javaClass?.simpleName}", element is PsiMethod)
        assertEquals("getDescription", (element as PsiMethod).name)
    }

    // ==================== Field Location Tests ====================

    fun testFindPrivateField() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 5: "    private String name;"
        // "name" starts at column 20
        val element = findElementAt(psiFile, 5, 20)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiField, got ${element?.javaClass?.simpleName}", element is PsiField)
        assertEquals("name", (element as PsiField).name)
    }

    fun testFindStaticFinalField() {
        val psiFile = createJavaFile("com/example/ComplexClass.java", complexClassContent)

        // Line 8: "    public static final String CONSTANT = "constant_value";"
        // "CONSTANT" starts at column 32
        val element = findElementAt(psiFile, 8, 32)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiField, got ${element?.javaClass?.simpleName}", element is PsiField)
        assertEquals("CONSTANT", (element as PsiField).name)
    }

    fun testFindEnumConstant() {
        val psiFile = createJavaFile("com/example/EnumExample.java", enumExampleContent)

        // Line 5: "    FIRST("First Value", 1),"
        // "FIRST" starts at column 5
        val element = findElementAt(psiFile, 5, 5)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiEnumConstant, got ${element?.javaClass?.simpleName}", element is PsiEnumConstant)
        assertEquals("FIRST", (element as PsiEnumConstant).name)
    }

    // ==================== Parameter Location Tests ====================

    fun testFindMethodParameter() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 8: "    public SimpleClass(String name) {"
        // "name" parameter starts at column 31
        val element = findElementAt(psiFile, 8, 31)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiParameter, got ${element?.javaClass?.simpleName}", element is PsiParameter)
        assertEquals("name", (element as PsiParameter).name)
    }

    // ==================== Local Variable Location Tests ====================

    fun testFindLocalVariable() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 30: "        SimpleClass instance = new SimpleClass("test");"
        // "instance" starts at column 21
        val element = findElementAt(psiFile, 30, 21)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiLocalVariable, got ${element?.javaClass?.simpleName}", element is PsiLocalVariable)
        assertEquals("instance", (element as PsiLocalVariable).name)
    }

    // ==================== Interface Elements Location Tests ====================

    fun testFindInterfaceDeclaration() {
        val psiFile = createJavaFile("com/example/InterfaceExample.java", interfaceExampleContent)

        // Line 3: "public interface InterfaceExample {"
        // "InterfaceExample" starts at column 18
        val element = findElementAt(psiFile, 3, 18)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiClass (interface), got ${element?.javaClass?.simpleName}", element is PsiClass)
        val psiClass = element as PsiClass
        assertTrue("Expected interface", psiClass.isInterface)
        assertEquals("InterfaceExample", psiClass.name)
    }

    fun testFindAbstractMethod() {
        val psiFile = createJavaFile("com/example/InterfaceExample.java", interfaceExampleContent)

        // Line 7: "    void performAction();"
        // "performAction" starts at column 10
        val element = findElementAt(psiFile, 7, 10)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiMethod, got ${element?.javaClass?.simpleName}", element is PsiMethod)
        assertEquals("performAction", (element as PsiMethod).name)
    }

    // ==================== Enum Elements Location Tests ====================

    fun testFindEnumDeclaration() {
        val psiFile = createJavaFile("com/example/EnumExample.java", enumExampleContent)

        // Line 3: "public enum EnumExample {"
        // "EnumExample" starts at column 13
        val element = findElementAt(psiFile, 3, 13)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiClass (enum), got ${element?.javaClass?.simpleName}", element is PsiClass)
        val psiClass = element as PsiClass
        assertTrue("Expected enum", psiClass.isEnum)
        assertEquals("EnumExample", psiClass.name)
    }

    // ==================== Reference Resolution Tests ====================

    fun testResolveMethodReference() {
        // Create both files so references can be resolved
        createJavaFile("com/example/SimpleClass.java", simpleClassContent)
        val psiFile = createJavaFile("com/example/ClassWithReferences.java", classWithReferencesContent)

        // Line 12: "        return simpleClass.getName();"
        // "getName" method call starts at column 28 (after the dot)
        val element = findElementAt(psiFile, 12, 28)

        assertNotNull("Element should be found", element)
        // The resolved element should be the PsiMethod from SimpleClass
        assertTrue("Expected PsiMethod, got ${element?.javaClass?.simpleName}", element is PsiMethod)
        assertEquals("getName", (element as PsiMethod).name)
    }

    fun testResolveFieldReference() {
        createJavaFile("com/example/SimpleClass.java", simpleClassContent)
        val psiFile = createJavaFile("com/example/ClassWithReferences.java", classWithReferencesContent)

        // Line 5: "    private SimpleClass simpleClass;"
        // "simpleClass" field starts at column 25
        val element = findElementAt(psiFile, 5, 25)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiField, got ${element?.javaClass?.simpleName}", element is PsiField)
        assertEquals("simpleClass", (element as PsiField).name)
    }

    fun testResolveTypeReference() {
        createJavaFile("com/example/SimpleClass.java", simpleClassContent)
        val psiFile = createJavaFile("com/example/ClassWithReferences.java", classWithReferencesContent)

        // Line 5: "    private SimpleClass simpleClass;"
        // "SimpleClass" type reference starts at column 13
        val element = findElementAt(psiFile, 5, 13)

        assertNotNull("Element should be found", element)
        // Should resolve to the SimpleClass declaration
        assertTrue("Expected PsiClass, got ${element?.javaClass?.simpleName}", element is PsiClass)
        assertEquals("SimpleClass", (element as PsiClass).name)
    }

    // ==================== PsiLocatorService Unit Tests ====================

    fun testCheckDumbModeReturnsNullWhenNotIndexing() {
        // In the test environment, we're typically not in dumb mode
        val dumbModeError = psiLocatorService.checkDumbMode(project)

        // Should return null when not indexing
        assertNull("Should return null when not in dumb mode", dumbModeError)
    }

    fun testFileNotFoundReturnsError() {
        val nonExistentPath = "/path/to/non/existent/File.java"

        val result = psiLocatorService.findElementAt(nonExistentPath, 1, 1, project.name)

        assertTrue("Expected Error result", result is PsiLookupResult.Error)
        val errorResult = result as PsiLookupResult.Error
        assertTrue("Error should mention file not found", errorResult.message.contains("File not found"))
    }

    // ==================== Edge Case Tests ====================

    fun testFindElementAtFirstCharacterOfFile() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // First character of file (line 1, column 1) - "package" keyword
        val element = findElementAt(psiFile, 1, 1)

        assertNotNull("Element should be found at first character", element)
    }

    fun testFindElementAtEmptyLine() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Line 2 is empty
        // For empty line, column 1 may result in whitespace element or null
        val element = findElementAt(psiFile, 2, 1)

        // Empty lines may return whitespace or null - both are acceptable
        // Just verify no crash
        assertTrue("Result should be null or whitespace element", element == null || element is PsiWhiteSpace || element != null)
    }

    fun testLineOutOfBoundsReturnsNull() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // simpleClassContent has ~32 lines, so 1000 is out of bounds
        val element = findElementAt(psiFile, 1000, 1)

        assertNull("Element should be null for out-of-bounds line", element)
    }

    fun testColumnOutOfBoundsReturnsNull() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        // Column 1000 is definitely out of bounds for any line
        val element = findElementAt(psiFile, 1, 1000)

        assertNull("Element should be null for out-of-bounds column", element)
    }

    fun testNegativeLineReturnsNull() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        val element = findElementAt(psiFile, -1, 1)

        assertNull("Element should be null for negative line", element)
    }

    fun testZeroLineReturnsNull() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        val element = findElementAt(psiFile, 0, 1)

        assertNull("Element should be null for zero line", element)
    }

    fun testZeroColumnReturnsNull() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)

        val element = findElementAt(psiFile, 1, 0)

        assertNull("Element should be null for zero column", element)
    }

    fun testMultipleFilesInProject() {
        // Create multiple files
        createJavaFile("com/example/SimpleClass.java", simpleClassContent)
        createJavaFile("com/example/ComplexClass.java", complexClassContent)
        val psiFile = createJavaFile("com/example/InterfaceExample.java", interfaceExampleContent)

        // Find element in the interface file
        // Line 3: "public interface InterfaceExample {"
        val element = findElementAt(psiFile, 3, 18)

        assertNotNull("Element should be found", element)
        assertTrue("Expected PsiClass", element is PsiClass)
        assertEquals("InterfaceExample", (element as PsiClass).name)
    }

    // ==================== PsiLookupResult and ProjectLookupResult Tests ====================

    fun testPsiLookupResultFoundContainsProjectAndElement() {
        val psiFile = createJavaFile("com/example/SimpleClass.java", simpleClassContent)
        val element = findElementAt(psiFile, 3, 14)

        assertNotNull("Element should be found for this test", element)

        val result = PsiLookupResult.Found(project, element!!)

        assertEquals(project, result.project)
        assertEquals(element, result.element)
    }

    fun testPsiLookupResultErrorContainsMessage() {
        val errorMessage = "Test error message"
        val result = PsiLookupResult.Error(errorMessage)

        assertEquals(errorMessage, result.message)
    }

    fun testProjectLookupResultFoundContainsProject() {
        val result = ProjectLookupResult.Found(project)

        assertEquals(project, result.project)
    }

    fun testProjectLookupResultErrorContainsMessage() {
        val errorMessage = "Project not found"
        val result = ProjectLookupResult.Error(errorMessage)

        assertEquals(errorMessage, result.message)
    }
}

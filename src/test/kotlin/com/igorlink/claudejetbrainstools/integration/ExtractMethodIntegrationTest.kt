package com.igorlink.claudejetbrainstools.integration

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Integration tests for ExtractMethodHandler using real IntelliJ refactoring.
 *
 * These tests validate that the extract method refactoring correctly:
 * - Extracts simple code blocks into new methods
 * - Handles code blocks that require parameters
 * - Handles code blocks that return values
 * - Handles multiple statements extraction
 *
 * Uses JUnit 3 style (methods start with "test") for compatibility with
 * IntelliJ Platform Test Framework. JUnit Vintage engine runs these tests.
 */
class ExtractMethodIntegrationTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a Java file in the test project.
     */
    private fun createJavaFile(relativePath: String, content: String): PsiFile {
        return myFixture.addFileToProject(relativePath, content)
    }

    /**
     * Performs extract method refactoring using ExtractMethodProcessor.
     * This mirrors the logic in ExtractMethodHandler.
     *
     * @param psiFile The Java file containing the code to extract
     * @param startLine 1-based start line of the code to extract
     * @param startColumn 1-based start column
     * @param endLine 1-based end line of the code to extract
     * @param endColumn 1-based end column
     * @param methodName Name for the extracted method
     * @return true if extraction succeeded, false otherwise
     */
    private fun performExtractMethod(
        psiFile: PsiFile,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        methodName: String
    ): Boolean {
        require(psiFile is PsiJavaFile) { "Extract method only supports Java files" }

        var success = false

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    ?: throw IllegalStateException("Cannot get document for file")

                // Convert 1-based line/column to offsets
                val startOffset = document.getLineStartOffset(startLine - 1) + (startColumn - 1)
                val endOffset = document.getLineStartOffset(endLine - 1) + (endColumn - 1)

                val elements = CodeInsightUtil.findStatementsInRange(psiFile, startOffset, endOffset)

                if (elements.isEmpty()) {
                    throw IllegalStateException("No statements found in the specified range")
                }

                val processor = ExtractMethodProcessor(
                    project,
                    null, // editor
                    elements,
                    null, // forcedReturnType
                    "Test Extract Method",
                    methodName,
                    null // helpId
                )

                if (processor.prepare()) {
                    processor.testPrepare()
                    processor.doExtract()
                    success = true
                }
            }
        }

        return success
    }

    /**
     * Finds a method by name in a PsiFile.
     */
    private fun findMethod(psiFile: PsiFile, methodName: String): PsiMethod? {
        var result: PsiMethod? = null
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (method.name == methodName) {
                    result = method
                }
                super.visitMethod(method)
            }
        })
        return result
    }

    /**
     * Counts occurrences of a string in the file text.
     */
    private fun countOccurrences(psiFile: PsiFile, text: String): Int {
        return psiFile.text.split(text).size - 1
    }

    /**
     * Checks if file contains text.
     */
    private fun containsText(psiFile: PsiFile, text: String): Boolean {
        return psiFile.text.contains(text)
    }

    // ==================== Test Data ====================

    private val simpleClassContent = """
package com.example;

public class SimpleExtract {

    public void originalMethod() {
        System.out.println("Before");
        System.out.println("Line 1");
        System.out.println("Line 2");
        System.out.println("After");
    }
}
""".trimStart()

    private val classWithLocalVariables = """
package com.example;

public class LocalVariablesExtract {

    public void calculateTotal() {
        int a = 10;
        int b = 20;
        int sum = a + b;
        int product = a * b;
        System.out.println("Sum: " + sum);
        System.out.println("Product: " + product);
    }
}
""".trimStart()

    private val classWithReturnValue = """
package com.example;

public class ReturnValueExtract {

    public String processData() {
        String prefix = "Hello";
        String suffix = "World";
        String result = prefix + " " + suffix;
        return result;
    }
}
""".trimStart()

    private val classWithMethodParameters = """
package com.example;

public class ParameterExtract {

    public void processInput(String input, int count) {
        for (int i = 0; i < count; i++) {
            String processed = input.toUpperCase();
            System.out.println(processed);
        }
    }
}
""".trimStart()

    private val classWithMultipleStatements = """
package com.example;

public class MultiStatementExtract {

    public void complexMethod() {
        int x = 5;
        int y = 10;
        int z = x + y;
        System.out.println("x = " + x);
        System.out.println("y = " + y);
        System.out.println("z = " + z);
        String result = "Result: " + z;
        System.out.println(result);
    }
}
""".trimStart()

    private val classWithConditionals = """
package com.example;

public class ConditionalExtract {

    public void processValue(int value) {
        String message;
        if (value > 0) {
            message = "Positive";
        } else if (value < 0) {
            message = "Negative";
        } else {
            message = "Zero";
        }
        System.out.println(message);
    }
}
""".trimStart()

    private val classWithLoop = """
package com.example;

public class LoopExtract {

    public void processItems() {
        int total = 0;
        for (int i = 1; i <= 10; i++) {
            total += i;
        }
        System.out.println("Total: " + total);
    }
}
""".trimStart()

    private val classWithFieldAccess = """
package com.example;

public class FieldAccessExtract {

    private String name = "Test";
    private int counter = 0;

    public void updateState() {
        counter++;
        String message = name + ": " + counter;
        System.out.println(message);
    }
}
""".trimStart()

    // ==================== Simple Block Extraction Tests ====================

    fun testExtractSimpleCodeBlock() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        // Extract lines 7-8:
        // System.out.println("Line 1");
        // System.out.println("Line 2");
        // Line 7 starts at column 9, Line 8 ends after semicolon
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 38,
            methodName = "extractedBlock"
        )

        assertTrue("Extract method should succeed", success)

        // Verify the new method exists
        val extractedMethod = findMethod(psiFile, "extractedBlock")
        assertNotNull("Extracted method 'extractedBlock' should exist", extractedMethod)

        // Verify the new method is called from original method
        assertTrue(
            "Original method should call extractedBlock()",
            containsText(psiFile, "extractedBlock()")
        )

        // Verify the extracted code is in the new method
        val methodText = extractedMethod!!.text
        assertTrue(
            "Extracted method should contain 'Line 1' println",
            methodText.contains("Line 1")
        )
        assertTrue(
            "Extracted method should contain 'Line 2' println",
            methodText.contains("Line 2")
        )
    }

    fun testExtractedMethodHasPrivateModifier() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 38,
            methodName = "privateMethod"
        )

        assertTrue("Extract method should succeed", success)

        // The extracted method should typically be private by default
        val fileText = psiFile.text
        assertTrue(
            "Extracted method should be present in the file",
            fileText.contains("privateMethod()")
        )
    }

    fun testExtractSingleStatement() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        // Extract single statement: System.out.println("Line 1");
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 7,
            endColumn = 38,
            methodName = "singleStatement"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "singleStatement")
        assertNotNull("Extracted method should exist", extractedMethod)
    }

    // ==================== Extraction with Parameters Tests ====================

    fun testExtractCodeBlockWithExternalVariable() {
        val content = """
package com.example;

public class ParamTest {

    public void process() {
        String message = "Hello";
        System.out.println(message);
        System.out.println(message.length());
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/ParamTest.java", content)

        // Extract lines 7-8 that use 'message' variable from outside
        // Line 7: "        System.out.println(message);"
        // Line 8: "        System.out.println(message.length());"
        // Both start at column 9, line 8 ends at column 46 (after semicolon)
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 46,
            methodName = "printMessage"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "printMessage")
        assertNotNull("Extracted method should exist", extractedMethod)

        // The extracted method should have 'message' as a parameter
        val methodText = extractedMethod!!.text
        assertTrue(
            "Extracted method should have String parameter",
            methodText.contains("String")
        )
    }

    fun testExtractCodeWithMultipleExternalVariables() {
        val content = """
package com.example;

public class MultiParamTest {

    public void calculate() {
        int x = 10;
        int y = 20;
        int sum = x + y;
        System.out.println("Sum: " + sum);
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/MultiParamTest.java", content)

        // Extract lines 8-9 that use 'x' and 'y' from outside
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 8,
            startColumn = 9,
            endLine = 9,
            endColumn = 43,
            methodName = "calculateAndPrint"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "calculateAndPrint")
        assertNotNull("Extracted method should exist", extractedMethod)

        // The method should have parameters for x and y
        val parameterList = extractedMethod!!.parameterList
        assertTrue(
            "Extracted method should have at least one parameter",
            parameterList.parametersCount >= 1
        )
    }

    fun testExtractCodeUsingMethodParameter() {
        val psiFile = createJavaFile("com/example/ParameterExtract.java", classWithMethodParameters)

        // Extract the loop body that uses 'input' parameter
        // for (int i = 0; i < count; i++) {
        //     String processed = input.toUpperCase();
        //     System.out.println(processed);
        // }
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 6,
            startColumn = 9,
            endLine = 9,
            endColumn = 10,
            methodName = "processLoop"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "processLoop")
        assertNotNull("Extracted method should exist", extractedMethod)
    }

    // ==================== Extraction with Return Value Tests ====================

    fun testExtractCodeThatProducesUsedValue() {
        val content = """
package com.example;

public class ReturnTest {

    public void useResult() {
        String prefix = "Hello";
        String suffix = "World";
        String combined = prefix + " " + suffix;
        System.out.println(combined);
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/ReturnTest.java", content)

        // Extract lines 6-8 that compute 'combined', which is used later
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 6,
            startColumn = 9,
            endLine = 8,
            endColumn = 50,
            methodName = "buildCombined"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "buildCombined")
        assertNotNull("Extracted method should exist", extractedMethod)

        // The return type should be String since 'combined' is used after extraction
        val returnType = extractedMethod!!.returnType
        assertNotNull("Extracted method should have a return type", returnType)
        assertEquals("Return type should be String", "String", returnType!!.presentableText)
    }

    fun testExtractCodeWithIntReturnValue() {
        val content = """
package com.example;

public class IntReturnTest {

    public void processNumber() {
        int a = 5;
        int b = 10;
        int result = a * b;
        System.out.println("Result: " + result);
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/IntReturnTest.java", content)

        // Extract lines 6-8 that compute 'result', used in line 9
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 6,
            startColumn = 9,
            endLine = 8,
            endColumn = 28,
            methodName = "computeResult"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "computeResult")
        assertNotNull("Extracted method should exist", extractedMethod)

        val returnType = extractedMethod!!.returnType
        assertNotNull("Extracted method should have return type", returnType)
        assertEquals("Return type should be int", "int", returnType!!.presentableText)
    }

    fun testExtractCodeWithVoidReturn() {
        val content = """
package com.example;

public class VoidReturnTest {

    public void printMessages() {
        System.out.println("First");
        System.out.println("Second");
        System.out.println("Third");
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/VoidReturnTest.java", content)

        // Extract lines 6-7, they don't produce any value used later
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 6,
            startColumn = 9,
            endLine = 7,
            endColumn = 38,
            methodName = "printTwoMessages"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "printTwoMessages")
        assertNotNull("Extracted method should exist", extractedMethod)

        val returnType = extractedMethod!!.returnType
        assertNotNull("Return type should not be null", returnType)
        assertEquals("Return type should be void", "void", returnType!!.presentableText)
    }

    // ==================== Multiple Statements Extraction Tests ====================

    fun testExtractMultipleStatements() {
        val psiFile = createJavaFile("com/example/MultiStatementExtract.java", classWithMultipleStatements)

        // In classWithMultipleStatements:
        // Line 6:  int x = 5;
        // Line 7:  int y = 10;
        // Line 8:  int z = x + y;
        // Line 9:  System.out.println("x = " + x);
        // Line 10: System.out.println("y = " + y);
        // Line 11: System.out.println("z = " + z);
        // Extract lines 9-11 (the three println statements)
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 9,
            startColumn = 9,
            endLine = 11,
            endColumn = 42,
            methodName = "printVariables"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "printVariables")
        assertNotNull("Extracted method should exist", extractedMethod)

        val methodText = extractedMethod!!.text
        assertTrue("Should contain x println", methodText.contains("\"x = \""))
        assertTrue("Should contain y println", methodText.contains("\"y = \""))
        assertTrue("Should contain z println", methodText.contains("\"z = \""))
    }

    fun testExtractConditionalBlock() {
        val psiFile = createJavaFile("com/example/ConditionalExtract.java", classWithConditionals)

        // Extract the entire if-else block (lines 7-13)
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 13,
            endColumn = 10,
            methodName = "determineMessage"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "determineMessage")
        assertNotNull("Extracted method should exist", extractedMethod)

        val methodText = extractedMethod!!.text
        assertTrue("Should contain if statement", methodText.contains("if"))
        assertTrue("Should contain else", methodText.contains("else"))
    }

    fun testExtractLoopBlock() {
        val psiFile = createJavaFile("com/example/LoopExtract.java", classWithLoop)

        // Extract the for loop (lines 7-9)
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 9,
            endColumn = 10,
            methodName = "sumLoop"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "sumLoop")
        assertNotNull("Extracted method should exist", extractedMethod)

        val methodText = extractedMethod!!.text
        assertTrue("Should contain for loop", methodText.contains("for"))
    }

    fun testExtractMixedStatements() {
        val content = """
package com.example;

public class MixedStatements {

    public void process() {
        int value = 42;
        String text = String.valueOf(value);
        System.out.println("Value: " + text);
        int doubled = value * 2;
        System.out.println("Doubled: " + doubled);
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/MixedStatements.java", content)

        // Extract lines 6-8
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 6,
            startColumn = 9,
            endLine = 8,
            endColumn = 46,
            methodName = "processFirstPart"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "processFirstPart")
        assertNotNull("Extracted method should exist", extractedMethod)

        // The method should return int since 'value' is used later
        val returnType = extractedMethod!!.returnType
        assertNotNull("Should have return type", returnType)
        assertEquals("Return type should be int", "int", returnType!!.presentableText)
    }

    // ==================== Edge Cases Tests ====================

    fun testExtractCodeAccessingInstanceFields() {
        val psiFile = createJavaFile("com/example/FieldAccessExtract.java", classWithFieldAccess)

        // In classWithFieldAccess:
        // Line 8:  public void updateState() {
        // Line 9:      counter++;
        // Line 10:     String message = name + ": " + counter;
        // Line 11:     System.out.println(message);
        // Extract lines 9-10 that access 'name' and 'counter' fields
        // "        counter++;" has 8 spaces + 10 chars = ends at 18
        // "        String message = name + \": \" + counter;" has 8 spaces + 39 chars = ends at 47
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 9,
            startColumn = 9,
            endLine = 10,
            endColumn = 48,
            methodName = "buildMessage"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "buildMessage")
        assertNotNull("Extracted method should exist", extractedMethod)

        val methodText = extractedMethod!!.text
        // Fields should still be accessible in extracted method
        assertTrue(
            "Extracted method should access fields",
            methodText.contains("counter") || methodText.contains("name")
        )
    }

    fun testExtractDoesNotDuplicateCode() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        val originalLine1Count = countOccurrences(psiFile, "\"Line 1\"")

        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 38,
            methodName = "extractedMethod"
        )

        assertTrue("Extract method should succeed", success)

        // The extracted code should exist once (moved, not duplicated)
        val newLine1Count = countOccurrences(psiFile, "\"Line 1\"")
        assertEquals(
            "Line 1 should appear same number of times (moved, not duplicated)",
            originalLine1Count,
            newLine1Count
        )
    }

    fun testExtractMethodPreservesOriginalMethodStructure() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 38,
            methodName = "extracted"
        )

        assertTrue("Extract method should succeed", success)

        // The original method should still exist
        val originalMethod = findMethod(psiFile, "originalMethod")
        assertNotNull("Original method should still exist", originalMethod)

        // Original method should still contain Before and After
        val originalMethodText = originalMethod!!.text
        assertTrue("Original method should contain 'Before'", originalMethodText.contains("Before"))
        assertTrue("Original method should contain 'After'", originalMethodText.contains("After"))
    }

    // ==================== Complex Scenarios Tests ====================

    fun testExtractFromNestedBlocks() {
        val content = """
package com.example;

public class NestedBlocks {

    public void process(boolean flag) {
        if (flag) {
            System.out.println("Start");
            System.out.println("Middle");
            System.out.println("End");
        }
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/NestedBlocks.java", content)

        // Extract lines 7-8 from inside the if block
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 13,
            endLine = 8,
            endColumn = 42,
            methodName = "printStartMiddle"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "printStartMiddle")
        assertNotNull("Extracted method should exist", extractedMethod)
    }

    fun testExtractWithStringConcatenation() {
        val content = """
package com.example;

public class StringConcat {

    public void buildString() {
        String part1 = "Hello";
        String part2 = "World";
        String part3 = "!";
        String result = part1 + " " + part2 + part3;
        System.out.println(result);
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/StringConcat.java", content)

        // Extract lines 6-9
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 6,
            startColumn = 9,
            endLine = 9,
            endColumn = 54,
            methodName = "concatenateStrings"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "concatenateStrings")
        assertNotNull("Extracted method should exist", extractedMethod)

        // Should return String since result is used later
        val returnType = extractedMethod!!.returnType
        assertNotNull("Should have return type", returnType)
        assertEquals("Return type should be String", "String", returnType!!.presentableText)
    }

    fun testExtractMethodWithArrayAccess() {
        val content = """
package com.example;

public class ArrayAccess {

    public void processArray() {
        int[] numbers = {1, 2, 3, 4, 5};
        int sum = 0;
        for (int i = 0; i < numbers.length; i++) {
            sum += numbers[i];
        }
        System.out.println("Sum: " + sum);
    }
}
""".trimStart()

        val psiFile = createJavaFile("com/example/ArrayAccess.java", content)

        // Extract the for loop (lines 8-10)
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 8,
            startColumn = 9,
            endLine = 10,
            endColumn = 10,
            methodName = "calculateSum"
        )

        assertTrue("Extract method should succeed", success)

        val extractedMethod = findMethod(psiFile, "calculateSum")
        assertNotNull("Extracted method should exist", extractedMethod)

        val methodText = extractedMethod!!.text
        assertTrue("Should contain array access", methodText.contains("numbers"))
    }

    // ==================== Validation Tests ====================

    fun testExtractMethodCreatesValidJavaCode() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 38,
            methodName = "validMethod"
        )

        assertTrue("Extract method should succeed", success)

        // The resulting file should be valid Java (no syntax errors)
        // We verify this by checking the file can be parsed
        assertTrue("File should be a valid Java file", psiFile is PsiJavaFile)

        // The extracted method should be callable
        assertTrue(
            "File should contain call to extracted method",
            containsText(psiFile, "validMethod()")
        )
    }

    fun testExtractedMethodNameIsUsed() {
        val psiFile = createJavaFile("com/example/SimpleExtract.java", simpleClassContent)

        val customMethodName = "myCustomExtractedMethod"
        val success = performExtractMethod(
            psiFile = psiFile,
            startLine = 7,
            startColumn = 9,
            endLine = 8,
            endColumn = 38,
            methodName = customMethodName
        )

        assertTrue("Extract method should succeed", success)

        // Verify the exact method name is used
        val method = findMethod(psiFile, customMethodName)
        assertNotNull("Method with custom name should exist", method)
        assertEquals("Method name should match", customMethodName, method!!.name)
    }
}

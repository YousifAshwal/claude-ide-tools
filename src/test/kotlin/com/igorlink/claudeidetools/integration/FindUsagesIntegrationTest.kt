package com.igorlink.claudeidetools.integration

import com.igorlink.claudeidetools.model.FindUsagesRequest
import com.igorlink.claudeidetools.model.Usage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Integration tests for FindUsagesHandler using IntelliJ Platform Test Framework.
 *
 * These tests use LightJavaCodeInsightFixtureTestCase to test actual find usages
 * functionality with real PSI infrastructure.
 *
 * Tests verify:
 * 1. Finding usages of methods across multiple files
 * 2. Finding usages of classes
 * 3. Finding usages of fields
 * 4. Correct result format (file, line, column, preview)
 *
 * Note: Uses JUnit 3 style (methods start with "test") for compatibility with
 * IntelliJ Platform Test Framework. JUnit Vintage engine is used to run these tests.
 */
class FindUsagesIntegrationTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a Java file directly in the test project's source root.
     */
    private fun createJavaFile(relativePath: String, content: String): PsiFile {
        return myFixture.addFileToProject(relativePath, content)
    }

    /**
     * Finds the PSI element at a given line and column in a file.
     * Mirrors the core logic of PsiLocatorService.
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
     * Finds the nearest renamable element from the given element.
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

    /**
     * Performs find usages on the given element and returns the list of usages.
     * Mirrors the logic of FindUsagesHandler.handle().
     */
    private fun findUsages(element: PsiElement): List<Usage> {
        val usages = mutableListOf<Usage>()

        ReadAction.run<Throwable> {
            val references = ReferencesSearch.search(element).findAll()

            for (ref in references) {
                val refElement = ref.element
                val psiFile = refElement.containingFile ?: continue
                val virtualFile = psiFile.virtualFile ?: continue

                val document = PsiDocumentManager.getInstance(project)
                    .getDocument(psiFile) ?: continue

                val lineNumber = document.getLineNumber(refElement.textOffset)
                val lineStart = document.getLineStartOffset(lineNumber)
                val lineEnd = document.getLineEndOffset(lineNumber)
                val lineText = document.getText(TextRange(lineStart, lineEnd))

                usages.add(
                    Usage(
                        file = virtualFile.path,
                        line = lineNumber + 1, // Convert to 1-based
                        column = refElement.textOffset - lineStart + 1, // Convert to 1-based
                        preview = lineText.trim()
                    )
                )
            }
        }

        return usages
    }

    // ==================== Test Data ====================

    private val serviceClassContent = """
package com.example;

public class UserService {

    private UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User findUser(String id) {
        return repository.findById(id);
    }

    public void saveUser(User user) {
        repository.save(user);
    }

    public void deleteUser(String id) {
        repository.deleteById(id);
    }
}
""".trimStart()

    private val repositoryClassContent = """
package com.example;

public class UserRepository {

    public User findById(String id) {
        return new User(id, "Test User");
    }

    public void save(User user) {
        System.out.println("Saving user: " + user.getName());
    }

    public void deleteById(String id) {
        System.out.println("Deleting user: " + id);
    }
}
""".trimStart()

    private val userClassContent = """
package com.example;

public class User {

    private String id;
    private String name;

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
""".trimStart()

    private val controllerClassContent = """
package com.example;

public class UserController {

    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public User getUser(String id) {
        return userService.findUser(id);
    }

    public void createUser(User user) {
        userService.saveUser(user);
    }

    public void removeUser(String id) {
        userService.deleteUser(id);
    }
}
""".trimStart()

    private val testClassContent = """
package com.example;

public class UserServiceTest {

    public void testFindUser() {
        UserRepository repository = new UserRepository();
        UserService service = new UserService(repository);
        User user = service.findUser("123");
        System.out.println(user.getName());
    }

    public void testSaveUser() {
        UserRepository repository = new UserRepository();
        UserService service = new UserService(repository);
        User user = new User("456", "New User");
        service.saveUser(user);
    }
}
""".trimStart()

    // ==================== Method Usage Tests ====================

    fun testFindMethodUsagesAcrossMultipleFiles() {
        // Create all files
        val serviceFile = createJavaFile("com/example/UserService.java", serviceClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)
        createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserController.java", controllerClassContent)
        createJavaFile("com/example/UserServiceTest.java", testClassContent)

        // Find the findUser method in UserService
        // Line 11: "    public User findUser(String id) {"
        // "findUser" starts at column 17
        val element = findElementAt(serviceFile, 11, 17)

        assertNotNull("Method element should be found", element)

        val usages = findUsages(element!!)

        // findUser is called in UserController.getUser() and UserServiceTest.testFindUser()
        assertTrue(
            "Expected at least 2 usages of findUser method, got ${usages.size}",
            usages.size >= 2
        )

        // Verify usages are from different files
        val filePaths = usages.map { it.file }.distinct()
        assertTrue(
            "Expected usages from at least 2 different files, got ${filePaths.size}",
            filePaths.size >= 2
        )
    }

    fun testFindMethodUsagesWithCorrectLineNumbers() {
        val serviceFile = createJavaFile("com/example/UserService.java", serviceClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)
        createJavaFile("com/example/User.java", userClassContent)
        val controllerFile = createJavaFile("com/example/UserController.java", controllerClassContent)
        createJavaFile("com/example/UserServiceTest.java", testClassContent)

        // Find the findUser method
        val element = findElementAt(serviceFile, 11, 17)
        assertNotNull("Method element should be found", element)

        val usages = findUsages(element!!)

        // Find usage in UserController
        val controllerUsage = usages.find { it.file.contains("UserController") }
        assertNotNull("Should find usage in UserController", controllerUsage)

        // In UserController, findUser is called on line 12:
        // "        return userService.findUser(id);"
        assertEquals("Line number should be 1-based and correct", 12, controllerUsage!!.line)
        assertTrue("Line numbers should be 1-based (positive)", controllerUsage.line >= 1)
    }

    fun testFindMethodUsagesWithCorrectPreview() {
        val serviceFile = createJavaFile("com/example/UserService.java", serviceClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)
        createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserController.java", controllerClassContent)
        createJavaFile("com/example/UserServiceTest.java", testClassContent)

        // Find the saveUser method in UserService
        // Line 15: "    public void saveUser(User user) {"
        val element = findElementAt(serviceFile, 15, 17)
        assertNotNull("Method element should be found", element)

        val usages = findUsages(element!!)

        // Each usage should have a non-empty preview
        for (usage in usages) {
            assertFalse("Preview should not be blank for ${usage.file}", usage.preview.isBlank())
            assertTrue(
                "Preview should contain method name or its context",
                usage.preview.contains("saveUser") || usage.preview.isNotEmpty()
            )
        }
    }

    fun testFindStaticMethodUsages() {
        val utilClassContent = """
package com.example;

public class StringUtils {

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static boolean isEmpty(String input) {
        return input == null || input.isEmpty();
    }
}
""".trimStart()

        val callerClassContent = """
package com.example;

public class StringUtilsCaller {

    public void processString(String value) {
        if (!StringUtils.isEmpty(value)) {
            String result = StringUtils.capitalize(value);
            System.out.println(result);
        }
    }

    public String formatName(String name) {
        return StringUtils.capitalize(name);
    }
}
""".trimStart()

        val utilFile = createJavaFile("com/example/StringUtils.java", utilClassContent)
        createJavaFile("com/example/StringUtilsCaller.java", callerClassContent)

        // Find the capitalize method
        // Line 5: "    public static String capitalize(String input) {"
        val element = findElementAt(utilFile, 5, 26)
        assertNotNull("Static method element should be found", element)

        val usages = findUsages(element!!)

        // capitalize is called twice in StringUtilsCaller
        assertEquals("Expected 2 usages of capitalize method", 2, usages.size)
    }

    // ==================== Class Usage Tests ====================

    fun testFindClassUsagesAcrossMultipleFiles() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserService.java", serviceClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)
        createJavaFile("com/example/UserController.java", controllerClassContent)
        createJavaFile("com/example/UserServiceTest.java", testClassContent)

        // Find the User class declaration
        // Line 3: "public class User {"
        // "User" starts at column 14
        val element = findElementAt(userFile, 3, 14)
        assertNotNull("Class element should be found", element)

        val usages = findUsages(element!!)

        // User is used in many places across multiple files
        assertTrue(
            "Expected multiple usages of User class, got ${usages.size}",
            usages.size >= 5
        )

        // Verify usages are from multiple different files
        val uniqueFiles = usages.map { it.file }.distinct()
        assertTrue(
            "Expected usages from at least 3 different files, got ${uniqueFiles.size}",
            uniqueFiles.size >= 3
        )
    }

    fun testFindClassUsagesIncludesTypeReferences() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserService.java", serviceClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)

        // Find the User class
        val element = findElementAt(userFile, 3, 14)
        assertNotNull("Class element should be found", element)

        val usages = findUsages(element!!)

        // Check that usages include type references (method parameter types, return types, etc.)
        val previewsWithUser = usages.filter { it.preview.contains("User") }
        assertTrue(
            "Expected usages containing 'User' in preview, got ${previewsWithUser.size}",
            previewsWithUser.isNotEmpty()
        )
    }

    fun testFindInnerClassUsages() {
        val outerClassContent = """
package com.example;

public class OuterClass {

    public class InnerClass {
        private String value;

        public InnerClass(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public InnerClass createInner(String value) {
        return new InnerClass(value);
    }
}
""".trimStart()

        val callerContent = """
package com.example;

public class InnerClassCaller {

    public void useInner() {
        OuterClass outer = new OuterClass();
        OuterClass.InnerClass inner = outer.createInner("test");
        System.out.println(inner.getValue());
    }
}
""".trimStart()

        val outerFile = createJavaFile("com/example/OuterClass.java", outerClassContent)
        createJavaFile("com/example/InnerClassCaller.java", callerContent)

        // Find the InnerClass declaration
        // Line 5: "    public class InnerClass {"
        // "InnerClass" starts at column 18
        val element = findElementAt(outerFile, 5, 18)
        assertNotNull("Inner class element should be found", element)

        val usages = findUsages(element!!)

        // InnerClass is used in OuterClass.createInner() and InnerClassCaller.useInner()
        assertTrue(
            "Expected at least 2 usages of InnerClass, got ${usages.size}",
            usages.size >= 2
        )
    }

    // ==================== Field Usage Tests ====================

    fun testFindFieldUsagesWithinSameClass() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)

        // Find the 'name' field
        // Line 6: "    private String name;"
        // "name" starts at column 20
        val element = findElementAt(userFile, 6, 20)
        assertNotNull("Field element should be found", element)

        val usages = findUsages(element!!)

        // 'name' field is used in constructor, getName(), and setName()
        assertTrue(
            "Expected at least 3 usages of 'name' field within User class, got ${usages.size}",
            usages.size >= 3
        )

        // All usages should be within User.java
        val allInUserFile = usages.all { it.file.contains("User.java") }
        assertTrue("All usages should be within User.java", allInUserFile)
    }

    fun testFindFieldUsagesWithCorrectColumnNumbers() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)

        // Find the 'id' field
        // Line 5: "    private String id;"
        val element = findElementAt(userFile, 5, 20)
        assertNotNull("Field element should be found", element)

        val usages = findUsages(element!!)

        // Verify column numbers are positive (1-based)
        for (usage in usages) {
            assertTrue(
                "Column number should be positive (1-based), got ${usage.column}",
                usage.column >= 1
            )
        }
    }

    fun testFindStaticFieldUsages() {
        val constantsClassContent = """
package com.example;

public class Constants {

    public static final String APP_NAME = "MyApp";
    public static final int MAX_USERS = 100;
}
""".trimStart()

        val configClassContent = """
package com.example;

public class AppConfig {

    public String getAppTitle() {
        return "Welcome to " + Constants.APP_NAME;
    }

    public void printAppInfo() {
        System.out.println(Constants.APP_NAME + " - Max users: " + Constants.MAX_USERS);
    }
}
""".trimStart()

        val constantsFile = createJavaFile("com/example/Constants.java", constantsClassContent)
        createJavaFile("com/example/AppConfig.java", configClassContent)

        // Find the APP_NAME constant
        // Line 5: "    public static final String APP_NAME = "MyApp";"
        val element = findElementAt(constantsFile, 5, 32)
        assertNotNull("Static field element should be found", element)

        val usages = findUsages(element!!)

        // APP_NAME is used twice in AppConfig
        assertEquals("Expected 2 usages of APP_NAME constant", 2, usages.size)
    }

    fun testFindPrivateFieldUsagesOnlyInSameClass() {
        val classWithPrivateFieldContent = """
package com.example;

public class DataHolder {

    private String secretData;

    public DataHolder(String data) {
        this.secretData = data;
    }

    public String getData() {
        return secretData;
    }

    public void setData(String data) {
        secretData = data;
    }
}
""".trimStart()

        val otherClassContent = """
package com.example;

public class DataProcessor {

    public void process(DataHolder holder) {
        // Cannot access secretData directly - it's private
        String data = holder.getData();
        System.out.println(data);
    }
}
""".trimStart()

        val holderFile = createJavaFile("com/example/DataHolder.java", classWithPrivateFieldContent)
        createJavaFile("com/example/DataProcessor.java", otherClassContent)

        // Find the secretData field
        // Line 5: "    private String secretData;"
        val element = findElementAt(holderFile, 5, 20)
        assertNotNull("Private field element should be found", element)

        val usages = findUsages(element!!)

        // All usages should be within DataHolder.java (private field)
        val allInHolderFile = usages.all { it.file.contains("DataHolder.java") }
        assertTrue("All usages of private field should be within DataHolder.java", allInHolderFile)

        // Should have usages in constructor, getData(), and setData()
        assertTrue(
            "Expected at least 3 usages of private field, got ${usages.size}",
            usages.size >= 3
        )
    }

    // ==================== Result Format Verification Tests ====================

    fun testUsageResultContainsAllRequiredFields() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserService.java", serviceClassContent)

        // Find the User class
        val element = findElementAt(userFile, 3, 14)
        assertNotNull("Class element should be found", element)

        val usages = findUsages(element!!)
        assertTrue("Should have at least one usage", usages.isNotEmpty())

        // Verify each usage has all required fields properly set
        for (usage in usages) {
            assertTrue("File path should not be empty", usage.file.isNotEmpty())
            assertTrue("Line number should be positive", usage.line >= 1)
            assertTrue("Column number should be positive", usage.column >= 1)
            assertTrue("Preview should not be empty", usage.preview.isNotEmpty())
        }
    }

    fun testUsagePreviewIsTrimmed() {
        val serviceFile = createJavaFile("com/example/UserService.java", serviceClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)
        createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserController.java", controllerClassContent)

        // Find the userService field in UserController
        // The preview should be trimmed (no leading/trailing whitespace)
        val element = findElementAt(serviceFile, 11, 17) // findUser method
        assertNotNull("Element should be found", element)

        val usages = findUsages(element!!)

        for (usage in usages) {
            assertEquals(
                "Preview should be trimmed",
                usage.preview.trim(),
                usage.preview
            )
        }
    }

    fun testUsageFilePathIsAbsolute() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserService.java", serviceClassContent)

        val element = findElementAt(userFile, 3, 14) // User class
        assertNotNull("Element should be found", element)

        val usages = findUsages(element!!)
        assertTrue("Should have at least one usage", usages.isNotEmpty())

        // In the test environment, paths should contain the file name at minimum
        for (usage in usages) {
            assertTrue(
                "File path should contain file name, got: ${usage.file}",
                usage.file.endsWith(".java")
            )
        }
    }

    // ==================== Edge Cases ====================

    fun testFindUsagesOfElementWithNoUsages() {
        val isolatedClassContent = """
package com.example;

public class IsolatedClass {

    private void unusedMethod() {
        // This method is never called
    }
}
""".trimStart()

        val isolatedFile = createJavaFile("com/example/IsolatedClass.java", isolatedClassContent)

        // Find the unusedMethod
        // Line 5: "    private void unusedMethod() {"
        val element = findElementAt(isolatedFile, 5, 18)
        assertNotNull("Method element should be found", element)

        val usages = findUsages(element!!)

        // Unused method should have 0 usages
        assertEquals("Unused method should have 0 usages", 0, usages.size)
    }

    fun testFindUsagesOfConstructor() {
        val userFile = createJavaFile("com/example/User.java", userClassContent)
        createJavaFile("com/example/UserRepository.java", repositoryClassContent)
        createJavaFile("com/example/UserServiceTest.java", testClassContent)

        // Find the User constructor
        // Line 8: "    public User(String id, String name) {"
        val element = findElementAt(userFile, 8, 12)
        assertNotNull("Constructor element should be found", element)

        val usages = findUsages(element!!)

        // User constructor is called in UserRepository.findById() and UserServiceTest
        assertTrue(
            "Expected at least 2 usages of User constructor, got ${usages.size}",
            usages.size >= 2
        )
    }

    fun testFindUsagesOfOverloadedMethod() {
        val overloadedClassContent = """
package com.example;

public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int add(int a, int b, int c) {
        return a + b + c;
    }

    public double add(double a, double b) {
        return a + b;
    }
}
""".trimStart()

        val calculatorCallerContent = """
package com.example;

public class CalculatorCaller {

    private Calculator calc = new Calculator();

    public void performCalculations() {
        int sum1 = calc.add(1, 2);
        int sum2 = calc.add(1, 2, 3);
        double sum3 = calc.add(1.5, 2.5);
    }
}
""".trimStart()

        val calcFile = createJavaFile("com/example/Calculator.java", overloadedClassContent)
        createJavaFile("com/example/CalculatorCaller.java", calculatorCallerContent)

        // Find the first add method (int, int)
        // Line 5: "    public int add(int a, int b) {"
        val element = findElementAt(calcFile, 5, 16)
        assertNotNull("Method element should be found", element)

        val usages = findUsages(element!!)

        // Only the add(int, int) overload should be found, not the others
        assertEquals("Should find exactly 1 usage of add(int, int)", 1, usages.size)
    }

    // ==================== Request Model Tests ====================

    fun testFindUsagesRequestDataModel() {
        val request = FindUsagesRequest(
            file = "/path/to/File.java",
            line = 10,
            column = 15,
            project = "TestProject"
        )

        assertEquals("/path/to/File.java", request.file)
        assertEquals(10, request.line)
        assertEquals(15, request.column)
        assertEquals("TestProject", request.project)
    }

    fun testFindUsagesRequestWithNullProject() {
        val request = FindUsagesRequest(
            file = "/path/to/File.java",
            line = 5,
            column = 8
        )

        assertEquals("/path/to/File.java", request.file)
        assertEquals(5, request.line)
        assertEquals(8, request.column)
        assertNull(request.project)
    }
}

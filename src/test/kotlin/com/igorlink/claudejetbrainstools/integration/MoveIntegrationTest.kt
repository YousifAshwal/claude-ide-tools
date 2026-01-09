package com.igorlink.claudejetbrainstools.integration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Integration tests for move class refactoring using real IntelliJ infrastructure.
 *
 * These tests validate that the move refactoring correctly:
 * - Moves class to a different package
 * - Updates import statements in dependent files
 * - Handles classes with dependencies
 * - Updates fully qualified references
 *
 * Uses JUnit 3 style (methods start with "test") for compatibility with
 * IntelliJ Platform Test Framework. JUnit Vintage engine runs these tests.
 */
class MoveIntegrationTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    override fun setUp() {
        super.setUp()
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a Java file in the test project.
     */
    private fun createJavaFile(relativePath: String, content: String): PsiFile {
        return myFixture.addFileToProject(relativePath, content)
    }

    /**
     * Creates a package directory in the test project.
     */
    private fun createPackage(packageName: String): PsiDirectory {
        val parts = packageName.split(".")
        var currentDir = myFixture.tempDirFixture.getFile("")?.let {
            PsiManager.getInstance(project).findDirectory(it)
        } ?: error("Cannot find temp directory")

        for (part in parts) {
            val existingSubdir = currentDir.findSubdirectory(part)
            currentDir = existingSubdir ?: WriteCommandAction.writeCommandAction(project).compute<PsiDirectory, Throwable> {
                currentDir.createSubdirectory(part)
            }
        }
        return currentDir
    }

    /**
     * Performs move refactoring on a PsiClass to target package.
     */
    private fun performMove(psiClass: PsiClass, targetPackage: String) {
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val targetDir = findPackageDirectory(targetPackage)
                    ?: error("Target package directory not found: $targetPackage")

                val packageWrapper = PackageWrapper(PsiManager.getInstance(project), targetPackage)
                val destination = SingleSourceRootMoveDestination(packageWrapper, targetDir)

                val processor = MoveClassesOrPackagesProcessor(
                    project,
                    arrayOf(psiClass),
                    destination,
                    false, // searchInComments
                    false, // searchInNonJavaFiles
                    null   // moveCallback
                )
                processor.setPreviewUsages(false)
                processor.run()
            }
        }
    }

    /**
     * Finds package directory by package name.
     */
    private fun findPackageDirectory(packageName: String): PsiDirectory? {
        val existingPackage = JavaPsiFacade.getInstance(project).findPackage(packageName)
        return existingPackage?.directories?.firstOrNull()
    }

    /**
     * Finds a class by name in a PsiFile.
     */
    private fun findClass(psiFile: PsiFile, className: String): PsiClass? {
        var result: PsiClass? = null
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (aClass.name == className) {
                    result = aClass
                }
                super.visitClass(aClass)
            }
        })
        return result
    }

    /**
     * Finds a class by fully qualified name in the project.
     */
    private fun findClassByFqn(fqn: String): PsiClass? {
        return JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.projectScope(project))
    }

    /**
     * Checks if file contains text.
     */
    private fun containsText(psiFile: PsiFile, text: String): Boolean {
        return psiFile.text.contains(text)
    }

    /**
     * Refreshes the PSI file to get current content.
     */
    private fun refreshFile(psiFile: PsiFile): PsiFile {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return psiFile.virtualFile?.let {
            PsiManager.getInstance(project).findFile(it)
        } ?: psiFile
    }

    // ==================== Test Data ====================

    private val simpleClassContent = """
package com.example.source;

public class SimpleClass {

    private String name;

    public SimpleClass(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
""".trimStart()

    private val dependentClassContent = """
package com.example.client;

import com.example.source.SimpleClass;

public class DependentClass {

    private SimpleClass simpleClass;

    public DependentClass() {
        this.simpleClass = new SimpleClass("test");
    }

    public String getSimpleName() {
        return simpleClass.getName();
    }
}
""".trimStart()

    private val classWithMultipleDependencies = """
package com.example.service;

import com.example.model.User;
import com.example.model.Address;

public class UserService {

    public User createUser(String name, Address address) {
        User user = new User(name);
        user.setAddress(address);
        return user;
    }

    public void printUserInfo(User user) {
        Address addr = user.getAddress();
        System.out.println(user.getName() + " - " + addr.getCity());
    }
}
""".trimStart()

    private val userClassContent = """
package com.example.model;

public class User {

    private String name;
    private Address address;

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
""".trimStart()

    private val addressClassContent = """
package com.example.model;

public class Address {

    private String city;
    private String street;

    public Address(String city, String street) {
        this.city = city;
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public String getStreet() {
        return street;
    }
}
""".trimStart()

    private val classWithFullyQualifiedReferences = """
package com.example.fqn;

public class FqnReferenceClass {

    public com.example.source.SimpleClass createSimple() {
        return new com.example.source.SimpleClass("fqn");
    }

    public void useSimple(com.example.source.SimpleClass simple) {
        System.out.println(simple.getName());
    }
}
""".trimStart()

    private val interfaceContent = """
package com.example.api;

public interface Processor {

    void process(String input);

    String getResult();
}
""".trimStart()

    private val interfaceImplContent = """
package com.example.impl;

import com.example.api.Processor;

public class ProcessorImpl implements Processor {

    private String result;

    @Override
    public void process(String input) {
        this.result = input.toUpperCase();
    }

    @Override
    public String getResult() {
        return result;
    }
}
""".trimStart()

    private val interfaceClientContent = """
package com.example.client;

import com.example.api.Processor;
import com.example.impl.ProcessorImpl;

public class ProcessorClient {

    private Processor processor;

    public ProcessorClient() {
        this.processor = new ProcessorImpl();
    }

    public void doProcess(String input) {
        processor.process(input);
    }
}
""".trimStart()

    // ==================== Basic Move Tests ====================

    fun testMoveClassToNewPackageUpdatesPackageDeclaration() {
        // Create source package and target package
        createPackage("com.example.source")
        createPackage("com.example.target")

        val sourceFile = createJavaFile("com/example/source/SimpleClass.java", simpleClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val psiClass = findClass(sourceFile, "SimpleClass")
        assertNotNull("SimpleClass should be found", psiClass)

        performMove(psiClass!!, "com.example.target")

        // Find the moved class by FQN
        val movedClass = findClassByFqn("com.example.target.SimpleClass")
        assertNotNull("Class should be found in target package", movedClass)

        // Verify package declaration is updated
        val movedFile = movedClass!!.containingFile
        assertTrue(
            "File should contain 'package com.example.target'",
            containsText(movedFile, "package com.example.target")
        )
        assertFalse(
            "File should not contain 'package com.example.source'",
            containsText(movedFile, "package com.example.source")
        )
    }

    fun testMoveClassToNewPackagePreservesClassContent() {
        createPackage("com.example.source")
        createPackage("com.example.target")

        val sourceFile = createJavaFile("com/example/source/SimpleClass.java", simpleClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val psiClass = findClass(sourceFile, "SimpleClass")
        assertNotNull("SimpleClass should be found", psiClass)

        performMove(psiClass!!, "com.example.target")

        val movedClass = findClassByFqn("com.example.target.SimpleClass")
        assertNotNull("Class should be found in target package", movedClass)

        val movedFile = movedClass!!.containingFile

        // Verify class content is preserved
        assertTrue("File should contain class declaration", containsText(movedFile, "public class SimpleClass"))
        assertTrue("File should contain field", containsText(movedFile, "private String name"))
        assertTrue("File should contain constructor", containsText(movedFile, "public SimpleClass(String name)"))
        assertTrue("File should contain method", containsText(movedFile, "public String getName()"))
    }

    // ==================== Import Update Tests ====================

    fun testMoveClassUpdatesImportInDependentFile() {
        createPackage("com.example.source")
        createPackage("com.example.target")
        createPackage("com.example.client")

        val sourceFile = createJavaFile("com/example/source/SimpleClass.java", simpleClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val dependentFile = createJavaFile("com/example/client/DependentClass.java", dependentClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Verify initial import
        assertTrue(
            "DependentClass should initially import from source package",
            containsText(dependentFile, "import com.example.source.SimpleClass")
        )

        val psiClass = findClass(sourceFile, "SimpleClass")
        assertNotNull("SimpleClass should be found", psiClass)

        performMove(psiClass!!, "com.example.target")

        // Refresh the dependent file to get updated content
        val refreshedDependentFile = refreshFile(dependentFile)

        // Verify import is updated
        assertTrue(
            "DependentClass should import from target package after move",
            containsText(refreshedDependentFile, "import com.example.target.SimpleClass")
        )
        assertFalse(
            "DependentClass should not import from source package after move",
            containsText(refreshedDependentFile, "import com.example.source.SimpleClass")
        )
    }

    fun testMoveClassPreservesUsagesInDependentFile() {
        createPackage("com.example.source")
        createPackage("com.example.target")
        createPackage("com.example.client")

        val sourceFile = createJavaFile("com/example/source/SimpleClass.java", simpleClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val dependentFile = createJavaFile("com/example/client/DependentClass.java", dependentClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val psiClass = findClass(sourceFile, "SimpleClass")
        assertNotNull("SimpleClass should be found", psiClass)

        performMove(psiClass!!, "com.example.target")

        val refreshedDependentFile = refreshFile(dependentFile)

        // Verify usages are preserved (not broken)
        assertTrue(
            "DependentClass should still have SimpleClass field",
            containsText(refreshedDependentFile, "private SimpleClass simpleClass")
        )
        assertTrue(
            "DependentClass should still have SimpleClass instantiation",
            containsText(refreshedDependentFile, "new SimpleClass(")
        )
        assertTrue(
            "DependentClass should still call getName()",
            containsText(refreshedDependentFile, "simpleClass.getName()")
        )
    }

    // ==================== Multiple Dependencies Tests ====================

    fun testMoveClassWithMultipleDependentsUpdatesAllImports() {
        createPackage("com.example.source")
        createPackage("com.example.target")
        createPackage("com.example.client1")
        createPackage("com.example.client2")

        val sourceFile = createJavaFile("com/example/source/SimpleClass.java", simpleClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val client1Content = """
package com.example.client1;

import com.example.source.SimpleClass;

public class Client1 {
    private SimpleClass simple;

    public Client1() {
        simple = new SimpleClass("client1");
    }
}
""".trimStart()

        val client2Content = """
package com.example.client2;

import com.example.source.SimpleClass;

public class Client2 {
    public void process(SimpleClass simple) {
        System.out.println(simple.getName());
    }
}
""".trimStart()

        val client1File = createJavaFile("com/example/client1/Client1.java", client1Content)
        val client2File = createJavaFile("com/example/client2/Client2.java", client2Content)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val psiClass = findClass(sourceFile, "SimpleClass")
        assertNotNull("SimpleClass should be found", psiClass)

        performMove(psiClass!!, "com.example.target")

        val refreshedClient1 = refreshFile(client1File)
        val refreshedClient2 = refreshFile(client2File)

        // Verify both clients have updated imports
        assertTrue(
            "Client1 should import from target package",
            containsText(refreshedClient1, "import com.example.target.SimpleClass")
        )
        assertTrue(
            "Client2 should import from target package",
            containsText(refreshedClient2, "import com.example.target.SimpleClass")
        )

        // Verify neither client imports from source package
        assertFalse(
            "Client1 should not import from source package",
            containsText(refreshedClient1, "import com.example.source.SimpleClass")
        )
        assertFalse(
            "Client2 should not import from source package",
            containsText(refreshedClient2, "import com.example.source.SimpleClass")
        )
    }

    fun testMoveClassDependingOnOtherClassesPreservesDependencies() {
        createPackage("com.example.model")
        createPackage("com.example.domain")
        createPackage("com.example.service")

        val userFile = createJavaFile("com/example/model/User.java", userClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val addressFile = createJavaFile("com/example/model/Address.java", addressClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val serviceFile = createJavaFile("com/example/service/UserService.java", classWithMultipleDependencies)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Move User class to domain package
        val userClass = findClass(userFile, "User")
        assertNotNull("User class should be found", userClass)

        performMove(userClass!!, "com.example.domain")

        val refreshedService = refreshFile(serviceFile)

        // Verify UserService imports are updated
        assertTrue(
            "UserService should import User from domain package",
            containsText(refreshedService, "import com.example.domain.User")
        )
        // Address import should remain unchanged
        assertTrue(
            "UserService should still import Address from model package",
            containsText(refreshedService, "import com.example.model.Address")
        )

        // Verify User class imports Address correctly in new location
        val movedUserClass = findClassByFqn("com.example.domain.User")
        assertNotNull("Moved User class should be found", movedUserClass)
        val movedUserFile = movedUserClass!!.containingFile

        assertTrue(
            "Moved User should import Address",
            containsText(movedUserFile, "import com.example.model.Address")
        )
    }

    // ==================== Fully Qualified References Tests ====================

    fun testMoveClassUpdatesFullyQualifiedReferences() {
        createPackage("com.example.source")
        createPackage("com.example.target")
        createPackage("com.example.fqn")

        val sourceFile = createJavaFile("com/example/source/SimpleClass.java", simpleClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val fqnFile = createJavaFile("com/example/fqn/FqnReferenceClass.java", classWithFullyQualifiedReferences)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val psiClass = findClass(sourceFile, "SimpleClass")
        assertNotNull("SimpleClass should be found", psiClass)

        performMove(psiClass!!, "com.example.target")

        val refreshedFqnFile = refreshFile(fqnFile)
        val fileText = refreshedFqnFile.text

        // IntelliJ may either update FQN references or optimize to use imports
        // Either way, it should reference the class from target package
        val hasUpdatedFqn = fileText.contains("com.example.target.SimpleClass")
        val hasImportFromTarget = fileText.contains("import com.example.target.SimpleClass")

        assertTrue(
            "File should have either updated FQN or import from target package. Content: $fileText",
            hasUpdatedFqn || hasImportFromTarget
        )

        // Verify old references are gone - this is the main assertion
        assertFalse(
            "Old FQN should not be present",
            containsText(refreshedFqnFile, "com.example.source.SimpleClass")
        )
    }

    // ==================== Interface Implementation Tests ====================

    fun testMoveInterfaceUpdatesImplementorImports() {
        createPackage("com.example.api")
        createPackage("com.example.core")
        createPackage("com.example.impl")
        createPackage("com.example.client")

        val interfaceFile = createJavaFile("com/example/api/Processor.java", interfaceContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val implFile = createJavaFile("com/example/impl/ProcessorImpl.java", interfaceImplContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val clientFile = createJavaFile("com/example/client/ProcessorClient.java", interfaceClientContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val interfaceClass = findClass(interfaceFile, "Processor")
        assertNotNull("Processor interface should be found", interfaceClass)

        performMove(interfaceClass!!, "com.example.core")

        val refreshedImplFile = refreshFile(implFile)
        val refreshedClientFile = refreshFile(clientFile)

        // Verify implementation imports updated interface from new location
        assertTrue(
            "ProcessorImpl should import Processor from core package",
            containsText(refreshedImplFile, "import com.example.core.Processor")
        )
        assertFalse(
            "ProcessorImpl should not import Processor from api package",
            containsText(refreshedImplFile, "import com.example.api.Processor")
        )

        // Verify client imports updated interface from new location
        assertTrue(
            "ProcessorClient should import Processor from core package",
            containsText(refreshedClientFile, "import com.example.core.Processor")
        )
        assertFalse(
            "ProcessorClient should not import Processor from api package",
            containsText(refreshedClientFile, "import com.example.api.Processor")
        )

        // Verify implements clause is preserved
        assertTrue(
            "ProcessorImpl should still implement Processor",
            containsText(refreshedImplFile, "implements Processor")
        )
    }

    fun testMoveImplementationUpdatesClientImports() {
        createPackage("com.example.api")
        createPackage("com.example.impl")
        createPackage("com.example.internal")
        createPackage("com.example.client")

        val interfaceFile = createJavaFile("com/example/api/Processor.java", interfaceContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val implFile = createJavaFile("com/example/impl/ProcessorImpl.java", interfaceImplContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val clientFile = createJavaFile("com/example/client/ProcessorClient.java", interfaceClientContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val implClass = findClass(implFile, "ProcessorImpl")
        assertNotNull("ProcessorImpl class should be found", implClass)

        performMove(implClass!!, "com.example.internal")

        val refreshedClientFile = refreshFile(clientFile)

        // Verify client imports updated implementation from new location
        assertTrue(
            "ProcessorClient should import ProcessorImpl from internal package",
            containsText(refreshedClientFile, "import com.example.internal.ProcessorImpl")
        )
        assertFalse(
            "ProcessorClient should not import ProcessorImpl from impl package",
            containsText(refreshedClientFile, "import com.example.impl.ProcessorImpl")
        )

        // Verify interface import is unchanged
        assertTrue(
            "ProcessorClient should still import Processor from api package",
            containsText(refreshedClientFile, "import com.example.api.Processor")
        )
    }

    // ==================== Inner Class Tests ====================

    fun testMoveOuterClassPreservesInnerClass() {
        createPackage("com.example.source")
        createPackage("com.example.target")

        val outerClassContent = """
package com.example.source;

public class OuterClass {

    private InnerClass inner;

    public class InnerClass {
        private String value;

        public InnerClass(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public OuterClass() {
        this.inner = new InnerClass("test");
    }

    public InnerClass getInner() {
        return inner;
    }
}
""".trimStart()

        val sourceFile = createJavaFile("com/example/source/OuterClass.java", outerClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val outerClass = findClass(sourceFile, "OuterClass")
        assertNotNull("OuterClass should be found", outerClass)

        performMove(outerClass!!, "com.example.target")

        val movedOuterClass = findClassByFqn("com.example.target.OuterClass")
        assertNotNull("Moved OuterClass should be found", movedOuterClass)

        val movedFile = movedOuterClass!!.containingFile

        // Verify inner class is preserved
        assertTrue(
            "Moved file should contain InnerClass",
            containsText(movedFile, "public class InnerClass")
        )
        assertTrue(
            "Moved file should have correct package",
            containsText(movedFile, "package com.example.target")
        )
    }

    fun testMoveOuterClassUpdatesInnerClassReferences() {
        createPackage("com.example.source")
        createPackage("com.example.target")
        createPackage("com.example.client")

        val outerClassContent = """
package com.example.source;

public class OuterClass {

    public static class StaticNested {
        public String getData() {
            return "nested";
        }
    }
}
""".trimStart()

        val clientContent = """
package com.example.client;

import com.example.source.OuterClass;

public class NestedClient {

    public void useNested() {
        OuterClass.StaticNested nested = new OuterClass.StaticNested();
        System.out.println(nested.getData());
    }
}
""".trimStart()

        val sourceFile = createJavaFile("com/example/source/OuterClass.java", outerClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val clientFile = createJavaFile("com/example/client/NestedClient.java", clientContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val outerClass = findClass(sourceFile, "OuterClass")
        assertNotNull("OuterClass should be found", outerClass)

        performMove(outerClass!!, "com.example.target")

        val refreshedClientFile = refreshFile(clientFile)

        // Verify import is updated
        assertTrue(
            "Client should import OuterClass from target package",
            containsText(refreshedClientFile, "import com.example.target.OuterClass")
        )

        // Verify nested class references are preserved (they reference via OuterClass)
        assertTrue(
            "Client should reference StaticNested via OuterClass",
            containsText(refreshedClientFile, "OuterClass.StaticNested")
        )
    }

    // ==================== Same Package Move Tests ====================

    fun testMoveClassBetweenSubpackagesUpdatesImports() {
        createPackage("com.example.domain.v1")
        createPackage("com.example.domain.v2")
        createPackage("com.example.service")

        val v1ClassContent = """
package com.example.domain.v1;

public class DomainObject {

    private String id;

    public DomainObject(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
""".trimStart()

        val serviceContent = """
package com.example.service;

import com.example.domain.v1.DomainObject;

public class DomainService {

    public DomainObject create(String id) {
        return new DomainObject(id);
    }
}
""".trimStart()

        val v1File = createJavaFile("com/example/domain/v1/DomainObject.java", v1ClassContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val serviceFile = createJavaFile("com/example/service/DomainService.java", serviceContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val domainClass = findClass(v1File, "DomainObject")
        assertNotNull("DomainObject should be found", domainClass)

        performMove(domainClass!!, "com.example.domain.v2")

        val refreshedServiceFile = refreshFile(serviceFile)

        // Verify import is updated to v2
        assertTrue(
            "Service should import DomainObject from v2 package",
            containsText(refreshedServiceFile, "import com.example.domain.v2.DomainObject")
        )
        assertFalse(
            "Service should not import DomainObject from v1 package",
            containsText(refreshedServiceFile, "import com.example.domain.v1.DomainObject")
        )
    }

    // ==================== Static Import Tests ====================

    fun testMoveClassWithStaticMembersUpdatesStaticImports() {
        createPackage("com.example.util")
        createPackage("com.example.common")
        createPackage("com.example.client")

        val utilsContent = """
package com.example.util;

public class StringUtils {

    public static final String EMPTY = "";

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static boolean isBlank(String input) {
        return input == null || input.trim().isEmpty();
    }
}
""".trimStart()

        val clientWithStaticImportsContent = """
package com.example.client;

import static com.example.util.StringUtils.capitalize;
import static com.example.util.StringUtils.EMPTY;

public class StringClient {

    public String process(String input) {
        if (input == null) {
            return EMPTY;
        }
        return capitalize(input);
    }
}
""".trimStart()

        val utilsFile = createJavaFile("com/example/util/StringUtils.java", utilsContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val clientFile = createJavaFile("com/example/client/StringClient.java", clientWithStaticImportsContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val utilsClass = findClass(utilsFile, "StringUtils")
        assertNotNull("StringUtils should be found", utilsClass)

        performMove(utilsClass!!, "com.example.common")

        val refreshedClientFile = refreshFile(clientFile)

        // Verify static imports are updated
        assertTrue(
            "Client should have static import capitalize from common package",
            containsText(refreshedClientFile, "import static com.example.common.StringUtils.capitalize")
        )
        assertTrue(
            "Client should have static import EMPTY from common package",
            containsText(refreshedClientFile, "import static com.example.common.StringUtils.EMPTY")
        )

        // Verify old static imports are removed
        assertFalse(
            "Client should not have static import from util package",
            containsText(refreshedClientFile, "import static com.example.util.StringUtils")
        )
    }

    // ==================== Circular Dependency Tests ====================

    fun testMoveClassWithCircularDependencyUpdatesCorrectly() {
        createPackage("com.example.a")
        createPackage("com.example.b")
        createPackage("com.example.c")

        val classAContent = """
package com.example.a;

import com.example.b.ClassB;

public class ClassA {

    private ClassB classB;

    public void setClassB(ClassB classB) {
        this.classB = classB;
    }

    public String getInfo() {
        return "A";
    }
}
""".trimStart()

        val classBContent = """
package com.example.b;

import com.example.a.ClassA;

public class ClassB {

    private ClassA classA;

    public void setClassA(ClassA classA) {
        this.classA = classA;
    }

    public String getInfo() {
        return "B referencing " + classA.getInfo();
    }
}
""".trimStart()

        val classAFile = createJavaFile("com/example/a/ClassA.java", classAContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val classBFile = createJavaFile("com/example/b/ClassB.java", classBContent)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val classA = findClass(classAFile, "ClassA")
        assertNotNull("ClassA should be found", classA)

        performMove(classA!!, "com.example.c")

        val refreshedClassBFile = refreshFile(classBFile)
        val movedClassA = findClassByFqn("com.example.c.ClassA")
        assertNotNull("Moved ClassA should be found", movedClassA)
        val movedClassAFile = movedClassA!!.containingFile

        // Verify ClassB imports ClassA from new location
        assertTrue(
            "ClassB should import ClassA from c package",
            containsText(refreshedClassBFile, "import com.example.c.ClassA")
        )

        // Verify moved ClassA still imports ClassB correctly
        assertTrue(
            "Moved ClassA should import ClassB from b package",
            containsText(movedClassAFile, "import com.example.b.ClassB")
        )
    }
}

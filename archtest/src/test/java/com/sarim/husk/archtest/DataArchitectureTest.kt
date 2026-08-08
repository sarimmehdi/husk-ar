package com.sarim.husk.archtest

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaConstructor
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.Before
import org.junit.Test
import java.io.File

class DataArchitectureTest : ArchitectureTestSupport() {
    private lateinit var dataClasses: JavaClasses

    @Before
    fun importClasses() {
        dataClasses = importLayer("data")
    }

    @Test
    fun `data - dao interfaces must have Dao suffix`() {
        classes()
            .that()
            .resideInAPackage("..dao..")
            .and()
            .areInterfaces()
            .should()
            .haveSimpleNameEndingWith("Dao")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - dao interfaces must be public`() {
        classes()
            .that()
            .resideInAPackage("..dao..")
            .and()
            .areInterfaces()
            .should()
            .bePublic()
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - dao interfaces must reside directly in the feature root dot dao package`() {
        classes()
            .that()
            .areInterfaces()
            .and()
            .haveSimpleNameEndingWith("Dao")
            .should(resideInDirectSubPackageNamed())
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - FeatureConverter must be internal`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("FeatureConverter")
            .should()
            .bePublic()
            .because("FeatureConverter must be Kotlin internal (public in bytecode with compiler-enforced access)")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - FeatureConverter must reside in database package`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("FeatureConverter")
            .should()
            .resideInAPackage("..database")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - RoomDatabase subclasses must be public`() {
        classes()
            .that()
            .areAssignableTo("androidx.room.RoomDatabase")
            .and()
            .areNotAssignableFrom("androidx.room.RoomDatabase")
            .should()
            .bePublic()
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - RoomDatabase subclasses must reside in database package`() {
        classes()
            .that()
            .areAssignableTo("androidx.room.RoomDatabase")
            .and()
            .areNotAssignableFrom("androidx.room.RoomDatabase")
            .should()
            .resideInAPackage("..database")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - DatabaseCallback classes must reside in database package`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("DatabaseCallback")
            .and()
            .arePublic()
            .should()
            .resideInAPackage("..database")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - Entity classes must be public and reside in entity package`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("Entity")
            .should()
            .bePublic()
            .andShould()
            .resideInAPackage("..entity")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - repository package must only contain classes ending with RepositoryImpl`() {
        classes()
            .that()
            .resideInAPackage("..repository")
            .and()
            .areNotInterfaces()
            .and(notASyntheticInnerClass())
            .and(notAKotlinFileFacade())
            .should()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - RepositoryImpl classes must be public`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .should()
            .bePublic()
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - RepositoryImpl classes must implement an interface ending with Repository`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .should(implementInterfaceEndingWith("Repository"))
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - serializer package must only contain public objects ending with Serializer`() {
        classes()
            .that()
            .resideInAPackage("..serializer")
            .and(notASyntheticInnerClass())
            .should()
            .haveSimpleNameEndingWith("Serializer")
            .andShould()
            .bePublic()
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - Serializer objects must implement androidx datastore Serializer`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("Serializer")
            .and(notASyntheticInnerClass())
            .should(implementInterfaceEndingWith("Serializer"))
            .allowEmptyShould(true)
            .check(dataClasses)
    }

    @Test
    fun `data - Android Context must never be passed as an argument`() {
        classes()
            .should(notAcceptAndroidContextAsParameter())
            .check(dataClasses)
    }
}

private val CLASS_DIR_SUFFIXES =
    listOf(
        "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
    )

private val SOURCE_EXTENSIONS = setOf("kt", "java")

abstract class ArchitectureTestSupport {
    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir system property is not set"))
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile ?: break
        }
        dir
    }

    protected fun importModule(vararg moduleRelativePaths: String): JavaClasses {
        val unbuilt = mutableListOf<String>()
        val classDirs =
            moduleRelativePaths
                .flatMap { modulePath ->
                    val dirs =
                        CLASS_DIR_SUFFIXES
                            .map { suffix -> File(projectRoot, "$modulePath/$suffix") }
                            .filter(File::isDirectory)
                    // A module with sources but nothing compiled would otherwise be dropped from the
                    // import and its rules quietly never checked, while the suite still passed on
                    // whichever modules did happen to be built.
                    if (dirs.isEmpty() && hasSources(modulePath)) {
                        unbuilt += modulePath
                    }
                    dirs
                }.map(File::toPath)

        check(unbuilt.isEmpty()) {
            "These modules have sources but no compiled classes, so their architecture would go " +
                "unverified: " + unbuilt + ". Build them first (./gradlew assemble)."
        }

        check(classDirs.isNotEmpty()) {
            "No compiled class directories found under $projectRoot for: " +
                moduleRelativePaths.toList() +
                ". Ensure the modules are compiled (./gradlew assemble) before running arch tests."
        }

        return ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPaths(classDirs)
    }

    /** Whether a module has anything for the compiler to produce classes from. */
    private fun hasSources(modulePath: String): Boolean {
        val main = File(projectRoot, "$modulePath/src/main")
        return main.isDirectory &&
            main
                .walkTopDown()
                .any { it.isFile && it.extension in SOURCE_EXTENSIONS }
    }

    protected fun importLayer(layer: String): JavaClasses {
        val modules =
            projectRoot
                .walkTopDown()
                .onEnter { directory -> directory.name !in setOf("build", ".gradle", ".git") }
                .filter { directory ->
                    directory.isDirectory &&
                        directory.name == layer &&
                        File(directory, "build.gradle.kts").isFile
                }.map { directory -> directory.relativeTo(projectRoot).invariantSeparatorsPath }
                .toList()

        check(modules.isNotEmpty()) {
            "No '$layer' modules found below $projectRoot. Generate a feature containing that layer first."
        }
        return importModule(*modules.toTypedArray())
    }

    protected fun notASyntheticInnerClass(): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("not a synthetic inner/anonymous class") {
            override fun test(item: JavaClass): Boolean = '$' !in item.name
        }

    protected fun notAKotlinFileFacade(): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("not a Kotlin file facade") {
            override fun test(item: JavaClass): Boolean =
                !(
                    item.simpleName.endsWith("Kt") &&
                        item.isAnnotatedWith("kotlin.Metadata")
                )
        }

    protected fun notAnAndroidGeneratedClass(): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("not an Android generated class") {
            override fun test(item: JavaClass): Boolean = item.simpleName !in setOf("BuildConfig", "R")
        }

    protected fun resideInDirectSubPackageNamed(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("reside in a direct sub-package named 'dao'") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val pkg = item.packageName
                val satisfied = pkg.split(".").lastOrNull() == "dao"
                events.add(SimpleConditionEvent(item, satisfied, "${item.name} resides in package '$pkg'"))
            }
        }

    protected fun implementInterfaceEndingWith(suffix: String): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("implement an interface whose name ends with '$suffix'") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val satisfied = item.rawInterfaces.any { it.name.endsWith(suffix) }
                val message = "${item.name} raw interfaces: ${item.rawInterfaces.map { it.name }}"
                events.add(SimpleConditionEvent(item, satisfied, message))
            }
        }

    protected fun beDataClassOrEnum(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("be a data class or enum class") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val satisfied = item.isEnum || item.methods.any { it.name.startsWith("component") }
                val type = if (satisfied) "a data/enum class" else "neither a data class nor an enum"
                events.add(SimpleConditionEvent(item, satisfied, "${item.name} is $type"))
            }
        }

    protected fun haveOnlyUseCaseMembers(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("have only fields whose types end with UseCase") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val violations = item.fields.filter { !it.name.startsWith("$") && !it.type.name.endsWith("UseCase") }
                val message =
                    if (violations.isEmpty()) {
                        "${item.name} fields all end with UseCase"
                    } else {
                        "${item.name} has fields with non-UseCase types: ${violations.map { it.name }}"
                    }
                events.add(SimpleConditionEvent(item, violations.isEmpty(), message))
            }
        }

    protected fun notAcceptAndroidContextAsParameter(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("not accept Android Context as a constructor or method argument") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val violations =
                    item.codeUnits
                        .filter { codeUnit ->
                            codeUnit.rawParameterTypes.any { parameterType ->
                                parameterType.isAssignableTo(ANDROID_CONTEXT)
                            }
                        }.filterNot { codeUnit ->
                            codeUnit.isRequiredAndroidFrameworkEntryPoint(item)
                        }
                val message =
                    if (violations.isEmpty()) {
                        "${item.name} does not accept Android Context arguments"
                    } else {
                        "${item.name} accepts Android Context in: ${violations.map { it.fullName }}"
                    }
                events.add(SimpleConditionEvent(item, violations.isEmpty(), message))
            }
        }

    private companion object {
        const val ANDROID_CONTEXT = "android.content.Context"
        const val ANDROID_BROADCAST_RECEIVER = "android.content.BroadcastReceiver"
        const val ANDROID_WORKER_PARAMETERS = "androidx.work.WorkerParameters"
    }

    private fun JavaCodeUnit.isRequiredAndroidFrameworkEntryPoint(owner: JavaClass): Boolean =
        (
            owner.simpleName.endsWith("Worker") &&
                this is JavaConstructor &&
                rawParameterTypes.any { it.name == ANDROID_WORKER_PARAMETERS }
        ) ||
            (owner.isAssignableTo(ANDROID_BROADCAST_RECEIVER) && name == "onReceive")
}

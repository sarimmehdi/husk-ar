package com.sarim.husk.archtest

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Before
import org.junit.Test

class DiArchitectureTest : ArchitectureTestSupport() {
    private lateinit var diClasses: JavaClasses

    @Before
    fun importClasses() {
        diClasses = importLayer("di")
    }

    @Test
    fun `di - non-public declarations must be private`() {
        noClasses()
            .that()
            .resideInAPackage("..di")
            .and()
            .haveSimpleNameNotEndingWith("Module")
            .and()
            .haveSimpleNameNotEndingWith("ModuleKt")
            .and()
            .haveSimpleNameNotContaining("ComposableSingletons")
            .and(notASyntheticInnerClass())
            .and(notAKotlinFileFacade())
            .and(notAnAndroidGeneratedClass())
            .and()
            .arePublic()
            .should()
            .bePublic()
            .allowEmptyShould(true)
            .because("DI modules must expose only the module val (ModuleKt); all helpers must be private")
            .check(diClasses)
    }
}

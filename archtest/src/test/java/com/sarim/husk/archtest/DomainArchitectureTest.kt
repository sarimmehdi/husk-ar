package com.sarim.husk.archtest

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.Before
import org.junit.Test

class DomainArchitectureTest : ArchitectureTestSupport() {
    private lateinit var domainClasses: JavaClasses

    @Before
    fun importClasses() {
        domainClasses = importLayer("domain")
    }

    @Test
    fun `domain - model package must only contain public data classes or enum classes`() {
        classes()
            .that()
            .resideInAPackage("..model")
            .and(notASyntheticInnerClass())
            .should(beDataClassOrEnum())
            .andShould()
            .bePublic()
            .allowEmptyShould(true)
            .check(domainClasses)
    }

    @Test
    fun `domain - repository package must only contain public interfaces ending with Repository`() {
        classes()
            .that()
            .resideInAPackage("..repository")
            .should()
            .beInterfaces()
            .andShould()
            .bePublic()
            .andShould()
            .haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true)
            .check(domainClasses)
    }

    @Test
    fun `domain - usecase package must only contain public classes ending with UseCase`() {
        classes()
            .that()
            .resideInAPackage("..usecase")
            .and(notASyntheticInnerClass())
            .and(notAKotlinFileFacade())
            .should()
            .notBeInterfaces()
            .andShould()
            .bePublic()
            .andShould()
            .haveSimpleNameEndingWith("UseCase")
            .allowEmptyShould(true)
            .check(domainClasses)
    }
}

package com.sarim.husk.archtest

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.Before
import org.junit.Test

class PresentationArchitectureTest : ArchitectureTestSupport() {
    private lateinit var presentationClasses: JavaClasses

    @Before
    fun importClasses() {
        presentationClasses = importLayer("presentation")
    }

    @Test
    fun `presentation - component package files must be internal`() {
        classes()
            .that()
            .resideInAPackage("..component")
            .and()
            .haveSimpleNameEndingWith("Component")
            .and(notASyntheticInnerClass())
            .should()
            .bePublic()
            .because("Component classes must be Kotlin internal (public in bytecode)")
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ComponentParameterProvider classes must be internal and implement PreviewParameterProvider`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ComponentParameterProvider")
            .and(notASyntheticInnerClass())
            .should()
            .bePublic()
            .andShould(implementInterfaceEndingWith("PreviewParameterProvider"))
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ComponentPreview classes must be internal`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ComponentPreview")
            .and(notASyntheticInnerClass())
            .should()
            .bePublic()
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ScreenState data classes must be public, annotated with Parcelize, and implement Parcelable`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ScreenState")
            .should()
            .bePublic()
            .andShould()
            .beAnnotatedWith("kotlinx.parcelize.Parcelize")
            .andShould(implementInterfaceEndingWith("Parcelable"))
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ScreenToViewModelEvents sealed interfaces must be public and annotated with Immutable`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ScreenToViewModelEvents")
            .should()
            .bePublic()
            .andShould()
            .beAnnotatedWith("androidx.compose.runtime.Immutable")
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ScreenUseCase data classes must be public`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ScreenUseCase")
            .and()
            .areNotInterfaces()
            .should()
            .bePublic()
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ScreenUseCase members must be types ending with UseCase`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ScreenUseCase")
            .and()
            .areNotInterfaces()
            .should(haveOnlyUseCaseMembers())
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - ViewModel classes must be public and extend ViewModel`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ViewModel")
            .and()
            .areNotInterfaces()
            .should()
            .bePublic()
            .andShould()
            .beAssignableTo(
                "androidx.lifecycle.ViewModel",
            ).allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - Screen ParameterProvider must be public and implement PreviewParameterProvider`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("ScreenParameterProvider")
            .and(notASyntheticInnerClass())
            .should()
            .bePublic()
            .andShould(implementInterfaceEndingWith("PreviewParameterProvider"))
            .allowEmptyShould(true)
            .check(presentationClasses)
    }

    @Test
    fun `presentation - Android Context must never be passed as an argument`() {
        classes()
            .should(notAcceptAndroidContextAsParameter())
            .check(presentationClasses)
    }
}

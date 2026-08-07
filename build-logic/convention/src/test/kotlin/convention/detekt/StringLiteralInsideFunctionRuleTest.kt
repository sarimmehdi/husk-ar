package convention.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StringLiteralInsideFunctionRuleTest {

    @Test
    fun `reports a local string literal inside a function`() {
        val code =
            """
            fun loadUser() {
                val status = "active"
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a string passed to a regular function`() {
        val code =
            """
            fun loadUsers() {
                repository.load("users")
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertEquals(1,findings.size)
    }

    @Test
    fun `does not report a top-level constant`() {
        val code =
            """
            private const val ACTIVE_STATUS = "active"

            fun loadUser() {
                val status = ACTIVE_STATUS
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a companion object constant`() {
        val code =
            """
            class UserRepository {

                fun loadUser() {
                    val status = ACTIVE_STATUS
                }

                private companion object {
                    const val ACTIVE_STATUS = "active"
                }
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report strings passed to Timber`() {
        val code =
            """
            fun loadUser() {
                Timber.d("Loading user")
                Timber.tag("UserRepository").i("User loaded")
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report diagnostic messages`() {
        val code =
            """
            fun validateUser(userId: String?) {
                require(userId != null) {
                    "User ID is required"
                }

                check(userId.isNotBlank()) {
                    "User ID cannot be blank"
                }

                if (userId == "invalid") {
                    error("Invalid user ID")
                }
            }
            """.trimIndent()

        val findings = createRule().lint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report explicitly ignored literals`() {
        val code =
            """
            fun buildPath() {
                val empty = ""
                val space = " "
                val dash = "-"
                val slash = "/"
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports each non-ignored string literal separately`() {
        val code =
            """
            fun loadUser() {
                val status = "active"
                val endpoint = "users"
            }
            """.trimIndent()

        val findings = createRule().lint(code)

        assertEquals(2, findings.size)
    }

    @Test
    fun `uses configured ignored call prefixes`() {
        val code =
            """
            fun loadUser() {
                CustomLogger.info("Loading user")
            }
            """.trimIndent()

        val rule =
            createRule(
                ignoredCallPrefixes = listOf("CustomLogger."),
            )

        val findings = rule.lint(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `uses configured ignored literals`() {
        val code =
            """
            fun buildQuery() {
                val wildcard = "*"
            }
            """.trimIndent()

        val rule =
            createRule(
                ignoredLiterals = listOf("*"),
            )

        val findings = rule.lint(code)

        assertTrue(findings.isEmpty())
    }

    private fun createRule(
        ignoredCallPrefixes: List<String> =
            listOf(
                "Timber.",
                "timber.log.Timber.",
                "require(",
                "requireNotNull(",
                "check(",
                "checkNotNull(",
                "error(",
            ),
        ignoredLiterals: List<String> =
            listOf(
                "",
                " ",
                "-",
                "/",
            ),
    ): StringLiteralInsideFunctionRule =
        StringLiteralInsideFunctionRule(
            config =
                TestConfig(
                    "ignoredCallPrefixes" to ignoredCallPrefixes,
                    "ignoredLiterals" to ignoredLiterals,
                ),
        )
}

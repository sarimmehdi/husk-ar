package convention.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class StringLiteralInsideFunctionRule(
    config: Config,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "String literals inside functions should be extracted.",
        debt = Debt.FIVE_MINS,
    )

    private val ignoredCallPrefixes: List<String> by config(
        defaultValue = listOf(
            "Timber.",
            "timber.log.Timber.",
        ),
    )

    private val ignoredLiterals: List<String> by config(
        defaultValue = listOf(
            "",
            " ",
        ),
    )

    override fun visitStringTemplateExpression(
        expression: KtStringTemplateExpression,
    ) {
        super.visitStringTemplateExpression(expression)

        if (!expression.isInsideFunction()) return
        if (expression.literalContent() in ignoredLiterals) return
        if (expression.isInsideIgnoredCall()) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "Extract this string literal into a constant or another appropriate source.",
            ),
        )
    }

    private fun KtStringTemplateExpression.isInsideFunction(): Boolean =
        getStrictParentOfType<KtNamedFunction>() != null

    private fun KtStringTemplateExpression.isInsideIgnoredCall(): Boolean {
        val call = getStrictParentOfType<KtCallExpression>()
            ?: return false

        val completeCall = when (val parent = call.parent) {
            is KtDotQualifiedExpression -> parent.text
            else -> call.text
        }

        return ignoredCallPrefixes.any(completeCall::startsWith)
    }

    private fun KtStringTemplateExpression.literalContent(): String =
        text
            .removePrefix("\"\"\"")
            .removeSuffix("\"\"\"")
            .removeSurrounding("\"")
}

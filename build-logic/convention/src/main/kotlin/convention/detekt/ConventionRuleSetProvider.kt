package convention.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class ConventionRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "custom-rules"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            id = ruleSetId,
            rules = listOf(
                StringLiteralInsideFunctionRule(config),
            ),
        )
}

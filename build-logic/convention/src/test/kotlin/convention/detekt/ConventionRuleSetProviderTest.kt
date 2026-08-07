package convention.detekt

import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ConventionRuleSetProviderTest {

    @Test
    fun `provider can be loaded through ServiceLoader`() {
        val provider =
            ServiceLoader
                .load(RuleSetProvider::class.java)
                .firstOrNull {
                    it.javaClass.name ==
                        ConventionRuleSetProvider::class.java.name
                }

        assertNotNull(provider)
        assertEquals("custom-rules", provider?.ruleSetId)
    }
}

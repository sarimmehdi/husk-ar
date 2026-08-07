package com.sarim.husk.utils.logging

import com.sarim.husk.utils.logging.LinkableDebugTree
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

internal class LinkableDebugTreeTest {
    @Test
    fun `createStackElementTag formats string correctly using reflection`() {
        val tree = LinkableDebugTree()
        val element = StackTraceElement("Class", "myMethod", "TestFile.kt", 99)

        val method: Method =
            LinkableDebugTree::class.java.getDeclaredMethod(
                "createStackElementTag",
                StackTraceElement::class.java,
            )
        method.isAccessible = true

        val result = method.invoke(tree, element) as String

        assertEquals("(TestFile.kt:99)#myMethod", result)
    }
}

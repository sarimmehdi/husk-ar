package com.sarim.husk.utils.logging

import timber.log.Timber

/** Timber tree that adds an IDE-linkable source location to every debug log tag. */
class LinkableDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String =
        "(${element.fileName}:${element.lineNumber})#${element.methodName}"
}

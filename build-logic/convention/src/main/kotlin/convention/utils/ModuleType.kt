package convention.utils

enum class ModuleType {
    DOMAIN,
    DATA,
    DI,
    PRESENTATION,
    NAV,
    UTILS,
    UI,

    /**
     * A leaf module with no project or platform dependencies of its own.
     *
     * Used for pure computation such as geometry, which must stay free of Android and framework
     * types so it can be reasoned about and tested in isolation.
     */
    PURE,
}

package convention.utils

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface AndroidLibraryExtension {
    val internalDependencies: ListProperty<String>
    val moduleType: Property<ModuleType>
}

package convention.utils

import org.gradle.api.provider.SetProperty

interface MutationExtension {
    val targetClasses: SetProperty<String>
    val targetTests: SetProperty<String>
}

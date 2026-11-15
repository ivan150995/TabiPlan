plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 compila Kotlin por sí mismo; declararlo aquí solo fija la versión de Kotlin.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

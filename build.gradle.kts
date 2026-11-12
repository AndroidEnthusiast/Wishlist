plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Compose's compiler is part of Kotlin since 2.0 (P19, 2.191).
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    // Room's code generator (ROADMAP S2.1); its version must match Kotlin's.
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false
}

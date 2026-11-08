plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Room's code generator (ROADMAP S2.1); its version must match Kotlin's.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

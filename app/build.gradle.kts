plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wishlist.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.wishlist.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 138
        versionName = "2.137"
    }
    // The release key is NOT in this project. GitHub puts it in place while building, from the repository secrets
    // KEYSTORE_BASE64, KEYSTORE_PASSWORD and KEY_ALIAS (see .github/workflows/build.yml). Keep a copy of the key safe:
    // without it, an update can't install over the app and everyone would have to reinstall.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("WISHLIST_KEYSTORE") ?: "release.jks")
            storePassword = System.getenv("WISHLIST_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("WISHLIST_KEY_ALIAS") ?: "wishlist"
            keyPassword = System.getenv("WISHLIST_KEYSTORE_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    // A release build also runs Android's lint checks; they are advice here, not a reason to stop the build.
    lint { checkReleaseBuilds = false }
    // Screenshot tests (app/src/test, Roborazzi + Robolectric): run by Claude before a push, never by the GitHub
    // build (that only runs assembleRelease). See tests/README.md, "App screenshots".
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.maxHeapSize = "3g"
            // Each test class in a fresh Java process: the app keeps its state in objects (Repo, ...), and one
            // class (dark) left the next (light) unable to settle.
            it.forkEvery = 1
            // The shop checks need the internet; they only run with -Pwl.shops (tests/README.md).
            it.systemProperty("wl.shops", if (project.hasProperty("wl.shops")) "1" else "")
            it.testLogging.showStandardStreams = project.hasProperty("wl.shops")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Instant message notifications (Push.kt). Set up in code from Config, so no google-services plugin or file.
    implementation("com.google.firebase:firebase-messaging:24.0.0")

    // Only for the screenshot tests; none of this goes into the app.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.work:work-testing:2.9.0")
}

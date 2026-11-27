plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Room keeps a copy of each database version's layout in app/schemas, so later versions can be checked against it.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

android {
    namespace = "com.wishlist.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.wishlist.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 304
        versionName = "2.303"
        // Crash reports (Crash.kt): Crashlytics wants a build id, which its Gradle plugin would normally add. The app
        // isn't shrunk or renamed (isMinifyEnabled = false), so there is no mapping file to upload; one id per version.
        resValue("string", "com.google.firebase.crashlytics.mapping_file_id", "wishlist-$versionCode")
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
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    // A release build also runs Android's lint checks; they are advice here, not a reason to stop the build.
    lint { checkReleaseBuilds = false }
    // App tests (app/src/test). The plain JUnit checks of the app's logic run on every GitHub build before the APK is
    // made (gradle testReleaseUnitTest assembleRelease): a failing check stops the update (ROADMAP S0.1). The screenshot
    // tests (Roborazzi + Robolectric) are slow and only run on request with -Pwl.screens (owner, 5 November 2026: never
    // in the GitHub build). See tests/README.md, "App tests".
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            val screens = project.hasProperty("wl.screens")
            if (!screens) it.filter.excludeTestsMatching("*Screenshot*")
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            // 2 GB each when four run at once (four × 3 GB was too close to GitHub's 16 GB); the screenshots keep 3 GB.
            it.maxHeapSize = if (screens) "3g" else "2g"
            // Robolectric opens files through Android's file descriptors (BackupTest writes a real Export file).
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
            // Each test class in a fresh Java process: the app keeps its state in objects (Repo, ...), and a class
            // could leave the next unable to settle (screenshots: dark then light; ListNavigationTest, 2.156).
            it.forkEvery = 1
            // Four classes at a time (owner, 20 November 2026: builds felt slow; measured 6 min 17 s → 3 min 49 s): each
            // still in its own process, so they can't disturb each other. -Pwl.forks=1 runs them one by one again.
            it.maxParallelForks = (project.findProperty("wl.forks") as String?)?.toIntOrNull()
                ?: Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            // One of several GitHub computers (owner, 20 November 2026: builds took too long): -Pwl.shard=2/4 runs the
            // second quarter of the checks. Quarters are made equal with each class's time in app/test-times.txt
            // (the slowest first, each to the least busy computer); a class not listed yet goes by its name.
            (project.findProperty("wl.shard") as String?)?.split("/")?.let { sh ->
                val k = sh[0].toInt() - 1
                val n = sh[1].toInt()
                val times = project.file("test-times.txt").takeIf { f -> f.exists() }?.readLines().orEmpty()
                    .mapNotNull { l -> l.trim().split(" ").takeIf { p -> p.size == 2 }?.let { p -> p[0] to (p[1].toDoubleOrNull() ?: 0.0) } }
                val load = DoubleArray(n)
                val home = HashMap<String, Int>()
                for ((c, t) in times.sortedByDescending { x -> x.second }) {
                    val i = load.indices.minByOrNull { x -> load[x] } ?: 0
                    home[c] = i; load[i] += t
                }
                it.include(org.gradle.api.specs.Spec<org.gradle.api.file.FileTreeElement> { e ->
                    if (e.isDirectory) true else {
                        val c = e.name.substringBefore('$').removeSuffix(".class")
                        (home[c] ?: Math.floorMod(c.hashCode(), n)) == k
                    }
                })
            }
            // The shop checks need the internet; they only run with -Pwl.shops (tests/README.md).
            it.systemProperty("wl.shops", if (project.hasProperty("wl.shops")) "1" else "")
            // The layout inspector (LayoutGuard.kt): -Pwl.guardReport writes every problem to build/layout-report.txt
            // instead of stopping at the first screen.
            it.systemProperty("wl.guardReport", if (project.hasProperty("wl.guardReport")) "1" else "")
            // The screenshots in one style (Design.kt STYLES), e.g. -Pwl.style=chaos; Quiet when left out.
            it.systemProperty("wl.style", (project.findProperty("wl.style") as String?).orEmpty())
            // The data move checked on an owner's real Export (DbMoveTest): -Pwl.export=/path/to/prefs.json, here only.
            it.systemProperty("wl.export", (project.findProperty("wl.export") as String?).orEmpty())
            it.testLogging.showStandardStreams = project.hasProperty("wl.shops")
            // Robolectric downloads Android itself from Maven Central, which answers cloud computers with "429"; on
            // such a computer tests/android-setup.sh sets wl.mavenMirror to Google's copy (the GitHub build doesn't).
            (project.findProperty("wl.mavenMirror") as String?)?.let { m -> it.systemProperty("robolectric.dependency.repo.url", m) }
            it.testLogging.events("failed")
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Screens as routes with their own back stack (ROADMAP Stage 1, Nav.kt). 2.9.x fits Compose 1.8 (the BOM above, P19 2.191).
    implementation("androidx.navigation:navigation-compose:2.9.0")
    // The database on the phone (ROADMAP Stage 2, Db.kt). 2.7.x fits Kotlin 2.1 and KSP 2 (P19, 2.191).
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Live updates (Live.kt, 2.174): the WebSocket to Supabase Realtime. The same OkHttp Coil already brought along.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Instant message notifications (Push.kt). Set up in code from Config, so no google-services plugin or file.
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    // Crash reports (owner, D2, 8 November 2026; Crash.kt). Same Firebase release as firebase-messaging 24.0.0.
    implementation("com.google.firebase:firebase-crashlytics:19.0.0")
    // The recovery key into the phone's password manager, and offered back on a new phone (RecoveryKey.kt, 2.187).
    // 1.5.x fits compileSdk 36. The second part lets phones with Google Play services use Google Password Manager.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")

    // Only for the tests; none of this goes into the app.
    testImplementation("junit:junit:4.13.2")
    // The real org.json for plain JUnit (Android's own copy only works on a phone).
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.06.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.work:work-testing:2.10.2")
}

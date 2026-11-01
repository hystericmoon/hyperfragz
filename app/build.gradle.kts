import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Release signing comes from keystore.properties (kept out of version control).
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.hosni.hyperfragz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hosni.hyperfragz"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.2.1"
    }

    signingConfigs {
        create("release") {
            if (signing.isNotEmpty()) {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release build, so testing never replaces it.
            applicationIdSuffix = ".debug"
            // Test builds (.github/workflows/test-build.yml): -PtestBuild=<run number>
            // makes the version 1.2.0-test.<n>, which the Debug app's updater compares.
            providers.gradleProperty("testBuild").orNull?.let { versionNameSuffix = "-test.$it" }
            // -PsignDebug=true: sign with the release key, so every test build installs
            // over the previous one (CI's own debug key changes from run to run).
            if (providers.gradleProperty("signDebug").orNull == "true" && signing.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // Screenshot tests (src/test) render the Compose screens on the JVM with Robolectric.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Golden images live next to the tests; see "Screenshot tests" in CLAUDE.md.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation(bom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

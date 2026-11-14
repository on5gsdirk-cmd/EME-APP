plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "be.on5gs.eme"
    compileSdk = 35

    defaultConfig {
        applicationId = "be.on5gs.eme"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.16"
    }

    signingConfigs {
        // Alleen in de release-workflow (.github/workflows/release.yml): de sleutel komt uit de
        // GitHub-secrets. Op je eigen pc is RELEASE_KEYSTORE_FILE niet gezet en verandert er niets.
        System.getenv("RELEASE_KEYSTORE_FILE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Schermafdrukken alleen maken met: ./gradlew testDebugUnitTest -Pscreenshots
            all { it.systemProperty("screenshots", project.hasProperty("screenshots").toString()) }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Alleen voor schermafdruk-tests op de pc (zie ScreenshotTest)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

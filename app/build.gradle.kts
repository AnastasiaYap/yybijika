import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signing config lives in keystore.properties, which is gitignored along with
// the keystore itself. Both must be backed up: Android identifies an app by its
// signing key, so losing them means updates can no longer install over the top.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "io.tr8.yybijika"
    compileSdk = 36

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.tr8.yybijika"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "0.14.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where the in-app updater looks for new releases.
        buildConfigField("String", "UPDATE_REPO", "\"AnastasiaYap/yybijika\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // content.db is opened directly by Room's createFromAsset, which needs a real
    // file on disk. Compressing it in the APK would force a decompress-to-cache
    // copy on every cold start.
    androidResources {
        noCompress += "db"
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    // Android stubs org.json in unit tests, so every call throws "not mocked".
    // The real implementation makes the release-parsing tests runnable on the JVM.
    testImplementation(libs.json)
    testImplementation(libs.coroutines.test)
    // So a JVM test can run ContentDb's own SQL against the shipped deck,
    // which is the only way the coverage counts and the exercise registry
    // can be checked against each other rather than kept in step by hand.
    testImplementation(libs.sqlite.jdbc)
}

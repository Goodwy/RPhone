import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val productId = localProperties.getProperty("PRODUCT_ID_X1", "")
val subscriptionId = localProperties.getProperty("SUBSCRIPTION_ID_X1", "")
val subscriptionYearId = localProperties.getProperty("SUBSCRIPTION_YEAR_ID_X1", "")

android {
    namespace = "dev.goodwy.rphone"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.goodwy.rphone"
        minSdk = 29
        targetSdk = 37
        versionCode = 55
        versionName = "0.5.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PRODUCT_ID_X1", "\"$productId\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X1", "\"$subscriptionId\"")
        buildConfigField("String", "SUBSCRIPTION_YEAR_ID_X1", "\"$subscriptionYearId\"")
    }

    base {
        archivesName = "rill-phone-${defaultConfig.versionCode}"
    }

    signingConfigs {
        create("release") {
            if (file("keystore.jks").exists()) {
                storeFile = file("keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("gplay") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
        create("rustore") { dimension = "distribution" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)

            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    // UI Stack managed by BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.exifinterface)
    implementation(libs.graphics.shapes)
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.graphics)
    implementation(libs.compose.icons)

    // Core Android / Kotlin
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation & Serialization
    implementation(libs.navigation.compose)
    implementation(libs.preference.ktx)
    implementation(libs.datastore.preferences)

    // Biometrics
    implementation(libs.biometric)
    implementation(libs.fragment.ktx)
    implementation(libs.kotlinx.serialization.json)

    // Compose Destinations
    implementation(libs.compose.destinations.core)
    ksp(libs.compose.destinations.ksp)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // DI: Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Image & Utility
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)

    debugImplementation(libs.compose.tooling)

    // Goodwy
    "gplayImplementation"(libs.billing)
    "rustoreImplementation"(libs.rustore.bom)
    "rustoreImplementation"(libs.rustore.pay)
}

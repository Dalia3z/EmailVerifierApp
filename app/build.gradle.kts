// Application module build script: Android configuration + all dependencies.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // Compose compiler (Kotlin 2.x)
    alias(libs.plugins.ksp)            // Room annotation processing
}

android {
    namespace = "com.example.emailverifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.emailverifier"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // ---- AndroidX / Lifecycle / Coroutines ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ---- Jetpack Compose (Material 3) ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- Room: local progress database ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---- CSV parsing / writing ----
    implementation(libs.opencsv)

    // ---- Phone number validation (Google libphonenumber) ----
    implementation(libs.libphonenumber)

    // ---- Email verification library + SLF4J binding + Ktor CIO engine ----
    // ktor-client-cio is declared explicitly so the network engine is guaranteed
    // on the compile AND runtime classpaths (the library also depends on it).
    implementation(libs.emailverifier.kt)
    implementation(libs.ktor.client.cio)
    implementation(libs.slf4j.simple)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tw.nanbu.ourochrono"
    compileSdk = 34

    defaultConfig {
        applicationId = "tw.nanbu.ourochrono"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

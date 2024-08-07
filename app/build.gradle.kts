plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.a13e300.tools.ifo"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.a13e300.tools.ifo"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // We don't need to do R8 since it is a really small module
            isMinifyEnabled = false // true
            // isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("androidx.annotation:annotation:1.8.1")
}

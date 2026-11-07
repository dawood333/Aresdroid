plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "org.aresdroid.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.aresdroid.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/pivotLabAssets"))
}

// Package the NDK-built standalone executable as an APK asset. The app copies
// it to /data/local/tmp and runs it only under unshare -m.
val packagePivotLab by tasks.registering(Copy::class) {
    dependsOn("externalNativeBuildDebug")
    from(fileTree(layout.buildDirectory.dir("intermediates/cxx/Debug")) {
        include("**/aresdroid-pivot-lab")
    })
    into(layout.buildDirectory.dir("generated/pivotLabAssets"))
    rename { "aresdroid-pivot-lab" }
    doFirst {
        val matches = fileTree(layout.buildDirectory.dir("intermediates/cxx/Debug")) {
            include("**/aresdroid-pivot-lab")
        }.files
        if (matches.isEmpty()) throw GradleException("NDK pivot lab executable was not produced")
    }
}
tasks.named("mergeDebugAssets").configure { dependsOn(packagePivotLab) }

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
}

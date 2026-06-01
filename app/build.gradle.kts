plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zune.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zune.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Metrolist KMP core modules
    implementation(projects.common)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.media3)
    implementation(projects.kotlinYtmusicScraper)
    implementation(projects.composeApp)

    // Media3 dependencies for video playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // Koin Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Androidx dependencies
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.work.runtime.ktx)

    // Compose foundational dependencies
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Fix 16 KB alignment issue on Android 15
    implementation("androidx.graphics:graphics-path:1.0.1")

    // Coil & Palette (use versions from version catalog where possible)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // OkHttp (aligned with Metrolist)
    implementation(libs.okhttp3.okhttp)

    // Audio tagger for downloaded files metadata
    implementation("org.mp4parser:isoparser:1.9.41")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation(libs.ui.tooling)
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
    resolutionStrategy {
        force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
    }
}

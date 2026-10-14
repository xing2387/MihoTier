plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "top.xing2387.mihotier.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":easytier"))
    api(files("libs/mihomo.aar"))
    implementation(libs.okhttp)
    // Dependencies for config parsing/generation, e.g. JSON/YAML if needed later
}

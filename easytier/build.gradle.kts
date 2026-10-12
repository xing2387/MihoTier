import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "top.xing2387.mihotier.easytier"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("cargo-jniLibs").get().asFile.absolutePath)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}

fun resolveNdkHome(): String? {
    System.getenv("ANDROID_NDK_HOME")?.takeIf { it.isNotBlank() }?.let { return it }
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties().apply { localPropsFile.inputStream().use { load(it) } }
        props.getProperty("ndk.dir")?.takeIf { it.isNotBlank() }?.let { return it }
        val sdkDir = props.getProperty("sdk.dir")
        if (!sdkDir.isNullOrBlank()) {
            val ndkRoot = File(sdkDir, "ndk")
            if (ndkRoot.isDirectory) {
                ndkRoot.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }?.let {
                    return it.absolutePath
                }
            }
        }
    }
    return null
}

// Add a custom task to run cargo ndk
val cargoBuild = tasks.register<Exec>("cargoBuild") {
    workingDir = file("src/main/rust")

    val outDir = layout.buildDirectory.dir("cargo-jniLibs").get().asFile

    resolveNdkHome()?.let { ndkHome ->
        environment("ANDROID_NDK_HOME", ndkHome)
    }

    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-o", outDir.absolutePath,
        "build", "--release"
    )

    doLast {
        outDir.walkTopDown()
            .filter { it.isFile && it.extension == "so" && it.name != "libeasytier.so" }
            .forEach { it.delete() }
    }
}

// Ensure the cargo build task runs before JNI libs are packaged
tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(cargoBuild)
    }
}

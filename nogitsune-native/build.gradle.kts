plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.kitsuri.nogitsune.jni"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 28
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    buildFeatures {
        prefab = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)

                // Ktor Client
                val ktorVersion = project.property("ktor.version") as String
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")

                // Coroutines
                val coroutinesVersion = project.property("coroutines.version") as String
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

                // Serialization
                val serializationVersion = project.property("serialization.version") as String
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")

                // Ktor Android / OkHttp Client
                val ktorVersion = project.property("ktor.version") as String
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")

                // Media3 / ExoPlayer
                implementation("androidx.media3:media3-exoplayer:1.2.0")
                implementation("androidx.media3:media3-ui:1.2.0")
                implementation("androidx.media3:media3-datasource-okhttp:1.2.0")

                // Image Loading
                implementation("io.coil-kt:coil-compose:2.5.0")
            }
        }
    }
}

android {
    namespace = "com.myrealtv.app"
    compileSdk = (project.property("android.compileSdk") as String).toInt()

    defaultConfig {
        applicationId = "com.myrealtv.app"
        minSdk = (project.property("android.minSdk") as String).toInt()
        targetSdk = (project.property("android.targetSdk") as String).toInt()
        versionCode = 33
        versionName = "1.2.12"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}



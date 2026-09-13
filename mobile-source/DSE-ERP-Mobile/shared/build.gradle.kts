import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isWindows = System.getProperty("os.name")
    .startsWith("Windows", ignoreCase = true)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "org.dse.mobile.shared"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    if (!isWindows) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("io.ktor:ktor-client-core:3.5.2")
            implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
        }

        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:3.5.2")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.2")
        }

        if (!isWindows) {
            val iosMain by getting {
                dependencies {
                    implementation("io.ktor:ktor-client-darwin:3.5.2")
                }
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.5.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

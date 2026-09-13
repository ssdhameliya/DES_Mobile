import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val isMacOs = System.getProperty("os.name")
    .startsWith("Mac", ignoreCase = true)

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "org.dse.mobile.compose"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }
    }

    if (isMacOs) {
        iosArm64()
        iosSimulatorArm64()

        targets.withType<KotlinNativeTarget>().configureEach {
            binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.apache.poi:poi:5.4.1")
            implementation("org.apache.poi:poi-ooxml:5.4.1")
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.dse.mobile.MainKt"
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uatStoreFile = providers.gradleProperty("jasviUatStoreFile").orNull
val uatStorePassword = providers.gradleProperty("jasviUatStorePassword").orNull
val uatKeyAlias = providers.gradleProperty("jasviUatKeyAlias").orNull
val uatKeyPassword = providers.gradleProperty("jasviUatKeyPassword").orNull

val uatSigningReady = listOf(
    uatStoreFile,
    uatStorePassword,
    uatKeyAlias,
    uatKeyPassword
).all { !it.isNullOrBlank() }

val prodStoreFile = providers.gradleProperty("jasviProdStoreFile").orNull
val prodStorePassword = providers.gradleProperty("jasviProdStorePassword").orNull
val prodKeyAlias = providers.gradleProperty("jasviProdKeyAlias").orNull
val prodKeyPassword = providers.gradleProperty("jasviProdKeyPassword").orNull

val prodSigningReady = listOf(
    prodStoreFile,
    prodStorePassword,
    prodKeyAlias,
    prodKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "org.dse.mobile.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.dse.erp.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 129
        versionName = "1.2.9"
    }

    signingConfigs {
        if (uatSigningReady) {
            create("uatRelease") {
                storeFile = file(uatStoreFile!!)
                storePassword = uatStorePassword
                keyAlias = uatKeyAlias
                keyPassword = uatKeyPassword
            }
        }

        if (prodSigningReady) {
            create("prodRelease") {
                storeFile = file(prodStoreFile!!)
                storePassword = prodStorePassword
                keyAlias = prodKeyAlias
                keyPassword = prodKeyPassword
            }
        }
    }

    flavorDimensions += "environment"

    productFlavors {
        create("uat") {
            dimension = "environment"

            if (uatSigningReady) {
                signingConfig = signingConfigs.getByName("uatRelease")
            }

            applicationIdSuffix = ".uat"

            buildConfigField("String", "RELEASE_CHANNEL", "\"UAT\"")
            buildConfigField("String", "ERP_SERVER_URL", "\"https://api-uat.jasviindustries.in\"")
            buildConfigField("String", "BLOCKED_SERVER_HOST", "\"api.jasviindustries.in\"")
            buildConfigField("boolean", "ALLOW_SERVER_EDIT", "true")
            buildConfigField("String", "UPDATE_APK_BASE_URL", "\"https://api-uat.jasviindustries.in/mobile/android/uat\"")
            buildConfigField("String", "DEEP_LINK_SCHEME", "\"dseerp-uat\"")

            manifestPlaceholders["deepLinkScheme"] = "dseerp-uat"
        }

        create("prod") {
            dimension = "environment"

            if (prodSigningReady) {
                signingConfig = signingConfigs.getByName("prodRelease")
            }

            buildConfigField("String", "RELEASE_CHANNEL", "\"PROD\"")
            buildConfigField("String", "ERP_SERVER_URL", "\"https://api.jasviindustries.in\"")
            buildConfigField("String", "BLOCKED_SERVER_HOST", "\"api-uat.jasviindustries.in\"")
            buildConfigField("boolean", "ALLOW_SERVER_EDIT", "false")
            buildConfigField("String", "UPDATE_APK_BASE_URL", "\"https://api.jasviindustries.in/mobile/android/prod\"")
            buildConfigField("String", "DEEP_LINK_SCHEME", "\"dseerp\"")

            manifestPlaceholders["deepLinkScheme"] = "dseerp"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
        if (variantBuilder.productFlavors.any { it.second == "prod" }) {
            variantBuilder.enable = false
        }
    }
}

gradle.taskGraph.whenReady {
    val requestsUatRelease =
        allTasks.any { it.name.contains("UatRelease", ignoreCase = true) }

    if (requestsUatRelease && !uatSigningReady) {
        throw GradleException(
            "UAT release signing is not configured. " +
                "Set jasviUatStoreFile, jasviUatStorePassword, " +
                "jasviUatKeyAlias and jasviUatKeyPassword " +
                "in your user Gradle properties before building uatRelease."
        )
    }

    val requestsProdRelease =
        allTasks.any { it.name.contains("ProdRelease", ignoreCase = true) }

    if (requestsProdRelease && !prodSigningReady) {
        throw GradleException(
            "PROD release signing is not configured. " +
                "Set jasviProdStoreFile, jasviProdStorePassword, " +
                "jasviProdKeyAlias and jasviProdKeyPassword " +
                "in your user Gradle properties before building prodRelease."
        )
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")
}


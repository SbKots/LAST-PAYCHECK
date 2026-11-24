plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val supabasePublishableKey = providers.gradleProperty("supabasePublishableKey")
    .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY"))
    .getOrElse("")
val publishableKeyPattern = Regex("sb_publishable_[A-Za-z0-9_-]+")
require(supabasePublishableKey.isEmpty() || supabasePublishableKey.matches(publishableKeyPattern)) {
    "Only a Supabase publishable key is accepted"
}

android {
    namespace = "com.lastpaycheck.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lastpaycheck"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        create("hardwareTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".hardwaretest"
            matchingFallbacks += "debug"
        }
    }
    testBuildType = if (providers.gradleProperty("hardwareTestBuild").isPresent) {
        "hardwareTest"
    } else {
        "debug"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":presentation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}

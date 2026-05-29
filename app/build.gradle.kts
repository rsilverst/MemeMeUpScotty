import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val replicateToken = localProperties.getProperty("REPLICATE_API_TOKEN") ?: System.getenv("REPLICATE_API_TOKEN") ?: "YOUR_ACCESS_TOKEN"

if (replicateToken.isBlank() || replicateToken == "YOUR_ACCESS_TOKEN") {
    logger.warn("""

        ===================================================================================
        WARNING: Replicate API token is not set!

        To generate images, you need a Replicate API token. Please follow these steps:
        1. Create a token at https://replicate.com/account/api-tokens
        2. Open or create the file 'local.properties' in the project root directory.
        3. Add the following entry with your actual token:
           REPLICATE_API_TOKEN=r8_your_token_here

        Alternatively, you can define an environment variable named:
           REPLICATE_API_TOKEN

        Optional: override the default model (sdxl-based/juggernaut-xl-lightning) with
           REPLICATE_MODEL_ID=owner/model
        ===================================================================================

    """.trimIndent())
}

android {
    namespace = "com.rsilverst.mememeupscotty"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rsilverst.mememeupscotty"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REPLICATE_API_TOKEN", "\"$replicateToken\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.moshi.kotlin.codegen)
}

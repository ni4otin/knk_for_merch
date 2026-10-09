plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.knk.scaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.knk.scaner"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "4.1"
    }

    flavorDimensions.add("version")
    productFlavors {
        create("gms") {
            dimension = "version"
            applicationIdSuffix = ".gms"
            versionNameSuffix = "-gms"
        }
        create("zxing") {
            dimension = "version"
            applicationIdSuffix = ".zxing"
            versionNameSuffix = "-zxing"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(libs.material)
    implementation(libs.androidx.appcompat)

    // Сканирование штрихкодов
    "gmsImplementation"(libs.play.services.code.scanner)
    "zxingImplementation"("com.journeyapps:zxing-android-embedded:4.3.0")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.github.Dantsu:ESCPOS-ThermalPrinter-Android:3.3.0")
}
import java.io.FileInputStream
import java.util.*

// PcscLikeSampleUsb/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.springcard.pcsclike_sample_usb"
    compileSdk = 35

    buildFeatures{
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.springcard.pcsclike_sample_usb"
        minSdk = 22
        targetSdk = 35
        versionCode = 13
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("DefaultSigning") {
            val localPropertiesFile = rootProject.file("local.properties")
            val localProperties = Properties().apply {
                load(FileInputStream(localPropertiesFile))
            }

            /*keyAlias = localProperties.getProperty("key_alias", "")
            keyPassword = localProperties.getProperty("key_password", "")
            storeFile = file(localProperties.getProperty("key_file", ""))
            storePassword = localProperties.getProperty("key_password", "")*/
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            //buildConfigField("Boolean", "appDebug", "false")
            signingConfig = signingConfigs.getByName("DefaultSigning")
        }
        debug {
            buildConfigField("Boolean", "appDebug", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    viewBinding {
        enable = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("com.android.volley:volley:1.2.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation(project(":PcscLikeSample"))
    implementation(project(":PcscLike"))
}
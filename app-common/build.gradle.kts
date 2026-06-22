plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "im.angry.openeuicc.common"
    compileSdk = 35

    val roam2WorldApiBaseUrl = providers.gradleProperty("roam2worldApiBaseUrl")
        .orElse("https://roam2world-panels-backend.onrender.com")
        .get()

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ROAM2WORLD_API_BASE_URL", "\"$roam2WorldApiBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.7.0")
    api(project(":libs:lpac-jni"))
    api(project(":app-deps"))

    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Roam2World: force Compose compiler plugin onto Kotlin compiler classpath.
// In this OpenEUICC build, compose=true did not populate kotlinCompilerPluginClasspathDebug.
configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspath") }.configureEach {
    project.dependencies.add(this.name, "androidx.compose.compiler:compiler:1.5.14")
}


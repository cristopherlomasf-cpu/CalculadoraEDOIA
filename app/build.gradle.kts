import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    buildFeatures {
        buildConfig = true
    }

    namespace = "com.example.calculadoraedoia"
    compileSdk = 36

    defaultConfig {
        val pplxKey = gradleLocalProperties(rootDir, providers).getProperty("PPLX_API_KEY") ?: ""
        buildConfigField("String", "PPLX_API_KEY", "\"$pplxKey\"")
        
        val mathpixKey = gradleLocalProperties(rootDir, providers).getProperty("MATHPIX_APP_ID") ?: ""
        val mathpixSecret = gradleLocalProperties(rootDir, providers).getProperty("MATHPIX_APP_KEY") ?: ""
        buildConfigField("String", "MATHPIX_APP_ID", "\"$mathpixKey\"")
        buildConfigField("String", "MATHPIX_APP_KEY", "\"$mathpixSecret\"")
        
        applicationId = "com.example.calculadoraedoia"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Material Design
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // ML Kit Text Recognition V2
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

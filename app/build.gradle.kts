import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""
val roadsApiKey: String = localProperties.getProperty("ROADS_API_KEY") ?: ""


android {
    namespace = "com.example.ai_smarttransportsystem"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ai_smarttransportsystem"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Corrected buildConfigField syntax
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "ROADS_API_KEY", "\"$roadsApiKey\"")
        manifestPlaceholders["googleMapsApiKey"] = mapsApiKey
    }

    buildFeatures {
        // Enable BuildConfig generation
        viewBinding = true
        buildConfig = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.cardview)

    // Fragment dependency for FragmentContainerView
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(libs.play.services.tflite.support)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.5.0")
    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Google Maps + Location (already have maps, add location if missing)
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")


    // Coroutines (for async API calls)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Optional: Material ProgressBar / Loading
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    //Google Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth:23.0.0")
    implementation("com.google.firebase:firebase-firestore:25.0.0")
}
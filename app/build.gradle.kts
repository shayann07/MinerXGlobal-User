plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.navigation.safe.args)
}

android {
    namespace = "com.minerxgloble.minerxgloble"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.minerxgloble.minerxgloble"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packagingOptions {
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/DEPENDENCIES")
    }
}

dependencies {

    implementation(libs.core)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.grpc.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.firestore.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.gson)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.dynamic.features.fragment)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.storage)
    implementation(libs.lottie)
    implementation(libs.glide)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.circleimageview)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.ultra.ptr)
    implementation(libs.okhttp)
    implementation(libs.picasso)
    implementation(libs.volley)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.androidx.coordinatorlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.luckywheel.android)
    implementation(libs.taptargetview)
    implementation(libs.materialshowcaseview)
    implementation(libs.ucrop)
    implementation(libs.photoview)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.shimmer)
}
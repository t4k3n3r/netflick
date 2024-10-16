val vCode: Int by rootProject.extra
val vName: String by rootProject.extra

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("kotlin-parcelize")
}

android {
  namespace = "com.pedro.streamer"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.pedro.streamer"
    minSdk = 16
    targetSdk = 34
    versionCode = vCode
    versionName = vName
    multiDexEnabled = true
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
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
    buildConfig = true
  }
}

dependencies {
  implementation(project(":library"))
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  //noinspection GradleDependency, version 1.7.0 need min sdk 21
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.multidex:multidex:2.0.1")
  implementation ("com.google.code.gson:gson:2.10.1")
  implementation ("com.squareup.picasso:picasso:2.71828")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    val cameraxVersion = "1.3.3"
  implementation("androidx.camera:camera-core:$cameraxVersion")
  implementation("androidx.camera:camera-camera2:$cameraxVersion")
  implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
}

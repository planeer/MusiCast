plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.sqldelight)
  kotlin("native.cocoapods")
}

kotlin {
  jvmToolchain(17)
  androidTarget()

  iosArm64()
  iosSimulatorArm64()

  cocoapods {
    summary = "MusiCast shared module"
    homepage = "https://github.com/nejcplan/musicast"
    version = "1.0"
    ios.deploymentTarget = "15.0"
    podfile = project.file("../iosApp/Podfile")
    framework {
      baseName = "ComposeApp"
      isStatic = true
    }
    pod("TensorFlowLiteC") {
      version = "~> 2.14.0"
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.ktor.client.okhttp)
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.sqldelight.android.driver)
      implementation(libs.media3.exoplayer)
      implementation(libs.media3.session)
      implementation(libs.tflite)
      implementation(libs.tflite.support)
      implementation(libs.koin.android)
    }
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)

      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.datetime)

      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)

      implementation(libs.sqldelight.runtime)
      implementation(libs.sqldelight.coroutines)

      implementation(libs.rssparser)

      implementation(libs.koin.core)
      implementation(libs.koin.compose)

      implementation(libs.coil.compose)
      implementation(libs.coil.network.ktor)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
    iosMain.dependencies {
      implementation(libs.ktor.client.darwin)
      implementation(libs.sqldelight.native.driver)
    }
  }
}

android {
  namespace = "com.musicast.musicast"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.musicast.musicast"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  androidResources {
    noCompress += "tflite"
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

sqldelight {
  databases {
    create("PodcastDatabase") {
      packageName.set("com.musicast.musicast.db")
    }
  }
}

dependencies {
  debugImplementation(libs.compose.uiTooling)
}

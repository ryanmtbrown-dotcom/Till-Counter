plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
 namespace = "com.tillcounter.app"
 compileSdk = 36
 signingConfigs { create("stable") { storeFile = rootProject.file("signing/till-counter.jks"); storePassword = "tillcounter"; keyAlias = "tillcounter"; keyPassword = "tillcounter" } }
 defaultConfig { applicationId = "com.tillcounter.app"; minSdk = 24; targetSdk = 36; versionCode = providers.gradleProperty("TILL_VERSION_CODE").get().toInt(); versionName = providers.gradleProperty("TILL_VERSION_NAME").get(); testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("stable") } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.appcompat:appcompat:1.7.1")
 implementation("androidx.activity:activity-ktx:1.11.0")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

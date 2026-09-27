plugins { id("com.android.application") }
android {
    namespace = "com.tillcounter.app"
    compileSdk = 36
    defaultConfig { applicationId = "com.tillcounter.app"; minSdk = 26; targetSdk = 36; versionCode = 4; versionName = "0.2.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.webkit:webkit:1.14.0")
}

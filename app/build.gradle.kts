plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.khader.aicodestudio"
    compileSdk = 34
    defaultConfig { applicationId = "com.khader.aicodestudio"; minSdk = 24; targetSdk = 34; versionCode = 7; versionName = "1.6" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

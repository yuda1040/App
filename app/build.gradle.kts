plugins { id("com.android.application") }

android {
    namespace = "com.yuda1040.radio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuda1040.radio"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
}

dependencies {
    implementation("com.linkedin.dexmaker:dexmaker:2.28.3")
}

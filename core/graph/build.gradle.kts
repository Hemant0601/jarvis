plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jarvis.graph"
    compileSdk = 34
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:embed"))
    implementation(project(":core:llm"))

    implementation(libs.moshi.kotlin)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val sourceRoot = File(providers.environmentVariable("FITNESS_SOURCE_ROOT").orElse(rootProject.projectDir.absolutePath).get())
val signingFile = File(sourceRoot, ".private/signing.properties")
val signingValues = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

android {
    namespace = "cn.wangguanwen.fitnessdiary"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.wangguanwen.fitnessdiary"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (signingFile.exists()) {
        signingConfigs.create("personal") {
            storeFile = File(sourceRoot, ".private/fitness-diary.p12")
            storePassword = signingValues.getProperty("storePassword")
            keyAlias = "fitness-diary"
            keyPassword = signingValues.getProperty("keyPassword")
            storeType = "PKCS12"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("personal")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "512m"
            it.jvmArgs("-XX:+UseSerialGC", "-XX:ActiveProcessorCount=2")
            it.systemProperty("fitness.preview.dir", layout.buildDirectory.dir("ui-preview").get().asFile.absolutePath)
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

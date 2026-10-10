plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------- 正式签名配置 ----------
// CI 通过 GitHub Secrets 注入环境变量（见 .github/workflows/android.yml）；
// 本地可写入 ~/.gradle/gradle.properties（androidKeystorePath / androidKeystorePassword /
// androidKeyAlias / androidKeyPassword），密钥文件不入库。
// 四项缺任意一项即视为未配置，release 退回未签名包，保证无密钥的 PR 构建仍可通过。
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH") ?: findProperty("androidKeystorePath") as String?
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: findProperty("androidKeystorePassword") as String?
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: findProperty("androidKeyAlias") as String?
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: findProperty("androidKeyPassword") as String?
val releaseKeystoreFile = releaseKeystorePath?.let { file(it) }?.takeIf { it.exists() }
val hasReleaseSigning = releaseKeystoreFile != null && releaseKeystorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.glassous.betterhrbust"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.glassous.betterhrbust"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 本地真机验证使用独立包名，保留维护者签名的正式版；默认 CI Debug 不变。
            if (providers.gradleProperty("localWidgetPreview").orNull == "true") {
                applicationIdSuffix = ".widgetpreview"
                versionNameSuffix = "-widget-preview"
            }
        }
        release {
            // 存在正式密钥时启用签名，确保升级安装不报「签名不一致」；未配置密钥时保持未签名
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 依赖裁剪：R8 代码压缩 + 资源压缩（保守策略，见 src/main/keepRules/rules.keep）
            isMinifyEnabled = true
            isShrinkResources = true
            // src/main/keepRules/ 下的规则文件由 AGP 自动合并进 R8
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX Core & Base
    implementation(libs.androidx.core.ktx)

    // Compose BOM & Core UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Activity & Lifecycle Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation & Adaptive
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.adaptive.navigation.suite)

    // KotlinX Serialization & Coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Network & HTML Parsing
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Persistence: Room & DataStore
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.about.libraries)
}

android {
    namespace = "com.chuishui.otheme"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chuishui.otheme"
        minSdk = 30
        targetSdk = 37
        versionCode = 195
        versionName = "1.9.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    // 签名配置
    signingConfigs {
        create("release") {
            
            storeFile = file(System.getenv("OTHEME_KEYSTORE_PATH") ?: "../otheme.jks")
            storePassword = System.getenv("OTHEME_KEYSTORE_PASSWORD") ?: "3442681588Mc/"
            keyAlias = System.getenv("OTHEME_KEY_ALIAS") ?: "otheme"
            keyPassword = System.getenv("OTHEME_KEY_PASSWORD") ?: "3442681588Mc/"
        }
    }

    buildTypes {
        release {
            // 启用代码混淆
            isMinifyEnabled = true
            // 启用资源压缩
            isShrinkResources = true
            // ProGuard 配置文件
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 使用签名配置
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug 版本不混淆，方便调试
            isMinifyEnabled = false
            isShrinkResources = false
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
        compose = true
        buildConfig = true
    }
    
    // 打包优化配置
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
    
    // APK 输出配置
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val appName = "OTheme"
            val versionName = defaultConfig.versionName
            val buildType = buildType.name
            output.outputFileName = "${appName}_v${versionName}_${buildType}.apk"
        }
    }
}

aboutLibraries {
    // 禁用离线模式，允许获取远程许可证信息（v11 顶层属性）
    offlineMode = false
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.android)
    // AboutLibraries
    implementation(libs.aboutlibraries.core)
    // Apache Commons Compress: explicit ZIP encoding control and robust ZIP handling
    implementation("org.apache.commons:commons-compress:1.23.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.booknote"
    // 🌟 【核心修改】：升级到 37，使用 Android 17 的最新代码包开发
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.booknote"
        minSdk = 31 // 门槛：最低只允许 Android 12 及以上的手机安装！抛弃老旧历史包袱。
        // 🌟 【核心修改】：靶心升级到 37，以 Android 17 为绝对优先的最高标准进行底层性能与 UI 优化！
        targetSdk = 37
        versionCode = 27
        versionName = "2.8.26"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 暂时关闭代码混淆，确保打包 100% 成功
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
dependencies {
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.ui.graphics)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 第三方核心库
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0") // 💡 移除了下面重复的 2.5.0 版本，保留最新的 2.6.0
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.glance:glance:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("dev.chrisbanes.haze:haze:0.7.3")
    implementation("dev.shreyaspatil:capturable:2.1.0")
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
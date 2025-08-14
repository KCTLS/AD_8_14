plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.adproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.adproject"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 可留作兜底（也可删掉，靠 flavors 提供）
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/student/\"")
    }

    // ✅ 合并成一个 buildTypes
    buildTypes {
        debug {
            // 调试打印网络日志
            buildConfigField("boolean", "ENABLE_HTTP_LOG", "true")
            isMinifyEnabled = false
        }
        release {
            // 线上关闭日志
            buildConfigField("boolean", "ENABLE_HTTP_LOG", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 语言/工具链
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "11" }

    // 合并为一个 buildFeatures
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // ✅ 多环境
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/student/\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField(
                "String", "BASE_URL",
                "\"http://adproject-backend.southeastasia.cloudapp.azure.com/student/\""
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media3.common.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Retrofit / OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // ✅ 保留一种 logging 依赖（二选一），这里用版本库那种：
    implementation(libs.okhttp.logging.interceptor)
    // 如果你更想手动锁版本，就改成下面这两行并删除上面那行：
    // implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JavaNetCookieJar 需要这个
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

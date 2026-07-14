import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** 根据构建宿主 OS 选择 JavaFX 原生库分类器，避免硬编码 :win。 */
val javafxClassifier: String by lazy {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (arch == "aarch64") "mac-aarch64" else "mac"
        os.contains("linux") -> "linux"
        else -> {
            println("[WARN] 无法识别 OS '$os'/'$arch'，默认使用 JavaFX :win 分类器")
            "win"
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.cbor)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // jvmShared: desktop 与 android 共享的 JVM 代码（JDK DOM / ZIP 实现的乐谱解析）
        val jvmShared by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(":mididriver-1.29@aar")
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                // 看谱模式：JavaFX WebView（仅 Desktop），按构建宿主自动选择原生分类器
                val jfx = javafxClassifier
                implementation("org.openjfx:javafx-base:23:$jfx")
                implementation("org.openjfx:javafx-graphics:23:$jfx")
                implementation("org.openjfx:javafx-controls:23:$jfx")
                implementation("org.openjfx:javafx-swing:23:$jfx")
                implementation("org.openjfx:javafx-media:23:$jfx")
                implementation("org.openjfx:javafx-web:23:$jfx")
                // JNA for native Windows Dark Mode Title Bar
                implementation("net.java.dev.jna:jna:5.14.0")
                implementation("net.java.dev.jna:jna-platform:5.14.0")
            }
        }
    }
}

android {
    namespace = "com.windscore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.windscore"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.desktop {
    application {
        mainClass = "com.windscore.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "WindScore"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

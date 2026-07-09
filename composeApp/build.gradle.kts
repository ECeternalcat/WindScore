import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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
                implementation(libs.kotlinx.coroutines.core)
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
                // 看谱模式：JavaFX WebView（仅 Desktop）。:win 分类器带 Windows 原生库
                implementation("org.openjfx:javafx-base:21.0.5:win")
                implementation("org.openjfx:javafx-graphics:21.0.5:win")
                implementation("org.openjfx:javafx-controls:21.0.5:win")
                implementation("org.openjfx:javafx-swing:21.0.5:win")
                implementation("org.openjfx:javafx-media:21.0.5:win")
                implementation("org.openjfx:javafx-web:21.0.5:win")
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

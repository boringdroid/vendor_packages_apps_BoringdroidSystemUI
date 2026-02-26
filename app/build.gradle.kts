import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.diffplug.spotless")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

spotless {
    format("misc") {
        target("*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        ktfmt().kotlinlangStyle()
    }

    kotlin {
        target("**/*.kt")
        ktfmt().kotlinlangStyle()
    }
}

android {
    namespace = "com.boringdroid.systemui"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.boringdroid.systemui"
        minSdk = 28
        targetSdk = 33
        versionCode = 130
        versionName = "130"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets { getByName("main").resources.directories.add("src/main/SystemUISharedRes") }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    signingConfigs {
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("plugin.keystore")
            storePassword = "android"
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { abortOnError = false }

    buildFeatures { compose = true }
}

configurations.named("implementation") {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-io")
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "androidx.core")
    exclude(group = "androidx.annotation")
    exclude(group = "androidx.collection")
    exclude(group = "androidx.concurrent")
    exclude(group = "androidx.arch.core")
    exclude(group = "androidx.customview")
    exclude(group = "androidx.lifecycle")
    exclude(group = "androidx.profileinstaller")
    exclude(group = "androidx.startup")
    exclude(group = "androidx.tracing")
    exclude(group = "com.google.guava")
    exclude(group = "org.jetbrains.kotlinx")
}

dependencies {
    implementation(files("libs/SystemUISharedLib.jar"))
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")

    testImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("com.google.truth.extensions:truth-java8-extension:1.4.4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.enabledSdks", "33")

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = false
        exceptionFormat = TestExceptionFormat.FULL
    }

    outputs.upToDateWhen { false }
}

tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

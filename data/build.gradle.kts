plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.plefy.app.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        // No Compose / no BuildConfig needed for the data layer.
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(11)
}

// Robolectric's instrumented Android SDK (compileSdk 36) requires a newer JDK than the module's
// Java 11 bytecode target. Keep compilation on 11 but run the unit-test JVM on Java 21 so Robolectric
// can create its sandbox; production bytecode is unaffected.
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":backend:parser"))
    implementation(project(":backend:inference"))

    implementation(libs.room.ktx)
    implementation(libs.paging.runtime)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}

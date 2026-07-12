plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.plefy.app.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(11)
}

// Robolectric's instrumented Android SDK (compileSdk 36 / pinned 34) requires a newer JDK than the
// module's Java 11 bytecode target. Keep compilation on 11 but run the unit-test JVM on Java 21 so
// Robolectric can create its sandbox; production bytecode is unaffected.
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

    implementation(libs.room.runtime)
    implementation(libs.paging.runtime)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}

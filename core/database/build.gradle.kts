plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.plefy.app.database"
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
        // No Compose / no BuildConfig needed for the persistence layer.
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

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // Required so Room can generate the LimitOffsetPagingSource backing RowDao.pagingSource().
    implementation("androidx.room:room-paging:2.8.4")
    implementation(libs.paging.runtime)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}

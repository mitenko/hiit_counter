plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.mitenko.repkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mitenko.repkit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Spec rev 30 §2: debug builds send no crash reports or analytics unless built with -PcrashlyticsInDebug=true.
        buildConfigField("boolean", "CRASHLYTICS_IN_DEBUG", (project.findProperty("crashlyticsInDebug") == "true").toString())
    }

    // Play upload key (spec rev 31): read from the user-level ~/.gradle/gradle.properties, never from the repo.
    // Without these properties (CI, build workers) the release build is unsigned and is signed afterwards.
    val uploadStoreFile = providers.gradleProperty("repkitUploadStoreFile").orNull
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("repkitUploadStorePassword").get()
                keyAlias = providers.gradleProperty("repkitUploadKeyAlias").get()
                keyPassword = providers.gradleProperty("repkitUploadKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    // Exported Room schemas served as debug assets: Robolectric reads the merged debug assets, so MigrationTestHelper can load them (spec §4). Release builds are unaffected.
    sourceSets["debug"].assets.srcDir("$projectDir/schemas")
    lint {
        abortOnError = true
    }
}

kotlin { jvmToolchain(17) }

room {
    schemaDirectory("$projectDir/schemas")
}

// mergeDebugAssets reads app/schemas, which the Room plugin's copyRoomSchemas* tasks write; declare the order Gradle can't infer.
tasks.configureEach {
    if (name == "mergeDebugAssets")
        dependsOn(tasks.matching { it.name.startsWith("copyRoomSchemas") })
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

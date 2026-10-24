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

    // Spec rev 41 §2.1: debug always uses Google's test IDs; release takes -Prepkit.* (falling back to test IDs).
    val testApp = "ca-app-pub-3940256099942544~3347511713"
    val testBanner = "ca-app-pub-3940256099942544/9214589741"
    fun prop(name: String, fallback: String) = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: fallback

    defaultConfig {
        applicationId = "com.mitenko.repkit"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Spec rev 30 §2: debug builds send no crash reports or analytics unless built with -PcrashlyticsInDebug=true.
        buildConfigField("boolean", "CRASHLYTICS_IN_DEBUG", (project.findProperty("crashlyticsInDebug") == "true").toString())
        // Spec rev 41 §1.4: -Prepkit.tier=FREE|PRO fixes the tier in debug builds (no billing); release ignores it.
        buildConfigField("String", "FORCED_TIER", "\"${project.findProperty("repkit.tier") ?: ""}\"")
        manifestPlaceholders["admobAppId"] = testApp
        buildConfigField("String", "AD_UNIT_LIST", "\"$testBanner\"")
        buildConfigField("String", "AD_UNIT_ENTRY", "\"$testBanner\"")
        // Debug only (read by UmpConsentGateway): -Prepkit.consentEea=true shows the EEA consent form; -Prepkit.consentTestDevice=<hashed id> names the device.
        buildConfigField("boolean", "CONSENT_EEA", (project.findProperty("repkit.consentEea") == "true").toString())
        buildConfigField("String", "CONSENT_TEST_DEVICE", "\"${prop("repkit.consentTestDevice", "")}\"")
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
            manifestPlaceholders["admobAppId"] = prop("repkit.admobAppId", testApp)
            buildConfigField("String", "AD_UNIT_LIST", "\"${prop("repkit.adUnitList", testBanner)}\"")
            buildConfigField("String", "AD_UNIT_ENTRY", "\"${prop("repkit.adUnitEntry", testBanner)}\"")
            // When an ID fell back to Google's test one (naming which, never the values): bundleRelease, the Play upload,
            // fails unless -Prepkit.allowTestAds=true; assembleRelease (R8 smoke builds) only warns.
            gradle.taskGraph.whenReady {
                val bundle = hasTask(":app:bundleRelease")
                if (bundle || hasTask(":app:assembleRelease")) {
                    val testOnes = listOf(
                        "repkit.admobAppId" to testApp,
                        "repkit.adUnitList" to testBanner,
                        "repkit.adUnitEntry" to testBanner,
                    ).filter { (name, fallback) -> prop(name, fallback) == fallback }.map { it.first }
                    if (testOnes.isNotEmpty()) {
                        val names = testOnes.joinToString()
                        if (bundle && project.findProperty("repkit.allowTestAds") != "true") {
                            throw GradleException(
                                "REPKIT bundleRelease would ship AdMob TEST ids for: $names. Pass the real ids as " +
                                    "-P<name>=... (or put them in ~/.gradle/gradle.properties), or add -Prepkit.allowTestAds=true.",
                            )
                        }
                        logger.warn("REPKIT release build is using AdMob TEST ids for: $names")
                    }
                }
            }
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
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
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

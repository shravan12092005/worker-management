import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ─── Read credentials from local.properties (never hardcode in source) ───────
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}
val supabaseUrl: String = localProps.getProperty("SUPABASE_URL")
    ?: error("SUPABASE_URL not set in local.properties — see local.properties.example")
val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY")
    ?: error("SUPABASE_ANON_KEY not set in local.properties — see local.properties.example")

android {
    namespace = "com.workermanagement.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Expose credentials as BuildConfig constants — source code reads only
        // BuildConfig.SUPABASE_URL / SUPABASE_ANON_KEY, never string literals.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Allow Robolectric unit tests to use Android classes
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Tell Room's KSP processor where to export the schema JSON
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

val roomVersion = "2.6.1"

dependencies {
    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // HTTP client for SyncManager — used in main source (not test-only)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Wage engine — pure Kotlin JVM subproject (no Android deps)
    implementation(project(":wages"))

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

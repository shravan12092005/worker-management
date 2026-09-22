// Root build.gradle.kts — plugin declarations only.
// No source in the root project; all code lives in :wages, :data, :app.
plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

group = "com.workermanagement"
version = "1.0-SNAPSHOT"

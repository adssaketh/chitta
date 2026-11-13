plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Plain Kotlin, no Android imports (master prompt section 9): reads bank SMS, Ping files and UPI payment pages into
// candidate entries. It saves nothing; the app shows the candidates in a review list first.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:money"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

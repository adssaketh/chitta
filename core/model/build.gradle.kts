plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain Kotlin, no Android imports (master prompt section 9): enums and data shared by the importer and the database.
kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

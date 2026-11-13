plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain Kotlin, no Android imports (master prompt section 9): domain rules stay unit-testable.
kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

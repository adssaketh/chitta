plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain Kotlin, no Android imports (master prompt section 9): balances, transfers, search, bulk edit, history.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:money"))
    testImplementation(kotlin("test"))
}

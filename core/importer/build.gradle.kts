plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

// Plain Kotlin: reads rows through BackupSource, so the same code runs in JVM tests (sqlite-jdbc) and on the phone.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:money"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testFixturesImplementation(libs.sqlite.jdbc)
    testFixturesApi(libs.junit)
}

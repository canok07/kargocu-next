import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

// Routine :game:test stays bounded: the opt-in stress package never runs there.
tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("com.canok.kargotycoon.game.stress.*")
        excludeTestsMatching("com.canok.kargotycoon.game.career.*")
    }
}

tasks.register<Test>("careerBalanceTest") {
    group = "verification"
    description = "Opt-in seeded career pacing and economic reachability campaign."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("com.canok.kargotycoon.game.career.*") }
    maxHeapSize = "2g"
    systemProperty("career.metrics.dir", layout.buildDirectory.dir("career-metrics").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}

// Dedicated, deterministic, opt-in JVM stress campaign (engine + persistence).
// Run with: ./gradlew -PgameOnly=true :game:stressTest
tasks.register<Test>("stressTest") {
    group = "verification"
    description = "Opt-in deterministic JVM stress campaign for the game engine and persistence."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("com.canok.kargotycoon.game.stress.*")
    }
    maxHeapSize = "2g"
    systemProperty("stress.metrics.dir", layout.buildDirectory.dir("stress-metrics").get().asFile.absolutePath)
    findProperty("stressSeeds")?.let { systemProperty("stress.seeds", it.toString()) }
    findProperty("stressIterations")?.let { systemProperty("stress.iterations", it.toString()) }
    findProperty("stressCareerSeeds")?.let { systemProperty("stress.career.seeds", it.toString()) }
    findProperty("stressCareerDays")?.let { systemProperty("stress.career.days", it.toString()) }
    outputs.upToDateWhen { false }
}

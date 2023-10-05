plugins {
    application
    kotlin("jvm") version "1.8.0"
    id("com.google.protobuf") version "0.8.17"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
}

repositories {
    mavenCentral()
}

val ktor_version = "2.0.0"
val exposedVersion = "0.40.1"

dependencies {
    runtimeOnly("org.slf4j:slf4j-simple:1.7.32")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

    implementation("org.json:json:20230618")
    implementation("com.h2database:h2:2.1.214")
    implementation("io.grpc:grpc-protobuf:1.40.1")
}

tasks.test {
    useJUnitPlatform()
    useTestNG()
}

kotlin {
    jvmToolchain(8)
}

project.tasks.named("processResources", Copy::class.java) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

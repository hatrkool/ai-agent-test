plugins {
    java
    checkstyle
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ee.example"
version = "0.0.1-SNAPSHOT"
description = "IT Services Info Agent — Spring Boot + Spring AI"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

checkstyle {
    toolVersion = "10.20.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

extra["springAiVersion"] = "2.0.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-vector-store")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.19.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 split test-slice annotations (@WebMvcTest etc.) out of spring-boot-test-autoconfigure.
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

// Separate source set for live-model integration tests (needs OPENAI_API_KEY),
// kept out of `./gradlew test` so unit tests always stay green without network access.
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-test")
    // TestRestTemplate moved out of spring-boot-test into this dedicated artifact in Boot 4 —
    // confirmed by downloading and inspecting the jar directly, not by guessing from docs.
    "integrationTestImplementation"("org.springframework.boot:spring-boot-resttestclient")
}

tasks.test {
    useJUnitPlatform()
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against a live OpenAI model. Requires OPENAI_API_KEY."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/integrationTest"))
}

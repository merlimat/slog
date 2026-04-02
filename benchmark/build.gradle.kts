plugins {
    java
    id("me.champeau.jmh").version("0.7.3")
}

val slf4jVersion: String by project
val log4j2Version: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    jmhImplementation(project(":"))
    jmhImplementation("org.slf4j:slf4j-api:$slf4jVersion")
    jmhImplementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    jmhImplementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    jmhImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Version")
}

jmh {
    includes.addAll(providers.gradleProperty("jmhIncludes").map { listOf(it) }.orElse(emptyList()))
}

tasks.register("benchmark") {
    description = "Run JMH benchmarks"
    group = "benchmark"
    dependsOn(tasks.named("jmh"))
}

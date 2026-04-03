plugins {
    java
    id("me.champeau.jmh").version("0.7.3")
}

val slf4jVersion: String by project
val log4j2Version: String by project
val floggerVersion = "0.9"

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
    jmhImplementation("com.google.flogger:flogger:$floggerVersion")
    jmhImplementation("com.google.flogger:flogger-log4j2-backend:$floggerVersion")
}

jmh {
    includes.addAll(providers.gradleProperty("jmhIncludes").map { listOf(it) }.orElse(emptyList()))
}

tasks.register("benchmark") {
    description = "Run JMH benchmarks"
    group = "benchmark"
    dependsOn(tasks.named("jmh"))
}

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp").version("1.4.4")
    id("com.gradleup.nmcp.aggregation").version("1.4.4")

}

group = "io.github.merlimat.slog"
version = System.getenv("RELEASE_VERSION") ?: "0.0.0-SNAPSHOT"

val slf4jVersion: String by project
val log4j2Version: String by project
val junitVersion: String by project
val jacksonVersion: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.slf4j:slf4j-api:$slf4jVersion")
    compileOnly("org.apache.logging.log4j:log4j-core:$log4j2Version")
    compileOnly("org.apache.logging.log4j:log4j-api:$log4j2Version")

    testImplementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Version")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    // Required by AsyncLoggerContextSelector in the asyncLoggerTest task
    testRuntimeOnly("com.lmax:disruptor:3.4.4")

    nmcpAggregation(project(":"))
}

tasks.register("benchmark") {
    description = "Run JMH benchmarks"
    group = "benchmark"
    dependsOn(":benchmark:jmh")
}

tasks.javadoc {
    exclude("io/github/merlimat/slog/impl/**")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
    // These run in their own tasks with a modified environment
    exclude("**/AsyncLoggerModeTest*")
    exclude("**/NoDisruptorTest*")
}

val asyncLoggerTest = tasks.register<Test>("asyncLoggerTest") {
    description = "Runs tests that require the full-async Log4j2 context selector"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/AsyncLoggerModeTest*")
    systemProperty(
        "log4j2.contextSelector",
        "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
    )
    testLogging {
        showStandardStreams = true
    }
}

val noDisruptorTest = tasks.register<Test>("noDisruptorTest") {
    description = "Runs tests without the LMAX Disruptor on the classpath"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath.filter { !it.name.startsWith("disruptor") }
    include("**/NoDisruptorTest*")
}

tasks.check {
    dependsOn(asyncLoggerTest)
    dependsOn(noDisruptorTest)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "slog"

            pom {
                name = "slog"
                description = "Structured logging for Java, inspired by Go's log/slog"
                url = "https://github.com/merlimat/slog"

                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }

                developers {
                    developer {
                        id = "merlimat"
                        name = "Matteo Merli"
                        email = "matteo.merli@gmail.com"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/merlimat/slog.git"
                    developerConnection = "scm:git:ssh://github.com/merlimat/slog.git"
                    url = "https://github.com/merlimat/slog"
                }
            }
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

nmcpAggregation {
    centralPortal {
        username = System.getenv("CENTRAL_USERNAME")
        password = System.getenv("CENTRAL_PASSWORD")
        publishingType = "AUTOMATIC"
    }
}

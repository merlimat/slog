plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp").version("1.4.4")
    id("com.gradleup.nmcp.aggregation").version("1.4.4")
}

group = "io.github.merlimat.slog"
version = System.getenv("RELEASE_VERSION") ?: "0.0.0-SNAPSHOT"

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
    api("org.slf4j:slf4j-api:2.0.16")
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    compileOnly("org.apache.logging.log4j:log4j-api:2.25.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
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

plugins {
    java
    id("com.gradleup.shadow") version "8.3.7" apply false
}

group = "org.openpass"
version = "1.0.0"

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.purpurmc.org/snapshots")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

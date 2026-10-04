dependencies {
    implementation(project(":openpass-common"))
    compileOnly("org.spongepowered:spongeapi:8.1.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("OpenPass-Sponge")
        relocate("org.bouncycastle", "org.openpass.libs.bouncycastle")
        relocate("com.zaxxer.hikari", "org.openpass.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }
}

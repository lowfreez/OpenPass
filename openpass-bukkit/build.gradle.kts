dependencies {
    implementation(project(":openpass-common"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("OpenPass-Paper-Spigot-Folia")
        relocate("org.bouncycastle", "org.openpass.libs.bouncycastle")
        relocate("com.zaxxer.hikari", "org.openpass.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }
}

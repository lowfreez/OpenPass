dependencies {
    implementation(project(":openpass-common"))
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4-SNAPSHOT")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("OpenPass-Bungee-Waterfall")
        relocate("org.bouncycastle", "org.openpass.libs.bouncycastle")
        relocate("com.zaxxer.hikari", "org.openpass.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }
}

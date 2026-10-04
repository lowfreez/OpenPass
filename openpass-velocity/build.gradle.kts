dependencies {
    implementation(project(":openpass-common"))
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("OpenPass-Velocity")
        relocate("org.bouncycastle", "org.openpass.libs.bouncycastle")
        relocate("com.zaxxer.hikari", "org.openpass.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }
}

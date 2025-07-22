plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm")
}

application.mainClass = "com.hightech.discordbot.Bot" //
group = "org.hightech"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.mattmalec.com/repository/releases")
    }
    maven { url = uri("https://jitpack.io") }

}

dependencies {
    implementation ("io.github.cdimascio:dotenv-java:3.2.0")
    implementation ("nl.vv32.rcon:rcon:1.2.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("net.dv8tion:JDA:5.6.1")
}

kotlin {
    jvmToolchain(21)
}
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    // minimize() // Do not use with Java 21
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/LICENSE*", "META-INF/NOTICE*")
}

plugins {
    application
    id("com.gradleup.shadow") version "8.3.1"
}

application.mainClass = "com.example.discordbot.Bot" //
group = "org.example"
version = "1.0"

val jdaVersion = "5.2.1" //

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.mattmalec.com/repository/releases")
    }
    maven { url = uri("https://jitpack.io") }

}

dependencies {
    implementation ("io.github.cdimascio:dotenv-java:2.3.0")
    implementation ("nl.vv32.rcon:rcon:1.2.0")
    implementation("org.postgresql:postgresql:42.6.0")
    implementation ("com.mattmalec:Pterodactyl4J:2.BETA_142")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.dv8tion:JDA:5.2.1")
}

tasks.test {
    useJUnitPlatform()
}
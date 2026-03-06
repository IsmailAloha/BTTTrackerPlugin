plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "com.github.IsmailAloha"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

gradlePlugin {
    plugins {
        create("bttTrackerPlugin") {
            id = "com.bluetriangle.tracker"
            implementationClass = "com.bluetriangle.trackerplugin.BttTrackerPlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    compileOnly("com.android.tools.build:gradle:8.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
}
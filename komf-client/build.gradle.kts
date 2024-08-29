@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
    signing
}

group = "io.github.snd-r"
version = "1.0.0-alpha01"

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        publishLibraryVariants("release")
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "komf-client"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":komf-api-models"))
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.encoding)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }

}
android {
    namespace = "snd.komf"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    coordinates("io.github.snd-r.komf", "client", "1.0.0-alpha01")
    signAllPublications()

    pom {
        name.set("Komf API client")
        description.set("Komf API client")
        url.set("https://github.com/Snd-R/komf")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/Snd-R/komf/blob/master/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("Snd-R")
                name.set("Snd-R")
                url.set("https://github.com/Snd-R")
            }
        }
        scm {
            url.set("https://github.com/Snd-R/komf")
            connection.set("scm:git:git://github.com/Snd-R/komf.git")
            developerConnection.set("scm:git:ssh://git@github.com/Snd-R/komf.git")
        }
    }
}
signing {
    useGpgCmd()
}

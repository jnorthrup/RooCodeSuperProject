import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

//which stanza do we add a linux64 cinteropdef for liburing below? (the linux64 stanza is the only one that has a cinterop block)

plugins {
    kotlin("multiplatform") version "2.0.20" // Updated to Kotlin 2.0.20
    `maven-publish`
}

group = "org.bereft"
version = "1.0"

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
    google()
    maven("https://www.jitpack.io")
}
publishing {
    repositories {
        maven {
            url = uri("file://${System.getProperty("user.home")}/.m2/repository")
        }
    }
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class) compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.RequiresOptIn", // Add more opt-in annotations as needed
            "-Xsuppress-version-warnings", // Suppress version warnings
            "-Xexpect-actual-classes", // Enable expect/actual classes
        )
    }

    jvmToolchain(21)

    jvm {
        withJava()
    }

    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        nodejs()
    }

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")

    when {
        hostOs == "Windows" -> mingwX64("windows")
        hostOs == "Mac OS X" ->
            if (System.getProperty("os.arch") == "aarch64") {
                macosArm64("macosArm64")
            } else {
                macosX64("macos")
            }
        hostOs == "Linux" -> linuxX64("linux") // io_uring lives in linux sourceset only
        isMingwX64 -> mingwX64("posix")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                api("org.jetbrains.kotlin:kotlin-reflect:2.0.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                //datetime
                api("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0")
                //coroutines
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4")
                //serialization
                api("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.0")
            }
        }

        val jvmTest by getting {
            //bring in the dependencies from jvmMain
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        if (hostOs in listOf("Linux", "Mac OS X")) {
            //posix targets
            val posixMain by creating {
                dependsOn(commonMain)
            }

            val posixTest by creating {
                dependsOn(commonTest)
            }

            when (hostOs) {
                "Linux" -> {
                    //io_uring - LINUX SPECIFIC REMOVED
                    /*val linuxMain by getting {
                        dependsOn(posixMain)
                        dependencies {
                            implementation("org.bereft:io_uring:1.0")
                        }
                    }
                    val linuxTest by getting {
                        dependsOn(posixTest)
                    }*/
                }

                "Mac OS X" -> {
                    //libdispatch
                    val macosMain by creating {
                        dependsOn(posixMain)
                    }
                    val macosTest by creating {
                        dependsOn(posixTest)
                    }
                }

                else -> {
                    TODO("OS wtf!!")
                }
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinTest> {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

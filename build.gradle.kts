import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.android.library") version "8.8.2"
    id("org.jetbrains.dokka") version "2.0.0"
    `maven-publish`
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

val ktorVersion = "2.3.13"
val coroutinesVersion = "1.10.1"
val serializationVersion = "1.8.0"
val datetimeVersion = "0.6.1"
val junitVersion = "5.11.4"

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-websockets:$ktorVersion")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            implementation("io.ktor:ktor-client-mock:$ktorVersion")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }

        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:$ktorVersion")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }

        jvmTest.dependencies {
            implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
        }

        androidInstrumentedTest.dependencies {
            implementation("androidx.test.ext:junit:1.2.1")
            implementation("androidx.test:runner:1.6.2")
            implementation("androidx.test:core-ktx:1.6.1")
        }
    }
}

android {
    namespace = "com.surrealdb.kotlin"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = when (name) {
            "kotlinMultiplatform" -> "kotlin"
            else -> "kotlin-$name"
        }

        pom {
            name.set("SurrealDB Kotlin")
            description.set("Kotlin Multiplatform SurrealDB v3 driver")
            url.set(providers.gradleProperty("POM_URL"))

            licenses {
                license {
                    name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                    url.set(providers.gradleProperty("POM_LICENSE_URL"))
                }
            }

            developers {
                developer {
                    id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                    name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                }
            }

            scm {
                url.set(providers.gradleProperty("POM_SCM_URL"))
                connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
                developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
            }
        }
    }
}

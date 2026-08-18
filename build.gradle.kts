import com.android.build.gradle.LibraryExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply false
}

val javaVersion = JavaVersion.VERSION_11
val androidCompileSdk = 35
val androidMinSdk = 26

val expectedArtifactSuffixes =
    listOf(
        "",
        "-jvm",
        "-android",
    )

val expectedArtifactBases =
    mapOf(
        "kotlin" to "kotlin",
        "spectron" to "kotlin-spectron",
    )

val appleArtifactSuffixes =
    listOf(
        "-iosX64",
        "-iosArm64",
        "-iosSimulatorArm64",
    )

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "maven-publish")

        configure<KotlinMultiplatformExtension> {
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

            sourceSets.getByName("commonTest").dependencies {
                implementation(kotlin("test"))
                implementation(libs.bundles.testing)
            }

            sourceSets.getByName("jvmTest").dependencies {
                implementation(libs.junit.jupiter)
            }
        }

        configure<LibraryExtension> {
            compileSdk = androidCompileSdk

            defaultConfig {
                minSdk = androidMinSdk
            }

            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed")
                showExceptions = true
                showCauses = true
                showStackTraces = true
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        configurePublishing()
    }
}

/**
 * Maps the KMP publications onto the artifact ids consumers write down, and fills in
 * the POM. The base id comes from each module's own `gradle.properties`.
 *
 * Runs in [afterEvaluate] because the Android publication's artifactId is set by the
 * Kotlin plugin's own afterEvaluate hook; configuring earlier is silently overwritten.
 */
fun Project.configurePublishing() {
    // `providers.gradleProperty` deliberately does not see a subproject's own
    // gradle.properties, so the module-scoped values are read off the project.
    fun prop(name: String): String =
        findProperty(name)?.toString()
            ?: error("$path is missing the '$name' property (module gradle.properties)")

    val artifactBase = prop("POM_ARTIFACT_ID")
    val pomName = prop("POM_NAME")
    val pomDescription = prop("POM_DESCRIPTION")

    afterEvaluate {
        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                artifactId =
                    when (name) {
                        "kotlinMultiplatform" -> artifactBase
                        "androidRelease" -> "$artifactBase-android"
                        else -> "$artifactBase-$name"
                    }

                pom {
                    this.name.set(pomName)
                    this.description.set(pomDescription)
                    url.set(prop("POM_URL"))

                    licenses {
                        license {
                            this.name.set(prop("POM_LICENSE_NAME"))
                            url.set(prop("POM_LICENSE_URL"))
                        }
                    }

                    developers {
                        developer {
                            id.set(prop("POM_DEVELOPER_ID"))
                            this.name.set(prop("POM_DEVELOPER_NAME"))
                        }
                    }

                    scm {
                        url.set(prop("POM_SCM_URL"))
                        connection.set(prop("POM_SCM_CONNECTION"))
                        developerConnection.set(prop("POM_SCM_DEV_CONNECTION"))
                    }
                }
            }

            val onApple = HostManager.hostIsMac
            val base =
                expectedArtifactBases[project.name]
                    ?: error("$path publishes but has no entry in expectedArtifactBases")
            val expected =
                (expectedArtifactSuffixes + if (onApple) appleArtifactSuffixes else emptyList())
                    .map { "$base$it" }
                    .toSortedSet()
            val skipped =
                if (onApple) emptySet() else appleArtifactSuffixes.map { "$base$it" }.toSortedSet()
            val actual = publications.withType<MavenPublication>().map { it.artifactId }.toSortedSet()

            tasks.register("verifyPublicationCoordinates") {
                group = "verification"
                description = "Fails if this module's published Maven coordinates change."
                doLast {
                    val missing = expected - actual
                    val unexpected = actual - expected - skipped
                    check(missing.isEmpty() && unexpected.isEmpty()) {
                        buildString {
                            appendLine("$path publishes different coordinates than expected.")
                            appendLine("  missing:   ${missing.ifEmpty { "-" }}")
                            appendLine("  unexpected: ${unexpected.ifEmpty { "-" }}")
                            appendLine("  not checked on this host: ${skipped.ifEmpty { "-" }}")
                            append(
                                "If the change is intended, update expectedArtifactBases or " +
                                    "expectedArtifactSuffixes in the root build to match — but note " +
                                    "that a released coordinate cannot be taken back.",
                            )
                        }
                    }
                }
            }
        }
    }
}

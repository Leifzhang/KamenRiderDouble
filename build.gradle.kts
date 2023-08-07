plugins {

    id("org.jetbrains.kotlin.android") version ("1.7.10") apply (false)
    id("org.jetbrains.kotlin.jvm") version ("1.7.10") apply (false)

    id("com.google.osdetector") version ("1.7.0")
}
allprojects {
    repositories {
        mavenCentral()
    }
}
val downloadProtoc by configurations.creating {
    isTransitive = false
}

val wellKnownTypes by configurations.creating {
    isTransitive = false
}

dependencies {
    downloadProtoc(
        group = "com.google.protobuf",
        name = "protoc",
        version = "3.19.1",
        classifier = osdetector.classifier,
        ext = "exe"
    )
    wellKnownTypes("com.google.protobuf:protobuf-java:3.19.1")
}

val extractWellKnownTypeProtos by tasks.registering(Sync::class) {
    dependsOn(wellKnownTypes)
    from({
        wellKnownTypes.filter { it.extension == "jar" }.map { zipTree(it) }
    })
    include("**/*.proto")
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("bundled-protos"))
}
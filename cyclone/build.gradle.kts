import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("kotlinx-serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.19.1")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.google.api:api-common:2.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("reflect"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.5")
}

val extractWellKnownTypeProtos = rootProject.tasks.named<Sync>("extractWellKnownTypeProtos")


tasks {
    val generateTestProtoDescriptor by registering(DescriptorProtocTask::class) {
        val out = File(project.buildDir, name).also { it.mkdirs() }
        includeDir.set(project.file("src/test/resources/protos"))
        outputDir.set(out)
    }
    getByName("test").dependsOn(generateTestProtoDescriptor)
}

@Suppress("UnstableApiUsage")
open class DescriptorProtocTask : ProtocTask() {
    init {
        plugin.set("descriptor_set")
        protocOptions.add("--include_imports")
        outputFileName.convention("fileDescriptor.protoset")
    }
}


@Suppress("UnstableApiUsage")
open class ProtocTask : AbstractExecTask<ProtocTask>(ProtocTask::class.java) {
    @InputDirectory
    val includeDir: DirectoryProperty = project.objects.directoryProperty()

    @InputDirectory
    val wellKnownTypesDir: Provider<Directory> =
        project.layout.dir(project.rootProject.tasks.named<Sync>("extractWellKnownTypeProtos")
            .map { it.destinationDir })

    @OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @Input
    @Optional
    val outputFileName: Property<String> = project.objects.property()

    @InputFile
    val protoc: RegularFileProperty = project.objects.fileProperty().fileProvider(
            project.rootProject.configurations.named("downloadProtoc").map { it.singleFile })

    @Input
    val plugin: Property<String> = project.objects.property()

    @Input
    @Optional
    val pluginOptions: ListProperty<Pair<Any, Any>> = project.objects.listProperty()

    @InputFile
    @Optional
    val pluginPath: RegularFileProperty = project.objects.fileProperty()

    @Input
    @Optional
    val protocOptions: ListProperty<String> = project.objects.listProperty()

    private val protoFileDir: DirectoryProperty = project.objects.directoryProperty().apply {
        convention(includeDir)
    }

    fun protoFileSubdir(dir: String) {
        protoFileDir.set(includeDir.dir(dir))
    }

    @InputFiles
    @SkipWhenEmpty
    val protoFiles: FileCollection = protoFileDir.asFileTree.matching {
        this.include("*.proto")
    }

    override fun exec() {
        executable = protoc.get().asFile.also {
            it.setExecutable(true)
        }.absolutePath

        // If outputFileName was specified, append it to outputDir. Otherwise use outputDir as-is.
        val output = outputFileName.map { outputDir.file(it).get().asFile }.orElse(outputDir.asFile)

        // Build CLI args
        args(pluginOptions.orNull?.takeUnless { it.isEmpty() }
            ?.joinToString(separator = ",", postfix = ":") { (k, v) -> "$k=$v" }
            .let { "--${plugin.get()}_out=${it.orEmpty()}${output.get()}" })

        pluginPath.orNull?.let {
            args("--plugin=protoc-gen-${plugin.get()}=${it.asFile.absolutePath}")
        }

        args("-I", wellKnownTypesDir.get())
        args("-I", includeDir.get())

        protocOptions.orNull?.let {
            args(it)
        }

        args(protoFiles.map { it.absolutePath })

        super.exec()
    }
}

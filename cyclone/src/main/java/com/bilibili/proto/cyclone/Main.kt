package com.bilibili.proto.cyclone

import com.google.protobuf.compiler.PluginProtos

public class Main {
    fun main() {
        val input = PluginProtos.CodeGeneratorRequest.parseFrom(System.`in`)
        input.fileToGenerateList
        input.protoFileList
    }
}


internal fun runGenerator(request: Request) {
    val kotlinTypeMappings = mutableMapOf<String, String>()
    val params =
        if (request.parameter.isNullOrEmpty()) emptyMap()
        else request.parameter.split(',').map { it.substringBefore('=') to it.substringAfter('=', "") }.toMap()

    request.protoFile.forEach { it ->

        val file = FileBuilder.buildFile(FileBuilder.Context(it, mapOf()))
        kotlinTypeMappings += file.kotlinTypeMappings()
        if (request.file.contains(it.name)) {
            val fileNameSansPath = it.name!!.substringAfterLast('/')
            val filePath = (file.kotlinPackageName?.replace('.', '/')?.plus('/') ?: "") +
                    fileNameSansPath.removeSuffix(".proto") + ".kt"
            println ("Generating $filePath" )
            val code =
                CodeGenerator(file = file, kotlinTypeMappings = kotlinTypeMappings, params = params).generate()
            println("code generate:${code}")
        }
    }
}

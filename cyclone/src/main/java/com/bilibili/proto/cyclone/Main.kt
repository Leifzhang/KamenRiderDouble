package com.bilibili.proto.cyclone

import com.google.protobuf.compiler.PluginProtos

public class Main {
    fun main() {
        val input = PluginProtos.CodeGeneratorRequest.parseFrom(System.`in`)
        val request = Request(input.fileToGenerateList, input.parameter, input.protoFileList)
        val response=runGenerator(request)
        response.writeTo(System.out)
    }
}


internal fun runGenerator(request: Request): PluginProtos.CodeGeneratorResponse {
    val kotlinTypeMappings = mutableMapOf<String, String>()
    val params = if (request.parameter.isNullOrEmpty()) emptyMap()
    else request.parameter.split(',').map { it.substringBefore('=') to it.substringAfter('=', "") }
        .toMap()
    val fileBuilder = PluginProtos.CodeGeneratorResponse.newBuilder()
    request.protoFile.forEach { it ->
        val file = FileBuilder.buildFile(FileBuilder.Context(it, mapOf()))
        kotlinTypeMappings += file.kotlinTypeMappings()
        if (request.file.contains(it.name)) {
            val fileNameSansPath = it.name!!.substringAfterLast('/')
            val filePath = (file.kotlinPackageName?.replace('.', '/')?.plus('/')
                ?: "") + fileNameSansPath.removeSuffix(".proto") + ".kt"
            println("Generating $filePath")
            val code = CodeGenerator(
                file = file, kotlinTypeMappings = kotlinTypeMappings, params = params
            ).generate()
            println("code generate:\r\r\r\r${code} \r\r\r\r")
            fileBuilder.addFile(
                PluginProtos.CodeGeneratorResponse.File.newBuilder().setName(filePath).setContent(
                        code
                    ).build()
            )
        }
    }
    return fileBuilder.build()
}

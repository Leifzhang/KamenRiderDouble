package com.bilibili.proto.cyclone

import com.google.protobuf.compiler.PluginProtos

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = PluginProtos.CodeGeneratorRequest.parseFrom(System.`in`)
            val request = Request(input.fileToGenerateList, input.parameter, input.protoFileList)
            val response = runGenerator(request)
            response.writeTo(System.out)
        }
    }

}


internal fun runGenerator(request: Request): PluginProtos.CodeGeneratorResponse {
    val kotlinTypeMappings = mutableMapOf<String, String>()
    val params = if (request.parameter.isNullOrEmpty()) emptyMap()
    else request.parameter.split(',').map { it.substringBefore('=') to it.substringAfter('=', "") }
        .toMap()
    val fileBuilder = PluginProtos.CodeGeneratorResponse.newBuilder()

    request.protoFile.forEach {
        val file = FileBuilder.buildFile(FileBuilder.Context(it, mapOf()))
        kotlinTypeMappings += file.kotlinTypeMappings()
        if (request.file.contains(it.name)) {
            val fileNameSansPath = it.name!!.substringAfterLast('/')
            val filePath = (file.kotlinPackageName?.replace('.', '/')?.plus('/')
                ?: "") + fileNameSansPath.removeSuffix(".proto") + ".kt"
            val code = CodeGenerator(
                file = file, kotlinTypeMappings = kotlinTypeMappings, params = params
            ).generate()
           // println("code:\r$code")
            fileBuilder.addFile(
                PluginProtos.CodeGeneratorResponse.File.newBuilder().setName(filePath)
                    .setContent(code).build()
            )
        }
    }
    val textFile="META-INF/request.txt"
    fileBuilder.addFile(
        PluginProtos.CodeGeneratorResponse.File.newBuilder().setName(textFile)
            .setContent("""fileName : ${request.file}
            parameter :${request.parameter}
            protoFile :${request.protoFile}
        """.trimIndent()).build())
    return fileBuilder.build()
}

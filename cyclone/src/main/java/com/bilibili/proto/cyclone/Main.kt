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
    request.protoFile.forEach { it ->
        if (request.file.contains(it.name)) {
            println("file name:${it.name}")
        }
    }
}

package com.bilibili.proto.cyclone

import java.io.File

class Request(
    val file: List<String> = emptyList(),
    val parameter: String? = null,
    val protoFile: List<com.google.protobuf.DescriptorProtos.FileDescriptorProto> = emptyList()
)
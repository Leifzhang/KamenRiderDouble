package com.bilibili.w.sample

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf


object MyClass {
    @JvmStatic
    fun main(args: Array<String>) {
        val sample = Sample("66666666666")
        val encode = ProtoBuf.Default.encodeToByteArray(sample)
        val newSample = ProtoBuf.Default.decodeFromByteArray<Sample>(encode)
        println("newSample:${newSample.text}")

        val value = Value(Value.StrVal("夏老师是傻逼"))
        val valueEncode = ProtoBuf.Default.encodeToByteArray(value)
        val newValue = ProtoBuf.Default.decodeFromByteArray<Value>(valueEncode)
        println("newSample:${newValue.strVal}")
    }
}
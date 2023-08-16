package com.bilibili.w.sample

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf


object MyClass {
    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val sample1 = Sample("66666666666")
        val encode = ProtoBuf.Default.encodeToByteArray(sample1)
        val newSample = ProtoBuf.Default.decodeFromByteArray<Sample>(encode)
        println("newSample:${newSample.text}")

        val value = Value(KProto3PresenceEnum.fromValue(0), Value.StrVal("夏老师是傻逼"))
        val valueEncode = ProtoBuf {}.encodeToByteArray(value)
        val newValue = ProtoBuf {}.decodeFromByteArray<Value>(valueEncode)
        println("newStrValue:${newValue.strVal}")

        val intValue = Value(KProto3PresenceEnum.fromValue(0), Value.IntVal(66666))
        val intValueEncode = ProtoBuf {}.encodeToByteArray(intValue)
        val newIntValue = ProtoBuf {}.decodeFromByteArray<Value>(intValueEncode)
        println("newIntValue:${newIntValue.value}")
        val moudle = SerializersModule {
            polymorphic(Value.Value::class) {
                subclass(Value.IntVal::class)
                subclass(Value.StrVal::class)
            }
        }
        ProtoBuf {
            serializersModule = moudle
        }
    }
}
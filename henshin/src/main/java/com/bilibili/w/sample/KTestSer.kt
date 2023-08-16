package com.bilibili.w.sample

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pbandk.testpb.KProto3PresenceMain

/**
 *
 *  @Author LiABao
 *  @Since 2023/8/15
 *
 */
class KTestSer : KSerializer<KProto3PresenceMain> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("message") {

        }

    override fun deserialize(decoder: Decoder): KProto3PresenceMain {
     //  val message= decoder.decodeNullableSerializableValue()
        TODO()
    }

    override fun serialize(encoder: Encoder, value: KProto3PresenceMain) {
     //   encoder.encodeNullableSerializableValue(value.message?.serializer(),value.message)
    }
}

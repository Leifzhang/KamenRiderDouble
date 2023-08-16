package com.bilibili.w.sample

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked
import kotlin.reflect.KProperty0

/**
 *
 *  @Author LiABao
 *  @Since 2023/8/10
 *
 */
@Serializable
public data class Value(
    @ProtoNumber(1) val type: KProto3PresenceEnum = KProto3PresenceEnum.fromValue(0),
    @ProtoPacked @ProtoNumber(0) val value: Value? = null,
) : Function0<String> {


    @Serializable
    public sealed interface Value

    @Serializable
    public class IntVal(@ProtoNumber(1) val intVal: Int = 0) : Value

    @Serializable
    public class StrVal(val strVal: String = "") : Value


    val intVal: Int?
        get() = (value as? IntVal)?.intVal
    val strVal: String?
        get() = (value as? StrVal)?.strVal

    override fun invoke(): String = "com.bilibili.w.sample.Value"


}

@Serializable
public enum class KProto3PresenceEnum(val value: Int){
    PROTO3_PRESENCE_ENUM_UNSPECIFIED(0),
    UNRECOGNIZED(-1);

    public companion object {
        public val values: List<KProto3PresenceEnum> by lazy { listOf(PROTO3_PRESENCE_ENUM_UNSPECIFIED) }
        fun fromValue(value: Int): KProto3PresenceEnum = values.firstOrNull { it.value == value } ?: UNRECOGNIZED
        fun fromName(name: String): KProto3PresenceEnum = values.firstOrNull { it.name == name } ?: throw IllegalArgumentException("No KProto3PresenceEnum with name: $name")
    }
}

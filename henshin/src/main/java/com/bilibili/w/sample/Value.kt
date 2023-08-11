package com.bilibili.w.sample

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
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
    @ProtoPacked val value: Value? = null,
) : Function0<String> {
    companion object {
        const val TARGET_PATH = ""
    }

    @Serializable
    public sealed interface Value

    @Serializable
    public class IntVal(val intVal: Int = 0) : Value

    @Serializable
    public class StrVal(val strVal: String = "") : Value


    val intVal: Int?
        get() = (value as? IntVal)?.intVal
    val strVal: String?
        get() = (value as? StrVal)?.strVal

    override fun invoke(): String = "com.bilibili.w.sample.Value"


}

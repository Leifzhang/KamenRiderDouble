package com.bilibili.w.sample

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 *
 *  @Author LiABao
 *  @Since 2023/8/10
 *
 */
@Serializable
public data class Value(
    val value: Value ? = null,
){
    @Serializable
    public sealed interface Value

    @Serializable
    public class IntVal( val intVal: Int = 0) : Value
    @Serializable
    public class StrVal( val strVal: String = "") : Value


    val intVal: Int?
        get() = (value as? IntVal)?.intVal
    val strVal: String?
        get() = (value as? StrVal)?.strVal

}

package pbandk.testpb

import kotlinx.serialization.Serializable

/**
 * @Author LiABao
 * @Since 2023/8/10
 */
@Serializable
public sealed class Proto3PresenceEnum( val value: Int,  val name: String? = null){
    @Serializable
    public object PROTO3_PRESENCE_ENUM_UNSPECIFIED : Proto3PresenceEnum(0, "PROTO3_PRESENCE_ENUM_UNSPECIFIED")
    public class UNRECOGNIZED(value: Int) : Proto3PresenceEnum(value)

    public companion object {
        public val values: List<Proto3PresenceEnum> by lazy { listOf(PROTO3_PRESENCE_ENUM_UNSPECIFIED) }
        fun fromValue(value: Int): Proto3PresenceEnum = values.firstOrNull { it.value == value } ?: UNRECOGNIZED(value)
        fun fromName(name: String): Proto3PresenceEnum = values.firstOrNull { it.name == name } ?: throw IllegalArgumentException("No Proto3PresenceEnum with name: $name")
    }
}

@Serializable
public data class Proto3PresenceMessage(
    val string: String = "",
){
}

@Serializable
public data class Proto3PresenceMain(
    val message: pbandk.testpb.Proto3PresenceMessage? = null,
    val string: String = "",
    val int: Int = 0,
    val enum: pbandk.testpb.Proto3PresenceEnum = pbandk.testpb.Proto3PresenceEnum.fromValue(0),
    val optionalMessage: pbandk.testpb.Proto3PresenceMessage? = null,
    val optionalString: String? = null,
    val optionalInt: Int? = null,
    val optionalEnum: pbandk.testpb.Proto3PresenceEnum? = null,
    val oneOf: OneOf ? = null,
){
    @Serializable
    public sealed interface OneOf

    @Serializable
    public class OneOfString( val oneOfString: String = "") : OneOf
    @Serializable
    public class OneOfInt( val oneOfInt: Int = 0) : OneOf


    val oneOfString: String?
        get() = (oneOf as? OneOfString)?.oneOfString
    val oneOfInt: Int?
        get() = (oneOf as? OneOfInt)?.oneOfInt

}
package pbandk.testpb

import com.bilibili.w.sample.KTestSer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * @Author LiABao
 * @Since 2023/8/10
 */
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
@Serializable
public data class KProto3PresenceMessage(
    @ProtoNumber(4)  val string: String = "",
) :  Function0<String> {
    override fun invoke(): String ="pbandk.testpb.Proto3PresenceMessage"
}

@Serializable(with=KTestSer::class)
public data class KProto3PresenceMain(
    @ProtoNumber(2)  val message: pbandk.testpb.KProto3PresenceMessage? = null,
    @ProtoNumber(4)  val string: String = "",
    @ProtoNumber(6)  val int: Int = 0,
    @ProtoNumber(8)  val enum: pbandk.testpb.KProto3PresenceEnum = pbandk.testpb.KProto3PresenceEnum.fromValue(0),
    @ProtoNumber(1)  val optionalMessage: pbandk.testpb.KProto3PresenceMessage? = null,
    @ProtoNumber(3)  val optionalString: String? = null,
    @ProtoNumber(5)  val optionalInt: Int? = null,
    @ProtoNumber(7)  val optionalEnum: pbandk.testpb.KProto3PresenceEnum? = null,
    val oneOf: KOneOf ? = null,
) :  Function0<String> {
    @Serializable
    public sealed interface KOneOf

    @Serializable
    public class KOneOfString( val oneOfString: String = "") : KOneOf
    @Serializable
    public class KOneOfInt( val oneOfInt: Int = 0) : KOneOf


    val oneOfString: String?
        get() = (oneOf as? KOneOfString)?.oneOfString
    val oneOfInt: Int?
        get() = (oneOf as? KOneOfInt)?.oneOfInt

    override fun invoke(): String ="pbandk.testpb.Proto3PresenceMain"
}

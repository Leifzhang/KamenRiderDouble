@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package pbandk.testpb

import com.bilibili.w.sample.KTestSer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * @Author LiABao
 * @Since 2023/8/10
 */
@Serializable
public enum class KProto3PresenceEnum(val value: Int) {
    PROTO3_PRESENCE_ENUM_UNSPECIFIED(0), UNRECOGNIZED(-1);

    public companion object {
        public val values: List<KProto3PresenceEnum> by lazy {
            listOf(
                PROTO3_PRESENCE_ENUM_UNSPECIFIED
            )
        }

        fun fromValue(value: Int): KProto3PresenceEnum =
            values.firstOrNull { it.value == value } ?: UNRECOGNIZED

        fun fromName(name: String): KProto3PresenceEnum = values.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("No KProto3PresenceEnum with name: $name")
    }
}

@Serializable
public data class KProto3PresenceMessage(
    @ProtoNumber(4) val string: String = "",
) : Function0<String> {

    init {
    }

    override fun invoke(): String = "pbandk.testpb.Proto3PresenceMessage"
}

@Serializable
public data class KProto3PresenceMain(
    @ProtoNumber(2) val message: pbandk.testpb.KProto3PresenceMessage? = null,
    @ProtoNumber(4) val string: String = "",
    @ProtoNumber(6) val int: Int = 0,
    @ProtoNumber(8) val enum: Int = 0,
    @ProtoNumber(1) val optionalMessage: pbandk.testpb.KProto3PresenceMessage? = null,
    @ProtoNumber(3) val optionalString: String? = null,
    @ProtoNumber(5) val optionalInt: Int? = null,
    @ProtoNumber(7) val optionalEnum: Int = 0,
    @ProtoNumber(9) private val oneOfString: String? = null,
    @ProtoNumber(10) private val oneOfInt: Int? = null,
) : Function0<String> {

    @delegate:Transient
    private val oneOfNumber by lazy {
        if( oneOfString != null) {
            0
        } else if( oneOfInt != null){
            1
        } else {
            -1
        }
    }


    public sealed class KOneOf(val value: Int)

    public object KOneOfString : KOneOf(0)
    public object KOneOfInt : KOneOf(1)

    fun fromValue(): KOneOf? = values.firstOrNull { it.value == oneOfNumber }

    fun <T> oneOfValue(): T? {
        if (oneOfString != null) {
            return oneOfString as T
        } else if (oneOfInt != null) {
            return oneOfInt as T
        } else {
            return null
        }
    }


    fun enumEnum(): pbandk.testpb.KProto3PresenceEnum? =
        pbandk.testpb.KProto3PresenceEnum.fromValue(enum)

    fun optionalEnumEnum(): pbandk.testpb.KProto3PresenceEnum? =
        pbandk.testpb.KProto3PresenceEnum.fromValue(optionalEnum)

    companion object {
        val values: List<KOneOf> by lazy {
            listOf(
                KOneOfString, KOneOfInt
            )
        }


    }

    override fun invoke(): String = "pbandk.testpb.Proto3PresenceMain"
}
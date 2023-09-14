@file:OptIn(ExperimentalSerializationApi::class)

package pbandk.testpb
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked

@Serializable
public enum class KProto3PresenceEnum(val value: Int){
    PROTO3_PRESENCE_ENUM_UNSPECIFIED(0),
    UNRECOGNIZED(-1);

    public companion object : Function0<String> {
        public val values: List<KProto3PresenceEnum> by lazy { listOf(PROTO3_PRESENCE_ENUM_UNSPECIFIED) }
        fun fromValue(value: Int): KProto3PresenceEnum = values.firstOrNull { it.value == value } ?: UNRECOGNIZED
        fun fromName(name: String): KProto3PresenceEnum = values.firstOrNull { it.name == name } ?: throw IllegalArgumentException("No KProto3PresenceEnum with name: $name")
        const val TAG = "pbandk.testpb.Proto3PresenceEnum"
        override fun invoke(): String = TAG
    }

}

@Serializable
public data class KProto3PresenceMessage(
    @ProtoNumber(4)  val string: String = "",
){
    companion object : Function0<String> {
        const val TAG = "pbandk.testpb.Proto3PresenceMessage"
        override fun invoke(): String = TAG
    }


}

@Serializable
public data class KProto3PresenceMain(
    @ProtoNumber(2)  val message: pbandk.testpb.KProto3PresenceMessage? = null,
    @ProtoNumber(4)  val string: String = "",
    @ProtoNumber(6)  val int: Int = 0,
    @ProtoNumber(8) @ProtoPacked  val enum: List<Int> = emptyList(),
    @ProtoNumber(1)  val optionalMessage: pbandk.testpb.KProto3PresenceMessage? = null,
    @ProtoNumber(3)  val optionalString: String? = null,
    @ProtoNumber(5)  val optionalInt: Int? = null,
    @ProtoNumber(7)  val optionalEnum: Int = 0,
    @ProtoNumber(9) private val oneOfString: String?  = null,
    @ProtoNumber(10) private val oneOfInt: Int?  = null,
){

    @delegate:kotlin.jvm.Transient
    private val oneOfNumber by lazy {
        if( oneOfString != null) {
            0
        } else if( oneOfInt != null){
            1
        } else {
            -1
        }
    }

    public sealed class KOneOf(val value:Int)

    public object KOneOfString : KOneOf (0)

    public object KOneOfInt : KOneOf (1)


    fun <T> oneOfValue() : T? {
        if(oneOfString != null){
            return oneOfString  as T
        } else if(oneOfInt != null){
            return oneOfInt  as T
        } else { return null }
    }

    fun oneOfType(): KOneOf ? = oneOfValues.firstOrNull { it.value == oneOfNumber }


    companion object : Function0<String> {
        val oneOfValues : List<KOneOf> by lazy {
            listOf(KOneOfString,KOneOfInt)
        }
        const val TAG = "pbandk.testpb.Proto3PresenceMain"
        override fun invoke(): String = TAG
    }

    fun  optionalEnumEnum() : pbandk.testpb.KProto3PresenceEnum  = pbandk.testpb.KProto3PresenceEnum.fromValue(optionalEnum)


}
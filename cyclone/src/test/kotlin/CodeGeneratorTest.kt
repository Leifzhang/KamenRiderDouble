import com.bilibili.proto.cyclone.Request
import com.bilibili.proto.cyclone.runGenerator
import com.google.protobuf.DescriptorProtos
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import java.io.File

class CodeGeneratorTest {

    private val descriptorSetOutput =
        File("build/generateTestProtoDescriptor/fileDescriptor.protoset")
    private val fileDescriptorSet by lazy {
        check(descriptorSetOutput.exists()) {
            "${descriptorSetOutput.absolutePath} does not exist, make sure it is generated via :generateTestProto"
        }
        DescriptorProtos.FileDescriptorSet.parseFrom(descriptorSetOutput.inputStream()).fileList
    }


    @Test
    fun testSample() {
        request("simple.proto")
    }

    @Test
    fun testDeprecatedAnnotation() {
        request("options.proto")
    }

    @Test
    fun testOneOf_SameNameField() {
        request("oneof_same_name.proto")
    }

    fun request(name: String): KotlinCompilation.Result {
        val request = runGenerator(Request(listOf(name), protoFile = fileDescriptorSet))
        val kotlinSource = SourceFile.kotlin(
            File(request.fileList.first().name!!).name, request.fileList.first().content!!
        )
        return KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
    }
}
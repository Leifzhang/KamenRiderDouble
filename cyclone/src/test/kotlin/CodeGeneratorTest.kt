import com.bilibili.proto.cyclone.Request
import com.bilibili.proto.cyclone.runGenerator
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
       // FileDescriptorSet.decodeFromStream(descriptorSetOutput.inputStream()).file
    }


    @Test
    fun testSample() {
        request("simple.proto")
    }

    fun request(name: String) {
        val request = runGenerator(Request(listOf(name)))
        //val kotlinSource = SourceFile.kotlin(File(gen.file.first().name!!).name, gen.file.first().content!!)
    }
}
# KamenRiderDouble

 [protobuf 2 kotlin 插件](https://juejin.cn/post/7269032845787840567)

 本仓库结合了[pbandk 仓库地址](https://github.com/streem/pbandk)以及kotlin官方`serialization`插件库。然后完成了proto文件转化成kotin data class的能力。

 好处就是生成出来的所有的类全部都是kotlin，然后可以被一个kmp(kotlin multiplatform project)工程依赖,然后解决protobuf的class问题。

 其中对于proto 语法中的枚举还有oneof都测试已经通过了。而且在实际工程中也完成了调用`serialization`的`protobuf`支持，完成了序列化和反序列化的测试。

# 测试

可以直接用cyclone的 test直接模拟类生成的逻辑。

```kotlin 
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


    @Test
    fun testProto3Optional() {
        val result = request("proto_3_presence.proto")
    }

    @Test
    fun testAddress() {
        val result = request("addressbook.proto")
    }

    @Test
    fun testGoogle() {
        val result = request("descriptor.proto")
    }

```

# TODO

 google protobuf的基础库我忘了输出了，后续会进行补充。

 另外
 
 
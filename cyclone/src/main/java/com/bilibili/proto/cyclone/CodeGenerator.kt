package com.bilibili.proto.cyclone

open class CodeGenerator(
    public val file: File,
    public val kotlinTypeMappings: Map<String, String>,
    public val params: Map<String, String>
) {
    protected val visibility: String = params["visibility"] ?: "public"

    protected val bld: StringBuilder = StringBuilder()
    protected var indent: String = ""

    public fun generate(): String {
        //   line("@file:OptIn(pbandk.PublicForGeneratedCode::class)").line()
        file.kotlinPackageName?.let {
            line("@file:OptIn(ExperimentalSerializationApi::class)")
            line()
            line("package $it")
            line("import kotlinx.serialization.Serializable")
            line("import kotlinx.serialization.ExperimentalSerializationApi")
            line("import kotlinx.serialization.protobuf.ProtoNumber")
            line("import kotlinx.serialization.protobuf.ProtoPacked")
        }
        file.types.forEach { writeType(it) }
        //  file.extensions.forEach { writeExtension(it) }
        // file.types.filterIsInstance<File.Type.Message>().forEach { writeMessageExtensions(it) }
        return bld.toString()
    }

    protected fun line(): CodeGenerator = also { bld.appendLine() }
    protected fun line(str: String): CodeGenerator = also { bld.append(indent).appendLine(str) }
    protected fun lineBegin(str: String = ""): CodeGenerator =
        also { bld.append(indent).append(str) }

    protected fun lineMid(str: String): CodeGenerator = also { bld.append(str) }
    protected fun lineEnd(str: String = ""): CodeGenerator = also { bld.appendLine(str) }
    protected fun indented(fn: () -> Any?): CodeGenerator = also {
        indent += "    "
        fn().also { indent = indent.dropLast(4) }
    }

    protected fun writeType(
        type: File.Type, nested: Boolean = false
    ) {
        when (type) {
            is File.Type.Enum -> writeEnumType(type, nested)
            is File.Type.Message -> writeMessageType(type, nested)
        }
    }

    protected fun writeEnumType(type: File.Type.Enum, nested: Boolean = false) {
        line()
        line("@Serializable")
        // Only mark top-level classes for export, internal classes will be exported transitively
        // Enums are sealed classes w/ a value and a name, and a companion object with all values
        line("$visibility enum class ${type.kotlinTypeName}(val value: Int){").indented {
            type.values.forEach {
                line("${it.kotlinValueTypeName}(${it.number}),")
            }
            line("UNRECOGNIZED(-1);")
            line()
            line("$visibility companion object {").indented {
                line("$visibility val values: List<${type.kotlinFullTypeName}> by lazy { listOf(${
                    type.values.joinToString(
                        ", "
                    ) { it.kotlinValueTypeName }
                }) }")
                line("fun fromValue(value: Int): ${type.kotlinFullTypeName} = values.firstOrNull { it.value == value } ?: UNRECOGNIZED")
                line("fun fromName(name: String): ${type.kotlinFullTypeName} = values.firstOrNull { it.name == name } ?: throw IllegalArgumentException(\"No ${type.kotlinTypeName} with name: \$name\")")

                line("""cont val TAG = "${type.realName()}" """)
            }.line("}").line()
        }.line("}")
    }

    protected fun writeMessageType(type: File.Type.Message, nested: Boolean = false) {/* var messageInterface =
             if (type.extensionRange.isNotEmpty()) "pbandk.ExtendableMessage" else "pbandk.Message"*/

        //if (type.mapEntry) messageInterface += ", Map.Entry<${type.mapEntryKeyKotlinType}, ${type.mapEntryValueKotlinType}>"

        line()
        // Only mark top-level classes for export, internal classes will be exported transitively
        line("@Serializable")
        val classType = if (type.fields.isEmpty()) "" else "data"
        line("$visibility $classType class ${type.kotlinTypeName}(").indented {
            val fieldBegin = ""
            type.fields.forEach { field ->
                when (field) {
                    is File.Field.Numbered -> {
                        addDeprecatedAnnotation(field)
                        lineBegin(fieldBegin).writeConstructorField(field).lineEnd(",")
                    }

                    is File.Field.OneOf -> writeOneofCase(field)
                }
            }
            lineEnd("){").indented {
                //   type.fields.filterIsInstance<File.Field.OneOf>().forEach(::writeOneOfType)
                initOneofCase(type, type.fields.filterIsInstance<File.Field.OneOf>())
                type.fields.filterIsInstance<File.Field.Numbered.Standard>().filter { it ->
                    it.type == File.Field.Type.ENUM
                }.apply { enumsCase(this) }
                // Companion object

                // Nested enums and types
                type.nestedTypes.forEach { writeType(it, true) }
                line()
            }
        }.line("}")
    }

    private fun writeOneofCase(oneof: File.Field.OneOf) {
        oneof.fields.forEach { field ->
            line(
                "@ProtoNumber(${field.number}) private val ${field.kotlinFieldName}: ${
                    field.kotlinValueType(
                        false
                    )
                }?  = ${field.defaultValue(allowNulls = true)},"
            )
        }
    }

    private fun enumsCase(enums: List<File.Field.Numbered.Standard>) {
        enums.forEach { it ->
            if (!it.repeated && !it.map) {
                line(
                    "fun  ${it.kotlinFieldName}Enum() : ${it.kotlinQualifiedTypeNameNonEnum}  = ${
                        it.kotlinQualifiedTypeNameNonEnum
                    }.fromValue(${it.kotlinFieldName})"
                ).line()
            }
        }
    }

    private fun initOneofCase(type: File.Type.Message, case: List<File.Field.OneOf>) {
        if (!case.isEmpty()) {
            line()
            case.forEach {
                line("@delegate:Transient")
                line("private val ${it.kotlinFieldName}Number by lazy { ").indented {
                    var getNumberText = ""
                    it.fields.forEachIndexed { index, standard ->
                        getNumberText += if (index == 0) {
                            "if( ${standard.kotlinFieldName} != null) {\r"
                        } else {
                            "} else if( ${standard.kotlinFieldName} != null){\r"
                        }
                        getNumberText += "    $index \r"
                        if (index == it.fields.size - 1) {
                            getNumberText += """} else {
                            |    -1
                            |}
                        """.trimMargin()
                        }
                    }
                    getNumberText.lines().forEach { line ->
                        line(line)
                    }
                }.line("}")
            }
            line()
            case.forEach { oneOf ->
                line("$visibility sealed class ${oneOf.kotlinTypeName}(val value:Int)").line()
                var getOneofValue = ""
                oneOf.fields.forEachIndexed { index, field ->
                    addDeprecatedAnnotation(field)
                    lineBegin("$visibility object ${oneOf.kotlinFieldTypeNames[field.name]}")
                    lineEnd(" : ${oneOf.kotlinTypeName} ($index)").line()
                    if (index == 0) {
                        getOneofValue += "if"
                    } else {
                        getOneofValue += " else if"
                    }
                    getOneofValue += """(${field.kotlinFieldName} != null){
                    |    return ${field.kotlinFieldName}  as T
                    |}
                """.trimMargin()
                }
                getOneofValue += """ else { return null }"""

                line()

                line("fun <T> ${oneOf.kotlinFieldName}Value() : T? {").indented {
                    getOneofValue.lines().forEach {
                        line(it)
                    }
                }.line("}")

                line()
                line("fun ${oneOf.kotlinFieldName}Type(): ${oneOf.kotlinTypeName} ? = ${oneOf.kotlinFieldName}Values.firstOrNull { it.value == ${oneOf.kotlinFieldName}Number }")

                line().line()
            }
        }
        line("companion object {").indented {
            case.forEach { oneOf ->
                var text = ""
                oneOf.fields.forEachIndexed { index, field ->
                    text += "${oneOf.kotlinFieldTypeNames[field.name]},"
                }
                line("val ${oneOf.kotlinFieldName}Values : List<${oneOf.kotlinTypeName}> by lazy {").indented {
                    line("listOf(${text.substring(0, text.length - 1)})")
                }.line("}")
            }
            line("""cont val TAG = "${type.realName()}" """)
        }.line("}").line()
    }


    private fun writeConstructorField(field: File.Field.Numbered): CodeGenerator {
        if (field is File.Field.Numbered.Standard && field.required) {
            lineMid(
                "@ProtoNumber(${field.number}) ${
                    if (field.repeated || field.map) {
                        "@ProtoPacked "
                    } else {
                        ""
                    }
                }val ${field.kotlinFieldName}: ${field.kotlinValueType(false)}"
            )
        } else {
            lineMid(
                "@ProtoNumber(${field.number}) ${
                    if (field.repeated) {
                        "@ProtoPacked "
                    } else {
                        ""
                    }
                } val ${field.kotlinFieldName}: ${field.kotlinValueType(true)}"
            )
            lineMid(" = ${field.defaultValue}")
        }
        return this
    }


    protected fun findLocalType(protoName: String, parent: File.Type.Message? = null): File.Type? {
        // Get the set to look in and the type name
        val (lookIn, typeName) = if (parent == null) file.types to protoName.removePrefix(".${file.packageName}.")
        else parent.nestedTypes to protoName
        // Go deeper if there's a dot
        typeName.indexOf('.').let {
            if (it == -1) return lookIn.find { type -> type.name == typeName }
            return findLocalType(typeName.substring(it + 1),
                typeName.substring(0, it).let { parentTypeName ->
                    lookIn.find { type -> type.name == parentTypeName } as? File.Type.Message
                } ?: return null)
        }
    }

    private fun writeOneOfType(oneOf: File.Field.OneOf) {
        line("@Serializable")
        line("$visibility sealed interface ${oneOf.kotlinTypeName}").line()
        oneOf.fields.forEach { field ->
            addDeprecatedAnnotation(field)
            line("@Serializable")
            lineBegin("$visibility class ${oneOf.kotlinFieldTypeNames[field.name]}(@ProtoNumber(${field.number}) val ")
            lineMid("${field.kotlinFieldName}: ${field.kotlinValueType(false)}")
            if (field.type != File.Field.Type.MESSAGE) lineMid(" = ${field.defaultValue(allowNulls = false)}")
            lineEnd(") : ${oneOf.kotlinTypeName}")
        }
        line().line()

        oneOf.fields.forEach { field ->
            addDeprecatedAnnotation(field)
            line("val ${field.kotlinFieldName}: ${field.kotlinValueType(false)}?").indented {
                if (field.options.deprecated == true) line("@Suppress(\"DEPRECATION\")")
                lineBegin("get() = ")
                lineMid("(${oneOf.kotlinFieldName} as? ${oneOf.kotlinFieldTypeNames[field.name]})")
                lineEnd("?.${field.kotlinFieldName}")
            }
        }
        line()
    }

    protected val File.Type.Message.mapEntryKeyField: File.Field.Numbered.Standard?
        get() = if (!mapEntry) null else (fields[0] as File.Field.Numbered.Standard)
    protected val File.Type.Message.mapEntryValueField: File.Field.Numbered.Standard?
        get() = if (!mapEntry) null else (fields[1] as File.Field.Numbered.Standard)
    protected val File.Type.Message.mapEntryKeyKotlinType: String?
        get() = if (!mapEntry) null else (fields[0] as File.Field.Numbered.Standard).kotlinValueType(
            true
        )
    protected val File.Type.Message.mapEntryValueKotlinType: String?
        get() = if (!mapEntry) null else (fields[1] as File.Field.Numbered.Standard).kotlinValueType(
            true
        )

    protected fun File.Field.Numbered.kotlinValueType(allowNulls: Boolean): String = when (this) {
        is File.Field.Numbered.Standard -> kotlinValueType(allowNulls)
        is File.Field.Numbered.Wrapper -> kotlinValueType(allowNulls)
    }

    protected val File.Field.Numbered.defaultValue: String
        get() = when (this) {
            is File.Field.Numbered.Standard -> defaultValue()
            is File.Field.Numbered.Wrapper -> defaultValue
        }

    protected fun File.Field.Numbered.Standard.mapEntry(): File.Type.Message? =
        if (!map) null else (localType as? File.Type.Message)?.takeIf { it.mapEntry }

    protected val File.Field.Numbered.Standard.localType: File.Type?
        get() = localTypeName?.let {
            findLocalType(
                it
            )
        }
    private val File.Field.Numbered.Standard.kotlinQualifiedTypeName: String
        get() = let {
            if (type == File.Field.Type.ENUM) {
                "Int"
            } else if (kotlinLocalTypeName?.isNotEmpty() == true) {
                kotlinLocalTypeName
            } else if (localTypeName?.isNotEmpty() == true) {
                localTypeName.let { kotlinTypeMappings.getOrElse(it) { error("Unable to find mapping for $it") } }
            } else {
                type.standardTypeName
            }
        }

    private val File.Field.Numbered.Standard.kotlinQualifiedTypeNameNonEnum: String
        get() = let {
            if (kotlinLocalTypeName?.isNotEmpty() == true) {
                kotlinLocalTypeName
            } else if (localTypeName?.isNotEmpty() == true) {
                localTypeName.let { kotlinTypeMappings.getOrElse(it) { error("Unable to find mapping for $it") } }
            } else {
                type.standardTypeName
            }
        }


    private fun File.Field.Numbered.Standard.kotlinValueType(allowNulls: Boolean): String = when {
        map -> mapEntry()!!.let { "Map<${it.mapEntryKeyKotlinType}, ${it.mapEntryValueKotlinType}>" }
        repeated -> "List<$kotlinQualifiedTypeName>"
        allowNulls && hasPresence && type != File.Field.Type.ENUM -> "$kotlinQualifiedTypeName?"
        else -> kotlinQualifiedTypeName
    }

    protected fun File.Field.Numbered.Standard.defaultValue(allowNulls: Boolean = true): String =
        when {
            map -> "emptyMap()"
            repeated -> "emptyList()"
            type == File.Field.Type.ENUM -> "0"
            allowNulls && hasPresence -> "null"
            //  type == File.Field.Type.ENUM -> "$kotlinQualifiedTypeName.fromValue(0)"
            else -> type.defaultValue
        }

    protected val File.Field.Numbered.Standard.requiresExplicitTypeWithVal: Boolean
        get() = repeated || hasPresence || type.requiresExplicitTypeWithVal

    protected fun File.Field.Numbered.Wrapper.kotlinValueType(allowNulls: Boolean): String = when {
        repeated -> "List<${wrappedType.standardTypeName}>"
        else -> wrappedType.standardTypeName + if (allowNulls) "?" else ""
    }

    protected val File.Field.Numbered.Wrapper.defaultValue: String
        get() = when {
            repeated -> "emptyList()"
            type == File.Field.Type.ENUM -> "0"
            else -> "null"
        }

    protected val File.Field.Type.string: String
        get() = when (this) {
            File.Field.Type.BOOL -> "bool"
            File.Field.Type.BYTES -> "bytes"
            File.Field.Type.DOUBLE -> "double"
            File.Field.Type.ENUM -> "enum"
            File.Field.Type.FIXED32 -> "fixed32"
            File.Field.Type.FIXED64 -> "fixed64"
            File.Field.Type.FLOAT -> "float"
            File.Field.Type.INT32 -> "int32"
            File.Field.Type.INT64 -> "int64"
            File.Field.Type.MESSAGE -> "message"
            File.Field.Type.SFIXED32 -> "sFixed32"
            File.Field.Type.SFIXED64 -> "sFixed64"
            File.Field.Type.SINT32 -> "sInt32"
            File.Field.Type.SINT64 -> "sInt64"
            File.Field.Type.STRING -> "string"
            File.Field.Type.UINT32 -> "uInt32"
            File.Field.Type.UINT64 -> "uInt64"
        }
    protected val File.Field.Type.standardTypeName: String
        get() = when (this) {
            File.Field.Type.BOOL -> "Boolean"
            File.Field.Type.BYTES -> "ByteArray"
            File.Field.Type.DOUBLE -> "Double"
            File.Field.Type.ENUM -> error("No standard type name for enums")
            File.Field.Type.FIXED32 -> "Int"
            File.Field.Type.FIXED64 -> "Long"
            File.Field.Type.FLOAT -> "Float"
            File.Field.Type.INT32 -> "Int"
            File.Field.Type.INT64 -> "Long"
            File.Field.Type.MESSAGE -> error("No standard type name for messages")
            File.Field.Type.SFIXED32 -> "Int"
            File.Field.Type.SFIXED64 -> "Long"
            File.Field.Type.SINT32 -> "Int"
            File.Field.Type.SINT64 -> "Long"
            File.Field.Type.STRING -> "String"
            File.Field.Type.UINT32 -> "Int"
            File.Field.Type.UINT64 -> "Long"
        }
    protected val File.Field.Type.defaultValue: String
        get() = when (this) {
            File.Field.Type.BOOL -> "false"
            File.Field.Type.BYTES -> "byteArrayOf()"
            File.Field.Type.DOUBLE -> "0.0"
            File.Field.Type.ENUM -> error("No generic default value for enums")
            File.Field.Type.FIXED32, File.Field.Type.INT32, File.Field.Type.SFIXED32, File.Field.Type.SINT32, File.Field.Type.UINT32 -> "0"

            File.Field.Type.FIXED64, File.Field.Type.INT64, File.Field.Type.SFIXED64, File.Field.Type.SINT64, File.Field.Type.UINT64 -> "0L"

            File.Field.Type.FLOAT -> "0.0F"
            File.Field.Type.MESSAGE -> "null"
            File.Field.Type.STRING -> "\"\""
        }
    protected val File.Field.Type.requiresExplicitTypeWithVal: Boolean
        get() = this == File.Field.Type.BYTES || this == File.Field.Type.ENUM || this == File.Field.Type.MESSAGE
    protected val File.Field.Type.wrapperKotlinTypeName: String
        get() = kotlinTypeMappings[wrapperTypeName] ?: error("No Kotlin type found for wrapper")

    private fun addDeprecatedAnnotation(field: File.Field) {
        when (field) {
            is File.Field.Numbered -> if (field.options.deprecated) line("@Deprecated(message = \"Field marked deprecated in ${file.name}\")")
            is File.Field.OneOf -> {
                // oneof fields do not support the `deprecated` protobuf option
            }
        }
    }
}

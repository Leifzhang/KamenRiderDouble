package com.bilibili.proto.cyclone

import com.google.protobuf.DescriptorProtos
public open class CodeGenerator(
    public val file: File,
    public val kotlinTypeMappings: Map<String, String>,
    public val params: Map<String, String>
) {
    protected val visibility: String = params["visibility"] ?: "public"

    protected val bld: StringBuilder = StringBuilder()
    protected var indent: String = ""

    public fun generate(): String {
     //   line("@file:OptIn(pbandk.PublicForGeneratedCode::class)").line()
        file.kotlinPackageName?.let { line("package $it") }
        file.types.forEach { writeType(it) }
      //  file.extensions.forEach { writeExtension(it) }
       // file.types.filterIsInstance<File.Type.Message>().forEach { writeMessageExtensions(it) }
        return bld.toString()
    }

    protected fun line(): CodeGenerator = also { bld.appendLine() }
    protected fun line(str: String): CodeGenerator = also { bld.append(indent).appendLine(str) }
    protected fun lineBegin(str: String = ""): CodeGenerator = also { bld.append(indent).append(str) }
    protected fun lineMid(str: String): CodeGenerator = also { bld.append(str) }
    protected fun lineEnd(str: String = ""): CodeGenerator = also { bld.appendLine(str) }
    protected fun indented(fn: () -> Any?): CodeGenerator = also {
        indent += "    "
        fn().also { indent = indent.dropLast(4) }
    }

    protected fun writeType(
        type: File.Type,
        nested: Boolean = false
    ) {
        when (type) {
            is File.Type.Enum -> writeEnumType(type, nested)
            is File.Type.Message -> writeMessageType(type, nested)
        }
    }

    protected fun writeEnumType(type: File.Type.Enum, nested: Boolean = false) {
        line()
        // Only mark top-level classes for export, internal classes will be exported transitively
        if (!nested) line("@pbandk.Export")
        // Enums are sealed classes w/ a value and a name, and a companion object with all values
        line("$visibility sealed class ${type.kotlinTypeName}(override val value: Int, override val name: String? = null) : pbandk.Message.Enum {")
            .indented {
                line("override fun equals(other: kotlin.Any?): Boolean = other is ${type.kotlinFullTypeName} && other.value == value")
                line("override fun hashCode(): Int = value.hashCode()")
                line("override fun toString(): String = \"${type.kotlinFullTypeName}.\${name ?: \"UNRECOGNIZED\"}(value=\$value)\"")
                line()
                type.values.forEach { line("$visibility object ${it.kotlinValueTypeName} : ${type.kotlinTypeName}(${it.number}, \"${it.name}\")") }
                line("$visibility class UNRECOGNIZED(value: Int) : ${type.kotlinTypeName}(value)")
                line()
                line("$visibility companion object : pbandk.Message.Enum.Companion<${type.kotlinFullTypeName}> {").indented {
                    line("$visibility val values: List<${type.kotlinFullTypeName}> by lazy { listOf(${type.values.joinToString(", ") { it.kotlinValueTypeName }}) }")
                    line("override fun fromValue(value: Int): ${type.kotlinFullTypeName} = values.firstOrNull { it.value == value } ?: UNRECOGNIZED(value)")
                    line("override fun fromName(name: String): ${type.kotlinFullTypeName} = values.firstOrNull { it.name == name } ?: throw IllegalArgumentException(\"No ${type.kotlinTypeName} with name: \$name\")")
                }.line("}")
            }.line("}")
    }

    protected fun writeMessageType(type: File.Type.Message, nested: Boolean = false) {
        var messageInterface = if (type.extensionRange.isNotEmpty()) "pbandk.ExtendableMessage" else "pbandk.Message"

        if (type.mapEntry) messageInterface += ", Map.Entry<${type.mapEntryKeyKotlinType}, ${type.mapEntryValueKotlinType}>"

        line()
        // Only mark top-level classes for export, internal classes will be exported transitively
        if (!nested) line("@Serializable")
        line("$visibility data class ${type.kotlinTypeName}(").indented {
            val fieldBegin = if (type.mapEntry) "override " else ""
            type.fields.forEach { field ->
                when (field) {
                    is File.Field.Numbered -> {
                        addDeprecatedAnnotation(field)
                        lineBegin(fieldBegin).writeConstructorField(field).lineEnd(",")
                    }
                    is File.Field.OneOf -> line("val ${field.kotlinFieldName}: ${field.kotlinTypeName}<*>? = null,")
                }
            }

            line(")")
        }
    }

    protected fun writeConstructorField(field: File.Field.Numbered): CodeGenerator {
        if (field is File.Field.Numbered.Standard && field.required) {
            lineMid("val ${field.kotlinFieldName}: ${field.kotlinValueType(false)}")
        } else {
            lineMid("val ${field.kotlinFieldName}: ${field.kotlinValueType(true)}")
            lineMid(" = ${field.defaultValue}")
        }
        return this
    }

    protected fun writeMessageDescriptor(type: File.Type.Message) {
        val allFields = type.sortedStandardFieldsWithOneOfs()
        val chunkSize = 200
        val needToChunk = allFields.size > chunkSize

        // Messages can have circular references to each other (e.g. `pbandk.wkt.Struct` includes a `pbandk.wkt.Value`
        // field, but `pbandk.wkt.Value` includes a `pbandk.wkt.Struct` field). On Kotlin/Native the companion object
        // (e.g. `pbandk.wkt.Value.Companion`) is automatically frozen because it's a singleton. But Kotlin/Native
        // doesn't allow cyclic frozen structures:
        // https://kotlinlang.org/docs/reference/native/concurrency.html#global-variables-and-singletons. In order to
        // break the circular references, `descriptor` needs to be a `lazy` field.
        line("override val descriptor: pbandk.MessageDescriptor<${type.kotlinTypeNameWithPackage}> by lazy {").indented {
            // XXX: When a message has lots of fields (e.g. `TestAllTypesProto3`), declaring the list of field
            // descriptors directly in the [MessageDescriptor] constructor can cause a
            // `java.lang.OutOfMemoryError: Java heap space` error in the Kotlin compiler (as of Kotlin 1.4.20).
            // As a workaround, we generate methods to generate each fieldDescriptor in chunks, as many as needed, with
            // a max size of $chunkSize to limit the size of the methods.
            line("val fieldsList = ArrayList<pbandk.FieldDescriptor<${type.kotlinTypeNameWithPackage}, *>>(${allFields.size})")
            if (needToChunk) {
                allFields.chunked(chunkSize).forEachIndexed { index, _ ->
                    line("addFields${index}(fieldsList)")
                }
            } else {
                addFields(allFields, type.kotlinTypeNameWithPackage)
            }

            line("pbandk.MessageDescriptor(").indented {
                line("fullName = \"${type.fullName}\",")
                line("messageClass = ${type.kotlinTypeNameWithPackage}::class,")
                line("messageCompanion = this,")
                line("fields = fieldsList")
            }.line(")")
        }.line("}")

        if (needToChunk) {
            allFields.chunked(chunkSize).forEachIndexed { index, chunk ->
                line("fun addFields${index}(fieldsList: ArrayList<pbandk.FieldDescriptor<${type.kotlinTypeNameWithPackage}, *>>) {").indented {
                    addFields(chunk, type.kotlinTypeNameWithPackage)
                }.line("}")
            }
        }
    }

    private fun addFields(
        chunk: List<Pair<File.Field.Numbered, File.Field.OneOf?>>,
        fullTypeName: String
    ) {
        line("fieldsList.apply {").indented {
            chunk.forEach { (field, oneof) ->
                if (field.options.deprecated == true) line("@Suppress(\"DEPRECATION\")")
                line("add(").indented {
                    line("pbandk.FieldDescriptor(").indented {
                        generateFieldDescriptorConstructorValues(
                            field,
                            fullTypeName,
                            oneof,
                            "this@Companion::descriptor"
                        )
                    }.line(")")
                }.line(")")
            }
        }.line("}")
    }

    private fun generateFieldOptions(fieldOptions: DescriptorProtos.FieldOptions) {
        // We don't use/support other field option values currently. Once we support all of the options, this check
        // should change to `fieldOptions != FieldOptions.defaultInstance`
//        val unknownFields= fieldOptions.unknownFields.asMap()
//        if (fieldOptions.deprecated != null || unknownFields.isNotEmpty()) {
//            line("options = pbandk.wkt.FieldOptions(").indented {
//                fieldOptions.deprecated?.let {
//                    lineBegin("deprecated = $it")
//                    if (unknownFields.isEmpty()) lineEnd() else lineEnd(",")
//                }
//                if (unknownFields.isNotEmpty()) {
//                    generateUnknownFields(fieldOptions.unknownFields.asMap())
//                }
//            }.line("),")
//        }
    }


    protected fun writeMessageOrDefaultExtension(type: File.Type.Message) {
        line()
        // There can be multiple differently-typed `orDefault` functions in the same scope which
        // Kotlin/JS cannot handle unfortunately. We have to annotate each of them with a unique
        // name so that Kotlin/JS knows which name to choose.
        //
        // Also, if current type is an inner class, `fullTypeName` will contains dots which we
        // have to get rid of (i.e. `Person.AddressBook` becomes `PersonAddressBook`).
        line("$visibility fun ${type.kotlinFullTypeName}?.orDefault(): ${type.kotlinTypeNameWithPackage} = this ?: ${type.kotlinFullTypeName}.defaultInstance")
    }

    protected fun writeMessageMergeExtension(type: File.Type.Message) {
        fun mergeStandard(field: File.Field.Numbered.Standard) {
            val paramEquals = "${field.kotlinFieldName} ="
            when {
                field.repeated ->
                    line("$paramEquals ${field.kotlinFieldName} + plus.${field.kotlinFieldName},")
                field.type == File.Field.Type.MESSAGE ->
                    if (field.required) {
                        line("$paramEquals ${field.kotlinFieldName}.plus(plus.${field.kotlinFieldName}),")
                    } else {
                        line("$paramEquals ${field.kotlinFieldName}?.plus(plus.${field.kotlinFieldName}) ?: plus.${field.kotlinFieldName},")
                    }
                field.hasPresence && !field.required ->
                    line("$paramEquals plus.${field.kotlinFieldName} ?: ${field.kotlinFieldName},")
            }
        }

        fun mergeWrapper(field: File.Field.Numbered.Wrapper) {
            if (field.repeated) {
                line("${field.kotlinFieldName} = ${field.kotlinFieldName} + plus.${field.kotlinFieldName},")
            } else {
                line("${field.kotlinFieldName} = plus.${field.kotlinFieldName} ?: ${field.kotlinFieldName},")
            }
        }

        fun mergeOneOf(oneOf: File.Field.OneOf) {
            val fieldsToMerge = oneOf.fields.filter { it.repeated || it.type == File.Field.Type.MESSAGE }
            if (fieldsToMerge.isEmpty()) {
                line("${oneOf.kotlinFieldName} = plus.${oneOf.kotlinFieldName} ?: ${oneOf.kotlinFieldName},")
            } else {
                line("${oneOf.kotlinFieldName} = when {").indented {
                    fieldsToMerge.forEach { subField ->
                        val subTypeName = "${type.kotlinFullTypeName}." +
                            "${oneOf.kotlinTypeName}.${oneOf.kotlinFieldTypeNames[subField.name]}"
                        line(
                            "${oneOf.kotlinFieldName} is $subTypeName && " +
                                "plus.${oneOf.kotlinFieldName} is $subTypeName ->"
                        ).indented {
                            line(
                                "$subTypeName(${oneOf.kotlinFieldName}.value + " +
                                    "plus.${oneOf.kotlinFieldName}.value)"
                            )
                        }
                    }
                    line("else ->").indented {
                        line("plus.${oneOf.kotlinFieldName} ?: ${oneOf.kotlinFieldName}")
                    }
                }.line("},")
            }
        }

        line()
        line("private fun ${type.kotlinFullTypeName}.protoMergeImpl(plus: pbandk.Message?): ${type.kotlinFullTypeName} = (plus as? ${type.kotlinFullTypeName})?.let {").indented {
            if (type.sortedStandardFieldsWithOneOfs().any { it.first.options.deprecated == true }) {
                line("@Suppress(\"DEPRECATION\")")
            }
            line("it.copy(").indented {
                type.fields.forEach { field ->
                    when (field) {
                        is File.Field.Numbered.Standard -> mergeStandard(field)
                        is File.Field.Numbered.Wrapper -> mergeWrapper(field)
                        is File.Field.OneOf -> mergeOneOf(field)
                    }
                }
                line("unknownFields = unknownFields + plus.unknownFields")
            }.line(")")

        }.line("} ?: this")
    }

    protected fun writeMessageDecodeWithExtension(type: File.Type.Message) {
        val lineStr = "private fun ${type.kotlinFullTypeName}.Companion." +
            "decodeWithImpl(u: pbandk.MessageDecoder): ${type.kotlinFullTypeName} {"
        line().line("@Suppress(\"UNCHECKED_CAST\")").line(lineStr).indented {
            // A bunch of locals for each field, initialized with defaults
            val doneKotlinFields = type.fields.map {
                when (it) {
                    is File.Field.Numbered.Standard -> {
                        line(it.decodeWithVarDecl)
                        it.decodeWithVarDone
                    }
                    is File.Field.Numbered.Wrapper -> {
                        line(it.decodeWithVarDecl)
                        it.decodeWithVarDone
                    }
                    is File.Field.OneOf -> {
                        line("var ${it.kotlinFieldName}: ${type.kotlinFullTypeName}.${it.kotlinTypeName}<*>? = null")
                        it.kotlinFieldName
                    }
                }
            }

            // Now loop reading each field and check fields in order
            line().lineBegin("val unknownFields = u.readMessage(this) { ")
            type.sortedStandardFieldsWithOneOfs().takeIf { it.isNotEmpty() }?.let { fields ->
                lineEnd("_fieldNumber, _fieldValue ->").indented {
                    line("when (_fieldNumber) {").indented {
                        fields.forEach { (field, oneOf) ->
                            lineBegin("${field.number} -> ")
                            if (oneOf == null) {
                                lineMid("${field.kotlinFieldName} = ")
                                val kotlinType = when (field) {
                                    is File.Field.Numbered.Standard -> field.kotlinQualifiedTypeName
                                    is File.Field.Numbered.Wrapper -> field.wrappedType.standardTypeName
                                }
                                when {
                                    field is File.Field.Numbered.Standard && field.map -> {
                                        val mapEntry = field.mapEntry()!!
                                        lineEnd("(${field.kotlinFieldName} ?: pbandk.MessageMap.Builder()).apply { this.entries += _fieldValue as kotlin.sequences.Sequence<pbandk.MessageMap.Entry<${mapEntry.mapEntryKeyKotlinType}, ${mapEntry.mapEntryValueKotlinType}>> }")
                                    }
                                    field.repeated -> {
                                        // TODO update ListWithSize.protoSize as each field is read
                                        // or maybe just make the field lazy and computed the first time it's accessed?
                                        lineEnd("(${field.kotlinFieldName} ?: pbandk.ListWithSize.Builder()).apply { this += _fieldValue as kotlin.sequences.Sequence<$kotlinType> }")
                                    }
                                    else -> {
                                        // TODO: for message types, merge multiple instances of the same field
                                        // see https://developers.google.com/protocol-buffers/docs/encoding#optional
                                        lineEnd("_fieldValue as $kotlinType")
                                    }
                                }
                            } else {
                                val oneOfTyp =
                                    "${type.kotlinFullTypeName}.${oneOf.kotlinTypeName}.${oneOf.kotlinFieldTypeNames[field.name]}"
                                require(field is File.Field.Numbered.Standard && !field.repeated)
                                val lineContent = "${oneOf.kotlinFieldName} = $oneOfTyp(_fieldValue as ${field.kotlinQualifiedTypeName})"
                                if (field.options.deprecated == true) {
                                    lineEnd("{").indented {
                                        line("@Suppress(\"DEPRECATION\")")
                                        line(lineContent)
                                    }.line("}")
                                } else {
                                    lineEnd(lineContent)
                                }
                            }
                        }
                    }.line("}")
                }.line("}")
            } ?: lineEnd("_, _ -> }")

            line()
            type.fields.filterIsInstance<File.Field.Numbered.Standard>().filter { it.required }.forEach { field ->
                line("if (${field.kotlinFieldName} == null) {").indented {
                    line("throw pbandk.InvalidProtocolBufferException.missingRequiredField(\"${field.name}\")")
                }.line("}")
            }

            // Wrap the params to the class and return it
            lineBegin("return ${type.kotlinFullTypeName}(")
            val chunkedDoneFields = doneKotlinFields.chunked(4)
            chunkedDoneFields.forEachIndexed { index, doneFieldSet ->
                val doneFieldStr = doneFieldSet.joinToString(", ", postfix = ",")
                if (index == 0 && chunkedDoneFields.size == 1) lineMid(doneFieldStr)
                else if (index == 0) lineEnd(doneFieldStr)
                else if (index == chunkedDoneFields.size - 1) indented { lineBegin(doneFieldStr) }
                else indented { line(doneFieldStr) }
            }
            if (chunkedDoneFields.isNotEmpty()) lineMid(" ")
            lineEnd("unknownFields)")
        }.line("}")
    }

    protected fun findLocalType(protoName: String, parent: File.Type.Message? = null): File.Type? {
        // Get the set to look in and the type name
        val (lookIn, typeName) =
            if (parent == null) file.types to protoName.removePrefix(".${file.packageName}.")
            else parent.nestedTypes to protoName
        // Go deeper if there's a dot
        typeName.indexOf('.').let {
            if (it == -1) return lookIn.find { type -> type.name == typeName }
            return findLocalType(typeName.substring(it + 1), typeName.substring(0, it).let { parentTypeName ->
                lookIn.find { type -> type.name == parentTypeName } as? File.Type.Message
            } ?: return null)
        }
    }

    protected val File.Type.kotlinTypeNameWithPackage: String
        get() = file.kotlinPackageName?.let { "$it." }.orEmpty() + kotlinFullTypeName

    protected fun File.Type.Message.sortedStandardFieldsWithOneOfs(): List<Pair<File.Field.Numbered, File.Field.OneOf?>> =
        fields.flatMap {
            when (it) {
                is File.Field.Numbered.Standard -> listOf(it to null)
                is File.Field.Numbered.Wrapper -> listOf(it to null)
                is File.Field.OneOf -> it.fields.map { f -> f to it }
            }
        }.sortedBy { it.first.number }

    protected val File.Type.Message.mapEntryKeyField: File.Field.Numbered.Standard?
        get() = if (!mapEntry) null else (fields[0] as File.Field.Numbered.Standard)
    protected val File.Type.Message.mapEntryValueField: File.Field.Numbered.Standard?
        get() = if (!mapEntry) null else (fields[1] as File.Field.Numbered.Standard)
    protected val File.Type.Message.mapEntryKeyKotlinType: String?
        get() = if (!mapEntry) null else (fields[0] as File.Field.Numbered.Standard).kotlinValueType(true)
    protected val File.Type.Message.mapEntryValueKotlinType: String?
        get() = if (!mapEntry) null else (fields[1] as File.Field.Numbered.Standard).kotlinValueType(true)

    protected fun File.Field.Numbered.kotlinValueType(allowNulls: Boolean): String = when (this) {
        is File.Field.Numbered.Standard -> kotlinValueType(allowNulls)
        is File.Field.Numbered.Wrapper -> kotlinValueType(allowNulls)
    }

    protected val File.Field.Numbered.extendeeKotlinType: String?
        get() = extendee?.let { kotlinTypeMappings[it] }

    protected val File.Field.Numbered.defaultValue: String
        get() = when (this) {
            is File.Field.Numbered.Standard -> defaultValue()
            is File.Field.Numbered.Wrapper -> defaultValue
        }

    protected fun File.Field.Numbered.fieldDescriptorType(isOneOfMember: Boolean = false): String {
        return "pbandk.FieldDescriptor.Type." + when (this) {
            is File.Field.Numbered.Standard -> when {
                map -> {
                    val mapEntry = mapEntry()!!
                    "Map<${mapEntry.mapEntryKeyKotlinType}, ${mapEntry.mapEntryValueKotlinType}>(" +
                        "keyType = ${mapEntry.mapEntryKeyField!!.fieldDescriptorType()}, " +
                        "valueType = ${mapEntry.mapEntryValueField!!.fieldDescriptorType()}" +
                        ")"
                }
                repeated -> "Repeated<$kotlinQualifiedTypeName>(valueType = ${copy(repeated = false).fieldDescriptorType()}${if (packed) ", packed = true" else ""})"
                type == File.Field.Type.MESSAGE -> "Message(messageCompanion = $kotlinQualifiedTypeName.Companion)"
                type == File.Field.Type.ENUM -> "Enum(enumCompanion = $kotlinQualifiedTypeName.Companion" + (if (hasPresence) ", hasPresence = true" else "") + ")"
                else -> "Primitive.${type.string.replaceFirstChar { it.titlecase() }}(" + (if (hasPresence) "hasPresence = true" else "") + ")"
            }
            is File.Field.Numbered.Wrapper -> when {
                repeated -> "Repeated<${wrappedType.standardTypeName}>(valueType = ${copy(repeated = false).fieldDescriptorType()})"
                else -> "Message(messageCompanion = ${wrappedType.wrapperKotlinTypeName}.Companion)"
            }
        }
    }

    protected fun File.Field.Numbered.Standard.mapEntry(): File.Type.Message? =
        if (!map) null else (localType as? File.Type.Message)?.takeIf { it.mapEntry }

    protected val File.Field.Numbered.Standard.localType: File.Type? get() = localTypeName?.let { findLocalType(it) }
    protected val File.Field.Numbered.Standard.kotlinQualifiedTypeName: String
        get() = kotlinLocalTypeName
            ?: localTypeName?.let { kotlinTypeMappings.getOrElse(it) { error("Unable to find mapping for $it") } }
            ?: type.standardTypeName
    protected val File.Field.Numbered.Standard.decodeWithVarDecl: String
        get() = when {
            repeated -> mapEntry().let { mapEntry ->
                if (mapEntry == null) "var $kotlinFieldName: pbandk.ListWithSize.Builder<$kotlinQualifiedTypeName>? = null"
                else "var $kotlinFieldName: pbandk.MessageMap.Builder<" +
                    "${mapEntry.mapEntryKeyKotlinType}, ${mapEntry.mapEntryValueKotlinType}>? = null"
            }
            requiresExplicitTypeWithVal -> "var $kotlinFieldName: ${kotlinValueType(true)} = ${defaultValue()}"
            else -> "var $kotlinFieldName = ${defaultValue()}"
        }
    protected val File.Field.Numbered.Standard.decodeWithVarDone: String
        get() = when {
            map -> "pbandk.MessageMap.Builder.fixed($kotlinFieldName)"
            repeated -> "pbandk.ListWithSize.Builder.fixed($kotlinFieldName)"
            required -> "${kotlinFieldName}!!"
            else -> kotlinFieldName
        }

    protected fun File.Field.Numbered.Standard.kotlinValueType(allowNulls: Boolean): String = when {
        map -> mapEntry()!!.let { "Map<${it.mapEntryKeyKotlinType}, ${it.mapEntryValueKotlinType}>" }
        repeated -> "List<$kotlinQualifiedTypeName>"
        allowNulls && hasPresence -> "$kotlinQualifiedTypeName?"
        else -> kotlinQualifiedTypeName
    }

    protected fun File.Field.Numbered.Standard.defaultValue(allowNulls: Boolean = true): String = when {
        map -> "emptyMap()"
        repeated -> "emptyList()"
        allowNulls && hasPresence -> "null"
        type == File.Field.Type.ENUM -> "$kotlinQualifiedTypeName.fromValue(0)"
        else -> type.defaultValue
    }
    protected val File.Field.Numbered.Standard.requiresExplicitTypeWithVal: Boolean
        get() = repeated || hasPresence || type.requiresExplicitTypeWithVal

    protected val File.Field.Numbered.Wrapper.decodeWithVarDecl: String
        get() = when {
            repeated -> "var $kotlinFieldName: pbandk.ListWithSize.Builder<${wrappedType.standardTypeName}>? = null"
            else -> "var $kotlinFieldName: ${wrappedType.standardTypeName}? = $defaultValue"
        }
    protected val File.Field.Numbered.Wrapper.decodeWithVarDone: String
        get() = when {
            repeated -> "pbandk.ListWithSize.Builder.fixed($kotlinFieldName)"
            else -> kotlinFieldName
        }

    protected fun File.Field.Numbered.Wrapper.kotlinValueType(allowNulls: Boolean): String = when {
        repeated -> "List<${wrappedType.standardTypeName}>"
        else -> wrappedType.standardTypeName + if (allowNulls) "?" else ""
    }

    protected val File.Field.Numbered.Wrapper.defaultValue: String
        get() = when {
            repeated -> "emptyList()"
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
            File.Field.Type.BYTES -> "pbandk.ByteArr"
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
            File.Field.Type.BYTES -> "pbandk.ByteArr.empty"
            File.Field.Type.DOUBLE -> "0.0"
            File.Field.Type.ENUM -> error("No generic default value for enums")
            File.Field.Type.FIXED32, File.Field.Type.INT32, File.Field.Type.SFIXED32,
            File.Field.Type.SINT32, File.Field.Type.UINT32 -> "0"
            File.Field.Type.FIXED64, File.Field.Type.INT64, File.Field.Type.SFIXED64,
            File.Field.Type.SINT64, File.Field.Type.UINT64 -> "0L"
            File.Field.Type.FLOAT -> "0.0F"
            File.Field.Type.MESSAGE -> "null"
            File.Field.Type.STRING -> "\"\""
        }
    protected val File.Field.Type.requiresExplicitTypeWithVal: Boolean
        get() = this == File.Field.Type.BYTES || this == File.Field.Type.ENUM || this == File.Field.Type.MESSAGE
    protected val File.Field.Type.wrapperKotlinTypeName: String
        get() = kotlinTypeMappings[wrapperTypeName] ?: error("No Kotlin type found for wrapper")

    private fun generateFieldDescriptorConstructorValues(
        field: File.Field.Numbered,
        fullTypeName: String,
        oneof: File.Field.OneOf?,
        messageDescriptorCompanion: String
    ) {
        line("messageDescriptor = ${messageDescriptorCompanion},")
        line("name = \"${field.name}\",")
        line("number = ${field.number},")
        line("type = ${field.fieldDescriptorType(oneof != null)},")
        oneof?.let { line("oneofMember = true,") }
        field.jsonName?.let { line("jsonName = \"$it\",") }
        generateFieldOptions(field.options)
        line("value = $fullTypeName::${field.kotlinFieldName}")
    }

    private fun addDeprecatedAnnotation(field: File.Field) {
        when (field) {
            is File.Field.Numbered -> if (field.options.deprecated == true) line("@Deprecated(message = \"Field marked deprecated in ${file.name}\")")
            is File.Field.OneOf -> {
                // oneof fields do not support the `deprecated` protobuf option
            }
        }
    }
}

package com.bilibili.proto.cyclone

import com.google.protobuf.DescriptorProtos

internal open class FileBuilder(
    val namer: Namer = Namer.Standard,
    val supportMaps: Boolean = true
) {
    fun buildFile(ctx: Context): File {
        val packageName = ctx.fileDesc.`package`?.takeIf { it.isNotEmpty() }
        val types= typesFromProto(
            ctx,
            ctx.fileDesc.enumTypeList,
            ctx.fileDesc.messageTypeList,
            packageName,
            null,
            mutableSetOf()
        )
        return File(
            name = ctx.fileDesc.name!!,
            packageName = packageName,
            kotlinPackageName = ctx.kotlinPackageName,
            version = ctx.fileDesc.syntax?.removePrefix("proto")?.toIntOrNull() ?: 2,
            types = typesFromProto(
                ctx,
                ctx.fileDesc.enumTypeList,
                ctx.fileDesc.messageTypeList,
                packageName,
                null,
                mutableSetOf()
            ),
            extensions = ctx.fileDesc.extensionList.map {
                numberedFieldFromProto(
                    ctx,
                    it,
                    mutableSetOf()
                )
            }
        )
    }

    protected fun typesFromProto(
        ctx: Context,
        enumTypes: List<DescriptorProtos.EnumDescriptorProto>,
        msgTypes: List<DescriptorProtos.DescriptorProto>,
        parentFullName: String?,
        parentKotlinFullName: String?,
        usedTypeNames: MutableSet<String>
    ) = enumTypes.map { fromProto(ctx, it, parentFullName, parentKotlinFullName, usedTypeNames) } +
            msgTypes.map { fromProto(ctx, it, parentFullName, parentKotlinFullName, usedTypeNames) }

    protected fun fromProto(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        enumDesc: DescriptorProtos.EnumDescriptorProto,
        parentFullName: String?,
        parentKotlinFullName: String?,
        usedTypeNames: MutableSet<String>,
    ): File.Type.Enum {
        val kotlinTypeName = namer.newTypeName(enumDesc.name!!, usedTypeNames).also {
            usedTypeNames += it
        }

        return File.Type.Enum(
            name = enumDesc.name!!,
            fullName = parentFullName?.let { "$it." }.orEmpty() + enumDesc.name!!,
            values = enumDesc.valueList.fold(listOf()) { values, value ->
                values + File.Type.Enum.Value(
                    number = value.number!!,
                    name = value.name!!,
                    kotlinValueTypeName = namer.newEnumValueTypeName(
                        enumDesc.name!!,
                        value.name!!,
                        values.map { it.kotlinValueTypeName })
                )
            },
            kotlinTypeName = kotlinTypeName,
            kotlinFullTypeName = parentKotlinFullName?.let { "${it}." }.orEmpty() + kotlinTypeName,
        )
    }

    protected fun fromProto(
        ctx: Context,
        msgDesc: DescriptorProtos.DescriptorProto,
        parentFullName: String?,
        parentKotlinFullName: String?,
        usedTypeNames: MutableSet<String>,
    ): File.Type.Message {
        val fullName = parentFullName?.let { "$it." }.orEmpty() + msgDesc.name!!
        val kotlinTypeName = namer.newTypeName(msgDesc.name!!, usedTypeNames).also {
            usedTypeNames += it
        }
        val kotlinFullTypeName = parentKotlinFullName?.let { "${it}." }.orEmpty() + kotlinTypeName

        val usedNestedTypeNames = mutableSetOf<String>()
        return File.Type.Message(
            name = msgDesc.name!!,
            fullName = fullName,
            fields = fieldsFromProto(ctx, msgDesc, usedNestedTypeNames),
            nestedTypes = typesFromProto(
                ctx,
                msgDesc.enumTypeList,
                msgDesc.nestedTypeList,
                fullName,
                kotlinFullTypeName,
                usedNestedTypeNames
            ),
            mapEntry = supportMaps && msgDesc.options?.mapEntry == true,
            kotlinTypeName = kotlinTypeName,
            kotlinFullTypeName = kotlinFullTypeName,
            extensionRange = msgDesc.extensionRangeList
        )
    }

    protected fun fieldsFromProto(
        ctx: Context,
        msgDesc: DescriptorProtos.DescriptorProto,
        usedTypeNames: MutableSet<String>
    ): List<File.Field> {
        val usedFieldNames = mutableSetOf<String>()
        return msgDesc.fieldList
            // Exclude any group fields
            .filterNot { it.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_GROUP }
            // Handle fields that are part of a oneof specially
            .partition { !it.hasOneofIndex() }
            .let { (standardFields, oneofFields) ->
                standardFields.map {
                    numberedFieldFromProto(ctx, it, usedFieldNames)
                } + oneofFields.groupBy { it.oneofIndex }
                    .mapNotNull { (oneofIndex, fields) ->
                        // "Every proto3 optional field is placed into a one-field oneof.
                        // We call this a "synthetic" oneof, as it was not present in the source .proto file."
                        // https://github.com/protocolbuffers/protobuf/blob/master/docs/implementing_proto3_presence.md#background
                        val synthetic = fields.size == 1 && (fields[0].proto3Optional ?: false)
                        if (synthetic) {
                            numberedFieldFromProto(ctx, fields[0], usedFieldNames)
                        } else {
                            msgDesc.oneofDeclList[oneofIndex]?.name?.let { oneofName ->
                                    oneofFieldFromProto(
                                        ctx,
                                        oneofName,
                                        fields,
                                        usedFieldNames,
                                        usedTypeNames
                                    )
                            }
                        }
                    }
            }
    }

    protected fun oneofFieldFromProto(
        ctx: Context,
        oneofName: String,
        oneofFields: List<DescriptorProtos.FieldDescriptorProto>,
        usedFieldNames: MutableSet<String>,
        usedTypeNames: MutableSet<String>
    ): File.Field.OneOf {
        val fields = oneofFields.map {
            // wrapper fields are not supposed to be used inside of oneof's
            numberedFieldFromProto(
                ctx,
                it,
                mutableSetOf(),
                oneofField = true
            ) as File.Field.Numbered.Standard
        }
        return File.Field.OneOf(
            name = oneofName,
            fields = fields,
            kotlinFieldTypeNames = fields.fold(mapOf()) { typeNames, field ->
                typeNames + (field.name to namer.newTypeName(field.name, typeNames.values))
            },
            kotlinFieldName = namer.newFieldName(oneofName, usedFieldNames).also {
                usedFieldNames += it
            },
            kotlinTypeName = namer.newTypeName(oneofName, usedTypeNames).also {
                usedTypeNames += it
            }
        )
    }

    protected fun numberedFieldFromProto(
        ctx: Context,
        fieldDesc: DescriptorProtos.FieldDescriptorProto,
        usedFieldNames: MutableSet<String>,
        oneofField: Boolean = false
    ): File.Field.Numbered {
        val type = fromProto(fieldDesc.type ?: error("Missing field type"))
        val wrappedType = fieldDesc.typeName
            ?.takeIf { type == File.Field.Type.MESSAGE }
            ?.let { File.Field.Type.WRAPPER_TYPE_NAME_TO_TYPE[it] }

        return if (wrappedType != null) {
            File.Field.Numbered.Wrapper(
                number = fieldDesc.number!!,
                name = fieldDesc.name!!,
                kotlinFieldName = namer.newFieldName(fieldDesc.name!!, usedFieldNames).also {
                    usedFieldNames += it
                },
                repeated = fieldDesc.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED,
                jsonName = fieldDesc.jsonName,
                wrappedType = wrappedType,
                options = fieldDesc.options ?: DescriptorProtos.FieldOptions.getDefaultInstance(),
                extendee = fieldDesc.extendee
            )
        } else {
            File.Field.Numbered.Standard(
                number = fieldDesc.number!!,
                name = fieldDesc.name!!,
                type = type,
                localTypeName = fieldDesc.typeName,
                repeated = fieldDesc.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED,
                jsonName = fieldDesc.jsonName,
                hasPresence = (fieldDesc.label != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) &&
                        (ctx.fileDesc.usesProto2Syntax ||
                                oneofField ||
                                (fieldDesc.proto3Optional ?: false) ||
                                (type == File.Field.Type.MESSAGE)),
                required = fieldDesc.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED,
                packed = !type.neverPacked && (fieldDesc.options?.packed
                    ?: (ctx.fileDesc.syntax == "proto3")),
                map = supportMaps &&
                        fieldDesc.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED &&
                        fieldDesc.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE &&
                        ctx.findLocalMessage(fieldDesc.typeName!!)?.options?.mapEntry == true,
                kotlinFieldName = namer.newFieldName(fieldDesc.name!!, usedFieldNames).also {
                    usedFieldNames += it
                },
                kotlinLocalTypeName = fieldDesc.typeName?.takeUnless { it.startsWith('.') }?.let {
                    namer.newTypeName(it, emptySet())
                },
                options = fieldDesc.options ?: DescriptorProtos.FieldOptions.getDefaultInstance(),
                extendee = fieldDesc.extendee
            )
        }
    }

    protected fun fromProto(type: DescriptorProtos.FieldDescriptorProto.Type) = when (type) {
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL -> File.Field.Type.BOOL
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES -> File.Field.Type.BYTES
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE -> File.Field.Type.DOUBLE
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM -> File.Field.Type.ENUM
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32 -> File.Field.Type.FIXED32
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64 -> File.Field.Type.FIXED64
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT -> File.Field.Type.FLOAT
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_GROUP -> TODO("Group types not supported")
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32 -> File.Field.Type.INT32
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64 -> File.Field.Type.INT64
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> File.Field.Type.MESSAGE
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32 -> File.Field.Type.SFIXED32
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64 -> File.Field.Type.SFIXED64
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32 -> File.Field.Type.SINT32
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64 -> File.Field.Type.SINT64
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING -> File.Field.Type.STRING
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32 -> File.Field.Type.UINT32
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64 -> File.Field.Type.UINT64
        else -> error("Unknown type: $type")
    }

    data class Context(
        val fileDesc: DescriptorProtos.FileDescriptorProto,
        val params: Map<String, String>
    ) {
        // Support option kotlin_package_mapping=from.package1->to.package1;from.package2->to.package2
        // or kotlin_package_mapping="from.*->to.*"
        val packageMappings = params["kotlin_package_mapping"]
            ?.split(";")
            ?.associate { it.substringBefore("->") to it.substringAfter("->", "") }
            ?: emptyMap()


        private fun getPackageName(): String? =
            params["kotlin_package"]
                ?: fileDesc.options?.uninterpretedOptionList?.find {
                    it.nameList.singleOrNull()?.namePart == "kotlin_package"
                }?.stringValue?.toByteArray()?.decodeToString()
                ?: fileDesc.options?.javaPackage?.takeIf { it.isNotEmpty() }
                ?: fileDesc.`package`?.takeIf { it.isNotEmpty() }

        private fun matchPackageNameFromPackageMappings(packageName: String): String? {
            if (packageMappings[fileDesc.`package`] != null) return packageMappings[fileDesc.`package`]

            return packageMappings
                .filterKeys { it.endsWith("*") }
                .firstNotNullOfOrNull { (from, to) ->
                    val prefixToMatch = from.substringBefore("*")
                    if (packageName.startsWith(prefixToMatch)) {
                        if (to.contains("*")) {
                            val prefixToReplaceWith = to.replace("*", "")
                            packageName.replaceFirst(prefixToMatch, prefixToReplaceWith)
                        } else {
                            to
                        }
                    } else {
                        null
                    }
                }
        }

        val kotlinPackageName =
            getPackageName()?.let { matchPackageNameFromPackageMappings(it) ?: it }

        fun findLocalMessage(
            name: String,
            parent: DescriptorProtos.DescriptorProto? = null
        ): DescriptorProtos.DescriptorProto? {
            // Get the set to look in and the type name
            val (lookIn, typeName) =
                if (parent == null) fileDesc.messageTypeList to name.removePrefix(".${fileDesc.`package`}.")
                else parent.nestedTypeList to name
            // Go deeper if there's a dot
            typeName.indexOf('.').let {
                if (it == -1) return lookIn.find { it.name == typeName }
                return findLocalMessage(
                    typeName.substring(it + 1),
                    typeName.substring(0, it).let { parentTypeName ->
                        lookIn.find { it.name == parentTypeName }
                    } ?: return null)
            }
        }
    }

    companion object : FileBuilder()
}

private val DescriptorProtos.FileDescriptorProto.usesProto2Syntax: Boolean get() = syntax == null || syntax == "proto2"

package com.bilibili.proto.cyclone

public fun underscoreToCamelCase(str: String): String {
    var ret = str
    var lastIndex = -1
    while (true) {
        lastIndex = ret.indexOf('_', lastIndex+1).also { if (it == -1) return ret }
        ret = ret.substring(0, lastIndex) + ret.substring(lastIndex + 1).replaceFirstChar { it.titlecase() }
    }
}

public fun splitWordsToSnakeCase(str: String): String =
    str.replace(Regex("(?<=[a-z])([A-Z0-9])"), "_$1").lowercase()

public interface Namer {
    public fun newTypeName(preferred: String, nameSet: Collection<String>): String
    public fun newFieldName(preferred: String, nameSet: Collection<String>): String
    public fun newEnumValueTypeName(enumTypeName: String, preferred: String, nameSet: Collection<String>): String

    public open class Standard : Namer {
        private val disallowedTypeNames = setOf(
            "Boolean", "Companion", "Double", "Float", "Int", "List", "Long", "Map", "String"
        )
        private val disallowedFieldNames = setOf(
            "decodeWith", "descriptor", "emptyList", "encodeWith", "pbandk", "plus", "protoSize", "unknownFields"
        )
        private val disallowedFieldNamePrefixes = setOf(
            "decodeFrom", "encodeTo"
        )
        private val disallowedValueTypeNames = disallowedTypeNames + setOf(
            "UNRECOGNIZED"
        )
        private val kotlinKeywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )

        override fun newTypeName(preferred: String, nameSet: Collection<String>): String {
            var name = underscoreToCamelCase(preferred).replaceFirstChar { it.titlecase() }
            while (nameSet.contains(name) || disallowedTypeNames.contains(name)) name += '_'
            return "K$name"
        }

        override fun newFieldName(preferred: String, nameSet: Collection<String>): String {
            var name = underscoreToCamelCase(preferred).replaceFirstChar { it.lowercase() }
            while (nameSet.contains(name) || disallowedFieldNames.contains(name)) name += '_'
            if (disallowedFieldNamePrefixes.any { name.startsWith(it) } && !name.endsWith('_')) name += '_'
            if (kotlinKeywords.contains(name)) name = "`$name`"
            return name
        }

        override fun newEnumValueTypeName(enumTypeName: String, preferred: String, nameSet: Collection<String>): String {
            val typePrefix = splitWordsToSnakeCase(enumTypeName) + '_'
            var name = splitWordsToSnakeCase(preferred).substringAfter(typePrefix)
            name = name.uppercase()

            while (nameSet.contains(name) ||
                    disallowedValueTypeNames.contains(name) ||
                    enumTypeName == name) {
                name += '_'
            }
            return name
        }

        public companion object : Standard()
    }
}

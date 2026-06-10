package com.upx.builder.editor

/** A programming language upxBuilder understands for editing, highlighting and building. */
enum class Language(
    val displayName: String,
    val extensions: List<String>,
    val keywords: Set<String>,
    val lineComment: String,
) {
    DART(
        displayName = "Dart / Flutter",
        extensions = listOf("dart"),
        keywords = setOf(
            "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class",
            "const", "continue", "covariant", "default", "deferred", "do", "dynamic", "else",
            "enum", "export", "extends", "extension", "external", "factory", "false", "final",
            "finally", "for", "Function", "get", "hide", "if", "implements", "import", "in",
            "is", "late", "library", "mixin", "new", "null", "on", "operator", "part", "required",
            "rethrow", "return", "set", "show", "static", "super", "switch", "sync", "this",
            "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield",
        ),
        lineComment = "//",
    ),
    CPP(
        displayName = "C++",
        extensions = listOf("cpp", "cc", "cxx", "h", "hpp", "hh", "c"),
        keywords = setOf(
            "alignas", "alignof", "auto", "bool", "break", "case", "catch", "char", "class",
            "const", "constexpr", "continue", "decltype", "default", "delete", "do", "double",
            "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend",
            "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept",
            "nullptr", "operator", "private", "protected", "public", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "template", "this", "throw", "true", "try",
            "typedef", "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "while",
        ),
        lineComment = "//",
    ),
    JAVA(
        displayName = "Java",
        extensions = listOf("java"),
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "true", "false", "null", "try", "void", "volatile", "while", "var", "record", "sealed",
        ),
        lineComment = "//",
    ),
    KOTLIN(
        displayName = "Kotlin",
        extensions = listOf("kt", "kts"),
        keywords = setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
            "const", "constructor", "continue", "crossinline", "data", "do", "dynamic", "else",
            "enum", "expect", "external", "false", "final", "finally", "for", "fun", "get", "if",
            "import", "in", "infix", "init", "inline", "inner", "interface", "internal", "is",
            "lateinit", "noinline", "null", "object", "open", "operator", "out", "override",
            "package", "private", "protected", "public", "reified", "return", "sealed", "set",
            "super", "suspend", "tailrec", "this", "throw", "true", "try", "typealias", "val",
            "var", "vararg", "when", "where", "while",
        ),
        lineComment = "//",
    ),
    PYTHON(
        displayName = "Python",
        extensions = listOf("py", "pyw"),
        keywords = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
            "global", "if", "import", "in", "is", "lambda", "match", "case", "nonlocal", "not",
            "or", "pass", "raise", "return", "self", "try", "while", "with", "yield",
        ),
        lineComment = "#",
    ),
    JAVASCRIPT(
        displayName = "JavaScript / TS",
        extensions = listOf("js", "jsx", "ts", "tsx", "mjs", "cjs"),
        keywords = setOf(
            "abstract", "any", "as", "async", "await", "boolean", "break", "case", "catch",
            "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum",
            "export", "extends", "false", "finally", "for", "from", "function", "get", "if",
            "implements", "import", "in", "instanceof", "interface", "let", "new", "null", "number",
            "of", "package", "private", "protected", "public", "readonly", "return", "set",
            "static", "string", "super", "switch", "this", "throw", "true", "try", "type",
            "typeof", "undefined", "var", "void", "while", "with", "yield",
        ),
        lineComment = "//",
    ),
    GO(
        displayName = "Go",
        extensions = listOf("go"),
        keywords = setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "false", "for", "func", "go", "goto", "if", "import", "interface",
            "iota", "map", "nil", "package", "range", "return", "select", "struct", "switch",
            "true", "type", "var", "append", "cap", "close", "copy", "delete", "len", "make",
            "new", "panic", "print", "println", "recover",
        ),
        lineComment = "//",
    ),
    PLAIN(
        displayName = "Plain Text",
        extensions = listOf("txt", "md", "yaml", "yml", "json", "gradle", "xml", "html", "css"),
        keywords = emptySet(),
        lineComment = "#",
    );

    companion object {
        fun fromFileName(name: String): Language {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: PLAIN
        }
    }
}

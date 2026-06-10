package com.upx.builder.project

import java.io.File

/** An open project rooted at a directory on disk. */
data class Project(val name: String, val root: File)

/** A node in the project file tree. */
data class FileNode(
    val file: File,
    val isDirectory: Boolean,
) {
    val name: String get() = file.name

    fun children(): List<FileNode> =
        if (isDirectory) {
            file.listFiles()
                ?.filterNot { it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { FileNode(it, it.isDirectory) }
                ?: emptyList()
        } else emptyList()

    companion object {
        fun of(file: File) = FileNode(file, file.isDirectory)
    }
}

/** A file currently open in an editor tab. */
data class OpenFile(
    val file: File,
    val content: String,
    val dirty: Boolean = false,
) {
    val name: String get() = file.name
}

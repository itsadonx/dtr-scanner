package com.dtr.scanner.data

import android.content.Context
import java.io.File

object FaceTemplateStore {

    private const val ROOT_DIR = "face_templates"
    private const val PATH_SEPARATOR = "||"

    fun saveTemplateJpegs(context: Context, profileId: Long, jpegs: List<ByteArray>): String {
        if (jpegs.isEmpty()) return ""
        val dir = profileDir(context, profileId)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val paths = ArrayList<String>(jpegs.size)
        jpegs.forEachIndexed { index, bytes ->
            val file = File(dir, "$index.jpg")
            file.writeBytes(bytes)
            paths.add(relativePath(profileId, index))
        }
        return encodePaths(paths)
    }

    fun decodePaths(stored: String): List<String> {
        if (stored.isBlank()) return emptyList()
        return stored.split(PATH_SEPARATOR).filter { it.isNotBlank() }
    }

    /** Resolves stored relative paths and legacy absolute paths. */
    fun resolveImageFile(context: Context, storedPath: String): File? {
        if (storedPath.isBlank()) return null
        val direct = File(storedPath)
        if (direct.isAbsolute && direct.exists()) return direct
        val relative = File(context.filesDir, storedPath)
        if (relative.exists()) return relative
        return null
    }

    private fun relativePath(profileId: Long, index: Int): String {
        return "$ROOT_DIR/$profileId/$index.jpg"
    }

    fun deleteProfileImages(context: Context, profileId: Long) {
        profileDir(context, profileId).deleteRecursively()
    }

    private fun profileDir(context: Context, profileId: Long): File {
        return File(context.filesDir, "$ROOT_DIR/$profileId")
    }

    private fun encodePaths(paths: List<String>): String = paths.joinToString(PATH_SEPARATOR)
}

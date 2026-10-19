package com.dtr.scanner.data

import android.content.Context
import com.dtr.scanner.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FaceDataBackup {
    const val FORMAT_VERSION = 1
    const val MIME_TYPE = "application/zip"
    const val ENTRY_MANIFEST = "backup.json"

    data class ImportSummary(
        val faceProfilesImported: Int,
        val scanRecordsImported: Int,
    )

    fun export(
        context: Context,
        output: OutputStream,
        profiles: List<FaceProfile>,
        scanRecords: List<ScanRecord>,
    ) {
        val profileEntries = JSONArray()
        ZipOutputStream(output).use { zip ->
            profiles.forEachIndexed { profileIndex, profile ->
                val templateFiles = JSONArray()
                val storedPaths = FaceTemplateStore.decodePaths(profile.templateImagePaths)
                storedPaths.forEachIndexed { templateIndex, storedPath ->
                    val file = FaceTemplateStore.resolveImageFile(context, storedPath) ?: return@forEachIndexed
                    val zipPath = faceImageZipPath(profileIndex, templateIndex)
                    templateFiles.put(zipPath)
                    zip.putNextEntry(ZipEntry(zipPath))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
                profileEntries.put(
                    JSONObject()
                        .put("fullName", profile.fullName)
                        .put("area", profile.area)
                        .put("businessUnit", profile.businessUnit)
                        .put("signature", profile.signature)
                        .put("enrolledAtEpochMs", profile.enrolledAtEpochMs)
                        .put("templateFiles", templateFiles),
                )
            }

            val scanEntries = JSONArray()
            scanRecords.forEach { record ->
                scanEntries.put(
                    JSONObject()
                        .put("content", record.content)
                        .put("scannedAtEpochMs", record.scannedAtEpochMs)
                        .put("scanType", record.scanType),
                )
            }

            val manifest = JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("exportedAtEpochMs", System.currentTimeMillis())
                .put("appVersionName", BuildConfig.VERSION_NAME)
                .put("faceProfiles", profileEntries)
                .put("scanRecords", scanEntries)

            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    suspend fun import(context: Context, input: InputStream): ImportSummary {
        val zipBytes = input.readBytes()
        val entries = readZipEntries(zipBytes)
        val manifestBytes = entries[ENTRY_MANIFEST]
            ?: throw IllegalArgumentException("Backup file is missing $ENTRY_MANIFEST.")
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        val formatVersion = manifest.getInt("formatVersion")
        if (formatVersion != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup format version: $formatVersion")
        }

        val profileArray = manifest.getJSONArray("faceProfiles")
        val scanArray = manifest.optJSONArray("scanRecords") ?: JSONArray()

        val parsedProfiles = buildList {
            for (i in 0 until profileArray.length()) {
                val item = profileArray.getJSONObject(i)
                val templateFiles = buildList {
                    val files = item.optJSONArray("templateFiles") ?: JSONArray()
                    for (j in 0 until files.length()) {
                        add(files.getString(j))
                    }
                }
                add(
                    ParsedProfile(
                        fullName = item.getString("fullName"),
                        area = item.optString("area", ""),
                        businessUnit = item.optString("businessUnit", ""),
                        signature = item.getString("signature"),
                        enrolledAtEpochMs = item.getLong("enrolledAtEpochMs"),
                        templateFiles = templateFiles,
                    ),
                )
            }
        }

        val parsedScans = buildList {
            for (i in 0 until scanArray.length()) {
                val item = scanArray.getJSONObject(i)
                add(
                    ScanRecord(
                        content = item.getString("content"),
                        scannedAtEpochMs = item.getLong("scannedAtEpochMs"),
                        scanType = item.optString("scanType", ScanType.QR),
                    ),
                )
            }
        }

        return restore(context, parsedProfiles, parsedScans, entries)
    }

    private suspend fun restore(
        context: Context,
        profiles: List<ParsedProfile>,
        scans: List<ScanRecord>,
        zipEntries: Map<String, ByteArray>,
    ): ImportSummary {
        val database = AppDatabase.get(context)
        val faceDao = database.faceProfileDao()
        val scanDao = database.scanDao()

        faceDao.listAllIds().forEach { id ->
            FaceTemplateStore.deleteProfileImages(context, id)
        }
        faceDao.deleteAll()
        scanDao.deleteAll()

        profiles.forEach { parsed ->
            val profile = FaceProfile(
                fullName = parsed.fullName,
                area = parsed.area,
                businessUnit = parsed.businessUnit,
                signature = parsed.signature,
                enrolledAtEpochMs = parsed.enrolledAtEpochMs,
            )
            val newId = faceDao.insert(profile)
            val jpegs = parsed.templateFiles.mapNotNull { zipPath ->
                zipEntries[zipPath]
            }
            if (jpegs.isNotEmpty()) {
                val paths = FaceTemplateStore.saveTemplateJpegs(context, newId, jpegs)
                if (paths.isNotEmpty()) {
                    faceDao.updateTemplatePaths(newId, paths)
                }
            }
        }

        scans.forEach { scanDao.insert(it) }

        return ImportSummary(
            faceProfilesImported = profiles.size,
            scanRecordsImported = scans.size,
        )
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun faceImageZipPath(profileIndex: Int, templateIndex: Int): String {
        return "face/$profileIndex/$templateIndex.jpg"
    }

    private data class ParsedProfile(
        val fullName: String,
        val area: String,
        val businessUnit: String,
        val signature: String,
        val enrolledAtEpochMs: Long,
        val templateFiles: List<String>,
    )
}

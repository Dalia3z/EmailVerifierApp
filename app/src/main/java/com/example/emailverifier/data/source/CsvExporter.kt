package com.example.emailverifier.data.source

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports verification results as CSV files into the public Downloads folder.
 *
 * - API 29+ : MediaStore.Downloads with RELATIVE_PATH (scoped storage - no permission).
 * - API < 29 : direct File write into Environment.getExternalStoragePublicDirectory
 *              (requires WRITE_EXTERNAL_STORAGE, requested at runtime in the UI).
 */
object CsvExporter {

    private const val EXPORT_DIR = "EmailVerifier"

    /** Writes a CSV containing only the VALID addresses. Returns the user-visible path. */
    fun exportValid(context: Context, emails: List<String>): String {
        val fileName = "valid_emails_${timestamp()}.csv"
        return writeCsv(context, fileName) { writer ->
            writer.writeNext(arrayOf("email", "status"))
            emails.forEach { writer.writeNext(arrayOf(it, "VALID")) }
        }
    }

    /** Writes a CSV containing INVALID + FAILED addresses with their reason. */
    fun exportInvalid(context: Context, rows: List<Pair<String, String>>): String {
        val fileName = "invalid_emails_${timestamp()}.csv"
        return writeCsv(context, fileName) { writer ->
            writer.writeNext(arrayOf("email", "reason"))
            rows.forEach { writer.writeNext(arrayOf(it.first, it.second)) }
        }
    }

    internal fun writeCsv(
        context: Context,
        fileName: String,
        block: (CSVWriter) -> Unit,
    ): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, fileName, block)
        } else {
            writeViaLegacyFile(context, fileName, block)
        }

    // ---- API 29+ : MediaStore / scoped storage --------------------------------
    private fun writeViaMediaStore(
        context: Context,
        fileName: String,
        block: (CSVWriter) -> Unit,
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIR")
            put(MediaStore.Downloads.IS_PENDING, 1) // invisible until fully written
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create $fileName in Downloads")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                CSVWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use(block)
            } ?: throw IllegalStateException("Could not open output stream for $fileName")

            // Publish the file so it appears in the Downloads app.
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            return "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIR/$fileName"
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    // ---- API < 29 : legacy external storage -----------------------------------
    private fun writeViaLegacyFile(
        context: Context,
        fileName: String,
        block: (CSVWriter) -> Unit,
    ): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, EXPORT_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Could not create folder: ${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { stream ->
            CSVWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use(block)
        }
        return file.absolutePath
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Shared file-name time suffix (used by [PhoneCsvExporter]). */
    fun timestampSuffix(): String = timestamp()
}

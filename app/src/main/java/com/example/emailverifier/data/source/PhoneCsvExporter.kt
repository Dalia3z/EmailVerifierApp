package com.example.emailverifier.data.source

import android.content.Context

/**
 * Exports phone validation results as CSV files into the public Downloads folder.
 *
 * Reuses the shared [CsvExporter.writeCsv] MediaStore/legacy logic.
 */
object PhoneCsvExporter {

    /**
     * Writes a CSV of VALID numbers.
     *
     * @param rows (rawNumber, formattedE164, numberType)
     * @return the user-visible path of the created file.
     */
    fun exportValid(context: Context, rows: List<Triple<String, String, String>>): String {
        val fileName = "valid_phones_${CsvExporter.timestampSuffix()}.csv"
        return CsvExporter.writeCsv(context, fileName) { writer ->
            writer.writeNext(arrayOf("raw_number", "e164", "type"))
            rows.forEach { writer.writeNext(arrayOf(it.first, it.second, it.third)) }
        }
    }

    /** Writes a CSV of INVALID + FAILED numbers with their reasons. */
    fun exportInvalid(context: Context, rows: List<Pair<String, String>>): String {
        val fileName = "invalid_phones_${CsvExporter.timestampSuffix()}.csv"
        return CsvExporter.writeCsv(context, fileName) { writer ->
            writer.writeNext(arrayOf("raw_number", "reason"))
            rows.forEach { writer.writeNext(arrayOf(it.first, it.second)) }
        }
    }
}

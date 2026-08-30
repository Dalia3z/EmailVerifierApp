package com.example.emailverifier.data.source

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Reads a CSV or TXT stream (OpenCSV) and returns unique, trimmed, lower-cased
 * email addresses.
 *
 * - CSV: every cell of every row is treated as a candidate; a header row is skipped.
 * - TXT: one address per line works too (OpenCSV returns one column per line).
 * - Cells containing several addresses separated by ';' / ' ' / TAB are split.
 * - Blank lines, '#' comments and the header row are ignored.
 * - Duplicates are removed while keeping the first-occurrence order.
 */
object CsvParser {

    private val headerWords = setOf(
        "email", "emails", "mail", "address", "emailaddress", "name", "list",
    )

    fun parseEmails(input: InputStream): List<String> {
        val emails = LinkedHashSet<String>()

        val parser = CSVParserBuilder().withIgnoreQuotations(false).build()
        val reader = CSVReaderBuilder(InputStreamReader(input, Charsets.UTF_8))
            .withCSVParser(parser)
            .build()

        reader.use { csv ->
            var row: Array<String>? = csv.readNext()
            while (row != null) {
                for (cell in row) {
                    // Some files pack several addresses into one cell
                    // (comma is already handled by OpenCSV's parser).
                    for (token in cell.split(';', ' ', '\t')) {
                        val candidate = token.trim().removeSurrounding("\"").trim()
                        if (candidate.isEmpty()) continue
                        if (candidate.startsWith("#")) continue
                        if (candidate.lowercase(Locale.ROOT) in headerWords) continue
                        emails.add(candidate.lowercase(Locale.ROOT))
                    }
                }
                row = csv.readNext()
            }
        }

        return emails.toList()
    }
}

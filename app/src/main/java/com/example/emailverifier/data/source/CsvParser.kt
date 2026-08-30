package com.example.emailverifier.data.source

import com.example.emailverifier.domain.model.EmailFormat
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/** Result of parsing an input file: valid addresses + how many lines were rejected. */
data class ParsedEmails(
    val emails: List<String>,
    val skipped: Int,
)

/**
 * Reads a CSV or TXT stream (OpenCSV) and returns unique, trimmed, lower-cased,
 * strictly valid email addresses.
 *
 * - CSV: every cell of every row is treated as a candidate; a header row is skipped.
 * - TXT: one address per line works too (OpenCSV returns one column per line).
 * - Cells containing several addresses separated by ';' / ' ' / TAB are split.
 * - Blank lines, '#' comments and the header row are ignored.
 * - Candidates that do not match [EmailFormat] (non-ASCII, spaces, malformed...)
 *   are skipped immediately and counted, so they can never reach the verifier
 *   (which would crash on java.net.IDN.toASCII).
 * - Duplicates are removed while keeping the first-occurrence order.
 */
object CsvParser {

    private val headerWords = setOf(
        "email", "emails", "mail", "address", "emailaddress", "name", "list",
    )

    fun parseEmails(input: InputStream): ParsedEmails {
        val emails = LinkedHashSet<String>()
        var skipped = 0

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

                        val normalized = candidate.lowercase(Locale.ROOT)
                        // Strict format filter: reject non-ASCII / malformed lines NOW,
                        // so IDN.toASCII() inside the library never sees them.
                        if (EmailFormat.isValid(normalized)) {
                            emails.add(normalized)
                        } else {
                            skipped++
                        }
                    }
                }
                row = csv.readNext()
            }
        }

        return ParsedEmails(emails = emails.toList(), skipped = skipped)
    }
}


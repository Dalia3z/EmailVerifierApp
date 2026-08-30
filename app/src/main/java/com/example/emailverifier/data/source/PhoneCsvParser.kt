package com.example.emailverifier.data.source

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.InputStreamReader

/** Result of parsing a phone file: raw numbers + how many lines were rejected. */
data class ParsedPhones(
    val numbers: List<String>,
    val skipped: Int,
)

/**
 * Reads a CSV or TXT stream (OpenCSV) and returns unique raw phone numbers.
 *
 * - Numbers are CLEANED (spaces/dashes/parentheses/dots/slashes stripped, "+" kept)
 *   but NOT validated here - that happens in PhoneValidationService.
 * - Header words, blank lines and '#' comments are skipped.
 * - Cells containing several numbers separated by ';' are split.
 * - Duplicates are removed while keeping the first-occurrence order.
 *
 * Note: unlike emails, spaces are treated as part of the number ("06 12 34 56 78"
 * is one number), so cells are NOT split on spaces.
 */
object PhoneCsvParser {

    private val headerWords = setOf(
        "phone", "phones", "number", "numbers", "telephone", "mobile", "tel",
        "name", "list", "country", "region", "e164",
    )

    fun parsePhones(input: InputStream): ParsedPhones {
        val numbers = LinkedHashSet<String>()
        var skipped = 0

        val parser = CSVParserBuilder().withIgnoreQuotations(false).build()
        val reader = CSVReaderBuilder(InputStreamReader(input, Charsets.UTF_8))
            .withCSVParser(parser)
            .build()

        reader.use { csv ->
            var row: Array<String>? = csv.readNext()
            while (row != null) {
                for (cell in row) {
                    for (fragment in cell.split(';')) {
                        val raw = fragment.trim().removeSurrounding("\"").trim()
                        if (raw.isEmpty()) continue
                        if (raw.startsWith("#")) continue
                        if (raw.lowercase() in headerWords) continue

                        val cleaned = clean(raw)
                        if (cleaned.any { it.isDigit() }) {
                            numbers.add(cleaned)
                        } else {
                            skipped++
                        }
                    }
                }
                row = csv.readNext()
            }
        }

        return ParsedPhones(numbers = numbers.toList(), skipped = skipped)
    }

    /** Removes typical formatting: whitespace, parentheses, dots, dashes and slashes. */
    fun clean(value: String): String = value.replace(Regex("[\\s().\\-/]"), "")
}

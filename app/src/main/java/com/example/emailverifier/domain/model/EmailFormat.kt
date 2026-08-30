package com.example.emailverifier.domain.model

/**
 * Strict email format validator used to filter imported lines BEFORE they reach
 * the verification library.
 *
 * emailverifier-kt internally calls java.net.IDN.toASCII() while parsing the
 * domain, which throws IllegalArgumentException ("Invalid input to toASCII: ...")
 * for characters that cannot be converted (e.g. Arabic/Thai/emoji, spaces,
 * control characters, underscores inside the domain, ...).
 *
 * This pattern only accepts plain-ASCII addresses with a standard format:
 *  - local part: letters, digits and RFC-sane special characters
 *  - domain:     labels separated by dots, TLD with at least 2 letters
 * Anything else is rejected BEFORE it can ever reach IDN.toASCII().
 */
object EmailFormat {

    val PATTERN = Regex(
        """^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$""",
    )

    fun isValid(value: String): Boolean = PATTERN.matches(value)
}

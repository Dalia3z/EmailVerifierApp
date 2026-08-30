package com.example.emailverifier.domain.model

/**
 * The lifecycle status of one email inside the app.
 *
 * The values are stored as strings in the Room database, so the enum names must
 * never change once the app is released.
 */
enum class VerificationStatus {
    /** Not verified yet - waiting in the queue (used to resume after a crash/stop). */
    PENDING,

    /** The email is deliverable (SMTP server accepted it). */
    VALID,

    /** The email is undeliverable / burned / disposable / rejected. */
    INVALID,

    /** The verification itself failed (timeout, IOException, refused connection...). */
    FAILED,
}

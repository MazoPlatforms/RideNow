package com.ridenow.driver.ui.preview

/** Sample data and mock trip settings for preview screens. */
object PreviewConfig {
    const val ENABLED = true
    /** Fill rides, earnings, payouts, tickets and notifications with sample data. Set false for a real build to start empty. */
    const val SAMPLE_DATA = true
    /** Milliseconds after going online before a sample trip request appears. */
    const val MOCK_REQUEST_DELAY_MS = 8_000L
    /** Seconds the driver has to accept a request. */
    const val REQUEST_TIMEOUT_SECONDS = 15
}

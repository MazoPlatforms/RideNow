package com.ridenow.driver.ui.preview

/**
 * Settings for the UI preview, where there is no backend. Set [ENABLED] to false for a real build:
 * the Submitted screen then waits for a decision and requests only arrive from the server.
 */
object PreviewConfig {
    const val ENABLED = true
    /** Seconds on the Application submitted screen before the preview jumps to Home. */
    const val SUBMITTED_TO_HOME_SECONDS = 6
    /** Milliseconds after going online before a sample trip request appears. */
    const val MOCK_REQUEST_DELAY_MS = 8_000L
    /** Seconds the driver has to accept a request. */
    const val REQUEST_TIMEOUT_SECONDS = 15
}

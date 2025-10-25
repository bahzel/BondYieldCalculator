package yield.scraping;

/**
 * Result of a bond data extraction attempt
 */
public enum ExtractionResult {
    /** Data extraction was successful and complete */
    COMPLETE,

    /** Data extraction was successful but incomplete (missing some fields) */
    INCOMPLETE,

    /** HTTP 400 or 404 error - bond not found */
    NOT_FOUND,

    /** Other error (timeout, network error, etc.) */
    ERROR
}


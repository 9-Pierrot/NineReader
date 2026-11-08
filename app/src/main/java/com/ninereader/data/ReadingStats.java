package com.ninereader.data;

/** Reading statistics of a document (RAP-07): time spent, pages read and remaining time estimate. */
public final class ReadingStats {

    /** Fallback reading speed when no history exists yet: 90 s per page. */
    public static final long DEFAULT_MS_PER_PAGE = 90_000L;

    public final long totalTimeMs;
    public final int pagesRead;
    public final int pagesRemaining;

    public ReadingStats(long totalTimeMs, int pagesRead, int pagesRemaining) {
        this.totalTimeMs = Math.max(0, totalTimeMs);
        this.pagesRead = Math.max(0, pagesRead);
        this.pagesRemaining = Math.max(0, pagesRemaining);
    }

    /** Average time per page, using a default when there is not enough history. */
    public long msPerPage() {
        if (pagesRead < 3 || totalTimeMs <= 0) return DEFAULT_MS_PER_PAGE;
        return totalTimeMs / pagesRead;
    }

    /** Estimated time to finish the document. */
    public long estimatedRemainingMs() {
        return msPerPage() * pagesRemaining;
    }

    /** Formats a duration as "1 h 05 min" / "12 min" / "< 1 min". */
    public static String formatDuration(long ms, String hourUnit, String minuteUnit, String lessThanMinute) {
        long minutes = ms / 60_000L;
        if (minutes < 1) return lessThanMinute;
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) return m + " " + minuteUnit;
        return h + " " + hourUnit + " " + (m < 10 ? "0" : "") + m + " " + minuteUnit;
    }
}

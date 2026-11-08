package com.ninereader.reader;

/**
 * Maps pages to "spreads" (items of the page list). In single-page modes a spread is one page; in
 * double-page mode the first page (cover) is alone and the following pages are paired 2-3, 4-5...
 */
public final class SpreadLayout {

    private final int pageCount;
    private final boolean doublePage;
    private final boolean coverAlone;

    public SpreadLayout(int pageCount, boolean doublePage, boolean coverAlone) {
        this.pageCount = Math.max(0, pageCount);
        this.doublePage = doublePage;
        this.coverAlone = coverAlone;
    }

    public boolean isDoublePage() {
        return doublePage;
    }

    public int spreadCount() {
        if (!doublePage) return pageCount;
        if (pageCount == 0) return 0;
        if (coverAlone) return 1 + (pageCount - 1 + 1) / 2;
        return (pageCount + 1) / 2;
    }

    /** First page of a spread. */
    public int firstPage(int spread) {
        if (!doublePage) return spread;
        if (coverAlone) return spread == 0 ? 0 : 1 + (spread - 1) * 2;
        return spread * 2;
    }

    /** Number of pages (1 or 2) in a spread. */
    public int pagesIn(int spread) {
        if (!doublePage) return 1;
        if (coverAlone && spread == 0) return 1;
        int first = firstPage(spread);
        return first + 1 < pageCount ? 2 : 1;
    }

    /** Spread containing a page. */
    public int spreadOf(int page) {
        if (page <= 0) return 0;
        int p = Math.min(page, pageCount - 1);
        if (!doublePage) return p;
        if (coverAlone) return 1 + (p - 1) / 2;
        return p / 2;
    }
}

package dev.wordflow.app;

/** Deterministic offline review intervals; deliberately not FSRS. */
public final class WordReview {
    private WordReview() {}
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    public static int rememberedCount(int previous, boolean remembered) {
        return remembered ? Math.max(0, previous) + 1 : 0;
    }
    public static long nextDue(long nowMs, int previousKnown, boolean remembered) {
        if (!remembered) return nowMs + 10 * MINUTE;
        int level = rememberedCount(previousKnown, true);
        int days = level == 1 ? 1 : level == 2 ? 3 : level == 3 ? 7 : level == 4 ? 14 : 30;
        return nowMs + days * DAY;
    }
}

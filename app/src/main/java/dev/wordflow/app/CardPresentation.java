package dev.wordflow.app;

/** Pure Java formatting of a vocabulary card for compact Android notifications. */
public final class CardPresentation {
    private CardPresentation() {}

    public static final class Lines {
        public final String title;
        public final String subtitle;
        public Lines(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public static Lines format(String english, String meaning, String mode, boolean revealed) {
        if ("chinese".equals(mode)) return new Lines(meaning, "");
        if ("english".equals(mode)) return new Lines(english, "");
        if ("reveal".equals(mode) && !revealed) {
            return new Lines(english, "先回忆释义，然后查看…");
        }
        return new Lines(english, meaning);
    }
}

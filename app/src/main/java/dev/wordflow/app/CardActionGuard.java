package dev.wordflow.app;

/** Pure-Java guard against stale and repeated notification actions. */
public final class CardActionGuard {
    private CardActionGuard() {}

    /**
     * A UI-only NEXT may rotate the active session without referring to a card.
     * Notification actions must carry a token from the card the user actually saw.
     */
    public static boolean accepts(boolean isNext, boolean fromUi,
                                  String activeCardToken, String incomingToken) {
        if (isNext && fromUi) return true;
        return activeCardToken != null && activeCardToken.equals(incomingToken);
    }
}

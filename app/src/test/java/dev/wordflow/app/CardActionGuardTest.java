package dev.wordflow.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CardActionGuardTest {
    @Test public void staleCardNeverEditsNextCard() {
        assertTrue(CardActionGuard.accepts(false, false, "session-1-card-1", "session-1-card-1"));
        assertFalse(CardActionGuard.accepts(false, false, "session-1-card-2", "session-1-card-1"));
        assertFalse(CardActionGuard.accepts(false, false, "session-2-card-1", "session-1-card-1"));
        assertFalse(CardActionGuard.accepts(false, false, "session-2-card-1", null));
    }
    @Test public void onlyUiNextCanOperateWithoutToken() {
        assertTrue(CardActionGuard.accepts(true, true, "session", null));
        assertFalse(CardActionGuard.accepts(false, true, "session", null));
        assertFalse(CardActionGuard.accepts(true, false, "session", null));
    }
}

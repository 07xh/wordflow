package dev.wordflow.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class WordReviewTest {
    @Test public void intervals() {
        long day = 86_400_000L;
        int[] steps = {1, 3, 7, 14, 30, 30};
        for (int i = 0; i < steps.length; i++) {
            assertEquals(steps[i] * day, WordReview.nextDue(0L, i, true));
        }
    }
    @Test public void wrongAnswerSchedulesRetryAndResetsCount() {
        assertEquals(600_000L, WordReview.nextDue(0L, 4, false));
        assertEquals(0, WordReview.rememberedCount(4, false));
    }
}

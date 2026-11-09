import dev.wordflow.app.WordReview;

public class WordReviewCliTest {
    public static void main(String[] args) {
        final long now = 1_700_000_000_000L;
        final long day = 86_400_000L;
        int[] days = {1, 3, 7, 14, 30, 30};
        for (int i = 0; i < days.length; i++) {
            if (WordReview.nextDue(now, i, true) != now + days[i] * day)
                throw new AssertionError("remembered step " + i);
        }
        if (WordReview.nextDue(now, 3, false) != now + 600_000L)
            throw new AssertionError("retry interval");
        if (WordReview.rememberedCount(3, false) != 0 || WordReview.rememberedCount(3, true) != 4)
            throw new AssertionError("reset/advance count");
        System.out.println("PASS: 8 review schedule checks");
    }
}

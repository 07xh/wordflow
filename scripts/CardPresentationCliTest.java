import dev.wordflow.app.CardPresentation;

public final class CardPresentationCliTest {
    public static void main(String[] args) {
        test("chinese", false, "苹果", "");
        test("english", false, "apple", "");
        test("both", false, "apple", "苹果");
        test("reveal", false, "apple", "先回忆释义，然后查看…");
        test("reveal", true, "apple", "苹果");
        System.out.println("PASS: 5 card presentation checks");
    }
    private static void test(String mode, boolean revealed, String title, String subtitle) {
        CardPresentation.Lines lines = CardPresentation.format("apple", "苹果", mode, revealed);
        if (!title.equals(lines.title) || !subtitle.equals(lines.subtitle)) {
            throw new AssertionError("Presentation mismatch for " + mode + " revealed=" + revealed);
        }
    }
}

import dev.wordflow.app.CardActionGuard;

public class CardActionGuardCliTest {
    private static int count;
    private static void check(boolean value, String why) {
        count++;
        if (!value) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        String current = "session-X-card-2";
        check(CardActionGuard.accepts(false, false, current, current), "known/current");
        check(CardActionGuard.accepts(true, false, current, current), "notification next/current");
        check(!CardActionGuard.accepts(false, false, current, "session-X-card-1"), "old card rejected");
        check(!CardActionGuard.accepts(true, false, current, "session-Y-card-2"), "old session rejected");
        check(!CardActionGuard.accepts(false, false, current, null), "untagged answer rejected");
        check(!CardActionGuard.accepts(true, false, null, null), "stopped notification rejected");
        check(CardActionGuard.accepts(true, true, current, null), "user interface next permitted");
        check(!CardActionGuard.accepts(false, true, current, null), "ui cannot forge answer");
        System.out.println("PASS: " + count + " stale-card action checks");
    }
}

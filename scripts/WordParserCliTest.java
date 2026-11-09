import dev.wordflow.app.WordCsv;
import dev.wordflow.app.WordParser;
import java.util.List;

public class WordParserCliTest {
    private static int checks = 0;
    private static void ok(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<WordParser.Item> a = WordParser.parse("\uFEFFapple\t苹果\nword,meaning\n\"feature\",\"特点,功能\"\n");
        ok(a.size() == 2, "line parsing and CSV header");
        ok("特点,功能".equals(a.get(1).meaning), "quoted comma");
        List<WordParser.Item> b = WordParser.parse("Word|词语\nword|新释义\n# ignored\n");
        ok(b.size() == 1 && "新释义".equals(b.get(0).meaning), "deduplication");
        ok(WordParser.parse("not a pair\n").isEmpty(), "ignore malformed");
        ok(WordParser.parse("hi\\t你好").size() == 1, "escaped tab");
        String tricky = "顿悟|理解,包含 \"引号\" 和\n第二行";
        String csv = "english,meaning\r\n" + WordCsv.row("ah,ha", tricky);
        List<WordParser.Item> roundTrip = WordParser.parse(csv);
        ok(roundTrip.size() == 1, "CSV round-trip one entry");
        ok("ah,ha".equals(roundTrip.get(0).english), "quoted English with comma");
        ok(tricky.equals(roundTrip.get(0).meaning), "quoted Chinese with pipe, quote and newline");
        List<WordParser.Item> quoted = WordParser.parse("word,\"some|meaning\"\n");
        ok(quoted.size() == 1 && "some|meaning".equals(quoted.get(0).meaning), "delimiter within CSV quotes");
        List<WordParser.Item> tsv = WordParser.parse("goal\t目标\nheart - 心\nkey = 钥匙\n");
        ok(tsv.size() == 3, "TSV, spaced hyphen and equals separators");
        System.out.println("PASS: " + checks + " parser/export checks");
    }
}

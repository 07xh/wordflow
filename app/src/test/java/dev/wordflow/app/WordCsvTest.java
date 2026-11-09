package dev.wordflow.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class WordCsvTest {
    @Test public void exportAndImportMultilineWithQuotesAndDelimiters() {
        String meaning = "释义|双语, \"引号\"\n换行";
        List<WordParser.Item> words = WordParser.parse("english,meaning\r\n" + WordCsv.row("hi,there", meaning));
        assertEquals(1, words.size());
        assertEquals("hi,there", words.get(0).english);
        assertEquals(meaning, words.get(0).meaning);
    }
}

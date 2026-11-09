package dev.wordflow.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class WordParserTest {
    @Test public void txtTabAndBom() {
        List<WordParser.Item> x = WordParser.parse("\uFEFFapple\t苹果\nbanana\t香蕉\n");
        assertEquals(2, x.size()); assertEquals("apple", x.get(0).english);
        assertEquals("香蕉", x.get(1).meaning);
    }
    @Test public void csvQuotedComma() {
        List<WordParser.Item> x = WordParser.parse("word,meaning\n\"feature\",\"特点,功能\"\n");
        assertEquals(1, x.size()); assertEquals("特点,功能", x.get(0).meaning);
    }
    @Test public void removeDuplicateWithinImport() {
        List<WordParser.Item> x = WordParser.parse("Word|词语\nword|不同释义\n");
        assertEquals(1, x.size()); assertEquals("不同释义", x.get(0).meaning);
    }
    @Test public void skipInvalidLines() {
        assertTrue(WordParser.parse("# comment\nno-separator\n\n").isEmpty());
    }
}

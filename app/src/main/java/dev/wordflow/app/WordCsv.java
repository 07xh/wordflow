package dev.wordflow.app;

/** RFC 4180-style CSV encoder for exporting vocabulary as two columns. */
public final class WordCsv {
    private WordCsv() {}
    public static String row(String english, String meaning) {
        return field(english) + "," + field(meaning) + "\r\n";
    }
    private static String field(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

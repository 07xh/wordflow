package dev.wordflow.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Single-level on-device recovery snapshot, kept in app-private storage. */
public final class RestoreSafetyStore {
    private static final int MAX_BYTES = 25_000_000;
    private final AtomicFile file;

    public RestoreSafetyStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "restore-safety.json"));
    }

    public boolean exists() { return file.getBaseFile().isFile(); }

    public void save(WordBackup.Snapshot snapshot) throws IOException, JSONException {
        byte[] bytes = WordBackup.encode(snapshot).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Safety snapshot too large");
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
        } catch (IOException | RuntimeException e) {
            if (stream != null) file.failWrite(stream);
            throw e;
        }
    }

    public WordBackup.Snapshot load() throws IOException, JSONException {
        try (InputStream stream = file.openRead();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                if (bytes.size() + n > MAX_BYTES) throw new IOException("Safety snapshot too large");
                bytes.write(buffer, 0, n);
            }
            return WordBackup.decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}

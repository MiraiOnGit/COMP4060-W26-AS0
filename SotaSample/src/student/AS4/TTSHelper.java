package student.AS4;

import jp.vstone.RobotLib.CPlayWave;
import jp.vstone.RobotLib.CRobotUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

// ═════════════════════════════════════════════
// TTSHelper.java — Piper TTS + CPlayWave
//
// Generates speech via Piper server on first
// call, caches wav to disk, then plays via
// CPlayWave which is the correct SOTA audio
// path (no mixer lookup needed).
//
// Usage:
//   TTSHelper.speak("Hello!");         // blocking
//   TTSHelper.speakAsync("Keep going"); // non-blocking
// ═════════════════════════════════════════════

public class TTSHelper {

    static final String PIPER_URL = "http://hri.cs.umanitoba.ca:5000";
    static final String CACHE_DIR = "../resources/sound/cache/tts/";

    static { new File(CACHE_DIR).mkdirs(); }

    // Blocking — mutes mic, plays audio, returns when done
    public static void speak(String text) {
        String path = cachedPath(text);
        if (path != null) {
            SpeechHelper.micOff();
            CPlayWave.PlayWave_wait(path);
        }
    }

    // Speaks with pauses at . ? ! for comedic timing
    // Splits into segments and inserts a wait between each
    public static void speakWithPauses(String text) {
        SpeechHelper.micOff();
        // Split on sentence-ending punctuation, keeping the delimiter
        String[] segments = text.split("(?<=[.?!])\\s*");
        for (String seg : segments) {
            seg = seg.trim();
            if (seg.isEmpty()) continue;
            String path = cachedPath(seg);
            if (path != null) {
                CPlayWave.PlayWave_wait(path);
                CRobotUtil.wait(300); // pause between segments
            }
        }
    }

    // Non-blocking — mutes mic, plays audio on background thread
    public static void speakAsync(String text) {
        String path = cachedPath(text);
        if (path == null) return;
        final String p = path;
        Thread t = new Thread(() -> {
            SpeechHelper.micOff();
            CPlayWave.PlayWave_wait(p);
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Cache ─────────────────────────────────

    private static String cachedPath(String text) {
        String filename = CACHE_DIR + Math.abs(text.hashCode()) + ".wav";
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("[TTS] Generating: " + text);
            byte[] wav = generate(text);
            if (wav == null) return null;
            try (FileOutputStream fos = new FileOutputStream(filename)) {
                fos.write(wav);
            } catch (IOException e) {
                System.out.println("[TTS] Cache write failed: " + e.getMessage());
                return null;
            }
        }
        return filename;
    }

    // ── Piper request ─────────────────────────

    private static byte[] generate(String text) {
        try {
            URL url = new URL(PIPER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String json = "{\"text\": \"" + text.replace("\"", "'") + "\","
                    + "\"sample_rate\": 22050,"
                    + "\"length_scale\": 1.0,"
                    + "\"noise_scale\": 0.667,"
                    + "\"noise_w_scale\": 0.8}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] b = new byte[4096]; int n;
                while ((n = in.read(b)) != -1) buf.write(b, 0, n);
            }
            return buf.toByteArray();

        } catch (Exception e) {
            System.out.println("[TTS] Piper failed: " + e.getMessage());
            return null;
        }
    }
}
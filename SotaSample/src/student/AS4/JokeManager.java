package student.AS4;

import jp.vstone.RobotLib.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ═════════════════════════════════════════════
// JokeManager.java
//
// Loads lines from a text file, picks one at
// random, and speaks it via TTSHelper.
//
// Used by DadJokeTask and IntellectTask.
// Lines starting with # are skipped.
// ═════════════════════════════════════════════

public class JokeManager {

    private final List<String> _lines = new ArrayList<>();
    private final Random       _rand  = new Random();

    public JokeManager(String filePath) {
        loadLines(filePath);
        System.out.println("[JOKE] Loaded " + _lines.size() + " lines from " + filePath);
    }

    private void loadLines(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                _lines.add(line);
            }
        } catch (IOException e) {
            System.out.println("[JOKE] Failed to load " + path + ": " + e.getMessage());
        }
    }

    public void speakRandom(CSotaMotion motion) {
        if (_lines.isEmpty()) { System.out.println("[JOKE] No lines loaded."); return; }
        String line = _lines.get(_rand.nextInt(_lines.size()));
        System.out.println("[JOKE] → " + line);
        TTSHelper.speakWithPauses(line);
    }
}

package student.AS4;

import jp.vstone.RobotLib.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ═════════════════════════════════════════════
// PickupLineManager.java
//
// Loads pickup_lines.txt into 5 level buckets.
// Speaks via TTSHelper — no wav files needed.
//
// Level = BB_LOVE_LEVEL (0-4), grows every 5
// visits automatically in SaveStateTask.
//
//   Level 0 (visits  0-4 ): shy / cheesy
//   Level 1 (visits  5-9 ): classic
//   Level 2 (visits 10-14): funny and flirty
//   Level 3 (visits 15-19): bold rizz
//   Level 4 (visits 20+  ): full rizz mode
// ═════════════════════════════════════════════

public class PickupLineManager {

    static final String LINES_FILE = "../resources/pickup_lines.txt";

    private final List<List<String>> _levels = new ArrayList<>();
    private final Random             _rand   = new Random();

    public PickupLineManager() {
        for (int i = 0; i < 5; i++) _levels.add(new ArrayList<>());
        loadLines();
    }

    private void loadLines() {
        int currentLevel = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(LINES_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("# LEVEL")) {
                    try {
                        currentLevel = Integer.parseInt(
                                line.split("LEVEL")[1].trim().split("\\s+")[0]
                                        .replace("—","").replace("–","").trim());
                    } catch (Exception e) { /* keep current */ }
                    continue;
                }
                if (line.startsWith("#")) continue;
                if (currentLevel >= 0 && currentLevel < _levels.size())
                    _levels.get(currentLevel).add(line);
            }
        } catch (IOException e) {
            System.out.println("[PICKUP] Failed to load: " + e.getMessage());
        }
        for (int i = 0; i < _levels.size(); i++)
            System.out.println("[PICKUP] Level " + i + ": " + _levels.get(i).size() + " lines.");
    }

    public int getLevel(float loveLevel) {
        return Math.min((int) loveLevel, 4);
    }

    public void speakRandom(CSotaMotion motion, float loveLevel) {
        int level = getLevel(loveLevel);
        List<String> pool = _levels.get(level);
        if (pool.isEmpty()) { System.out.println("[PICKUP] No lines at level " + level); return; }
        String line = pool.get(_rand.nextInt(pool.size()));
        System.out.println("[PICKUP] Level " + level + " → " + line);
        PickupLineGestureAction.runWithSpeech(motion, level, line);
    }
}
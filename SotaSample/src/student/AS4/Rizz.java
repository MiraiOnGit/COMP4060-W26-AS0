package student.AS4;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import jp.vstone.RobotLib.*;
import pocketsphinx.PocketSphinx;

// ═════════════════════════════════════════════
// PickupLineKiosk.java
//
// Standalone pickup line machine.
// Delivers a pickup line at the current level,
// then asks if you want to level up or stay.
//
// Keywords:
//   "yes" / "again" / "more"  → level up
//   "no"  / "stop"  / "enough" → stay same level
//
// Power button → exit
// ═════════════════════════════════════════════

public class Rizz {

    static final String TAG        = "Rizz";
    static final String SPHINX_ROOT = "/home/root/sotaprograms/resources/sphinxmodel/";
    static final int    MAX_LEVEL   = 4;

    public static void main(String[] args) {
        new Rizz().run();
    }

    void run() {
        CRobotUtil.Log(TAG, "Starting PickupLineKiosk");

        CRobotMem   mem    = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if (!mem.Connect()) { CRobotUtil.Log(TAG, "Connection failed"); return; }
        motion.InitRobot_Sota();
        motion.ServoOn();

        PocketSphinx sphinx = new PocketSphinx();
        long decoderPtr = sphinx.initialize_kws(
                SPHINX_ROOT + "en-us/en-us",
                SPHINX_ROOT + "keyphrases.txt",
                SPHINX_ROOT + "en-us/cmudict-en-us.dict"
        );
        if (decoderPtr == 0) { CRobotUtil.Log(TAG, "Sphinx init failed"); return; }

        PickupLineManager pickup = new PickupLineManager();
        int level = 0;

        // Opening line
        TTSHelper.speak("Hello! I have pickup lines for every occasion. Let's start easy.");
        LoveLED.set(motion, level);

        while (!motion.isButton_Power()) {

            // Deliver pickup line at current level
            System.out.println("[KIOSK] Delivering level " + level + " pickup line.");
            LoveLED.set(motion, level);
            PickupLineGestureAction.poseFirst(motion, level);
            pickup.speakRandom(motion, level);
            PickupLineGestureAction.returnHome(motion);

            // Ask if they want to level up
            CRobotUtil.wait(500);
            if (level < MAX_LEVEL) {
                TTSHelper.speak("Want me to turn it up a notch?");
            } else {
                TTSHelper.speak("That's full rizz mode. Want another one?");
            }

            // Listen for yes/no
            ListenLED.on(motion);
            String heard = SpeechHelper.listenForKeyword(sphinx, decoderPtr,
                     "ok", "more", "stop", "enough");
            ListenLED.off(motion, level);

            System.out.println("[KIOSK] Heard: " + heard);

            if (heard.equals("again") || heard.equals("more") ||  heard.equals("ok")) {
                if (level < MAX_LEVEL) {
                    level++;
                    System.out.println("[KIOSK] Leveling up to: " + level);
                    TTSHelper.speak("Ooh, brave. Let's go.");
                } else {
                    TTSHelper.speak("Already at max. Here's another one.");
                }
            } else if ( heard.equals("stop") || heard.equals("enough")) {
                TTSHelper.speak("Staying at this level. You asked for it.");
            } else {
                // timeout — deliver another at same level
                TTSHelper.speak("I'll take that as a yes.");
            }

            CRobotUtil.wait(300);
        }

        // Power button exit
        TTSHelper.speak("Goodbye. You were a great audience.");
        motion.ServoOff();
        CRobotUtil.Log(TAG, "Done.");
    }
}
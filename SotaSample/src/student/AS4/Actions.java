package student.AS4;

import jp.vstone.RobotLib.*;
import java.awt.Color;

// ═════════════════════════════════════════════
// Actions.java
//
// Every action speaks via TTSHelper (Piper TTS).
// No wav files needed — all dialog is scripted
// text here, generated and cached on first run.
//
// LED colors go from cold to hot as love level
// rises. Level is passed in where it matters.
//
//   Level 0 — ice blue   (shy)
//   Level 1 — cool cyan  (warming up)
//   Level 2 — soft pink  (flirty)
//   Level 3 — magenta    (bold)
//   Level 4 — red        (full rizz)
//
// Gestures:
//   Kiss Right  — 2-step: arm extends right then head turns toward hand
//   Kiss Left   — 2-step: arm extends left  then head turns toward hand
//   Hihi        — 1-step: both arms up, head tilted (used after check-in)
//   Face Palm   — 2-step: arm raises then hand covers face
//   Hahahaha    — 1-step: head thrown back laughing
// ═════════════════════════════════════════════


// ─────────────────────────────────────────────
// NEUTRAL POSE — return to rest after any gesture
// ─────────────────────────────────────────────

class NeutralPose {
    public static void run(CSotaMotion motion) {
        CRobotPose pose = new CRobotPose();
        pose.SetPose(
                new Byte[]  {1, 2, 3, 4, 5, 6, 7, 8},
                new Short[] {0, 0, 0, 0, 0, 0, 0, 0}
        );
        motion.play(pose, 500);
        motion.waitEndinterpAll();
    }
}


// ─────────────────────────────────────────────
// LED COLORS BY LOVE LEVEL — cold to hot
// ─────────────────────────────────────────────

class LoveLED {

    static final Color[] EYE = {
            new Color(100, 180, 255),   // level 0 — ice blue
            new Color(  0, 220, 220),   // level 1 — cool cyan
            new Color(255, 150, 180),   // level 2 — soft pink
            new Color(220,  50, 220),   // level 3 — magenta
            new Color(255,  30,  30),   // level 4 — red
    };

    static final int[] MOUTH = { 50, 100, 150, 200, 255 };

    public static void set(CSotaMotion motion, int level) {
        int l = Math.min(Math.max(level, 0), 4);
        CRobotPose pose = new CRobotPose();
        pose.setLED_Sota(EYE[l], EYE[l], MOUTH[l], EYE[l]);
        motion.play(pose, 300);
        motion.waitEndinterpAll();
    }

    public static void dim(CSotaMotion motion) {
        CRobotPose pose = new CRobotPose();
        pose.setLED_Sota(Color.BLACK, Color.BLACK, 0, Color.BLACK);
        motion.play(pose, 300);
        motion.waitEndinterpAll();
    }
}


// ─────────────────────────────────────────────
// LISTEN LED — green while listening, restores after
// ─────────────────────────────────────────────

class ListenLED {
    static final Color LISTEN_COLOR = new Color(0, 255, 80); // green — not used elsewhere

    public static void on(CSotaMotion motion) {
        CRobotPose pose = new CRobotPose();
        pose.setLED_Sota(LISTEN_COLOR, LISTEN_COLOR, 30, LISTEN_COLOR);
        motion.play(pose, 150);
        motion.waitEndinterpAll();
    }

    public static void off(CSotaMotion motion, int loveLevel) {
        LoveLED.set(motion, loveLevel);
    }
}




class IdleBodyAction {
    public static void run(CSotaMotion motion) {
        LoveLED.dim(motion);
        NeutralPose.run(motion);
    }
}


// ─────────────────────────────────────────────
// WELCOME — greeting scales with love level
//
// Gesture: Wave (arm moves twice)
//   speakAsync so wave plays during greeting.
//   Speech and wave happen simultaneously.
// ─────────────────────────────────────────────

class WelcomeAction {
    static final String[] LINES = {
            "Oh hey! I didn't see you there. Welcome!",
            "Hello again! Nice to see you.",
            "Well hi there. I was just thinking about you.",
            "Oh, it's you. My favorite person.",
            "You came back. I knew you would.",
    };

    public static void run(CSotaMotion motion, int loveLevel) {
        int l = Math.min(loveLevel, LINES.length - 1);
        LoveLED.set(motion, l);
        // Wave pose first
        CRobotPose wave1 = new CRobotPose();
        wave1.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,  7,   8},
                new Short[] {-17, 387, -594, 951, 102,  23, -86,  6}
        );
        CRobotPose wave2 = new CRobotPose();
        wave2.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,  7,   8},
                new Short[] {-18, 376, -139, 951, 102,  23, -85,  7}
        );
        // Start wave, speak concurrently using speakAsync, then wait for both
        motion.play(wave1, 300); motion.waitEndinterpAll();
        TTSHelper.speakAsync(LINES[l]); // start audio while waving
        motion.play(wave2, 300); motion.waitEndinterpAll();
        motion.play(wave1, 300); motion.waitEndinterpAll();
        motion.play(wave2, 300); motion.waitEndinterpAll();
        NeutralPose.run(motion);
        // Block until TTS fully finishes — speakAsync may still be playing
        CRobotUtil.wait(2000); // generous wait for audio to complete
    }
}


// ─────────────────────────────────────────────
// PROMPT — asks which keyword to say
//
// Gesture: Sign (arm extended, presenting)
//   Pose held while speaking, then neutral.
//   Speech first — explanation lands, arm
//   reinforces like a presenter signing off.
// ─────────────────────────────────────────────

class PromptAction {
    static final String[] LINES = {
            "Ask me for some puns or dad jokes.",
            "What'll it be? Puns or dad jokes?",
            "Go ahead. Puns or dad jokes.",
            "You know what to say.",
            "I'm waiting. Puns or dad jokes.",
    };

    public static void run(CSotaMotion motion, int loveLevel) {
        int l = Math.min(loveLevel, LINES.length - 1);
        // Raise to sign pose while speaking
        CRobotPose sign = new CRobotPose();
        sign.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,  7,   8},
                new Short[] {-18, 367, -102, 951, 102,  23, -85,  7}
        );
        motion.play(sign, 400);
        motion.waitEndinterpAll();
        TTSHelper.speak(LINES[l]); // blocking — arm holds during speech
        NeutralPose.run(motion);
    }
}


// ─────────────────────────────────────────────
// CHECK-IN — asks if they want another one
//
// Gesture: Hihi
//   Both arms up + head tilt — playful and eager.
//   Pose AFTER speech so the question lands first,
//   then the body language reinforces it.
// ─────────────────────────────────────────────

class CheckInAction {
    static final String[] LINES = {
            "Want another one?",
            "Should I keep going?",
            "One more?",
            "You want more, don't you.",
            "I could do this all day. Want another?",
    };

    public static void run(CSotaMotion motion, int loveLevel) {
        int l = Math.min(loveLevel, LINES.length - 1);
        // Speak first — ask the question
        TTSHelper.speak(LINES[l]);
        // Then pose — hihi gesture (both arms up, head tilted)
        CRobotPose pose = new CRobotPose();
        pose.SetPose(
                new Byte[]  {1,  2,    3,    4,   5,   6,   7,   8},
                new Short[] {-12, -753, -12, 957,  96, -452, -310,  7}
        );
        motion.play(pose, 400);
        motion.waitEndinterpAll();
        CRobotUtil.wait(600);
        NeutralPose.run(motion);
    }
}


// ─────────────────────────────────────────────
// ANOTHER ONE — bridge line when user says yes
//
// Gesture: Hihi (same eager pose as check-in)
//   Pose AFTER speech — enthusiasm as a reaction.
// ─────────────────────────────────────────────

class AnotherOneAction {
    static final String[] LINES = {
            "Okay, here's another one.",
            "Alright, you asked for it.",
            "Coming right up.",
            "You're really into this, huh.",
            "I had a feeling you'd say yes.",
    };

    public static void run(CSotaMotion motion, int loveLevel) {
        int l = Math.min(loveLevel, LINES.length - 1);
        // Speak first — bridge line
        TTSHelper.speak(LINES[l]);
        // Then hihi pose
        CRobotPose pose = new CRobotPose();
        pose.SetPose(
                new Byte[]  {1,   2,    3,   4,   5,   6,    7,   8},
                new Short[] {-12, -753, -12, 957,  96, -452, -310,  7}
        );
        motion.play(pose, 400);
        motion.waitEndinterpAll();
        CRobotUtil.wait(400);
        NeutralPose.run(motion);
    }
}


// ─────────────────────────────────────────────
// DAD JOKE — hahahaha gesture
//
// Deliver punchline first so the laugh lands
// as a physical reaction to the joke itself.
// Pose AFTER speech.
// ─────────────────────────────────────────────

class DadJokeGestureAction {
    public static void run(CSotaMotion motion) {
        // Speak joke first (called by DadJokeTask before this)
        // Then throw head back laughing
        CRobotPose pose = new CRobotPose();
        pose.SetPose(
                new Byte[]  {1,   2,   3,   4,   5,   6,    7,    8},
                new Short[] {-12, -753, -12, 957,  96, -452, -310,   7}
        );
        motion.play(pose, 350);
        motion.waitEndinterpAll();
        CRobotUtil.wait(800);
        NeutralPose.run(motion);
    }
}


// ─────────────────────────────────────────────
// INTELLECTUAL PUN — face palm gesture
//
// The pun deserves a self-aware groan reaction.
// Step 1: arm raises (anticipation)
// Step 2: hand covers face (the reaction)
// Pose AFTER speech — the facepalm is the review.
// ─────────────────────────────────────────────

class PunGestureAction {
    public static void run(CSotaMotion motion) {
        // Speak pun first (called by IntellectTask before this)
        // Step 1 — arm raises
        CRobotPose step1 = new CRobotPose();
        step1.SetPose(
                new Byte[]  {1,   2,   3,   4,   5,   6,   7,   8},
                new Short[] {-13, 221, -26, 958,  96, 693, 121,  19}
        );
        motion.play(step1, 500);
        motion.waitEndinterpAll();
        // Step 2 — face palm (hand covers face)
        CRobotPose step2 = new CRobotPose();
        step2.SetPose(
                new Byte[]  {1,   2,   3,   4,   5,   6,   7,   8},
                new Short[] {-13, 378, -458, 957,  96, 693, 122,  19}
        );
        motion.play(step2, 400);
        motion.waitEndinterpAll();
        CRobotUtil.wait(700);
        NeutralPose.run(motion);
    }
}


// ─────────────────────────────────────────────
// PICKUP LINE — kiss gesture (right or left)
//
// The pose comes FIRST — physical boldness before
// the words, so the line lands while SOTA is
// already mid-gesture. More impactful that way.
//
// Alternates right/left by love level parity.
//   Even levels → Kiss Right
//   Odd levels  → Kiss Left
// ─────────────────────────────────────────────

class PickupLineGestureAction {

    // Pose into position, speak the line during the hold, then return home
    public static void runWithSpeech(CSotaMotion motion, int loveLevel, String line) {
        if (loveLevel % 2 == 0) {
            kissRight(motion, line);
        } else {
            kissLeft(motion, line);
        }
        CRobotUtil.wait(300);
        NeutralPose.run(motion);
    }

    // Legacy support — pose only (no speech)
    public static void poseFirst(CSotaMotion motion, int loveLevel) {
        if (loveLevel % 2 == 0) {
            kissRightPose(motion);
        } else {
            kissLeftPose(motion);
        }
    }

    public static void returnHome(CSotaMotion motion) {
        CRobotUtil.wait(500);
        NeutralPose.run(motion);
    }

    private static void kissRight(CSotaMotion motion, String line) {
        CRobotPose step1 = new CRobotPose();
        step1.SetPose(
                new Byte[]  {1,   2,    3,   4,    5,   6,   7,    8},
                new Short[] {-21, -989, -82, -127, 932, 168, -128,  14}
        );
        motion.play(step1, 500);
        motion.waitEndinterpAll();
        // Speak during the held pose
        TTSHelper.speakAsync(line);
        CRobotPose step2 = new CRobotPose();
        step2.SetPose(
                new Byte[]  {1,   2,    3,   4,    5,   6,    7,    8},
                new Short[] {-18, -989, -82, -235, 155, -493, -130,   9}
        );
        motion.play(step2, 400);
        motion.waitEndinterpAll();
        // Wait for audio to finish
        CRobotUtil.wait(3000);
    }

    private static void kissLeft(CSotaMotion motion, String line) {
        CRobotPose step1 = new CRobotPose();
        step1.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,   7,   8},
                new Short[] {-12, -41, -885, 954, 101, 198,  77,  19}
        );
        motion.play(step1, 500);
        motion.waitEndinterpAll();
        // Speak during the held pose
        TTSHelper.speakAsync(line);
        CRobotPose step2 = new CRobotPose();
        step2.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,   7,   8},
                new Short[] {-12, 343, -124, 953,  97, 561, -42,  20}
        );
        motion.play(step2, 400);
        motion.waitEndinterpAll();
        // Wait for audio to finish
        CRobotUtil.wait(3000);
    }

    private static void kissRightPose(CSotaMotion motion) {
        CRobotPose step1 = new CRobotPose();
        step1.SetPose(
                new Byte[]  {1,   2,    3,   4,    5,   6,   7,    8},
                new Short[] {-21, -989, -82, -127, 932, 168, -128,  14}
        );
        motion.play(step1, 500);
        motion.waitEndinterpAll();
        CRobotPose step2 = new CRobotPose();
        step2.SetPose(
                new Byte[]  {1,   2,    3,   4,    5,   6,    7,    8},
                new Short[] {-18, -989, -82, -235, 155, -493, -130,   9}
        );
        motion.play(step2, 400);
        motion.waitEndinterpAll();
    }

    private static void kissLeftPose(CSotaMotion motion) {
        CRobotPose step1 = new CRobotPose();
        step1.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,   7,   8},
                new Short[] {-12, -41, -885, 954, 101, 198,  77,  19}
        );
        motion.play(step1, 500);
        motion.waitEndinterpAll();
        CRobotPose step2 = new CRobotPose();
        step2.SetPose(
                new Byte[]  {1,   2,   3,    4,   5,   6,   7,   8},
                new Short[] {-12, 343, -124, 953,  97, 561, -42,  20}
        );
        motion.play(step2, 400);
        motion.waitEndinterpAll();
    }
}


// ─────────────────────────────────────────────
// FAREWELL — goodbye scales with love level
// LED dims to black at the very end
// ─────────────────────────────────────────────

class FarewellAction {
    static final String[] LINES = {
            "Okay, goodbye. Come back sometime.",
            "See you around. I'll be here.",
            "Bye! I had fun. Don't be a stranger.",
            "Leaving already? I'll miss you. A little.",
            "You're leaving? Rude. Come back soon.",
    };

    public static void run(CSotaMotion motion, int loveLevel) {
        int l = Math.min(loveLevel, LINES.length - 1);
        LoveLED.set(motion, l);
        // Speak farewell — then wave (hihi pose repurposed as a wave)
        TTSHelper.speakAsync(LINES[l]);
        CRobotPose pose = new CRobotPose();
        pose.SetPose(
                new Byte[]  {1,   2,    3,   4,   5,   6,    7,    8},
                new Short[] {-12, -753, -12, 957,  96, -452, -310,   7}
        );
        motion.play(pose, 400);
        motion.waitEndinterpAll();
        CRobotUtil.wait(1000);
        NeutralPose.run(motion);
        LoveLED.dim(motion);
    }
}
package student.AS4;

import jp.vstone.RobotLib.*;
import pocketsphinx.PocketSphinx;
import pocketsphinx.RecognitionResult;
import javax.sound.sampled.*;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;

import student.AS2.ServoRangeTool;
import student.AS2.SotaForwardK;
import student.AS2.SotaInverseK;
import student.AS2.SotaInverseK.JType;
import student.AS2.Frames.FrameKeys;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;

// ═════════════════════════════════════════════
// Helpers.java
//
//   IKHelper      — move robot using inverse kinematics
//   GestureHelper — robot servo poses
//   SpeechHelper  — PocketSphinx keyword listen
//   RobotState    — read/write robot_state.json
// ═════════════════════════════════════════════


// ─────────────────────────────────────────────
// IK HELPER
//
// Uses AS2 ServoRangeTool + SotaInverseK to move
// the robot to a target position in 3D space.
//
// Usage:
//   IKHelper ik = new IKHelper(motion);
//
//   // move left hand to x,y,z offset from its home
//   ik.moveHand(FrameKeys.L_HAND, 0.02, 0.0, 0.01, 500);
//
//   // move right hand
//   ik.moveHand(FrameKeys.R_HAND, -0.02, 0.0, 0.01, 500);
//
//   // move head
//   ik.moveHead(0.0, 0.05, 0.0, 500);
//
//   // return to home (the pose IK was initialized from)
//   ik.returnHome(500);
// ─────────────────────────────────────────────

class IKHelper {

    static final int MAX_IK_TRIES = 20;

    private final CSotaMotion  _motion;
    private final ServoRangeTool _ranges;
    private RealVector _homeAngles;     // angles at time of construction
    private double[]   _lHandHome;     // home position of left hand
    private double[]   _rHandHome;     // home position of right hand
    private double[]   _headHome;      // home position of head

    public IKHelper(CSotaMotion motion) {
        _motion = motion;
        _ranges = ServoRangeTool.Load();

        // read current pose as home
        _homeAngles = _ranges.calcAngles(_motion.getReadPose());

        SotaForwardK fk = new SotaForwardK(_homeAngles);
        _lHandHome = MatrixHelp.getTrans(fk.frames.get(FrameKeys.L_HAND)).toArray();
        _rHandHome = MatrixHelp.getTrans(fk.frames.get(FrameKeys.R_HAND)).toArray();
        _headHome  = MatrixHelp.getTrans(fk.frames.get(FrameKeys.HEAD)).toArray();
    }

    // Move left or right hand to an offset from its home position
    // dx, dy, dz are in metres relative to the hand's home position
    public void moveHand(FrameKeys hand, double dx, double dy, double dz, int ms) {
        double[] home = hand == FrameKeys.L_HAND ? _lHandHome : _rHandHome;
        double[] target = { home[0] + dx, home[1] + dy, home[2] + dz };

        RealVector currentAngles = _ranges.calcAngles(_motion.getReadPose());
        RealVector solved = SotaInverseK.solve(
                hand, JType.O,
                MatrixUtils.createRealVector(target),
                currentAngles, MAX_IK_TRIES
        );

        CRobotPose pose = _ranges.calcMotorValues(solved);
        _motion.play(pose, ms);
        _motion.waitEndinterpAll();
    }

    // Move head to an offset from its home position
    public void moveHead(double dx, double dy, double dz, int ms) {
        double[] target = { _headHome[0] + dx, _headHome[1] + dy, _headHome[2] + dz };

        RealVector currentAngles = _ranges.calcAngles(_motion.getReadPose());
        RealVector solved = SotaInverseK.solve(
                FrameKeys.HEAD, JType.O,
                MatrixUtils.createRealVector(target),
                currentAngles, MAX_IK_TRIES
        );

        CRobotPose pose = _ranges.calcMotorValues(solved);
        _motion.play(pose, ms);
        _motion.waitEndinterpAll();
    }

    // Return to the pose IK was initialized from
    public void returnHome(int ms) {
        CRobotPose pose = _ranges.calcMotorValues(_homeAngles);
        _motion.play(pose, ms);
        _motion.waitEndinterpAll();
    }
}

// ═════════════════════════════════════════════
// Helpers.java
//
//   GestureHelper  — robot servo poses
//   SpeechHelper   — PocketSphinx keyword listen
//   RobotState     — read/write robot_state.json
// ═════════════════════════════════════════════


// ─────────────────────────────────────────────
// GESTURE HELPER
// Servo IDs:
//   1 = body yaw      2 = left shoulder
//   3 = left elbow    4 = right shoulder
//   5 = right elbow   6 = head yaw
//   7 = head pitch    8 = head roll
// ─────────────────────────────────────────────

class GestureHelper {

    public static void poseNeutral(CSotaMotion motion) {
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
// SPEECH HELPER
// Wraps PocketSphinx keyword spotting.
// Blocks until a keyword from keyphrases.txt is heard.
//
// keyphrases.txt must contain:
//   alpha /1e-20/
//   z /1e-20/
//   yes /1e-20/
//   no /1e-20/
//   bye /1e-20/
// ─────────────────────────────────────────────

class SpeechHelper {

    static final int SAMPLERATE    = 16000;
    static final int BITRATE       = 16;
    static final int WINDOW_MS     = 256;
    static final int BUFFER_SIZE   = WINDOW_MS * SAMPLERATE * 2 / 1000;
    static final int TIMEOUT_MS    = 60000;
    static final int MIN_SCORE     = 0; // accept all — tune after seeing real scores

    // Shared mic line — kept open between listens so we can stop/start it
    // around TTS playback to prevent speaker bleed.
    private static TargetDataLine _mic = null;

    // Call before any TTS — stops mic so speaker audio is not captured
    public static void micOff() {
        if (_mic != null && _mic.isOpen()) {
            _mic.stop();
            _mic.flush();
            System.out.println("[MIC] Off.");
        }
    }

    // Call after TTS — restarts mic ready for next listen
    public static void micOn() {
        try {
            if (_mic == null || !_mic.isOpen()) {
                AudioFormat fmt = new AudioFormat(SAMPLERATE, BITRATE, 1, true, false);
                _mic = (TargetDataLine) AudioSystem.getLine(
                        new DataLine.Info(TargetDataLine.class, fmt));
                _mic.open(fmt);
            }
            _mic.flush();
            _mic.start();
            System.out.println("[MIC] On.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read from shared mic — used by CheckInRouteTask too
    public static int readMic(byte[] buffer) {
        if (_mic == null || !_mic.isRunning()) return 0;
        return _mic.read(buffer, 0, buffer.length);
    }

    // Listen for a keyword with 30s timeout.
    // allowedWords: only these exact words will be accepted. null = accept any single word.
    // Returns keyword string if heard, "" if timeout.
    // Overload with motion for blinking eyes while listening
    public static String listenForKeyword(CSotaMotion motion, int loveLevel, PocketSphinx sphinx, long decoderPtr, String... allowedWords) {
        micOn();
        CRobotUtil.wait(1000);
        _mic.flush();
        CRobotUtil.wait(200);
        _mic.flush();

        if (motion != null) ListenLED.on(motion);

        sphinx.startListening(decoderPtr);
        byte[] buffer   = new byte[BUFFER_SIZE];
        String heard    = "";
        long   deadline = System.currentTimeMillis() + TIMEOUT_MS;

        while (heard.isEmpty() && System.currentTimeMillis() < deadline) {
            int bytes = _mic.read(buffer, 0, buffer.length);
            sphinx.processAudio(decoderPtr, buffer, bytes);
            RecognitionResult result = sphinx.getRecognitionHypothesis(decoderPtr);
            if (result != null && !result.result.isEmpty()) {
                sphinx.stopListening(decoderPtr);
                sphinx.startListening(decoderPtr);
                String raw = result.result.trim().toLowerCase();
                System.out.println("[SPEECH] Raw: '" + raw + "'  score: " + result.score);
                String[] tokens = raw.split("\\s+");
                for (int i = tokens.length - 1; i >= 0; i--) {
                    String token = tokens[i].trim();
                    if (isAllowed(token, allowedWords)) {
                        heard = token;
                        System.out.println("[SPEECH] Accepted: " + heard);
                        break;
                    }
                }
            }
        }

        if (motion != null) ListenLED.off(motion, loveLevel);
        micOff();
        sphinx.stopListening(decoderPtr);
        if (heard.isEmpty()) System.out.println("[SPEECH] Timeout — no keyword heard.");
        return heard;
    }

    public static String listenForKeyword(PocketSphinx sphinx, long decoderPtr, String... allowedWords) {
        return listenForKeyword(null, 0, sphinx, decoderPtr, allowedWords);
    }

    private static boolean isAllowed(String word, String[] allowedWords) {
        if (allowedWords == null || allowedWords.length == 0) return true;
        for (String w : allowedWords) {
            if (word.equals(w)) return true;
        }
        return false;
    }

    // Language model listen — records a fixed window then transcribes.
    // More reliable than kws for short responses like yes/no.
    // recordMs: how long to record in milliseconds.
    // Returns the full transcription, lowercased.
    public static String listenLM(PocketSphinx sphinx, long decoderPtr, int recordMs) {
        micOn();
        CRobotUtil.wait(500);
        _mic.flush();

        int size = recordMs * SAMPLERATE * 2 / 1000;
        byte[] buffer = new byte[size];

        sphinx.startListening(decoderPtr);
        int bytesRead = _mic.read(buffer, 0, buffer.length);
        sphinx.processAudio(decoderPtr, buffer, bytesRead);

        micOff();
        sphinx.stopListening(decoderPtr);

        RecognitionResult result = sphinx.getRecognitionHypothesis(decoderPtr);
        String heard = (result != null && result.result != null)
                ? result.result.trim().toLowerCase() : "";
        System.out.println("[LM] Heard: '" + heard + "'  score: " + (result != null ? result.score : 0));
        return heard;
    }
}


// ─────────────────────────────────────────────
// ROBOT STATE
// Reads and writes robot_state.json.
// Persists interaction_count and corruption_level
// across reboots.
//
// robot_state.json:
// {
//   "interaction_count": 12,
//   "corruption_level": 6.0
// }
// ─────────────────────────────────────────────

class RobotState {

    private static final String STATE_FILE = "robot_state.json";

    public static void load(Blackboard bb) {
        File f = new File(STATE_FILE);
        if (!f.exists()) {
            System.out.println("[STATE] No state file. Starting fresh.");
            bb.data.put("BB_JOKE_COUNT", 0);
            bb.data.put("BB_LOVE_LEVEL", 0.0f);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(f.toPath()));
            int count = parseIntField(content, "joke_count");
            int level = parseIntField(content, "love_level");
            bb.data.put("BB_JOKE_COUNT", count);
            bb.data.put("BB_LOVE_LEVEL", (float) level);
            System.out.println("[STATE] Loaded — jokes told: " + count + "  love level: " + level);
        } catch (IOException e) {
            System.out.println("[STATE] Read failed: " + e.getMessage());
            bb.data.put("BB_JOKE_COUNT", 0);
            bb.data.put("BB_LOVE_LEVEL", 0.0f);
        }
    }

    public static void save(int jokeCount, int loveLevel) {
        String json = "{\n"
                + "  \"joke_count\": " + jokeCount  + ",\n"
                + "  \"love_level\": " + loveLevel  + "\n"
                + "}\n";
        try {
            Files.write(Paths.get(STATE_FILE), json.getBytes());
            System.out.println("[STATE] Saved — jokes: " + jokeCount + "  love level: " + loveLevel);
        } catch (IOException e) {
            System.out.println("[STATE] Write failed: " + e.getMessage());
        }
    }

    private static int parseIntField(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int i = json.indexOf(":", idx) + 1;
        while (i < json.length() && !Character.isDigit(json.charAt(i)) && json.charAt(i) != '-') i++;
        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return Integer.parseInt(json.substring(i, end).trim());
    }

    private static float parseFloatField(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return 0.0f;
        int i = json.indexOf(":", idx) + 1;
        while (i < json.length() && !Character.isDigit(json.charAt(i)) && json.charAt(i) != '-') i++;
        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return Float.parseFloat(json.substring(i, end).trim());
    }
}

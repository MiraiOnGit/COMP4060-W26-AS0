package Demo;

import jp.vstone.RobotLib.*;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MocapPlayer
 * ===========
 * Plays a MediaPipe pose JSON in sync with the original audio (.wav).
 *
 * Usage
 * -----
 *   java ... Demo.AS2.MocapPlayer videoplayback_pose.json videoplayback.wav
 */
public class Demo {

    static final String TAG        = "MocapPlayer";
    static final String RESOURCES  = "../resources/";
    static final String POSE_FILE  = RESOURCES + "CS50.json";
    static final String AUDIO_FILE = RESOURCES + "CS50.wav";

    // ── Motion poll rate (Hz) ─────────────────────────────────────────────
    // 2 → one pose every 500 ms
    static final int POLL_HZ = 2;

    // ── Exponential smoothing ─────────────────────────────────────────────
    // 0.0 = no smoothing, 0.5 = balanced, 0.9 = very smooth/laggy
    static final double SMOOTH_ALPHA = 0.5;

    // ── URDF elbow axes (matches SotaForwardK.rotRodrigues calls) ─────────
    private static final double[] L_ELBOW_AXIS = {  0.6258053, 0.329192519, 0.707106769 };
    private static final double[] R_ELBOW_AXIS = { -0.6258053, 0.329192519, 0.707106769 };

    // ── Servo index constants (matches ServoRangeTool.SERVO_IDS order) ────
    private static final int IDX_BODY_Y     = Frames.idx(CSotaMotion.SV_BODY_Y);
    private static final int IDX_L_SHOULDER = Frames.idx(CSotaMotion.SV_L_SHOULDER);
    private static final int IDX_L_ELBOW    = Frames.idx(CSotaMotion.SV_L_ELBOW);
    private static final int IDX_R_SHOULDER = Frames.idx(CSotaMotion.SV_R_SHOULDER);
    private static final int IDX_R_ELBOW    = Frames.idx(CSotaMotion.SV_R_ELBOW);
    private static final int IDX_HEAD_Y     = Frames.idx(CSotaMotion.SV_HEAD_Y);
    private static final int IDX_HEAD_P     = Frames.idx(CSotaMotion.SV_HEAD_P);
    private static final int IDX_HEAD_R     = Frames.idx(CSotaMotion.SV_HEAD_R);
    private static final int N_JOINTS       = ServoRangeTool.SERVO_IDS.length;

    // ── Landmark store ────────────────────────────────────────────────────

    /** One detected frame from the JSON — just the named landmarks we need. */
    private static class LandmarkFrame {
        double timestampMs;
        // indexed by LANDMARK_NAMES order, null if frame not detected
        double[] x, y, z;  // parallel arrays, length 33

        static final String[] NAMES = {
                "nose", "left_eye_inner", "left_eye", "left_eye_outer",
                "right_eye_inner", "right_eye", "right_eye_outer",
                "left_ear", "right_ear", "mouth_left", "mouth_right",
                "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
                "left_wrist", "right_wrist", "left_pinky", "right_pinky",
                "left_index", "right_index", "left_thumb", "right_thumb",
                "left_hip", "right_hip", "left_knee", "right_knee",
                "left_ankle", "right_ankle", "left_heel", "right_heel",
                "left_foot_index", "right_foot_index"
        };

        static int nameToIdx(String name) {
            for (int i = 0; i < NAMES.length; i++)
                if (NAMES[i].equals(name)) return i;
            return -1;
        }

        RealVector get(String name) {
            int i = nameToIdx(name);
            if (i < 0 || x == null) throw new IllegalArgumentException("Landmark not found: " + name);
            return MatrixUtils.createRealVector(new double[]{ x[i], y[i], z[i] });
        }
    }

    // ── Minimal JSON parser (no dependencies) ─────────────────────────────
    // The pose JSON structure is regular enough to parse line by line.
    // Each landmark is on consecutive lines: "index": N, "name": "...", "x": N, "y": N, "z": N

    private static List<LandmarkFrame> parseJson(String path) throws IOException {
        List<LandmarkFrame> frames = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            LandmarkFrame current = null;
            boolean inLandmarks  = false;
            double  lx = 0, ly = 0, lz = 0;
            String  lname = null;
            int     lindex = -1;
            String  line;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Start of a new frame object
                if (line.contains("\"frame_index\"")) {
                    current = new LandmarkFrame();
                    current.x = new double[33];
                    current.y = new double[33];
                    current.z = new double[33];
                    inLandmarks = false;
                }

                if (current == null) continue;

                if (line.contains("\"timestamp_ms\"")) {
                    current.timestampMs = parseDouble(line);
                }

                if (line.contains("\"detected\": false")) {
                    current.x = null; // mark as undetected
                }

                if (line.contains("\"landmarks\"")) {
                    inLandmarks = true;
                    lname = null; lindex = -1;
                }

                if (inLandmarks) {
                    if (line.contains("\"index\""))  lindex = (int) parseDouble(line);
                    if (line.contains("\"name\""))   lname  = parseString(line);
                    if (line.contains("\"x\""))      lx     = parseDouble(line);
                    if (line.contains("\"y\""))      ly     = parseDouble(line);
                    if (line.contains("\"z\"")) {
                        lz = parseDouble(line);
                        // z is always last field — store the landmark
                        if (lindex >= 0 && lindex < 33 && current.x != null) {
                            current.x[lindex] = lx;
                            current.y[lindex] = ly;
                            current.z[lindex] = lz;
                        }
                        lname = null; lindex = -1;
                    }
                }

                // End of frame (closing brace at depth 2)
                if (line.equals("}") || line.equals("},")) {
                    if (current != null && current.timestampMs >= 0) {
                        frames.add(current);
                        current = null;
                        inLandmarks = false;
                    }
                }
            }
        }

        return frames;
    }

    private static double parseDouble(String line) {
        // extract the last number from a line like:  "timestamp_ms": 123.456,
        String s = line.replaceAll(".*:\\s*", "").replaceAll("[,}].*", "").trim();
        return Double.parseDouble(s);
    }

    private static String parseString(String line) {
        // extract value from:  "name": "left_shoulder",
        int a = line.lastIndexOf('"', line.length() - 2);
        int b = line.lastIndexOf('"');
        if (b > a && a >= 0) return line.substring(a + 1, b);
        return "";
    }

    // ── Elbow angle around URDF diagonal axis ─────────────────────────────

    private static double elbowAngle(RealVector shoulder, RealVector elbow,
                                     RealVector wrist, double[] axis) {
        RealVector axisVec = MatrixUtils.createRealVector(axis);

        RealVector ua = elbow.subtract(shoulder);
        if (ua.getNorm() < 1e-9) return 0.0;
        ua = ua.mapDivide(ua.getNorm());

        RealVector fa = wrist.subtract(elbow);
        if (fa.getNorm() < 1e-9) return 0.0;
        fa = fa.mapDivide(fa.getNorm());

        RealVector uaP = ua.subtract(axisVec.mapMultiply(ua.dotProduct(axisVec)));
        RealVector faP = fa.subtract(axisVec.mapMultiply(fa.dotProduct(axisVec)));
        if (uaP.getNorm() < 1e-9 || faP.getNorm() < 1e-9) return 0.0;
        uaP = uaP.mapDivide(uaP.getNorm());
        faP = faP.mapDivide(faP.getNorm());

        double cx = uaP.getEntry(1)*faP.getEntry(2) - uaP.getEntry(2)*faP.getEntry(1);
        double cy = uaP.getEntry(2)*faP.getEntry(0) - uaP.getEntry(0)*faP.getEntry(2);
        double cz = uaP.getEntry(0)*faP.getEntry(1) - uaP.getEntry(1)*faP.getEntry(0);
        return Math.atan2(cx*axis[0] + cy*axis[1] + cz*axis[2], uaP.dotProduct(faP));
    }

    // ── Landmarks → Sota angle vector ─────────────────────────────────────
    // Coordinate correction:
    //   MediaPipe: X=person's right, Y=down, Z=toward camera
    //   Sota:      X=robot's left,   Y=forward, Z=up
    // Person faces camera → MediaPipe X and Sota X are mirrored.

    private static RealVector toAngles(LandmarkFrame f) {
        RealVector nose      = f.get("nose");
        RealVector lEar      = f.get("left_ear");
        RealVector rEar      = f.get("right_ear");
        RealVector lShoulder = f.get("left_shoulder");
        RealVector rShoulder = f.get("right_shoulder");
        RealVector lElbow    = f.get("left_elbow");
        RealVector rElbow    = f.get("right_elbow");
        RealVector lWrist    = f.get("left_wrist");
        RealVector rWrist    = f.get("right_wrist");
        RealVector lHip      = f.get("left_hip");
        RealVector rHip      = f.get("right_hip");

        double[] a = new double[N_JOINTS];

        // SV_BODY_Y — rotZ in Sota (Z=up axis)
        // Use r-l (not l-r): Sota X = -MediaPipe X due to mirroring
        double sa = Math.atan2(rShoulder.getEntry(2) - lShoulder.getEntry(2),
                rShoulder.getEntry(0) - lShoulder.getEntry(0));
        double ha = Math.atan2(rHip.getEntry(2)      - lHip.getEntry(2),
                rHip.getEntry(0)      - lHip.getEntry(0));
        a[IDX_BODY_Y] = ((sa - ha + Math.PI) % (2 * Math.PI)) - Math.PI;

        // SV_HEAD_Y — rotZ, negate X (mirrored axis)
        double earMx = (lEar.getEntry(0) + rEar.getEntry(0)) / 2.0;
        double earMz = (lEar.getEntry(2) + rEar.getEntry(2)) / 2.0;
        a[IDX_HEAD_Y] = Math.atan2(-(nose.getEntry(0) - earMx),
                Math.max(Math.abs(nose.getEntry(2) - earMz), 0.01));

        // SV_HEAD_R — rotX, Sota Z=up = -MediaPipe Y so flip: rEar-lEar
        a[IDX_HEAD_R] = Math.atan2(rEar.getEntry(1) - lEar.getEntry(1),
                Math.abs(lEar.getEntry(0) - rEar.getEntry(0)) + 1e-6);

        // SV_HEAD_P — rotY, negate Y (Y-down → Z-up already handled by negation)
        double shMidY = (lShoulder.getEntry(1) + rShoulder.getEntry(1)) / 2.0;
        double shMidZ = (lShoulder.getEntry(2) + rShoulder.getEntry(2)) / 2.0;
        a[IDX_HEAD_P] = Math.atan2(-(nose.getEntry(1) - shMidY),
                Math.abs(nose.getEntry(2) - shMidZ) + 0.01);

        // SV_L_SHOULDER — rotX, negate Y (arm raised = elbow.y < shoulder.y in MP)
        a[IDX_L_SHOULDER] = Math.atan2(-(lElbow.getEntry(1) - lShoulder.getEntry(1)),
                Math.abs(lElbow.getEntry(2) - lShoulder.getEntry(2)) + 0.01);

        // SV_L_ELBOW — signed rotation around diagonal URDF axis
        a[IDX_L_ELBOW] = elbowAngle(lShoulder, lElbow, lWrist, L_ELBOW_AXIS);

        // SV_R_SHOULDER — rotX
        a[IDX_R_SHOULDER] = Math.atan2(-(rElbow.getEntry(1) - rShoulder.getEntry(1)),
                Math.abs(rElbow.getEntry(2) - rShoulder.getEntry(2)) + 0.01);

        // SV_R_ELBOW
        a[IDX_R_ELBOW] = elbowAngle(rShoulder, rElbow, rWrist, R_ELBOW_AXIS);

        return new ArrayRealVector(a);
    }

    // ── Frame data ────────────────────────────────────────────────────────

    static class PoseEntry {
        final double     timestampMs;
        final CRobotPose pose;   // null = undetected, hold last pose
        PoseEntry(double ts, CRobotPose p) { timestampMs = ts; pose = p; }
    }

    // ── Load + sample at POLL_HZ with exponential smoothing ───────────────

    static List<PoseEntry> loadPoses(String jsonPath, ServoRangeTool ranges)
            throws IOException {

        List<LandmarkFrame> raw      = parseJson(jsonPath);
        double pollIntervalMs        = 1000.0 / POLL_HZ;
        List<PoseEntry> result       = new ArrayList<>();
        RealVector smoothed          = null;
        double nextSampleMs          = 0.0;
        int converted = 0, skipped  = 0;

        for (LandmarkFrame f : raw) {
            if (f.timestampMs < nextSampleMs) continue;
            nextSampleMs = f.timestampMs + pollIntervalMs;

            if (f.x == null) {   // undetected
                result.add(new PoseEntry(f.timestampMs, null));
                skipped++;
                continue;
            }

            try {
                RealVector angles = toAngles(f);
                smoothed = (smoothed == null)
                        ? angles
                        : smoothed.mapMultiply(SMOOTH_ALPHA).add(angles.mapMultiply(1.0 - SMOOTH_ALPHA));
                result.add(new PoseEntry(f.timestampMs, ranges.calcMotorValues(smoothed)));
                converted++;
            } catch (Exception e) {
                result.add(new PoseEntry(f.timestampMs, null));
                skipped++;
            }
        }

        CRobotUtil.Log(TAG, String.format(
                "Loaded %d poses at %d Hz (%d converted, %d skipped).",
                result.size(), POLL_HZ, converted, skipped));
        return result;
    }

    // ── Main ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // Use constants above, or override with command-line args
        String poseFile  = args.length > 0 ? args[0] : POSE_FILE;
        String audioFile = args.length > 1 ? args[1] : AUDIO_FILE;

        ServoRangeTool ranges = ServoRangeTool.Load();
        if (ranges == null) {
            CRobotUtil.Log(TAG, "range.dat not found — run AS2_1 first.");
            System.exit(1);
        }

        List<PoseEntry> poses = loadPoses(poseFile, ranges);
        if (poses.isEmpty()) {
            CRobotUtil.Log(TAG, "No poses loaded.");
            System.exit(1);
        }

        CRobotMem   mem    = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);

        if (!mem.Connect()) {
            CRobotUtil.Log(TAG, "Connection failed.");
            System.exit(1);
        }

        motion.InitRobot_Sota();
        CRobotUtil.Log(TAG, "Rev. " + mem.FirmwareRev.get());
        motion.ServoOn();

        // Move to first detected pose before audio starts
        for (PoseEntry e : poses) {
            if (e.pose != null) {
                motion.play(e.pose, 1000);
                motion.waitEndinterpAll();
                break;
            }
        }

        CRobotUtil.Log(TAG, "Starting audio + motion sync...");

        // Audio runs in background; wall clock starts when it does
        Thread audioThread = new Thread(() -> CPlayWave.PlayWave(audioFile));
        audioThread.setDaemon(true);
        audioThread.start();
        long wallStart = System.currentTimeMillis();

        for (int i = 0; i < poses.size(); i++) {
            PoseEntry entry = poses.get(i);
            if (entry.pose == null) continue;

            // transMs = gap to next detected pose so servo fills the interval exactly
            int transMs = 1000 / POLL_HZ;
            for (int j = i + 1; j < poses.size(); j++) {
                if (poses.get(j).pose != null) {
                    transMs = Math.max(20, (int)(poses.get(j).timestampMs - entry.timestampMs));
                    break;
                }
            }

            long waitMs = (long) entry.timestampMs - (System.currentTimeMillis() - wallStart);
            if (waitMs > 0) CRobotUtil.wait((int) waitMs);

            motion.play(entry.pose, transMs);
        }

        audioThread.join();

        // Return to neutral
        CRobotUtil.Log(TAG, "Returning to neutral.");
        CRobotPose neutral = new CRobotPose();
        neutral.SetPose(ServoRangeTool.SERVO_IDS, new Short[]{0, 0, 0, 0, 0, 0, 0, 0});
        motion.play(neutral, 1000);
        motion.waitEndinterpAll();

        CRobotUtil.Log(TAG, "Playback complete.");
        motion.ServoOff();
        mem.Disconnect();
    }
}
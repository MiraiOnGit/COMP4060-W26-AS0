package student.AS2;

import jp.vstone.RobotLib.*;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    static final String TAG        = "Demo";
    static final String RESOURCES  = "../resources/";
    static final String POSE_FILE  = RESOURCES + "sound/CS50.csv";
    static final String AUDIO_FILE = RESOURCES + "sound/CS50.wav";
    // ── Mouth LED settings ───────────────────────────────────────────────────
    static final int   LED_WINDOW_MS      = 100;   // how often LED updates (ms)
    static final float LED_THRESHOLD      = 0.02f; // RMS below this = silent
    static final int   LED_MOUTH_SILENT   = 0;     // mouth brightness when silent
    static final int   LED_MOUTH_MAX      = 255;   // mouth brightness when speaking


    // ── Motion poll rate (Hz) ─────────────────────────────────────────────
    // 2 → one pose every 500 ms
    static final int POLL_HZ = 24;

    // ── Exponential smoothing ─────────────────────────────────────────────
    // 0.0 = no smoothing, 0.5 = balanced, 0.9 = very smooth/laggy
    static final double SMOOTH_ALPHA = 0.7;

    // ── Elbow axes converted from Sota frame to MediaPipe world frame ──────
    // URDF defines axes in Sota frame: +X=left, +Y=back, +Z=up
    // MediaPipe world frame:           +X=right, +Y=down, +Z=toward camera
    // Conversion: mp_x=-sota_x, mp_y=-sota_z, mp_z=-sota_y
    // Left  Sota: ( 0.6258053,  0.329192519,  0.707106769)
    // Left  MP:   (-0.6258053, -0.707106769, -0.329192519)
    // Right Sota: (-0.6258053,  0.329192519,  0.707106769)
    // Right MP:   ( 0.6258053, -0.707106769, -0.329192519)
    private static final double[] L_ELBOW_AXIS = { -0.6258053, -0.707106769, -0.329192519 };
    private static final double[] R_ELBOW_AXIS = {  0.6258053, -0.707106769, -0.329192519 };

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

    // CSV column order (after frame_index, timestamp_ms, detected):
    // nose, left_ear, right_ear, left_shoulder, right_shoulder,
    // left_elbow, right_elbow, left_wrist, right_wrist, left_hip, right_hip
    // Each landmark = 3 doubles (x, y, z), so 33 doubles total starting at col 3.
    private static final String[] LM_NAMES = {
            "nose", "left_ear", "right_ear",
            "left_shoulder", "right_shoulder",
            "left_elbow", "right_elbow",
            "left_wrist", "right_wrist",
            "left_hip", "right_hip"
    };

    private static class LandmarkFrame {
        double timestampMs;
        boolean detected;
        java.util.Map<String, double[]> landmarks = new java.util.HashMap<>();

        RealVector get(String name) {
            double[] v = landmarks.get(name);
            if (v == null) throw new IllegalArgumentException("Landmark not found: " + name);
            return MatrixUtils.createRealVector(v);
        }
    }

    // ── CSV parser ────────────────────────────────────────────────────────
    // Row format: frame_index, timestamp_ms, detected, x0,y0,z0, x1,y1,z1, ...

    private static List<LandmarkFrame> parseCsv(String path) throws IOException {
        List<LandmarkFrame> frames = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",");
                LandmarkFrame f = new LandmarkFrame();
                f.timestampMs = Double.parseDouble(cols[1]);
                f.detected    = Boolean.parseBoolean(cols[2]);

                if (f.detected) {
                    for (int i = 0; i < LM_NAMES.length; i++) {
                        int base = 3 + i * 3;
                        f.landmarks.put(LM_NAMES[i], new double[]{
                                Double.parseDouble(cols[base]),
                                Double.parseDouble(cols[base + 1]),
                                Double.parseDouble(cols[base + 2])
                        });
                    }
                }

                frames.add(f);
            }
        }

        return frames;
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

        // SV_L_SHOULDER — rotX, left shoulder range is INVERTED in ServoRangeTool
        // (lower=1.745, upper=-2.617) so raised arm = negative angle
        a[IDX_L_SHOULDER] = Math.atan2(-(lElbow.getEntry(1) - lShoulder.getEntry(1)),
                Math.abs(lElbow.getEntry(2) - lShoulder.getEntry(2)) + 0.01);

        // SV_L_ELBOW — signed rotation around diagonal URDF axis
        a[IDX_L_ELBOW] = elbowAngle(lShoulder, lElbow, lWrist, L_ELBOW_AXIS);

        // SV_R_SHOULDER — rotX
        a[IDX_R_SHOULDER] = Math.atan2((rElbow.getEntry(1) - rShoulder.getEntry(1)),
                Math.abs(rElbow.getEntry(2) - rShoulder.getEntry(2)) + 0.01);

        // SV_R_ELBOW — negate: right elbow servo direction is opposite to left
        a[IDX_R_ELBOW] = -elbowAngle(rShoulder, rElbow, rWrist, R_ELBOW_AXIS);

        return new ArrayRealVector(a);
    }

    // ── Frame data ────────────────────────────────────────────────────────

    static class PoseEntry {
        final double     timestampMs;
        final CRobotPose pose;     // null = undetected, hold last pose
        final RealVector  angles;  // raw MediaPipe-derived angles in radians
        PoseEntry(double ts, CRobotPose p, RealVector a) {
            timestampMs = ts; pose = p; angles = a;
        }
    }

    // ── Load + sample at POLL_HZ with exponential smoothing ───────────────

    static List<PoseEntry> loadPoses(String csvPath, ServoRangeTool ranges)
            throws IOException {

        List<LandmarkFrame> raw      = parseCsv(csvPath);
        double pollIntervalMs        = 1000.0 / POLL_HZ;
        List<PoseEntry> result       = new ArrayList<>();
        RealVector smoothed          = null;
        double nextSampleMs          = 0.0;
        int converted = 0, skipped  = 0;

        for (LandmarkFrame f : raw) {
            if (f.timestampMs < nextSampleMs) continue;
            nextSampleMs = f.timestampMs + pollIntervalMs;

            if (!f.detected) {   // undetected
                result.add(new PoseEntry(f.timestampMs, null, null));
                skipped++;
                continue;
            }

            try {
                RealVector angles = toAngles(f);
                smoothed = (smoothed == null)
                        ? angles
                        : smoothed.mapMultiply(SMOOTH_ALPHA).add(angles.mapMultiply(1.0 - SMOOTH_ALPHA));
                CRobotPose pose = ranges.calcMotorValues(smoothed);
                result.add(new PoseEntry(f.timestampMs, pose, smoothed.copy()));
                converted++;
            } catch (Exception e) {
                // Print the actual exception so we can see if parsing is failing
                CRobotUtil.Log(TAG, "Frame error at t=" + f.timestampMs + ": " + e.getMessage());
                result.add(new PoseEntry(f.timestampMs, null, null));
                skipped++;
            }
        }

        CRobotUtil.Log(TAG, String.format(
                "Loaded %d poses at %d Hz (%d converted, %d skipped).",
                result.size(), POLL_HZ, converted, skipped));
        return result;
    }

    // ── WAV amplitude timeline ────────────────────────────────────────────
    // Returns RMS energy per LED_WINDOW_MS chunk, normalised to 0.0–1.0.
    // Supports 16-bit PCM WAV (the format CPlayWave expects).

    static float[] loadWavRms(String wavPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(wavPath)))) {

            // Read RIFF header
            byte[] tag = new byte[4];
            dis.readFully(tag); // "RIFF"
            readInt32LE(dis);   // file size
            dis.readFully(tag); // "WAVE"

            // Find fmt chunk
            int sampleRate = 44100, bitsPerSample = 16, channels = 1;
            while (true) {
                dis.readFully(tag);
                int chunkSize = readInt32LE(dis);
                String chunkId = new String(tag);
                if (chunkId.equals("fmt ")) {
                    readInt16LE(dis);               // audio format (1=PCM)
                    channels      = readInt16LE(dis);
                    sampleRate    = readInt32LE(dis);
                    readInt32LE(dis);               // byte rate
                    readInt16LE(dis);               // block align
                    bitsPerSample = readInt16LE(dis);
                    int remaining = chunkSize - 16;
                    if (remaining > 0) dis.skipBytes(remaining);
                    break;
                } else {
                    dis.skipBytes(chunkSize);
                }
            }

            // Find data chunk
            int dataSize = 0;
            while (true) {
                dis.readFully(tag);
                dataSize = readInt32LE(dis);
                if (new String(tag).equals("data")) break;
                dis.skipBytes(dataSize);
            }

            // Read PCM samples (16-bit signed little-endian)
            byte[] raw = new byte[dataSize];
            dis.readFully(raw);

            int samplesPerWindow = (sampleRate * LED_WINDOW_MS) / 1000;
            int totalSamples     = dataSize / (bitsPerSample / 8) / channels;
            int numWindows       = (totalSamples + samplesPerWindow - 1) / samplesPerWindow;
            float[] rms          = new float[numWindows];
            float   maxRms       = 0;

            for (int w = 0; w < numWindows; w++) {
                int start = w * samplesPerWindow;
                int end   = Math.min(start + samplesPerWindow, totalSamples);
                double sum = 0;
                for (int s = start; s < end; s++) {
                    int byteIdx = s * channels * 2;
                    if (byteIdx + 1 >= raw.length) break;
                    short sample = (short)(((raw[byteIdx+1] & 0xFF) << 8) | (raw[byteIdx] & 0xFF));
                    sum += (double) sample * sample;
                }
                rms[w] = (float) Math.sqrt(sum / (end - start));
                if (rms[w] > maxRms) maxRms = rms[w];
            }

            // Normalise to 0.0–1.0
            if (maxRms > 0)
                for (int i = 0; i < rms.length; i++) rms[i] /= maxRms;

            CRobotUtil.Log(TAG, String.format(
                    "WAV: %d Hz  %d-bit  %d ch  %d windows @ %d ms",
                    sampleRate, bitsPerSample, channels, numWindows, LED_WINDOW_MS));
            return rms;
        }
    }

    private static int readInt32LE(DataInputStream dis) throws IOException {
        byte[] b = new byte[4];
        dis.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int readInt16LE(DataInputStream dis) throws IOException {
        byte[] b = new byte[2];
        dis.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort();
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

        // Pre-load WAV amplitude for LED sync
        float[] rms = null;
        try { rms = loadWavRms(audioFile); }
        catch (Exception e) { CRobotUtil.Log(TAG, "WAV LED load failed: " + e.getMessage()); }
        final float[] rmsData = rms;

        CRobotUtil.Log(TAG, "Starting audio + motion sync...");

        // Audio runs in background; wall clock starts when it does
        Thread audioThread = new Thread(() -> CPlayWave.PlayWave(audioFile));
        audioThread.setDaemon(true);
        audioThread.start();
        long wallStart = System.currentTimeMillis();

        // LED thread: blink mouth LED based on WAV RMS energy
        final CRobotPose ledPose = new CRobotPose();
        Thread ledThread = new Thread(() -> {
            if (rmsData == null) return;
            for (int w = 0; w < rmsData.length && !Thread.interrupted(); w++) {
                long targetMs = (long) w * LED_WINDOW_MS;
                long waitMs   = targetMs - (System.currentTimeMillis() - wallStart);
                if (waitMs > 0) CRobotUtil.wait((int) waitMs);
                // setLED_Sota(eye_L, eye_R, mouthBrightness, powerButton)
                int mouthBrightness = rmsData[w] >= LED_THRESHOLD
                        ? (int)(rmsData[w] * LED_MOUTH_MAX) : LED_MOUTH_SILENT;
                ledPose.setLED_Sota(Color.RED, Color.RED, mouthBrightness, null);
                motion.play(ledPose, LED_WINDOW_MS);
            }
            // Turn off LED when done
            ledPose.setLED_Sota(Color.WHITE, Color.WHITE, 5, null);
            motion.play(ledPose, 200);
        });
        ledThread.setDaemon(true);
        ledThread.start();

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

            // Print MediaPipe angles and motor positions when pose is sent
            Short[] pos = entry.pose.getServoAngles(ServoRangeTool.SERVO_IDS);
            RealVector a = entry.angles;
            CRobotUtil.Log(TAG, String.format(
                    "t=%.0fms  MP(rad): bodyY=%+.2f lSh=%+.2f lEl=%+.2f rSh=%+.2f rEl=%+.2f headY=%+.2f headP=%+.2f headR=%+.2f",
                    entry.timestampMs,
                    a.getEntry(IDX_BODY_Y), a.getEntry(IDX_L_SHOULDER), a.getEntry(IDX_L_ELBOW),
                    a.getEntry(IDX_R_SHOULDER), a.getEntry(IDX_R_ELBOW),
                    a.getEntry(IDX_HEAD_Y), a.getEntry(IDX_HEAD_P), a.getEntry(IDX_HEAD_R)));
            CRobotUtil.Log(TAG, String.format(
                    "t=%.0fms  Motor:   bodyY=%5d lSh=%5d lEl=%5d rSh=%5d rEl=%5d headY=%5d headP=%5d headR=%5d",
                    entry.timestampMs,
                    pos[0], pos[1], pos[2], pos[3], pos[4], pos[5], pos[6], pos[7]));

            motion.play(entry.pose, transMs);
        }

        audioThread.join();
        ledThread.interrupt();

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
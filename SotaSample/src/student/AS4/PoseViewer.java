package student.AS4;

import jp.vstone.RobotLib.*;
import student.AS2.*;
import student.AS2.Frames.FrameKeys;
import org.apache.commons.math3.linear.RealVector;

// ═════════════════════════════════════════════
// PoseViewer.java
//
// Run this to see SOTA's current servo positions
// and hand/head positions in real time.
// Move SOTA by hand (servos off) to find poses.
// Press ENTER to capture the current pose as a
// Java code snippet you can paste into Actions.java.
// Press power button to quit.
//
// Output every 200ms:
//   ── servo raw positions (IDs 1-8)
//   ── servo positions as new Short[]{...} ready to paste
//   ── FK: left hand, right hand, head (x, y, z in metres)
//
// ENTER: prints a captured keyframe as Java code
// Power button: exits
// ═════════════════════════════════════════════

public class PoseViewer {

    static final String TAG = "PoseViewer";

    public static void main(String[] args) throws Exception {
        CRobotUtil.Log(TAG, "Starting PoseViewer");

        CRobotMem   mem    = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);

        if (!mem.Connect()) {
            CRobotUtil.Log(TAG, "Connection failed.");
            return;
        }

        motion.InitRobot_Sota();
        CRobotUtil.Log(TAG, "Connected. Rev: " + mem.FirmwareRev.get());

        // Servos OFF so you can move SOTA by hand
        motion.ServoOff();
        CRobotUtil.Log(TAG, "Servos OFF — move SOTA by hand.");
        CRobotUtil.Log(TAG, "Press ENTER to capture keyframe. Press power button to quit.");

        // Load servo ranges for FK
        ServoRangeTool ranges = ServoRangeTool.Load();
        if (ranges == null) {
            CRobotUtil.Log(TAG, "WARNING: range.dat not found. FK positions will not be shown.");
        }

        // Non-blocking stdin reader for ENTER key
        Thread inputThread = new Thread(() -> {
            try {
                java.io.BufferedReader reader =
                        new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                while (true) {
                    reader.readLine(); // blocks until ENTER
                    captureKeyframe(motion, ranges);
                }
            } catch (Exception e) { /* exit */ }
        });
        inputThread.setDaemon(true);
        inputThread.start();

        // Clear screen
        System.out.print("\033[H\033[2J");
        System.out.flush();

        // Main display loop
        while (!motion.isButton_Power()) {
            System.out.print("\033[H"); // move cursor to top left

            Short[] pos = motion.getReadpos();
            Byte[]  ids = motion.getDefaultIDs();

            printDivider("CURRENT SERVO POSITIONS");

            // Named servo display
            String[] names = {
                    "1 body yaw    ",
                    "2 L shoulder  ",
                    "3 L elbow     ",
                    "4 R shoulder  ",
                    "5 R elbow     ",
                    "6 head yaw    ",
                    "7 head pitch  ",
                    "8 head roll   "
            };
            for (int i = 0; i < pos.length && i < names.length; i++) {
                System.out.printf("  ID %s : %6d%n", names[i], pos[i]);
            }

            // Paste-ready short array
            System.out.println();
            printDivider("PASTE-READY");
            System.out.print("  new Short[]{");
            for (int i = 0; i < pos.length; i++) {
                System.out.printf("%6d", pos[i]);
                if (i < pos.length - 1) System.out.print(", ");
            }
            System.out.println("}");
            System.out.println("  // body, Lshoulder, Lelbow, Rshoulder, Relbow, headYaw, headPitch, headRoll");

            // FK positions
            if (ranges != null) {
                System.out.println();
                printDivider("FK POSITIONS (metres)");
                try {
                    RealVector angles = ranges.calcAngles(motion.getReadPose());
                    SotaForwardK fk   = new SotaForwardK(angles);

                    double[] lh = MatrixHelp.getTrans(fk.frames.get(FrameKeys.L_HAND)).toArray();
                    double[] rh = MatrixHelp.getTrans(fk.frames.get(FrameKeys.R_HAND)).toArray();
                    double[] hd = MatrixHelp.getTrans(fk.frames.get(FrameKeys.HEAD)).toArray();

                    System.out.printf("  L hand : x=%7.4f  y=%7.4f  z=%7.4f%n", lh[0], lh[1], lh[2]);
                    System.out.printf("  R hand : x=%7.4f  y=%7.4f  z=%7.4f%n", rh[0], rh[1], rh[2]);
                    System.out.printf("  Head   : x=%7.4f  y=%7.4f  z=%7.4f%n", hd[0], hd[1], hd[2]);
                } catch (Exception e) {
                    System.out.println("  (FK error: " + e.getMessage() + ")");
                }
            }

            System.out.println();
            System.out.println("  [ENTER] capture keyframe    [POWER BUTTON] quit");
            System.out.flush();

            CRobotUtil.wait(200);
        }

        CRobotUtil.Log(TAG, "Power button pressed. Exiting.");
    }

    // ── Print a captured keyframe as Java code ─────────────

    static void captureKeyframe(CSotaMotion motion, ServoRangeTool ranges) {
        Short[] pos = motion.getReadpos();

        System.out.println();
        System.out.println("╔══════════ KEYFRAME CAPTURED ══════════╗");
        System.out.println();

        // Direct SetPose snippet
        System.out.println("// Option A — direct servo values:");
        System.out.println("CRobotPose pose = new CRobotPose();");
        System.out.println("pose.SetPose(");
        System.out.println("    new Byte[]  {1, 2, 3, 4, 5, 6, 7, 8},");
        System.out.printf ("    new Short[] {%d, %d, %d, %d, %d, %d, %d, %d}%n",
                pos[0], pos[1], pos[2], pos[3], pos[4], pos[5], pos[6], pos[7]);
        System.out.println(");");
        System.out.println("motion.play(pose, 500);");
        System.out.println("motion.waitEndinterpAll();");

        // IK snippet if ranges available
        if (ranges != null) {
            System.out.println();
            System.out.println("// Option B — IK target (offsets from home, in metres):");
            System.out.println("// Run with IKHelper.moveHand(FrameKeys.L_HAND, dx, dy, dz, ms)");
            try {
                RealVector angles = ranges.calcAngles(motion.getReadPose());
                SotaForwardK fk   = new SotaForwardK(angles);
                double[] lh = MatrixHelp.getTrans(fk.frames.get(FrameKeys.L_HAND)).toArray();
                double[] rh = MatrixHelp.getTrans(fk.frames.get(FrameKeys.R_HAND)).toArray();
                double[] hd = MatrixHelp.getTrans(fk.frames.get(FrameKeys.HEAD)).toArray();
                System.out.printf("// L hand absolute: (%.4f, %.4f, %.4f)%n", lh[0], lh[1], lh[2]);
                System.out.printf("// R hand absolute: (%.4f, %.4f, %.4f)%n", rh[0], rh[1], rh[2]);
                System.out.printf("// Head   absolute: (%.4f, %.4f, %.4f)%n", hd[0], hd[1], hd[2]);
            } catch (Exception e) {
                System.out.println("// (FK error: " + e.getMessage() + ")");
            }
        }

        System.out.println();
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.println();
    }

    static void printDivider(String title) {
        int dashes = Math.max(0, 38 - title.length());
        StringBuilder sb = new StringBuilder("──── " + title + " ");
        for (int i = 0; i < dashes; i++) sb.append('─');
        System.out.println(sb.toString());
    }
}
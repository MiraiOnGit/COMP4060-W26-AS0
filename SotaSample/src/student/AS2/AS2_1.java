package student.AS2;

import jp.vstone.RobotLib.*;

import java.awt.*;


public class AS2_1 {
    static final String TAG = "AS2_1";   // set this to support the Sota logging system
    static final String RESOURCES = "../resources/";
    static final String SOUNDS = RESOURCES+"sound/";
    static final int HZ = 10;
//    Write a small program that connects to the Sota
//    turns off the motors
//    enters a while loop that does not quit until the user presses the robot’s power button.
//    Inside the loop, read the robot motor positions,
//    report the positions to your ServoRangeTool (see below) which keeps track of
//        the minimum,
//        maximum,
//        midpoint positions
//    for all the motors.
//    Save the observed mid and max ranges to a file for later loading
    public static void main(String[] args) {
        CRobotUtil.Log(TAG, "Start " + TAG);

        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);

        if(mem.Connect()){
            motion.InitRobot_Sota();
            CRobotUtil.Log(TAG, "Rev. " + mem.FirmwareRev.get());

            ServoRangeTool rangeTool = new ServoRangeTool(new Byte[]{
                    CSotaMotion.SV_BODY_Y,
                    CSotaMotion.SV_L_SHOULDER,
                    CSotaMotion.SV_L_ELBOW,
                    CSotaMotion.SV_R_SHOULDER,
                    CSotaMotion.SV_R_ELBOW,
                    CSotaMotion.SV_HEAD_Y,
                    CSotaMotion.SV_HEAD_P,
                    CSotaMotion.SV_HEAD_R
            });
            // clear screen and move cursor to top left
            System.out.print("\033[H\033[2J"); System.out.flush();
            CRobotUtil.Log(TAG, "Servo Off");
            motion.ServoOff();


            while (!motion.isButton_Power()) {  // stop when power button pressed
                System.out.print("\033[H"); // move cursor to top left before redrawing
                Short[] pos = motion.getReadpos();
                rangeTool.register(pos);
                rangeTool.printMotorRanges(pos);

                System.out.flush();  // force stdout flush before waiting to avoid tearing / flicker.
                CRobotUtil.wait(1000 / HZ);
            }

            rangeTool.save();
            CRobotUtil.Log(TAG, "Ranges saved to " + ServoRangeTool.FILENAME);

            CRobotUtil.Log(TAG, "Servo Off");
            motion.ServoOff();
        }
    }
}

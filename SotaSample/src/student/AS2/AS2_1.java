package student.AS2;

import jp.vstone.RobotLib.*;

import java.awt.*;


public class AS2_1 {
    static final String TAG = "AS2_1";   // set this to support the Sota logging system
    static final String RESOURCES = "../resources/";
    static final String SOUNDS = RESOURCES+"sound/";
    static final int HZ = 10;

    public static void main(String[] args) {
        CRobotUtil.Log(TAG, "Start " + TAG);

        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);

        if(mem.Connect()){
            motion.InitRobot_Sota();
            CRobotUtil.Log(TAG, "Rev. " + mem.FirmwareRev.get());
            ServoRangeTool rangeTool = new ServoRangeTool(ServoRangeTool.SERVO_IDS);

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

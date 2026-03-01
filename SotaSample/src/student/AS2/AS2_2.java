package student.AS2;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;

public class AS2_2 {
    static final String TAG = "AS2_2";   // set this to support the Sota logging system
    static final String RESOURCES = "../resources/";
    static final int HZ = 10;

    public static void main(String[] args) {
        CRobotUtil.Log(TAG, "Start " + TAG);

        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if(mem.Connect()){
            motion.InitRobot_Sota();
            CRobotUtil.Log(TAG, "Rev. " + mem.FirmwareRev.get());
            ServoRangeTool rangeTool = ServoRangeTool.Load();

            CRobotUtil.Log(TAG, "Ranges loaded from " + ServoRangeTool.FILENAME);

            // Print the loaded ranges so we can verify they look correct
            rangeTool.printMotorRanges();
            CRobotUtil.wait(2000);  // pause so user can read the ranges

            CRobotPose minPose = rangeTool.getMinPose();
            CRobotPose midPose = rangeTool.getMidPose();
            CRobotPose maxPose = rangeTool.getMaxPose();

            CRobotUtil.Log(TAG, "Servo On - Mid Pos");
            motion.ServoOn();
            motion.play(midPose, 1000);

            motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
            CRobotUtil.wait(500);   //pause the program / current thread


            CRobotUtil.Log(TAG, "Servo On - Mid Pos");
            motion.ServoOn();
            motion.play(midPose, 1000);

            motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
            CRobotUtil.wait(500);   //pause the program / current thread

            for (Byte id : ServoRangeTool.SERVO_IDS) {
                CRobotUtil.Log(TAG, "Testing joint ID: " + id);
                CRobotPose currentPose = rangeTool.getMidPose();

                currentPose.SetPose(new Byte[]{id},
                        new Short[]{minPose.getServoAngle(id)});

                CRobotUtil.Log(TAG, "Min joint: " + minPose.getServoAngle(id));
                motion.play(currentPose, 1000);
                motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
                CRobotUtil.wait(500);   //pause the program / current thread

                currentPose.SetPose(new Byte[]{id},
                        new Short[]{maxPose.getServoAngle(id)});
                CRobotUtil.Log(TAG, "Max joint: " + maxPose.getServoAngle(id));
                motion.play(currentPose, 1000);
                motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
                CRobotUtil.wait(500);   //pause the program / current thread

                currentPose.SetPose(new Byte[]{id},
                        new Short[]{midPose.getServoAngle(id)});
                CRobotUtil.Log(TAG, "Mid joint: " + midPose.getServoAngle(id));
                motion.play(currentPose, 1000);
                motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
                CRobotUtil.wait(500);   //pause the program / current thread

            }
            CRobotUtil.Log(TAG, "Servo Off");
            motion.ServoOff();



        }
    }
}

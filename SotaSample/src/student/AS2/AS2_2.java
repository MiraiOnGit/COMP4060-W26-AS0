package student.AS2;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;

public class AS2_2 {
    static final String TAG = "AS2_2";   // set this to support the Sota logging system
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
            ServoRangeTool rangeTool = ServoRangeTool.Load();

            if (rangeTool == null) {
                CRobotUtil.Log(TAG, "ERROR: Could not load " + ServoRangeTool.FILENAME);
                return;
            }
            CRobotUtil.Log(TAG, "Ranges loaded from " + ServoRangeTool.FILENAME);

            // Print the loaded ranges so we can verify they look correct
            rangeTool.printMotorRanges();
            CRobotUtil.wait(2000);  // pause so user can read the ranges


        }
    }
}

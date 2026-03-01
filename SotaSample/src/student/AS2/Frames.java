package student.AS2;

//This is a simple mapping class that lets you specify which motors contribute to which frame.
//== for the FK case, you just use this as a type to index into maps, etc.
//== for the IK case, the details on which joints contribute to which frame is essential. Packing it in here enables you
//   to avoid a lot of if-statements later.

import jp.vstone.RobotLib.CSotaMotion;

public class Frames {

    public static int idx(byte servoID) {
        for (int i = 0; i < ServoRangeTool.SERVO_IDS.length; i++)
            if (ServoRangeTool.SERVO_IDS[i] == servoID) return i;
        throw new IllegalArgumentException("Unknown servo ID: " + servoID);
    }

    public enum FrameKeys{
        // store the motor indices that contribute to each frame here for use later.
        // hint: use IDtoIndex and the CSotaMotion. constants to make this easy to do.

        L_HAND(
                Frames.idx(CSotaMotion.SV_BODY_Y),
                Frames.idx(CSotaMotion.SV_L_SHOULDER),
                Frames.idx(CSotaMotion.SV_L_ELBOW)
        ),  // populate the parameters with the 3 motor indices that contribute to this position, and the constructor loads them into the int array below
        R_HAND(
                Frames.idx(CSotaMotion.SV_BODY_Y),
                Frames.idx(CSotaMotion.SV_R_SHOULDER),
                Frames.idx(CSotaMotion.SV_R_ELBOW)),  // same, add the three indices here for the r hand.
        HEAD(
                Frames.idx(CSotaMotion.SV_BODY_Y),
                Frames.idx(CSotaMotion.SV_HEAD_Y),
                Frames.idx(CSotaMotion.SV_HEAD_P),
                Frames.idx(CSotaMotion.SV_HEAD_R));   // here you need 4 indices

        public int[] motorindices;
        FrameKeys(int... motorindices){
            this.motorindices = motorindices;            
        }
    }
}
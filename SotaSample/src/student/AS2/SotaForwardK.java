package student.AS2;

import jp.vstone.RobotLib.CSotaMotion;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import student.AS2.Frames.FrameKeys;

import java.util.HashMap;
import java.util.Map;

public class SotaForwardK {

    // stores the final FK matrix for each frame type
    public final Map<FrameKeys, RealMatrix> frames = new HashMap<>();

    public SotaForwardK(double[] angles) { this(MatrixUtils.createRealVector(angles)); }
    public SotaForwardK(RealVector angles) {

        //======== setup Transformation matrices
        RealMatrix _base_to_origin   = MatrixUtils.createRealIdentityMatrix(4);

        RealMatrix _body_to_base     = MatrixHelp.T(MatrixHelp.rotZ(
                angles.getEntry(Frames.idx(CSotaMotion.SV_BODY_Y))),
                0,0,0.005);

        //HEAD
        RealMatrix _head_y_to_body     = MatrixHelp.T(MatrixHelp.rotZ(
                        angles.getEntry(Frames.idx(CSotaMotion.SV_HEAD_Y))),
                0,0,0.190);

        RealMatrix _head_r_to_head_y     = MatrixHelp.T(MatrixHelp.rotY(
                        angles.getEntry(Frames.idx(CSotaMotion.SV_HEAD_P))),
                0,0,0);

        RealMatrix _head_p_to_head_r     = MatrixHelp.T(MatrixHelp.rotX(
                        angles.getEntry(Frames.idx(CSotaMotion.SV_HEAD_R))),
                0,0,0);

        //L_HAND
        RealMatrix _l_shoulder_to_body     = MatrixHelp.T(MatrixHelp.rotX(
                        angles.getEntry(Frames.idx(CSotaMotion.SV_L_SHOULDER))),
                0.039,0,0.1415);

        RealMatrix _l_elbow_to_l_shoulder = MatrixHelp.T(
                MatrixHelp.rotRodrigues(0.6258053, 0.329192519, 0.707106769,
                        angles.getEntry(Frames.idx(CSotaMotion.SV_L_ELBOW))),
                0.0225, -0.03897, 0);

        RealMatrix _l_hand_to_l_elbow = MatrixHelp.trans(0,-0.048,0);
        //R_HAND
        RealMatrix _r_shoulder_to_body     = MatrixHelp.T(MatrixHelp.rotX(
                        angles.getEntry(Frames.idx(CSotaMotion.SV_R_SHOULDER))),
                -0.039,0,0.1415);
        RealMatrix _r_elbow_to_r_shoulder = MatrixHelp.T(
                MatrixHelp.rotRodrigues(-0.6258053, 0.329192519, 0.707106769,
                        angles.getEntry(Frames.idx(CSotaMotion.SV_R_ELBOW))),
                -0.0225, -0.03897, 0);
        RealMatrix _r_hand_to_r_elbow = MatrixHelp.trans(0,-0.048,0);

        //========== precalculate combined chains
        RealMatrix _body_to_origin      = _base_to_origin.multiply(_body_to_base);

        RealMatrix _head_y_to_origin    = _body_to_origin.multiply(_head_y_to_body);
        RealMatrix _head_r_to_origin    = _head_y_to_origin.multiply(_head_r_to_head_y);
        RealMatrix _head_p_to_origin    = _head_r_to_origin.multiply(_head_p_to_head_r);

        //L_HAND
        RealMatrix _l_shoulder_to_origin = _body_to_origin.multiply(_l_shoulder_to_body);
        RealMatrix _l_elbow_to_origin = _l_shoulder_to_origin.multiply(_l_elbow_to_l_shoulder);
        RealMatrix _l_hand_to_origin = _l_elbow_to_origin.multiply(_l_hand_to_l_elbow);
        //R_HAND
        RealMatrix _r_shoulder_to_origin = _body_to_origin.multiply(_r_shoulder_to_body);
        RealMatrix _r_elbow_to_origin = _r_shoulder_to_origin.multiply(_r_elbow_to_r_shoulder);
        RealMatrix _r_hand_to_origin = _r_elbow_to_origin.multiply(_r_hand_to_r_elbow);


        frames.put(FrameKeys.HEAD, _head_p_to_origin);
        frames.put(FrameKeys.L_HAND, _l_hand_to_origin);
        frames.put(FrameKeys.R_HAND, _r_hand_to_origin);
    }

}
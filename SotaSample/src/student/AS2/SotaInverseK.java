package student.AS2;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import student.AS2.Frames.FrameKeys;

import java.util.TreeMap;

public class SotaInverseK {

    private static double NUMERICAL_DELTA_rad = 1e-10;
    private static double DISTANCE_THRESH = 1e-3; // 1mm

    public enum JType {  // We separate the jacobians into origin and rotation components to simplify the problem
        O, // origin
        R; // rotation / orientation

        public static final int OUT_DIM = 3; // each has 3 outputs
    }

    public TreeMap<FrameKeys, RealMatrix>[] J;  // arrays, for O and R elements
    public TreeMap<FrameKeys, RealMatrix>[] Jinv;

    @SuppressWarnings("unchecked")
    SotaInverseK(RealVector currentAngles, FrameKeys frameType) {
        J = new TreeMap[JType.values().length];
        Jinv = new TreeMap[JType.values().length];
        for (int i=0; i < JType.values().length; i++) {
            J[i] = new TreeMap<FrameKeys, RealMatrix>();
            Jinv[i] = new TreeMap<FrameKeys, RealMatrix>();
        }
       makeJacobian(currentAngles, frameType);
    }

    // Makes both the jacobian and inverse from the current configuration for the
    // given frame type. Creates both JTypes.
    private void makeJacobian(RealVector currentAngles, FrameKeys frameType) {
        int[] motorIndices = frameType.motorindices;  // which motors affect this frame
        int n = motorIndices.length;                  // number of contributing motors
        RealMatrix J_O = MatrixUtils.createRealMatrix(JType.OUT_DIM, n);
        RealMatrix J_R = MatrixUtils.createRealMatrix(JType.OUT_DIM, n);
        SotaForwardK baseFk = new SotaForwardK(currentAngles);
        RealVector baseO = MatrixHelp.getTrans(baseFk.frames.get(frameType)).getSubVector(0, 3);
        RealVector baseR = MatrixHelp.getYPRVec(baseFk.frames.get(frameType));
        for (int col = 0; col < n; col++) {
            int motorIdx = motorIndices[col];
            RealVector perturbedAngles = currentAngles.copy();
            perturbedAngles.setEntry(motorIdx, currentAngles.getEntry(motorIdx) + NUMERICAL_DELTA_rad);
            SotaForwardK perturbedFk = new SotaForwardK(perturbedAngles);
            RealVector perturbedO = MatrixHelp.getTrans(perturbedFk.frames.get(frameType)).getSubVector(0, 3);
            RealVector perturbedR = MatrixHelp.getYPRVec(perturbedFk.frames.get(frameType));
            RealVector dO = perturbedO.subtract(baseO).mapDivide(NUMERICAL_DELTA_rad);
            RealVector dR = perturbedR.subtract(baseR).mapDivide(NUMERICAL_DELTA_rad);
            J_O.setColumnVector(col, dO);
            J_R.setColumnVector(col, dR);
        }
        J[JType.O.ordinal()].put(frameType, J_O);
        J[JType.R.ordinal()].put(frameType, J_R);
        Jinv[JType.O.ordinal()].put(frameType, MatrixHelp.pseudoInverse(J_O));
        Jinv[JType.R.ordinal()].put(frameType, MatrixHelp.pseudoInverse(J_R));
    }
    // solves for the target pose on the given frame and type, starting at the current angle configuration.
    static public RealVector solve(FrameKeys frameType, JType jtype, RealVector targetPose, RealVector curMotorAngles, final int MAX_TRIES) {
        RealVector solution = curMotorAngles.copy();;
        double bestError = Double.MAX_VALUE;
        RealVector currentAngles = curMotorAngles.copy();
        for (int iter = 0; iter < MAX_TRIES; iter++) {
            SotaForwardK fk = new SotaForwardK(currentAngles);
            RealMatrix frame = fk.frames.get(frameType);
            RealVector currentPose;
            if (jtype == JType.O) {
                currentPose = MatrixHelp.getTrans(frame).getSubVector(0, 3);
            } else {
                currentPose = MatrixHelp.getYPRVec(frame);
            }
            RealVector error = targetPose.subtract(currentPose);
            double errorNorm = error.getNorm();
            if (errorNorm < bestError) {
                bestError = errorNorm;
                solution = currentAngles.copy();
            }
            if (errorNorm < DISTANCE_THRESH) break;
            SotaInverseK ik = new SotaInverseK(currentAngles, frameType);
            RealMatrix Jinv = ik.Jinv[jtype.ordinal()].get(frameType);
            RealVector deltaTheta = Jinv.operate(error);
            int[] motorIndices = frameType.motorindices;
            for (int i = 0; i < motorIndices.length; i++) {
                int motorIdx = motorIndices[i];
                currentAngles.setEntry(motorIdx,
                        currentAngles.getEntry(motorIdx) + deltaTheta.getEntry(i));
            }
        }
        return solution;
    }
}
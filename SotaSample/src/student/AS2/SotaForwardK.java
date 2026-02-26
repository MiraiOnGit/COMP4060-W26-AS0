package student.AS2Distribute;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.math3.linear.*;
import student.AS2Distribute.Frames.FrameKeys;

public class SotaForwardK {

    // stores the final FK matrix for each frame type
    public final Map<FrameKeys, RealMatrix> frames = new HashMap<>();

    public SotaForwardK(double[] angles) { this(MatrixUtils.createRealVector(angles)); }
    public SotaForwardK(RealVector angles) {
        // TODO
        // constructs all the frame matrices and stores them in the frames Map
    }
}
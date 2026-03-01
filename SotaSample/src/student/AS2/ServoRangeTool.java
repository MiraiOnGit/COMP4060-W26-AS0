package student.AS2;

import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CSotaMotion;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import java.io.*;
import java.util.TreeMap;

public class ServoRangeTool implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Short[] _minpos = null;  // internal arrays for precalcualted values
    private Short[] _maxpos = null;
    private Short[] _midpos = null;
    private Byte[] _servoIDs;
    private TreeMap<Byte, Byte> IDtoIndex = new TreeMap<>();
    private final TreeMap<Byte, Double[]> _motorRanges_rad = new TreeMap<>();
    public static final Byte[] SERVO_IDS = new Byte[]{
            CSotaMotion.SV_BODY_Y,      // index 0
            CSotaMotion.SV_L_SHOULDER,  // index 1
            CSotaMotion.SV_L_ELBOW,     // index 2
            CSotaMotion.SV_R_SHOULDER,  // index 3
            CSotaMotion.SV_R_ELBOW,     // index 4
            CSotaMotion.SV_HEAD_Y,      // index 5
            CSotaMotion.SV_HEAD_P,      // index 6
            CSotaMotion.SV_HEAD_R       // index 7
    };

    final static String FILENAME = "range.dat";

    public ServoRangeTool(Byte[] servoIDs) {
        _servoIDs = servoIDs;

        for (byte i = 0; i < servoIDs.length; i++) {
            IDtoIndex.put(servoIDs[i], (byte) i);
        }
        _motorRanges_rad.put(CSotaMotion.SV_BODY_Y,     new Double[]{ -1.077363736,  1.077363736 });
        _motorRanges_rad.put(CSotaMotion.SV_L_SHOULDER, new Double[]{ 1.745329252,  -2.617993878 });
        _motorRanges_rad.put(CSotaMotion.SV_L_ELBOW,    new Double[]{ -1.745329252,  1.221730476 });
        _motorRanges_rad.put(CSotaMotion.SV_R_SHOULDER, new Double[]{ -1.745329252,  2.617993878 });
        _motorRanges_rad.put(CSotaMotion.SV_R_ELBOW,    new Double[]{ -1.221730476,  1.745329252 });
        _motorRanges_rad.put(CSotaMotion.SV_HEAD_Y,     new Double[]{ -1.495996502,  1.495996502 });
        _motorRanges_rad.put(CSotaMotion.SV_HEAD_P,     new Double[]{ -2.617993878,  2.617993878 });
        _motorRanges_rad.put(CSotaMotion.SV_HEAD_R,     new Double[]{ -1.495996502,  1.495996502 });

        int n = servoIDs.length;
        _minpos = new Short[n];
        _maxpos = new Short[n];
        _midpos = new Short[n];


    }

    public void register(CRobotPose pose) {
        register(pose.getServoAngles(_servoIDs));
     }
     public void register(Short[] pos) {
         for (int i = 0; i < pos.length; i++) {
             if (pos[i] == null) continue;

             if (_minpos[i] == null) {
                 _minpos[i] = pos[i];
                 _maxpos[i] = pos[i];
                 _midpos[i] = pos[i];
                 continue;
             }

             if (pos[i] < _minpos[i]) _minpos[i] = pos[i];
             if (pos[i] > _maxpos[i]) _maxpos[i] = pos[i];
             _midpos[i] = (short) ((_minpos[i] + _maxpos[i]) / 2);
         }

     }

    
    ///==================== Export as CRobotPose objects
    ///====================
    private CRobotPose makePose(Short[] pos) {  // convert short[] to CRobotPose object
        CRobotPose pose = new CRobotPose();
        pose.SetPose(_servoIDs, pos);
        return pose;
    }

    public CRobotPose getMinPose() { return makePose(_minpos);}
    public CRobotPose getMaxPose() { return makePose(_maxpos);}
    public CRobotPose getMidPose() { return makePose(_midpos);}

    ///==================== Angle <-> motor pos conversions
    ///====================
    public RealVector calcAngles(CRobotPose pose) { // convert pose in motor positions to radians
        Short[] positions = pose.getServoAngles(_servoIDs);
        double[] angles = new double[_servoIDs.length];
        for (int i = 0; i < _servoIDs.length; i++) {
            angles[i] = posToRad(_servoIDs[i], positions[i]);
        }
        return new ArrayRealVector(angles);
    }

    public CRobotPose calcMotorValues(RealVector angles) { // convert pose in angles to motor positions
        Short[] positions = new Short[_servoIDs.length];
        for (int i = 0; i < _servoIDs.length; i++) {
            positions[i] = radToPos(_servoIDs[i], angles.getEntry(i));
        }
        return makePose(positions);
    }

    private double posToRad(Byte servoID, Short pos) { // convert motor position to angle, in radians 
        int i = IDtoIndex.get(servoID);
        double lower = _motorRanges_rad.get(servoID)[0];
        double upper = _motorRanges_rad.get(servoID)[1];
        double min   = _minpos[i];
        double max   = _maxpos[i];
        return lower + (pos - min) * (upper - lower) / (max - min);
    }

    private short radToPos(Byte servoID, double angle) { // convert angles, in radians, to motor position
        int i = IDtoIndex.get(servoID);
        double lower = _motorRanges_rad.get(servoID)[0];
        double upper = _motorRanges_rad.get(servoID)[1];
        double min   = _minpos[i];
        double max   = _maxpos[i];
        return (short) Math.round(min + (angle - lower) * (max - min) / (upper - lower));
    }
    
	///==================== Pretty Print
    /// ///====================
	private String formattedLine(String title, Byte servoID, Short[] minpos, Short[] maxpos, Short[] middle, Short[] pos) {
        int i = IDtoIndex.get(servoID);
        double rad = 0;
        if (pos != null) rad = posToRad(servoID, pos[i]);
		String format = "%14s %8d %8d %8d    %.2f rad";
		return String.format(format, title, minpos[i], middle[i], maxpos[i], rad);
	}

    public void printMotorRanges() {printMotorRanges(null);}
	public void printMotorRanges(Short[] pos) {  // will print the current position as given by the pos array
		System.out.println("-------------");
		System.out.println( formattedLine("Body Y: ", CSotaMotion.SV_BODY_Y, _minpos, _maxpos, _midpos, pos));
		System.out.println( formattedLine("L Shoulder: ", CSotaMotion.SV_L_SHOULDER, _minpos, _maxpos, _midpos, pos));
        System.out.println( formattedLine("L Elbow: ", CSotaMotion.SV_L_ELBOW, _minpos, _maxpos, _midpos, pos));
		System.out.println( formattedLine("R Shoulder: ", CSotaMotion.SV_R_SHOULDER, _minpos, _maxpos, _midpos, pos));
		System.out.println( formattedLine("R Elbow: ", CSotaMotion.SV_R_ELBOW, _minpos, _maxpos, _midpos, pos));
        System.out.println( formattedLine("Head Y: ", CSotaMotion.SV_HEAD_Y, _minpos, _maxpos, _midpos, pos));
		System.out.println( formattedLine("Head P: ", CSotaMotion.SV_HEAD_P, _minpos, _maxpos, _midpos, pos));
        System.out.println( formattedLine("Head R: ", CSotaMotion.SV_HEAD_R, _minpos, _maxpos, _midpos, pos));
	}

    ///==================== LOAD AND SAVE
    ///====================
    public static ServoRangeTool Load(){ return ServoRangeTool.Load(FILENAME);}
    public static ServoRangeTool Load(String filename){
        try (ObjectInputStream ois =
                     new ObjectInputStream(new FileInputStream(filename))) {
            return (ServoRangeTool) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load: " + e.getMessage());
            return null;
        }
    }

    public void save() { save(FILENAME);}
    public void save(String filename) {
        try (ObjectOutputStream oos =
                     new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(this);
            System.out.println("ServoRangeTool saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Failed to save: " + e.getMessage());
        }
    }
}
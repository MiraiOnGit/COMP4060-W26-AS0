package student.AS2;

import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CSotaMotion;
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

    final static String FILENAME = "range.dat";

    public ServoRangeTool(Byte[] servoIDs) {
        _servoIDs = servoIDs;

        for (byte i = 0; i < servoIDs.length; i++) {
            IDtoIndex.put(servoIDs[i], (byte) i);
        }

        int n = servoIDs.length;
        _minpos = new Short[n];
        _maxpos = new Short[n];
        _midpos = new Short[n];


    }

        
    public void register(CRobotPose pose) {
        register(pose.getServoAngles(_servoIDs));
     }
     public void register(Short[] pos) {
         //todo
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
//    public RealVector calcAngles(CRobotPose pose) { // convert pose in motor positions to radians
//        return null; // TODO
//    }

//    public CRobotPose calcMotorValues(RealVector angles) { // convert pose in angles to motor positions
//        return null; // TODO
//    }

    private double posToRad(Byte servoID, Short pos) { // convert motor position to angle, in radians 
        return 0; // TODO
    }

    private short radToPos(Byte servoID, double angle) { // convert angles, in radians, to motor position
        return 0; // TODO
    }
    
	///==================== Pretty Print
    /// ///====================
	private String formattedLine(String title, Byte servoID, Short[] minpos, Short[] maxpos, Short[] middle, Short[] pos) {
//		int i = 0;
        int i = IDtoIndex.get(servoID);
//        double rad = 0;
//        if (pos != null) rad = posToRad(servoID, pos[i]);
//		String format = "%14s %8d %8d %8d    %.2f rad";
//		return String.format(format, title, minpos[i], middle[i], maxpos[i], rad);
        String format = "%14s %8d %8d %8d    ";
        return String.format(format, title, minpos[i], middle[i], maxpos[i]);
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
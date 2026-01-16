package student;
import java.awt.Color;
import java.io.File;

import jp.vstone.RobotLib.*;



public class Sample {
	static final String TAG = "sample";   // set this to support the Sota logging system
	static final String RESOURCES = "../resources/";
	static final String SOUNDS = RESOURCES+"sound/";

	public static void main(String args[]){
		

		CRobotUtil.Log(TAG, "Start " + TAG);

		CRobotPose pose = new CRobotPose();  // classes to manage robot pose information
		CRobotMem mem = new CRobotMem(); // connector for the Sota's information system (VSMD), connects via internal socket.
		CSotaMotion motion = new CSotaMotion(mem);   // motion control class. Pass it an instantiated CRobotMem
		
		if(mem.Connect()){ // connect to the robot's subsystem
			CRobotUtil.Log(TAG, "connect " + TAG);
			motion.InitRobot_Sota();  // initialize the Sota VSMD			
			CRobotUtil.Log(TAG, "Rev. " + mem.FirmwareRev.get());
			
			CRobotUtil.Log(TAG, "Servo On");
			motion.ServoOn();   // turns on motor torque / engage motors at current location
			
			Short[] pos = motion.getReadpos();     // read motor positions into an array
			Byte[] ids = motion.getDefaultIDs();  // get an array of the motor IDs

			for(int i = 0; i < pos.length;i++){
				CRobotUtil.Log(TAG, "Read Pos. ID:" + ids[i] + " , Pos:" + pos[i]);
			}

			// example of how to set poses in bulk. Store the pose information into the pose object.
			pose = new CRobotPose();
//			pose.SetPose(new Byte[] {
//							1, //body yaw
//							2, //left shoulder
//							3, //left elbow
//							4, //right shoulder
//							5, //right elbow
//							6, //head yaw
//							7, //head pitch
//							8 //head roll
//			}	//id
//			,  new Short[]{0   , 0 , 0   , 0 ,0   ,0   ,0   ,0}				//target pos
//			);

			// illuminate LEDS:  left eye, right eye, mouth, power button
			// all are RGB (use Java.colors) except mouth which is 8 bit brightness
			pose.setLED_Sota(Color.MAGENTA, Color.MAGENTA, 255, Color.RED);

			// apply the pose, do motion over 1000msec, linear interpolation
//			motion.play(pose,1000);

			// wait until motion has finished
//			motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
//			CRobotUtil.wait(500);   //pause the program / current thread

			pose.SetPose(new Byte[] {
							1, //body yaw
							2, //left shoulder
							3, //left elbow
							4, //right shoulder
							5, //right elbow
							6, //head yaw
							7, //head pitch
							8 //head roll
					}	//id
					,  new Short[]{0   , 0 , 0   , 0 ,700   ,-500   ,400   ,0}				//target pos
			);

			motion.play(pose,1000);

			// wait until motion has finished
			motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
			CRobotUtil.wait(500);   //pause the program / current thread

//			motion.ServoOff();
//			pose = new CRobotPose();
			pose.setLED_Sota(Color.MAGENTA, Color.MAGENTA, 255, Color.GREEN);
			motion.play(pose,500);
			motion.waitEndinterpAll();
			CPlayWave.PlayWave(SOUNDS+"ina-tomorrow.wav");
			CRobotUtil.wait(2000);

			pose.SetPose(new Byte[] {
							1, //body yaw
							2, //left shoulder
							3, //left elbow
							4, //right shoulder
							5, //right elbow
							6, //head yaw
							7, //head pitch
							8 //head roll
					}	//id
					,  new Short[]{0   , 0 , 0   , 0 ,0   ,0   ,0   ,0}				//target pos
			);
			// wait until motion has finished
			motion.waitEndinterpAll();   // also async public boolean isEndInterpAll()
			CRobotUtil.wait(500);   //pause the program / current thread

			motion.ServoOff();
		}
	}
}


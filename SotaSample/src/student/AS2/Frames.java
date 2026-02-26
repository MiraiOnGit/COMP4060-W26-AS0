package student.AS2Distribute;

//This is a simple mapping class that lets you specify which motors contribute to which frame.
//== for the FK case, you just use this as a type to index into maps, etc.
//== for the IK case, the details on which joints contribute to which frame is essential. Packing it in here enables you
//   to avoid a lot of if-statements later.

public class Frames {

    public enum FrameKeys{
        // store the motor indices that contribute to each frame here for use later.
        // hint: use IDtoIndex and the CSotaMotion. constants to make this easy to do.

        L_HAND(),  // populate the parameters with the 3 motor indices that contribute to this position, and the constructor loads them into the int array below
        R_HAND(),  // same, add the three indices here for the r hand.
        HEAD();   // here you need 4 indices

        public int[] motorindices;
        FrameKeys(int... motorindices){
            this.motorindices = motorindices;            
        }
    }
}
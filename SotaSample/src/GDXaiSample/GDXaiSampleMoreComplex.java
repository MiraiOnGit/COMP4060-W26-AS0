package GDXaiSample;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;


/// //////////////////////////////
/// This file has a more complex tree with more things happening. It uses the classes defined
/// in the other files, but adds new ones. Watch the traces to figure out what is happening
/// in the tree
///

// Simple Action node that continues for a while, maybe a very long time, but when
//	a task is done it sets the variable to true.
//	 ---  A good example of how to do node setup and clean up by monitoring whether you
//	      need to be setup or not.

class RandomTask extends LeafTaskTraced<Blackboard> {

	final int MAX_ACCUMULATION = 250; // when reached + or -, success
	final int MAX_TICKS = 30; // if we fail to reach in this many ticks, fail
	final int RAND_RATE = 11;  // random range from -1/2 to 1/2 this

	private final String _taskSuccessKey;

	private int _rate = 0;
	private int _total = 0;
	private int _ticks = 0; // times tried

	private Boolean _first = true; // needs to be setup
    public RandomTask(String taskSuccessKey) {
		_taskSuccessKey = taskSuccessKey;
    }

	private void _reset() {
		_rate = _total = _ticks = 0;
		_first = true;
		getObject().data.put(_taskSuccessKey, false); // not successful yet
	}

    @Override
    public Status executeTraced() {
		if (_first) {
			_reset();
			_first = false;
		}

        int rand = (int)(Math.random()*RAND_RATE-RAND_RATE/2);
		_rate += rand;
		_total += _rate;
		_ticks++;
//		System.out.println(rand+" "+_rate+ " "+_total);

		Status result = Status.RUNNING;
		if (Math.abs(_total) > MAX_ACCUMULATION) {
			result = Status.SUCCEEDED;
			_reset();
		} else if (_ticks > MAX_TICKS) {
			result = Status.FAILED;
			_reset();
		}
		getObject().data.put(_taskSuccessKey, result==Status.SUCCEEDED);
		return result; 
    }
	
	//// You need to implement this to support leaf and tree cloning and copying. You may never do it,
	///   but the API requires it. So for now, just put minimal effort into getting it to compile
	///   unless you start copying and cloning your subtrees.
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new RandomTask(_taskSuccessKey);
    }
}


public class GDXaiSampleMoreComplex {

	// shared data tags for the blackboard map. These keys just need to be mutually unique.
	private static final String KEY_randomResult = "RandomResult";
	private static final String KEY_randomTaskResult = "RandomTaskResult";

    public static BehaviorTree<Blackboard> makeMainTaskTree(Blackboard bb) {
		@SuppressWarnings("unchecked")  // just to suppress the annoying message
		BehaviorTree<Blackboard> tree = new BehaviorTree<>(
			new Selector<>(
				new ConditionTaskTraced(KEY_randomTaskResult),		// true when our random rask actually finished.
					new Sequence<>( // otherwise, do the following
						new RandomResultTraced(KEY_randomResult), // simulate some results
						new RandomTask(KEY_randomTaskResult),
						new PrintResultTraced(KEY_randomResult)
				)
			)
		);
		tree.setObject(bb);
		return tree;
	}

	public static BehaviorTree<Blackboard> makeTree(Blackboard bb) {
		@SuppressWarnings("unchecked")  // just to suppress the annoying message
		BehaviorTree<Blackboard> tree = new BehaviorTree<>(
			new Sequence<>(
					makeMainTaskTree(bb).getChild(0),
					new RandomResultTraced("")
			)
		);
		tree.setObject(bb);
		return tree;
	}

	void run() {

		// shared data structure to communicate within the tree. You may want to make your own robot state object
		Blackboard bb = new Blackboard();
		LeafTaskTraced.setDebugTraceEnabled(true); // important for global tracing.

		bb.data.put(KEY_randomResult, "none set yet");

		// Create a behavior tree manually
		// the BehaviorTree generic type is the blackboard type it expects. use your object type
		BehaviorTree<Blackboard> tree = makeTree(bb);
	
		final int MAX_TICKS = 1000;
		int ticks = 0;
		while (ticks < MAX_TICKS && tree.getStatus() != Status.SUCCEEDED) {

			LeafTaskTraced.incrementTickCounter();
			tree.step();  // tick the tree

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			ticks++;
		}
		bb.cleanup();
	}

	public static void main(String args[]){
		GDXaiSampleMoreComplex sample = new GDXaiSampleMoreComplex();
		sample.run();
	}

}

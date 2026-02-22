package GDXaiSample;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;

import java.util.HashMap;
import java.util.Map;


/// //////////////////////////////
/// This is a version of the GDXaiSample that has subclassed leaf node. If you use this new leaf node
/// instead of the build in LeafTask then you can easily turn on and off tracing. This will save you
/// boatloads of time in debugging. Note how it changes which function you call for executed
///

abstract class LeafTaskTraced<E> extends LeafTask<E> {

	private static Boolean _debugTraceEnabled = false;
	private static int _tickCounter = 0; // if used, keep track of what tick number to help in debugging

	// you can set a human-readable label for each specific leaf if the class name is not enough. it will
	//  use this in the printout trace
	private final String _humanReadableLabel;

	public LeafTaskTraced() { this("");}
	public LeafTaskTraced(String humanReadableLabel) {_humanReadableLabel = humanReadableLabel;}

	public static Boolean isDebugTraceEnabled() { 	return _debugTraceEnabled;	}
	public static void setDebugTraceEnabled(Boolean enabled) { _debugTraceEnabled = enabled; }
	public static void incrementTickCounter() { _tickCounter++; }

	public abstract Status executeTraced();

	@Override
	public Status execute() {
		String tag = "("+_tickCounter+") Trace "+_humanReadableLabel+" ("+this.getClass().getName()+"): ";
		if (_debugTraceEnabled) System.out.println(tag+"execute");
		Status result = executeTraced();
		if (_debugTraceEnabled) System.out.println(tag+"finished, result: "+result.toString());
		return result;
	}
}

// Simple Action Node that logs a message
class LogActionTraced extends LeafTaskTraced<Blackboard> {
    private final String message;

    public LogActionTraced(String message) {
		this.message = message;
    }

    @Override
    public Status executeTraced() {
        System.out.println(message);
        return Status.SUCCEEDED;
    }
	
	//// You need to implement this to support leaf and tree cloning and copying. You may never do it,
	///   but the API requires it. So for now, just put minimal effort into getting it to compile
	///   unless you start copying and cloning your subtrees.
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new LogAction(this.message);
    }
}

// Simple Action node that reads the random result from the blackboard and prints it
class RandomResultTraced extends LeafTaskTraced<Blackboard> {

	private final String _resultKey;

    public RandomResultTraced(String resultKey) {
		_resultKey = resultKey;
    }

    @Override
    public Status executeTraced() {
        int rand = (int)(Math.random()*3);
		Status result = Status.RUNNING;
		if (rand == 0)
			result =  Status.SUCCEEDED;
		else if (rand == 1)
			result = Status.FAILED;
		
		// save the result in our blackboard
		Blackboard bb = getObject(); // get the blackboard
		bb.data.put(_resultKey, result);
		return result; 
    }
	
	//// You need to implement this to support leaf and tree cloning and copying. You may never do it,
	///   but the API requires it. So for now, just put minimal effort into getting it to compile
	///   unless you start copying and cloning your subtrees.
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new RandomResult(_resultKey);
    }
}

// example task that checks a boolean on the blackboard and returns success if its true.
class ConditionTaskTraced extends LeafTaskTraced<Blackboard> {

	String _key = "";

	public ConditionTaskTraced(String booleanKey) {
		_key = booleanKey;
	}

	@Override
	public Status executeTraced() {
		Blackboard bb = getObject(); // get the blackboard
		if (bb.data.containsKey(_key) && (Boolean)bb.data.get(_key) )
			return Status.SUCCEEDED;
		else
			return Status.FAILED;
	}

	@Override
	protected Task<Blackboard> copyTo(Task<Blackboard> task) {
		return null;
	}
}

// Simple Action node that makes a random result and shares it in the blackboard
class PrintResultTraced extends LeafTaskTraced<Blackboard> {
	private final String _resultKey;

    public PrintResultTraced(String resultKey) {
		_resultKey = resultKey;
	}

    @Override
    public Status executeTraced() {
		Blackboard bb = getObject(); // get the blackboard
        System.out.println("Print result: "+bb.data.get(_resultKey));
		return Status.SUCCEEDED; 
    }
	
	//// You need to implement this to support leaf and tree cloning and copying. You may never do it,
	///   but the API requires it. So for now, just put minimal effort into getting it to compile
	///   unless you start copying and cloning your subtrees.
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new PrintResult(_resultKey);  }
}

public class GDXaiSampleDebugTraced {

	// shared data tags for the blackboard map. These keys just need to be mutually unique.
	private static final String KEY_randomResult = "RandomResult";

    public static BehaviorTree<Blackboard> makeTree(Blackboard bb) {
		@SuppressWarnings("unchecked")  // just to suppress the annoying message
		BehaviorTree<Blackboard> tree = new BehaviorTree<>(new Selector<>(
			new Sequence<>(
				new LogActionTraced("Step 1: Doing Random condition..."),
				new RandomResultTraced(KEY_randomResult)
			),
			new Sequence<>(
				new LogActionTraced("Step 2: Fallback action executed"),
				new PrintResultTraced(KEY_randomResult)
			)
		));
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
	
		final int MAX_TICKS = 10;
		int ticks = 0;
		while (ticks < MAX_TICKS) {

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
		GDXaiSampleDebugTraced sample = new GDXaiSampleDebugTraced();
		sample.run();
	}

}

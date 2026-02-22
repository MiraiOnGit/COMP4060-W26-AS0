package GDXaiSample;
import com.badlogic.gdx.ai.btree.*;
import com.badlogic.gdx.ai.btree.branch.*;

import java.util.HashMap;
import java.util.Map;


/// //////////////////////////////
/// This sample works to show you how to make a tree, a blackboard, how to connect them, etc.
/// For your project, it is strongly recommended to make a new .java for each task, each tree, and
/// the blackboard.
///
/// I made a "trees" folder / namespace, a "tasks" folder, and a "blackboard" folder which also
/// contains helpers for the blackboard. This lets you keep everything clean and separate.
/// The Java8 generics and the design of this API are a little painful.


// You probably want to put much of your robot control and state in here, and expose
// methods that are then called by the behavior tree. This simplifies the tree and keeps
// relevant code separated.
class Blackboard {

	// put your references here to things like your Sota control engine, speech engine, etc. This way
	// all the parts of the behavior tree have access, and state can be managed here.

	// I like to use a map for general data storage because you can then modify your tree, add new variables,
	// etc., without changing the Blackboard object.
	public Map<String, Object> data = new HashMap<>();  // general data storage

	public Blackboard() {
		// any needed global setup
	}

	public void cleanup() {
		// any needed global cleanup.
	}
}

// Simple Action Node that logs a message
class LogAction extends LeafTask<Blackboard> {
    private final String message;

    public LogAction(String message) {
        this.message = message;
    }

    @Override
    public Status execute() {
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

// Simple Action node that makes a random result and shares it in the blackboard
class RandomResult extends LeafTask<Blackboard> {

	private final String _resultKey;

    public RandomResult(String resultKey) {
		_resultKey = resultKey;
    }

    @Override
    public Status execute() {
        int rand = (int)(Math.random()*3);
		Status result = Status.RUNNING;
		if (rand == 0)
			result =  Status.SUCCEEDED;
		else if (rand == 1)
			result = Status.FAILED;
		
		// save the result in our blackboard
		Blackboard bb = getObject(); // get the blackboard
		bb.data.put(_resultKey, result.toString());
		System.out.println("Random Result: "+result.toString());
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

// Simple Action node that reads the random result from the blackboard and prints it
class PrintResult extends LeafTask<Blackboard> {
	private final String _resultKey;

    public PrintResult(String resultKey) {
		_resultKey = resultKey;
	}

    @Override
    public Status execute() {
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

// example task that checks a boolean on the blackboard and returns the state accordingly
class ConditionTask extends LeafTask<Blackboard> {

	String _key = "";

	public ConditionTask(String booleanKey) {
		_key = booleanKey;
	}

	@Override
	public Status execute() {
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
public class GDXaiSample {

	// shared data tags for the blackboard map. These keys just need to be mutually unique.
	private static final String KEY_randomResult = "RandomResult";

    public static BehaviorTree<Blackboard> makeTree(Blackboard bb) {
		@SuppressWarnings("unchecked")  // just to suppress the annoying message
		BehaviorTree<Blackboard> tree = new BehaviorTree<>(new Selector<>(
			new Sequence<>(
				new LogAction("Step 1: Doing Random condition..."),
				new RandomResult(KEY_randomResult)
			),
			new Sequence<>(
				new LogAction("Step 2: Fallback action executed"),
				new PrintResult(KEY_randomResult)
			)
		));
		tree.setObject(bb);
		return tree;
	}

	void run() {

		// shared data structure to communicate within the tree. You may want to make your own robot state object
		Blackboard bb = new Blackboard();
		bb.data.put(KEY_randomResult, "none set yet");

		// Create a behavior tree manually
		// the BehaviorTree generic type is the blackboard type it expects. use your object type
		BehaviorTree<Blackboard> tree = makeTree(bb);
	
		final int MAX_TICKS = 10;
		int ticks = 0;
		while (ticks < MAX_TICKS) {

			System.out.println("\nPre Tick: "+ticks+" Start");
			tree.step();  // tick the tree
			System.out.println("Post Tick: "+ticks+" status: "+tree.getStatus().toString());

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
		GDXaiSample sample = new GDXaiSample();
		sample.run();
	}

}

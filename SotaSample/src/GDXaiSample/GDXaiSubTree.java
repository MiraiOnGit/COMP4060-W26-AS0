package GDXaiSample;

import com.badlogic.gdx.ai.btree.*;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.ai.btree.branch.*;

public class GDXaiSubTree {

	void run() {

		// shared data structure to communicate within the tree. You may want to make your own robot state object
		Blackboard blackboard = new Blackboard();
;
		// Create a behavior tree manually
		// the BehaviorTree generic type is the blackboard type it expects
		@SuppressWarnings("unchecked")  // just to suppress the annoying generics message
		BehaviorTree<Blackboard> tree = new BehaviorTree<>(
			new Sequence<>(
				new LogAction("New Tree, before calling subtree..."),

					// to embed a tree in another tree, just get the first child to bypass
					//  the root note, and it works as expected
					GDXaiSample.makeTree(blackboard).getChild(0)
			)
		);
		tree.setObject(blackboard);
	
		final int MAX_TICKS = 10;
		int ticks = 0;
		while (ticks < MAX_TICKS) {
			// System.out.print("\033[H");  // move to origin
			
			System.out.println("\nTick: "+ticks+" Start");
			tree.step();  // tick the tree
			
			Status status = tree.getChild(0).getStatus();
			System.out.println("Tick: "+ticks+" status: "+status.toString());
			
			// System.out.flush();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			ticks++;
		}
	}

	public static void main(String args[]){
		GDXaiSubTree sample = new GDXaiSubTree();
		sample.run();
	}

}

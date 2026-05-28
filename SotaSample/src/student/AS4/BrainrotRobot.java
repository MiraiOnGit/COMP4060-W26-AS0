package student.AS4;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.decorator.UntilFail;
import com.badlogic.gdx.ai.btree.decorator.AlwaysFail;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;

import jp.vstone.RobotLib.*;
import pocketsphinx.PocketSphinx;


public class BrainrotRobot {

    static final String  TAG              = "BrainrotRobot";
    static final String  SPHINX_ROOT      = "/home/root/sotaprograms/resources/sphinxmodel/";
    static final double  MOTION_THRESHOLD = 0.05;
    static final boolean PICKUP_MODE      = true; // true = pickup line kiosk, false = joke mode

    @SuppressWarnings("unchecked")
    public static BehaviorTree<Blackboard> makeDialogTree(Blackboard bb) {

        BehaviorTree<Blackboard> tree = new BehaviorTree<>(

                // Outer UntilFail — loops prompt→listen→joke→checkin forever
                // Only exits when goodbye is heard (Selector fails → Sequence fails → UntilFail exits)
                new UntilFail<Blackboard>(
                        new Sequence<Blackboard>(

                                new PromptTask(),

                                new Sequence<Blackboard>(
                                        new ListenTask(),
                                        new Selector<Blackboard>(

                                                new Sequence<Blackboard>(
                                                        new IsDadCondition(),
                                                        new DadJokeTask()
                                                ),

                                                new Sequence<Blackboard>(
                                                        new IsSmartCondition(),
                                                        new IntellectTask()
                                                ),

                                                new Sequence<Blackboard>(
                                                        new IsLikeCondition(),
                                                        new LikeTask()
                                                ),

                                                // goodbye → AlwaysFail → Selector fails → Sequence fails
                                                // → outer UntilFail exits → farewell
                                                new AlwaysFail<Blackboard>(
                                                        new IsByeCondition()
                                                )
                                        )
                                ),

                                // Check-in loop — stop returns FAILED → inner UntilFail exits
                                // → outer Sequence continues to next tick → PromptTask again
                                new UntilFail<Blackboard>(
                                        new Sequence<Blackboard>(
                                                new CheckInAskTask(),
                                                new CheckInRouteTask()
                                        )
                                )
                        )
                )
        );

        tree.setObject(bb);
        return tree;
    }

    void run() {
        CRobotUtil.Log(TAG, "Starting BrainrotRobot");

        CRobotMem   mem    = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if (!mem.Connect()) { CRobotUtil.Log(TAG, "Connection failed"); return; }
        motion.InitRobot_Sota();

        // Camera for motion detection idle
        jp.vstone.camera.CRoboCamera cam = new jp.vstone.camera.CRoboCamera("/dev/video0", motion);

        PocketSphinx sphinx     = new PocketSphinx();
        long         decoderPtr = sphinx.initialize_kws(
                SPHINX_ROOT + "en-us/en-us",
                SPHINX_ROOT + "keyphrases.txt",
                SPHINX_ROOT + "en-us/cmudict-en-us.dict"
        );
        if (decoderPtr == 0) { CRobotUtil.Log(TAG, "Sphinx KWS init failed"); return; }

        Blackboard bb = new Blackboard();
        LeafTaskTraced.setDebugTraceEnabled(true);
        bb.data.put("BB_MOTION",      motion);
        bb.data.put("BB_SPHINX",      sphinx);
        bb.data.put("BB_DECODER",     decoderPtr);
        bb.data.put("BB_KEYWORD",     "");
        bb.data.put("BB_MODE",        "");
        bb.data.put("BB_RESPONSE",    "");
        bb.data.put("BB_VOICE_INDEX", 0);
        RobotState.load(bb);

        // ── IDLE — wait for motion ────────────────
        CRobotUtil.Log(TAG, "Idle — waiting for motion...");
        NeutralPose.run(motion);
        cam.StartMotionDetection();
        while (!motion.isButton_Power()) {
            Double motionVal = cam.getMotionDetectResult();
            if (motionVal != null && motionVal > MOTION_THRESHOLD) {
                System.out.println("[IDLE] Motion detected: " + motionVal);
                break;
            }
            CRobotUtil.wait(200);
        }
        cam.StopMotionDetection();
        if (motion.isButton_Power()) {
            motion.ServoOff();
            return;
        }

        // ── WELCOME ───────────────────────────────
        BehaviorTree<Blackboard> welcomeTree = new BehaviorTree<>(new WelcomeTask());
        welcomeTree.setObject(bb);
        welcomeTree.step();

        if (PICKUP_MODE) {
            runPickupMode(motion, sphinx, decoderPtr, bb);
        } else {
            runJokeMode(motion, sphinx, decoderPtr, bb);
        }

        // Save and shut down
        int count = (int) bb.data.getOrDefault("BB_JOKE_COUNT", 0);
        int level = (int)(float)(Object) bb.data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
        RobotState.save(count, level);
        motion.ServoOff();
        CRobotUtil.Log(TAG, "Done. Jokes told: " + count + "  Love level: " + level);
    }

    // ── JOKE MODE ─────────────────────────────────
    void runJokeMode(CSotaMotion motion, PocketSphinx sphinx, long decoderPtr, Blackboard bb) {
        BehaviorTree<Blackboard> tree = makeDialogTree(bb);
        boolean goodbye = false;
        while (!motion.isButton_Power() && !goodbye) {
            LeafTaskTraced.incrementTickCounter();
            tree.step();
            if (tree.getStatus() == com.badlogic.gdx.ai.btree.Task.Status.SUCCEEDED
                    || tree.getStatus() == com.badlogic.gdx.ai.btree.Task.Status.FAILED) {
                BehaviorTree<Blackboard> farewellTree = new BehaviorTree<>(new FarewellTask());
                farewellTree.setObject(bb);
                farewellTree.step();
                goodbye = true;
            }
            CRobotUtil.wait(100);
        }
    }

    // ── PICKUP LINE MODE ──────────────────────────
    void runPickupMode(CSotaMotion motion, PocketSphinx sphinx, long decoderPtr, Blackboard bb) {
        PickupLineManager pickup = new PickupLineManager();
        int level = 1;

        TTSHelper.speak("Hello! I have pickup lines for every occasion. Let's start easy.");
        LoveLED.set(motion, level);

        while (!motion.isButton_Power()) {
            System.out.println("[PICKUP MODE] Delivering level " + level + " pickup line.");
            LoveLED.set(motion, level);
            pickup.speakRandom(motion, level);

            CRobotUtil.wait(500);
            if (level < JokeLevelConfig.MAX_LEVEL) {
                TTSHelper.speak("Want me to turn it up a notch?");
            } else {
                TTSHelper.speak("That's full rizz mode. Want another one?");
            }

            ListenLED.on(motion);
            String heard = SpeechHelper.listenForKeyword(sphinx, decoderPtr,
                    "ok", "again", "more", "stop", "enough", "stay");
            ListenLED.off(motion, level);
            System.out.println("[PICKUP MODE] Heard: " + heard);

            if (heard.equals("ok") || heard.equals("again") || heard.equals("more")) {
                if (level < JokeLevelConfig.MAX_LEVEL) {
                    level++;
                    bb.data.put("BB_LOVE_LEVEL", (float) level);
                    System.out.println("[PICKUP MODE] Leveling up to: " + level);
                    TTSHelper.speak("Ooh, brave. Let's go.");
                } else {
                    TTSHelper.speak("Already at max. Here's another one.");
                }
            } else if (heard.equals("stop") || heard.equals("enough") || heard.equals("stay")) {
                TTSHelper.speak("Staying at this level. You asked for it.");
            } else {
                TTSHelper.speak("I'll take that as a yes.");
            }
            CRobotUtil.wait(300);
        }

        TTSHelper.speak("Goodbye. You were a great audience.");
    }

    public static void main(String[] args) {
        new BrainrotRobot().run();
    }
}
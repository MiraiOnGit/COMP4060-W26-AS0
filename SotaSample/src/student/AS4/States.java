package student.AS4;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.Task.Status;
import jp.vstone.RobotLib.*;
import pocketsphinx.PocketSphinx;
import pocketsphinx.RecognitionResult;


class WelcomeTask extends LeafTaskTraced<Blackboard> {
    @Override
    public Status executeTraced() {
        CSotaMotion motion = (CSotaMotion) getObject().data.get("BB_MOTION");
        int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
        motion.ServoOn();
        LoveLED.set(motion, loveLevel);
        WelcomeAction.run(motion, loveLevel);
        System.out.println("[WELCOME] Done. Love level: " + loveLevel);
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new WelcomeTask(); }
}


// PROMPT

class PromptTask extends LeafTaskTraced<Blackboard> {
    @Override
    public Status executeTraced() {
        CSotaMotion motion = (CSotaMotion) getObject().data.get("BB_MOTION");
        int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
        LoveLED.set(motion, loveLevel);
        TTSHelper.speak("Do you like a smart joke or a dad joke?");
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new PromptTask(); }
}


// LISTEN - kws for "dad" or "smart", 30s timeout fires pickup line

class ListenTask extends LeafTaskTraced<Blackboard> {

    private static PickupLineManager _pickup = null;
    private static PickupLineManager getPickup() {
        if (_pickup == null) _pickup = new PickupLineManager();
        return _pickup;
    }

    @Override
    public Status executeTraced() {
        PocketSphinx sphinx     = (PocketSphinx) getObject().data.get("BB_SPHINX");
        long         decoderPtr = (long)          getObject().data.get("BB_DECODER");
        CSotaMotion  motion     = (CSotaMotion)   getObject().data.get("BB_MOTION");

        String heard = "";
        while (heard.isEmpty()) {
            int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
            heard = SpeechHelper.listenForKeyword(motion, loveLevel, sphinx, decoderPtr, "dad", "smart", "like", "goodbye");
            if (heard.isEmpty()) {
                loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
                System.out.println("[LISTEN] Timeout - pickup line.");
                LoveLED.set(motion, loveLevel);
                getPickup().speakRandom(motion, loveLevel);
            }
        }

        getObject().data.put("BB_KEYWORD", heard);
        System.out.println("[LISTEN] Heard: " + heard);
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new ListenTask(); }
}

class IsDadCondition extends LeafTaskTraced<Blackboard> {
    @Override
    public Status executeTraced() {
        String heard = (String) getObject().data.getOrDefault("BB_KEYWORD", "");
        return heard.equals("dad") ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new IsDadCondition(); }
}

class IsSmartCondition extends LeafTaskTraced<Blackboard> {
    @Override
    public Status executeTraced() {
        String heard = (String) getObject().data.getOrDefault("BB_KEYWORD", "");
        return heard.equals("smart") ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new IsSmartCondition(); }
}

class IsLikeCondition extends LeafTaskTraced<Blackboard> {
    @Override
    public Status executeTraced() {
        String heard = (String) getObject().data.getOrDefault("BB_KEYWORD", "");
        return heard.equals("like") ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new IsLikeCondition(); }
}

class IsByeCondition extends LeafTaskTraced<Blackboard> {
    @Override
    public Status executeTraced() {
        String heard = (String) getObject().data.getOrDefault("BB_KEYWORD", "");
        return heard.equals("goodbye") ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new IsByeCondition(); }
}


// RESPONSE TASKS
// Every 3rd joke slot delivers pickup line + love level +1

class JokeLevelConfig {
    static final int MAX_LEVEL = 4;
}

class DadJokeTask extends LeafTaskTraced<Blackboard> {
    private static JokeManager _jokes = null;
    public static JokeManager get() {
        if (_jokes == null) _jokes = new JokeManager("../resources/dad_jokes.txt");
        return _jokes;
    }
    private static PickupLineManager _pickup = null;
    private static PickupLineManager getPickup() {
        if (_pickup == null) _pickup = new PickupLineManager();
        return _pickup;
    }

    @Override
    public Status executeTraced() {
        CSotaMotion motion = (CSotaMotion) getObject().data.get("BB_MOTION");
        getObject().data.put("BB_LAST_CATEGORY", "dad");
        int count = (int) getObject().data.getOrDefault("BB_JOKE_COUNT", 0);

        if (count > 0 && count % 3 == 0) {
            int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
            loveLevel = Math.min(loveLevel + 1, JokeLevelConfig.MAX_LEVEL);
            getObject().data.put("BB_LOVE_LEVEL", (float) loveLevel);
            System.out.println("[DAD] Pickup slot - love level now: " + loveLevel);
            LoveLED.set(motion, loveLevel);
            getPickup().speakRandom(motion, loveLevel);
        } else {
            int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
            LoveLED.set(motion, loveLevel);
            System.out.println("[DAD] Dad joke.");
            get().speakRandom(motion);
            DadJokeGestureAction.run(motion);
        }

        getObject().data.put("BB_JOKE_COUNT", count + 1);
        System.out.println("[DAD] Jokes told: " + (count + 1));
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new DadJokeTask(); }
}

class IntellectTask extends LeafTaskTraced<Blackboard> {
    private static JokeManager _jokes = null;
    public static JokeManager get() {
        if (_jokes == null) _jokes = new JokeManager("../resources/intellectual_puns.txt");
        return _jokes;
    }
    private static PickupLineManager _pickup = null;
    private static PickupLineManager getPickup() {
        if (_pickup == null) _pickup = new PickupLineManager();
        return _pickup;
    }

    @Override
    public Status executeTraced() {
        CSotaMotion motion = (CSotaMotion) getObject().data.get("BB_MOTION");
        getObject().data.put("BB_LAST_CATEGORY", "smart");
        int count = (int) getObject().data.getOrDefault("BB_JOKE_COUNT", 0);

        if (count > 0 && count % 3 == 0) {
            int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
            loveLevel = Math.min(loveLevel + 1, JokeLevelConfig.MAX_LEVEL);
            getObject().data.put("BB_LOVE_LEVEL", (float) loveLevel);
            System.out.println("[SMART] Pickup slot - love level now: " + loveLevel);
            LoveLED.set(motion, loveLevel);
            getPickup().speakRandom(motion, loveLevel);
        } else {
            int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);
            LoveLED.set(motion, loveLevel);
            System.out.println("[SMART] Smart pun.");
            get().speakRandom(motion);
            PunGestureAction.run(motion);
        }

        getObject().data.put("BB_JOKE_COUNT", count + 1);
        System.out.println("[SMART] Jokes told: " + (count + 1));
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new IntellectTask(); }
}

// LikeTask — pickup line + love level +1, then loops back to prompt
class LikeTask extends LeafTaskTraced<Blackboard> {
    private static PickupLineManager _pickup = null;
    private static PickupLineManager getPickup() {
        if (_pickup == null) _pickup = new PickupLineManager();
        return _pickup;
    }

    @Override
    public Status executeTraced() {
        CSotaMotion motion = (CSotaMotion) getObject().data.get("BB_MOTION");
        int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);

        // Increase love level by 1
        loveLevel = Math.min(loveLevel + 1, JokeLevelConfig.MAX_LEVEL);
        getObject().data.put("BB_LOVE_LEVEL", (float) loveLevel);
        System.out.println("[LIKE] Love level now: " + loveLevel);

        // Pickup line at new love level
        LoveLED.set(motion, loveLevel);
        getPickup().speakRandom(motion, loveLevel);

        return Status.SUCCEEDED; // tree continues → check-in → back to prompt on stop
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new LikeTask(); }
}


// CHECK-IN TASKS

class CheckInAskTask extends LeafTaskTraced<Blackboard> {
    static final String[] LINES = {
            "Do you want another one, or do you want me to stop?",
            "Want to hear another one, or should I stop?",
            "One more, or are you done?",
            "Keep going, or stop?",
    };
    @Override
    public Status executeTraced() {
        int index = (int) getObject().data.getOrDefault("BB_CHECKIN_INDEX", 0);
        TTSHelper.speak(LINES[index % LINES.length]);
        getObject().data.put("BB_CHECKIN_INDEX", index + 1);
        CRobotUtil.wait(1500);
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new CheckInAskTask(); }
}

class CheckInRouteTask extends LeafTaskTraced<Blackboard> {

    private static PickupLineManager _pickup = null;
    private static PickupLineManager getPickup() {
        if (_pickup == null) _pickup = new PickupLineManager();
        return _pickup;
    }

    @Override
    public Status executeTraced() {
        CSotaMotion  motion     = (CSotaMotion)  getObject().data.get("BB_MOTION");
        PocketSphinx sphinx     = (PocketSphinx) getObject().data.get("BB_SPHINX");
        long         kwsDecoder = (long)          getObject().data.get("BB_DECODER");
        int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);

        String heard = SpeechHelper.listenForKeyword(motion, loveLevel, sphinx, kwsDecoder,
                "again", "another", "more", "ok", "stop", "enough");
        System.out.println("[CHECK-IN] Heard: '" + heard + "'");

        if (heard.equals("again") || heard.equals("another") || heard.equals("more") || heard.equals("ok")) {
            String category = (String) getObject().data.getOrDefault("BB_LAST_CATEGORY", "dad");
            int count = (int) getObject().data.getOrDefault("BB_JOKE_COUNT", 0);

            if (count > 0 && count % 3 == 0) {
                loveLevel = Math.min(loveLevel + 1, JokeLevelConfig.MAX_LEVEL);
                getObject().data.put("BB_LOVE_LEVEL", (float) loveLevel);
                LoveLED.set(motion, loveLevel);
                getPickup().speakRandom(motion, loveLevel);
            } else if (category.equals("smart")) {
                LoveLED.set(motion, loveLevel);
                IntellectTask.get().speakRandom(motion);
                PunGestureAction.run(motion);
            } else {
                LoveLED.set(motion, loveLevel);
                DadJokeTask.get().speakRandom(motion);
                DadJokeGestureAction.run(motion);
            }
            count++;
            getObject().data.put("BB_JOKE_COUNT", count);
            System.out.println("[CHECK-IN] Jokes told: " + count);
            return Status.SUCCEEDED;

        } else if (heard.equals("stop") || heard.equals("enough")) {
            System.out.println("[CHECK-IN] Stop - back to prompt.");
            return Status.FAILED;

        } else {
            System.out.println("[CHECK-IN] Silence - pickup line.");
            CRobotUtil.wait(300);
            LoveLED.set(motion, loveLevel);
            getPickup().speakRandom(motion, loveLevel);
            return Status.FAILED;
        }
    }

    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new CheckInRouteTask(); }
}

// FAREWELL

class FarewellTask extends LeafTaskTraced<Blackboard> {
    static final String[] LINES = {
            "Goodbye! Come back soon.",
            "See you later! This was fun.",
            "Bye! I'll miss you.",
            "Farewell! Until next time.",
            "Later! Don't be a stranger.",
    };

    private static PickupLineManager _pickup = null;
    private static PickupLineManager getPickup() {
        if (_pickup == null) _pickup = new PickupLineManager();
        return _pickup;
    }

    @Override
    public Status executeTraced() {
        CSotaMotion motion = (CSotaMotion) getObject().data.get("BB_MOTION");
        int loveLevel = (int)(float)(Object) getObject().data.getOrDefault("BB_LOVE_LEVEL", 0.0f);

        // Random pickup line before leaving
        int randomLevel = (int)(Math.random() * (JokeLevelConfig.MAX_LEVEL + 1));
        LoveLED.set(motion, randomLevel);
        getPickup().speakRandom(motion, randomLevel);

        // Farewell line scaled to love level
        LoveLED.set(motion, loveLevel);
        TTSHelper.speak(LINES[loveLevel % LINES.length]);
        System.out.println("[FAREWELL] Done.");
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) { return new FarewellTask(); }
}
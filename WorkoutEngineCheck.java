import com.example.sportchrono.WorkoutEngine;
import com.example.sportchrono.WorkoutPlan;
import java.util.Arrays;

public class WorkoutEngineCheck {
    static class Events implements WorkoutEngine.Listener {
        int transitions, finishes, cues;
        public void transition(WorkoutEngine.Step step) { transitions++; }
        public void countdown(int seconds) { cues++; }
        public void finish(boolean completed) { finishes++; }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static WorkoutPlan plan(WorkoutPlan.Mode mode, int exercises) {
        WorkoutPlan p = new WorkoutPlan(); p.mode = mode; p.name = "Test";
        p.prepSec = 0; p.warmupSec = 0; p.cooldownSec = 0;
        p.defaultSets = 4; p.defaultCapSec = 30; p.restSeriesSec = 10; p.restExerciseSec = 20;
        for (int i = 0; i < exercises; i++) {
            WorkoutPlan.Exercise e = new WorkoutPlan.Exercise();
            e.name = i == 0 ? "Tractions" : i == 1 ? "Pompes" : "Exercice " + i;
            p.exercises.add(e);
        }
        return p;
    }
    public static void main(String[] args) {
        Events listener = new Events();
        WorkoutPlan p = plan(WorkoutPlan.Mode.STANDARD, 4);
        WorkoutEngine e = new WorkoutEngine(p, 0);
        check(e.step().kind == WorkoutEngine.Kind.WORK && e.step().series == 1, "first set");
        e.complete(5_000, 8, 0, 0, listener);
        check(e.step().kind == WorkoutEngine.Kind.REST_SERIES && e.displayMs(5_000) == 10_000,
                "early completion starts only set rest");
        e.tick(15_000, listener);
        check(e.step().series == 2 && e.step().exerciseIndex == 0, "next set");
        e.complete(17_000, 7, 0, 0, listener); e.tick(27_000, listener);
        e.complete(30_000, 6, 0, 0, listener); e.tick(40_000, listener);
        e.complete(42_000, 5, 0, 0, listener);
        check(e.step().kind == WorkoutEngine.Kind.REST_EXERCISE
                && e.displayMs(42_000) == 20_000, "no doubled rest after last set");
        e.tick(62_000, listener);
        check(e.step().exerciseIndex == 1 && e.step().series == 1, "next exercise");
        e.tick(92_000, listener);
        check(e.events.get(4).result == WorkoutEngine.Result.EXPIRED, "unconfirmed expiry");

        p = plan(WorkoutPlan.Mode.EMOM, 2); p.emomIntervalSec = 60; p.emomCycles = 3;
        e = new WorkoutEngine(p, 0); listener = new Events();
        e.complete(20_000, 5, 0, 0, listener);
        check(e.step().kind == WorkoutEngine.Kind.REST_EMOM && e.displayMs(20_000) == 40_000,
                "EMOM remainder");
        e.tick(60_000, listener);
        check(e.step().exerciseIndex == 1 && e.step().series == 2, "EMOM rotates tasks");
        e.tick(120_000, listener);
        check(e.events.get(1).result == WorkoutEngine.Result.EXPIRED
                && e.step().series == 3, "EMOM expired cycle recorded");

        p = plan(WorkoutPlan.Mode.PYRAMID, 1);
        check(p.pyramidTargets().equals(Arrays.asList(2,4,6,8,10,8,6,4,2)), "pyramid both");
        p.repeatPeak = true;
        check(p.pyramidTargets().equals(Arrays.asList(2,4,6,8,10,10,8,6,4,2)), "double peak");
        p.pyramidDirection = WorkoutPlan.PyramidDirection.DOWN;
        check(p.pyramidTargets().equals(Arrays.asList(10,8,6,4,2)), "descending");

        p = plan(WorkoutPlan.Mode.CIRCUIT, 2); p.circuitRounds = 2;
        e = new WorkoutEngine(p, 0); listener = new Events();
        e.complete(100, 2, 0, 0, listener);
        check(e.step().kind == WorkoutEngine.Kind.WORK && e.step().exerciseIndex == 1,
                "circuit does not pause between exercises");
        e.complete(200, 2, 0, 0, listener);
        check(e.step().kind == WorkoutEngine.Kind.REST_CIRCUIT, "circuit rest after round");

        p = plan(WorkoutPlan.Mode.AMRAP, 1); p.amrapDurationSec = 60; p.defaultCapSec = 0;
        e = new WorkoutEngine(p, 0); listener = new Events();
        e.complete(15_000, 8, 0, 0, listener);
        check(e.round() == 2, "AMRAP round increments");
        e.pause(20_000); e.resume(30_000);
        e.tick(70_000, listener);
        check(e.finished() && e.events.size() == 2 && e.events.get(1).result == WorkoutEngine.Result.EXPIRED,
                "AMRAP deadline remains after pause");

        p = plan(WorkoutPlan.Mode.STANDARD, 1);
        e = new WorkoutEngine(p, 0); e.pause(4_000);
        e = WorkoutEngine.restore(p, e.snapshot(), 8_000);
        check(e.paused() && e.displayMs(8_000) == 26_000, "restored paused timer");
        System.out.println("WorkoutEngine OK");
    }
}

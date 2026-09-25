package com.example.sportchrono;

import java.util.ArrayList;
import java.util.List;

/** Platform independent description of a user-created workout. */
public final class WorkoutPlan {
    public enum Mode { STANDARD, EMOM, PYRAMID, CIRCUIT, SUPERSET, AMRAP }
    public enum Load { NONE, ADDED, ASSISTED }
    public enum PyramidDirection { UP, DOWN, BOTH }

    public static final class Exercise {
        public String catalogId = "", name = "", category = "", notes = "";
        public int sets = -1, reps = -1, capSec = -1, restSec = -1;
        public boolean perSide;
        public Load load = Load.NONE;
        public double kilograms;
        public Exercise copy() {
            Exercise e = new Exercise();
            e.catalogId = catalogId; e.name = name; e.category = category; e.notes = notes;
            e.sets = sets; e.reps = reps; e.capSec = capSec; e.restSec = restSec;
            e.perSide = perSide; e.load = load; e.kilograms = kilograms;
            return e;
        }
    }

    public String id = "", name = "Ma séance";
    public Mode mode = Mode.STANDARD;
    public int prepSec = 5, warmupSec = 0, cooldownSec = 0;
    public int defaultSets = 4, defaultReps = 0, defaultCapSec = 30;
    public int restSeriesSec = 60, restExerciseSec = 120;
    public int emomIntervalSec = 60, emomCycles = 10;
    public int pyramidStart = 2, pyramidMax = 10, pyramidStep = 2, pyramidRestSec = 60;
    public PyramidDirection pyramidDirection = PyramidDirection.BOTH;
    public boolean repeatPeak;
    public int circuitRounds = 4, circuitRestSec = 90;
    public int amrapDurationSec = 600;
    public final List<Exercise> exercises = new ArrayList<>();

    public WorkoutPlan copy() {
        WorkoutPlan p = new WorkoutPlan();
        p.id = id; p.name = name; p.mode = mode;
        p.prepSec = prepSec; p.warmupSec = warmupSec; p.cooldownSec = cooldownSec;
        p.defaultSets = defaultSets; p.defaultReps = defaultReps; p.defaultCapSec = defaultCapSec;
        p.restSeriesSec = restSeriesSec; p.restExerciseSec = restExerciseSec;
        p.emomIntervalSec = emomIntervalSec; p.emomCycles = emomCycles;
        p.pyramidStart = pyramidStart; p.pyramidMax = pyramidMax; p.pyramidStep = pyramidStep;
        p.pyramidRestSec = pyramidRestSec; p.pyramidDirection = pyramidDirection;
        p.repeatPeak = repeatPeak; p.circuitRounds = circuitRounds;
        p.circuitRestSec = circuitRestSec; p.amrapDurationSec = amrapDurationSec;
        for (Exercise e : exercises) p.exercises.add(e.copy());
        return p;
    }
    public void validate() {
        if (name.trim().isEmpty() || exercises.isEmpty() || exercises.size() > 60)
            throw new IllegalArgumentException("Nommez la séance et ajoutez de 1 à 60 exercices.");
        if (prepSec < 0 || warmupSec < 0 || cooldownSec < 0 || defaultSets < 1
                || defaultSets > 100 || defaultReps < 0 || defaultCapSec < 0
                || restSeriesSec < 0 || restExerciseSec < 0)
            throw new IllegalArgumentException("Vérifiez les durées et les séries.");
        if (mode == Mode.EMOM && (emomIntervalSec < 5 || emomCycles < 1 || emomCycles > 300))
            throw new IllegalArgumentException("Vérifiez les intervalles EMOM.");
        if (mode == Mode.PYRAMID && (pyramidStart < 1 || pyramidStep < 1
                || pyramidMax < pyramidStart || (pyramidMax - pyramidStart) % pyramidStep != 0
                || pyramidMax > 1000))
            throw new IllegalArgumentException("Le maximum doit être atteint par des marches entières.");
        if ((mode == Mode.CIRCUIT || mode == Mode.SUPERSET)
                && (circuitRounds < 1 || circuitRounds > 100 || circuitRestSec < 0))
            throw new IllegalArgumentException("Vérifiez les tours et le repos du circuit.");
        if (mode == Mode.AMRAP && (amrapDurationSec < 5 || amrapDurationSec > 86_400))
            throw new IllegalArgumentException("Durée AMRAP invalide.");
        if (mode == Mode.SUPERSET && exercises.size() < 2)
            throw new IllegalArgumentException("Une supersérie comporte au moins deux exercices.");
        for (Exercise e : exercises) {
            if (e.name.trim().isEmpty() || e.sets == 0 || e.sets < -1 || e.reps < -1
                    || e.capSec < -1 || e.restSec < -1 || e.kilograms < 0 || e.kilograms > 1000)
                throw new IllegalArgumentException("Vérifiez les options de chaque exercice.");
        }
    }
    public List<Integer> pyramidTargets() {
        List<Integer> result = new ArrayList<>();
        if (pyramidDirection == PyramidDirection.DOWN) {
            for (int n = pyramidMax; n >= pyramidStart; n -= pyramidStep) result.add(n);
        } else {
            for (int n = pyramidStart; n <= pyramidMax; n += pyramidStep) result.add(n);
            if (pyramidDirection == PyramidDirection.BOTH) {
                if (repeatPeak) result.add(pyramidMax);
                for (int n = pyramidMax - pyramidStep; n >= pyramidStart; n -= pyramidStep)
                    result.add(n);
            }
        }
        return result;
    }
}

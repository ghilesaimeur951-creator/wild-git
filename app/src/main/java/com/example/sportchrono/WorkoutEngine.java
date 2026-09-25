package com.example.sportchrono;

import java.util.ArrayList;
import java.util.List;

/** Monotonic, Android-free state machine used by the foreground workout service. */
public final class WorkoutEngine {
    public enum Kind { PREPARE, WARMUP, WORK, REST_SERIES, REST_EXERCISE,
        REST_CIRCUIT, REST_EMOM, COOLDOWN }
    public enum Result { DONE, EXPIRED, SKIPPED }
    public static final class Step {
        public Kind kind;
        public int exerciseIndex = -1, series, seriesTotal, targetReps, cycle;
        public long durationMs;
        Step(Kind kind, long durationMs) { this.kind = kind; this.durationMs = durationMs; }
        public boolean isRest() {
            return kind == Kind.REST_SERIES || kind == Kind.REST_EXERCISE
                    || kind == Kind.REST_CIRCUIT || kind == Kind.REST_EMOM;
        }
    }
    public static final class Event {
        public int stepIndex, cycle, exerciseIndex, series, target, actual, left, right;
        public long elapsedMs;
        public Result result;
    }
    public static final class State {
        public int index, round;
        public long phaseStart, deadline, pausedAt, amrapEnd, emomBoundary;
        public boolean paused, finished, completed, specialCooldown;
        public final List<Event> events = new ArrayList<>();
    }
    public interface Listener {
        void transition(Step step);
        void countdown(int seconds);
        void finish(boolean completed);
    }
    public final WorkoutPlan plan;
    public final List<Step> steps = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();
    private int index, round = 1, lastCue = -1;
    private long phaseStart, deadline, pausedAt, amrapEnd, emomBoundary;
    private boolean paused, finished, completed, specialCooldown;

    public WorkoutEngine(WorkoutPlan source, long now) {
        source.validate();
        plan = source.copy();
        build();
        if (steps.isEmpty()) throw new IllegalArgumentException("Séance vide");
        startStep(0, now);
    }
    private void addTimed(Kind kind, int seconds) {
        if (seconds > 0) steps.add(new Step(kind, seconds * 1000L));
    }
    private int sets(WorkoutPlan.Exercise e) { return e.sets < 0 ? plan.defaultSets : e.sets; }
    private int reps(WorkoutPlan.Exercise e) { return e.reps < 0 ? plan.defaultReps : e.reps; }
    private int cap(WorkoutPlan.Exercise e) { return e.capSec < 0 ? plan.defaultCapSec : e.capSec; }
    private int rest(WorkoutPlan.Exercise e) { return e.restSec < 0 ? plan.restSeriesSec : e.restSec; }
    private void work(int exercise, int series, int total, int target, int seconds, int cycle) {
        Step s = new Step(Kind.WORK, seconds * 1000L);
        s.exerciseIndex = exercise; s.series = series; s.seriesTotal = total;
        s.targetReps = target; s.cycle = cycle; steps.add(s);
    }
    private void standardFrom(int start) {
        for (int i = start; i < plan.exercises.size(); i++) {
            WorkoutPlan.Exercise e = plan.exercises.get(i);
            int count = sets(e);
            for (int n = 1; n <= count; n++) {
                work(i, n, count, reps(e), cap(e), 1);
                if (n < count) addTimed(Kind.REST_SERIES, rest(e));
            }
            if (i + 1 < plan.exercises.size()) addTimed(Kind.REST_EXERCISE, plan.restExerciseSec);
        }
    }
    private void build() {
        addTimed(Kind.PREPARE, plan.prepSec);
        addTimed(Kind.WARMUP, plan.warmupSec);
        switch (plan.mode) {
            case STANDARD: standardFrom(0); break;
            case PYRAMID:
                List<Integer> targets = plan.pyramidTargets();
                WorkoutPlan.Exercise first = plan.exercises.get(0);
                for (int i = 0; i < targets.size(); i++) {
                    work(0, i + 1, targets.size(), targets.get(i), cap(first), 1);
                    if (i + 1 < targets.size()) addTimed(Kind.REST_SERIES, plan.pyramidRestSec);
                }
                if (plan.exercises.size() > 1) {
                    addTimed(Kind.REST_EXERCISE, plan.restExerciseSec);
                    standardFrom(1);
                }
                break;
            case EMOM:
                for (int i = 0; i < plan.emomCycles; i++) {
                    int e = i % plan.exercises.size();
                    work(e, i + 1, plan.emomCycles, reps(plan.exercises.get(e)), plan.emomIntervalSec, i + 1);
                    if (i + 1 < plan.emomCycles) steps.add(new Step(Kind.REST_EMOM, -2));
                }
                break;
            case CIRCUIT: case SUPERSET:
                int group = plan.mode == WorkoutPlan.Mode.SUPERSET ? 2 : plan.exercises.size();
                for (int begin = 0; begin < plan.exercises.size(); begin += group) {
                    int end = Math.min(begin + group, plan.exercises.size());
                    for (int round = 1; round <= plan.circuitRounds; round++) {
                        for (int i = begin; i < end; i++) {
                            WorkoutPlan.Exercise e = plan.exercises.get(i);
                            work(i, round, plan.circuitRounds, reps(e), cap(e), round);
                        }
                        if (round < plan.circuitRounds) addTimed(Kind.REST_CIRCUIT, plan.circuitRestSec);
                    }
                    if (end < plan.exercises.size()) addTimed(Kind.REST_EXERCISE, plan.restExerciseSec);
                }
                break;
            case AMRAP:
                for (int i = 0; i < plan.exercises.size(); i++) {
                    WorkoutPlan.Exercise e = plan.exercises.get(i);
                    work(i, 1, 1, reps(e), cap(e), 1);
                }
                break;
        }
        if (plan.mode != WorkoutPlan.Mode.AMRAP) addTimed(Kind.COOLDOWN, plan.cooldownSec);
    }
    private Step current() {
        return specialCooldown ? new Step(Kind.COOLDOWN, plan.cooldownSec * 1000L) : steps.get(index);
    }
    public Step step() { return finished ? null : current(); }
    public int index() { return index; }
    public int round() { return round; }
    public boolean paused() { return paused; }
    public boolean finished() { return finished; }
    public boolean completed() { return completed; }
    public long amrapRemaining(long now) {
        if (amrapEnd == 0) return plan.amrapDurationSec * 1000L;
        return Math.max(0, amrapEnd - (paused ? pausedAt : now));
    }
    public long displayMs(long now) {
        if (finished) return 0;
        long time = paused ? pausedAt : now;
        return deadline == 0 ? Math.max(0, time - phaseStart) : Math.max(0, deadline - time);
    }
    private void startStep(int next, long time) {
        index = next; specialCooldown = false;
        Step s = steps.get(index);
        phaseStart = time;
        deadline = s.kind == Kind.REST_EMOM ? emomBoundary
                : s.durationMs == 0 ? 0 : time + s.durationMs;
        if (plan.mode == WorkoutPlan.Mode.AMRAP && s.kind == Kind.WORK && amrapEnd == 0)
            amrapEnd = time + plan.amrapDurationSec * 1000L;
        if (s.kind == Kind.WORK && plan.mode == WorkoutPlan.Mode.EMOM) emomBoundary = deadline;
        lastCue = -1;
    }
    private void record(Result result, int actual, int left, int right, long time) {
        Step s = current();
        if (s.kind != Kind.WORK) return;
        Event e = new Event(); e.stepIndex = index;
        e.cycle = plan.mode == WorkoutPlan.Mode.AMRAP ? round : s.cycle;
        e.exerciseIndex = s.exerciseIndex; e.series = s.series;
        e.target = s.targetReps; e.actual = actual; e.left = left; e.right = right;
        e.result = result; e.elapsedMs = Math.max(0, time - phaseStart);
        events.add(e);
    }
    private void end(boolean success, Listener listener) {
        finished = true; completed = success; listener.finish(success);
    }
    private void next(long time, Listener listener) {
        if (specialCooldown) { end(true, listener); return; }
        int next = index + 1;
        if (plan.mode == WorkoutPlan.Mode.AMRAP && next >= steps.size()) {
            round++;
            for (next = 0; next < steps.size() && steps.get(next).kind != Kind.WORK; next++) { }
        }
        if (next >= steps.size()) { end(true, listener); return; }
        startStep(next, time); listener.transition(current());
    }
    private void endAmrap(long time, Listener listener) {
        if (current().kind == Kind.WORK) record(Result.EXPIRED, -1, -1, -1, time);
        if (plan.cooldownSec == 0) { end(true, listener); return; }
        specialCooldown = true; phaseStart = time; deadline = time + plan.cooldownSec * 1000L;
        lastCue = -1; listener.transition(current());
    }
    public void tick(long now, Listener listener) {
        if (finished || paused) return;
        int guard = 0;
        while (!finished && guard++ < 10_000) {
            if (plan.mode == WorkoutPlan.Mode.AMRAP && amrapEnd > 0 && now >= amrapEnd
                    && !specialCooldown) { endAmrap(amrapEnd, listener); continue; }
            if (deadline == 0 || now < deadline) break;
            long boundary = deadline;
            if (current().kind == Kind.WORK) record(Result.EXPIRED, -1, -1, -1, boundary);
            next(boundary, listener);
        }
        if (!finished && deadline > 0) {
            int seconds = (int) ((Math.max(0, deadline - now) + 999) / 1000);
            if (seconds >= 1 && seconds <= 5 && seconds != lastCue) {
                lastCue = seconds; listener.countdown(seconds);
            }
        }
    }
    public void complete(long now, int actual, int left, int right, Listener listener) {
        if (finished || paused || current().kind != Kind.WORK) return;
        record(Result.DONE, actual, left, right, now);
        next(now, listener);
    }
    public void skip(long now, Listener listener) {
        if (finished || paused || current().kind == Kind.REST_EMOM) return;
        if (current().kind == Kind.WORK) record(Result.SKIPPED, -1, -1, -1, now);
        next(now, listener);
    }
    public void previous(long now, Listener listener) {
        if (finished || events.isEmpty()) return;
        Event last = events.remove(events.size() - 1);
        if (plan.mode == WorkoutPlan.Mode.AMRAP) round = last.cycle;
        startStep(last.stepIndex, now);
        if (paused) { pausedAt = now; }
        listener.transition(current());
    }
    public void pause(long now) { if (!finished && !paused) { paused = true; pausedAt = now; } }
    public void resume(long now) {
        if (!paused || finished) return;
        long delta = now - pausedAt;
        phaseStart += delta;
        if (deadline > 0) deadline += delta;
        if (amrapEnd > 0) amrapEnd += delta;
        if (emomBoundary > 0) emomBoundary += delta;
        paused = false;
    }
    public void stop(Listener listener) { if (!finished) end(false, listener); }
    public State snapshot() {
        State s = new State(); s.index = index; s.round = round;
        s.phaseStart = phaseStart; s.deadline = deadline; s.pausedAt = pausedAt;
        s.amrapEnd = amrapEnd; s.emomBoundary = emomBoundary;
        s.paused = paused; s.finished = finished; s.completed = completed;
        s.specialCooldown = specialCooldown; s.events.addAll(events);
        return s;
    }
    public static WorkoutEngine restore(WorkoutPlan plan, State s, long now) {
        WorkoutEngine engine = new WorkoutEngine(plan, now);
        if (s.finished || s.index < 0 || s.index >= engine.steps.size() || s.round < 1)
            throw new IllegalArgumentException("État invalide");
        engine.index = s.index; engine.round = s.round;
        engine.phaseStart = s.phaseStart; engine.deadline = s.deadline;
        engine.pausedAt = s.pausedAt; engine.amrapEnd = s.amrapEnd;
        engine.emomBoundary = s.emomBoundary; engine.paused = s.paused;
        engine.specialCooldown = s.specialCooldown;
        engine.events.addAll(s.events); return engine;
    }
}

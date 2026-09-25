package com.example.sportchrono;

/** Time is supplied by the caller using a monotonic clock (elapsedRealtime on Android). */
public final class SessionEngine {
    public enum Mode { STOPWATCH, TIMER, INTERVAL }
    public enum Phase { PREPARE, WORK, REST, FINISHED }
    public interface Listener {
        void count(int seconds);
        void phaseChanged();
        void completed();
    }

    public final Mode mode;
    public Phase phase;
    public final int rounds;
    public int round = 1;
    public boolean paused;
    public boolean finished;
    private final long prepMs, workMs, restMs;
    private long phaseEnd, pauseAt, stopwatchStart;
    private int lastCount = -1;

    public static final class State {
        public String mode, phase;
        public int prepSeconds, workSeconds, restSeconds, rounds, round;
        public long phaseEnd, pauseAt, stopwatchStart;
        public boolean paused, finished;
    }

    public SessionEngine(Mode mode, int prepSeconds, int workSeconds, int restSeconds, int rounds, long now) {
        if (mode == null || prepSeconds < 0 || workSeconds < 1 || restSeconds < 0 || rounds < 1)
            throw new IllegalArgumentException("Durée ou nombre de tours invalide");
        this.mode = mode;
        this.rounds = mode == Mode.INTERVAL ? rounds : 1;
        prepMs = prepSeconds * 1000L;
        workMs = workSeconds * 1000L;
        restMs = restSeconds * 1000L;
        phase = mode == Mode.INTERVAL && prepMs > 0 ? Phase.PREPARE : Phase.WORK;
        phaseEnd = now + (phase == Phase.PREPARE ? prepMs : workMs);
        stopwatchStart = now;
    }

    public void tick(long now, Listener listener) {
        if (paused || finished || mode == Mode.STOPWATCH) return;
        // Retain the planned boundary so a delayed handler never lengthens a round.
        while (now >= phaseEnd && !finished) {
            if (mode == Mode.TIMER || (phase == Phase.REST && round == rounds)
                    || (phase == Phase.WORK && round == rounds && restMs == 0)) {
                phase = Phase.FINISHED;
                finished = true;
                listener.phaseChanged();
                listener.completed();
                return;
            }
            if (phase == Phase.PREPARE || phase == Phase.REST) {
                if (phase == Phase.REST) round++;
                phase = Phase.WORK;
                phaseEnd += workMs;
            } else if (restMs > 0) {
                phase = Phase.REST;
                phaseEnd += restMs;
            } else {
                round++;
                phase = Phase.WORK;
                phaseEnd += workMs;
            }
            lastCount = -1;
            listener.phaseChanged();
        }
        long seconds = (Math.max(0, phaseEnd - now) + 999L) / 1000L;
        if (seconds >= 1 && seconds <= 5 && lastCount != (int) seconds) {
            lastCount = (int) seconds;
            listener.count((int) seconds);
        }
    }

    public long displayMs(long now) {
        long instant = paused ? pauseAt : now;
        if (mode == Mode.STOPWATCH) return Math.max(0, instant - stopwatchStart);
        return finished ? 0 : Math.max(0, phaseEnd - instant);
    }

    public void pause(long now) {
        if (!paused && !finished) { pauseAt = now; paused = true; }
    }

    public void resume(long now) {
        if (paused && !finished) {
            long delta = now - pauseAt;
            phaseEnd += delta;
            stopwatchStart += delta;
            paused = false;
            lastCount = -1;
        }
    }

    public State snapshot() {
        State state = new State();
        state.mode = mode.name(); state.phase = phase.name();
        state.prepSeconds = (int) (prepMs / 1000);
        state.workSeconds = (int) (workMs / 1000);
        state.restSeconds = (int) (restMs / 1000);
        state.rounds = rounds; state.round = round;
        state.phaseEnd = phaseEnd; state.pauseAt = pauseAt;
        state.stopwatchStart = stopwatchStart;
        state.paused = paused; state.finished = finished;
        return state;
    }

    public static SessionEngine restore(State state, long now) {
        SessionEngine engine = new SessionEngine(Mode.valueOf(state.mode), state.prepSeconds,
                state.workSeconds, state.restSeconds, state.rounds, now);
        if (state.round < 1 || state.round > state.rounds || state.finished)
            throw new IllegalArgumentException("État de séance invalide");
        engine.phase = Phase.valueOf(state.phase);
        engine.round = state.round; engine.phaseEnd = state.phaseEnd;
        engine.pauseAt = state.pauseAt; engine.stopwatchStart = state.stopwatchStart;
        engine.paused = state.paused;
        return engine;
    }
}

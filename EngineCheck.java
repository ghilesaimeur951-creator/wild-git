import com.example.sportchrono.SessionEngine;

public class EngineCheck {
    static class Events implements SessionEngine.Listener {
        int counts, changes, completions;
        public void count(int seconds) { counts++; }
        public void phaseChanged() { changes++; }
        public void completed() { completions++; }
    }
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        Events e = new Events();
        SessionEngine s = new SessionEngine(SessionEngine.Mode.INTERVAL, 5, 30, 10, 4, 1000);
        for (long t = 1000; t <= 181000; t += 1000) s.tick(t, e);
        check(s.finished && s.round == 4 && e.completions == 1);
        check(e.counts == 45); // Preparation, four work phases and four rests.
        check(e.changes == 9); // prepare -> work, 4 rests, 3 later works, finish

        e = new Events();
        s = new SessionEngine(SessionEngine.Mode.TIMER, 0, 10, 0, 1, 0);
        s.tick(2000, e); s.pause(2000); s.tick(20000, e);
        check(s.displayMs(20000) == 8000);
        s.resume(20000); s.tick(28000, e);
        check(s.finished && e.completions == 1);

        s = new SessionEngine(SessionEngine.Mode.STOPWATCH, 0, 1, 0, 1, 0);
        check(s.displayMs(1234) == 1234);
        s.pause(1234); s.resume(9000);
        check(s.displayMs(10000) == 2234);
        SessionEngine restored = SessionEngine.restore(s.snapshot(), 10001);
        check(restored.displayMs(10000) == 2234);
        s = new SessionEngine(SessionEngine.Mode.INTERVAL, 5, 30, 10, 4, 100);
        s.tick(5100, e); s.pause(9000);
        restored = SessionEngine.restore(s.snapshot(), 20_000);
        check(restored.paused && restored.phase == SessionEngine.Phase.WORK
                && restored.displayMs(20_000) == 26_100);
        System.out.println("SessionEngine OK");
    }
}

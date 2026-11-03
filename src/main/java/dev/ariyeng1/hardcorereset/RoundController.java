package dev.ariyeng1.hardcorereset;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Server-thread state machine. Has no ability to stop a server or disconnect a player. */
public final class RoundController<W> {
    public enum Phase { PREPARING, ACTIVE, COUNTDOWN, FAILED }

    public interface Worlds<W> {
        CompletableFuture<W> prepare(boolean resume);
        void spectateEveryone();
        void activate(W world) throws Exception;
        void retirePrevious() throws Exception;
        void announce(String message);
        default void announce(String message, String died) { announce(message); }
        void reportFailure(Throwable error);
    }

    private final Worlds<W> worlds;
    private final LongSupplier nanoTime;
    private Phase phase = Phase.PREPARING;
    private CompletableFuture<W> pending;
    private boolean resume = true;
    private long deadline;
    private int announced;
    private String deathName = "";

    public RoundController(Worlds<W> worlds, LongSupplier nanoTime) {
        this.worlds = worlds;
        this.nanoTime = nanoTime;
    }

    public Phase phase() { return phase; }
    public boolean frozen() { return phase != Phase.ACTIVE; }

    public void start() {
        if (pending != null) throw new IllegalStateException("Already started");
        prepare();
    }

    public void playerDied(String name) {
        if (phase != Phase.ACTIVE) return;
        phase = Phase.COUNTDOWN;
        resume = false;
        deathName = name;
        deadline = nanoTime.getAsLong() + 10_000_000_000L;
        announced = 10;
        pending = null;
        try {
            worlds.spectateEveryone();
            worlds.announce(name + " died! New world in 10 seconds.", deathName);
            prepare(); // Generate during the countdown, without blocking ticks.
        } catch (Exception error) {
            fail(error);
        }
    }

    private void prepare() {
        pending = null;
        try {
            pending = worlds.prepare(resume);
        } catch (Exception error) {
            fail(error);
        }
    }

    public void tick() {
        if (phase == Phase.ACTIVE) return;
        try {
            worlds.spectateEveryone();
        } catch (Exception error) {
            if (phase != Phase.FAILED) fail(error);
            return;
        }
        if (phase == Phase.FAILED) return;
        if (phase == Phase.COUNTDOWN) {
            long remaining = deadline - nanoTime.getAsLong();
            if (remaining > 0) {
                int seconds = (int) Math.ceil(remaining / 1_000_000_000.0);
                if (seconds < announced) {
                    announced = seconds;
                    worlds.announce("New world in " + seconds + "...", deathName);
                }
                return;
            }
            phase = Phase.PREPARING;
            worlds.announce("Moving to the new world. Stay connected...", deathName);
        }
        if (pending == null || !pending.isDone()) return;
        try {
            worlds.activate(pending.join());
            phase = Phase.ACTIVE;
            worlds.announce("A new run has begun!", deathName);
        } catch (Exception error) {
            fail(error);
            return;
        }
        // Cleanup failure must not strand players after a successful transfer.
        try {
            worlds.retirePrevious();
        } catch (Exception error) {
            worlds.reportFailure(error);
        }
    }

    private void fail(Throwable error) {
        phase = Phase.FAILED;
        worlds.reportFailure(error);
        worlds.announce("World reset paused. Players remain connected as spectators. "
                + "An operator can use /hardcorereset retry after checking the log.");
    }

    public void pause(Throwable error) { fail(error); }

    public boolean retry() {
        if (phase != Phase.FAILED) return false;
        phase = Phase.PREPARING;
        if (pending == null || pending.isCompletedExceptionally()) prepare();
        return true;
    }
}

package dev.ariyeng1.hardcorereset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Runs without Minecraft; assertions are explicit and cannot be disabled with JVM flags. */
public final class LiveResetTest {
    private static int passed;
    private static final class Worlds implements RoundController.Worlds<String> {
        CompletableFuture<String> future = CompletableFuture.completedFuture("first");
        List<String> calls = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        List<String> deathNames = new ArrayList<>();
        int freezes;
        int failures;
        boolean failTransfer;
        boolean failCleanup;
        boolean throwPrepare;
        boolean failFreeze;
        public CompletableFuture<String> prepare(boolean resume) {
            calls.add("prepare:" + resume);
            if (throwPrepare) throw new IllegalStateException("prepare failed");
            return future;
        }
        public void spectateEveryone() {
            freezes++;
            if (failFreeze) throw new IllegalStateException("spectator switch failed");
        }
        public void activate(String world) {
            calls.add("transfer:" + world);
            if (failTransfer) throw new IllegalStateException("transfer failed");
        }
        public void retirePrevious() {
            calls.add("retire");
            if (failCleanup) throw new IllegalStateException("cleanup failed");
        }
        public void announce(String text) { messages.add(text); }
        public void announce(String text, String died) { messages.add(text); deathNames.add(died); }
        public void reportFailure(Throwable error) { failures++; }
    }
    private record Fixture(Worlds worlds, AtomicLong clock, RoundController<String> controller) {}
    private static Fixture active() {
        Worlds worlds = new Worlds();
        AtomicLong time = new AtomicLong();
        var controller = new RoundController<String>(worlds, time::get);
        controller.start();
        controller.tick();
        check(controller.phase() == RoundController.Phase.ACTIVE, "initial world active");
        worlds.calls.clear();
        worlds.messages.clear();
        return new Fixture(worlds, time, controller);
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void test(String name, Runnable test) {
        test.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        test("no early transfer; prepare during countdown; transfer before retirement", () -> {
            var f = active();
            f.worlds.future = CompletableFuture.completedFuture("second");
            f.controller.playerDied("Alex");
            check(f.controller.frozen(), "everyone frozen immediately");
            check(f.worlds.calls.equals(List.of("prepare:false")), "generation starts immediately");
            f.clock.set(9_999_999_999L); f.controller.tick();
            check(f.worlds.calls.size() == 1, "no early transfer");
            f.clock.set(10_000_000_000L); f.controller.tick();
            check(f.worlds.calls.equals(List.of("prepare:false", "transfer:second", "retire")), "ordered handoff");
            check(!f.controller.frozen(), "new run playable");
        });
        test("countdown messages 10 through 1 and duplicate deaths do not extend it", () -> {
            var f = active(); f.controller.playerDied("Alex");
            for (int i = 1; i <= 9; i++) {
                f.clock.set(i * 1_000_000_000L);
                f.controller.playerDied("Sam"); f.controller.tick();
            }
            check(f.worlds.messages.size() == 10, "exactly ten countdown messages");
            check(f.worlds.deathNames.stream().filter(n -> !n.isBlank()).allMatch("Alex"::equals), "original username retained throughout countdown");
            check(f.worlds.messages.getFirst().contains("10 seconds"), "starts at ten");
            check(f.worlds.messages.getLast().contains("1..."), "ends at one");
            check(f.worlds.calls.equals(List.of("prepare:false")), "no duplicate generation");
            f.clock.set(10_000_000_000L); f.controller.tick();
            check(!f.controller.frozen(), "original deadline kept");
        });
        test("slow generation keeps players spectating without blocking ticks", () -> {
            var f = active(); f.worlds.future = new CompletableFuture<>();
            f.controller.playerDied("Alex");
            f.clock.set(11_000_000_000L); f.controller.tick();
            check(f.controller.phase() == RoundController.Phase.PREPARING, "waits for chunks");
            check(!f.worlds.calls.contains("retire"), "old world retained");
            f.worlds.future.complete("ready"); f.controller.tick();
            check(!f.controller.frozen(), "continues once ready");
        });
        test("players joining during countdown and preparation stay spectating", () -> {
            var f = active(); f.worlds.future = new CompletableFuture<>();
            f.controller.playerDied("Alex");
            int before = f.worlds.freezes;
            f.controller.tick(); f.clock.set(12_000_000_000L); f.controller.tick();
            check(f.worlds.freezes == before + 2, "spectators enforced each tick");
        });
        test("generation failure retains old world and retry prepares a new candidate", () -> {
            var f = active(); f.worlds.future = CompletableFuture.failedFuture(new Exception("chunks"));
            f.controller.playerDied("Alex"); f.clock.set(10_000_000_000L); f.controller.tick();
            check(f.controller.phase() == RoundController.Phase.FAILED, "failure isolated");
            check(!f.worlds.calls.contains("retire"), "no world deletion");
            f.worlds.future = CompletableFuture.completedFuture("retry");
            check(f.controller.retry(), "retry accepted"); f.controller.tick();
            check(!f.controller.frozen(), "retry succeeds");
        });
        test("transfer failure never retires old worlds; retry uses same prepared world", () -> {
            var f = active(); f.worlds.failTransfer = true;
            f.controller.playerDied("Alex"); f.clock.set(10_000_000_000L); f.controller.tick();
            check(f.controller.frozen() && !f.worlds.calls.contains("retire"), "retains worlds");
            long preparations = f.worlds.calls.stream().filter(c -> c.startsWith("prepare")).count();
            f.worlds.failTransfer = false; f.controller.retry(); f.controller.tick();
            check(f.worlds.calls.stream().filter(c -> c.startsWith("prepare")).count() == preparations,
                    "partial handoff uses same world on retry");
        });
        test("synchronous generation error cannot reuse the previous run on retry", () -> {
            var f = active(); f.worlds.throwPrepare = true; f.controller.playerDied("Alex");
            check(f.controller.phase() == RoundController.Phase.FAILED, "error captured");
            f.worlds.throwPrepare = false; f.worlds.future = CompletableFuture.completedFuture("new");
            f.controller.retry(); f.controller.tick();
            check(f.worlds.calls.contains("transfer:new"), "new run prepared");
        });
        test("cleanup failure keeps successfully transferred players playing", () -> {
            var f = active(); f.worlds.failCleanup = true;
            f.controller.playerDied("Alex"); f.clock.set(10_000_000_000L); f.controller.tick();
            check(f.controller.phase() == RoundController.Phase.ACTIVE, "cleanup does not freeze new run");
            check(f.worlds.failures == 1, "cleanup failure reported");
        });
        test("spectator errors pause the reset instead of escaping the server tick", () -> {
            var f = active(); f.worlds.failFreeze = true;
            f.controller.playerDied("Alex");
            check(f.controller.phase() == RoundController.Phase.FAILED, "failure caught in death event");
            f.controller.tick();
            check(f.worlds.failures == 1, "tick does not throw or repeatedly announce the error");
            f.worlds.failFreeze = false; f.controller.retry(); f.controller.tick();
            check(!f.controller.frozen(), "recovery succeeds without stopping server");
        });
        test("active tick does not restart or refreeze; retry outside failure is rejected", () -> {
            var f = active(); int before = f.worlds.freezes;
            for (int i = 0; i < 100; i++) f.controller.tick();
            check(f.worlds.calls.isEmpty() && f.worlds.freezes == before, "active run undisturbed");
            check(!f.controller.retry(), "retry rejected");
        });
        test("second death after a successful reset starts another independent run", () -> {
            var f = active(); f.controller.playerDied("Alex");
            f.clock.set(10_000_000_000L); f.controller.tick();
            f.worlds.calls.clear(); f.controller.playerDied("Sam");
            f.clock.set(20_000_000_000L); f.controller.tick();
            check(f.worlds.calls.equals(List.of("prepare:false", "transfer:first", "retire")), "second reset works");
        });
        Path temporary = Files.createTempDirectory("hardcorereset-state-test-");
        try {
            Path state = temporary.resolve("nested/run.properties");
            RunState first = RunState.readOrCreate(state);
            first.save(state);
            check(RunState.readOrCreate(state).equals(first), "ordinary server restart resumes run");
            passed++; System.out.println("PASS active run seed and ID survive restart");
            RunState second = RunState.fresh(); second.save(state);
            check(RunState.readOrCreate(state).equals(second), "atomic replacement updates run");
            check(!first.id().equals(second.id()), "unique world folder per run");
            passed++; System.out.println("PASS new run atomically replaces active manifest");
            Files.writeString(state, "id=broken\nseed=bad\n");
            try { RunState.readOrCreate(state); throw new AssertionError("corrupt state accepted"); }
            catch (java.io.IOException expected) { passed++; System.out.println("PASS corrupt state cannot silently reset worlds"); }
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println(passed + " live-reset controller/state tests passed.");
    }
}

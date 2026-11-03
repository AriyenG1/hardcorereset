import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ariyeng1.hardcorereset.NextSeedCommand;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real Minecraft permission checks and seed parsing without starting a server. */
public final class NextSeedCommandTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var accepted = new ArrayList<Long>();
        var active = new AtomicBoolean(true);
        NextSeedCommand.register(dispatcher, seed -> {
            if (!active.get()) return false;
            accepted.add(seed);
            return true;
        });
        var ordinary = Commands.createCompilationContext(LevelBasedPermissionSet.ALL);
        try {
            dispatcher.execute("nextseed 123", ordinary);
            throw new AssertionError("Non-operator was allowed to queue a seed");
        } catch (CommandSyntaxException expected) {
            check(accepted.isEmpty(), "Denied command did not change the seed");
        }
        for (var level : PermissionLevel.values()) {
            if (level == PermissionLevel.ALL) continue;
            var operator = Commands.createCompilationContext(LevelBasedPermissionSet.forLevel(level));
            check(dispatcher.execute("nextseed 123", operator) == 1, "Operator level " + level + " is allowed");
        }
        System.out.println("PASS ordinary players denied; all four operator levels allowed");

        var operator = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER);
        accepted.clear();
        for (long seed : new long[]{0L, -123L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            check(dispatcher.execute("nextseed " + seed, operator) == 1, "Numeric seed accepted");
            check(accepted.getLast() == seed, "Numeric seed parsed exactly");
        }
        check(dispatcher.execute("nextseed a forest seed", operator) == 1, "Text seed accepted");
        check(accepted.getLast() == (long) "a forest seed".hashCode(), "Vanilla text seed hashing used");
        System.out.println("PASS zero, negative, signed-long limits, and text seeds parsed correctly");

        accepted.clear();
        try {
            dispatcher.execute("nextseed", operator);
            throw new AssertionError("Missing seed was accepted");
        } catch (CommandSyntaxException expected) {
            check(accepted.isEmpty(), "Missing argument did not queue a seed");
        }
        check(dispatcher.execute("nextseed    ", operator) == 0, "Blank seed rejected");
        check(accepted.isEmpty(), "Blank seed did not queue a seed");
        System.out.println("PASS missing and blank seeds rejected without changing queued seed");

        active.set(false);
        check(dispatcher.execute("nextseed 456", operator) == 0, "Busy reset reported failure");
        check(accepted.isEmpty(), "Busy reset did not queue a seed");
        System.out.println("PASS seed changes rejected while reset is busy");
        System.out.println("4 Minecraft command integration checks passed.");
    }
}

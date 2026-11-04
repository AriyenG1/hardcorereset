package dev.ariyeng1.hardcorereset;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import java.util.function.Function;

/** Uses vanilla deathCount, which survives run resets independently of player statistics. */
public final class DeathScoreboard {
    public static final String ID = "hardcorereset_deaths";
    private DeathScoreboard() {}

    public static Objective ensure(Scoreboard board) {
        Objective objective = board.getObjective(ID);
        if (objective == null) {
            objective = board.addObjective(ID, ObjectiveCriteria.DEATH_COUNT, Component.literal("Deaths"),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
            // Avoid replacing another mod's sidebar. Show automatically only on first creation.
            if (board.getDisplayObjective(DisplaySlot.SIDEBAR) == null)
                board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }
        if (objective.getCriteria() != ObjectiveCriteria.DEATH_COUNT)
            throw new IllegalStateException("Reserved death objective has the wrong criterion: " + ID);
        return objective;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, source -> source.getServer().getScoreboard());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Function<CommandSourceStack, Scoreboard> boards) {
        var root = Commands.literal("scoreboard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        root.then(Commands.literal("show").executes(c -> {
            var b = boards.apply(c.getSource()); b.setDisplayObjective(DisplaySlot.SIDEBAR, ensure(b));
            return reply(c.getSource(), "Death counter shown.");
        }));
        root.then(Commands.literal("hide").executes(c -> {
            var b = boards.apply(c.getSource()); var objective = ensure(b);
            if (b.getDisplayObjective(DisplaySlot.SIDEBAR) == objective) b.setDisplayObjective(DisplaySlot.SIDEBAR, null);
            return reply(c.getSource(), "Death counter hidden; deaths are still tracked.");
        }));
        root.then(Commands.literal("reset").executes(c -> {
            var b = boards.apply(c.getSource()); var o = ensure(b);
            for (var entry : b.listPlayerScores(o)) b.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.owner()), o).set(0);
            return reply(c.getSource(), "All tracked death counts reset to zero, including offline players.");
        }).then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
            var b = boards.apply(c.getSource());
            b.getOrCreatePlayerScore(ScoreHolder.forNameOnly(StringArgumentType.getString(c, "player")), ensure(b)).set(0);
            return reply(c.getSource(), "Player death count reset.");
        })));
        root.then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("count", IntegerArgumentType.integer(0)).executes(c -> {
                    var b = boards.apply(c.getSource());
                    b.getOrCreatePlayerScore(ScoreHolder.forNameOnly(StringArgumentType.getString(c, "player")), ensure(b))
                            .set(IntegerArgumentType.getInteger(c, "count"));
                    return reply(c.getSource(), "Player death count updated.");
                }))));
        root.then(Commands.literal("get").then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
            var b = boards.apply(c.getSource()); String name = StringArgumentType.getString(c, "player");
            var info = b.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), ensure(b));
            return reply(c.getSource(), name + ": " + (info == null ? 0 : info.value()) + " deaths");
        })));
        root.then(Commands.literal("list").executes(c -> {
            var b = boards.apply(c.getSource()); var entries = b.listPlayerScores(ensure(b));
            if (entries.isEmpty()) return reply(c.getSource(), "No deaths tracked yet.");
            for (var entry : entries) reply(c.getSource(), entry.owner() + ": " + entry.value());
            return entries.size();
        }));
        root.then(Commands.literal("help").executes(c -> reply(c.getSource(),
                "/scoreboard show | hide | reset [player] | get <player> | set <player> <count> | list. These shortcuts manage only Hardcore Reset deaths.")));
        dispatcher.register(root); // Brigadier merges these shortcuts with the vanilla subtree.
    }

    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}

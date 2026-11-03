package dev.ariyeng1.hardcorereset;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldOptions;

import java.util.function.LongPredicate;

/** Uses vanilla operator permissions and world-creation seed parsing. */
public final class NextSeedCommand {
    private NextSeedCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, LongPredicate queueSeed) {
        dispatcher.register(Commands.literal("nextseed")
                .requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
                .then(Commands.argument("seed", StringArgumentType.greedyString()).executes(context -> {
                    var seed = WorldOptions.parseSeed(StringArgumentType.getString(context, "seed"));
                    if (seed.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Enter a numeric or text seed."));
                        return 0;
                    }
                    if (!queueSeed.test(seed.getAsLong())) {
                        context.getSource().sendFailure(Component.literal(
                                "Set the next seed during an active run, before the reset countdown starts."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Next map seed set to " + seed.getAsLong() + ". Later maps will use random seeds again."), false);
                    return 1;
                })));
    }
}

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ariyeng1.hardcorereset.DeathScoreboard;
import net.minecraft.SharedConstants;
import net.minecraft.commands.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class DeathScoreboardTest {
    static void check(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var board = new Scoreboard(); var objective = DeathScoreboard.ensure(board);
        var alice = ScoreHolder.forNameOnly("Alice"); var bob = ScoreHolder.forNameOnly("OfflineBob");
        var other = board.addObjective("other", ObjectiveCriteria.DUMMY, net.minecraft.network.chat.Component.literal("Other"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        board.getOrCreatePlayerScore(alice, other).set(99);
        board.forAllObjectives(ObjectiveCriteria.DEATH_COUNT, alice, ScoreAccess::increment);
        check(board.getPlayerScoreInfo(alice, objective).value() == 1, "Vanilla death increments counter once");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("scoreboard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.literal("objectives").executes(c -> 42)));
        DeathScoreboard.register(dispatcher, s -> board);
        var op = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER);
        var ordinary = Commands.createCompilationContext(LevelBasedPermissionSet.ALL);
        try { dispatcher.execute("scoreboard reset", ordinary); throw new AssertionError("Permission bypass"); }
        catch (CommandSyntaxException expected) {}
        check(dispatcher.execute("scoreboard objectives", op)==42, "Vanilla subtree preserved");
        dispatcher.execute("scoreboard set OfflineBob 5", op);
        dispatcher.execute("scoreboard reset Alice", op);
        check(board.getPlayerScoreInfo(bob, objective).value()==5, "Single reset preserves other players");
        dispatcher.execute("scoreboard hide", op); DeathScoreboard.ensure(board);
        check(board.getDisplayObjective(DisplaySlot.SIDEBAR)==null,"Hide survives repeated initialization");
        board.forAllObjectives(ObjectiveCriteria.DEATH_COUNT, alice, ScoreAccess::increment);
        check(board.getPlayerScoreInfo(alice, objective).value()==1,"Hidden counter still tracks deaths");
        dispatcher.execute("scoreboard show", op);
        check(board.getDisplayObjective(DisplaySlot.SIDEBAR)==objective,"Show selects counter");
        dispatcher.execute("scoreboard reset", op);
        check(board.getPlayerScoreInfo(alice, objective).value()==0 && board.getPlayerScoreInfo(bob, objective).value()==0,"All reset includes offline scores");
        check(board.getPlayerScoreInfo(alice, other).value()==99,"Other objectives unchanged");
        try { dispatcher.execute("scoreboard set Alice -1", op); throw new AssertionError("Negative allowed"); }
        catch (CommandSyntaxException expected) {}
        dispatcher.execute("scoreboard get Alice", op); dispatcher.execute("scoreboard list", op); dispatcher.execute("scoreboard help", op);
        board.setDisplayObjective(DisplaySlot.SIDEBAR, other); dispatcher.execute("scoreboard hide", op);
        check(board.getDisplayObjective(DisplaySlot.SIDEBAR)==other,"Hide preserves unrelated sidebar");
        System.out.println("PASS death counting, permissions, vanilla command merging, offline/per-player reset, hidden counting, other-objective isolation, negative input and command execution");
    }
}

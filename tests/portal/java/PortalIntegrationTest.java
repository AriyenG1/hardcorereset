import dev.ariyeng1.hardcorereset.HardcoreReset;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only mod. Never packaged in the release; uses a disposable localhost server. */
public final class PortalIntegrationTest implements ModInitializer {
    private boolean finished;
    private int ticks;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) return;
            try {
                if (++ticks > 2400) throw new AssertionError("Run did not activate within 2400 ticks");
                for (ServerLevel level : server.getAllLevels()) {
                    var worlds = HardcoreReset.worldsFor(level);
                    if (worlds == null) continue;
                    finished = true;
                    var overworld = server.getLevel(worlds.remap(Level.OVERWORLD));
                    var nether = server.getLevel(worlds.remap(Level.NETHER));
                    var end = server.getLevel(worlds.remap(Level.END));
                    testIgnition(overworld, true);
                    testIgnition(nether, true);
                    testIgnition(end, false);
                    testIgnition(server.overworld(), true);
                    testIgnition(server.getLevel(Level.NETHER), true);
                    testIgnition(server.getLevel(Level.END), false);
                    var unrelated = Fantasy.get(server).openTemporaryLevel(new RuntimeLevelConfig()
                            .setDimensionType(server.overworld().dimensionTypeRegistration())
                            .setGenerator(server.overworld().getChunkSource().getGenerator()).setSeed(1));
                    testIgnition(unrelated.asLevel(), false);
                    testRoundTrip(overworld, nether);
                    testRoundTrip(server.overworld(), server.getLevel(Level.NETHER));
                    Files.writeString(Path.of("portal-test-passed.txt"), "PASS portal ignition and round-trip travel\n");
                    System.out.println("PASS all portal integration checks");
                    server.halt(false);
                    break;
                }
            } catch (Throwable failure) {
                finished = true;
                failure.printStackTrace();
                server.halt(false);
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static BlockPos at(BlockPos origin, int width, int height, boolean alongX) {
        return origin.offset(alongX ? width : 0, height, alongX ? 0 : width);
    }

    private static void frame(ServerLevel level, BlockPos origin, boolean alongX, boolean valid) {
        for (int x = -1; x <= 4; x++) for (int y = -1; y <= 5; y++) {
            boolean border = x >= 0 && x <= 3 && y >= 0 && y <= 4
                    && (x == 0 || x == 3 || y == 0 || y == 4);
            level.setBlockAndUpdate(at(origin, x, y, alongX),
                    (border ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState());
        }
        if (!valid) level.setBlockAndUpdate(at(origin, 1, 4, alongX), Blocks.AIR.defaultBlockState());
    }

    private static int portalBlocks(ServerLevel level, BlockPos origin, boolean alongX) {
        int count = 0;
        for (int x = 1; x <= 2; x++) for (int y = 1; y <= 3; y++) {
            if (level.getBlockState(at(origin, x, y, alongX)).is(Blocks.NETHER_PORTAL)) count++;
        }
        return count;
    }

    private static void ignite(ServerLevel level, BlockPos origin, boolean alongX, Item item, boolean side) {
        // The upper side click has no solid floor: it exercises canBePlacedAt's portal branch too.
        BlockPos clicked = at(origin, side ? 0 : 1, side ? 3 : 0, alongX);
        Direction face = side ? (alongX ? Direction.EAST : Direction.SOUTH) : Direction.UP;
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
        item.useOn(new UseOnContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(item), hit) {
            @Override public Direction getHorizontalDirection() { return alongX ? Direction.NORTH : Direction.EAST; }
        });
    }

    private static void testIgnition(ServerLevel level, boolean allowed) {
        BlockPos origin = new BlockPos(160, 80, 0);
        for (boolean alongX : new boolean[]{true, false}) {
            for (Item item : new Item[]{Items.FLINT_AND_STEEL, Items.FIRE_CHARGE}) {
                for (boolean side : new boolean[]{false, true}) {
                    frame(level, origin, alongX, true);
                    ignite(level, origin, alongX, item, side);
                    check(portalBlocks(level, origin, alongX) == (allowed ? 6 : 0),
                            "Ignition " + level.dimension().identifier() + " " + item + " X=" + alongX + " side=" + side);
                }
                frame(level, origin, alongX, false);
                ignite(level, origin, alongX, item, false);
                check(portalBlocks(level, origin, alongX) == 0, "Broken frame must not ignite");
            }
        }
        var floor = origin.offset(8, 0, 8);
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
        check(BaseFireBlock.canBePlacedAt(level, floor.above(), Direction.NORTH), "Normal fire placement remains allowed");
        level.setBlockAndUpdate(floor.above(), BaseFireBlock.getState(level, floor.above()));
        check(level.getBlockState(floor.above()).is(Blocks.FIRE), "Normal fire remains fire");
        System.out.println("PASS ignition/invalid frames/ordinary fire in " + level.dimension().identifier());
    }

    private static void testRoundTrip(ServerLevel overworld, ServerLevel nether) {
        BlockPos ow = new BlockPos(160, 80, 0), ne = new BlockPos(20, 80, 0);
        frame(overworld, ow, true, true);
        frame(nether, ne, true, true);
        ignite(overworld, ow, true, Items.FLINT_AND_STEEL, false);
        ignite(nether, ne, true, Items.FIRE_CHARGE, false);
        var pig = EntityTypes.PIG.create(overworld, EntitySpawnReason.COMMAND);
        check(pig != null, "Test pig created");
        pig.setPos(Vec3.atBottomCenterOf(ow.offset(1, 1, 0)));
        overworld.addFreshEntity(pig);
        var portal = (NetherPortalBlock) Blocks.NETHER_PORTAL;
        var outward = portal.getPortalDestination(overworld, pig, ow.offset(1, 1, 0));
        check(outward != null && outward.newLevel() == nether, "Travel must target paired Nether");
        check(Math.abs(outward.position().x - 21) < 4, "Overworld to Nether must scale by 1/8");
        var traveler = pig.teleport(outward);
        check(traveler != null && traveler.level() == nether, "Entity actually transfers to Nether");
        var returning = portal.getPortalDestination(nether, traveler, ne.offset(1, 1, 0));
        check(returning != null && returning.newLevel() == overworld, "Return must target paired Overworld");
        check(Math.abs(returning.position().x - 161) < 4, "Nether to Overworld must scale by 8");
        var returned = traveler.teleport(returning);
        check(returned != null && returned.level() == overworld, "Entity actually returns to Overworld");
        returned.discard();
        System.out.println("PASS scaled round-trip travel " + overworld.dimension().identifier());
    }
}

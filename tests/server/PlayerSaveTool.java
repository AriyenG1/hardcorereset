import java.nio.file.*;
import net.minecraft.nbt.*;

/** Reads and deliberately corrupts only a disposable test player's save. */
public final class PlayerSaveTool {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[1]);
        CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        if (args[0].equals("corrupt")) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".regression-backup"), StandardCopyOption.REPLACE_EXISTING);
            tag.putFloat("Health", 0);
            tag.putInt("playerGameType", 3);
            tag.putShort("DeathTime", (short) 20);
            tag.putString("Dimension", "hardcorereset:run_00000000-0000-0000-0000-000000000001/overworld");
            NbtIo.writeCompressed(tag, path);
        }
        System.out.println("FILE " + path.getFileName() + " HEALTH=" + tag.getFloatOr("Health", -1)
                + " MODE=" + tag.getIntOr("playerGameType", -1) + " DIMENSION=" + tag.getStringOr("Dimension", "missing")
                + " POS=" + tag.get("Pos"));
    }
}

package de.buildai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructurePlacer {
    public record Result(String name, int placed, int skipped) {}
    private record Snapshot(ResourceKey<Level> dim, Map<BlockPos, BlockState> old) {}
    private record Op(String type, int[] min, int[] max, String block) {}

    private static final Map<UUID, Deque<Snapshot>> HISTORY = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 10;

    /** Muss auf dem Server-Thread laufen. */
    public static Result place(ServerLevel level, UUID owner, BlockPos origin, Direction facing, JsonObject plan) {
        AIConfig cfg = AIConfig.get();
        JsonArray ops = plan.getAsJsonArray("operations");
        if (ops == null || ops.isEmpty()) throw new IllegalArgumentException("Leerer Bauplan.");

        // 1) Plan parsen und validieren
        List<Op> parsed = new ArrayList<>();
        long total = 0;
        for (JsonElement el : ops) {
            JsonObject o = el.getAsJsonObject();
            String type = o.get("op").getAsString();
            String block = o.get("block").getAsString();
            int[] a, b;
            if (type.equals("set")) {
                a = toArr(o.getAsJsonArray("pos"));
                b = a;
            } else if (type.equals("fill") || type.equals("hollow")) {
                a = toArr(o.getAsJsonArray("from"));
                b = toArr(o.getAsJsonArray("to"));
            } else continue;
            int[] min = new int[3], max = new int[3];
            long vol = 1;
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(a[i], b[i]);
                max[i] = Math.max(a[i], b[i]);
                int len = max[i] - min[i] + 1;
                if (len > cfg.maxSize) throw new IllegalArgumentException("Operation zu gross (" + len + " > " + cfg.maxSize + ").");
                vol *= len;
            }
            total += vol;
            parsed.add(new Op(type, min, max, block));
        }
        if (total > cfg.maxBlocks) throw new IllegalArgumentException("Bauplan zu gross (" + total + " > " + cfg.maxBlocks + " Bloecke).");

        // 2) Platzieren
        Rotation rot = rotationFor(facing);
        Map<BlockPos, BlockState> old = new LinkedHashMap<>();
        Map<String, Optional<BlockState>> cache = new HashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        int placed = 0, skipped = 0;

        for (Op op : parsed) {
            Optional<BlockState> parsedState = cache.computeIfAbsent(op.block(), s -> parse(level, s));
            if (parsedState.isEmpty()) {
                skipped++;
                continue;
            }
            BlockState rotated = parsedState.get().rotate(rot);
            boolean hollow = op.type().equals("hollow");
            for (int x = op.min()[0]; x <= op.max()[0]; x++)
                for (int y = op.min()[1]; y <= op.max()[1]; y++)
                    for (int z = op.min()[2]; z <= op.max()[2]; z++) {
                        boolean interior = hollow
                                && x > op.min()[0] && x < op.max()[0]
                                && y > op.min()[1] && y < op.max()[1]
                                && z > op.min()[2] && z < op.max()[2];
                        BlockPos wp = origin.offset(new BlockPos(x, y, z).rotate(rot));
                        if (level.isOutsideBuildHeight(wp)) continue;
                        old.putIfAbsent(wp.immutable(), level.getBlockState(wp));
                        level.setBlock(wp, interior ? air : rotated, Block.UPDATE_CLIENTS);
                        placed++;
                    }
        }

        Deque<Snapshot> dq = HISTORY.computeIfAbsent(owner, k -> new ArrayDeque<>());
        dq.push(new Snapshot(level.dimension(), old));
        while (dq.size() > MAX_HISTORY) dq.removeLast();

        String name = plan.has("name") ? plan.get("name").getAsString() : "Bauwerk";
        return new Result(name, placed, skipped);
    }

    /** @return Anzahl wiederhergestellter Bloecke oder -1 wenn nichts zum Rueckgaengigmachen da ist. */
    public static int undo(ServerLevel anyLevel, UUID owner) {
        Deque<Snapshot> dq = HISTORY.get(owner);
        if (dq == null || dq.isEmpty()) return -1;
        Snapshot snap = dq.pop();
        ServerLevel level = anyLevel.getServer().getLevel(snap.dim());
        if (level == null) return -1;
        snap.old().forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        return snap.old().size();
    }

    private static Optional<BlockState> parse(ServerLevel level, String s) {
        try {
            return Optional.of(BlockStateParser
                    .parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), s, false)
                    .blockState());
        } catch (Exception e) {
            BuildAIMod.LOGGER.warn("Unbekannter Block von der KI: {}", s);
            return Optional.empty();
        }
    }

    private static int[] toArr(JsonArray a) {
        return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
    }

    /** Plan-Rueckseite zeigt nach NORTH; wir drehen so, dass sie in Blickrichtung des Spielers zeigt. */
    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}

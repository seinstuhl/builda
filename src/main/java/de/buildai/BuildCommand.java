package de.buildai;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuildCommand {
    private static final Set<UUID> RUNNING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXEC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "buildai-http");
        t.setDaemon(true);
        return t;
    });

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aibuild")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("undo").executes(ctx -> undo(ctx.getSource())))
                .then(Commands.literal("reload").executes(ctx -> {
                    AIConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Build AI: Config neu geladen."), false);
                    return 1;
                }))
                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                        .executes(ctx -> build(ctx.getSource(), StringArgumentType.getString(ctx, "prompt")))));
    }

    private static int build(CommandSourceStack src, String prompt) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        if (AIConfig.get().resolvedKey().isBlank()) {
            src.sendFailure(Component.literal("Kein API-Key gesetzt. Trage ihn in config/buildai.json ein und nutze /aibuild reload."));
            return 0;
        }
        UUID id = player.getUUID();
        if (!RUNNING.add(id)) {
            src.sendFailure(Component.literal("Es laeuft bereits eine Generierung fuer dich."));
            return 0;
        }

        ServerLevel level = src.getLevel();
        Direction facing = player.getDirection();
        BlockPos origin = player.blockPosition().relative(facing, 4);
        MinecraftServer server = src.getServer();

        src.sendSuccess(() -> Component.literal("Die KI plant dein Bauwerk ..."), false);

        EXEC.submit(() -> {
            JsonObject plan;
            try {
                plan = AIClient.generate(prompt);
            } catch (Exception e) {
                RUNNING.remove(id);
                BuildAIMod.LOGGER.error("KI-Anfrage fehlgeschlagen", e);
                server.execute(() -> src.sendFailure(Component.literal("Fehler: " + e.getMessage())));
                return;
            }
            server.execute(() -> {
                try {
                    StructurePlacer.Result r = StructurePlacer.place(level, id, origin, facing, plan);
                    String extra = r.skipped() > 0 ? " (" + r.skipped() + " unbekannte Bloecke uebersprungen)" : "";
                    src.sendSuccess(() -> Component.literal("Fertig: " + r.name() + " - " + r.placed()
                            + " Bloecke" + extra + ". Rueckgaengig: /aibuild undo"), true);
                } catch (Exception e) {
                    BuildAIMod.LOGGER.error("Platzieren fehlgeschlagen", e);
                    src.sendFailure(Component.literal("Bauplan ungueltig: " + e.getMessage()));
                } finally {
                    RUNNING.remove(id);
                }
            });
        });
        return 1;
    }

    private static int undo(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        int n = StructurePlacer.undo(src.getLevel(), player.getUUID());
        if (n < 0) {
            src.sendFailure(Component.literal("Nichts zum Rueckgaengigmachen."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Rueckgaengig gemacht: " + n + " Bloecke."), false);
        return 1;
    }
}

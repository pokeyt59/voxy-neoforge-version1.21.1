package me.cortex.voxy.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.commonImpl.importers.DHImporter;
import me.cortex.voxy.commonImpl.importers.WorldImporter;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;


public class VoxyCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        var imports = Commands.literal("import")
                .then(Commands.literal("world")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(VoxyCommands::importWorldSuggester)
                                .executes(VoxyCommands::importWorld)))
                .then(Commands.literal("bobby")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(VoxyCommands::importBobbySuggester)
                                .executes(VoxyCommands::importBobby)))
                .then(Commands.literal("raw")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(VoxyCommands::importRaw)))
                .then(Commands.literal("zip")
                        .then(Commands.argument("zipPath", StringArgumentType.string())
                                .executes(VoxyCommands::importZip)
                                .then(Commands.argument("innerPath", StringArgumentType.string())
                                        .executes(VoxyCommands::importZip))))
                .then(Commands.literal("cancel")
                        .executes(VoxyCommands::cancelImport));

        if (DHImporter.HasRequiredLibraries) {
            imports = imports
                    .then(Commands.literal("distant_horizons")
                    .then(Commands.argument("sqlDbPath", StringArgumentType.string())
                            .executes(VoxyCommands::importDistantHorizons)));
        }

        var cache = Commands.literal("cache")
                .then(Commands.literal("clear")
                        .executes(ctx -> clearCache(ctx, false))
                        .then(Commands.literal("all")
                                .executes(ctx -> clearCache(ctx, true))));

        return Commands.literal("voxy").requires(ctx -> VoxyCommon.getInstance() != null)
                .then(Commands.literal("reload")
                        .executes(VoxyCommands::reloadInstance))
                .then(cache)
                .then(imports);
    }

    /**
     * Deletes voxy's stored LoD sections so they get re-ingested with the current mip/lighting code.
     *
     * <p>Any change to ingest-time logic (Mipper, opacity, lighting defaults) only affects sections as they are
     * written, so already-cached terrain keeps rendering with whatever code was active when it was first
     * ingested — which has repeatedly looked like the fix "not working". Clearing forces a rebuild.
     *
     * <p>Only voxy's own LoD cache is touched; the vanilla save (region/, level.dat, playerdata/) is untouched
     * and the terrain re-ingests as you explore, so this costs CPU rather than world data.
     *
     * @param all when true clears every dimension cached for this save, otherwise just the current one
     */
    private static int clearCache(CommandContext<CommandSourceStack> ctx, boolean all) {
        var instance = (VoxyClientInstance) VoxyCommon.getInstance();
        if (instance == null) return 1;

        //Resolve targets before shutting anything down, we lose the instance below
        var basePath = instance.getStorageBasePath();
        var targets = new ArrayList<Path>();
        if (all) {
            try (var dirs = Files.list(basePath)) {
                dirs.filter(Files::isDirectory)
                        .map(dir -> dir.resolve("storage"))
                        .filter(Files::isDirectory)
                        .forEach(targets::add);
            } catch (IOException e) {
                Logger.error("Failed to enumerate voxy caches under " + basePath, e);
                feedback(ctx, "Failed to enumerate voxy caches, see log");
                return 1;
            }
        } else {
            var world = WorldIdentifier.of(Minecraft.getInstance().player.clientLevel);
            if (world == null) {
                feedback(ctx, "Could not identify the current world");
                return 1;
            }
            var dir = basePath.resolve(world.getWorldId()).resolve("storage");
            if (Files.isDirectory(dir)) targets.add(dir);
        }

        if (targets.isEmpty()) {
            feedback(ctx, "No voxy LoD cache to clear");
            return 1;
        }

        //RocksDB holds a LOCK file inside storage/, so the instance has to go down before we can delete it.
        //Same shutdown/recreate dance as /voxy reload.
        var wr = Minecraft.getInstance().levelRenderer;
        if (wr != null) ((IGetVoxyRenderSystem) wr).shutdownRenderer();
        VoxyCommon.shutdownInstance();

        long freed = 0;
        int failed = 0;
        for (var target : targets) {
            try {
                freed += deleteRecursively(target);
            } catch (IOException e) {
                failed++;
                Logger.error("Failed to clear voxy LoD cache at " + target, e);
            }
        }

        VoxyCommon.createInstance();
        if (wr != null) ((IGetVoxyRenderSystem) wr).createRenderer();

        long freedMB = freed / (1024 * 1024);
        String msg = "Cleared " + (targets.size() - failed) + "/" + targets.size()
                + " voxy LoD cache(s), freed ~" + freedMB + "MB. Terrain will re-ingest as you explore.";
        if (failed != 0) msg += " " + failed + " failed, see log.";
        feedback(ctx, msg);
        return failed == 0 ? 0 : 1;
    }

    /** Deletes a directory tree depth-first, returning the total bytes removed. */
    private static long deleteRecursively(Path dir) throws IOException {
        long freed = 0;
        try (var walk = Files.walk(dir)) {
            //Reverse order puts children before their parents so each directory is empty when we reach it
            for (var path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isRegularFile(path)) freed += Files.size(path);
                Files.deleteIfExists(path);
            }
        }
        return freed;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal("[voxy] " + message), false);
    }

    private static int reloadInstance(CommandContext<CommandSourceStack> ctx) {
        var instance = (VoxyClientInstance) VoxyCommon.getInstance();
        if (instance == null) return 1;

        var wr = Minecraft.getInstance().levelRenderer;
        if (wr != null) ((IGetVoxyRenderSystem) wr).shutdownRenderer();
        VoxyCommon.shutdownInstance();
        VoxyCommon.createInstance();
        if (wr != null) ((IGetVoxyRenderSystem) wr).createRenderer();
        return 0;
    }

    private static int importDistantHorizons(CommandContext<CommandSourceStack> ctx) {
        var instance = (VoxyClientInstance) VoxyCommon.getInstance();
        if (instance == null) return 1;

        var dbFile = new File(ctx.getArgument("sqlDbPath", String.class));
        if (!dbFile.exists()) return 1;
        if (dbFile.isDirectory()) {
            dbFile = dbFile.toPath().resolve("DistantHorizons.sqlite").toFile();
            if (!dbFile.exists()) return 1;
        }

        File dbFile_ = dbFile;
        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().player.clientLevel);
        if (engine == null) return 1;
        return instance.getImportManager().makeAndRunIfNone(engine, () ->
                new DHImporter(dbFile_, engine, Minecraft.getInstance().player.clientLevel,
                        instance.getThreadPool(), instance.savingServiceRateLimiter)) ? 0 : 1;
    }

    private static boolean fileBasedImporter(File directory) {
        var instance = (VoxyClientInstance) VoxyCommon.getInstance();
        if (instance == null) return false;

        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().player.clientLevel);
        if (engine == null) return false;
        return instance.getImportManager().makeAndRunIfNone(engine, () -> {
            var importer = new WorldImporter(engine, Minecraft.getInstance().player.clientLevel,
                    instance.getThreadPool(), instance.savingServiceRateLimiter);
            importer.importRegionDirectoryAsync(directory);
            return importer;
        });
    }

    private static int importRaw(CommandContext<CommandSourceStack> ctx) {
        return fileBasedImporter(new File(ctx.getArgument("path", String.class))) ? 0 : 1;
    }

    private static int importBobby(CommandContext<CommandSourceStack> ctx) {
        var file = new File(".bobby").toPath().resolve(ctx.getArgument("world_name", String.class)).toFile();
        return fileBasedImporter(file) ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> importWorldSuggester(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder sb) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve("saves"), sb);
    }

    private static CompletableFuture<Suggestions> importBobbySuggester(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder sb) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve(".bobby"), sb);
    }

    private static CompletableFuture<Suggestions> fileDirectorySuggester(Path dir, SuggestionsBuilder sb) {
        var str = sb.getRemaining().replace("\\\\", "\\").replace("\\", "/");
        if (str.startsWith("\"")) str = str.substring(1);
        if (str.endsWith("\""))   str = str.substring(0, str.length() - 1);
        var remaining = str;
        if (str.contains("/")) {
            int idx = str.lastIndexOf('/');
            remaining = str.substring(idx + 1);
            try {
                dir = dir.resolve(str.substring(0, idx));
            } catch (Exception e) {
                return Suggestions.empty();
            }
            str = str.substring(0, idx + 1);
        } else {
            str = "";
        }

        try {
            var worlds = Files.list(dir).toList();
            for (var world : worlds) {
                if (!world.toFile().isDirectory()) continue;
                var wn = world.getFileName().toString();
                if (wn.equals(remaining)) continue;
                if (SharedSuggestionProvider.matchesSubStr(remaining, wn)
                        || SharedSuggestionProvider.matchesSubStr(remaining, '"' + wn)) {
                    wn = str + wn + "/";
                    sb.suggest(StringArgumentType.escapeIfRequired(wn));
                }
            }
        } catch (IOException ignored) {}

        return sb.buildFuture();
    }

    private static int importWorld(CommandContext<CommandSourceStack> ctx) {
        var name = ctx.getArgument("world_name", String.class);
        var file = new File("saves").toPath().resolve(name);
        name = name.toLowerCase();
        if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        if (!name.endsWith("region")) file = file.resolve("region");
        return fileBasedImporter(file.toFile()) ? 0 : 1;
    }

    private static int importZip(CommandContext<CommandSourceStack> ctx) {
        var zip = new File(ctx.getArgument("zipPath", String.class));
        var innerDir = "region/";
        try { innerDir = ctx.getArgument("innerPath", String.class); } catch (Exception ignored) {}

        var instance = (VoxyClientInstance) VoxyCommon.getInstance();
        if (instance == null) return 1;
        String finalInnerDir = innerDir;

        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().player.clientLevel);
        if (engine != null) {
            return instance.getImportManager().makeAndRunIfNone(engine, () -> {
                var importer = new WorldImporter(engine, Minecraft.getInstance().player.clientLevel,
                        instance.getThreadPool(), instance.savingServiceRateLimiter);
                importer.importZippedRegionDirectoryAsync(zip, finalInnerDir);
                return importer;
            }) ? 0 : 1;
        }
        return 1;
    }

    private static int cancelImport(CommandContext<CommandSourceStack> ctx) {
        var instance = (VoxyClientInstance) VoxyCommon.getInstance();
        if (instance == null) return 1;
        var world = WorldIdentifier.ofEngineNullable(Minecraft.getInstance().player.clientLevel);
        if (world != null) return instance.getImportManager().cancelImport(world) ? 0 : 1;
        return 1;
    }
}

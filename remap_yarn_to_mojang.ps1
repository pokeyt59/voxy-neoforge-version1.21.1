# Yarn → Mojang bulk remapping for Minecraft 1.21.1 (multi-pass).
# Idempotent: safe to re-run; already-Mojang names are left alone because
# replacements are based on Yarn-specific package paths or class identifiers.

$ErrorActionPreference = "Stop"

# ──── Wildcard package replacements ────────────────────────────────────────
$packageMap = [ordered]@{
    'net\.minecraft\.client\.render\.model\.'        = 'net.minecraft.client.renderer.block.model.'
    'net\.minecraft\.client\.render\.'               = 'net.minecraft.client.renderer.'
    'net\.minecraft\.client\.texture\.'              = 'net.minecraft.client.renderer.texture.'
    'net\.minecraft\.client\.gl\.'                   = 'com.mojang.blaze3d.platform.'
    'net\.minecraft\.client\.network\.'              = 'net.minecraft.client.multiplayer.'
    'net\.minecraft\.client\.world\.'                = 'net.minecraft.client.multiplayer.'
    'net\.minecraft\.client\.gui\.hud\.'             = 'net.minecraft.client.gui.components.'
    'net\.minecraft\.client\.gui\.screen\.'          = 'net.minecraft.client.gui.screens.'
    'net\.minecraft\.client\.util\.math\.'           = 'com.mojang.blaze3d.vertex.'
    'net\.minecraft\.world\.chunk\.light\.'          = 'net.minecraft.world.level.lighting.'
    'net\.minecraft\.world\.chunk\.'                 = 'net.minecraft.world.level.chunk.'
    'net\.minecraft\.world\.biome\.source\.'         = 'net.minecraft.world.level.biome.'
    'net\.minecraft\.world\.biome\.'                 = 'net.minecraft.world.level.biome.'
    'net\.minecraft\.world\.dimension\.'             = 'net.minecraft.world.level.dimension.'
    'net\.minecraft\.world\.storage\.'               = 'net.minecraft.world.level.storage.'
    'net\.minecraft\.block\.entity\.'                = 'net.minecraft.world.level.block.entity.'
    'net\.minecraft\.block\.'                        = 'net.minecraft.world.level.block.'
    'net\.minecraft\.fluid\.'                        = 'net.minecraft.world.level.material.'
    'net\.minecraft\.registry\.entry\.'              = 'net.minecraft.core.'
    'net\.minecraft\.registry\.'                     = 'net.minecraft.core.'
    'net\.minecraft\.util\.math\.random\.'           = 'net.minecraft.world.level.levelgen.'
    'net\.minecraft\.util\.math\.'                   = 'net.minecraft.core.'
    'net\.minecraft\.util\.collection\.'             = 'net.minecraft.core.'
    'net\.minecraft\.util\.profiler\.'               = 'net.minecraft.util.profiling.'
    'net\.minecraft\.util\.thread\.'                 = 'net.minecraft.util.thread.'
    'net\.minecraft\.text\.'                         = 'net.minecraft.network.chat.'
    'net\.minecraft\.command\.'                      = 'net.minecraft.commands.'
    'net\.minecraft\.server\.world\.'                = 'net.minecraft.server.level.'
    'net\.minecraft\.server\.network\.'              = 'net.minecraft.server.network.'
    'net\.minecraft\.entity\.player\.'               = 'net.minecraft.world.entity.player.'
    'net\.minecraft\.entity\.'                       = 'net.minecraft.world.entity.'
    'net\.minecraft\.network\.packet\.s2c\.play\.'   = 'net.minecraft.network.protocol.game.'
    'net\.minecraft\.network\.packet\.s2c\.common\.' = 'net.minecraft.network.protocol.common.'
    'net\.minecraft\.network\.packet\.c2s\.play\.'   = 'net.minecraft.network.protocol.game.'
    'net\.minecraft\.network\.packet\.c2s\.common\.' = 'net.minecraft.network.protocol.common.'
    'net\.minecraft\.network\.packet\.'              = 'net.minecraft.network.protocol.'
}

# ──── Specific full-path import replacements (after wildcard rules run) ────
$importMap = [ordered]@{
    'net\.minecraft\.client\.MinecraftClient'                                = 'net.minecraft.client.Minecraft'
    'net\.minecraft\.client\.renderer\.WorldRenderer'                        = 'net.minecraft.client.renderer.LevelRenderer'
    'net\.minecraft\.client\.multiplayer\.ClientWorld'                       = 'net.minecraft.client.multiplayer.ClientLevel'
    'net\.minecraft\.client\.multiplayer\.ClientChunkManager'                = 'net.minecraft.client.multiplayer.ClientChunkCache'
    'net\.minecraft\.client\.multiplayer\.ClientPlayerEntity'                = 'net.minecraft.client.player.LocalPlayer'
    'net\.minecraft\.client\.multiplayer\.ClientPlayNetworkHandler'          = 'net.minecraft.client.multiplayer.ClientPacketListener'
    'net\.minecraft\.client\.multiplayer\.ClientPlayerInteractionManager'    = 'net.minecraft.client.multiplayer.MultiPlayerGameMode'
    'net\.minecraft\.client\.multiplayer\.ClientCommonNetworkHandler'        = 'net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl'
    'net\.minecraft\.client\.multiplayer\.ClientLoginNetworkHandler'         = 'net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl'
    'net\.minecraft\.client\.renderer\.LightmapTextureManager'               = 'net.minecraft.client.renderer.LightTexture'
    'net\.minecraft\.client\.renderer\.RenderLayers'                         = 'net.minecraft.client.renderer.ItemBlockRenderTypes'
    'net\.minecraft\.client\.renderer\.RenderLayer'                          = 'net.minecraft.client.renderer.RenderType'
    'net\.minecraft\.client\.renderer\.RenderPhase'                          = 'net.minecraft.client.renderer.RenderStateShard'
    'net\.minecraft\.client\.renderer\.Camera'                               = 'net.minecraft.client.Camera'
    'net\.minecraft\.client\.renderer\.Frustum'                              = 'net.minecraft.client.renderer.culling.Frustum'
    'net\.minecraft\.client\.renderer\.BackgroundRenderer'                   = 'net.minecraft.client.renderer.FogRenderer'
    'net\.minecraft\.client\.renderer\.BufferBuilder'                        = 'com.mojang.blaze3d.vertex.BufferBuilder'
    'net\.minecraft\.client\.renderer\.BuiltBuffer'                          = 'com.mojang.blaze3d.vertex.MeshData'
    'net\.minecraft\.client\.renderer\.VertexFormats'                        = 'com.mojang.blaze3d.vertex.DefaultVertexFormat'
    'net\.minecraft\.client\.renderer\.VertexFormat'                         = 'com.mojang.blaze3d.vertex.VertexFormat'
    'net\.minecraft\.client\.renderer\.VertexConsumer'                       = 'com.mojang.blaze3d.vertex.VertexConsumer'
    'net\.minecraft\.client\.renderer\.Tessellator'                          = 'com.mojang.blaze3d.vertex.Tesselator'
    'net\.minecraft\.client\.renderer\.RenderTickCounter'                    = 'net.minecraft.client.DeltaTracker'
    'net\.minecraft\.client\.renderer\.block\.model\.BakedQuad'              = 'net.minecraft.client.renderer.block.model.BakedQuad'
    'net\.minecraft\.client\.gui\.components\.BossBarHud'                    = 'net.minecraft.client.gui.components.BossHealthOverlay'
    'net\.minecraft\.client\.gui\.components\.DebugHud'                      = 'net.minecraft.client.gui.components.DebugScreenOverlay'
    'net\.minecraft\.client\.gui\.components\.InGameHud'                     = 'net.minecraft.client.gui.Gui'
    'net\.minecraft\.client\.renderer\.texture\.SpriteAtlasTexture'          = 'net.minecraft.client.renderer.texture.TextureAtlas'
    'net\.minecraft\.client\.renderer\.texture\.NativeImageBackedTexture'    = 'net.minecraft.client.renderer.texture.DynamicTexture'
    'net\.minecraft\.client\.renderer\.texture\.NativeImage'                 = 'com.mojang.blaze3d.platform.NativeImage'
    'net\.minecraft\.client\.renderer\.texture\.MipmapHelper'                = 'net.minecraft.client.renderer.texture.MipmapGenerator'
    'net\.minecraft\.client\.util\.Window'                                   = 'com.mojang.blaze3d.platform.Window'
    'net\.minecraft\.client\.util\.MonitorTracker'                           = 'com.mojang.blaze3d.platform.ScreenManager'
    'com\.mojang\.blaze3d\.platform\.GlDebug'                                = 'com.mojang.blaze3d.platform.GlDebug'
    'net\.minecraft\.world\.level\.chunk\.WorldChunk'                        = 'net.minecraft.world.level.chunk.LevelChunk'
    'net\.minecraft\.world\.level\.chunk\.ChunkSection'                      = 'net.minecraft.world.level.chunk.LevelChunkSection'
    'net\.minecraft\.world\.level\.chunk\.ChunkStatus'                       = 'net.minecraft.world.level.chunk.status.ChunkStatus'
    'net\.minecraft\.world\.level\.chunk\.PalettedContainer'                 = 'net.minecraft.world.level.chunk.PalettedContainer'
    'net\.minecraft\.world\.level\.chunk\.ChunkNibbleArray'                  = 'net.minecraft.world.level.chunk.DataLayer'
    'net\.minecraft\.world\.level\.chunk\.ReadableContainer'                 = 'net.minecraft.world.level.chunk.PalettedContainerRO'
    'net\.minecraft\.world\.level\.chunk\.Chunk'                             = 'net.minecraft.world.level.chunk.ChunkAccess'
    'net\.minecraft\.world\.level\.lighting\.LightStorage'                   = 'net.minecraft.world.level.lighting.LayerLightSectionStorage'
    'net\.minecraft\.world\.level\.lighting\.LightingProvider'               = 'net.minecraft.world.level.lighting.LevelLightEngine'
    'net\.minecraft\.world\.level\.biome\.source\.BiomeAccess'               = 'net.minecraft.world.level.biome.BiomeManager'
    'net\.minecraft\.world\.MutableWorldProperties'                          = 'net.minecraft.world.level.storage.WritableLevelData'
    'net\.minecraft\.world\.LightType'                                       = 'net.minecraft.world.level.LightLayer'
    'net\.minecraft\.world\.World'                                           = 'net.minecraft.world.level.Level'
    'net\.minecraft\.world\.level\.block\.FluidBlock'                        = 'net.minecraft.world.level.block.LiquidBlock'
    'net\.minecraft\.world\.level\.block\.BlockState'                        = 'net.minecraft.world.level.block.state.BlockState'
    'net\.minecraft\.core\.Registries'                                       = 'net.minecraft.core.registries.BuiltInRegistries'
    'net\.minecraft\.core\.RegistryKeys'                                     = 'net.minecraft.core.registries.Registries'
    'net\.minecraft\.core\.RegistryKey'                                      = 'net.minecraft.resources.ResourceKey'
    'net\.minecraft\.core\.RegistryEntry'                                    = 'net.minecraft.core.Holder'
    'net\.minecraft\.core\.DynamicRegistryManager'                           = 'net.minecraft.core.RegistryAccess'
    'net\.minecraft\.core\.IdList'                                           = 'net.minecraft.core.IdMapper'
    'net\.minecraft\.core\.IndexedIterable'                                  = 'net.minecraft.core.IdMap'
    'net\.minecraft\.core\.PackedIntegerArray'                               = 'net.minecraft.util.SimpleBitStorage'
    'net\.minecraft\.core\.EmptyPaletteStorage'                              = 'net.minecraft.util.ZeroBitStorage'
    'net\.minecraft\.core\.PaletteStorage'                                   = 'net.minecraft.util.BitStorage'
    'net\.minecraft\.core\.MathHelper'                                       = 'net.minecraft.util.Mth'
    'net\.minecraft\.core\.Identifier'                                       = 'net.minecraft.resources.ResourceLocation'
    'net\.minecraft\.core\.ChunkPos'                                         = 'net.minecraft.world.level.ChunkPos'
    'net\.minecraft\.core\.ChunkSectionPos'                                  = 'net.minecraft.core.SectionPos'
    'net\.minecraft\.core\.Vec3d'                                            = 'net.minecraft.world.phys.Vec3'
    'net\.minecraft\.core\.Box'                                              = 'net.minecraft.world.phys.AABB'
    'net\.minecraft\.world\.level\.levelgen\.RandomSeed'                     = 'net.minecraft.world.level.levelgen.RandomSupport'
    'net\.minecraft\.util\.profiling\.Profiler'                              = 'net.minecraft.util.profiling.ProfilerFiller'
    'net\.minecraft\.util\.thread\.ThreadExecutor'                           = 'net.minecraft.util.thread.BlockableEventLoop'
    'net\.minecraft\.network\.chat\.MutableText'                             = 'net.minecraft.network.chat.MutableComponent'
    'net\.minecraft\.network\.chat\.Text'                                    = 'net.minecraft.network.chat.Component'
    'net\.minecraft\.commands\.CommandSource'                                = 'net.minecraft.commands.SharedSuggestionProvider'
    'net\.minecraft\.server\.level\.OptionalChunk'                           = 'net.minecraft.server.level.ChunkResult'
    'net\.minecraft\.server\.level\.ServerWorld'                             = 'net.minecraft.server.level.ServerLevel'
    'net\.minecraft\.server\.network\.ServerPlayerEntity'                    = 'net.minecraft.server.level.ServerPlayer'
    'net\.minecraft\.world\.entity\.player\.PlayerEntity'                    = 'net.minecraft.world.entity.player.Player'
    'net\.minecraft\.network\.protocol\.game\.GameJoinS2CPacket'             = 'net.minecraft.network.protocol.game.ClientboundLoginPacket'
    'net\.minecraft\.network\.protocol\.Packet'                              = 'net.minecraft.network.protocol.Packet'
    'net\.minecraft\.world\.level\.storage\.ChunkCompressionFormat'          = 'net.minecraft.world.level.chunk.storage.ChunkSerializer'
}

# ──── Bare-identifier (whole-word) replacements ────────────────────────────
$identMap = [ordered]@{
    # Longer / more specific names FIRST so they aren't shadowed.
    'MinecraftClient'                                = 'Minecraft'
    'ClientWorld'                                    = 'ClientLevel'
    'WorldRenderer'                                  = 'LevelRenderer'
    'WorldChunk'                                     = 'LevelChunk'
    'WorldImporter'                                  = 'WorldImporter'
    'WorldIdentifier'                                = 'WorldIdentifier'
    'WorldEngine'                                    = 'WorldEngine'
    'WorldConversionFactory'                         = 'WorldConversionFactory'
    'WorldConfig'                                    = 'WorldConfig'
    'WorldSavePath'                                  = 'LevelResource'
    'IWorldGetIdentifier'                            = 'IWorldGetIdentifier'
    'IGetWorldIdentifier'                            = 'IGetWorldIdentifier'
    'NbtCompound'                                    = 'CompoundTag'
    'NbtElement'                                     = 'Tag'
    'NbtSizeTracker'                                 = 'NbtAccounter'
    'PacketByteBuf'                                  = 'FriendlyByteBuf'
    'BlockColorProvider'                             = 'BlockColor'
    'BlockEntityProvider'                            = 'EntityBlock'
    'BlockRenderView'                                = 'BlockAndTintGetter'
    'ClientBossBar'                                  = 'LerpingBossEvent'
    'BossBar'                                        = 'BossEvent'
    'BiomeKeys'                                      = 'Biomes'
    'LocalRandom'                                    = 'LegacyRandomSource'
    'WindowSettings'                                 = 'DisplayData'
    'CheckedRandom'                                  = 'LegacyRandomSource'
    'ClientChunkManager'                             = 'ClientChunkCache'
    'ClientPlayerEntity'                             = 'LocalPlayer'
    'ClientPlayNetworkHandler'                       = 'ClientPacketListener'
    'ClientPlayerInteractionManager'                 = 'MultiPlayerGameMode'
    'ClientCommonNetworkHandler'                     = 'ClientCommonPacketListenerImpl'
    'ClientLoginNetworkHandler'                      = 'ClientHandshakePacketListenerImpl'
    'LightmapTextureManager'                         = 'LightTexture'
    'RenderLayers'                                   = 'ItemBlockRenderTypes'
    'RenderLayer'                                    = 'RenderType'
    'RenderPhase'                                    = 'RenderStateShard'
    'BackgroundRenderer'                             = 'FogRenderer'
    'BuiltBuffer'                                    = 'MeshData'
    'RenderTickCounter'                              = 'DeltaTracker'
    'BossBarHud'                                     = 'BossHealthOverlay'
    'DebugHud'                                       = 'DebugScreenOverlay'
    'InGameHud'                                      = 'Gui'
    'SpriteAtlasTexture'                             = 'TextureAtlas'
    'NativeImageBackedTexture'                       = 'DynamicTexture'
    'MipmapHelper'                                   = 'MipmapGenerator'
    'MonitorTracker'                                 = 'ScreenManager'
    'MutableWorldProperties'                         = 'WritableLevelData'
    'LightType'                                      = 'LightLayer'
    'ChunkSection'                                   = 'LevelChunkSection'
    'ChunkNibbleArray'                               = 'DataLayer'
    'BiomeAccess'                                    = 'BiomeManager'
    'FluidBlock'                                     = 'LiquidBlock'
    'OptionalChunk'                                  = 'ChunkResult'
    'ServerWorld'                                    = 'ServerLevel'
    'ServerPlayerEntity'                             = 'ServerPlayer'
    'PlayerEntity'                                   = 'Player'
    'IdList'                                         = 'IdMapper'
    'IndexedIterable'                                = 'IdMap'
    'PackedIntegerArray'                             = 'SimpleBitStorage'
    'EmptyPaletteStorage'                            = 'ZeroBitStorage'
    'PaletteStorage'                                 = 'BitStorage'
    'MathHelper'                                     = 'Mth'
    'ChunkSectionPos'                                = 'SectionPos'
    'Vec3d'                                          = 'Vec3'
    'Box'                                            = 'AABB'
    'Identifier'                                     = 'ResourceLocation'
    'MutableText'                                    = 'MutableComponent'
    'CommandSource'                                  = 'SharedSuggestionProvider'
    'DynamicRegistryManager'                         = 'RegistryAccess'
    'Registries'                                     = 'BuiltInRegistries'
    'RegistryKeys'                                   = 'Registries'
    'RegistryKey'                                    = 'ResourceKey'
    'RegistryEntry'                                  = 'Holder'
    'Profiler'                                       = 'ProfilerFiller'
    'ThreadExecutor'                                 = 'BlockableEventLoop'
    'ReadableContainer'                              = 'PalettedContainerRO'
    'LightingProvider'                               = 'LevelLightEngine'
    'GameJoinS2CPacket'                              = 'ClientboundLoginPacket'
    'MatrixStack'                                    = 'PoseStack'
    'VertexConsumer'                                 = 'VertexConsumer'
    'AbstractTexture'                                = 'AbstractTexture'
    'BakedQuad'                                      = 'BakedQuad'
    'ChunkCompressionFormat'                         = 'ChunkSerializer'
    'RandomSeed'                                     = 'RandomSupport'
    # Vanilla `Text` → `Component`. Risk: `Text` collides only with itself,
    # since other token names (`MutableText`) are already remapped first.
    'Text'                                           = 'Component'
    # `World` last in the World cluster so longer names win first.
    'World'                                          = 'Level'
}

# ──── Field/method renames (\b-anchored) ───────────────────────────────────
$memberMap = [ordered]@{
    '\bworldRenderer\b'                              = 'levelRenderer'
    '\bclientWorld\b'                                = 'clientLevel'
    '\bgetBottomY\(\)'                               = 'getMinBuildHeight()'
    '\brunDirectory\b'                               = 'gameDirectory'
    '\bgetBottomSectionCoord\(\)'                    = 'getMinSectionY()'
    '\bgetSectionArray\(\)'                          = 'getSections()'
    '\bgetDefaultState\(\)'                          = 'defaultBlockState()'
    '\brandomUuid\(\)'                               = 'randomUUID()'
    '\binGameHud\b'                                  = 'gui'
    '\bResourceLocation\.of\('                       = 'ResourceLocation.parse('
    '\bResourceKey\.of\('                            = 'ResourceKey.create('
    '\.getRegistry\(\)'                              = '.registry()'
    # `getValue()` collides with countless real-world calls — leave it alone.
    # `getWorld()` collides with non-MC code; do it only when called on a chunk-like.
    '\bchunk\.getWorld\(\)'                          = 'chunk.getLevel()'
    '\bSectionPos\.from\('                           = 'SectionPos.of('
    # RenderType bean accessors
    '\bRenderType\.getSolid\(\)'                     = 'RenderType.solid()'
    '\bRenderType\.getCutout\(\)'                    = 'RenderType.cutout()'
    '\bRenderType\.getCutoutMipped\(\)'              = 'RenderType.cutoutMipped()'
    '\bRenderType\.getTranslucent\(\)'               = 'RenderType.translucent()'
    # ItemBlockRenderTypes (was RenderLayers)
    '\bItemBlockRenderTypes\.getBlockLayer\('        = 'ItemBlockRenderTypes.getChunkRenderType('
    '\bItemBlockRenderTypes\.getFluidLayer\('        = 'ItemBlockRenderTypes.getRenderLayer('
    # BossEvent (was BossBar)
    '\bsetPercent\('                                 = 'setProgress('
    # LevelChunkSection (was ChunkSection) accessors
    '\bgetBlockStateContainer\(\)'                   = 'getStates()'
    '\bgetBiomeContainer\(\)'                        = 'getBiomes()'
    # BlockPos
    '\bBlockPos\.ORIGIN\b'                           = 'BlockPos.ZERO'
    # Block.STATE_IDS
    '\bBlock\.STATE_IDS\b'                           = 'Block.BLOCK_STATE_REGISTRY'
    # Gui (was InGameHud) accessors
    '\bgetBossBarHud\(\)'                            = 'getBossOverlay()'
    '\bgetChatHud\(\)'                               = 'getChat()'
    # Yarn `getUuid()` → Mojang `getUUID()` (Java naming)
    '\bgetUuid\(\)'                                  = 'getUUID()'
    # Level.getLightingProvider() → getLightEngine()
    '\.getLightingProvider\(\)'                      = '.getLightEngine()'
    # Mojang's NbtAccounter (was NbtSizeTracker.ofUnlimitedBytes)
    '\bofUnlimitedBytes\(\)'                         = 'unlimitedHeap()'
    # MinecraftClient.world → Minecraft.level
    '\bMinecraft\.getInstance\(\)\.world\b'          = 'Minecraft.getInstance().level'
}

# Don't reapply identifier replacements that are no-ops (replacement equals key).
$identMapEffective = [ordered]@{}
foreach ($k in $identMap.Keys) {
    if ($identMap[$k] -ne $k) { $identMapEffective[$k] = $identMap[$k] }
}

$importMapEffective = [ordered]@{}
foreach ($k in $importMap.Keys) {
    if ($importMap[$k] -ne $k -and -not ($k -match '\\\.' -and (($k -replace '\\\.', '.') -eq $importMap[$k]))) {
        $importMapEffective[$k] = $importMap[$k]
    }
}

$javaFiles = Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java"
$totalChanges = 0
$skipped = 0

# Tokens unique to Yarn (1.21.1) — if a file has none of these, it's either
# already-Mojang or doesn't reference MC at all, so skip the rewrite.
$yarnMarkers = @(
    'net\.minecraft\.client\.MinecraftClient',
    'net\.minecraft\.client\.world\.',
    'net\.minecraft\.client\.network\.',
    'net\.minecraft\.client\.render\.',
    'net\.minecraft\.client\.texture\.',
    'net\.minecraft\.client\.gui\.hud\.',
    'net\.minecraft\.client\.gl\.',
    'net\.minecraft\.client\.util\.',
    'net\.minecraft\.world\.chunk\.',
    'net\.minecraft\.world\.biome\.',
    'net\.minecraft\.world\.dimension\.',
    'net\.minecraft\.block\.',
    'net\.minecraft\.fluid\.',
    'net\.minecraft\.registry\.',
    'net\.minecraft\.util\.math\.',
    'net\.minecraft\.util\.collection\.',
    'net\.minecraft\.util\.profiler\.',
    'net\.minecraft\.text\.',
    'net\.minecraft\.command\.',
    'net\.minecraft\.server\.world\.',
    'net\.minecraft\.entity\.',
    'net\.minecraft\.network\.packet\.',
    '\bMinecraftClient\b',
    '\bClientWorld\b',
    '\bWorldRenderer\b',
    '\bWorldChunk\b',
    '\bChunkSectionPos\b',
    '\bIdentifier\b',
    '\bMathHelper\b',
    '\bBlockPos\.ORIGIN\b'
)
$yarnRegex = '(' + ($yarnMarkers -join '|') + ')'

foreach ($f in $javaFiles) {
    $orig = Get-Content -Raw -LiteralPath $f.FullName

    # Skip files with no Yarn markers — they're already Mojang or unrelated.
    # Use case-sensitive IsMatch (PowerShell's -match operator is case-insensitive
    # by default, which falsely matches lowercase identifiers like 'identifier').
    if (-not [regex]::IsMatch($orig, $yarnRegex)) { $skipped++; continue }

    $text = $orig

    # 1) Wildcard package replacements (run first — they're broader)
    foreach ($k in $packageMap.Keys) {
        $text = [System.Text.RegularExpressions.Regex]::Replace($text, $k, $packageMap[$k])
    }

    # 2) Specific full-path import replacements (after package rules).
    # \b at the end prevents false matches like
    #   CommandSource → SharedSuggestionProvider mangling CommandSourceStack
    foreach ($k in $importMapEffective.Keys) {
        $text = [System.Text.RegularExpressions.Regex]::Replace($text, $k + '\b', $importMapEffective[$k])
    }

    # 3) Bare-identifier replacements (whole-word only)
    foreach ($k in $identMapEffective.Keys) {
        $pat = '\b' + [System.Text.RegularExpressions.Regex]::Escape($k) + '\b'
        $text = [System.Text.RegularExpressions.Regex]::Replace($text, $pat, $identMapEffective[$k])
    }

    # 4) Field/method replacements
    foreach ($k in $memberMap.Keys) {
        $text = [System.Text.RegularExpressions.Regex]::Replace($text, $k, $memberMap[$k])
    }

    if ($text -ne $orig) {
        Set-Content -LiteralPath $f.FullName -Value $text -NoNewline
        $totalChanges++
    }
}

Write-Host "Remapped $totalChanges files (skipped $skipped already-Mojang/unrelated files)"

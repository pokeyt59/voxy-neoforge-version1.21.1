# Targeted post-processing fixes for imports where the bulk remap script
# guessed wrong paths. Idempotent: each replacement is a no-op if already fixed.

$ErrorActionPreference = "Stop"

$pathFixes = [ordered]@{
    'net\.minecraft\.util\.Pair'                            = 'com.mojang.datafixers.util.Pair'
    'net\.minecraft\.world\.BlockAndTintGetter'             = 'net.minecraft.world.level.BlockAndTintGetter'
    'net\.minecraft\.world\.level\.biome\.ColorResolver'    = 'net.minecraft.world.level.ColorResolver'
    'net\.minecraft\.util\.CompoundTag'                     = 'net.minecraft.nbt.CompoundTag'
    'net\.minecraft\.util\.Tag'                             = 'net.minecraft.nbt.Tag'
    'net\.minecraft\.util\.NbtAccounter'                    = 'net.minecraft.nbt.NbtAccounter'
    'net\.minecraft\.util\.FriendlyByteBuf'                 = 'net.minecraft.network.FriendlyByteBuf'
    'net\.minecraft\.client\.gui\.components\.WindowEventHandler' = 'net.minecraft.client.WindowEventHandler'
    'net\.minecraft\.world\.level\.material\.FogType'       = 'net.minecraft.world.level.material.FogType'
    'net\.minecraft\.world\.level\.lighting\.LightStorage'  = 'net.minecraft.world.level.lighting.LayerLightSectionStorage'
    'com\.mojang\.blaze3d\.platform\.DisplayData'           = 'com.mojang.blaze3d.platform.DisplayData'
    'net\.minecraft\.world\.level\.lighting\.LayerLightSectionStorage' = 'net.minecraft.world.level.lighting.LayerLightSectionStorage'
    # Fix Mapper file's incorrect path
    'net\.minecraft\.world\.level\.block\.entity\.BlockEntity\.Provider' = 'net.minecraft.world.level.block.EntityBlock'
}

# Method/field fixes that need broader application
$memberFixes = [ordered]@{
    '\bBuiltInRegistries\.BIOME\b'           = 'Registries.BIOME'
    '\bBuiltInRegistries\.DIMENSION_TYPE\b'  = 'Registries.DIMENSION_TYPE'
    '\bisEmpty\(\)\s*\)\s*continue'          = 'hasOnlyAir()) continue'
    '\.getMinSectionY\(\)'                   = '.getMinSection()'
    '\bgetTripwire\(\)'                      = 'tripwire()'
    '\.getBlockState\(\)'                    = '.getBlockState()'
}

$javaFiles = Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java"
$totalChanges = 0

foreach ($f in $javaFiles) {
    $orig = Get-Content -Raw -LiteralPath $f.FullName
    $text = $orig

    foreach ($k in $pathFixes.Keys) {
        $text = [System.Text.RegularExpressions.Regex]::Replace($text, $k, $pathFixes[$k])
    }
    foreach ($k in $memberFixes.Keys) {
        $text = [System.Text.RegularExpressions.Regex]::Replace($text, $k, $memberFixes[$k])
    }

    if ($text -ne $orig) {
        Set-Content -LiteralPath $f.FullName -Value $text -NoNewline
        $totalChanges++
    }
}

Write-Host "Patched $totalChanges files"

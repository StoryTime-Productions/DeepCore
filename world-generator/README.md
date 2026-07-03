# Standalone World Generator

This is a standalone utility, intentionally separate from DeepCore plugin code.

## Source Of Truth For Jars

- The script uses Mojang API as the only method to resolve and download server jars.
- It downloads vanilla server jars from Mojang and caches them in `world-generator/.cache/` by default.

## Usage

Run from this folder:

```powershell
./generate-world.ps1 -Interactive
```

Or run with flags:

```powershell
./generate-world.ps1 -MinecraftVersion 1.21.10 -WorldType normal -WorldName practice_world -GenerateStructures $true
```

`-WorldName` can also be passed as `-Name`.

### Supported World Types

- `normal`
- `large_biomes`
- `amplified`
- `single_biome`
- `flat`
- `void` (no barrier)

### Optional Flags

- `-Seed <value>`
- `-Force`
- `-OutputRoot <path>`
- `-WorldOutputPath <path>`
- `-CacheRoot <path>`
- `-GenerationTimeoutSeconds <int>`
- `-MaxRetries <int>`
- `-JavaExecutable <path-or-command>`
- `-KeepTemporaryFiles` (keeps temp cache/workspace instead of cleaning them on success)

## Behavior

- Supports both interactive wizard mode and pure CLI flag mode.
- Validates each input step.
- Retries invalid input up to `-MaxRetries`.
- Uses Mojang version manifest to resolve available versions.
- Downloads vanilla server jar from Mojang for selected version (with SHA1 verification when available).
- Generates `<name>`, `<name>_nether`, and `<name>_the_end` folders.
- Default output path is `world-generator/outputs`.
- Uses a temporary runtime/cache workspace by default and removes it on successful completion.
- Writes only world folders to the output path (`-WorldOutputPath` or `-OutputRoot`), with no server artifact files left behind.

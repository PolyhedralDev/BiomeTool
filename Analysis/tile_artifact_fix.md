# Tile Rendering Artifact Fix — Resume Context

## Status
Code changes are complete and **not yet committed**. The fix needs a `gradlew build` and smoke-test before committing.

## The Bug
Opening multiple render tabs (e.g. TARTARUS + CHIMERA) causes rectangular tile-sized patches to appear with a distinctly different color palette — as if rendered in a different color space. The biome layout within the patch is geographically plausible but wrong for those coordinates.

### Root cause
`BiomeToolView.scope` uses a shared `ScheduledThreadPool` of up to `min(processors, 8)` threads. All `MapView` instances share this scope. `InternalMap.scheduleBigChunkGeneration` launches one coroutine per big-chunk group, so multiple tiles from the same tab call `BiomeProvider.getBiome` concurrently on the same provider instance.

Terra's `BiomePipelineProvider` has an internal chunk-level cache that is **not thread-safe**. Concurrent reads from multiple threads corrupt cached chunk data, causing some `getBiome` calls to return biomes from a different world location. The corrupted entry persists in the cache until evicted, producing a consistent rectangular artifact (not a flicker).

## The Fix
Each tab gets its own `CoroutineScope` with `limitedParallelism(1)` derived from the shared dispatcher. This serialises `getBiome` calls within a tab (eliminating the race) while still allowing different tabs — which use different `BiomeProvider` instances — to render concurrently.

## Files Changed (uncommitted)

### `BiomeToolView.kt`
- Added `import kotlinx.coroutines.cancel`
- `TabState` data class: added `scope: CoroutineScope` field (second position, after `mapView`)
- `reload()`: added `it.scope.cancel()` alongside the existing `it.mapView.close()` call
- `addBiomeViewTab()`:
  - Creates `val tabScope = CoroutineScope(SupervisorJob() + coroutineDispatcher.limitedParallelism(1))`
  - Passes `tabScope` to `mapview(...)` instead of `BiomeToolView.scope`
  - Passes `tabScope` into the `TabState(...)` constructor
- `setOnClosed` lambda: now calls both `state.mapView.close()` and `state.scope.cancel()` via a `let` block

No other files were changed for this fix.

## What Still Needs Doing
1. `./gradlew build` to verify it compiles
2. Launch via `StartBiomeTool.bat`, open two tabs (CHIMERA + TARTARUS), pan around and confirm the rectangular patches are gone
3. Commit — message should reference the thread-safety root cause

## Notes
- `BiomeToolView.scope` (the shared root scope) is still used as the parent for the thread pool/dispatcher but is no longer passed directly to any `MapView`
- `coroutineDispatcher` is a `private val` in `BiomeToolView.companion object`; `limitedParallelism(1)` is available because the project uses coroutines 1.10.2
- This issue does **not** affect Terra running inside Minecraft/Paper: Paper's chunk gen system gives each thread a distinct non-overlapping chunk, so threads never race on the same pipeline cache slot. BiomeTool's wide-viewport rendering is the pathological pattern.

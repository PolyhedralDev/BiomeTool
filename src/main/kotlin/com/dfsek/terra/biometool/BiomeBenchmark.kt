package com.dfsek.terra.biometool

import com.dfsek.terra.api.config.ConfigPack
import com.dfsek.terra.api.properties.Context
import com.dfsek.terra.api.properties.PropertyKey
import com.dfsek.terra.api.world.biome.Biome
import com.dfsek.terra.api.world.biome.generation.BiomeProvider
import java.io.File
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Headless benchmark for measuring Terra biome pipeline performance.
 * Simulates tile generation by querying the biome provider in a snake-order grid pattern
 * across a configurable number of threads.
 */
object BiomeBenchmark {

    private const val TILE_PIXEL_SIZE = 128

    private fun packBlendMaxY(pack: ConfigPack): Int {
        return try {
            val cfg    = pack.context.getByClassName(
                "com.dfsek.terra.addons.chunkgenerator.config.NoiseChunkGeneratorPackConfigTemplate"
            )
            val rawMax = cfg?.javaClass?.getMethod("getBlendMaxY")?.invoke(cfg) as? Int ?: Int.MAX_VALUE
            if (rawMax == Int.MAX_VALUE) 320 else rawMax
        } catch (_: Exception) { 320 }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val tilesX       = args.getOrNull(0)?.toIntOrNull() ?: 100
        val tilesY       = args.getOrNull(1)?.toIntOrNull() ?: tilesX
        val seed         = args.getOrNull(2)?.toLongOrNull() ?: 1L
        val csvPrefix    = args.getOrNull(3)
        val subsample    = args.getOrNull(4)?.toIntOrNull() ?: 4
        val lod          = args.getOrNull(5)?.toIntOrNull() ?: 0
        val threadCount      = (args.getOrNull(6)?.toIntOrNull() ?: 4).coerceAtLeast(1)
        val overflowEnabled  = (args.getOrNull(7)?.toIntOrNull() ?: 1) != 0
        val packName     = args.getOrNull(8)?.takeIf { it.isNotEmpty() } ?: "CHIMERA"
        val packPath     = args.getOrNull(9)?.takeIf { it.isNotEmpty() }?.let { File(it) }
        val dumpSurface  = (args.getOrNull(10)?.toIntOrNull() ?: 0) != 0
        val dumpDirArg   = args.getOrNull(11)?.takeIf { it.isNotEmpty() }

        val imageSize     = TILE_PIXEL_SIZE shr lod
        val sampleStep    = subsample shl lod
        val totalTiles    = tilesX * tilesY
        val tileWorldSize = TILE_PIXEL_SIZE * subsample
        val stripWidth    = maxOf(1, 1000 / tileWorldSize)
        // Grid is centered on world (0,0): tileWorldSize = 128 * subsample is always even, so the
        // origin is exact with no rounding drift. The snake traversal still starts from the corner
        // of this centered grid (intentional — it keeps the active processing zone ~1000 units wide
        // for Terra's spatial caching; see buildSnakeOrder).
        val originX       = -(tilesX * tileWorldSize) / 2
        val originZ       = -(tilesY * tileWorldSize) / 2

        println("=== BiomeTool Benchmark ===")
        println("Grid: ${tilesX}x${tilesY} tiles ($totalTiles total)")
        println("Tile size: ${imageSize}x${imageSize} pixels (${tileWorldSize}x${tileWorldSize} world blocks)")
        println("Subsample: ${subsample}x, LOD: $lod (effective stride: ${sampleStep} blocks/pixel)")
        println("Total pixels: ${totalTiles.toLong() * imageSize * imageSize}")
        println("Seed: $seed")
        println("Threads: $threadCount  |  Snake strip: $stripWidth tile(s) wide (${stripWidth * tileWorldSize} world units)")
        if (packPath != null) println("Pack path: ${packPath.absolutePath}  (single-pack mode)")
        println()

        // Set single-pack override BEFORE the platform singleton is first accessed.
        // BiomeToolPlatform.init{} calls load() (addons) using the real working dir;
        // reload() then uses the temp folder built from packPath for pack discovery.
        if (packPath != null) {
            require(packPath.isDirectory) {
                "Pack path '${packPath.absolutePath}' does not exist or is not a directory."
            }
            BiomeToolPlatform.singlePackOverride = packPath
        }

        println("Initializing Terra platform...")
        val platform = BiomeToolPlatform
        val reloadStart = System.nanoTime()
        platform.reload()
        val packLoadMs = (System.nanoTime() - reloadStart) / 1_000_000.0

        val packs = platform.configRegistry.keys().toList()
        if (packs.isEmpty()) {
            System.err.println("ERROR: No config packs found. Ensure packs are in the 'packs' directory.")
            System.exit(1)
        }

        val packKey = packs.find { it.id == packName }
            ?: run {
                System.err.println("ERROR: Pack '$packName' not found. Available packs: ${packs.joinToString { it.id }}")
                System.exit(1)
                null
            }!!
        val pack    = platform.configRegistry[packKey].get()
        println("Using pack: ${packKey.id}")
        println()

        val provider        = pack.biomeProvider
        val surfaceProvider = getSurfaceProvider(provider)
        val surfaceY        = packBlendMaxY(pack)
        println("Surface sample Y: $surfaceY  |  Subsurface sample Y: 0")

        // Deterministic biome id -> index map (seeded from the pack registry, sorted). Shared
        // read-mostly across worker threads; new ids encountered at runtime are appended.
        val packId       = packKey.getID()
        val biomeIndexMap = buildBiomeIndexMap(pack)

        // Per-tile surface dump (opt-in). Files land in tiles_<packId>/ next to the CSV by default.
        val dumpDir = if (dumpSurface) {
            val baseDir = dumpDirArg?.let { File(it) }
                ?: File(if (csvPrefix != null) File(csvPrefix).parentFile ?: File(".") else File("."), "tiles_$packId")
            baseDir.mkdirs()
            println("Per-tile surface dump ENABLED -> ${baseDir.absolutePath}")
            baseDir
        } else {
            println("Per-tile surface dump DISABLED (--dump-surface=0)")
            null
        }

        val overflowChecker: TerrainOverflowChecker? = if (overflowEnabled) TerrainOverflowChecker(pack, provider, surfaceProvider, seed) else null
        if (overflowChecker == null) {
            println("Terrain overflow check DISABLED (--overflow-check=0)")
        } else if (overflowChecker.available) {
            val note        = if (overflowChecker.configuredMaxY) "pack-configured" else "fallback — not set in pack"
            val worldStride = overflowChecker.stride * sampleStep
            println("Terrain overflow check ENABLED (blend.terrain.y-range.max = ${overflowChecker.blendMaxY} [$note])")
            println("  Overflow sample stride: every ${overflowChecker.stride} pixels / ~$worldStride world blocks (1 in ${overflowChecker.stride * overflowChecker.stride} pixels)")

            val missingNoiseProps = overflowChecker.scanMissingNoiseProps(pack)
            if (missingNoiseProps.isEmpty()) {
                println("  All pack biomes have terrain.sampler registered.")
            } else {
                println("  Biomes with no terrain.sampler (overflow check N/A — no BiomeNoiseProperties): ${missingNoiseProps.size}")
                missingNoiseProps.forEach { id -> println("    - $id") }
            }
        } else {
            println("Terrain overflow check DISABLED (chunk-generator-noise-3d not detected in pack)")
        }
        println()

        // Build the full traversal order once — shared by warm-up and all worker threads.
        val snakeOrder = buildSnakeOrder(tilesX, tilesY, tileWorldSize)

        // Warm-up: first 4 tiles, single-threaded, no overflow collection.
        // LongArray(1) counters avoid the per-pixel Long autoboxing that HashMap.merge does.
        println("Warming up (4 tiles)...")
        val warmupSurface    = HashMap<String, LongArray>()
        val warmupSubsurface = HashMap<String, LongArray>()
        val warmupFirst    = HashMap<String, IntArray>()
        for ((tx, ty) in snakeOrder.take(4)) {
            renderTile(provider, surfaceProvider, tx, ty, originX, originZ, seed, subsample, imageSize, sampleStep, surfaceY,
                warmupSurface, warmupSubsurface, warmupFirst, checker = null)
        }

        println("Running benchmark ($threadCount thread${if (threadCount == 1) "" else "s"})...")
        println()

        // Divide the snake into threadCount equal-length segments.
        val chunkSize = (snakeOrder.size + threadCount - 1) / threadCount
        val segments  = snakeOrder.chunked(chunkSize)

        val tilesCompleted = AtomicLong(0)
        val executor       = Executors.newFixedThreadPool(threadCount)

        val futures = segments.map { segment ->
            executor.submit(Callable {
                val localSurface    = HashMap<String, LongArray>()
                val localSubsurface = HashMap<String, LongArray>()
                val localFirst    = HashMap<String, IntArray>()
                for ((tileX, tileY) in segment) {
                    renderTile(provider, surfaceProvider, tileX, tileY, originX, originZ, seed, subsample,
                        imageSize, sampleStep, surfaceY, localSurface, localSubsurface,
                        localFirst, overflowChecker, biomeIndexMap, dumpDir)
                    tilesCompleted.incrementAndGet()
                }
                TileRenderResult(localSurface, localSubsurface, localFirst)
            })
        }
        executor.shutdown()

        // Progress: poll every second while threads are running.
        val startTime = System.nanoTime()
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            val done    = tilesCompleted.get()
            val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
            val tps     = if (elapsed > 0.0) done / elapsed else 0.0
            print("\r  Progress: $done/$totalTiles tiles (%.1f tiles/s)".format(tps))
            System.out.flush()
        }

        val elapsedNs  = System.nanoTime() - startTime
        val elapsedMs  = elapsedNs / 1_000_000.0
        val elapsedSec = elapsedMs / 1000.0

        // Merge per-thread results into final maps. Boxing here is fine — it happens
        // once per (thread × unique biome), not once per pixel.
        val surfaceCounts    = HashMap<String, Long>()
        val subsurfaceCounts = HashMap<String, Long>()
        // First-seen coordinate per biome. Futures are iterated in segment order, which is
        // global snake-traversal order, so putIfAbsent keeps the earliest occurrence.
        val firstCoord = HashMap<String, IntArray>()
        for (future in futures) {
            val result = future.get()
            for ((id, counter) in result.surfaceCounts)    surfaceCounts.merge(id, counter[0], Long::plus)
            for ((id, counter) in result.subsurfaceCounts) subsurfaceCounts.merge(id, counter[0], Long::plus)
            for ((id, coord) in result.firstCoord) firstCoord.putIfAbsent(id, coord)
        }

        val tilesPerSecond  = totalTiles / elapsedSec
        val pixelsPerSecond = (totalTiles.toLong() * imageSize * imageSize) / elapsedSec

        println()
        println()
        val worldMaxX = originX + tilesX * tileWorldSize
        val worldMaxZ = originZ + tilesY * tileWorldSize

        println("=== Results ===")
        println("Total time:       %.2f s".format(elapsedSec))
        println("Tiles/second:     %.2f".format(tilesPerSecond))
        println("Pixels/second:    %,.0f".format(pixelsPerSecond))
        println("Avg ms/tile:      %.3f".format(elapsedMs / totalTiles))
        println("World X range:    $originX .. $worldMaxX")
        println("World Z range:    $originZ .. $worldMaxZ")

        val samplerStats   = overflowChecker?.getSamplerStats()
        val noNoiseProps   = overflowChecker?.getNoNoisePropsCounts()
        val evalErrors     = overflowChecker?.getEvalErrorCounts()
        val csvFile  = writeBiomeCsv(packId, csvPrefix, surfaceCounts, subsurfaceCounts,
            firstCoord, biomeIndexMap, samplerStats, noNoiseProps, evalErrors)
        println()
        println("Biome distribution saved to: ${csvFile.absolutePath}")

        // Write the index legend so the raw tile dumps are self-contained.
        if (dumpDir != null) {
            val legend = writeIndexLegend(dumpDir, biomeIndexMap)
            println("Biome index legend saved to:   ${legend.absolutePath}")
        }

        val resultsFile = appendBenchmarkResult(
            tilesX, tilesY, seed, subsample, lod, threadCount, packId, csvPrefix,
            packLoadMs, elapsedSec, tilesPerSecond, pixelsPerSecond, elapsedMs / totalTiles)
        println("Benchmark result appended to:  ${resultsFile.absolutePath}")

        if (overflowChecker?.available == true) {
            val overflowFile = writeOverflowWarnings(tilesX, tilesY, seed, packId, csvPrefix, overflowChecker)
            println()
            println("Terrain overflow warnings:     ${overflowChecker.warningCount}")
            if (overflowChecker.warningCount > 0) {
                println("  Overflow file: ${overflowFile.absolutePath}")
            }

            val firstErrors = overflowChecker.getFirstErrors()
            if (firstErrors.isNotEmpty()) {
                val errorCounts = overflowChecker.getEvalErrorCounts()
                println()
                println("=== Sampler Eval Errors (first exception per biome) ===")
                firstErrors.entries
                    .sortedByDescending { errorCounts[it.key] ?: 0L }
                    .forEach { (biome, msg) ->
                        val count = errorCounts[biome] ?: 0L
                        println("  [$count] $biome: $msg")
                    }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Snake order
    // -------------------------------------------------------------------------

    /**
     * Builds the tile traversal order as a strip-boustrophedon (zigzag) path.
     *
     * The X axis is divided into strips of [stripWidth] columns, where
     * stripWidth = max(1, floor(1000 / tileWorldSize)), keeping the active
     * processing zone within ~1000 world units wide at any point.
     *
     * Within each strip, rows alternate direction (left→right / right→left)
     * and the next strip continues from whichever row the previous strip ended on,
     * keeping consecutive tiles spatially adjacent at every strip boundary.
     */
    internal fun buildSnakeOrder(tilesX: Int, tilesY: Int, tileWorldSize: Int): List<Pair<Int, Int>> {
        val stripWidth = maxOf(1, 1000 / tileWorldSize)
        val result     = ArrayList<Pair<Int, Int>>(tilesX * tilesY)
        var col         = 0
        var startFromTop = true

        while (col < tilesX) {
            val stripEnd = minOf(col + stripWidth, tilesX)
            val rowRange = if (startFromTop) 0 until tilesY else tilesY - 1 downTo 0

            for ((rowIdx, row) in rowRange.withIndex()) {
                if (rowIdx % 2 == 0) {
                    for (c in col until stripEnd)        result.add(c to row)
                } else {
                    for (c in stripEnd - 1 downTo col)  result.add(c to row)
                }
            }

            // The next strip enters from whichever end this strip exited.
            startFromTop = !startFromTop
            col = stripEnd
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Tile rendering
    // -------------------------------------------------------------------------

    private data class TileRenderResult(
        val surfaceCounts: HashMap<String, LongArray>,
        val subsurfaceCounts: HashMap<String, LongArray>,
        val firstCoord: HashMap<String, IntArray>,
    )

    private fun renderTile(
        provider: BiomeProvider,
        surfaceProvider: BiomeProvider,
        tileX: Int,
        tileY: Int,
        originX: Int,
        originZ: Int,
        seed: Long,
        subsample: Int,
        imageSize: Int,
        sampleStep: Int,
        surfaceY: Int,
        surfaceCounts: HashMap<String, LongArray>,
        subsurfaceCounts: HashMap<String, LongArray>,
        firstCoord: HashMap<String, IntArray>,
        checker: TerrainOverflowChecker?,
        biomeIndexMap: BiomeIndexMap? = null,
        dumpDir: File? = null,
    ) {
        val tileWorldSize = TILE_PIXEL_SIZE * subsample
        val worldX = originX + tileX * tileWorldSize
        val worldZ = originZ + tileY * tileWorldSize

        // Surface index buffer, allocated only when dumping (row-major: zi outer, xi inner).
        val surfaceIndexes = if (dumpDir != null && biomeIndexMap != null) IntArray(imageSize * imageSize) else null

        for (zi in 0 until imageSize) {
            for (xi in 0 until imageSize) {
                val px = worldX + xi * sampleStep
                val pz = worldZ + zi * sampleStep

                val surfaceBiome = surfaceProvider.getBiome(px, surfaceY, pz, seed)
                val surfaceCounter = surfaceCounts[surfaceBiome.id]
                if (surfaceCounter == null) surfaceCounts[surfaceBiome.id] = longArrayOf(1L) else surfaceCounter[0]++
                if (!firstCoord.containsKey(surfaceBiome.id)) firstCoord[surfaceBiome.id] = intArrayOf(px, pz)
                if (surfaceIndexes != null) {
                    surfaceIndexes[zi * imageSize + xi] = biomeIndexMap!!.indexOf(surfaceBiome.id)
                }

                val subsurfaceBiome = provider.getBiome(px, 0, pz, seed)
                val subsurfaceCounter = subsurfaceCounts[subsurfaceBiome.id]
                if (subsurfaceCounter == null) subsurfaceCounts[subsurfaceBiome.id] = longArrayOf(1L) else subsurfaceCounter[0]++
                if (!firstCoord.containsKey(subsurfaceBiome.id)) firstCoord[subsurfaceBiome.id] = intArrayOf(px, pz)

                if (checker != null && xi % checker.stride == 0 && zi % checker.stride == 0) {
                    checker.checkPoint(px, pz)
                }
            }
        }

        if (surfaceIndexes != null && dumpDir != null) {
            writeTileDump(dumpDir, tileX, tileY, worldX, worldZ, imageSize, sampleStep, surfaceIndexes)
        }
    }

    private fun getSurfaceProvider(provider: BiomeProvider): BiomeProvider {
        return try {
            val cls = provider::class.java
            if (cls.simpleName == "BiomeExtrusionProvider") {
                cls.getMethod("getDelegate").invoke(provider) as BiomeProvider
            } else {
                provider
            }
        } catch (_: Exception) {
            provider
        }
    }

    // -------------------------------------------------------------------------
    // Biome index map (id -> stable integer index)
    // -------------------------------------------------------------------------

    /**
     * Deterministic, thread-safe biome id -> index map. Seeded in sorted registry order so the
     * indexes are stable across runs of the same pack; any id seen at runtime but absent from the
     * registry (e.g. a synthetic provider wrapper biome) is appended on first lookup, guaranteeing
     * every dumped index can be rectified back to a biome id via [writeIndexLegend].
     */
    internal class BiomeIndexMap {
        private val map  = ConcurrentHashMap<String, Int>()
        private val next = AtomicInteger(0)

        fun indexOf(id: String): Int = map.computeIfAbsent(id) { next.getAndIncrement() }
        fun entriesSortedByIndex(): List<Pair<String, Int>> =
            map.entries.sortedBy { it.value }.map { it.key to it.value }
    }

    private fun buildBiomeIndexMap(pack: ConfigPack): BiomeIndexMap {
        val indexMap = BiomeIndexMap()
        val ids = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        pack.getCheckedRegistry(Biome::class.java).forEach { key, _ -> ids.add(key.id) }
        ids.forEach { indexMap.indexOf(it) }
        return indexMap
    }

    // -------------------------------------------------------------------------
    // File output
    // -------------------------------------------------------------------------

    /**
     * Writes one tile's surface biome-index grid as a self-describing little-endian binary file
     * (see plan / BTSF layout): magic, version, tile coords, world origin of pixel (0,0), width,
     * world stride per pixel, then width*width row-major (z-major) int32 indexes.
     */
    private fun writeTileDump(
        dumpDir: File,
        tileX: Int,
        tileY: Int,
        worldX: Int,
        worldZ: Int,
        width: Int,
        stride: Int,
        surfaceIndexes: IntArray,
    ) {
        // Header: 4-byte magic + 7 int32 fields (version, tileX, tileY, worldX, worldZ, width, stride).
        val headerBytes = 4 + 7 * 4
        val buffer = ByteBuffer.allocate(headerBytes + surfaceIndexes.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf('B'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte()))
        buffer.putInt(1)        // version
        buffer.putInt(tileX)
        buffer.putInt(tileY)
        buffer.putInt(worldX)
        buffer.putInt(worldZ)
        buffer.putInt(width)
        buffer.putInt(stride)
        for (v in surfaceIndexes) buffer.putInt(v)

        File(dumpDir, "tile_${tileX}_${tileY}.bin").outputStream().buffered().use { it.write(buffer.array()) }
    }

    /** Writes the index -> biome id legend so raw tile dumps are self-contained. */
    private fun writeIndexLegend(dumpDir: File, biomeIndexMap: BiomeIndexMap): File {
        val file = File(dumpDir, "index_legend.csv")
        file.bufferedWriter().use { writer ->
            writer.write("Index,Biome")
            writer.newLine()
            for ((id, idx) in biomeIndexMap.entriesSortedByIndex()) {
                writer.write("$idx,$id")
                writer.newLine()
            }
        }
        return file
    }

    private fun writeBiomeCsv(
        packId: String,
        csvPrefix: String?,
        surfaceCounts: HashMap<String, Long>,
        subsurfaceCounts: HashMap<String, Long>,
        firstCoord: HashMap<String, IntArray>,
        biomeIndexMap: BiomeIndexMap,
        samplerStats: Map<String, SamplerTiming>? = null,
        noNoiseProps: Map<String, Long>? = null,
        evalErrors: Map<String, Long>? = null,
    ): File {
        val file = if (csvPrefix != null) File("${csvPrefix}${packId}.csv")
                   else File("benchmark_${packId}.csv")

        val surfaceTotal    = surfaceCounts.values.sum().coerceAtLeast(1L)
        val subsurfaceTotal = subsurfaceCounts.values.sum().coerceAtLeast(1L)
        val overflowEnabled = samplerStats != null
        val allBiomes       = (surfaceCounts.keys + subsurfaceCounts.keys +
                               (samplerStats?.keys ?: emptySet()) +
                               (noNoiseProps?.keys ?: emptySet()) +
                               (evalErrors?.keys ?: emptySet()))
                               .toSortedSet(String.CASE_INSENSITIVE_ORDER)

        file.bufferedWriter().use { writer ->
            val header = buildString {
                append("Biome,Biome Index,Surface Count,Surface %,Subsurface Count,Subsurface %")
                append(",First X,First Z")
                if (overflowEnabled) append(",Overflow Samples,Avg Sampler µs,No Noise Props,Eval Errors")
            }
            writer.write(header)
            writer.newLine()
            for (id in allBiomes) {
                val sc  = surfaceCounts[id] ?: 0L
                val ssc = subsurfaceCounts[id] ?: 0L
                val first = firstCoord[id]
                val idx = biomeIndexMap.indexOf(id)
                val row = buildString {
                    append("$id,$idx,$sc,${"%.4f".format(sc * 100.0 / surfaceTotal)},$ssc,${"%.4f".format(ssc * 100.0 / subsurfaceTotal)}")
                    append(",${first?.get(0) ?: ""},${first?.get(1) ?: ""}")
                    if (overflowEnabled) {
                        val st    = samplerStats?.get(id)
                        val count = st?.sampleCount ?: 0L
                        val avgUs = if (st != null && st.sampleCount > 0L) st.avgMicros else 0.0
                        val nnp   = noNoiseProps?.get(id) ?: 0L
                        val err   = evalErrors?.get(id) ?: 0L
                        append(",$count,${"%.2f".format(avgUs)},$nnp,$err")
                    }
                }
                writer.write(row)
                writer.newLine()
            }
        }
        return file
    }

    private fun writeOverflowWarnings(
        tilesX: Int,
        tilesY: Int,
        seed: Long,
        packId: String,
        csvPrefix: String?,
        checker: TerrainOverflowChecker,
    ): File {
        val dir  = if (csvPrefix != null) File(csvPrefix).parentFile ?: File(".") else File(".")
        val file = File(dir, "terrain_overflow_${tilesX}x${tilesY}_seed${seed}_${packId}.txt")
        file.parentFile?.mkdirs()

        file.bufferedWriter().use { writer ->
            val note = if (checker.configuredMaxY) "pack-configured" else "fallback, not set in pack"
            writer.write("# Terrain overflow check: blend.terrain.y-range.max=${checker.blendMaxY} ($note)")
            writer.newLine()
            writer.write("# Tiles: ${tilesX}x${tilesY}, seed: $seed, pack: $packId")
            writer.newLine()
            writer.write("# Positions where terrain.sampler + terrain.sampler-2d density > 0 at y=${checker.blendMaxY}")
            writer.newLine()
            if (checker.warningCount == 0) {
                writer.write("# No overflow detected")
                writer.newLine()
            } else {
                for (warning in checker.getWarnings()) {
                    writer.write(warning)
                    writer.newLine()
                }
            }
        }
        return file
    }

    private fun appendBenchmarkResult(
        tilesX: Int,
        tilesY: Int,
        seed: Long,
        subsample: Int,
        lod: Int,
        threadCount: Int,
        packId: String,
        csvPrefix: String?,
        packLoadMs: Double,
        totalTimeSec: Double,
        tilesPerSecond: Double,
        pixelsPerSecond: Double,
        avgMsPerTile: Double,
    ): File {
        val dir  = if (csvPrefix != null) File(csvPrefix).parentFile ?: File(".") else File(".")
        val file = File(dir, "benchmark_results.csv")
        val isNew = !file.exists()
        file.parentFile?.mkdirs()

        java.io.FileWriter(file, /* append= */ true).buffered().use { writer ->
            if (isNew) {
                writer.write("Timestamp,Pack,TilesX,TilesY,Seed,Subsample,LOD,Threads,PackLoad_ms,TotalTime_s,Tiles_per_s,Pixels_per_s,AvgMs_per_tile")
                writer.newLine()
            }
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            writer.write("$ts,$packId,$tilesX,$tilesY,$seed,$subsample,$lod,$threadCount,${"%.1f".format(packLoadMs)},${"%.2f".format(totalTimeSec)},${"%.2f".format(tilesPerSecond)},${"%.0f".format(pixelsPerSecond)},${"%.3f".format(avgMsPerTile)}")
            writer.newLine()
        }
        return file
    }
}

// =============================================================================
// Terrain overflow checker + sampler timing
// =============================================================================

private data class SamplerTiming(val sampleCount: Long, val avgNs: Double) {
    val avgMicros: Double get() = avgNs / 1000.0
}

/**
 * Checks whether any biome at a given (x, z) generates terrain at or above the blend
 * y-range ceiling by evaluating terrain.sampler (3D) + terrain.sampler-2d (2D) * weight
 * at y = blendMaxY.
 *
 * Fully thread-safe: all Method objects are resolved once via a lazy initialiser
 * (Kotlin's default SYNCHRONIZED lazy mode) before the hot path runs.  The warnings
 * list is a synchronized list so concurrent threads can append freely.
 */
private class TerrainOverflowChecker(
    pack: ConfigPack,
    private val provider: BiomeProvider,
    private val surfaceProvider: BiomeProvider,
    private val seed: Long,
    val stride: Int = 1,
) {
    val blendMaxY: Int
    val configuredMaxY: Boolean
    val available: Boolean

    private val warnings: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // Per-biome sampler timing — LongAdder is designed for concurrent updates without contention.
    private val biomeSamplerNs    = ConcurrentHashMap<String, LongAdder>()
    private val biomeSamplerCount = ConcurrentHashMap<String, LongAdder>()

    // Diagnostic counters: track why checkPoint exits early per biome.
    // noNoiseProps: provider returned a biome with no BiomeNoiseProperties in its context.
    // evalErrors:   an exception was thrown during the reflection / sampler evaluation chain.
    private val biomeNoNoiseProps = ConcurrentHashMap<String, LongAdder>()
    private val biomeEvalErrors   = ConcurrentHashMap<String, LongAdder>()
    // First root-cause exception message per biome — stored once for diagnosis.
    private val biomeFirstError   = ConcurrentHashMap<String, String>()

    // PropertyKey<BiomeNoiseProperties> from Context's static class→key map, and the
    // Context.get(PropertyKey) method — both resolved once in the constructor.
    private val noisePropsKey: Any?
    private val contextGetWithKey: Method?

    // All Method objects needed in the hot path, bundled and lazily resolved on first
    // checkPoint call.  Kotlin lazy{} defaults to SYNCHRONIZED mode, so resolution is
    // guaranteed to run exactly once even under concurrent access.
    private data class ResolvedMethods(
        val samplers: Method,
        val base: Method,
        val elevation: Method,
        val elevWeight: Method,
        val sample3D: Method,
        val sample2D: Method,
        val sample3DDouble: Boolean,
        val sample2DDouble: Boolean,
    )

    private val methods: ResolvedMethods? by lazy { tryResolveMethods() }

    init {
        // --- blendMaxY ---
        val rawMax = try {
            val cfg = pack.context.getByClassName(
                "com.dfsek.terra.addons.chunkgenerator.config.NoiseChunkGeneratorPackConfigTemplate"
            )
            cfg?.javaClass?.getMethod("getBlendMaxY")?.invoke(cfg) as? Int ?: Int.MAX_VALUE
        } catch (_: Exception) { Int.MAX_VALUE }

        if (rawMax == Int.MAX_VALUE) {
            blendMaxY      = 320
            configuredMaxY = false
        } else {
            blendMaxY      = rawMax
            configuredMaxY = true
        }

        // --- PropertyKey for BiomeNoiseProperties ---
        // Context.create() stores class→key pairs in a static HashMap.  Locate the entry
        // by class name so the addon JAR need not be on the compile classpath.
        var resolvedKey: Any? = null
        var resolvedGetMethod: Method? = null
        var resolvedAvailable = false
        try {
            val propertiesField = Context::class.java.getDeclaredField("properties")
            propertiesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = propertiesField.get(null) as Map<*, *>
            resolvedKey = map.entries.firstOrNull { entry ->
                (entry.key as? Class<*>)?.name ==
                    "com.dfsek.terra.addons.chunkgenerator.config.noise.BiomeNoiseProperties"
            }?.value

            if (resolvedKey != null) {
                resolvedGetMethod  = Context::class.java.getMethod("get", PropertyKey::class.java)
                resolvedAvailable  = true
            }
        } catch (_: Exception) {
            // addon not loaded — checker will be silently inactive
        }

        noisePropsKey      = resolvedKey
        contextGetWithKey  = resolvedGetMethod
        available          = resolvedAvailable
    }

    // Invoked at most once (by lazy{}), on the first checkPoint call.
    private fun tryResolveMethods(): ResolvedMethods? {
        if (!available) return null
        return try {
            // Use surfaceProvider (unwrapped 2D pipeline) — y is ignored by the pipeline so any
            // value works.  The extruded provider may return synthetic wrapper biomes at blendMaxY
            // that were never registered with the NOISE_3D addon and therefore have no noise properties.
            val biome      = surfaceProvider.getBiome(0, 0, 0, seed)
            val noiseProps = contextGetWithKey!!.invoke(biome.context, noisePropsKey) ?: return null

            val samplersM    = noiseProps.javaClass.getMethod("samplers")
            val samplers     = samplersM.invoke(noiseProps) ?: return null
            val baseM        = samplers.javaClass.getMethod("base")
            val elevM        = samplers.javaClass.getMethod("elevation")
            val elevWeightM  = samplers.javaClass.getMethod("elevationWeight")

            val base         = baseM.invoke(samplers) ?: return null
            val elevation    = elevM.invoke(samplers) ?: return null

            val sample3DM    = preferDoubleOverload(base, 4)      ?: return null
            val sample2DM    = preferDoubleOverload(elevation, 3) ?: return null

            ResolvedMethods(
                samplers    = samplersM,
                base        = baseM,
                elevation   = elevM,
                elevWeight  = elevWeightM,
                sample3D    = sample3DM,
                sample2D    = sample2DM,
                sample3DDouble = sample3DM.parameterTypes[1] == Double::class.javaPrimitiveType,
                sample2DDouble = sample2DM.parameterTypes[1] == Double::class.javaPrimitiveType,
            )
        } catch (_: Exception) { null }
    }

    fun checkPoint(x: Int, z: Int) {
        val m = methods ?: return
        var biomeId = "<unknown>"
        try {
            val biome  = provider.getBiome(x, blendMaxY, z, seed)
            biomeId    = biome.id

            val noisePropsRaw = contextGetWithKey!!.invoke(biome.context, noisePropsKey)
            if (noisePropsRaw == null) {
                biomeNoNoiseProps.computeIfAbsent(biomeId) { LongAdder() }.increment()
                return
            }

            val samplers   = m.samplers.invoke(noisePropsRaw) ?: return
            val base       = m.base.invoke(samplers)          ?: return
            val elevation  = m.elevation.invoke(samplers)     ?: return
            val elevWeight = m.elevWeight.invoke(samplers) as? Double ?: 1.0

            val t0 = System.nanoTime()

            val density3d = if (m.sample3DDouble) {
                m.sample3D.invoke(base, seed, x.toDouble(), blendMaxY.toDouble(), z.toDouble()) as? Double
            } else {
                m.sample3D.invoke(base, seed, x, blendMaxY, z) as? Double
            } ?: return

            val density2d = if (m.sample2DDouble) {
                m.sample2D.invoke(elevation, seed, x.toDouble(), z.toDouble()) as? Double ?: 0.0
            } else {
                m.sample2D.invoke(elevation, seed, x, z) as? Double ?: 0.0
            }

            val elapsedNs = System.nanoTime() - t0
            biomeSamplerNs.computeIfAbsent(biomeId) { LongAdder() }.add(elapsedNs)
            biomeSamplerCount.computeIfAbsent(biomeId) { LongAdder() }.increment()

            val density = density3d + density2d * elevWeight
            if (density > 0.0) {
                warnings.add("x=$x z=$z y=$blendMaxY biome=$biomeId density=${"%.6f".format(density)}")
            }
        } catch (e: Exception) {
            biomeEvalErrors.computeIfAbsent(biomeId) { LongAdder() }.increment()
            biomeFirstError.putIfAbsent(biomeId, rootCause(e))
        }
    }

    private fun preferDoubleOverload(target: Any, paramCount: Int): Method? {
        // Resolve getSample from the root seismic Sampler interface so the Method is valid
        // for ANY Sampler implementation, not just the probe biome's concrete class.
        // Without this, calling a Method declared on e.g. NoiseFunction against a
        // DeferredExpressionSampler throws "not an instance of NoiseFunction".
        val source = findRootSamplerInterface(target.javaClass) ?: target.javaClass
        val candidates = source.methods.filter { m ->
            m.name == "getSample" && m.parameterCount == paramCount
        }
        return candidates.firstOrNull { m -> m.parameterTypes[1] == Double::class.javaPrimitiveType }
            ?: candidates.firstOrNull()
    }

    /** Walks the full type hierarchy to find the root com.dfsek.seismic Sampler interface. */
    private fun findRootSamplerInterface(cls: Class<*>): Class<*>? {
        if (cls.isInterface && cls.simpleName == "Sampler" &&
            cls.name.startsWith("com.dfsek.seismic")) return cls
        for (iface in cls.interfaces) {
            findRootSamplerInterface(iface)?.let { return it }
        }
        return cls.superclass?.let { findRootSamplerInterface(it) }
    }

    val warningCount: Int get() = warnings.size
    fun getWarnings(): List<String> = warnings.toList()

    fun getSamplerStats(): Map<String, SamplerTiming> =
        biomeSamplerCount.entries.associate { (id, countAdder) ->
            val count   = countAdder.sum()
            val totalNs = biomeSamplerNs[id]?.sum() ?: 0L
            id to SamplerTiming(count, if (count > 0L) totalNs.toDouble() / count else 0.0)
        }

    fun getNoNoisePropsCounts(): Map<String, Long> =
        biomeNoNoiseProps.entries.associate { (id, adder) -> id to adder.sum() }

    fun getEvalErrorCounts(): Map<String, Long> =
        biomeEvalErrors.entries.associate { (id, adder) -> id to adder.sum() }

    fun getFirstErrors(): Map<String, String> = HashMap(biomeFirstError)

    private fun rootCause(e: Exception): String {
        var cause: Throwable = e
        while (cause is java.lang.reflect.InvocationTargetException && cause.cause != null) {
            cause = cause.cause!!
        }
        return "${cause.javaClass.simpleName}: ${cause.message ?: "(no message)"}"
    }

    /**
     * Scans every biome in the pack registry once at startup and returns the IDs of those
     * that have no BiomeNoiseProperties in their context (no terrain.sampler in their config
     * or inheritance chain, or a failure during the NOISE_3D event-load for that biome).
     * Returns an empty list if the scan cannot run (unavailable, registry access failed, etc.).
     */
    fun scanMissingNoiseProps(pack: ConfigPack): List<String> {
        if (!available) return emptyList()
        return try {
            val missing = mutableListOf<String>()
            pack.getCheckedRegistry(Biome::class.java).forEach { key, biome ->
                val hasProps = try {
                    contextGetWithKey!!.invoke(biome.context, noisePropsKey) != null
                } catch (_: Exception) { false }
                if (!hasProps) missing.add(key.id)
            }
            missing.sorted()
        } catch (_: Exception) { emptyList() }
    }
}

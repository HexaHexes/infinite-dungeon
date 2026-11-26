package org.bitwisemadness.infinitedungeon.magicule;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeonapi.magicule.AmbientMagicule;
import org.bitwisemadness.infinitedungeonapi.magicule.ConcentrationModifier;
import org.bitwisemadness.infinitedungeonapi.magicule.RadiusScope;

/**
 * The magicule elevation system (plan section 7, issue #218): sources are
 * WORLD DATA - recorded when they appear, re-registered from
 * {@link ElevationSources} when a level loads - because the API's modifier
 * registry is in-memory only and chunks that generated before a restart never
 * generate again. Registration flows: level load re-feeds the level's recorded
 * sources exactly once; level unload removes precisely what that level added
 * (the position-keyed map makes both idempotent); new sources arrive through
 * {@link #registerSource}, which the source families call - chunk-load
 * derivation for Dungeon Crawl dungeons (the section 7 headline: dungeon
 * mouths become magicule-rich), and the split families #223 (portals) and
 * #224 (tower entrances) when they land.
 *
 * <p>The DC family: a loaded chunk asks the structure registry for
 * {@code dungeoncrawl:dungeon} (ModList-gated, so absence is a no-op) and
 * registers a source at its own surface exactly when one of the structure
 * references the chunk carries points AT that chunk - the reference set is the
 * structure-start chunk position, so each dungeon registers once, at its
 * start, with no stacking. Sources carry position only: a sphere applies at
 * its coordinates in every dimension (the record's "amount and radius" wording
 * as written; the precision follow-up is tracked in the API repo).</p>
 */
public final class MagiculeElevation {

    private static final ResourceLocation DUNGEON_CRAWL_DUNGEON =
            ResourceLocation.parse("dungeoncrawl:dungeon");

    /**
     * Registered modifiers per level, keyed by the exact recorded position:
     * unload removes exactly what load added, and repeated derivation cannot
     * double-register. Server-thread only (the event paths), like every
     * listener here.
     */
    private static final Map<ResourceKey<Level>, Map<BlockPos, ConcentrationModifier>> REGISTERED =
            new HashMap<>();

    private MagiculeElevation() {
    }

    /**
     * Records (if new) and registers one elevation source.
     *
     * @return true when the position was newly recorded (and registered now);
     *         false when it was already recorded - the dedupe, so a family may
     *         call this from every chunk that happens to see the source
     */
    public static boolean registerSource(ServerLevel level, BlockPos pos) {
        if (!ElevationSources.of(level).add(pos)) {
            return false;
        }
        apply(level, pos);
        return true;
    }

    /** Removes a recorded source and its registered modifier. */
    public static void removeSource(ServerLevel level, BlockPos pos) {
        ElevationSources.of(level).remove(pos);
        Map<BlockPos, ConcentrationModifier> byPosition =
                REGISTERED.get(level.dimension());
        if (byPosition != null) {
            ConcentrationModifier modifier = byPosition.remove(pos);
            if (modifier != null) {
                AmbientMagicule.removeModifier(modifier);
            }
        }
    }

    /** Level start: re-register every recorded source of this level exactly once. */
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, ConcentrationModifier> existing = REGISTERED.get(level.dimension());
        if (existing != null) {
            // Defensive: a second load replaces rather than doubles.
            existing.values().forEach(AmbientMagicule::removeModifier);
        }
        Map<BlockPos, ConcentrationModifier> registered = new HashMap<>();
        REGISTERED.put(level.dimension(), registered);
        for (BlockPos pos : ElevationSources.of(level).all()) {
            ConcentrationModifier modifier = makeModifier(pos);
            AmbientMagicule.registerModifier(modifier);
            registered.put(pos, modifier);
        }
    }

    /** Level stop: the in-memory registry keeps nothing for an unloaded level. */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, ConcentrationModifier> registered =
                REGISTERED.remove(level.dimension());
        if (registered != null) {
            registered.values().forEach(AmbientMagicule::removeModifier);
        }
    }

    /**
     * The DC-dungeon family: when a chunk carrying a
     * {@code dungeoncrawl:dungeon} structure reference loads and that reference
     * points at THIS chunk (the dungeon's start), register a surface-hugging
     * source here - once per dungeon, by construction, with SavedData as the
     * belt against re-registration across sessions.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registerChunkSources(level, event.getChunk());
        if (!ModList.get().isLoaded("dungeoncrawl")) {
            return;
        }
        Structure dungeon = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(DUNGEON_CRAWL_DUNGEON);
        if (dungeon == null) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        var references = chunk.getReferencesForStructure(dungeon);
        if (references.isEmpty()) {
            return;
        }
        var iterator = references.iterator();
        while (iterator.hasNext()) {
            if (new ChunkPos(iterator.nextLong()).equals(chunk.getPos())) {
                // The start sits in this chunk: the mouth's neighbourhood is THIS chunk's surface, so
                // the sphere hugs where players and mobs actually stand (the start chunk IS this
                // chunk, so the loaded chunk's heightmap is the right one by construction).
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
                registerSource(level, new BlockPos(
                        chunk.getPos().getMiddleBlockX(), surface,
                        chunk.getPos().getMiddleBlockZ()));
                return;
            }
        }
    }

    /**
     * The tower-entrance family (issue #224, riding #199): the tower's active
     * dungeon portal is its signature - the feature places it at generation,
     * seed-derived, so pregenerated and organic chunks converge - and each
     * chunk load registers a source where a portal stands. The section scan
     * is palette-first ({@code maybeHas}), so chunks without a portal cost a
     * per-section palette check only; SavedData dedupes across loads.
     */
    public static void registerChunkSources(ServerLevel level, ChunkAccess chunk) {
        if (!(chunk instanceof LevelChunk levelChunk)) {
            return;
        }
        BlockPos lowestPortal = findLowestPortal(levelChunk);
        if (lowestPortal != null) {
            registerSource(level, lowestPortal);
        }
    }

    private static BlockPos findLowestPortal(LevelChunk chunk) {
        Block portalBlock = PortalBlocks.DUNGEON_PORTAL.get();
        BlockPos found = null;
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(portalBlock))) {
                continue;
            }
            int sectionY = chunk.getSectionYFromSectionIndex(index);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).is(portalBlock)) {
                            BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x,
                                    SectionPos.sectionToBlockCoord(sectionY, y),
                                    chunk.getPos().getMinBlockZ() + z);
                            if (found == null || pos.getY() < found.getY()) {
                                found = pos;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    private static void apply(ServerLevel level, BlockPos pos) {
        ConcentrationModifier modifier = makeModifier(pos);
        REGISTERED.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(pos, modifier);
        AmbientMagicule.registerModifier(modifier);
    }

    /**
     * One source as the API expresses it: additive amount over a sphere of the
     * configured radius - both read here, so restarts pick up config changes
     * over the same recorded positions. Position-only scope by the record as
     * written (see the API repo's dimension-aware follow-up for precision).
     */
    private static ConcentrationModifier makeModifier(BlockPos pos) {
        return ConcentrationModifier.additive(DungeonConfigs.structureElevationAmount(),
                RadiusScope.around(pos.getX(), pos.getY(), pos.getZ(),
                        DungeonConfigs.structureElevationRadius()));
    }
}

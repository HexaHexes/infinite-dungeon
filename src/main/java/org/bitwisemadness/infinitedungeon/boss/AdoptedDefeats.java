package org.bitwisemadness.infinitedungeon.boss;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Which adopted declarations have earned their defeat (issue #255, D40's
 * credit rule): a declaration's id is stamped <em>only</em> when its entity
 * died to a player. An uncredited death - environmental, mob, void - stamps
 * nothing, so the record stays clear and the source that produced the fight
 * can fire again: a {@code required} condition downstream can never be stuck
 * shut by a death nobody earned.
 *
 * <p>Its own SavedData beside {@code LayerUnlocks}, {@code GuardianDefeats},
 * and {@code MinibossSlain} - a fourth distinct concern (adopted, not
 * scheduled), never merged into any. Storage is one newline-joined string of
 * ids: CompoundTag has no string-list methods (the recorded truth), and a
 * corrupt entry skips rather than poisoning the record ("never unlock on a
 * guess").</p>
 */
public final class AdoptedDefeats extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String STORAGE_KEY = "infinite_dungeon_adopted_defeats";

    private static final String SEPARATOR = "\n";

    private static final Factory<AdoptedDefeats> FACTORY =
            new Factory<>(AdoptedDefeats::new, AdoptedDefeats::load);

    private final Set<ResourceLocation> defeated = new HashSet<>();

    private AdoptedDefeats() {
    }

    /** The record of a level's saved data. */
    public static AdoptedDefeats of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Whether the declaration's fight has been earned (a player killed it). */
    public boolean isDefeated(ResourceLocation declarationId) {
        return defeated.contains(declarationId);
    }

    /** Stamps the fight earned (idempotent) - the credit path only. */
    public void stamp(ResourceLocation declarationId) {
        if (defeated.add(declarationId)) {
            setDirty();
        }
    }

    /** Forgets the stamp (test hygiene; no production caller). */
    public void forget(ResourceLocation declarationId) {
        if (defeated.remove(declarationId)) {
            setDirty();
        }
    }

    private static AdoptedDefeats load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new AdoptedDefeats();
        String raw = tag.getString("defeated");
        if (raw.isEmpty()) {
            return data;
        }
        for (String entry : raw.split(SEPARATOR)) {
            if (entry.isBlank()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                LOGGER.warn("Skipping corrupt adopted-defeat entry: {}", entry);
                continue; // foreign/corrupt: never credit on a guess
            }
            data.defeated.add(id);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("defeated", String.join(SEPARATOR, defeated.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList()));
        return tag;
    }
}

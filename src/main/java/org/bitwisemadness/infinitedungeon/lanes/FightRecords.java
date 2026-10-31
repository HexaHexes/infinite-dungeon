package org.bitwisemadness.infinitedungeon.lanes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who has had their fight, per encounter scope (issue #259, the per-player
 * half of api#85's D39 semantics). Scopes pack like {@code MinibossSlain}'s
 * keys - {@code layer << 32 | floor}, with floor {@link #GUARDIAN} (-1) for a
 * layer guardian and the real floor for a miniboss - so one store serves both
 * paths beside {@code LayerUnlocks}, {@code GuardianDefeats} and
 * {@code MinibossSlain} as its own distinct concern.
 *
 * <p>Serialization is one string per (scope, player) entry - {@code
 * "scope;hi;lo"} - so no parallel-array alignment can ever drift on load.
 * The record is written at the moment the encounter falls (the consumers
 * collect the players present); the flag only ever grows, matching the gate
 * family's "only ever opens" spirit.</p>
 */
public final class FightRecords extends SavedData {

    /** The scope marker for a layer guardian (floors are >= 0). */
    public static final int GUARDIAN = -1;

    private static final String STORAGE_KEY = "infinite_dungeon_fight_records";

    private static final Factory<FightRecords> FACTORY =
            new Factory<>(FightRecords::new, FightRecords::load);

    private final Map<Long, Set<UUID>> fights = new HashMap<>();

    private FightRecords() {
    }

    /** The records of a level's saved data. */
    public static FightRecords of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Whether this player already had the scope's fight. */
    public boolean hasFought(int layer, int floor, UUID player) {
        Set<UUID> scope = fights.get(key(layer, floor));
        return scope != null && scope.contains(player);
    }

    /** Marks the scope's fight for these players (idempotent per player). */
    public void recordFight(int layer, int floor, Collection<UUID> players) {
        Set<UUID> scope = fights.computeIfAbsent(key(layer, floor), ignored -> new HashSet<>());
        boolean changed = false;
        for (UUID player : players) {
            changed |= scope.add(player);
        }
        if (changed) {
            setDirty();
        }
    }

    /** Forgets a scope's records (test hygiene; no production caller). */
    public void forget(int layer, int floor) {
        if (fights.remove(key(layer, floor)) != null) {
            setDirty();
        }
    }

    /**
     * Records everyone within {@code radius} of the fall - plus the credited
     * killer from the death event's source, wherever they stood - as having
     * had the scope's fight (D39's "present when it fell" half). Called from
     * both consumers' death paths; a few set inserts at a death, never per
     * tick. The killer may be null (environmental deaths) and non-player
     * killers are never credited - presence covers melee killers anyway.
     */
    public static void recordPresentAt(ServerLevel level, int layer, int floor,
            LivingEntity fallen, Entity killer, double radius) {
        double squared = radius * radius;
        var present = new ArrayList<UUID>();
        for (ServerPlayer candidate : level.players()) {
            if (candidate.distanceToSqr(fallen.position()) <= squared) {
                present.add(candidate.getUUID());
            }
        }
        if (killer instanceof Player credited && !present.contains(credited.getUUID())) {
            present.add(credited.getUUID());
        }
        if (!present.isEmpty()) {
            of(level).recordFight(layer, floor, present);
        }
    }

    /** The packed scope key: layer in the high bits, floor in the low. */
    public static long key(int layer, int floor) {
        return (((long) layer) << 32) | (floor & 0xFFFFFFFFL);
    }

    /** Loads records from NBT; package-visible so the harness round-trips them. */
    static FightRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new FightRecords();
        String packed = tag.getString("fights");
        if (packed.isEmpty()) {
            return data;
        }
        for (String entry : packed.split("\n")) {
            String[] parts = entry.split(";", 3);
            if (parts.length != 3) {
                continue; // foreign/corrupt line: never invent a fought player
            }
            try {
                long scope = Long.parseLong(parts[0]);
                UUID player = new UUID(Long.parseUnsignedLong(parts[1]), Long.parseUnsignedLong(parts[2]));
                data.fights.computeIfAbsent(scope, ignored -> new HashSet<>()).add(player);
            } catch (NumberFormatException ignored) {
                // unparseable line skipped whole - same posture as GuardianDefeats' tags
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ArrayList<String>(countEntries());
        fights.forEach((scope, players) -> players.forEach(player ->
                entries.add(scope + ";" + Long.toUnsignedString(player.getMostSignificantBits())
                        + ";" + Long.toUnsignedString(player.getLeastSignificantBits()))));
        tag.putString("fights", String.join("\n", entries));
        return tag;
    }

    private int countEntries() {
        int total = 0;
        for (Set<UUID> scope : fights.values()) {
            total += scope.size();
        }
        return total;
    }
}

package org.bitwisemadness.infinitedungeon.boss;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * Boss bars (issue #253, D35 presentation-only): a vanilla
 * {@link ServerBossEvent} follows each fight entity - guardian (#42/#43),
 * miniboss (#41), adopted boss (#255) - while it walks the level.
 *
 * <p>Registration is an explicit {@link #track} call at each spawn site, never
 * an entity-join listener: both sites tag the entity <em>after</em>
 * {@code addFreshEntity}, so a join-time tag check would always miss the
 * initial spawn, and ambient spawns - which never call {@code track} - are
 * excluded structurally rather than by a filter that could misfire.</p>
 *
 * <p>The name is the entity's own display name: the roster picks which entity
 * is the boss, and no profile carries a boss display name (verified 2026-10-31
 * - {@code BossCandidate} and the api profiles hold none). Progress and
 * audience live in one tick listener: progress follows health so damage, heal,
 * and death need no per-cause events; the audience is every player in the
 * level, the awakening announcement's own audience; and anything no longer
 * walking the level (death, despawn, unload) is reaped there, so no removal
 * path needs its own hook.</p>
 *
 * <p>Zero gameplay effect by construction: this class only reads health and
 * talks to {@code ServerBossEvent} - it cannot reach gating, sealing, or
 * loot. The {@code boss.bossBar} switch is honoured at spawn and applied live
 * (a mid-session off reaps every bar on the next tick).</p>
 */
public final class BossBars {

    /**
     * Fight entity to its bar. Cross-level by design (a lobby or dungeon
     * fight each tick with their own level's players); every entry is owned by
     * the level its entity stands in, which the tick checks before touching it.
     */
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    /** One fight: the entity whose health the bar mirrors, and the bar. */
    private record Tracked(Mob boss, ServerBossEvent bar) {
    }

    private BossBars() {
    }

    /**
     * Starts presenting the entity as a boss fight (spawn sites call this
     * after tagging; #255's site calls the same seam). Idempotent per entity
     * and honours the config at the door.
     */
    public static void track(ServerLevel level, Mob boss) {
        if (!DungeonConfigs.bossBarEnabled()) {
            return;
        }
        UUID id = boss.getUUID();
        if (TRACKED.containsKey(id)) {
            return;
        }
        TRACKED.put(id, new Tracked(boss, new ServerBossEvent(
                boss.getDisplayName(), BossBarColor.RED, BossBarOverlay.NOTCHED_10)));
    }

    /** Whether this entity is currently presented (tests; no production caller). */
    public static boolean isTracked(Mob boss) {
        return TRACKED.containsKey(boss.getUUID());
    }

    /** The entity's live bar, empty when untracked (tests; no production caller). */
    public static Optional<ServerBossEvent> barFor(Mob boss) {
        Tracked tracked = TRACKED.get(boss.getUUID());
        return tracked == null ? Optional.empty() : Optional.of(tracked.bar());
    }

    /** Forgets every bar (test teardown; no production caller). */
    public static void clear() {
        TRACKED.values().forEach(tracked -> tracked.bar().removeAllPlayers());
        TRACKED.clear();
    }

    /**
     * The single lifecycle listener: progress, audience, and reaping. Runs per
     * level, so an entry belonging to another level is left for that level's
     * own tick rather than reaped here.
     */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || TRACKED.isEmpty()) {
            return;
        }
        if (!DungeonConfigs.bossBarEnabled()) {
            clear(); // the off switch applies live, not only at spawn
            return;
        }
        List<ServerPlayer> players = level.players();
        var iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next().getValue();
            Mob boss = tracked.boss();
            if (boss.isRemoved() || boss.isDeadOrDying()) {
                tracked.bar().removeAllPlayers();
                iterator.remove();
                continue;
            }
            if (boss.level() != level) {
                continue; // another level's tick owns this fight
            }
            ServerBossEvent bar = tracked.bar();
            float maximum = boss.getMaxHealth();
            bar.setProgress(maximum > 0.0F ? boss.getHealth() / maximum : 0.0F);
            // Both calls dedupe internally, so this is packet-safe every tick.
            for (ServerPlayer player : players) {
                bar.addPlayer(player);
            }
            for (ServerPlayer member : List.copyOf(bar.getPlayers())) {
                if (!players.contains(member)) {
                    bar.removePlayer(member);
                }
            }
        }
    }
}

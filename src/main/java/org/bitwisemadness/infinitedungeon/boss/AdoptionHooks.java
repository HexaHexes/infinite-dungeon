package org.bitwisemadness.infinitedungeon.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeonapi.event.BossSlainEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * The adoption rule end to end (issue #255, D40).
 *
 * <p><b>On spawn</b> ({@link EntityJoinLevelEvent}): a declared type is
 * tagged as its context's boss - the join check <em>is</em> the declaration
 * (a type check, so unlike a tag check it can never miss the moment), which
 * is also what keeps ambient spawns excluded structurally. The tags are the
 * identity: the adopted mark, the declaration id (the defeat record's key),
 * and, inside a dungeon lane, the layer where it appeared - identity decided
 * at spawn and surviving restarts, the guardian chain's own rule, and on a
 * namespace of its own so guardian logic never mistakes an adopted warden
 * for its scheduled guardian. A {@link Mob} is also presented through
 * {@link BossBars#track} ("defeat tracking <em>and</em> presentation").</p>
 *
 * <p><b>On death</b> ({@link LivingDeathEvent}): the D40 credit rule splits
 * two effects. A <em>player-kill</em> stamps {@link AdoptedDefeats} - the
 * record downstream conditions read - and carries the killer's id into the
 * event; an uncredited death stamps nothing, so the record stays clear and
 * the source can fire again. The {@link BossSlainEvent} notification posts
 * for either death <em>inside a dungeon layer</em> (the layer tag is the
 * context gate), with attribution present on credit and the documented
 * unknown (ADR-0003) otherwise - the guardian poster's posture. Gating is
 * untouched: its listener validates the roster's guardian id, and adopted
 * entities have no roster entry, so no adopted kill can unlock anything by
 * accident. The validated {@code LayerCompletedEvent}/{@code MinibossSlainEvent}
 * posters stay the scheduled chains' own - adoption never announces a
 * completion it does not own.</p>
 */
public final class AdoptionHooks {

    /** Marks an entity as adopted content's boss. */
    public static final String ADOPTED_TAG = "infinite_dungeon_adopted";

    /**
     * Prefix of the declaration-id tag: suffix is the datapack file id. The
     * {@code id_} segment keeps it disjoint from the layer prefix below -
     * {@code infinite_dungeon_adopted_layer_96} would otherwise start with a
     * bare {@code infinite_dungeon_adopted_} and misparse as an id.
     */
    public static final String ID_TAG_PREFIX = "infinite_dungeon_adopted_id_";

    /** Prefix of the spawn-layer tag - adopted's own namespace, never the guardian's. */
    public static final String LAYER_TAG_PREFIX = "infinite_dungeon_adopted_layer_";

    private AdoptionHooks() {
    }

    /** Tags and presents a declared type the moment it enters a level. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Entity entity = event.getEntity();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        var declaration = Adoptions.find(event.getLevel().dimension(), typeId.toString());
        if (declaration.isEmpty()) {
            return; // an ordinary mob: never tagged, never presented
        }
        entity.addTag(ADOPTED_TAG);
        entity.addTag(ID_TAG_PREFIX + declaration.get().id());
        if (event.getLevel() instanceof ServerLevel server) {
            int lane = LaneGeometry.laneIndex(entity.blockPosition().getX(), LaneGrid.laneStride());
            if (lane >= 1
                    && DimensionProfiles.isDungeonDimension(server.dimension().location().toString())) {
                entity.addTag(LAYER_TAG_PREFIX + lane);
            }
            if (entity instanceof Mob mob) {
                BossBars.track(server, mob); // presentation: the #253 seam, config honoured inside
            }
        }
    }

    /** The credit rule: a player-kill earns the stamp; every death notifies its layer. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().getTags().contains(ADOPTED_TAG)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation declarationId = declarationIdOf(event.getEntity());
        if (declarationId == null) {
            return; // adopted-marked but no readable identity: never credit on a guess
        }

        // Credit: the death source's entity is the killer (melee or projectile
        // owner), the pattern #259 verified against these mappings.
        Player killer = event.getSource().getEntity() instanceof Player player ? player : null;
        if (killer != null) {
            AdoptedDefeats.of(level).stamp(declarationId); // uncredited deaths stamp nothing
        }

        // Notify the layer it appeared in - context decided at spawn (its tag),
        // so a fight that drifted across a lane still reports where it began.
        Integer layer = layerOf(event.getEntity());
        if (layer != null) {
            NeoForge.EVENT_BUS.post(new BossSlainEvent(
                    level.dimension(), layer, event.getEntity(), killer == null ? null : killer.getUUID()));
        }
    }

    /** The declaration id this entity was adopted under, null when unreadable. */
    private static ResourceLocation declarationIdOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(ID_TAG_PREFIX)) {
                return ResourceLocation.tryParse(tag.substring(ID_TAG_PREFIX.length()));
            }
        }
        return null;
    }

    /** The dungeon layer this entity appeared in, null outside them. */
    private static Integer layerOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(LAYER_TAG_PREFIX)) {
                try {
                    return Integer.valueOf(tag.substring(LAYER_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return null; // foreign/corrupt tag: never post on a guess
                }
            }
        }
        return null;
    }
}

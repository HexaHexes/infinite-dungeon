package org.bitwisemadness.infinitedungeon.lobby;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalBlock;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeonapi.entrance.EntranceContext;
import org.bitwisemadness.infinitedungeonapi.entrance.EntranceDecision;
import org.bitwisemadness.infinitedungeonapi.entrance.EntranceGenerationPolicy;
import org.bitwisemadness.infinitedungeonapi.entrance.EntranceLocations;
import org.bitwisemadness.infinitedungeonapi.entrance.EntranceSite;

/**
 * The tower-entrance building's placement feature (issue #199, Piece 1 + 3).
 * The building itself is the datagen-authored {@code StructureTemplate}
 * ({@code data/infinite_dungeon/structure/tower_entrance.nbt}) - this feature
 * loads it through the server's structure manager and places it at the origin,
 * so the runtime path reads exactly the committed content the village
 * injection (Piece 2) will reference. The portal treatment (Piece 3): the
 * template ships the obsidian shell, and the feature activates the portal
 * after placement - the frame's incremental placement would collapse an
 * active interior, so the activation runs when the frame is complete, the
 * same way the ignition lights a built frame. Gated by the
 * {@code towerEntrance} toggles and by the API's entrance generation policy
 * (api#58): the feature asks the policy at the origin - position-carrying
 * query, the tower's fixed footprint - and generates only on a
 * {@code GENERATE} decision, never silently in unsuitable sites (the
 * register's rule). The portal toggle off leaves the building with its
 * obsidian shell only.
 */
public final class TowerEntranceFeature extends Feature<NoneFeatureConfiguration> {

    /** The authored structure template this feature places. */
    public static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "tower_entrance");

    /** The fixed lobby building (issue #131): the five lobby floors. */
    public static final ResourceLocation LOBBY_TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "lobby_entrance");

    /** The lobby building's fixed offset behind the tower (pending in-game confirm). */
    public static final BlockPos LOBBY_OFFSET = new BlockPos(0, 0, -7);

    /** The waystone room's arrival cell, relative to the tower's portal anchor. */
    public static final BlockPos WAYSTONE_ARRIVAL_OFFSET = new BlockPos(0, 15, -6);

    /** The waystone pedestal's block id (the room's signature). */
    private static final String PEDESTAL = "minecraft:polished_andesite";

    /** The entrance key this tower answers under in the API's registry. */
    public static final String ENTRANCE_KEY = "tower";

    /** The building's fixed footprint, in the register's "fixed" rule. */
    private static final int WIDTH = 7;
    private static final int HEIGHT = 8;
    private static final int DEPTH = 7;

    /** The portal interior cells inside the fixed template (local coords). */
    private static final BlockPos PORTAL_INTERIOR_1 = new BlockPos(3, 2, 1);
    private static final BlockPos PORTAL_INTERIOR_2 = new BlockPos(3, 3, 1);

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, InfiniteDungeon.MOD_ID);

    /** The registered feature: {@code infinite_dungeon:tower_entrance}. */
    public static final Supplier<TowerEntranceFeature> TOWER_ENTRANCE =
            FEATURES.register("tower_entrance", TowerEntranceFeature::new);

    private TowerEntranceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Registers the feature and its entrance provider on the mod bus. */
    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        EntranceLocations.register(ENTRANCE_KEY, context ->
                context.position().map(position -> new EntranceSite(position, TEMPLATE_ID)));
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!enabled()) {
            return false;
        }
        return build(context.level(), context.origin(), context.random());
    }

    /** The toggle gate (the {@code towerEntrance.enabled} config). */
    public static boolean enabled() {
        return DungeonConfigs.towerEntranceEnabled();
    }

    /** The portal-treatment toggle (the {@code towerEntrance.portal} config). */
    public static boolean portalEnabled() {
        return DungeonConfigs.towerEntrancePortal();
    }

    /** The testable path: the API policy gate, the placement, the treatment. */
    static boolean build(ServerLevelAccessor level, BlockPos origin, RandomSource random) {
        EntranceDecision decision = policy().decide(EntranceContext.at(level, origin, ENTRANCE_KEY, false));
        if (decision.outcome() != EntranceDecision.Outcome.GENERATE) {
            return false; // never silently generate in an unsuitable site
        }
        var template = level.getServer().getStructureManager().getOrCreate(TEMPLATE_ID);
        boolean placed = template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setIgnoreEntities(false), random, 2);
        if (placed) {
            if (portalEnabled()) {
                activatePortal(level, origin);
            }
            placeLobby(level, origin, random);
        }
        return placed;
    }

    /**
     * The fixed lobby construction (issue #131): the five-floor building
     * behind the tower, so the tower-entrance construction reads exactly the
     * committed geometry the room-content issues and the EasyNPC slots
     * reference. The offset is fixed; the in-game check confirms the joint.
     */
    private static void placeLobby(ServerLevelAccessor level, BlockPos origin, RandomSource random) {
        var lobby = level.getServer().getStructureManager().getOrCreate(LOBBY_TEMPLATE_ID);
        lobby.placeInWorld(level, origin.offset(LOBBY_OFFSET), origin.offset(LOBBY_OFFSET),
                new StructurePlaceSettings().setIgnoreEntities(false), random, 2);
    }

    /**
     * Whether the lobby's waystone room stands at the anchor's fixed offset
     * (issue #141): the death-lobby teleport lands there instead of at the
     * portal. The room's signature is its pedestal - absent, the lobby is not
     * built and the teleport falls back to the anchor portal.
     */
    public static boolean lobbyWaystoneRoomExists(ServerLevelAccessor level, BlockPos anchor) {
        BlockPos pedestal = anchor.offset(WAYSTONE_ARRIVAL_OFFSET).offset(0, -1, 1);
        return level.getBlockState(pedestal)
                .is(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .get(ResourceLocation.parse(PEDESTAL)));
    }

    /**
     * The portal treatment: fills the shell's interior with the active portal.
     * Done after the template, never inside it - the frame's incremental
     * placement would collapse an active interior (each portal cell sees the
     * other as air), so the activation runs when the frame is complete, the
     * same way the ignition lights a built frame.
     */
    private static void activatePortal(ServerLevelAccessor level, BlockPos origin) {
        var portal = PortalBlocks.DUNGEON_PORTAL.get().defaultBlockState()
                .setValue(DungeonPortalBlock.AXIS, Direction.Axis.X);
        level.setBlock(origin.offset(PORTAL_INTERIOR_1), portal, 3);
        level.setBlock(origin.offset(PORTAL_INTERIOR_2), portal, 3);
    }

    /** The API's entrance generation policy for the tower's fixed footprint. */
    private static EntranceGenerationPolicy policy() {
        return new EntranceGenerationPolicy(WIDTH, HEIGHT, DEPTH, PortalBlocks.DUNGEON_PORTAL.get());
    }
}

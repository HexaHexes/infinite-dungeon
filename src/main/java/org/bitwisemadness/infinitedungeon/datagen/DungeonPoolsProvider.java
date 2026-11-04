package org.bitwisemadness.infinitedungeon.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * Emits the profile spawn pools (issue #130): {@code infinite_dungeon/pools/}
 * {@code fodder}, {@code elite} and {@code boss}, each a list of
 * {@code entity} + {@code required_mod} entries. The runtime reads the committed
 * JSON through {@code SpawnPoolReloadListener}; this class is datagen-only.
 */
final class DungeonPoolsProvider implements DataProvider {

    /** One pool entry: the entity id and the mod that must be present. */
    private record Entry(String entity, String requiredMod) {
    }

    private static final List<Entry> FODDER = List.of(
            new Entry("mowziesmobs:foliaath", "mowziesmobs"),
            new Entry("mowziesmobs:grottol", "mowziesmobs"),
            new Entry("mowziesmobs:lantern", "mowziesmobs"),
            new Entry("mowziesmobs:bluff", "mowziesmobs"),
            new Entry("mobs_of_mythology:automaton", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:basilisk", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:chupacabra", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:kobold", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:kobold_warrior", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:sporeling", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:pegasus", "mobs_of_mythology"),
            new Entry("variantsandventures:gelid", "variantsandventures"),
            new Entry("variantsandventures:murk", "variantsandventures"),
            new Entry("variantsandventures:thicket", "variantsandventures"),
            new Entry("variantsandventures:verdant", "variantsandventures")
    );

    private static final List<Entry> ELITE = List.of(
            new Entry("mowziesmobs:frostmaw", "mowziesmobs"),
            new Entry("mowziesmobs:naga", "mowziesmobs"),
            new Entry("mowziesmobs:sculptor", "mowziesmobs"),
            new Entry("mowziesmobs:wroughtnaut", "mowziesmobs"),
            new Entry("mowziesmobs:umvuthana", "mowziesmobs"),
            new Entry("mowziesmobs:umvuthana_crane", "mowziesmobs"),
            new Entry("mowziesmobs:umvuthana_raptor", "mowziesmobs"),
            new Entry("mowziesmobs:umvuthi", "mowziesmobs"),
            new Entry("mobs_of_mythology:wendigo", "mobs_of_mythology"),
            new Entry("mobs_of_mythology:drake", "mobs_of_mythology")
    );

    private static final List<Entry> BOSS = List.of(
            new Entry("block_factorys_bosses:infernal_dragon", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:kraken", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:sandworm", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:yeti", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:pirate_captain", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:pirate_rook", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:crossbow_pirate", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:underworld_knight", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:soul_knight_wither_skeleton", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:soul_skeleton", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:dragon_guard_sword", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:flaming_skeleton_guard_sword", "block_factorys_bosses"),
            new Entry("block_factorys_bosses:flaming_skeleton_guard_fireball", "block_factorys_bosses"),
            new Entry("netherman:gilded_golem", "netherman"),
            new Entry("netherman:guardian", "netherman"),
            new Entry("netherman:manipulator", "netherman"),
            new Entry("netherman:ghastly", "netherman"),
            new Entry("iceandfire:fire_dragon", "iceandfire"),
            new Entry("iceandfire:ice_dragon", "iceandfire"),
            new Entry("iceandfire:lightning_dragon", "iceandfire"),
            new Entry("iceandfire:black_frost_dragon", "iceandfire"),
            new Entry("iceandfire:hydra", "iceandfire"),
            new Entry("iceandfire:amphithere", "iceandfire"),
            new Entry("iceandfire:cyclops", "iceandfire"),
            new Entry("iceandfire:deathworm", "iceandfire"),
            new Entry("iceandfire:gorgon", "iceandfire"),
            new Entry("iceandfire:hippogryph", "iceandfire"),
            new Entry("iceandfire:sea_serpent", "iceandfire"),
            new Entry("iceandfire:siren", "iceandfire"),
            new Entry("iceandfire:troll", "iceandfire"),
            new Entry("iceandfire:dread_knight", "iceandfire"),
            new Entry("iceandfire:dread_lich", "iceandfire"),
            new Entry("iceandfire:dread_queen", "iceandfire"),
            new Entry("iceandfire:dread_ghoul", "iceandfire"),
            new Entry("iceandfire:dread_beast", "iceandfire"),
            new Entry("cataclysm:netherite_monstrosity", "cataclysm"),
            new Entry("cataclysm:netherite_ministrosity", "cataclysm"),
            new Entry("cataclysm:the_leviathan", "cataclysm"),
            new Entry("cataclysm:the_baby_leviathan", "cataclysm"),
            new Entry("cataclysm:scylla", "cataclysm"),
            new Entry("cataclysm:ignis", "cataclysm"),
            new Entry("cataclysm:cindaria", "cataclysm"),
            new Entry("cataclysm:maledictus", "cataclysm"),
            new Entry("cataclysm:nameless_sorcerer", "cataclysm"),
            new Entry("cataclysm:aptrgangr", "cataclysm"),
            new Entry("cataclysm:the_harbinger", "cataclysm"),
            new Entry("cataclysm:the_watcher", "cataclysm"),
            new Entry("cataclysm:the_prowler", "cataclysm"),
            new Entry("cataclysm:ancient_remnant", "cataclysm"),
            new Entry("cataclysm:modern_remnant", "cataclysm"),
            new Entry("cataclysm:ender_golem", "cataclysm"),
            new Entry("cataclysm:ender_guardian", "cataclysm"),
            new Entry("cataclysm:endermaptera", "cataclysm"),
            new Entry("cataclysm:kobolediator", "cataclysm"),
            new Entry("cataclysm:royal_draugr", "cataclysm"),
            new Entry("cataclysm:ignited_berserker", "cataclysm"),
            new Entry("cataclysm:ignited_revenant", "cataclysm"),
            new Entry("cataclysm:coralssus", "cataclysm"),
            new Entry("cataclysm:hippocamtus", "cataclysm")
    );

    private static final Map<String, List<Entry>> POOLS = Map.of(
            "fodder", FODDER,
            "elite", ELITE,
            "boss", BOSS);

    private final PackOutput.PathProvider pools;

    DungeonPoolsProvider(PackOutput output) {
        this.pools = output.createPathProvider(PackOutput.Target.DATA_PACK, "infinite_dungeon/pools");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> pool : POOLS.entrySet()) {
            JsonArray entries = new JsonArray();
            for (Entry entry : pool.getValue()) {
                JsonObject object = new JsonObject();
                object.addProperty("entity", entry.entity());
                object.addProperty("required_mod", entry.requiredMod());
                entries.add(object);
            }
            JsonObject root = new JsonObject();
            root.add("entries", entries);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    InfiniteDungeon.MOD_ID, pool.getKey());
            futures.add(DataProvider.saveStable(cache, root, this.pools.json(id)));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Dungeon spawn pools";
    }
}

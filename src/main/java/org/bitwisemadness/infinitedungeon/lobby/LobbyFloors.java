package org.bitwisemadness.infinitedungeon.lobby;

/**
 * The fixed lobby layout (issue #131). The first layer is special - of fixed
 * construction, with exactly five floors - and exempt from the seed-derived
 * floor rule (section 3: the schedule covers layers 1 and above). Since issue
 * #246 the lobby is the Overworld entrance site, so this is the contract for
 * the Wave-2 tower-entrance construction (#63) and the room-content issues
 * (#138 reception, #139 guild shops, #140 specialised shops, #141 waystone
 * room): the fixed role of every lobby floor, named once so the room builders
 * and the EasyNPC slot table (infinite-dungeon-easynpc #1-#3) cannot disagree.
 *
 * <p>Pure by construction - no world access, no registry - so the contract is
 * GameTest- and JUnit-pinnable and pregeneration-stable (section 16).</p>
 */
public final class LobbyFloors {

    /** The exact floor count of the fixed lobby. */
    public static final int FLOOR_COUNT = 5;

    /** The fixed role a lobby floor fields. */
    public enum RoomKind {
        /** Floor 0: adventurer registration and task handling (issue #138). */
        GUILD_RECEPTION,
        /** Floor 1: guild-backed essentials shops and buy-back (issue #139). */
        GUILD_SHOPS,
        /** Floor 2: specialised shops (issue #140). */
        SPECIALISED_SHOPS,
        /** Floor 3: advanced specialised shops (issue #140). */
        ADVANCED_SHOPS,
        /** Floor 4: the waystone and respawn-arrival room (issue #141). */
        WAYSTONE_ROOM
    }

    private LobbyFloors() {
    }

    /**
     * The fixed role of a lobby floor, top-anchored like every band (floor 0
     * is the entry floor): 0 the guild reception, 4 the waystone room.
     */
    public static RoomKind roomKind(int floor) {
        return switch (floor) {
            case 0 -> RoomKind.GUILD_RECEPTION;
            case 1 -> RoomKind.GUILD_SHOPS;
            case 2 -> RoomKind.SPECIALISED_SHOPS;
            case 3 -> RoomKind.ADVANCED_SHOPS;
            case 4 -> RoomKind.WAYSTONE_ROOM;
            default -> throw new IllegalArgumentException(
                    "the lobby has exactly " + FLOOR_COUNT + " fixed floors, got " + floor);
        };
    }
}

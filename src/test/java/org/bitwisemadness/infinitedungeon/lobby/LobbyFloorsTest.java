package org.bitwisemadness.infinitedungeon.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The fixed lobby layout (issue #131): five fixed floors, each with one role. */
class LobbyFloorsTest {

    @Test
    void lobbyIsFixedAtFiveFloors() {
        assertEquals(5, LobbyFloors.FLOOR_COUNT, "the lobby has exactly five fixed floors");
    }

    @Test
    void everyFloorHasItsAuthoritativeRole() {
        assertEquals(LobbyFloors.RoomKind.GUILD_RECEPTION, LobbyFloors.roomKind(0));
        assertEquals(LobbyFloors.RoomKind.GUILD_SHOPS, LobbyFloors.roomKind(1));
        assertEquals(LobbyFloors.RoomKind.SPECIALISED_SHOPS, LobbyFloors.roomKind(2));
        assertEquals(LobbyFloors.RoomKind.ADVANCED_SHOPS, LobbyFloors.roomKind(3));
        assertEquals(LobbyFloors.RoomKind.WAYSTONE_ROOM, LobbyFloors.roomKind(4));
    }

    @Test
    void floorsOutsideTheFixedLobbyAreRefusedLoudly() {
        assertThrows(IllegalArgumentException.class, () -> LobbyFloors.roomKind(-1),
                "the contract refuses floors below the fixed lobby, never guesses");
        assertThrows(IllegalArgumentException.class, () -> LobbyFloors.roomKind(5),
                "the contract refuses floors above the fixed lobby, never guesses");
    }
}

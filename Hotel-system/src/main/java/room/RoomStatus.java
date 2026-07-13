package room;

public enum RoomStatus {
    AVAILABLE,
    OCCUPIED,      // guest is currently checked in
    RESERVED,      // has a confirmed future booking
    MAINTENANCE,   // temporarily out of service
    OUT_OF_ORDER
}

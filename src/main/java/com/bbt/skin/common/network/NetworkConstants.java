package com.bbt.skin.common.network;

/**
 * Network constants for skin synchronization
 */
public class NetworkConstants {
    
    // Maximum sizes
    public static final int MAX_SKIN_SIZE = 10 * 1024 * 1024; // 10MB max skin size
    public static final int MAX_STRING_LENGTH = 32767;
    public static final int CHUNK_SIZE = 32000; // Size of data chunks for large skins
    
    // Packet types
    public static final int PACKET_SKIN_SYNC = 1;
    public static final int PACKET_SKIN_REQUEST = 2;
    public static final int PACKET_SKIN_RESPONSE = 3;
    public static final int PACKET_SKIN_RESET = 4;
    
    // Timeouts (milliseconds)
    public static final int SKIN_REQUEST_TIMEOUT = 30000;
    public static final int SKIN_SYNC_TIMEOUT = 60000;
    
    private NetworkConstants() {}
}

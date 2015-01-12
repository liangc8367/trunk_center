package com.bluesky.cloudmontain;

/**
 * Created by liangc on 31/12/14.
 */
public class GlobalConstants {
    final static String TAG = "TrunkManager";
    public static final int TRUNK_CENTER_PORT   = 32000;
    public static final int INIT_SEQ_NUMBER     = 12345;

    /** call parameters */
    public static final int CALL_FLYWHEEL_PERIOD    = 1500;  // return to idle if no rxed packet
    public static final int CALL_HANG_PERIOD        = 10000; //
    public static final int CALL_PACKET_INTERVAL    = 20;    // 20ms
    public static final int CALL_PREAMBLE_NUMBER    = 3;
    public static final int CALL_TERM_NUMBER        = -3;

    public static final int COMPRESSED_20MS_AUDIO_SIZE  = 20;

    /** call info for faked echo */
    public static final long    SUID_TRUNK_MANAGER  = 1;


}

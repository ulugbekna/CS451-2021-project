package cs451;

public class Constants {
    public static final int ARG_LIMIT_CONFIG = 7;

    // indexes for id
    public static final int ID_KEY = 0;
    public static final int ID_VALUE = 1;

    // indexes for hosts
    public static final int HOSTS_KEY = 2;
    public static final int HOSTS_VALUE = 3;

    // indexes for output
    public static final int OUTPUT_KEY = 4;
    public static final int OUTPUT_VALUE = 5;

    // indexes for config
    public static final int CONFIG_VALUE = 6;

    /*
     * Constants introduced by Ulugbek
     * */

    public final static int THREAD_POOL_SZ = 32;

    public static final int SEND_RECV_BUF_SZ = 590; // TODO: make it dynamic based on the # of hosts
    public static final int INITIAL_RESEND_TIMEOUT = 500; // milliseconds

    /**
     * serialized AckPacket size in bytes
     */
    public static final int ACK_PACK_SZ = 9;
}

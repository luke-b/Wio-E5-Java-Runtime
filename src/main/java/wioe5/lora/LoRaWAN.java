package wioe5.lora;

public final class LoRaWAN {
    public static final int EU868 = 0;
    public static final int US915 = 1;
    public static final int AS923 = 2;

    public static final int IDLE = 0;
    public static final int JOINING = 1;
    public static final int JOINED = 2;
    public static final int TX_BUSY = 3;
    public static final int RX_PENDING = 4;

    private LoRaWAN() {
    }

    public static native int init(int region);

    public static native int joinOTAA(byte[] devEUI, byte[] appEUI, byte[] appKey);

    public static native int joinABP(byte[] devAddr, byte[] nwkSKey, byte[] appSKey);

    public static native int send(byte[] data, int len, int port, boolean confirmed);

    public static native int process();

    public static native int getStatus();

    public static native int readDownlink(byte[] buffer, int[] portOut);

    public static native int setTxPower(int dbm);

    public static native int setADR(boolean enabled);

    public static native int getLastRSSI();

    public static native int getLastSNR();
}

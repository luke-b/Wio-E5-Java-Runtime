package wioe5.storage;

public final class NVConfig {
    public static final int KEY_LORA_REGION = 0;
    public static final int KEY_LORA_DEVEUI = 1;
    public static final int KEY_LORA_APPEUI = 2;
    public static final int KEY_LORA_APPKEY = 3;
    public static final int KEY_SENSOR_CAL = 4;
    public static final int KEY_APP_VERSION = 5;

    private NVConfig() {
    }

    public static native int read(int key, byte[] buffer);

    public static native int write(int key, byte[] data, int len);
}

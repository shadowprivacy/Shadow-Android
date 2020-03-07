package su.sres.signalservice.api.kbs;

/**
 * Construct from a {@link HashedPin}.
 */
public final class KbsData {
    private final MasterKey masterKey;
    private final byte[]    kbsAccessKey;
    private final byte[]    cipherText;

    KbsData(byte[] masterKey, byte[] kbsAccessKey, byte[] cipherText) {
        this.masterKey    = new MasterKey(masterKey);
        this.kbsAccessKey = kbsAccessKey;
        this.cipherText   = cipherText;
    }

    public MasterKey getMasterKey() {
        return masterKey;
    }

    public byte[] getKbsAccessKey() {
        return kbsAccessKey;
    }

    public byte[] getCipherText() {
        return cipherText;
    }
}
package su.sres.signalservice.api;

public class ContentTooLargeException extends IllegalStateException {
    public ContentTooLargeException(long size) {
        super("Too large! Size: " + size + " bytes");
    }
}

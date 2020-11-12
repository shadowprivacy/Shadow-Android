package su.sres.securesms.contacts.sync;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class DirectoryEntryValue {

    @JsonProperty
    private UUID uuid;

    public DirectoryEntryValue() {
    }

    public DirectoryEntryValue(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}

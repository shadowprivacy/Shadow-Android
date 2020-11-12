package su.sres.securesms.contacts.sync;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Map;

import su.sres.signalservice.api.storage.protos.DirectoryResponse;
import su.sres.signalservice.api.storage.protos.DirectoryUpdate;

public class PlainDirectoryResult {

    private Optional<Map<String, String>> updateContents;
    private boolean isUpdate;
    private boolean isFullUpdate;
    private long version;

    PlainDirectoryResult(DirectoryResponse directoryResponse) {
        DirectoryResponse.StatusOrUpdateCase responseType = directoryResponse.getStatusOrUpdateCase();

        version = directoryResponse.getVersion();

        switch (responseType) {

            case DIRECTORY_UPDATE:
                isUpdate = true;
                DirectoryUpdate directoryUpdate = directoryResponse.getDirectoryUpdate();
                DirectoryUpdate.Type updateType = directoryUpdate.getType();
                updateContents = Optional.of(directoryUpdate.getDirectoryEntryMap());

                switch (updateType) {
                    case FULL:
                        isFullUpdate = true;
                        break;
                    default:
                        isFullUpdate = false;
                }

                break;

            default:
                isUpdate = false;
                isFullUpdate = false;
                updateContents = Optional.absent();
        }
    }

    public Optional<Map<String, String>> getUpdateContents() {
        return updateContents;
    }

    public long getVersion() {
        return version;
    }

    public boolean isUpdate() {
        return isUpdate;
    }

    public boolean isFullUpdate() {
        return isFullUpdate;
    }
}
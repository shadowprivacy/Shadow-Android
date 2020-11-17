package su.sres.signalservice.api.groupsv2;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.storageservice.protos.groups.local.DecryptedGroup;
import su.sres.storageservice.protos.groups.local.DecryptedGroupChange;

/**
 * Pair of a {@link DecryptedGroup} and the {@link DecryptedGroupChange} for that version.
 */
public final class DecryptedGroupHistoryEntry {

    private final Optional<DecryptedGroup>       group;
    private final Optional<DecryptedGroupChange> change;

    DecryptedGroupHistoryEntry(Optional<DecryptedGroup> group, Optional<DecryptedGroupChange> change)
            throws InvalidGroupStateException
    {
        if (group.isPresent() && change.isPresent() && group.get().getRevision() != change.get().getRevision()) {
            throw new InvalidGroupStateException();
        }

        this.group  = group;
        this.change = change;
    }

    public Optional<DecryptedGroup> getGroup() {
        return group;
    }

    public Optional<DecryptedGroupChange> getChange() {
        return change;
    }
}
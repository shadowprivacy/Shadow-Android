package su.sres.securesms.groups;

import androidx.annotation.StringRes;

import su.sres.securesms.R;

public enum GroupAccessControl {
    ALL_MEMBERS(R.string.GroupManagement_access_level_all_members),
    ONLY_ADMINS(R.string.GroupManagement_access_level_only_admins);

    private final @StringRes int string;

    GroupAccessControl(@StringRes int string) {
        this.string = string;
    }

    public @StringRes int getString() {
        return string;
    }
}
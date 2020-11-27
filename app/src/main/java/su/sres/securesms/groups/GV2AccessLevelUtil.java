package su.sres.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.storageservice.protos.groups.AccessControl;
import su.sres.securesms.R;

public final class GV2AccessLevelUtil {

    private GV2AccessLevelUtil() {
    }

    public static String toString(@NonNull Context context, @NonNull AccessControl.AccessRequired attributeAccess) {
        switch (attributeAccess) {
            case MEMBER        : return context.getString(R.string.GroupManagement_access_level_all_members);
            case ADMINISTRATOR : return context.getString(R.string.GroupManagement_access_level_only_admins);
            default            : return context.getString(R.string.GroupManagement_access_level_unknown);
        }
    }
}
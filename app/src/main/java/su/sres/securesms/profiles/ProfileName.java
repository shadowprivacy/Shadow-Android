package su.sres.securesms.profiles;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;

import su.sres.securesms.util.StringUtil;
import su.sres.securesms.util.cjkv.CJKVUtil;
import su.sres.signalservice.api.crypto.ProfileCipher;

public final class ProfileName implements Parcelable {

    public static final ProfileName EMPTY           = new ProfileName("", "");
    public static final int         MAX_PART_LENGTH = (ProfileCipher.NAME_PADDED_LENGTH - 1) / 2;

    private final String givenName;
    private final String familyName;
    private final String joinedName;

    private ProfileName(@Nullable String givenName, @Nullable String familyName) {
        this.givenName  = givenName  == null ? "" : givenName;
        this.familyName = familyName == null ? "" : familyName;
        this.joinedName = getJoinedName(this.givenName, this.familyName);
    }

    private ProfileName(Parcel in) {
        this(in.readString(), in.readString());
    }

    public @NonNull String getGivenName() {
        return givenName;
    }

    public @NonNull String getFamilyName() {
        return familyName;
    }

    @VisibleForTesting
    boolean isProfileNameCJKV() {
        return isCJKV(givenName, familyName);
    }

    public boolean isEmpty() {
        return joinedName.isEmpty();
    }

    public boolean isGivenNameEmpty() {
        return givenName.isEmpty();
    }

    public @NonNull String serialize() {
        if (isGivenNameEmpty()) {
            return "";
        }

        return String.format("%s\0%s", givenName, familyName);
    }

    @Override
    public @NonNull String toString() {
        return joinedName;
    }

    /**
     * Deserializes a profile name, trims if exceeds the limits.
     */
    public static @NonNull ProfileName fromSerialized(@Nullable String profileName) {
        if (profileName == null || profileName.isEmpty()) {
            return EMPTY;
        }

        String[] parts = profileName.split("\0");

        if (parts.length == 0) {
            return EMPTY;
        } else if (parts.length == 1) {
            return fromParts(parts[0], "");
        } else {
            return fromParts(parts[0], parts[1]);
        }
    }

    /**
     * Creates a profile name, trimming chars until it fits the limits.
     */
    public static @NonNull ProfileName fromParts(@Nullable String givenName, @Nullable String familyName) {
        givenName  = givenName  == null ? "" : givenName;
        familyName = familyName == null ? "" : familyName;

        givenName  = StringUtil.trimToFit(givenName.trim(), ProfileName.MAX_PART_LENGTH);
        familyName = StringUtil.trimToFit(familyName.trim(), ProfileName.MAX_PART_LENGTH);

        return new ProfileName(givenName, familyName);
    }

    private static @NonNull String getJoinedName(@NonNull String givenName, @NonNull String familyName) {
        if (givenName.isEmpty() && familyName.isEmpty()) return "";
        else if (givenName.isEmpty())                    return familyName;
        else if (familyName.isEmpty())                   return givenName;
        else if (isCJKV(givenName, familyName))          return String.format("%s %s",
                familyName,
                givenName);
        else                                             return String.format("%s %s",
                    givenName,
                    familyName);
    }

    private static boolean isCJKV(@NonNull String givenName, @NonNull String familyName) {
        if (givenName.isEmpty() && familyName.isEmpty()) {
            return false;
        } else {
            return Stream.of(givenName, familyName)
                    .filterNot(String::isEmpty)
                    .reduce(true, (a, s) -> a && CJKVUtil.isCJKV(s));
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(givenName);
        dest.writeString(familyName);
    }

    public static final Creator<ProfileName> CREATOR = new Creator<ProfileName>() {
        @Override
        public ProfileName createFromParcel(Parcel in) {
            return new ProfileName(in);
        }

        @Override
        public ProfileName[] newArray(int size) {
            return new ProfileName[size];
        }
    };
}
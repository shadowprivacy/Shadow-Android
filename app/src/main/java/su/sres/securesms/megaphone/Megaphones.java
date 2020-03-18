package su.sres.securesms.megaphone;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import su.sres.securesms.R;
import su.sres.securesms.database.model.MegaphoneRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.profiles.edit.EditProfileActivity;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Creating a new megaphone:
 * - Add an enum to {@link Event}
 * - Return a megaphone in {@link #forRecord(Context, MegaphoneRecord)}
 * - Include the event in {@link #buildDisplayOrder()}
 *
 * Common patterns:
 * - For events that have a snooze-able recurring display schedule, use a {@link RecurringSchedule}.
 * - For events guarded by feature flags, set a {@link ForeverSchedule} with false in
 *   {@link #buildDisplayOrder()}.
 * - For events that change, return different megaphones in {@link #forRecord(Context, MegaphoneRecord)}
 *   based on whatever properties you're interested in.
 */
public final class Megaphones {

    private static final String TAG = Log.tag(Megaphones.class);

    private static final MegaphoneSchedule ALWAYS         = new ForeverSchedule(true);
    private static final MegaphoneSchedule NEVER          = new ForeverSchedule(false);
    private static final MegaphoneSchedule EVERY_TWO_DAYS = new RecurringSchedule(TimeUnit.DAYS.toMillis(2));

    private Megaphones() {}

    static @Nullable Megaphone getNextMegaphone(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
        long currentTime = System.currentTimeMillis();

        List<Megaphone> megaphones = Stream.of(buildDisplayOrder())
                .filter(e -> {
                    MegaphoneRecord   record = Objects.requireNonNull(records.get(e.getKey()));
                    MegaphoneSchedule schedule = e.getValue();

                    return !record.isFinished() && schedule.shouldDisplay(record.getSeenCount(), record.getLastSeen(), record.getFirstVisible(), currentTime);
                })
                .map(Map.Entry::getKey)
                .map(records::get)
                .map(record -> Megaphones.forRecord(context, record))
                .toList();

        boolean hasOptional  = Stream.of(megaphones).anyMatch(m -> !m.isMandatory());
        boolean hasMandatory = Stream.of(megaphones).anyMatch(Megaphone::isMandatory);

        if (hasOptional && hasMandatory) {
            megaphones = Stream.of(megaphones).filter(Megaphone::isMandatory).toList();
        }

        if (megaphones.size() > 0) {
            return megaphones.get(0);
        } else {
            return null;
        }
    }

    /**
     * This is when you would hide certain megaphones based on {@link FeatureFlags}. You could
     * conditionally set a {@link ForeverSchedule} set to false for disabled features.
     */
    private static Map<Event, MegaphoneSchedule> buildDisplayOrder() {
        return new LinkedHashMap<Event, MegaphoneSchedule>() {{
            put(Event.REACTIONS, ALWAYS);
            put(Event.PROFILE_NAMES_FOR_ALL, FeatureFlags.profileNamesMegaphoneEnabled() ? EVERY_TWO_DAYS : NEVER);
        }};
    }

    private static @NonNull Megaphone forRecord(@NonNull Context context, @NonNull MegaphoneRecord record) {
        switch (record.getEvent()) {
            case REACTIONS:
                return buildReactionsMegaphone();
            case PROFILE_NAMES_FOR_ALL:
                return buildProfileNamesMegaphone(context);
            default:
                throw new IllegalArgumentException("Event not handled!");
        }
    }

    private static @NonNull Megaphone buildReactionsMegaphone() {
        return new Megaphone.Builder(Event.REACTIONS, Megaphone.Style.REACTIONS)
                .setMandatory(false)
                .build();
    }

    private static @NonNull Megaphone buildProfileNamesMegaphone(@NonNull Context context) {
        Megaphone.Builder builder = new Megaphone.Builder(Event.PROFILE_NAMES_FOR_ALL, Megaphone.Style.BASIC)
                .enableSnooze(null)
                .setImage(R.drawable.profile_megaphone);

        Megaphone.EventListener eventListener = (megaphone, listener) -> {
            listener.onMegaphoneSnooze(Event.PROFILE_NAMES_FOR_ALL);
            listener.onMegaphoneNavigationRequested(new Intent(context, EditProfileActivity.class));
        };

        if (TextSecurePreferences.getProfileName(ApplicationDependencies.getApplication()) == ProfileName.EMPTY) {
            return builder.setTitle(R.string.ProfileNamesMegaphone__add_a_profile_name)
                    .setBody(R.string.ProfileNamesMegaphone__this_will_be_displayed_when_you_start)
                    .setActionButton(R.string.ProfileNamesMegaphone__add_profile_name, eventListener)
                    .build();
        } else {
            return builder.setTitle(R.string.ProfileNamesMegaphone__confirm_your_profile_name)
                    .setBody(R.string.ProfileNamesMegaphone__your_profile_can_now_include)
                    .setActionButton(R.string.ProfileNamesMegaphone__confirm_name, eventListener)
                    .build();
        }
    }

    public enum Event {
        REACTIONS("reactions"),
        PROFILE_NAMES_FOR_ALL("profile_names");

        private final String key;

        Event(@NonNull String key) {
            this.key = key;
        }

        public @NonNull String getKey() {
            return key;
        }

        public static Event fromKey(@NonNull String key) {
            for (Event event : values()) {
                if (event.getKey().equals(key)) {
                    return event;
                }
            }
            throw new IllegalArgumentException("No event for key: " + key);
        }

    public static boolean hasKey(@NonNull String key) {
        for (Event event : values()) {
            if (event.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }
    }
}
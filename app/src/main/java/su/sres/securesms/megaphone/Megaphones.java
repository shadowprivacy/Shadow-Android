package su.sres.securesms.megaphone;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import su.sres.securesms.R;
import su.sres.securesms.conversationlist.ConversationListFragment;
import su.sres.securesms.database.model.MegaphoneRecord;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.messagerequests.MessageRequestMegaphoneActivity;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.profiles.edit.EditProfileActivity;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.AvatarUtil;
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
 * - Include the event in {@link #buildDisplayOrder(Context)}
 *
 * Common patterns:
 * - For events that have a snooze-able recurring display schedule, use a {@link RecurringSchedule}.
 * - For events guarded by feature flags, set a {@link ForeverSchedule} with false in
 *   {@link #buildDisplayOrder(Context)}.
 * - For events that change, return different megaphones in {@link #forRecord(Context, MegaphoneRecord)}
 *   based on whatever properties you're interested in.
 */
public final class Megaphones {

    private static final String TAG = Log.tag(Megaphones.class);

    private static final MegaphoneSchedule ALWAYS = new ForeverSchedule(true);
    private static final MegaphoneSchedule NEVER  = new ForeverSchedule(false);

    private Megaphones() {}

    static @Nullable Megaphone getNextMegaphone(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
        long currentTime = System.currentTimeMillis();

        List<Megaphone> megaphones = Stream.of(buildDisplayOrder(context))
                .filter(e -> {
                    MegaphoneRecord   record = Objects.requireNonNull(records.get(e.getKey()));
                    MegaphoneSchedule schedule = e.getValue();

                    return !record.isFinished() && schedule.shouldDisplay(record.getSeenCount(), record.getLastSeen(), record.getFirstVisible(), currentTime);
                })
                .map(Map.Entry::getKey)
                .map(records::get)
                .map(record -> Megaphones.forRecord(context, record))
                .sortBy(m -> -m.getPriority().getPriorityValue())
                .toList();

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
    private static Map<Event, MegaphoneSchedule> buildDisplayOrder(@NonNull Context context) {
        return new LinkedHashMap<Event, MegaphoneSchedule>() {{
            put(Event.REACTIONS, ALWAYS);
            put(Event.MESSAGE_REQUESTS, shouldShowMessageRequestsMegaphone() ? ALWAYS : NEVER);
            put(Event.LINK_PREVIEWS, shouldShowLinkPreviewsMegaphone(context) ? ALWAYS : NEVER);
            put(Event.CLIENT_DEPRECATED, SignalStore.misc().isClientDeprecated() ? ALWAYS : NEVER);
        }};
    }

    private static @NonNull Megaphone forRecord(@NonNull Context context, @NonNull MegaphoneRecord record) {
        switch (record.getEvent()) {
            case REACTIONS:
                return buildReactionsMegaphone();
            case MESSAGE_REQUESTS:
                return buildMessageRequestsMegaphone(context);
            case LINK_PREVIEWS:
                return buildLinkPreviewsMegaphone();
            case CLIENT_DEPRECATED:
                return buildClientDeprecatedMegaphone(context);
            default:
                throw new IllegalArgumentException("Event not handled!");
        }
    }

    private static @NonNull Megaphone buildReactionsMegaphone() {
        return new Megaphone.Builder(Event.REACTIONS, Megaphone.Style.REACTIONS)
                .setPriority(Megaphone.Priority.DEFAULT)
                .build();
    }

    @SuppressWarnings("CodeBlock2Expr")
    private static @NonNull Megaphone buildMessageRequestsMegaphone(@NonNull Context context) {
        return new Megaphone.Builder(Event.MESSAGE_REQUESTS, Megaphone.Style.FULLSCREEN)
                .disableSnooze()
                .setPriority(Megaphone.Priority.HIGH)
                .setOnVisibleListener(((megaphone, listener) -> {
                    listener.onMegaphoneNavigationRequested(new Intent(context, MessageRequestMegaphoneActivity.class),
                            ConversationListFragment.MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME);
                }))
                .build();
    }

    private static @NonNull Megaphone buildLinkPreviewsMegaphone() {
        return new Megaphone.Builder(Event.LINK_PREVIEWS, Megaphone.Style.LINK_PREVIEWS)
                .setPriority(Megaphone.Priority.HIGH)
                .build();
    }

    private static @NonNull Megaphone buildClientDeprecatedMegaphone(@NonNull Context context) {
        return new Megaphone.Builder(Event.CLIENT_DEPRECATED, Megaphone.Style.FULLSCREEN)
                .disableSnooze()
                .setPriority(Megaphone.Priority.HIGH)
                .setOnVisibleListener((megaphone, listener) -> listener.onMegaphoneNavigationRequested(new Intent(context, ClientDeprecatedActivity.class)))
                .build();
    }

    private static boolean shouldShowMessageRequestsMegaphone() {
        return Recipient.self().getProfileName() == ProfileName.EMPTY;
    }

    private static boolean shouldShowLinkPreviewsMegaphone(@NonNull Context context) {
        return TextSecurePreferences.wereLinkPreviewsEnabled(context) && !SignalStore.settings().isLinkPreviewsEnabled();
    }

    public enum Event {
        REACTIONS("reactions"),
        MESSAGE_REQUESTS("message_requests"),
        LINK_PREVIEWS("link_previews"),
        CLIENT_DEPRECATED("client_deprecated");

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
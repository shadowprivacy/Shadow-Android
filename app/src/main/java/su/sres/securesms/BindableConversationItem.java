package su.sres.securesms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import android.net.Uri;
import android.view.View;

import su.sres.securesms.components.voice.VoiceNotePlaybackState;
import su.sres.securesms.contactshare.Contact;
import su.sres.securesms.conversation.ConversationMessage;
import su.sres.securesms.conversation.colors.Colorizable;
import su.sres.securesms.conversation.colors.Colorizer;
import su.sres.securesms.database.model.InMemoryMessageRecord;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.securesms.giph.mp4.GiphyMp4Playable;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupMigrationMembershipChange;
import su.sres.securesms.linkpreview.LinkPreview;
import su.sres.securesms.mms.GlideRequests;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.stickers.StickerLocator;
import su.sres.securesms.video.exo.AttachmentMediaSourceFactory;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable, GiphyMp4Playable, Colorizable {
  void bind(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull ConversationMessage messageRecord,
            @NonNull Optional<MessageRecord> previousMessageRecord,
            @NonNull Optional<MessageRecord> nextMessageRecord,
            @NonNull GlideRequests glideRequests,
            @NonNull Locale locale,
            @NonNull Set<ConversationMessage> batchSelected,
            @NonNull Recipient recipients,
            @Nullable String searchQuery,
            boolean pulseMention,
            boolean hasWallpaper,
            boolean isMessageRequestAccepted,
            @NonNull AttachmentMediaSourceFactory attachmentMediaSourceFactory,
            boolean canPlayInline,
            @NonNull Colorizer colorizer);

  ConversationMessage getConversationMessage();

  void setEventListener(@Nullable EventListener listener);

  interface EventListener {
    void onQuoteClicked(MmsMessageRecord messageRecord);
    void onLinkPreviewClicked(@NonNull LinkPreview linkPreview);
    void onMoreTextClicked(@NonNull RecipientId conversationRecipientId, long messageId, boolean isMms);
    void onStickerClicked(@NonNull StickerLocator stickerLocator);
    void onViewOnceMessageClicked(@NonNull MmsMessageRecord messageRecord);
    void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView);
    void onAddToContactsClicked(@NonNull Contact contact);
    void onMessageSharedContactClicked(@NonNull List<Recipient> choices);
    void onInviteSharedContactClicked(@NonNull List<Recipient> choices);
    void onReactionClicked(@NonNull View reactionTarget, long messageId, boolean isMms);
    void onGroupMemberClicked(@NonNull RecipientId recipientId, @NonNull GroupId groupId);
    void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord);
    void onMessageWithRecaptchaNeededClicked(@NonNull MessageRecord messageRecord);
    void onRegisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver);
    void onUnregisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver);
    void onVoiceNotePause(@NonNull Uri uri);
    void onVoiceNotePlay(@NonNull Uri uri, long messageId, double position);
    void onVoiceNoteSeekTo(@NonNull Uri uri, double position);
    void onGroupMigrationLearnMoreClicked(@NonNull GroupMigrationMembershipChange membershipChange);
    void onDecryptionFailedLearnMoreClicked();
    void onSafetyNumberLearnMoreClicked(@NonNull Recipient recipient);
    void onJoinGroupCallClicked();
    void onInviteFriendsToGroupClicked(@NonNull GroupId.V2 groupId);
    void onEnableCallNotificationsClicked();
    void onPlayInlineContent(ConversationMessage conversationMessage);
    void onInMemoryMessageClicked(@NonNull InMemoryMessageRecord messageRecord);
    void onViewGroupDescriptionChange(@Nullable GroupId groupId, @NonNull String description, boolean isMessageRequestAccepted);

    /** @return true if handled, false if you want to let the normal url handling continue */
    boolean onUrlClicked(@NonNull String url);
  }
}

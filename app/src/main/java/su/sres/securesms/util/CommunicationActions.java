package su.sres.securesms.util;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.TaskStackBuilder;
import androidx.fragment.app.FragmentActivity;

import su.sres.securesms.R;
import su.sres.securesms.WebRtcCallActivity;
import su.sres.securesms.conversation.ConversationIntents;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.ui.invitesandrequests.joining.GroupJoinBottomSheetDialogFragment;
import su.sres.securesms.groups.ui.invitesandrequests.joining.GroupJoinUpdateRequiredBottomSheetDialogFragment;
import su.sres.securesms.groups.v2.GroupInviteLinkUrl;
import su.sres.core.util.logging.Log;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.proxy.ProxyBottomSheetFragment;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.sms.MessageSender;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.util.concurrent.SimpleTask;

public class CommunicationActions {

  private static final String TAG = Log.tag(CommunicationActions.class);

  public static void startVoiceCall(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {

    if (TelephonyUtil.isAnyPstnLineBusy(activity)) {
      Toast.makeText(activity,
                     R.string.CommunicationActions_a_cellular_call_is_already_in_progress,
                     Toast.LENGTH_SHORT)
           .show();
      return;
    }

    if (recipient.isRegistered()) {
      ApplicationDependencies.getSignalCallManager().isCallActive(new ResultReceiver(new Handler(Looper.getMainLooper())) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
          if (resultCode == 1) {
            startCallInternal(activity, recipient, false);
          } else {
            new AlertDialog.Builder(activity)
                .setMessage(R.string.CommunicationActions_start_voice_call)
                .setPositiveButton(R.string.CommunicationActions_call, (d, w) -> startCallInternal(activity, recipient, false))
                .setNegativeButton(R.string.CommunicationActions_cancel, (d, w) -> d.dismiss())
                .setCancelable(true)
                .show();
          }
        }
      });
    }
  }

  public static void startVideoCall(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {
    if (TelephonyUtil.isAnyPstnLineBusy(activity)) {
      Toast.makeText(activity,
                     R.string.CommunicationActions_a_cellular_call_is_already_in_progress,
                     Toast.LENGTH_SHORT)
           .show();
      return;
    }

    ApplicationDependencies.getSignalCallManager().isCallActive(new ResultReceiver(new Handler(Looper.getMainLooper())) {
      @Override
      protected void onReceiveResult(int resultCode, Bundle resultData) {
        startCallInternal(activity, recipient, resultCode != 1);
      }
    });
  }

  public static void startConversation(@NonNull Context context, @NonNull Recipient recipient, @Nullable String text) {
    startConversation(context, recipient, text, null);
  }

  public static void startConversation(@NonNull Context context,
                                       @NonNull Recipient recipient,
                                       @Nullable String text,
                                       @Nullable TaskStackBuilder backStack)
  {
    new AsyncTask<Void, Void, Long>() {
      @Override
      protected Long doInBackground(Void... voids) {
        return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient.getId());
      }

      @Override
      protected void onPostExecute(@Nullable Long threadId) {
        ConversationIntents.Builder builder = ConversationIntents.createBuilder(context, recipient.getId(), threadId != null ? threadId : -1);

        if (!TextUtils.isEmpty(text)) {
          builder.withDraftText(text);
        }

        Intent intent = builder.build();

        if (backStack != null) {
          backStack.addNextIntent(intent);
          backStack.startActivities();
        } else {
          context.startActivity(intent);
        }
      }
    }.execute();
  }

  public static void composeSmsThroughDefaultApp(@NonNull Context context, @NonNull Recipient recipient, @Nullable String text) {
    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + recipient.requireSmsAddress()));
    if (text != null) {
      intent.putExtra("sms_body", text);
    }
    context.startActivity(intent);
  }

  public static void openBrowserLink(@NonNull Context context, @NonNull String link) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
      context.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, R.string.CommunicationActions_no_browser_found, Toast.LENGTH_SHORT).show();
    }
  }

  public static void openEmail(@NonNull Context context, @NonNull String address, @Nullable String subject, @Nullable String body) {
    Intent intent = new Intent(Intent.ACTION_SENDTO);
    intent.setData(Uri.parse("mailto:"));
    intent.putExtra(Intent.EXTRA_EMAIL, new String[] { address });
    intent.putExtra(Intent.EXTRA_SUBJECT, Util.emptyIfNull(subject));
    intent.putExtra(Intent.EXTRA_TEXT, Util.emptyIfNull(body));

    context.startActivity(Intent.createChooser(intent, context.getString(R.string.CommunicationActions_send_email)));
  }

  /**
   * If the url is a group link it will handle it.
   * If the url is a malformed group link, it will assume Shadow needs to update.
   * Otherwise returns false, indicating was not a group link.
   */
  public static boolean handlePotentialGroupLinkUrl(@NonNull FragmentActivity activity, @NonNull String potentialGroupLinkUrl) {
    try {
      GroupInviteLinkUrl groupInviteLinkUrl = GroupInviteLinkUrl.fromUri(potentialGroupLinkUrl);

      if (groupInviteLinkUrl == null) {
        return false;
      }

      handleGroupLinkUrl(activity, groupInviteLinkUrl);
      return true;
    } catch (GroupInviteLinkUrl.InvalidGroupLinkException e) {
      Log.w(TAG, "Could not parse group URL", e);
      Toast.makeText(activity, R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_group_link_is_not_valid, Toast.LENGTH_SHORT).show();
      return true;
    } catch (GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
      Log.w(TAG, "Group link is for an advanced version", e);
      GroupJoinUpdateRequiredBottomSheetDialogFragment.show(activity.getSupportFragmentManager());
      return true;
    }
  }

  public static void handleGroupLinkUrl(@NonNull FragmentActivity activity,
                                        @NonNull GroupInviteLinkUrl groupInviteLinkUrl)
  {
    GroupId.V2 groupId = GroupId.v2(groupInviteLinkUrl.getGroupMasterKey());

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
                     GroupDatabase.GroupRecord group = DatabaseFactory.getGroupDatabase(activity)
                                                                      .getGroup(groupId)
                                                                      .orNull();

                     return group != null && group.isActive() ? Recipient.resolved(group.getRecipientId())
                                                              : null;
                   },
                   recipient -> {
                     if (recipient != null) {
                       CommunicationActions.startConversation(activity, recipient, null);
                       Toast.makeText(activity, R.string.GroupJoinBottomSheetDialogFragment_you_are_already_a_member, Toast.LENGTH_SHORT).show();
                     } else {
                       GroupJoinBottomSheetDialogFragment.show(activity.getSupportFragmentManager(), groupInviteLinkUrl);
                     }
                   });
  }

  private static void startCallInternal(@NonNull FragmentActivity activity, @NonNull Recipient recipient, boolean isVideo) {
    if (isVideo) startVideoCallInternal(activity, recipient);
    else startAudioCallInternal(activity, recipient);
  }

  /**
   * If the url is a proxy link it will handle it.
   * Otherwise returns false, indicating was not a proxy link.
   */
  public static boolean handlePotentialProxyLinkUrl(@NonNull FragmentActivity activity, @NonNull String potentialProxyLinkUrl) {
    String proxy = ShadowProxyUtil.parseHostFromProxyDeepLink(potentialProxyLinkUrl);

    if (proxy != null) {
      ProxyBottomSheetFragment.showForProxy(activity.getSupportFragmentManager(), proxy);
      return true;
    } else {
      return false;
    }
  }

  private static void startAudioCallInternal(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {
    Permissions.with(activity)
               .request(Manifest.permission.RECORD_AUDIO)
               .ifNecessary()
               .withRationaleDialog(activity.getString(R.string.ConversationActivity__to_call_s_signal_needs_access_to_your_microphone, recipient.getDisplayName(activity)),
                                    R.drawable.ic_mic_solid_24)
               .withPermanentDenialDialog(activity.getString(R.string.ConversationActivity__to_call_s_signal_needs_access_to_your_microphone, recipient.getDisplayName(activity)))
               .onAllGranted(() -> {
                 ApplicationDependencies.getSignalCallManager().startOutgoingAudioCall(recipient);

                 MessageSender.onMessageSent();

                 Intent activityIntent = new Intent(activity, WebRtcCallActivity.class);

                 activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                 activity.startActivity(activityIntent);
               })
               .execute();
  }

  private static void startVideoCallInternal(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {
    Permissions.with(activity)
               .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(activity.getString(R.string.ConversationActivity_signal_needs_the_microphone_and_camera_permissions_in_order_to_call_s, recipient.getDisplayName(activity)),
                                    R.drawable.ic_mic_solid_24,
                                    R.drawable.ic_video_solid_24_tinted)
               .withPermanentDenialDialog(activity.getString(R.string.ConversationActivity_signal_needs_the_microphone_and_camera_permissions_in_order_to_call_s, recipient.getDisplayName(activity)))
               .onAllGranted(() -> {
                 ApplicationDependencies.getSignalCallManager().startPreJoinCall(recipient);

                 Intent activityIntent = new Intent(activity, WebRtcCallActivity.class);

                 activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                               .putExtra(WebRtcCallActivity.EXTRA_ENABLE_VIDEO_IF_AVAILABLE, true);

                 activity.startActivity(activityIntent);
               })
               .execute();

  }
}

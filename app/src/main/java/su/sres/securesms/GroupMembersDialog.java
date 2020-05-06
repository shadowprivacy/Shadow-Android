package su.sres.securesms;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.groups.ui.GroupMemberEntry;
import su.sres.securesms.groups.ui.GroupMemberListView;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientExporter;
import su.sres.securesms.util.concurrent.SimpleTask;

import java.util.ArrayList;

public final class GroupMembersDialog {

  private final Context   context;
  private final Recipient groupRecipient;
  private final Lifecycle lifecycle;

  public GroupMembersDialog(@NonNull Context context,
                            @NonNull Recipient groupRecipient,
                            @NonNull Lifecycle lifecycle)
  {
    this.context        = context;
    this.groupRecipient = groupRecipient;
    this.lifecycle      = lifecycle;
  }

  public void display() {
    SimpleTask.run(
            lifecycle,
            () -> DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupRecipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_INCLUDING_SELF),
            members -> {
              AlertDialog dialog = new AlertDialog.Builder(context)
                      .setTitle(R.string.ConversationActivity_group_members)
                      .setIconAttribute(R.attr.group_members_dialog_icon)
                      .setCancelable(true)
                      .setView(R.layout.dialog_group_members)
                      .setPositiveButton(android.R.string.ok, null)
                      .show();

              GroupMemberListView memberListView = dialog.findViewById(R.id.list_members);

              ArrayList<GroupMemberEntry.FullMember> pendingMembers = new ArrayList<>(members.size());
              for (Recipient member : members) {
                GroupMemberEntry.FullMember entry = new GroupMemberEntry.FullMember(member);

                entry.setOnClick(() -> contactClick(member));

                if (member.isLocalNumber()) {
                  pendingMembers.add(0, entry);
                } else {
                  pendingMembers.add(entry);
                }
        }

              //noinspection ConstantConditions
              memberListView.setMembers(pendingMembers);
      }
    );
  }

  private void contactClick(@NonNull Recipient recipient) {
    if (recipient.getContactUri() != null) {
      Intent intent = new Intent(context, RecipientPreferenceActivity.class);
      intent.putExtra(RecipientPreferenceActivity.RECIPIENT_ID, recipient.getId());

      context.startActivity(intent);
    } else {
      context.startActivity(RecipientExporter.export(recipient).asAddContactIntent());
    }
  }
}

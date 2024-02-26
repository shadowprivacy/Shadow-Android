package su.sres.securesms.groups.ui.addmembers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProviders;

import su.sres.securesms.ContactSelectionActivity;
import su.sres.securesms.ContactSelectionListFragment;
import su.sres.securesms.PushContactSelectionActivity;
import su.sres.securesms.R;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.SelectionLimits;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.Util;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;

public class AddMembersActivity extends PushContactSelectionActivity {

  public static final String GROUP_ID = "group_id";

  private View                done;
  private AddMembersViewModel viewModel;

  public static @NonNull Intent createIntent(@NonNull Context context,
                                             @NonNull GroupId groupId,
                                             int displayModeFlags,
                                             int selectionWarning,
                                             int selectionLimit,
                                             @NonNull List<RecipientId> membersWithoutSelf)
  {
    Intent intent = new Intent(context, AddMembersActivity.class);

    intent.putExtra(AddMembersActivity.GROUP_ID, groupId.toString());
    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayModeFlags);
    intent.putExtra(ContactSelectionListFragment.SELECTION_LIMITS, new SelectionLimits(selectionWarning, selectionLimit));
    intent.putParcelableArrayListExtra(ContactSelectionListFragment.CURRENT_SELECTION, new ArrayList<>(membersWithoutSelf));

    return intent;
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    getIntent().putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.add_members_activity);
    super.onCreate(icicle, ready);

    AddMembersViewModel.Factory factory = new AddMembersViewModel.Factory(getGroupId());

    done      = findViewById(R.id.done);
    viewModel = ViewModelProviders.of(this, factory)
                                  .get(AddMembersViewModel.class);

    done.setOnClickListener(v ->
                                viewModel.getDialogStateForSelectedContacts(contactsFragment.getSelectedContacts(), this::displayAlertMessage)
    );

    disableDone();
  }

  @Override
  protected void initializeToolbar() {
    getToolbar().setNavigationIcon(R.drawable.ic_arrow_left_24);
    getToolbar().setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  @Override
  public boolean onBeforeContactSelected(Optional<RecipientId> recipientId, String number) {
    if (getGroupId().isV1() && recipientId.isPresent() && !Recipient.resolved(recipientId.get()).hasE164()) {
      Toast.makeText(this, R.string.AddMembersActivity__this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show();
      return false;
    }

    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    enableDone();

    return true;
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    if (contactsFragment.getSelectedContactsCount() < 1) {
      disableDone();
    }
  }

  @Override
  public void onSelectionChanged() {
    int selectedContactsCount = contactsFragment.getTotalMemberCount() + 1;
    if (selectedContactsCount == 0) {
      getToolbar().setTitle(getString(R.string.AddMembersActivity__add_members));
    } else {
      getToolbar().setTitle(getResources().getQuantityString(R.plurals.CreateGroupActivity__d_members, selectedContactsCount, selectedContactsCount));
    }
  }

  private void enableDone() {
    done.setEnabled(true);
    done.animate().alpha(1f);
  }

  private void disableDone() {
    done.setEnabled(false);
    done.animate().alpha(0.5f);
  }

  private GroupId getGroupId() {
    return GroupId.parseOrThrow(getIntent().getStringExtra(GROUP_ID));
  }

  private void displayAlertMessage(@NonNull AddMembersViewModel.AddMemberDialogMessageState state) {
    Recipient recipient = Util.firstNonNull(state.getRecipient(), Recipient.UNKNOWN);

    String message = getResources().getQuantityString(R.plurals.AddMembersActivity__add_d_members_to_s, state.getSelectionCount(),
                                                      recipient.getDisplayName(this), state.getGroupTitle(), state.getSelectionCount());

    new AlertDialog.Builder(this)
        .setMessage(message)
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
        .setPositiveButton(R.string.AddMembersActivity__add, (dialog, which) -> {
          dialog.dismiss();
          onFinishedSelection();
        })
        .setCancelable(true)
        .show();
  }
}
package su.sres.securesms.groups.ui.creategroup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.annimon.stream.Stream;

import su.sres.securesms.ContactSelectionActivity;
import su.sres.securesms.ContactSelectionListFragment;
import su.sres.securesms.R;
import su.sres.securesms.contacts.ContactsCursorLoader;
import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.groups.GroupsV2CapabilityChecker;
import su.sres.securesms.groups.ui.creategroup.details.AddGroupDetailsActivity;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.Stopwatch;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.concurrent.SimpleTask;
import su.sres.securesms.util.views.SimpleProgressDialog;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CreateGroupActivity extends ContactSelectionActivity {

    private static final String TAG = Log.tag(CreateGroupActivity.class);

    private static final int   MINIMUM_GROUP_SIZE       = 1;
    private static final short REQUEST_CODE_ADD_DETAILS = 17275;

    private View next;

    public static Intent newIntent(@NonNull Context context) {

        Intent intent = new Intent(context, CreateGroupActivity.class);

        intent.putExtra(ContactSelectionListFragment.MULTI_SELECT, true);
        intent.putExtra(ContactSelectionListFragment.REFRESHABLE, false);
        intent.putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.create_group_activity);

        int displayMode = TextSecurePreferences.isSmsEnabled(context) ? ContactsCursorLoader.DisplayMode.FLAG_SMS | ContactsCursorLoader.DisplayMode.FLAG_PUSH
                : ContactsCursorLoader.DisplayMode.FLAG_PUSH;

        intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
        intent.putExtra(ContactSelectionListFragment.TOTAL_CAPACITY, FeatureFlags.groupsV2create() ? FeatureFlags.gv2GroupCapacity() - 1
                : ContactSelectionListFragment.NO_LIMIT);

        return intent;
    }

    @Override
    public void onCreate(Bundle bundle, boolean ready) {
        super.onCreate(bundle, ready);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        next = findViewById(R.id.next);

        disableNext();
        next.setOnClickListener(v -> handleNextPressed());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_ADD_DETAILS && resultCode == RESULT_OK) {
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onContactSelected(Optional<RecipientId> recipientId, String number) {
        if (contactsFragment.hasQueryFilter()) {
            getToolbar().clear();
        }

        enableNext();

        return true;
    }

    @Override
    public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
        if (contactsFragment.hasQueryFilter()) {
            getToolbar().clear();
        }

        if (contactsFragment.getSelectedContactsCount() < MINIMUM_GROUP_SIZE) {
            disableNext();
        }
    }

    private void enableNext() {
        next.setEnabled(true);
        next.animate().alpha(1f);
    }

    private void disableNext() {
        next.setEnabled(false);
        next.animate().alpha(0.5f);
    }

    private void handleNextPressed() {
        Stopwatch                              stopwatch         = new Stopwatch("Recipient Refresh");
        SimpleProgressDialog.DismissibleDialog dismissibleDialog = SimpleProgressDialog.showDelayed(this);

        SimpleTask.run(getLifecycle(), () -> {
            List<RecipientId> ids = Stream.of(contactsFragment.getSelectedContacts())
                    .map(selectedContact -> selectedContact.getOrCreateRecipientId(this))
                    .toList();

            List<Recipient> resolved = Recipient.resolvedList(ids);

            stopwatch.split("resolve");

            List<Recipient> registeredChecks = Stream.of(resolved)
                    .filter(r -> r.getRegistered() == RecipientDatabase.RegisteredState.UNKNOWN)
                    .toList();

            Log.i(TAG, "Need to do " + registeredChecks.size() + " registration checks.");

/*            for (Recipient recipient : registeredChecks) {
                try {
                    DirectoryHelper.refreshDirectoryFor(this, recipient, false);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to refresh registered status for " + recipient.getId(), e);
                }
            }

 */

            stopwatch.split("registered");

            if (FeatureFlags.groupsV2create()) {
                try {
                    GroupsV2CapabilityChecker.refreshCapabilitiesIfNecessary(resolved);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to refresh all recipient capabilities.", e);
                }
            }

            stopwatch.split("capabilities");

            resolved = Recipient.resolvedList(ids);

            if (Stream.of(resolved).anyMatch(r -> r.getGroupsV2Capability() != Recipient.Capability.SUPPORTED) &&
                    Stream.of(resolved).anyMatch(r -> !r.hasE164()))
            {
                Log.w(TAG, "Invalid GV1 group...");
                ids = Collections.emptyList();
            }

            stopwatch.split("gv1-check");

            return ids;
        }, ids -> {
            dismissibleDialog.dismiss();

            stopwatch.stop(TAG);

            if (ids.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.CreateGroupActivity_some_contacts_cannot_be_in_legacy_groups)
                        .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                        .show();
            } else {
                startActivityForResult(AddGroupDetailsActivity.newIntent(this, ids), REQUEST_CODE_ADD_DETAILS);
            }
        });
    }
}
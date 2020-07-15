package su.sres.securesms.groups.ui.creategroup.details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import su.sres.securesms.PassphraseRequiredActionBarActivity;
import su.sres.securesms.R;
import su.sres.securesms.conversation.ConversationActivity;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.DynamicNoActionBarTheme;
import su.sres.securesms.util.DynamicTheme;

public class AddGroupDetailsActivity extends PassphraseRequiredActionBarActivity implements AddGroupDetailsFragment.Callback {

    private static final String EXTRA_RECIPIENTS = "recipient_ids";

    private final DynamicTheme theme = new DynamicNoActionBarTheme();

    public static Intent newIntent(@NonNull Context context, @NonNull RecipientId[] recipients) {
        Intent intent = new Intent(context, AddGroupDetailsActivity.class);

        intent.putExtra(EXTRA_RECIPIENTS, recipients);

        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle bundle, boolean ready) {
        theme.onCreate(this);

        setContentView(R.layout.add_group_details_activity);

        if (bundle == null) {
            Parcelable[]  parcelables = getIntent().getParcelableArrayExtra(EXTRA_RECIPIENTS);
            RecipientId[] ids         = new RecipientId[parcelables.length];

            System.arraycopy(parcelables, 0, ids, 0, parcelables.length);

            AddGroupDetailsFragmentArgs arguments = new AddGroupDetailsFragmentArgs.Builder(ids).build();
            NavGraph                    graph     = Navigation.findNavController(this, R.id.nav_host_fragment).getGraph();

            Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(graph, arguments.toBundle());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        theme.onResume(this);
    }

    @Override
    public void onGroupCreated(@NonNull RecipientId recipientId, long threadId) {
        Intent intent = ConversationActivity.buildIntent(this,
                recipientId,
                threadId,
                ThreadDatabase.DistributionTypes.DEFAULT,
                -1);

        startActivity(intent);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onNavigationButtonPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
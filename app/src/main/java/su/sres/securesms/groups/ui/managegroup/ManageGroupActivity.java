package su.sres.securesms.groups.ui.managegroup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import su.sres.securesms.PassphraseRequiredActionBarActivity;
import su.sres.securesms.R;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.util.DynamicNoActionBarTheme;
import su.sres.securesms.util.DynamicTheme;

public class ManageGroupActivity extends PassphraseRequiredActionBarActivity {

    private static final String GROUP_ID = "GROUP_ID";

    private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

    public static Intent newIntent(@NonNull Context context, @NonNull GroupId groupId) {
        Intent intent = new Intent(context, ManageGroupActivity.class);
        intent.putExtra(GROUP_ID, groupId.toString());
        return intent;
    }

    @Override
    protected void onPreCreate() {
        dynamicTheme.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, boolean ready) {
        super.onCreate(savedInstanceState, ready);
        setContentView(R.layout.group_manage_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, ManageGroupFragment.newInstance(getIntent().getStringExtra(GROUP_ID)))
                    .commitNow();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
    }
}
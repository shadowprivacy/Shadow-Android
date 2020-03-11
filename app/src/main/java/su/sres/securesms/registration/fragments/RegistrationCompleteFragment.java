package su.sres.securesms.registration.fragments;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
// import androidx.navigation.ActivityNavigator;

import su.sres.securesms.MainActivity;
import su.sres.securesms.R;
import su.sres.securesms.logging.Log;
import su.sres.securesms.profiles.edit.EditProfileActivity;

public final class RegistrationCompleteFragment extends BaseRegistrationFragment {

    private static final String TAG = Log.tag(RegistrationCompleteFragment.class);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration_blank, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FragmentActivity activity = requireActivity();

//        if (!isReregister()) {
//            activity.startActivity(getRoutedIntent(activity, EditProfileActivity.class, new Intent(activity, MainActivity.class)));
            doRestart(activity);
//        }

//        activity.finish();
//        ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
    }

    private static Intent getRoutedIntent(@NonNull Context context, Class<?> destination, @Nullable Intent nextIntent) {
        final Intent intent = new Intent(context, destination);
        if (nextIntent != null) intent.putExtra("next_intent", nextIntent);
        return intent;
    }

    private void doRestart(FragmentActivity activity) {
        try {
             Intent mStartActivity = getRoutedIntent(activity, EditProfileActivity.class, new Intent(activity, MainActivity.class));

            mStartActivity.putExtra(EditProfileActivity.SHOW_TOOLBAR, false);

            mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        // We use an AlarmManager to call this intent in 100ms
                        int mPendingIntentId = 223344;
                        PendingIntent mPendingIntent = PendingIntent.getActivity(activity, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);

                        //kill the application
                        System.exit(0);

        } catch (Exception ex) {
            Log.e(TAG, "Was not able to restart application");
        }
    }
}
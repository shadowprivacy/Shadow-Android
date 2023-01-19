package su.sres.securesms.preferences;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.widget.Toast;

import su.sres.securesms.ApplicationPreferencesActivity;
import su.sres.securesms.PassphraseChangeActivity;
import su.sres.securesms.R;
import su.sres.securesms.blocked.BlockedUsersActivity;
import su.sres.securesms.components.SwitchPreferenceCompat;
import su.sres.securesms.crypto.MasterSecretUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import su.sres.securesms.jobs.RefreshAttributesJob;
import su.sres.securesms.keyvalue.UserLoginPrivacyValues;
import su.sres.securesms.keyvalue.SettingsValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.megaphone.Megaphones;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.service.KeyCachingService;
import su.sres.securesms.util.CommunicationActions;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import mobi.upod.timedurationpicker.TimeDurationPickerDialog;

public class AppProtectionPreferenceFragment extends CorrectedPreferenceFragment  {

  private static final String TAG = Log.tag(AppProtectionPreferenceFragment.class);

  private static final String PREFERENCE_CATEGORY_BLOCKED             = "preference_category_blocked";
  private static final String PREFERENCE_UNIDENTIFIED_LEARN_MORE      = "pref_unidentified_learn_more";
  private static final String PREFERENCE_WHO_CAN_SEE_USER_LOGIN     = "pref_who_can_see_user_login";
  private static final String PREFERENCE_WHO_CAN_FIND_BY_USER_LOGIN = "pref_who_can_find_by_user_login";

  private CheckBoxPreference disablePassphrase;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    disablePassphrase = (CheckBoxPreference) this.findPreference("pref_enable_passphrase_temporary");

    this.findPreference(TextSecurePreferences.SCREEN_LOCK).setOnPreferenceChangeListener(new ScreenLockListener());
    this.findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setOnPreferenceClickListener(new ScreenLockTimeoutListener());

    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF).setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF).setOnPreferenceClickListener(new PassphraseIntervalClickListener());
    this.findPreference(TextSecurePreferences.READ_RECEIPTS_PREF).setOnPreferenceChangeListener(new ReadReceiptToggleListener());
    this.findPreference(TextSecurePreferences.TYPING_INDICATORS).setOnPreferenceChangeListener(new TypingIndicatorsToggleListener());
    this.findPreference(PREFERENCE_CATEGORY_BLOCKED).setOnPreferenceClickListener(new BlockedContactsClickListener());
    this.findPreference(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS).setOnPreferenceChangeListener(new ShowUnidentifiedDeliveryIndicatorsChangedListener());
//    this.findPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS).setOnPreferenceChangeListener(new UniversalUnidentifiedAccessChangedListener());
    this.findPreference(PREFERENCE_UNIDENTIFIED_LEARN_MORE).setOnPreferenceClickListener(new UnidentifiedLearnMoreClickListener());
    disablePassphrase.setOnPreferenceChangeListener(new DisablePassphraseClickListener());

    if (FeatureFlags.UserLoginPrivacy()) {
      Preference whoCanSeeUserLogin    = this.findPreference(PREFERENCE_WHO_CAN_SEE_USER_LOGIN);
      Preference whoCanFindByUserLogin = this.findPreference(PREFERENCE_WHO_CAN_FIND_BY_USER_LOGIN);

      whoCanSeeUserLogin.setPreferenceDataStore(null);
      whoCanSeeUserLogin.setOnPreferenceClickListener(new UserLoginPrivacyWhoCanSeeClickListener());

      whoCanFindByUserLogin.setPreferenceDataStore(null);
      whoCanFindByUserLogin.setOnPreferenceClickListener(new UserLoginPrivacyWhoCanFindClickListener());
    } else {
      this.findPreference("category_user_login_privacy").setVisible(false);
    }

    SwitchPreferenceCompat linkPreviewPref = (SwitchPreferenceCompat) this.findPreference(SettingsValues.LINK_PREVIEWS);
    linkPreviewPref.setChecked(SignalStore.settings().isLinkPreviewsEnabled());
    linkPreviewPref.setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    linkPreviewPref.setOnPreferenceChangeListener(new LinkPreviewToggleListener());

    initializeVisibility();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_app_protection);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__privacy);

    if (!TextSecurePreferences.isPasswordDisabled(getContext())) initializePassphraseTimeoutSummary();
    else                                                         initializeScreenLockTimeoutSummary();

    disablePassphrase.setChecked(!TextSecurePreferences.isPasswordDisabled(getActivity()));

    initializeUserLoginPrivacyWhoCanSeeSummary();
    initializeUserLoginPrivacyWhoCanFindSummary();
  }

  private void initializePassphraseTimeoutSummary() {
    int timeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(getActivity());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF)
        .setSummary(getResources().getQuantityString(R.plurals.AppProtectionPreferenceFragment_minutes, timeoutMinutes, timeoutMinutes));
  }

  private void initializeScreenLockTimeoutSummary() {
    long timeoutSeconds = TextSecurePreferences.getScreenLockTimeout(getContext());
    long hours          = TimeUnit.SECONDS.toHours(timeoutSeconds);
    long minutes        = TimeUnit.SECONDS.toMinutes(timeoutSeconds) - (TimeUnit.SECONDS.toHours(timeoutSeconds) * 60  );
    long seconds        = TimeUnit.SECONDS.toSeconds(timeoutSeconds) - (TimeUnit.SECONDS.toMinutes(timeoutSeconds) * 60);

    findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)
        .setSummary(timeoutSeconds <= 0 ? getString(R.string.AppProtectionPreferenceFragment_none) :
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
  }

  private void initializeUserLoginPrivacyWhoCanSeeSummary() {
    Preference preference = findPreference(PREFERENCE_WHO_CAN_SEE_USER_LOGIN);

    switch (SignalStore.userLoginPrivacy().getUserLoginSharingMode()) {
      case EVERYONE: preference.setSummary(R.string.PhoneNumberPrivacy_everyone);    break;
      case NOBODY  : preference.setSummary(R.string.PhoneNumberPrivacy_nobody);      break;
      default      : throw new AssertionError();
    }
  }

  private void initializeUserLoginPrivacyWhoCanFindSummary() {
    Preference preference = findPreference(PREFERENCE_WHO_CAN_FIND_BY_USER_LOGIN);

    switch (SignalStore.userLoginPrivacy().getUserLoginListingMode()) {
      case LISTED  : preference.setSummary(R.string.PhoneNumberPrivacy_everyone); break;
      case UNLISTED: preference.setSummary(R.string.PhoneNumberPrivacy_nobody);   break;
      default      : throw new AssertionError();
    }
  }

  private void initializeVisibility() {
    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      findPreference("pref_enable_passphrase_temporary").setVisible(false);
      findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF).setVisible(false);
      findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF).setVisible(false);
      findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF).setVisible(false);

      KeyguardManager keyguardManager = (KeyguardManager)getContext().getSystemService(Context.KEYGUARD_SERVICE);
      if (!keyguardManager.isKeyguardSecure()) {
        ((SwitchPreferenceCompat)findPreference(TextSecurePreferences.SCREEN_LOCK)).setChecked(false);
        findPreference(TextSecurePreferences.SCREEN_LOCK).setEnabled(false);
      }
    } else {
      findPreference(TextSecurePreferences.SCREEN_LOCK).setVisible(false);
      findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setVisible(false);
    }
  }

  private class ScreenLockListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (Boolean)newValue;
      TextSecurePreferences.setScreenLockEnabled(getContext(), enabled);

      Intent intent = new Intent(getContext(), KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCK_TOGGLED_EVENT);
      getContext().startService(intent);
      return true;
    }
  }

  private class ScreenLockTimeoutListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(getContext(), (view, duration) -> {
        if (duration == 0) {
          TextSecurePreferences.setScreenLockTimeout(getContext(), 0);
        } else {
          long timeoutSeconds = Math.max(TimeUnit.MILLISECONDS.toSeconds(duration), 60);
          TextSecurePreferences.setScreenLockTimeout(getContext(), timeoutSeconds);
        }

        initializeScreenLockTimeoutSummary();
      }, 0).show();

      return true;
    }
  }

  private class BlockedContactsClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), BlockedUsersActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class ReadReceiptToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
//        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(enabled,
                TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                SignalStore.settings().isLinkPreviewsEnabled()));

      });

      return true;
    }
  }

  private class TypingIndicatorsToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
//        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                enabled,
                TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                SignalStore.settings().isLinkPreviewsEnabled()));

        if (!enabled) {
          ApplicationDependencies.getTypingStatusRepository().clear();
        }
      });

      return true;
    }
  }

  private class LinkPreviewToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
//        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(requireContext()),
                enabled));
        if (enabled) {
          ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.LINK_PREVIEWS);
        }
      });

      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final int    privacySummaryResId = R.string.ApplicationPreferencesActivity_privacy_summary;
    final String onRes               = context.getString(R.string.ApplicationPreferencesActivity_on);
    final String offRes              = context.getString(R.string.ApplicationPreferencesActivity_off);

    if (TextSecurePreferences.isPasswordDisabled(context) && !TextSecurePreferences.isScreenLockEnabled(context)) {
        return context.getString(privacySummaryResId, offRes);
    } else {
        return context.getString(privacySummaryResId, onRes);
    }
  }

  // Derecated

  private class ChangePassphraseClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      if (MasterSecretUtil.isPassphraseInitialized(getActivity())) {
        startActivity(new Intent(getActivity(), PassphraseChangeActivity.class));
      } else {
        Toast.makeText(getActivity(),
                       R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class PassphraseIntervalClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(getContext(), (view, duration) -> {
        int timeoutMinutes = Math.max((int)TimeUnit.MILLISECONDS.toMinutes(duration), 1);

        TextSecurePreferences.setPassphraseTimeoutInterval(getActivity(), timeoutMinutes);

        initializePassphraseTimeoutSummary();

      }, 0).show();

      return true;
    }
  }

  private class DisablePassphraseClickListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_passphrase);
        builder.setMessage(R.string.ApplicationPreferencesActivity_this_will_permanently_unlock_signal_and_message_notifications);
        builder.setIcon(R.drawable.ic_warning);
        builder.setPositiveButton(R.string.ApplicationPreferencesActivity_disable, (dialog, which) -> {
          MasterSecretUtil.changeMasterSecretPassphrase(getActivity(),
                                                        KeyCachingService.getMasterSecret(getContext()),
                                                        MasterSecretUtil.UNENCRYPTED_PASSPHRASE);

          TextSecurePreferences.setPasswordDisabled(getActivity(), true);
          ((CheckBoxPreference)preference).setChecked(false);

          Intent intent = new Intent(getActivity(), KeyCachingService.class);
          intent.setAction(KeyCachingService.DISABLE_ACTION);
          getActivity().startService(intent);

          initializeVisibility();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
      } else {
        Intent intent = new Intent(getActivity(), PassphraseChangeActivity.class);
        startActivity(intent);
      }

      return false;
    }
  }

  private class ShowUnidentifiedDeliveryIndicatorsChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (boolean) newValue;
      SignalExecutors.BOUNDED.execute(() -> {
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
//        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(getContext()),
                TextSecurePreferences.isTypingIndicatorsEnabled(getContext()),
                enabled,
                SignalStore.settings().isLinkPreviewsEnabled()));
      });

      return true;
    }
  }

  private class UniversalUnidentifiedAccessChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return true;
    }
  }

  private class UnidentifiedLearnMoreClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      CommunicationActions.openBrowserLink(preference.getContext(), "https://signal.org/blog/sealed-sender/");
      return true;
    }
  }

  private final class UserLoginPrivacyWhoCanSeeClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      UserLoginPrivacyValues userLoginPrivacyValues = SignalStore.userLoginPrivacy();

      final UserLoginPrivacyValues.UserLoginSharingMode[] value = { userLoginPrivacyValues.getUserLoginSharingMode() };

      Map<UserLoginPrivacyValues.UserLoginSharingMode, CharSequence> items        = items(requireContext());
      List<UserLoginPrivacyValues.UserLoginSharingMode> modes        = new ArrayList<>(items.keySet());
      CharSequence[]                                                     modeStrings  = items.values().toArray(new CharSequence[0]);
      int                                                                selectedMode = modes.indexOf(value[0]);

      new AlertDialog.Builder(requireActivity())
              .setTitle(R.string.preferences_app_protection__see_my_phone_number)
              .setCancelable(true)
              .setSingleChoiceItems(modeStrings, selectedMode, (dialog, which) -> value[0] = modes.get(which))
              .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                UserLoginPrivacyValues.UserLoginSharingMode UserLoginSharingMode = value[0];
                userLoginPrivacyValues.setUserLoginSharingMode(UserLoginSharingMode);
                Log.i(TAG, String.format("UserLoginSharingMode changed to %s. Scheduling storage value sync", UserLoginSharingMode));
                // StorageSyncHelper.scheduleSyncForDataChange();
                initializeUserLoginPrivacyWhoCanSeeSummary();
              })
              .setNegativeButton(android.R.string.cancel, null)
              .show();

      return true;
    }

    private Map<UserLoginPrivacyValues.UserLoginSharingMode, CharSequence> items(Context context) {
      Map<UserLoginPrivacyValues.UserLoginSharingMode, CharSequence> map = new LinkedHashMap<>();

      map.put(UserLoginPrivacyValues.UserLoginSharingMode.EVERYONE, titleAndDescription(context, context.getString(R.string.PhoneNumberPrivacy_everyone), context.getString(R.string.PhoneNumberPrivacy_everyone_see_description)));
      map.put(UserLoginPrivacyValues.UserLoginSharingMode.NOBODY, context.getString(R.string.PhoneNumberPrivacy_nobody));

      return map;
    }
  }

  private final class UserLoginPrivacyWhoCanFindClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      UserLoginPrivacyValues userLoginPrivacyValues = SignalStore.userLoginPrivacy();

      final UserLoginPrivacyValues.UserLoginListingMode[] value = { userLoginPrivacyValues.getUserLoginListingMode() };

      new AlertDialog.Builder(requireActivity())
              .setTitle(R.string.preferences_app_protection__find_me_by_phone_number)
              .setCancelable(true)
              .setSingleChoiceItems(items(requireContext()),
                      value[0].ordinal(),
                      (dialog, which) -> value[0] = UserLoginPrivacyValues.UserLoginListingMode.values()[which])
              .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                UserLoginPrivacyValues.UserLoginListingMode UserLoginListingMode = value[0];
                userLoginPrivacyValues.setUserLoginListingMode(UserLoginListingMode);
                Log.i(TAG, String.format("UserLoginListingMode changed to %s. Scheduling storage value sync", UserLoginListingMode));
                // StorageSyncHelper.scheduleSyncForDataChange();
                ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
                initializeUserLoginPrivacyWhoCanFindSummary();
              })
              .setNegativeButton(android.R.string.cancel, null)
              .show();

      return true;
    }

    private CharSequence[] items(Context context) {
      return new CharSequence[]{
              titleAndDescription(context, context.getString(R.string.PhoneNumberPrivacy_everyone), context.getString(R.string.PhoneNumberPrivacy_everyone_find_description)),
              context.getString(R.string.PhoneNumberPrivacy_nobody) };
    }
  }

  /** Adds a detail row for radio group descriptions. */
  private static CharSequence titleAndDescription(@NonNull Context context, @NonNull String header, @NonNull String description) {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    builder.append("\n");
    builder.append(header);
    builder.append("\n");

    builder.setSpan(new TextAppearanceSpan(context, android.R.style.TextAppearance_Small), builder.length(), builder.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

    builder.append(description);
    builder.append("\n");

    return builder;
  }
}

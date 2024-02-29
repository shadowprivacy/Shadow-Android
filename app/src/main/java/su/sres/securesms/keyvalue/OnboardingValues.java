package su.sres.securesms.keyvalue;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class OnboardingValues extends SignalStoreValues {

  private static final String SHOW_NEW_GROUP  = "onboarding.new_group";
  private static final String SHOW_APPEARANCE = "onboarding.appearance";
  private static final String SHOW_ADD_PHOTO  = "onboarding.add_photo";

  OnboardingValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putBoolean(SHOW_NEW_GROUP, true);
    putBoolean(SHOW_APPEARANCE, true);
    putBoolean(SHOW_ADD_PHOTO, true);
  }

  @Override
  @NonNull
  List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  public void clearAll() {
    setShowNewGroup(false);
    setShowAppearance(false);
    setShowAddPhoto(false);
  }

  public boolean hasOnboarding(@NonNull Context context) {
    return shouldShowNewGroup() ||
           shouldShowAppearance() ||
           shouldShowAddPhoto();
  }

  public void setShowNewGroup(boolean value) {
    putBoolean(SHOW_NEW_GROUP, value);
  }

  public boolean shouldShowNewGroup() {
    return getBoolean(SHOW_NEW_GROUP, false);
  }

  public void setShowAppearance(boolean value) {
    putBoolean(SHOW_APPEARANCE, value);
  }

  public boolean shouldShowAppearance() {
    return getBoolean(SHOW_APPEARANCE, false);
  }

  public void setShowAddPhoto(boolean value) {
    putBoolean(SHOW_ADD_PHOTO, value);
  }

  public boolean shouldShowAddPhoto() {
    return getBoolean(SHOW_ADD_PHOTO, false);
  }
}

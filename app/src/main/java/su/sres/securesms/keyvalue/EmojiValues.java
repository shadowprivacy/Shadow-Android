package su.sres.securesms.keyvalue;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import su.sres.securesms.components.emoji.EmojiUtil;
import su.sres.securesms.util.Util;

public class EmojiValues extends SignalStoreValues {

  public static final List<String> DEFAULT_REACTIONS_LIST = Arrays.asList("\u2764\ufe0f",
                                                                          "\ud83d\udc4d",
                                                                          "\ud83d\udc4e",
                                                                          "\ud83d\ude02",
                                                                          "\ud83d\ude2e",
                                                                          "\ud83d\ude22");

  private static final String PREFIX               = "emojiPref__";
  private static final String NEXT_SCHEDULED_CHECK = PREFIX + "next_scheduled_check";
  private static final String REACTIONS_LIST       = PREFIX + "reactions_list";
  private static final String SEARCH_VERSION       = PREFIX + "search_version";
  private static final String SEARCH_LANGUAGE      = PREFIX + "search_language";
  private static final String LAST_SEARCH_CHECK    = PREFIX + "last_search_check";

  public static final String NO_LANGUAGE = "NO_LANGUAGE";

  EmojiValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putInteger(SEARCH_VERSION, 0);
  }

  @Override
  @NonNull
  List<String> getKeysToIncludeInBackup() {
    return Collections.singletonList(REACTIONS_LIST);
  }

  public long getNextScheduledImageCheck() {
    return getStore().getLong(NEXT_SCHEDULED_CHECK, 0);
  }

  public void setNextScheduledImageCheck(long nextScheduledCheck) {
    putLong(NEXT_SCHEDULED_CHECK, nextScheduledCheck);
  }

  public void setPreferredVariation(@NonNull String emoji) {
    String canonical = EmojiUtil.getCanonicalRepresentation(emoji);

    if (canonical.equals(emoji)) {
      getStore().beginWrite().remove(PREFIX + canonical).apply();
    } else {
      putString(PREFIX + canonical, emoji);
    }
  }

  public @NonNull String getPreferredVariation(@NonNull String emoji) {
    String canonical = EmojiUtil.getCanonicalRepresentation(emoji);

    return getString(PREFIX + canonical, emoji);
  }

  public @NonNull List<String> getReactions() {
    String list = getString(REACTIONS_LIST, "");
    if (TextUtils.isEmpty(list)) {
      return DEFAULT_REACTIONS_LIST;
    } else {
      return Arrays.asList(list.split(","));
    }
  }

  public void setReactions(List<String> reactions) {
    putString(REACTIONS_LIST, Util.join(reactions, ","));
  }

  public void onSearchIndexUpdated(int version, @NonNull String language) {
    getStore().beginWrite()
              .putInteger(SEARCH_VERSION, version)
              .putString(SEARCH_LANGUAGE, language)
              .apply();
  }

  public boolean hasSearchIndex() {
    return getSearchVersion() > 0 && getSearchLanguage() != null;
  }

  public int getSearchVersion() {
    return getInteger(SEARCH_VERSION, 0);
  }

  public @Nullable String getSearchLanguage() {
    return getString(SEARCH_LANGUAGE, null);
  }

  public long getLastSearchIndexCheck() {
    return getLong(LAST_SEARCH_CHECK, 0);
  }

  public void setLastSearchIndexCheck(long time) {
    putLong(LAST_SEARCH_CHECK, time);
  }
}
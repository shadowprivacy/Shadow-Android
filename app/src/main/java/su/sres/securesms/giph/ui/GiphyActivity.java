package su.sres.securesms.giph.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import android.widget.Toast;

import su.sres.securesms.PassphraseRequiredActivity;
import su.sres.securesms.R;
import su.sres.securesms.giph.mp4.GiphyMp4Fragment;
import su.sres.securesms.giph.mp4.GiphyMp4SaveResult;
import su.sres.securesms.giph.mp4.GiphyMp4ViewModel;
import su.sres.securesms.keyboard.emoji.KeyboardPageSearchView;
import su.sres.securesms.util.DynamicNoActionBarTheme;
import su.sres.securesms.util.DynamicTheme;
import su.sres.securesms.util.ViewUtil;
import su.sres.securesms.util.views.SimpleProgressDialog;

public class GiphyActivity extends PassphraseRequiredActivity implements KeyboardPageSearchView.Callbacks {

  public static final String EXTRA_IS_MMS = "extra_is_mms";
  public static final String EXTRA_WIDTH  = "extra_width";
  public static final String EXTRA_HEIGHT = "extra_height";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private GiphyMp4ViewModel giphyMp4ViewModel;
  private AlertDialog       progressDialog;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.giphy_activity);

    final boolean forMms = getIntent().getBooleanExtra(EXTRA_IS_MMS, false);

    giphyMp4ViewModel = ViewModelProviders.of(this, new GiphyMp4ViewModel.Factory(forMms)).get(GiphyMp4ViewModel.class);
    giphyMp4ViewModel.getSaveResultEvents().observe(this, this::handleGiphyMp4SaveResult);

    initializeToolbar();

    Fragment fragment = GiphyMp4Fragment.create(forMms);
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, fragment)
                               .commit();

    ViewUtil.focusAndShowKeyboard(findViewById(R.id.emoji_search_entry));
  }

  private void initializeToolbar() {
    KeyboardPageSearchView searchView = findViewById(R.id.giphy_search_text);
    searchView.setCallbacks(this);
    searchView.enableBackNavigation(true);
    ViewUtil.focusAndShowKeyboard(searchView);
  }

  private void handleGiphyMp4SaveResult(@NonNull GiphyMp4SaveResult result) {
    if (result instanceof GiphyMp4SaveResult.Success) {
      hideProgressDialog();
      handleGiphyMp4SuccessfulResult((GiphyMp4SaveResult.Success) result);
    } else if (result instanceof GiphyMp4SaveResult.Error) {
      hideProgressDialog();
      handleGiphyMp4ErrorResult((GiphyMp4SaveResult.Error) result);
    } else {
      progressDialog = SimpleProgressDialog.show(this);
    }
  }

  private void hideProgressDialog() {
    if (progressDialog != null) {
      progressDialog.dismiss();
    }
  }

  private void handleGiphyMp4SuccessfulResult(@NonNull GiphyMp4SaveResult.Success success) {
    Intent intent = new Intent();
    intent.setData(success.getBlobUri());
    intent.putExtra(EXTRA_WIDTH, success.getWidth());
    intent.putExtra(EXTRA_HEIGHT, success.getHeight());

    setResult(RESULT_OK, intent);
    finish();
  }

  private void handleGiphyMp4ErrorResult(@NonNull GiphyMp4SaveResult.Error error) {
    Toast.makeText(this, R.string.GiphyActivity_error_while_retrieving_full_resolution_gif, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onQueryChanged(@NonNull String query) {
    giphyMp4ViewModel.updateSearchQuery(query);
  }

  @Override
  public void onNavigationClicked() {
    ViewUtil.hideKeyboard(this, findViewById(android.R.id.content));
    finish();
  }

  @Override
  public void onFocusLost() {}

  @Override
  public void onFocusGained() {}

  @Override
  public void onClicked() {}
}

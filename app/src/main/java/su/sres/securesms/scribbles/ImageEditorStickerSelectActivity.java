package su.sres.securesms.scribbles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProviders;

import su.sres.securesms.R;
import su.sres.securesms.components.emoji.MediaKeyboard;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.model.StickerRecord;
import su.sres.securesms.keyboard.KeyboardPage;
import su.sres.securesms.keyboard.KeyboardPagerViewModel;
import su.sres.securesms.stickers.StickerKeyboardProvider;
import su.sres.securesms.stickers.StickerManagementActivity;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.util.ViewUtil;

public final class ImageEditorStickerSelectActivity extends AppCompatActivity implements StickerKeyboardProvider.StickerEventListener, MediaKeyboard.MediaKeyboardListener {

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.scribble_select_new_sticker_activity);

    KeyboardPagerViewModel keyboardPagerViewModel = ViewModelProviders.of(this).get(KeyboardPagerViewModel.class);
    keyboardPagerViewModel.setOnlyPage(KeyboardPage.STICKER);

    MediaKeyboard mediaKeyboard = findViewById(R.id.emoji_drawer);
    mediaKeyboard.show();
  }

  @Override
  public void onShown() {
  }

  @Override
  public void onHidden() {
    finish();
  }

  @Override
  public void onKeyboardChanged(@NonNull KeyboardPage page) {
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord sticker) {
    Intent intent = new Intent();
    intent.setData(sticker.getUri());
    setResult(RESULT_OK, intent);

    SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getStickerDatabase(getApplicationContext())
                                                         .updateStickerLastUsedTime(sticker.getRowId(), System.currentTimeMillis()));
    ViewUtil.hideKeyboard(this, findViewById(android.R.id.content));
    finish();
  }

  @Override
  public void onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(ImageEditorStickerSelectActivity.this));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
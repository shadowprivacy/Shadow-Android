package su.sres.securesms.giph.ui;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.Loader;

import su.sres.securesms.giph.model.GiphyImage;
import su.sres.securesms.giph.net.GiphyStickerLoader;

import java.util.List;

public class GiphyStickerFragment extends GiphyFragment {
  @Override
  public @NonNull Loader<List<GiphyImage>> onCreateLoader(int id, Bundle args) {
    return new GiphyStickerLoader(getActivity(), searchString);
  }
}

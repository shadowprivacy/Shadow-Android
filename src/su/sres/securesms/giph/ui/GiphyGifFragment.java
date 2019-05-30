package su.sres.securesms.giph.ui;


import android.os.Bundle;
import android.support.v4.content.Loader;

import su.sres.securesms.giph.model.GiphyImage;
import su.sres.securesms.giph.net.GiphyGifLoader;

import java.util.List;

public class GiphyGifFragment extends GiphyFragment {

  @Override
  public Loader<List<GiphyImage>> onCreateLoader(int id, Bundle args) {
    return new GiphyGifLoader(getActivity(), searchString);
  }

}

package su.sres.securesms.video.exo;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import su.sres.securesms.mms.PartAuthority;
import su.sres.securesms.providers.BlobProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AttachmentDataSource implements DataSource {

  private final DefaultDataSource defaultDataSource;
  private final PartDataSource    partDataSource;
  private final BlobDataSource    blobDataSource;

  private DataSource dataSource;

  public AttachmentDataSource(DefaultDataSource defaultDataSource,
                              PartDataSource partDataSource,
                              BlobDataSource blobDataSource)
  {
    this.defaultDataSource = defaultDataSource;
    this.partDataSource    = partDataSource;
    this.blobDataSource    = blobDataSource;
  }

  @Override
  public void addTransferListener(@NonNull TransferListener transferListener) {
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    if (BlobProvider.isAuthority(dataSpec.uri)) dataSource = blobDataSource;
    else if (PartAuthority.isLocalUri(dataSpec.uri)) dataSource = partDataSource;
    else dataSource = defaultDataSource;

    return dataSource.open(dataSpec);
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
    return dataSource.read(buffer, offset, readLength);
  }

  @Override
  public Uri getUri() {
    return dataSource.getUri();
  }

  @Override
  public @NonNull Map<String, List<String>> getResponseHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public void close() throws IOException {
    dataSource.close();
  }
}

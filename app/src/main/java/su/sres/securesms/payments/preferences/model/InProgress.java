package su.sres.securesms.payments.preferences.model;

import androidx.annotation.NonNull;

import su.sres.securesms.util.MappingModel;

public class InProgress implements MappingModel<InProgress> {
  @Override
  public boolean areItemsTheSame(@NonNull InProgress newItem) {
    return true;
  }

  @Override
  public boolean areContentsTheSame(@NonNull InProgress newItem) {
    return true;
  }
}

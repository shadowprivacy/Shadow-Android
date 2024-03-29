package su.sres.securesms.payments.preferences;

import androidx.annotation.NonNull;

import su.sres.securesms.R;
import su.sres.securesms.components.settings.SettingHeader;
import su.sres.securesms.payments.preferences.model.InProgress;
import su.sres.securesms.payments.preferences.model.InfoCard;
import su.sres.securesms.payments.preferences.model.IntroducingPayments;
import su.sres.securesms.payments.preferences.model.NoRecentActivity;
import su.sres.securesms.payments.preferences.model.PaymentItem;
import su.sres.securesms.payments.preferences.model.SeeAll;
import su.sres.securesms.payments.preferences.viewholder.InProgressViewHolder;
import su.sres.securesms.payments.preferences.viewholder.InfoCardViewHolder;
import su.sres.securesms.payments.preferences.viewholder.IntroducingPaymentViewHolder;
import su.sres.securesms.payments.preferences.viewholder.NoRecentActivityViewHolder;
import su.sres.securesms.payments.preferences.viewholder.PaymentItemViewHolder;
import su.sres.securesms.payments.preferences.viewholder.SeeAllViewHolder;
import su.sres.securesms.util.MappingAdapter;

public class PaymentsHomeAdapter extends MappingAdapter {

  public PaymentsHomeAdapter(@NonNull Callbacks callbacks) {
    registerFactory(IntroducingPayments.class, p -> new IntroducingPaymentViewHolder(p, callbacks), R.layout.payments_home_introducing_payments_item);
    registerFactory(NoRecentActivity.class, NoRecentActivityViewHolder::new, R.layout.payments_home_no_recent_activity_item);
    registerFactory(InProgress.class, InProgressViewHolder::new, R.layout.payments_home_in_progress);
    registerFactory(PaymentItem.class, p -> new PaymentItemViewHolder(p, callbacks), R.layout.payments_home_payment_item);
    registerFactory(SettingHeader.Item.class, SettingHeader.ViewHolder::new, R.layout.base_settings_header_item);
    registerFactory(SeeAll.class, p -> new SeeAllViewHolder(p, callbacks), R.layout.payments_home_see_all_item);
    registerFactory(InfoCard.class, p -> new InfoCardViewHolder(p, callbacks), R.layout.payment_info_card);
  }

  public interface Callbacks {
    default void onActivatePayments() {}
    default void onRestorePaymentsAccount() {}
    default void onSeeAll(@NonNull PaymentType paymentType) {}
    default void onPaymentItem(@NonNull PaymentItem model) {}
    default void onInfoCardDismissed() {}
    default void onViewRecoveryPhrase() {}
    default void onUpdatePin() {}
  }
}

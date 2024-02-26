package su.sres.securesms.payments.preferences;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.Navigation;

import su.sres.securesms.ContactSelectionListFragment;
import su.sres.securesms.LoggingFragment;
import su.sres.securesms.R;
import su.sres.securesms.components.ContactFilterView;
import su.sres.securesms.contacts.ContactsCursorLoader.DisplayMode;
import su.sres.securesms.conversation.ConversationIntents;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.payments.CanNotSendPaymentDialog;
import su.sres.securesms.payments.preferences.model.PayeeParcelable;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.ViewUtil;
import su.sres.securesms.util.concurrent.SimpleTask;

import org.whispersystems.libsignal.util.guava.Optional;


public class PaymentRecipientSelectionFragment extends LoggingFragment implements ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.ScrollCallback {

  private Toolbar                      toolbar;
  private ContactFilterView            contactFilterView;
  private ContactSelectionListFragment contactsFragment;

  public PaymentRecipientSelectionFragment() {
    super(R.layout.payment_recipient_selection_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    toolbar = view.findViewById(R.id.payment_recipient_selection_fragment_toolbar);
    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text);

    Bundle arguments = new Bundle();
    arguments.putBoolean(ContactSelectionListFragment.REFRESHABLE, false);
    arguments.putInt(ContactSelectionListFragment.DISPLAY_MODE, DisplayMode.FLAG_PUSH | DisplayMode.FLAG_HIDE_NEW);
    arguments.putBoolean(ContactSelectionListFragment.CAN_SELECT_SELF, false);

    Fragment child = getChildFragmentManager().findFragmentById(R.id.contact_selection_list_fragment_holder);
    if (child == null) {
      FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
      contactsFragment = new ContactSelectionListFragment();
      contactsFragment.setArguments(arguments);
      transaction.add(R.id.contact_selection_list_fragment_holder, contactsFragment);
      transaction.commit();
    } else {
      contactsFragment = (ContactSelectionListFragment) child;
    }

    initializeSearch();
  }

  private void initializeSearch() {
    contactFilterView.setOnFilterChangedListener(filter -> contactsFragment.setQueryFilter(filter));
  }

  @Override
  public boolean onBeforeContactSelected(@NonNull Optional<RecipientId> recipientId, @Nullable String number) {
    if (recipientId.isPresent()) {
      SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                     () -> Recipient.resolved(recipientId.get()),
                     this::createPaymentOrShowWarningDialog);
      return false;
    }
    return false;
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number) {}

  @Override
  public void onSelectionChanged() {
  }

  @Override
  public void onBeginScroll() {
    hideKeyboard();
  }

  private void hideKeyboard() {
    ViewUtil.hideKeyboard(requireContext(), toolbar);
    toolbar.clearFocus();
  }

  private void createPaymentOrShowWarningDialog(@NonNull Recipient recipient) {
    if (recipient.hasProfileKeyCredential()) {
      createPayment(recipient.getId());
    } else {
      showWarningDialog(recipient.getId());
    }
  }

  private void createPayment(@NonNull RecipientId recipientId) {
    hideKeyboard();
    Navigation.findNavController(requireView()).navigate(PaymentRecipientSelectionFragmentDirections.actionPaymentRecipientSelectionToCreatePayment(new PayeeParcelable(recipientId)));
  }

  private void showWarningDialog(@NonNull RecipientId recipientId) {
    CanNotSendPaymentDialog.show(requireContext(),
                                 () -> openConversation(recipientId));
  }

  private void openConversation(@NonNull RecipientId recipientId) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> DatabaseFactory.getThreadDatabase(requireContext()).getThreadIdIfExistsFor(recipientId),
                   threadId -> startActivity(ConversationIntents.createBuilder(requireContext(), recipientId, threadId).build()));
  }
}

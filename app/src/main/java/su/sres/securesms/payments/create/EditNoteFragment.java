package su.sres.securesms.payments.create;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import su.sres.securesms.LoggingFragment;
import su.sres.securesms.R;
import su.sres.securesms.components.emoji.EmojiEditText;
import su.sres.securesms.util.ViewUtil;

public class EditNoteFragment extends LoggingFragment {

  private CreatePaymentViewModel viewModel;
  private EmojiEditText          noteEditText;

  public EditNoteFragment() {
    super(R.layout.edit_note_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    viewModel = new ViewModelProvider(Navigation.findNavController(view).getViewModelStoreOwner(R.id.payments_create)).get(CreatePaymentViewModel.class);

    Toolbar toolbar = view.findViewById(R.id.edit_note_fragment_toolbar);
    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    noteEditText = view.findViewById(R.id.edit_note_fragment_edit_text);
    viewModel.getNote().observe(getViewLifecycleOwner(), note -> {
      noteEditText.setText(note);
      if (!TextUtils.isEmpty(note)) {
        noteEditText.setSelection(note.length());
      }
    });

    noteEditText.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        saveNote();
        return true;
      }
      return false;
    });

    View fab = view.findViewById(R.id.edit_note_fragment_fab);
    fab.setOnClickListener(v -> saveNote());

    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(noteEditText);
  }

  private void saveNote() {
    ViewUtil.hideKeyboard(requireView().getContext(), requireView());
    viewModel.setNote(noteEditText.getText() != null ? noteEditText.getText().toString() : null);
    Navigation.findNavController(requireView()).popBackStack();
  }
}

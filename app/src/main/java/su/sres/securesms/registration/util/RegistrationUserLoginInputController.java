package su.sres.securesms.registration.util;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;

import su.sres.securesms.components.LabeledEditText;

/**
 * Handle the logic and formatting of user login input for registration/change user login flows.
 */
public final class RegistrationUserLoginInputController {

  private final Context         context;
  private final LabeledEditText login;
  private final boolean         lastInput;
  private final Callbacks       callbacks;

  private boolean isUpdating = true;

  public RegistrationUserLoginInputController(@NonNull Context context,
                                              @NonNull LabeledEditText login,
                                              boolean lastInput,
                                              @NonNull Callbacks callbacks)
  {
    this.context   = context;
    this.login     = login;
    this.lastInput = lastInput;
    this.callbacks = callbacks;

    setUpUserLoginInput();
  }

  private void setUpUserLoginInput() {
    EditText userLoginInput = login.getInput();

    userLoginInput.addTextChangedListener(new LoginChangedListener());

    login.setOnFocusChangeListener((v, hasFocus) -> {
      if (hasFocus) {
        callbacks.onLoginFocused();
      }
    });

    userLoginInput.setImeOptions(lastInput ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
    userLoginInput.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_NEXT) {
        callbacks.onLoginInputNext(v);
        return true;
      } else if (actionId == EditorInfo.IME_ACTION_DONE) {
        callbacks.onLoginInputDone(v);
        return true;
      }
      return false;
    });
  }

  public void updateUserLogin(@NonNull String loginViewState) {
    isUpdating = true;
    this.login.setText(loginViewState);
    isUpdating = false;
  }

  private class LoginChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      String userLogin = s.toString();

      if (!isUpdating) {
        callbacks.setUserLogin(userLogin);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

  public interface Callbacks {
    void onLoginFocused();

    void onLoginInputNext(@NonNull View view);

    void onLoginInputDone(@NonNull View view);

    void setUserLogin(@NonNull String userLogin);
  }
}

package su.sres.securesms.reactions;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import su.sres.securesms.R;
import su.sres.securesms.megaphone.Megaphone;
import su.sres.securesms.megaphone.MegaphoneActionController;

public class ReactionsMegaphoneView extends FrameLayout {

    private View closeButton;

    public ReactionsMegaphoneView(Context context) {
        super(context);
        initialize(context);
    }

    public ReactionsMegaphoneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(@NonNull Context context) {
        inflate(context, R.layout.reactions_megaphone, this);

        this.closeButton = findViewById(R.id.reactions_megaphone_x);
    }

    public void present(@NonNull Megaphone megaphone, @NonNull MegaphoneActionController listener) {
        this.closeButton.setOnClickListener(v -> listener.onMegaphoneCompleted(megaphone.getEvent()));
    }
}
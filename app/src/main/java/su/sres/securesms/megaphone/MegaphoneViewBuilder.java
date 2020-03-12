package su.sres.securesms.megaphone;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.reactions.ReactionsMegaphoneView;

public class MegaphoneViewBuilder {

    public static @Nullable View build(@NonNull Context context,
                                       @NonNull Megaphone megaphone,
                                       @NonNull MegaphoneListener listener)
    {
        switch (megaphone.getStyle()) {
            case BASIC:
                return buildBasicMegaphone(context, megaphone, listener);
            case FULLSCREEN:
                return null;
            case REACTIONS:
                return buildReactionsMegaphone(context, megaphone, listener);
            default:
                throw new IllegalArgumentException("No view implemented for style!");
        }
    }

    private static @NonNull View buildBasicMegaphone(@NonNull Context context,
                                                     @NonNull Megaphone megaphone,
                                                     @NonNull MegaphoneListener listener)
    {
        BasicMegaphoneView view = new BasicMegaphoneView(context);
        view.present(megaphone, listener);
        return view;
    }

    private static @NonNull View buildReactionsMegaphone(@NonNull Context context,
                                                         @NonNull Megaphone megaphone,
                                                         @NonNull MegaphoneListener listener)
    {
        ReactionsMegaphoneView view = new ReactionsMegaphoneView(context);
        view.present(megaphone, listener);
        return view;
    }
}
package su.sres.securesms.megaphone;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sres.core.util.logging.Log;
import su.sres.securesms.R;
import su.sres.securesms.groups.ui.creategroup.CreateGroupActivity;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.profiles.manage.ManageProfileActivity;
import su.sres.securesms.wallpaper.ChatWallpaperActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the a fun rail of cards that educate the user about some actions they can take right after
 * they install the app.
 */
public class OnboardingMegaphoneView extends FrameLayout {

  private static final String TAG = Log.tag(OnboardingMegaphoneView.class);

  private RecyclerView cardList;

  public OnboardingMegaphoneView(Context context) {
    super(context);
    initialize(context);
  }

  public OnboardingMegaphoneView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context);
  }

  private void initialize(@NonNull Context context) {
    inflate(context, R.layout.onboarding_megaphone, this);

    this.cardList = findViewById(R.id.onboarding_megaphone_list);
  }

  public void present(@NonNull Megaphone megaphone, @NonNull MegaphoneActionController listener) {
    this.cardList.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    this.cardList.setAdapter(new CardAdapter(getContext(), listener));
  }

  private static class CardAdapter extends RecyclerView.Adapter<CardViewHolder> implements ActionClickListener {

    private static final int TYPE_GROUP      = 0;
    private static final int TYPE_APPEARANCE = 3;
    private static final int TYPE_ADD_PHOTO  = 4;

    private final Context                   context;
    private final MegaphoneActionController controller;
    private final List<Integer>             data;

    CardAdapter(@NonNull Context context, @NonNull MegaphoneActionController controller) {
      this.context    = context;
      this.controller = controller;
      this.data       = buildData(context);

      if (data.isEmpty()) {
        Log.i(TAG, "Nothing to show (constructor)! Considering megaphone completed.");
        controller.onMegaphoneCompleted(Megaphones.Event.ONBOARDING);
      }

      setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
      return data.get(position);
    }

    @Override
    public long getItemId(int position) {
      return data.get(position);
    }

    @Override
    public @NonNull CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.onboarding_megaphone_list_item, parent, false);
      switch (viewType) {
        case TYPE_GROUP:
          return new GroupCardViewHolder(view);
        case TYPE_APPEARANCE:
          return new AppearanceCardViewHolder(view);
        case TYPE_ADD_PHOTO:
          return new AddPhotoCardViewHolder(view);
        default:
          throw new IllegalStateException("Invalid viewType! " + viewType);
      }
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
      holder.bind(this, controller);
    }

    @Override
    public int getItemCount() {
      return data.size();
    }

    @Override
    public void onClick() {
      data.clear();
      data.addAll(buildData(context));
      if (data.isEmpty()) {
        Log.i(TAG, "Nothing to show! Considering megaphone completed.");
        controller.onMegaphoneCompleted(Megaphones.Event.ONBOARDING);
      }
      notifyDataSetChanged();
    }

    private static List<Integer> buildData(@NonNull Context context) {
      List<Integer> data = new ArrayList<>();

      if (SignalStore.onboarding().shouldShowNewGroup()) {
        data.add(TYPE_GROUP);
      }

      if (SignalStore.onboarding().shouldShowAddPhoto() && !SignalStore.misc().hasEverHadAnAvatar()) {
        data.add(TYPE_ADD_PHOTO);
      }

      if (SignalStore.onboarding().shouldShowAppearance()) {
        data.add(TYPE_APPEARANCE);
      }

      return data;
    }
  }

  private interface ActionClickListener {
    void onClick();
  }

  private static abstract class CardViewHolder extends RecyclerView.ViewHolder {
    private final ImageView image;
    private final TextView  actionButton;
    private final View      closeButton;

    public CardViewHolder(@NonNull View itemView) {
      super(itemView);
      this.image        = itemView.findViewById(R.id.onboarding_megaphone_item_image);
      this.actionButton = itemView.findViewById(R.id.onboarding_megaphone_item_button);
      this.closeButton  = itemView.findViewById(R.id.onboarding_megaphone_item_close);
    }

    public void bind(@NonNull ActionClickListener listener, @NonNull MegaphoneActionController controller) {
      image.setImageResource(getImageRes());
      actionButton.setText(getButtonStringRes());
      actionButton.setOnClickListener(v -> {
        onActionClicked(controller);
        listener.onClick();
      });
      closeButton.setOnClickListener(v -> {
        onCloseClicked();
        listener.onClick();
      });
    }

    abstract @StringRes int getButtonStringRes();

    abstract @DrawableRes int getImageRes();

    abstract void onActionClicked(@NonNull MegaphoneActionController controller);

    abstract void onCloseClicked();
  }

  private static class GroupCardViewHolder extends CardViewHolder {

    public GroupCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_new_group;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_megaphone_start_group;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(CreateGroupActivity.newIntent(controller.getMegaphoneActivity()));
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowNewGroup(false);
    }
  }

  private static class AppearanceCardViewHolder extends CardViewHolder {

    public AppearanceCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_appearance;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_signal_appearance;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(ChatWallpaperActivity.createIntent(controller.getMegaphoneActivity()));
      SignalStore.onboarding().setShowAppearance(false);
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowAppearance(false);
    }
  }

  private static class AddPhotoCardViewHolder extends CardViewHolder {

    public AddPhotoCardViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    int getButtonStringRes() {
      return R.string.Megaphones_add_photo;
    }

    @Override
    int getImageRes() {
      return R.drawable.ic_signal_add_photo;
    }

    @Override
    void onActionClicked(@NonNull MegaphoneActionController controller) {
      controller.onMegaphoneNavigationRequested(ManageProfileActivity.getIntentForAvatarEdit(controller.getMegaphoneActivity()));
      SignalStore.onboarding().setShowAddPhoto(false);
    }

    @Override
    void onCloseClicked() {
      SignalStore.onboarding().setShowAddPhoto(false);
    }

  }

}

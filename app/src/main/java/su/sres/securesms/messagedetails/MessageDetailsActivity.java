package su.sres.securesms.messagedetails;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import su.sres.securesms.PassphraseRequiredActivity;
import su.sres.securesms.R;
import su.sres.securesms.components.recyclerview.ToolbarShadowAnimationHelper;
import su.sres.securesms.conversation.colors.Colorizer;
import su.sres.securesms.conversation.colors.RecyclerViewColorizer;
import su.sres.securesms.conversation.ui.error.SafetyNumberChangeDialog;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.giph.mp4.GiphyMp4PlaybackController;
import su.sres.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder;
import su.sres.securesms.giph.mp4.GiphyMp4ProjectionRecycler;
import su.sres.securesms.messagedetails.MessageDetailsAdapter.MessageDetailsViewState;
import su.sres.securesms.messagedetails.MessageDetailsViewModel.Factory;
import su.sres.securesms.mms.GlideApp;
import su.sres.securesms.mms.GlideRequests;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.DynamicNoActionBarTheme;
import su.sres.securesms.util.DynamicTheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MessageDetailsActivity extends PassphraseRequiredActivity {

  private static final String MESSAGE_ID_EXTRA = "message_id";
  private static final String THREAD_ID_EXTRA  = "thread_id";
  private static final String TYPE_EXTRA       = "type";
  private static final String RECIPIENT_EXTRA  = "recipient_id";

  private GlideRequests           glideRequests;
  private MessageDetailsViewModel viewModel;
  private MessageDetailsAdapter   adapter;
  private Colorizer             colorizer;
  private RecyclerViewColorizer recyclerViewColorizer;

  private DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static @NonNull Intent getIntentForMessageDetails(@NonNull Context context, @NonNull MessageRecord message, @NonNull RecipientId recipientId, long threadId) {
    Intent intent = new Intent(context, MessageDetailsActivity.class);
    intent.putExtra(MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(THREAD_ID_EXTRA, threadId);
    intent.putExtra(TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    intent.putExtra(RECIPIENT_EXTRA, recipientId);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.message_details_activity);

    glideRequests = GlideApp.with(this);

    initializeList();
    initializeViewModel();
    initializeActionBar();
    initializeVideoPlayer();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    adapter.resumeMessageExpirationTimer();
  }

  @Override
  protected void onPause() {
    super.onPause();
    adapter.pauseMessageExpirationTimer();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void initializeList() {
    RecyclerView  list          = findViewById(R.id.message_details_list);
    View          toolbarShadow = findViewById(R.id.toolbar_shadow);

    colorizer             = new Colorizer();
    adapter               = new MessageDetailsAdapter(this, glideRequests, colorizer, this::onErrorClicked);
    recyclerViewColorizer = new RecyclerViewColorizer(list);

    list.setAdapter(adapter);
    list.setItemAnimator(null);
    list.addOnScrollListener(new ToolbarShadowAnimationHelper(toolbarShadow));
  }

  private void initializeViewModel() {
    final RecipientId recipientId = getIntent().getParcelableExtra(RECIPIENT_EXTRA);
    final String      type        = getIntent().getStringExtra(TYPE_EXTRA);
    final Long        messageId   = getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1);
    final Factory     factory     = new Factory(recipientId, type, messageId);

    viewModel = ViewModelProviders.of(this, factory).get(MessageDetailsViewModel.class);
    viewModel.getMessageDetails().observe(this, details -> {
      if (details == null) {
        finish();
      } else {
        adapter.submitList(convertToRows(details));
      }
    });

    viewModel.getRecipient().observe(this, recipient -> recyclerViewColorizer.setChatColors(recipient.getChatColors()));
  }

  private void initializeVideoPlayer() {
    FrameLayout                          videoContainer = findViewById(R.id.video_container);
    RecyclerView                         recyclerView   = findViewById(R.id.message_details_list);
    List<GiphyMp4ProjectionPlayerHolder> holders        = GiphyMp4ProjectionPlayerHolder.injectVideoViews(this, getLifecycle(), videoContainer, 1);
    GiphyMp4ProjectionRecycler           callback       = new GiphyMp4ProjectionRecycler(holders);

    GiphyMp4PlaybackController.attach(recyclerView, callback, 1);
  }

  private void initializeActionBar() {
    setSupportActionBar(findViewById(R.id.toolbar));
    requireSupportActionBar().setDisplayHomeAsUpEnabled(true);
    requireSupportActionBar().setTitle(R.string.AndroidManifest__message_details);
  }

  private List<MessageDetailsViewState<?>> convertToRows(MessageDetails details) {
    List<MessageDetailsViewState<?>> list = new ArrayList<>();

    list.add(new MessageDetailsViewState<>(details.getConversationMessage(), MessageDetailsViewState.MESSAGE_HEADER));

    if (details.getConversationMessage().getMessageRecord().isOutgoing()) {
      addRecipients(list, RecipientHeader.NOT_SENT, details.getNotSent());
      addRecipients(list, RecipientHeader.VIEWED, details.getViewed());
      addRecipients(list, RecipientHeader.READ, details.getRead());
      addRecipients(list, RecipientHeader.DELIVERED, details.getDelivered());
      addRecipients(list, RecipientHeader.SENT_TO, details.getSent());
      addRecipients(list, RecipientHeader.PENDING, details.getPending());
    } else {
      addRecipients(list, RecipientHeader.SENT_FROM, details.getSent());
    }

    return list;
  }

  private boolean addRecipients(List<MessageDetailsViewState<?>> list, RecipientHeader header, Collection<RecipientDeliveryStatus> recipients) {
    if (recipients.isEmpty()) {
      return false;
    }

    list.add(new MessageDetailsViewState<>(header, MessageDetailsViewState.RECIPIENT_HEADER));
    for (RecipientDeliveryStatus status : recipients) {
      list.add(new MessageDetailsViewState<>(status, MessageDetailsViewState.RECIPIENT));
    }
    return true;
  }

  private void onErrorClicked(@NonNull MessageRecord messageRecord) {
    SafetyNumberChangeDialog.show(this, messageRecord);
  }
}
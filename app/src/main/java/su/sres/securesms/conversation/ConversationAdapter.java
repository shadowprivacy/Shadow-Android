/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.securesms.conversation;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import su.sres.core.util.ThreadUtil;
import su.sres.paging.PagingController;
import su.sres.securesms.BindableConversationItem;
import su.sres.securesms.R;
import su.sres.core.util.logging.Log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.MediaItem;

import su.sres.securesms.conversation.colors.Colorizable;
import su.sres.securesms.conversation.colors.Colorizer;
import su.sres.securesms.conversation.multiselect.MultiselectPart;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.giph.mp4.GiphyMp4Playable;
import su.sres.securesms.giph.mp4.GiphyMp4PlaybackPolicyEnforcer;
import su.sres.securesms.mms.GlideRequests;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.CachedInflater;
import su.sres.securesms.util.DateUtils;
import su.sres.securesms.util.MessageRecordUtil;
import su.sres.securesms.util.Projection;
import su.sres.securesms.util.ProjectionList;
import su.sres.securesms.util.StickyHeaderDecoration;
import su.sres.securesms.util.ThemeUtil;
import su.sres.securesms.util.ViewUtil;

import org.whispersystems.libsignal.util.guava.Optional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter that renders a conversation.
 * <p>
 * Important spacial thing to keep in mind: The adapter is intended to be shown on a reversed layout
 * manager, so position 0 is at the bottom of the screen. That's why the "header" is at the bottom,
 * the "footer" is at the top, and we refer to the "next" record as having a lower index.
 */
public class ConversationAdapter
    extends ListAdapter<ConversationMessage, RecyclerView.ViewHolder>
    implements StickyHeaderDecoration.StickyHeaderAdapter<ConversationAdapter.StickyHeaderViewHolder>
{

  private static final String TAG = Log.tag(ConversationAdapter.class);

  public static final int HEADER_TYPE_POPOVER_DATE = 1;
  public static final int HEADER_TYPE_INLINE_DATE  = 2;
  public static final int HEADER_TYPE_LAST_SEEN    = 3;

  private static final int MESSAGE_TYPE_OUTGOING_MULTIMEDIA = 0;
  private static final int MESSAGE_TYPE_OUTGOING_TEXT       = 1;
  private static final int MESSAGE_TYPE_INCOMING_MULTIMEDIA = 2;
  private static final int MESSAGE_TYPE_INCOMING_TEXT       = 3;
  private static final int MESSAGE_TYPE_UPDATE              = 4;
  private static final int MESSAGE_TYPE_HEADER              = 5;
  public  static final int MESSAGE_TYPE_FOOTER              = 6;
  private static final int MESSAGE_TYPE_PLACEHOLDER         = 7;

  private static final int PAYLOAD_TIMESTAMP   = 0;
  public  static final int PAYLOAD_NAME_COLORS = 1;

  private final ItemClickListener clickListener;
  private final Context           context;
  private final LifecycleOwner    lifecycleOwner;
  private final GlideRequests     glideRequests;
  private final Locale            locale;
  private final Recipient         recipient;

  private final Set<MultiselectPart>         selected;
  private final List<ConversationMessage>    fastRecords;
  private final Set<Long>                    releasedFastRecords;
  private final Calendar                     calendar;
  private final MessageDigest                digest;

  private String              searchQuery;
  private ConversationMessage recordToPulse;
  private View                typingView;
  private View                footerView;
  private PagingController    pagingController;
  private boolean             hasWallpaper;
  private boolean             isMessageRequestAccepted;
  private ConversationMessage inlineContent;
  private Colorizer           colorizer;
  private boolean             isTypingViewEnabled;

  ConversationAdapter(@NonNull Context context,
                      @NonNull LifecycleOwner lifecycleOwner,
                      @NonNull GlideRequests glideRequests,
                      @NonNull Locale locale,
                      @Nullable ItemClickListener clickListener,
                      @NonNull Recipient recipient,
                      @NonNull Colorizer colorizer)
  {
    super(new DiffUtil.ItemCallback<ConversationMessage>() {
      @Override
      public boolean areItemsTheSame(@NonNull ConversationMessage oldItem, @NonNull ConversationMessage newItem) {
        return oldItem.getMessageRecord().getId() == newItem.getMessageRecord().getId();
      }

      @Override
      public boolean areContentsTheSame(@NonNull ConversationMessage oldItem, @NonNull ConversationMessage newItem) {
        return false;
      }
    });

    this.lifecycleOwner = lifecycleOwner;
    this.context        = context;

    this.glideRequests                = glideRequests;
    this.locale                       = locale;
    this.clickListener                = clickListener;
    this.recipient                    = recipient;
    this.selected                     = new HashSet<>();
    this.fastRecords                  = new ArrayList<>();
    this.releasedFastRecords          = new HashSet<>();
    this.calendar                     = Calendar.getInstance();
    this.digest                       = getMessageDigestOrThrow();
    this.hasWallpaper                 = recipient.hasWallpaper();
    this.isMessageRequestAccepted     = true;
    this.colorizer                    = colorizer;
  }

  @Override
  public int getItemViewType(int position) {
    if (isTypingViewEnabled() && position == 0) {
      return MESSAGE_TYPE_HEADER;
    }
    if (hasFooter() && position == getItemCount() - 1) {
      return MESSAGE_TYPE_FOOTER;
    }

    ConversationMessage conversationMessage = getItem(position);
    MessageRecord       messageRecord       = (conversationMessage != null) ? conversationMessage.getMessageRecord() : null;

    if (messageRecord == null) {
      return MESSAGE_TYPE_PLACEHOLDER;
    } else if (messageRecord.isUpdate()) {
      return MESSAGE_TYPE_UPDATE;
    } else if (messageRecord.isOutgoing()) {
      return MessageRecordUtil.isTextOnly(messageRecord, context) ? MESSAGE_TYPE_OUTGOING_TEXT : MESSAGE_TYPE_OUTGOING_MULTIMEDIA;
    } else {
      return MessageRecordUtil.isTextOnly(messageRecord, context) ? MESSAGE_TYPE_INCOMING_TEXT : MESSAGE_TYPE_INCOMING_MULTIMEDIA;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_INCOMING_TEXT:
      case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
      case MESSAGE_TYPE_OUTGOING_TEXT:
      case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
      case MESSAGE_TYPE_UPDATE:

        View itemView = CachedInflater.from(parent.getContext()).inflate(getLayoutForViewType(viewType), parent, false);
        BindableConversationItem bindable = (BindableConversationItem) itemView;

        itemView.setOnClickListener((v) -> {
          if (clickListener != null) {
            clickListener.onItemClick(bindable.getMultiselectPartForLatestTouch());
          }
        });

        itemView.setOnLongClickListener((v) -> {
          if (clickListener != null) {
            clickListener.onItemLongClick(itemView, bindable.getMultiselectPartForLatestTouch());
          }
          return true;
        });

        bindable.setEventListener(clickListener);

        return new ConversationViewHolder(itemView);
      case MESSAGE_TYPE_PLACEHOLDER:
        View v = new FrameLayout(parent.getContext());
        v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
        return new PlaceholderViewHolder(v);
      case MESSAGE_TYPE_HEADER:
        return new HeaderViewHolder(CachedInflater.from(parent.getContext()).inflate(R.layout.cursor_adapter_header_footer_view, parent, false));
      case MESSAGE_TYPE_FOOTER:
        return new FooterViewHolder(CachedInflater.from(parent.getContext()).inflate(R.layout.cursor_adapter_header_footer_view, parent, false));
      default:
        throw new IllegalStateException("Cannot create viewholder for type: " + viewType);
    }
  }

  private boolean containsValidPayload(@NonNull List<Object> payloads) {
    return payloads.contains(PAYLOAD_TIMESTAMP) || payloads.contains(PAYLOAD_NAME_COLORS);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (containsValidPayload(payloads)) {
      switch (getItemViewType(position)) {
        case MESSAGE_TYPE_INCOMING_TEXT:
        case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
        case MESSAGE_TYPE_OUTGOING_TEXT:
        case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
        case MESSAGE_TYPE_UPDATE:
          ConversationViewHolder conversationViewHolder = (ConversationViewHolder) holder;
          if (payloads.contains(PAYLOAD_TIMESTAMP)) {
            conversationViewHolder.getBindable().updateTimestamps();
          }

          if (payloads.contains(PAYLOAD_NAME_COLORS)) {
            conversationViewHolder.getBindable().updateContactNameColor();
          }
        default:
          return;
      }
    } else {
      super.onBindViewHolder(holder, position, payloads);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    switch (getItemViewType(position)) {
      case MESSAGE_TYPE_INCOMING_TEXT:
      case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
      case MESSAGE_TYPE_OUTGOING_TEXT:
      case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
      case MESSAGE_TYPE_UPDATE:
        ConversationViewHolder conversationViewHolder = (ConversationViewHolder) holder;
        ConversationMessage conversationMessage = Objects.requireNonNull(getItem(position));
        int adapterPosition = holder.getAdapterPosition();

        ConversationMessage previousMessage = adapterPosition < getItemCount() - 1 && !isFooterPosition(adapterPosition + 1) ? getItem(adapterPosition + 1) : null;
        ConversationMessage nextMessage = adapterPosition > 0 && !isHeaderPosition(adapterPosition - 1) ? getItem(adapterPosition - 1) : null;

        conversationViewHolder.getBindable().bind(lifecycleOwner,
                                                  conversationMessage,
                                                  Optional.fromNullable(previousMessage != null ? previousMessage.getMessageRecord() : null),
                                                  Optional.fromNullable(nextMessage != null ? nextMessage.getMessageRecord() : null),
                                                  glideRequests,
                                                  locale,
                                                  selected,
                                                  recipient,
                                                  searchQuery,
                                                  conversationMessage == recordToPulse,
                                                  hasWallpaper,
                                                  isMessageRequestAccepted,
                                                  conversationMessage == inlineContent,
                                                  colorizer);

        if (conversationMessage == recordToPulse) {
          recordToPulse = null;
        }
        break;
      case MESSAGE_TYPE_HEADER:
        ((HeaderViewHolder) holder).bind(typingView);
        break;
      case MESSAGE_TYPE_FOOTER:
        ((HeaderFooterViewHolder) holder).bind(footerView);
        break;
    }
  }

  @Override
  public int getItemCount() {
    boolean hasFooter = footerView != null;
    return super.getItemCount() + fastRecords.size() + (isTypingViewEnabled ? 1 : 0) + (hasFooter ? 1 : 0);
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ConversationViewHolder) {
      ((ConversationViewHolder) holder).getBindable().unbind();
    }
  }

  @Override
  public long getHeaderId(int position) {
    if (isHeaderPosition(position)) return -1;
    if (isFooterPosition(position)) return -1;
    if (position >= getItemCount()) return -1;
    if (position < 0) return -1;

    ConversationMessage conversationMessage = getItem(position);

    if (conversationMessage == null) return -1;

    calendar.setTimeInMillis(conversationMessage.getMessageRecord().getDateReceived());
    return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
  }

  @Override
  public StickyHeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position, int type) {
    return new StickyHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_item_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(StickyHeaderViewHolder viewHolder, int position, int type) {
    Context             context             = viewHolder.itemView.getContext();
    ConversationMessage conversationMessage = Objects.requireNonNull(getItem(position));
    viewHolder.setText(DateUtils.getConversationDateHeaderString(viewHolder.itemView.getContext(), locale, conversationMessage.getMessageRecord().getDateReceived()));

    if (type == HEADER_TYPE_POPOVER_DATE) {
      if (hasWallpaper) {
        viewHolder.setBackgroundRes(R.drawable.wallpaper_bubble_background_8);
      } else {
        viewHolder.setBackgroundRes(R.drawable.sticky_date_header_background);
      }
    } else if (type == HEADER_TYPE_INLINE_DATE) {
      if (hasWallpaper) {
        viewHolder.setBackgroundRes(R.drawable.wallpaper_bubble_background_8);
      } else {
        viewHolder.clearBackground();
      }
    }

    if (hasWallpaper && ThemeUtil.isDarkTheme(context)) {
      viewHolder.setTextColor(ContextCompat.getColor(context, R.color.core_grey_15));
    } else {
      viewHolder.setTextColor(ContextCompat.getColor(context, R.color.signal_text_secondary));
    }
  }

  public @Nullable ConversationMessage getItem(int position) {
    position = isTypingViewEnabled() ? position - 1 : position;

    if (position == -1) {
      return null;
    } else if (position < fastRecords.size()) {
      return fastRecords.get(position);
    } else {
      int correctedPosition = position - fastRecords.size();
      if (pagingController != null) {
        pagingController.onDataNeededAroundIndex(correctedPosition);
      }

      if (correctedPosition < getItemCount()) {
        return super.getItem(correctedPosition);
      } else {
        Log.d(TAG, "Could not access corrected position " + correctedPosition + " as it is out of bounds.");
        return null;
      }
    }
  }

  public void submitList(@Nullable List<ConversationMessage> pagedList) {
    cleanFastRecords();
    super.submitList(pagedList);
  }

  public void setPagingController(@Nullable PagingController pagingController) {
    this.pagingController = pagingController;
  }

  public boolean isForRecipientId(@NonNull RecipientId recipientId) {
    return recipient.getId().equals(recipientId);
  }

  void onBindLastSeenViewHolder(StickyHeaderViewHolder viewHolder, int position) {
    viewHolder.setText(viewHolder.itemView.getContext().getResources().getQuantityString(R.plurals.ConversationAdapter_n_unread_messages, (position + 1), (position + 1)));

    if (hasWallpaper) {
      viewHolder.setBackgroundRes(R.drawable.wallpaper_bubble_background_8);
      viewHolder.setDividerColor(viewHolder.itemView.getResources().getColor(R.color.transparent_black_80));
    } else {
      viewHolder.clearBackground();
      viewHolder.setDividerColor(viewHolder.itemView.getResources().getColor(R.color.core_grey_45));
    }
  }

  boolean hasNoConversationMessages() {
    return getItemCount() + fastRecords.size() == 0;
  }

  /**
   * The presence of a header may throw off the position you'd like to jump to. This will return
   * an adjusted message position based on adapter state.
   */
  @MainThread
  int getAdapterPositionForMessagePosition(int messagePosition) {
    return isTypingViewEnabled() ? messagePosition + 1 : messagePosition;
  }

  /**
   * Finds the received timestamp for the item at the requested adapter position. Will return 0 if
   * the position doesn't refer to an incoming message.
   */
  @MainThread
  long getReceivedTimestamp(int position) {
    if (isHeaderPosition(position)) return 0;
    if (isFooterPosition(position)) return 0;
    if (position >= getItemCount()) return 0;
    if (position < 0) return 0;

    ConversationMessage conversationMessage = getItem(position);

    if (conversationMessage == null || conversationMessage.getMessageRecord().isOutgoing()) {
      return 0;
    } else {
      return conversationMessage.getMessageRecord().getDateReceived();
    }
  }

  /**
   * Sets the view the appears at the top of the list (because the list is reversed).
   */
  void setFooterView(@Nullable View view) {
    boolean hadFooter = hasFooter();

    this.footerView = view;

    if (view == null && hadFooter) {
      notifyItemRemoved(getItemCount());
    } else if (view != null && hadFooter) {
      notifyItemChanged(getItemCount() - 1);
    } else if (view != null) {
      notifyItemInserted(getItemCount() - 1);
    }
  }

  /**
   * Sets the view that appears at the bottom of the list (because the list is reversed).
   */
  void setTypingView(@NonNull View view) {
    this.typingView = view;
  }

  void setTypingViewEnabled(boolean isTypingViewEnabled) {
    if (typingView == null && isTypingViewEnabled) {
      throw new IllegalStateException("Must set header before enabling.");
    }

    if (this.isTypingViewEnabled && !isTypingViewEnabled) {
      this.isTypingViewEnabled = false;
      notifyItemRemoved(0);
    } else if (this.isTypingViewEnabled) {
      notifyItemChanged(0);
    } else if (isTypingViewEnabled) {
      this.isTypingViewEnabled = true;
      notifyItemInserted(0);
    }
  }

  /**
   * Momentarily highlights a mention at the requested position.
   */
  void pulseAtPosition(int position) {
    if (position >= 0 && position < getItemCount()) {
      int correctedPosition = isHeaderPosition(position) ? position + 1 : position;

      recordToPulse = getItem(correctedPosition);
      notifyItemChanged(correctedPosition);
    }
  }

  /**
   * Conversation search query updated. Allows rendering of text highlighting.
   */
  void onSearchQueryUpdated(String query) {
    if (!Objects.equals(query, this.searchQuery)) {
      this.searchQuery = query;
      notifyDataSetChanged();
    }
  }

  /**
   * Lets the adapter know that the wallpaper state has changed.
   *
   * @return True if the internal wallpaper state changed, otherwise false.
   */
  boolean onHasWallpaperChanged(boolean hasWallpaper) {
    if (this.hasWallpaper != hasWallpaper) {
      Log.d(TAG, "Resetting adapter due to wallpaper change.");
      this.hasWallpaper = hasWallpaper;
      notifyDataSetChanged();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Adds a record to a memory cache to allow it to be rendered immediately, as opposed to waiting
   * for a database change.
   */
  @MainThread
  void addFastRecord(ConversationMessage conversationMessage) {
    fastRecords.add(0, conversationMessage);
    notifyDataSetChanged();
  }

  /**
   * Marks a record as no-longer-needed. Will be removed from the adapter the next time the database
   * changes.
   */
  @AnyThread
  void releaseFastRecord(long id) {
    synchronized (releasedFastRecords) {
      releasedFastRecords.add(id);
    }
  }

  /**
   * Returns set of records that are selected in multi-select mode.
   */
  public Set<MultiselectPart> getSelectedItems() {
    return new HashSet<>(selected);
  }

  public void removeFromSelection(@NonNull Set<MultiselectPart> parts) {
    selected.removeAll(parts);
  }

  /**
   * Clears all selected records from multi-select mode.
   */
  void clearSelection() {
    selected.clear();
  }

  /**
   * Toggles the selected state of a record in multi-select mode.
   */
  void toggleSelection(MultiselectPart multiselectPart) {
    if (selected.contains(multiselectPart)) {
      selected.remove(multiselectPart);
    } else {
      selected.add(multiselectPart);
    }
  }

  /**
   * Provided a pool, this will initialize it with view counts that make sense.
   */
  @MainThread
  static void initializePool(@NonNull RecyclerView.RecycledViewPool pool) {
    pool.setMaxRecycledViews(MESSAGE_TYPE_INCOMING_TEXT, 25);
    pool.setMaxRecycledViews(MESSAGE_TYPE_INCOMING_MULTIMEDIA, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_OUTGOING_TEXT, 25);
    pool.setMaxRecycledViews(MESSAGE_TYPE_OUTGOING_MULTIMEDIA, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_PLACEHOLDER, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_HEADER, 1);
    pool.setMaxRecycledViews(MESSAGE_TYPE_FOOTER, 1);
    pool.setMaxRecycledViews(MESSAGE_TYPE_UPDATE, 5);
  }

  @MainThread
  private void cleanFastRecords() {
    ThreadUtil.assertMainThread();

    synchronized (releasedFastRecords) {
      Iterator<ConversationMessage> messageIterator = fastRecords.iterator();
      while (messageIterator.hasNext()) {
        long id = messageIterator.next().getMessageRecord().getId();
        if (releasedFastRecords.contains(id)) {
          messageIterator.remove();
          releasedFastRecords.remove(id);
        }
      }
    }
  }

  public boolean isTypingViewEnabled() {
    return isTypingViewEnabled;
  }

  public boolean hasFooter() {
    return footerView != null;
  }

  private boolean isHeaderPosition(int position) {
    return isTypingViewEnabled() && position == 0;
  }

  private boolean isFooterPosition(int position) {
    return hasFooter() && position == (getItemCount() - 1);
  }

  private static @LayoutRes int getLayoutForViewType(int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_OUTGOING_TEXT:
        return R.layout.conversation_item_sent_text_only;
      case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
        return R.layout.conversation_item_sent_multimedia;
      case MESSAGE_TYPE_INCOMING_TEXT:
        return R.layout.conversation_item_received_text_only;
      case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
        return R.layout.conversation_item_received_multimedia;
      case MESSAGE_TYPE_UPDATE:
        return R.layout.conversation_item_update;
      default:
        throw new IllegalArgumentException("Unknown type!");
    }
  }

  private static MessageDigest getMessageDigestOrThrow() {
    try {
      return MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public @Nullable ConversationMessage getLastVisibleConversationMessage(int position) {
    try {
      return getItem(position - ((hasFooter() && position == getItemCount() - 1) ? 1 : 0));
    } catch (IndexOutOfBoundsException e) {
      Log.w(TAG, "Race condition changed size of conversation", e);
      return null;
    }
  }

  public void setMessageRequestAccepted(boolean messageRequestAccepted) {
    if (this.isMessageRequestAccepted != messageRequestAccepted) {
      this.isMessageRequestAccepted = messageRequestAccepted;
      notifyDataSetChanged();
    }
  }

  public void playInlineContent(@Nullable ConversationMessage conversationMessage) {
    if (this.inlineContent != conversationMessage) {
      this.inlineContent = conversationMessage;
      notifyDataSetChanged();
    }
  }

  public void updateTimestamps() {
    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_TIMESTAMP);
  }

  final static class ConversationViewHolder extends RecyclerView.ViewHolder implements GiphyMp4Playable, Colorizable {
    public ConversationViewHolder(final @NonNull View itemView) {
      super(itemView);
    }

    public BindableConversationItem getBindable() {
      return (BindableConversationItem) itemView;
    }

    @Override
    public void showProjectionArea() {
      getBindable().showProjectionArea();
    }

    @Override
    public void hideProjectionArea() {
      getBindable().hideProjectionArea();
    }

    @Override
    public @Nullable MediaItem getMediaItem() {
      return getBindable().getMediaItem();
    }

    @Override
    public @Nullable GiphyMp4PlaybackPolicyEnforcer getPlaybackPolicyEnforcer() {
      return getBindable().getPlaybackPolicyEnforcer();
    }

    @Override
    public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
      return getBindable().getGiphyMp4PlayableProjection(recyclerView);
    }

    @Override
    public boolean canPlayContent() {
      return getBindable().canPlayContent();
    }

    @Override
    public @NonNull ProjectionList getColorizerProjections(@NonNull ViewGroup coordinateRoot) {
      return getBindable().getColorizerProjections(coordinateRoot);
    }
  }

  static class StickyHeaderViewHolder extends RecyclerView.ViewHolder {
    TextView textView;
    View     divider;

    StickyHeaderViewHolder(View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.text);
      divider  = itemView.findViewById(R.id.last_seen_divider);
    }

    StickyHeaderViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }

    public void setText(CharSequence text) {
      textView.setText(text);
    }

    public void setTextColor(@ColorInt int color) {
      textView.setTextColor(color);
    }

    public void setBackgroundRes(@DrawableRes int resId) {
      textView.setBackgroundResource(resId);
    }

    public void setDividerColor(@ColorInt int color) {
      if (divider != null) {
        divider.setBackgroundColor(color);
      }
    }

    public void clearBackground() {
      textView.setBackground(null);
    }
  }

  public abstract static class HeaderFooterViewHolder extends RecyclerView.ViewHolder {

    private ViewGroup container;

    HeaderFooterViewHolder(@NonNull View itemView) {
      super(itemView);
      this.container = (ViewGroup) itemView;
    }

    void bind(@Nullable View view) {
      unbind();

      if (view != null) {
        removeViewFromParent(view);
        container.addView(view);
      }
    }

    void unbind() {
      container.removeAllViews();
    }

    private void removeViewFromParent(@NonNull View view) {
      if (view.getParent() != null) {
        ((ViewGroup) view.getParent()).removeView(view);
      }
    }
  }

  public static class FooterViewHolder extends HeaderFooterViewHolder {
    FooterViewHolder(@NonNull View itemView) { super(itemView); }
  }

  public static class HeaderViewHolder extends HeaderFooterViewHolder {
    HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  private static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
    PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  interface ItemClickListener extends BindableConversationItem.EventListener {
    void onItemClick(MultiselectPart item);

    void onItemLongClick(View itemView, MultiselectPart item);
  }
}


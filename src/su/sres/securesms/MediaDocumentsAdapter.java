package su.sres.securesms;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import su.sres.securesms.MediaDocumentsAdapter.HeaderViewHolder;
import su.sres.securesms.MediaDocumentsAdapter.ViewHolder;
import su.sres.securesms.components.DocumentView;
import su.sres.securesms.database.CursorRecyclerViewAdapter;
import su.sres.securesms.database.MediaDatabase;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.DocumentSlide;
import su.sres.securesms.mms.PartAuthority;
import su.sres.securesms.mms.Slide;
import su.sres.securesms.util.DateUtils;
import su.sres.securesms.util.MediaUtil;
import su.sres.securesms.util.StickyHeaderDecoration;
import su.sres.securesms.util.Util;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static com.codewaves.stickyheadergrid.StickyHeaderGridLayoutManager.TAG;

public class MediaDocumentsAdapter extends CursorRecyclerViewAdapter<ViewHolder> implements StickyHeaderDecoration.StickyHeaderAdapter<HeaderViewHolder> {

  private final Calendar     calendar;
  private final Locale       locale;

  MediaDocumentsAdapter(Context context, Cursor cursor, Locale locale) {
    super(context, cursor);

    this.calendar     = Calendar.getInstance();
    this.locale       = locale;
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.media_overview_document_item, parent, false));
  }

  @Override
  public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    MediaDatabase.MediaRecord mediaRecord = MediaDatabase.MediaRecord.from(getContext(), cursor);
    Slide                     slide       = MediaUtil.getSlideForAttachment(getContext(), mediaRecord.getAttachment());

    if (slide != null && slide.hasDocument()) {
      viewHolder.documentView.setDocument((DocumentSlide)slide, false);
      viewHolder.date.setText(DateUtils.getRelativeDate(getContext(), locale, mediaRecord.getDate()));
      viewHolder.documentView.setVisibility(View.VISIBLE);
      viewHolder.date.setVisibility(View.VISIBLE);
      viewHolder.documentView.setOnClickListener(view -> {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(PartAuthority.getAttachmentPublicUri(slide.getUri()), slide.getContentType());
        try {
          getContext().startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
          Log.w(TAG, "No activity existed to view the media.");
          Toast.makeText(getContext(), R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
        }
      });
    } else {
      viewHolder.documentView.setVisibility(View.GONE);
      viewHolder.date.setVisibility(View.GONE);
    }
  }

  @Override
  public long getHeaderId(int position) {
    if (!isActiveCursor())          return -1;
    if (isHeaderPosition(position)) return -1;
    if (isFooterPosition(position)) return -1;
    if (position >= getItemCount()) return -1;
    if (position < 0)               return -1;

    Cursor                    cursor      = getCursorAtPositionOrThrow(position);
    MediaDatabase.MediaRecord mediaRecord = MediaDatabase.MediaRecord.from(getContext(), cursor);

    calendar.setTime(new Date(mediaRecord.getDate()));
    return Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR));
  }

  @Override
  public HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.media_overview_document_item_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int position) {
    Cursor                    cursor      = getCursorAtPositionOrThrow(position);
    MediaDatabase.MediaRecord mediaRecord = MediaDatabase.MediaRecord.from(getContext(), cursor);
    viewHolder.textView.setText(DateUtils.getRelativeDate(getContext(), locale, mediaRecord.getDate()));
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {

    private final DocumentView documentView;
    private final TextView date;

    public ViewHolder(View itemView) {
      super(itemView);
      this.documentView = itemView.findViewById(R.id.document_view);
      this.date         = itemView.findViewById(R.id.date);
    }
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {

    private final TextView textView;

    HeaderViewHolder(View itemView) {
      super(itemView);
      this.textView = itemView.findViewById(R.id.text);
    }
  }

}

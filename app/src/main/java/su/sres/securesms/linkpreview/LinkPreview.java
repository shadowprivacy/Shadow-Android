package su.sres.securesms.linkpreview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.attachments.AttachmentId;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.util.JsonUtils;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

public class LinkPreview {

    @JsonProperty
    private final String       url;

    @JsonProperty
    private final String       title;

    @JsonProperty
    private final String       description;

    @JsonProperty
    private final long         date;

    @JsonProperty
    private final AttachmentId attachmentId;

    @JsonIgnore
    private final Optional<Attachment> thumbnail;

    public LinkPreview(@NonNull String url, @NonNull String title, @NonNull String description, long date, @NonNull DatabaseAttachment thumbnail) {
        this.url          = url;
        this.title        = title;
        this.description  = description;
        this.date         = date;
        this.thumbnail    = Optional.of(thumbnail);
        this.attachmentId = thumbnail.getAttachmentId();
    }

    public LinkPreview(@NonNull String url, @NonNull String title, @NonNull String description, long date, @NonNull Optional<Attachment> thumbnail) {
        this.url          = url;
        this.title        = title;
        this.description  = description;
        this.date         = date;
        this.thumbnail    = thumbnail;
        this.attachmentId = null;
    }

    public LinkPreview(@JsonProperty("url")          @NonNull  String url,
                       @JsonProperty("title")        @NonNull  String title,
                       @JsonProperty("description")  @Nullable String description,
                       @JsonProperty("date")                   long date,
                       @JsonProperty("attachmentId") @Nullable AttachmentId attachmentId)
    {
        this.url          = url;
        this.title        = title;
        this.description  = Optional.fromNullable(description).or("");
        this.date         = date;
        this.attachmentId = attachmentId;
        this.thumbnail    = Optional.absent();
    }

    public @NonNull String getUrl() {
        return url;
    }

    public @NonNull String getTitle() {
        return title;
    }

    public @NonNull String getDescription() {
        if (description.equals(title)) {
            return "";
        }

        return description;
    }

    public long getDate() {
        return date;
    }

    public @NonNull Optional<Attachment> getThumbnail() {
        return thumbnail;
    }

    public @Nullable AttachmentId getAttachmentId() {
        return attachmentId;
    }

    public @NonNull String serialize() throws IOException {
        return JsonUtils.toJson(this);
    }

    public static @NonNull LinkPreview deserialize(@NonNull String serialized) throws IOException {
        return JsonUtils.fromJson(serialized, LinkPreview.class);
    }
}
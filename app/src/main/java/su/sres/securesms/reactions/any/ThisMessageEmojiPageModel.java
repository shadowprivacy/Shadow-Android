package su.sres.securesms.reactions.any;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import su.sres.securesms.R;
import su.sres.securesms.components.emoji.Emoji;
import su.sres.securesms.components.emoji.EmojiPageModel;

import java.util.List;

/**
 * Contains the Emojis that have been used in reactions for a given message.
 */
class ThisMessageEmojiPageModel implements EmojiPageModel {

    private final List<String> emoji;

    ThisMessageEmojiPageModel(@NonNull List<String> emoji) {
        this.emoji = emoji;
    }

    @Override
    public int getIconAttr() {
        return R.attr.emoji_category_recent;
    }

    @Override
    public @NonNull List<String> getEmoji() {
        return emoji;
    }

    @Override
    public @NonNull List<Emoji> getDisplayEmoji() {
        return Stream.of(getEmoji()).map(Emoji::new).toList();
    }

    @Override
    public boolean hasSpriteMap() {
        return false;
    }

    @Override
    public @Nullable String getSprite() {
        return null;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
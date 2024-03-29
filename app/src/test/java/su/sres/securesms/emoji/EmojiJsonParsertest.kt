package su.sres.securesms.emoji

import android.app.Application
import android.net.Uri
import com.fasterxml.jackson.core.JsonParseException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import su.sres.securesms.components.emoji.CompositeEmojiPageModel
import su.sres.securesms.components.emoji.Emoji
import su.sres.securesms.components.emoji.EmojiPageModel
import su.sres.securesms.components.emoji.StaticEmojiPageModel

private const val INVALID_JSON = "{{}"
private const val EMPTY_JSON = "{}"
private const val SAMPLE_JSON_WITHOUT_OBSOLETE = """
  {
     "emoji": {
       "Places": [["d83cdf0d"], ["0003", "0004", "0005"]],
       "Foods": [["0001"], ["0002", "0003", "0004"]]
     },
     "metrics": {
       "raw_height": 64,
       "raw_width": 64,
       "per_row": 16
     },
     "densities": [ "xhdpi" ],
     "format": "png"
  }
"""

private const val SAMPLE_JSON_WITH_OBSOLETE = """
  {
     "emoji": {
       "Places_1": [["0002"], ["0003", "0004", "0005"]],
       "Places_2": [["0003"], ["0008", "0009", "0000"]],
       "Foods": [["0001"], ["0002", "0003", "0004"]]
     },
     "obsolete": [
       {"obsoleted": "0012", "replace_with": "0023"}
     ],
     "metrics": {
       "raw_height": 64,
       "raw_width": 64,
       "per_row": 16
     },
     "densities": [ "xhdpi" ],
     "format": "png"
  }
"""

private val SAMPLE_JSON_WITHOUT_OBSOLETE_EXPECTED = listOf(
  StaticEmojiPageModel(EmojiCategory.FOODS, listOf(Emoji("\u0001"), Emoji("\u0002", "\u0003", "\u0004")), Uri.parse("file:///Foods")),
  StaticEmojiPageModel(EmojiCategory.PLACES, listOf(Emoji("\ud83c\udf0d"), Emoji("\u0003", "\u0004", "\u0005")), Uri.parse("file:///Places"))
)

private val SAMPLE_JSON_WITH_OBSOLETE_EXPECTED_DISPLAY = listOf(
  StaticEmojiPageModel(EmojiCategory.FOODS, listOf(Emoji("\u0001"), Emoji("\u0002", "\u0003", "\u0004")), Uri.parse("file:///Foods")),
  CompositeEmojiPageModel(
    EmojiCategory.PLACES.icon,
    listOf(
      StaticEmojiPageModel(EmojiCategory.PLACES, listOf(Emoji("\u0002"), Emoji("\u0003", "\u0004", "\u0005")), Uri.parse("file:///Places_1")),
      StaticEmojiPageModel(EmojiCategory.PLACES, listOf(Emoji("\u0003"), Emoji("\u0008", "\u0009", "\u0000")), Uri.parse("file:///Places_2"))
    )
  )
)

private val SAMPLE_JSON_WITH_OBSOLETE_EXPECTED_DATA = listOf(
  StaticEmojiPageModel(EmojiCategory.FOODS, listOf(Emoji("\u0001"), Emoji("\u0002", "\u0003", "\u0004")), Uri.parse("file:///Foods")),
  StaticEmojiPageModel(EmojiCategory.PLACES, listOf(Emoji("\u0002"), Emoji("\u0003", "\u0004", "\u0005")), Uri.parse("file:///Places_1")),
  StaticEmojiPageModel(EmojiCategory.PLACES, listOf(Emoji("\u0003"), Emoji("\u0008", "\u0009", "\u0000")), Uri.parse("file:///Places_2"))
)

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EmojiJsonParserTest {

  @Test(expected = NullPointerException::class)
  fun `Given empty json, when I parse, then I expect a NullPointerException`() {
    val result = EmojiJsonParser.parse(EMPTY_JSON.byteInputStream(), this::uriFactory)

    result.getOrThrow()
  }

  @Test(expected = JsonParseException::class)
  fun `Given invalid json, when I parse, then I expect a JsonParseException`() {
    val result = EmojiJsonParser.parse(INVALID_JSON.byteInputStream(), this::uriFactory)

    result.getOrThrow()
  }

  @Test
  fun `Given sample without obselete, when I parse, then I expect source without obsolete`() {
    val result: ParsedEmojiData = EmojiJsonParser.parse(SAMPLE_JSON_WITHOUT_OBSOLETE.byteInputStream(), this::uriFactory).getOrThrow()

    Assert.assertTrue(result.obsolete.isEmpty())
    Assert.assertTrue(result.displayPages == result.dataPages)
    Assert.assertEquals(SAMPLE_JSON_WITHOUT_OBSOLETE_EXPECTED.size, result.dataPages.size)

    result.dataPages.zip(SAMPLE_JSON_WITHOUT_OBSOLETE_EXPECTED).forEach { (actual, expected) ->
      Assert.assertTrue(actual.isSameAs(expected))
    }
  }

  @Test
  fun `Given sample with obsolete, when I parse, then I expect source with obsolete`() {
    val result: ParsedEmojiData = EmojiJsonParser.parse(SAMPLE_JSON_WITH_OBSOLETE.byteInputStream(), this::uriFactory).getOrThrow()

    Assert.assertTrue(result.obsolete.size == 1)
    Assert.assertEquals("\u0012", result.obsolete[0].obsolete)
    Assert.assertEquals("\u0023", result.obsolete[0].replaceWith)
    Assert.assertFalse(result.displayPages == result.dataPages)
    Assert.assertEquals(SAMPLE_JSON_WITH_OBSOLETE_EXPECTED_DISPLAY.size, result.displayPages.size)

    result.displayPages.zip(SAMPLE_JSON_WITH_OBSOLETE_EXPECTED_DISPLAY).forEach { (actual, expected) ->
      Assert.assertTrue(actual.isSameAs(expected))
    }

    Assert.assertEquals(SAMPLE_JSON_WITH_OBSOLETE_EXPECTED_DATA.size, result.dataPages.size)

    result.dataPages.zip(SAMPLE_JSON_WITH_OBSOLETE_EXPECTED_DATA).forEach { (actual, expected) ->
      Assert.assertTrue(actual.isSameAs(expected))
    }

    Assert.assertEquals(result.densities, listOf("xhdpi"))
    Assert.assertEquals(result.format, "png")
  }

  private fun uriFactory(sprite: String, format: String) = Uri.parse("file:///$sprite")

  private fun EmojiPageModel.isSameAs(other: EmojiPageModel) =
    this.javaClass == other.javaClass &&
      this.emoji == other.emoji &&
      this.iconAttr == other.iconAttr &&
      this.spriteUri == other.spriteUri
}
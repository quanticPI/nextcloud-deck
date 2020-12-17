package it.niedermann.android.markdown.markwon;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.Map;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.simple.ext.SimpleExtPlugin;
import io.noties.markwon.syntax.Prism4jTheme;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.annotations.PrismBundle;
import it.niedermann.android.markdown.markwon.model.EListType;
import it.niedermann.android.markdown.markwon.plugins.NextcloudMentionsPlugin;
import it.niedermann.android.markdown.markwon.plugins.ThemePlugin;

@RestrictTo(value = RestrictTo.Scope.LIBRARY)
@PrismBundle(
        include = {
                "c", "clike", "clojure", "cpp", "csharp", "css", "dart", "git", "go", "groovy", "java", "javascript", "json",
                "kotlin", "latex", "makefile", "markdown", "markup", "python", "scala", "sql", "swift", "yaml"
        },
        grammarLocatorClassName = ".MarkwonGrammarLocator"
)
public class MarkwonMarkdownUtil {

    private MarkwonMarkdownUtil() {
        // Util class
    }

    public static Markwon.Builder initMarkwonEditor(@NonNull Context context) {
        return Markwon.builder(context)
                .usePlugin(ThemePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(SimpleExtPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(MarkwonInlineParserPlugin.create());
    }

    public static Markwon.Builder initMarkwonViewer(@NonNull Context context) {
        final Prism4j prism4j = new Prism4j(new MarkwonGrammarLocator());
        final Prism4jTheme prism4jTheme = Prism4jThemeDefault.create();
        return initMarkwonEditor(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(GlideImagesPlugin.create(context))
                .usePlugin(SyntaxHighlightPlugin.create(prism4j, prism4jTheme));
    }

    public static Markwon.Builder initMarkwonViewer(@NonNull Context context, @NonNull Map<String, String> mentions) {
        return initMarkwonViewer(context)
                .usePlugin(NextcloudMentionsPlugin.create(context, mentions));
    }

    public static int getStartOfLine(@NonNull CharSequence s, int cursorPosition) {
        int startOfLine = cursorPosition;
        while (startOfLine > 0 && s.charAt(startOfLine - 1) != '\n') {
            startOfLine--;
        }
        return startOfLine;
    }

    public static int getEndOfLine(@NonNull CharSequence s, int cursorPosition) {
        int nextLinebreak = s.toString().indexOf('\n', cursorPosition);
        if (nextLinebreak > -1) {
            return nextLinebreak;
        }
        return cursorPosition;
    }

    public static String getListItemIfIsEmpty(@NonNull String line) {
        for (EListType listType : EListType.values()) {
            if (line.equals(listType.checkboxUncheckedWithTrailingSpace)) {
                return listType.checkboxUncheckedWithTrailingSpace;
            } else if (line.equals(listType.listSymbolWithTrailingSpace)) {
                return listType.listSymbolWithTrailingSpace;
            }
        }
        return null;
    }

    public static boolean lineStartsWithCheckbox(@NonNull String line) {
        for (EListType listType : EListType.values()) {
            if (lineStartsWithCheckbox(line, listType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean lineStartsWithCheckbox(@NonNull String line, @NonNull EListType listType) {
        return line.startsWith(listType.checkboxUnchecked) || line.startsWith(listType.checkboxChecked);
    }

    public static boolean lineStartsWithList(@NonNull String line, @NonNull EListType listType) {
        return line.startsWith(listType.listSymbol);
    }

    /**
     * Modifies the {@param builder} and adds the given {@param punctuation} from
     * {@param selectionStart} to {@param selectionEnd} or removes the {@param punctuation} in case
     * it already is around the selected part.
     *
     * @return the new cursor position
     */
    public static int togglePunctuation(@NonNull StringBuilder builder, int selectionStart, int selectionEnd, @NonNull String punctuation) {
        switch (punctuation) {
            case "**":
            case "__":
            case "*":
            case "_":
            case "~~": {
                final boolean selectionIsSurroundedByPunctuation = selectionIsSurroundedByPunctuation(builder.toString(), selectionStart, selectionEnd, punctuation);
                if (selectionIsSurroundedByPunctuation) {
                    removeSurroundingPunctuation(builder, selectionStart, selectionEnd, punctuation);
                } else {
                    builder.insert(selectionEnd, punctuation);
                    builder.insert(selectionStart, punctuation);
                }
                return selectionIsSurroundedByPunctuation ? selectionEnd - punctuation.length() : selectionEnd + punctuation.length() * 2;
            }
            default:
                throw new UnsupportedOperationException("This kind of punctuation is not yet supported: " + punctuation);
        }
    }

    /**
     * Inserts a link into the given {@param builder} from {@param selectionStart} to {@param selectionEnd} and uses the {@param clipboardUrl} if available.
     *
     * @return the new cursor position
     */
    public static int insertLink(@NonNull StringBuilder builder, int selectionStart, int selectionEnd, @Nullable String clipboardUrl) {
        final CharSequence text = builder.toString();
        final boolean textToFormatIsLink = TextUtils.indexOf(text.subSequence(selectionStart, selectionEnd), "http") == 0;
        if (textToFormatIsLink) {
            if (clipboardUrl == null) {
                builder.insert(selectionEnd, ")");
                builder.insert(selectionStart, "[](");
            } else {
                builder.insert(selectionEnd, "](" + clipboardUrl + ")");
                builder.insert(selectionStart, "[");
                selectionEnd += clipboardUrl.length();
            }
        } else {
            if (clipboardUrl == null) {
                builder.insert(selectionEnd, "]()");
            } else {
                builder.insert(selectionEnd, "](" + clipboardUrl + ")");
                selectionEnd += clipboardUrl.length();
            }
            builder.insert(selectionStart, "[");
        }
        return textToFormatIsLink && clipboardUrl == null
                ? selectionStart + 1
                : selectionEnd + 3;
    }

    /**
     * @return whether or not the selection of {@param text} from {@param start} to {@param end} is
     * surrounded or not by the given {@param punctuation}.
     */
    private static boolean selectionIsSurroundedByPunctuation(@NonNull CharSequence text, int start, int end, @NonNull String punctuation) {
        if (text.length() < end + punctuation.length()) {
            return false;
        }
        if (start - punctuation.length() < 0 || end + punctuation.length() > text.length()) {
            return false;
        }
        return punctuation.contentEquals(text.subSequence(start - punctuation.length(), start))
                && punctuation.contentEquals(text.subSequence(end, end + punctuation.length()));
    }

    /**
     * Mutates the given {@param builder} and removes the text range from {@param start} to
     * {@param end} and its surrounding {@param punctuation}.
     * Doesn't make any assumptions about the text lengths and will throw a
     * {@link StringIndexOutOfBoundsException} if {@param start}, {@param end} or the
     * {@param punctuation} is out of range of the given {@param builder}.
     */
    private static void removeSurroundingPunctuation(@NonNull StringBuilder builder, int start, int end, @NonNull String punctuation) throws StringIndexOutOfBoundsException {
        builder.delete(end, end + punctuation.length());
        builder.delete(start - punctuation.length(), start);
    }
}

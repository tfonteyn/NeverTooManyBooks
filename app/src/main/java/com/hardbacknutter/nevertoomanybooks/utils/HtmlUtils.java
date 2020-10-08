/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HtmlUtils {

    private HtmlUtils() {
    }

    /**
     * Linkify partial HTML. Linkify methods remove all spans before building links,
     * this method preserves them.
     * <p>
     * See:
     * <a href="http://stackoverflow.com/questions/14538113">
     * using-linkify-addlinks-combine-with-html-fromhtml</a>
     *
     * <pre>
     *     {@code
     *          view.setText(HtmlUtils.linkify(body));
     *          view.setMovementMethod(LinkMovementMethod.getInstance());
     *     }
     * </pre>
     *
     * @param html Partial HTML
     *
     * @return Spannable with all links
     */
    @NonNull
    public static Spannable linkify(@NonNull final String html) {
        // Get the spannable HTML; single linefeed between things like LI elements.
        final Spanned text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);

        // Save the span details for later restoration
        final URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

        // Build an empty spannable and add the links
        final Spannable buffer = new SpannableString(text);
        // Only do web & email links. Most likely all we'll ever need.
        Linkify.addLinks(buffer, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);

        // Add back the HTML spannable's
        for (URLSpan span : currentSpans) {
            buffer.setSpan(span, text.getSpanStart(span), text.getSpanEnd(span), 0);
        }
        return buffer;
    }

    /**
     * Construct a multi-line list using html.
     *
     * @param collection collection
     * @param formatter  to use on each element,
     * @param <E>        type of elements
     *
     * @return formatted list, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String asList(@NonNull final Collection<E> collection,
                                    @NonNull final Function<E, String> formatter) {
        // sanity check
        if (collection.isEmpty()) {
            return "";
        }

        return collection.stream()
                         .map(formatter)
                         .map(s -> "<li>" + s + "</li>")
                         .collect(Collectors.joining("", "<ul>", "</ul>"));
    }
}

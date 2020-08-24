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

import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import androidx.annotation.NonNull;

/**
 * LinkifyUtils.
 * <p>
 * Linkify partial HTML. Linkify methods remove all spans before building links,
 * this method preserves them.
 * <p>
 * See:
 * <a href="http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml">stackoverflow</a>
 *
 * <pre>
 *     {@code
 *          view.setText(LinkifyUtils.fromHtml(body));
 *          view.setMovementMethod(LinkMovementMethod.getInstance());
 *     }
 * </pre>
 */
public final class LinkifyUtils {

    /** Only do web & email links. Most likely all we'll ever need. */
    private static final int LINKIFY_MASK = Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES;

    private LinkifyUtils() {
    }

    /**
     * Linkify partial HTML.
     *
     * @param html Partial HTML
     *
     * @return Spannable with all links
     */
    @NonNull
    public static Spannable fromHtml(@NonNull final String html) {
        // Get the spannable HTML
        final Spanned text;
        if (Build.VERSION.SDK_INT >= 24) {
            // single linefeed between things like LI elements.
            text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        } else {
            text = Html.fromHtml(html);
        }

        // Save the span details for later restoration
        final URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

        // Build an empty spannable and add the links
        final Spannable buffer = new SpannableString(text);
        Linkify.addLinks(buffer, LINKIFY_MASK);

        // Add back the HTML spannable's
        for (URLSpan span : currentSpans) {
            buffer.setSpan(span, text.getSpanStart(span), text.getSpanEnd(span), 0);
        }
        return buffer;
    }
}

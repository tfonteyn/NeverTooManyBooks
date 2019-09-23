/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

/**
 * LinkifyUtils.
 * <pre>
 *     {@code
 *          view.setText(LinkifyUtils.html(body));
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
     * Linkify partial HTML. Linkify methods remove all spans before building links,
     * this method preserves them.
     * <p>
     * See:
     * <a href="http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml">
     * http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml</a>
     *
     * @param html Partial HTML
     *
     * @return Spannable with all links
     */
    @NonNull
    public static Spannable html(@NonNull final String html) {
        // Get the spannable HTML
        Spanned text = Html.fromHtml(html);
        // Save the span details for later restoration
        URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

        // Build an empty spannable then add the links
        SpannableString buffer = new SpannableString(text);
        Linkify.addLinks(buffer, LINKIFY_MASK);

        // Add back the HTML spannable's
        for (URLSpan span : currentSpans) {
            int end = text.getSpanEnd(span);
            int start = text.getSpanStart(span);
            buffer.setSpan(span, start, end, 0);
        }
        return buffer;
    }
}

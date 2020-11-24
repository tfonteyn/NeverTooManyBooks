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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * FieldFormatter for HTML fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> with the caveat that 'enableLinks'
 *      is the same value</li>
 * </ul>
 *
 * @param <T> type of Field value.
 */
public class HtmlFormatter<T>
        implements FieldFormatter<T> {

    /** Whether to make links clickable. */
    private final boolean mEnableLinks;

    /**
     * Constructor.
     *
     * @param enableLinks {@code true} to enable links.
     *                    Ignored if the View has an onClickListener
     */
    public HtmlFormatter(final boolean enableLinks) {
        mEnableLinks = enableLinks;
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
     *          view.setText(HtmlFormatter.linkify(body));
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
        for (final URLSpan span : currentSpans) {
            buffer.setSpan(span, text.getSpanStart(span), text.getSpanEnd(span), 0);
        }
        return buffer;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final T rawValue) {
        return rawValue != null ? String.valueOf(rawValue) : "";
    }

    @Override
    public void apply(@Nullable final T rawValue,
                      @NonNull final TextView view) {

        view.setText(linkify(format(view.getContext(), rawValue)));

        if (mEnableLinks && !view.hasOnClickListeners()) {
            view.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
}

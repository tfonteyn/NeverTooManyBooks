/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Px;

import java.util.regex.Pattern;

/**
 * Accepts an html string formatted with {@code <p>} paragraphs containing numbered steps
 * and formats it for displaying inside a single TextView.
 * <p>
 * Example:
 * <pre>
 *     {@code
 *     Header with info.
 *     <br>
 *     <p>1. step</p>
 *     <p>text</p>
 *     <p>2. step</p>
 *     <p>text</p>
 *     <p>more text</p>
 *     <p>3. step</p>
 *     <p>text</p>
 *     }
 * </pre>
 */
public class OrderedTextListFormatter {

    /** Match any unicode digit sequence followed by a dot, or standard digits. */
    private static final Pattern LIST_DIGIT_PATTERN =
            Pattern.compile("^(\\d+|\\p{N}+)\\..*");

    @Px
    private final int firstLineIndentPx;
    @Px
    private final int restLinesMargin;

    public OrderedTextListFormatter(@NonNull final Context context,
                                    @Dimension(unit = Dimension.DP) final int firstIndentInDp,
                                    @Dimension(unit = Dimension.DP) final int restIndentInDp) {
        final DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

        firstLineIndentPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, firstIndentInDp, displayMetrics);

        final int hangingIndentPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, restIndentInDp, displayMetrics);
        restLinesMargin = firstLineIndentPx + hangingIndentPx;
    }

    @NonNull
    public CharSequence format(@NonNull final String raw) {
        // weblate inserts '\n'. Instead of trying to prevent that or keep editing...
        // .. we simply strip them out here BEFORE parsing the html.
        final String cleanHtml = raw.replace("\n", "");

        final Spanned htmlSpanned = Html.fromHtml(cleanHtml, Html.FROM_HTML_MODE_COMPACT);
        final SpannableStringBuilder builder = new SpannableStringBuilder(htmlSpanned);

        // Find paragraph boundaries and add hanging indent to numbered lines
        final String textStr = builder.toString();
        final String[] paragraphs = textStr.split("\n");
        int start = 0;

        boolean inHeader = true;
        for (final String paragraph : paragraphs) {
            final int end = start + paragraph.length();

            // Check if paragraph starts with a list digit
            if (LIST_DIGIT_PATTERN.matcher(paragraph.trim()).matches()) {
                // leaving the header section.
                inHeader = false;
                builder.setSpan(
                        new LeadingMarginSpan.Standard(firstLineIndentPx, restLinesMargin),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (inHeader) {
                // Initially we are in the header.
                builder.setSpan(
                        new LeadingMarginSpan.Standard(firstLineIndentPx, firstLineIndentPx),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // We're no longer in the header, these are
                // continuation lines for numbered paragraphs
                builder.setSpan(
                        new LeadingMarginSpan.Standard(restLinesMargin, restLinesMargin),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // skip \n
            start = end + 1;
        }

        return builder;
    }
}

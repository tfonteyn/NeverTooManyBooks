/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Formats a list of {@link T}s.
 * Each item gets a chevron next to it indicate it's clickable.
 * Lines are spaced with a plain '\n'.
 * <p>
 * This formatter provides the visual formatting only.
 * Actual 'click' functionality must be implemented elsewhere.
 */
@SuppressWarnings("WeakerAccess")
public class ClickableListFormatter<T>
        implements FieldFormatter<List<T>> {

    @NonNull
    private final Function<T, String> textSupplier;

    @NonNull
    private final Drawable icon;
    private final boolean isRTL;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param textSupplier provides the raw text to format
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public ClickableListFormatter(@NonNull final Context context,
                                  @NonNull final Function<T, String> textSupplier) {
        this.textSupplier = textSupplier;

        isRTL = context.getResources().getConfiguration()
                       .getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        //noinspection DataFlowIssue
        icon = context.getDrawable(isRTL ? R.drawable.chevron_left_24px
                                         : R.drawable.chevron_right_24px);
        //noinspection DataFlowIssue
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
    }

    @NonNull
    @Override
    public CharSequence format(@NonNull final Context context,
                               @Nullable final List<T> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < rawValue.size(); i++) {
            final T entity = rawValue.get(i);
            final ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);

            final Spanned text = Html.fromHtml(textSupplier.apply(entity),
                                               Html.FROM_HTML_MODE_COMPACT);
            if (isRTL) {
                // Text first, then icon
                builder.append(text);
                final int start = builder.length();
                // placeholder text, will be replaced with imageSpan
                builder.append(" ");
                builder.setSpan(imageSpan, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Icon first, then text
                final int start = builder.length();
                builder.append(" ");
                builder.setSpan(imageSpan, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(text);
            }
            // add spacing so tapping is easier (but not after the last row)
            if (i + 1 != rawValue.size()) {
                builder.append("\n");
            }

        }
        return builder;
    }
}

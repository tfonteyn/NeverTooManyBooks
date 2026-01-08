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

package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Formats a list of {@link T}s.
 * Each item gets a chevron next to it indicate it's clickable.
 * Lines are spaced with a plain '\n'.
 * <p>
 * This formatter provides the visual formatting only.
 * Actual 'click' functionality must be implemented elsewhere.
 * The static method {@link #getIndex(TextView, MotionEvent)} can be used to compute
 * the index of the clicked item.
 */
@SuppressWarnings("WeakerAccess")
public class ClickableListFormatter<T>
        implements FieldFormatter<List<T>> {

    private static final char LINE_SEPARATOR = '\n';
    private static final String ZERO_WIDTH_SPACE = "\u200B";

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
        icon = context.getDrawable(R.drawable.chevron);
        //noinspection DataFlowIssue
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
    }

    /**
     * Given the event (from a click) on the view, calculate which line was clicked.
     *
     * @param view  on which the click happened
     * @param event the click
     *
     * @return the index of the clicked line
     */
    @NonNull
    public static Optional<Integer> getIndex(@NonNull final TextView view,
                                             @NonNull final MotionEvent event) {
        final android.text.Layout layout = view.getLayout();
        if (layout == null) {
            return Optional.empty();
        }

        final CharSequence text = view.getText();
        final int y = (int) event.getY() + view.getScrollY();
        final int line = layout.getLineForVertical(y);
        final int offset = layout.getOffsetForHorizontal(line, event.getX());

        // Find start of the line
        int start = offset;
        while (start > 0 && text.charAt(start - 1) != LINE_SEPARATOR) {
            start--;
        }

        // Find end of the line
        int end = offset;
        while (end < text.length() && text.charAt(end) != LINE_SEPARATOR) {
            end++;
        }

        // Compute index by counting the number of lineSeparator before 'start'
        int index = 0;
        for (int i = 0; i < start; i++) {
            if (text.charAt(i) == LINE_SEPARATOR) {
                index++;
            }
        }
        return Optional.of(index);
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
            final ImageSpan imageSpan = new CenteredImageSpan(icon);

            final Spanned text = Html.fromHtml(textSupplier.apply(entity),
                                               Html.FROM_HTML_MODE_COMPACT);
            if (isRTL) {
                // Text first, then icon
                builder.append(text).append(" ");
                final int start = builder.length();
                // placeholder text, will be replaced with imageSpan
                builder.append(ZERO_WIDTH_SPACE);
                builder.setSpan(imageSpan, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Icon first, then text
                final int start = builder.length();
                builder.append(ZERO_WIDTH_SPACE);
                builder.setSpan(imageSpan, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(" ").append(text);
            }
            // add spacing so tapping is easier (but not after the last row)
            // This is also essential for the detecting the correct line clicked!
            if (i + 1 != rawValue.size()) {
                builder.append(LINE_SEPARATOR);
            }

        }
        return builder;
    }

    private static class CenteredImageSpan
            extends ImageSpan {

        CenteredImageSpan(@NonNull final Drawable drawable) {
            super(drawable);
        }

        @Override
        public void draw(@NonNull final Canvas canvas,
                         @NonNull final CharSequence text,
                         final int start,
                         final int end,
                         final float x,
                         final int top,
                         final int y,
                         final int bottom,
                         @NonNull final Paint paint) {
            final Drawable b = getDrawable();
            canvas.save();

            // 1. Get font metrics to find the centre of the text
            final Paint.FontMetricsInt fm = paint.getFontMetricsInt();

            // 2. Calculate the centre of the text line.
            // 'y' is the baseline. (y + fm.descent) is the bottom, (y + fm.ascent) is the top.
            final int textCenter = y + (fm.descent + fm.ascent) / 2;

            // 3. Calculate the translation needed to put the centre of the icon at the text centre
            final int transY = textCenter - (b.getBounds().height() / 2);

            canvas.translate(x, transY);
            b.draw(canvas);
            canvas.restore();
        }
    }
}

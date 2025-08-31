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

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * A formatter which uses {@link Entity#getLabel(Context, Details, Style)}
 * to display a list of {@link Entity}s.
 *
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> if they use the same Details/delimiter</li>
 * </ul>
 *
 * @param <T> type of Entity (== Field) value.
 */
public class ListFormatter<T extends Entity>
        extends HtmlFormatter<List<T>> {

    private static final String DEFAULT_DELIMITER = "; ";

    @NonNull
    private final Details details;

    @NonNull
    private final Style style;

    @NonNull
    private String delimiter = DEFAULT_DELIMITER;

    /**
     * Constructor.
     *
     * @param details how much details to show
     * @param style   to use
     */
    public ListFormatter(@NonNull final Details details,
                         @NonNull final Style style) {
        this.details = details;
        this.style = style;
    }

    /**
     * Set the delimiter to use. Only used by {@link Details#Normal}; ignored otherwise
     *
     * @param delimiter to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ListFormatter<T> setDelimiter(@NonNull final String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final List<T> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }

        final Details listDetails;
        final Details itemDetails;
        if (details == Details.AutoSelect) {
            final int orientation = context.getResources().getConfiguration().orientation;
            final ScreenSize screenSize = ScreenSize.compute(context);
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // In landscape
                if (screenSize.getWidth().isAtLeast(ScreenSize.Value.Medium)) {
                    listDetails = Details.Normal;
                    itemDetails = Details.Full;
                } else {
                    // Compact
                    listDetails = Details.Short;
                    itemDetails = Details.Short;
                }
            } else {
                // In portrait
                switch (screenSize.getHeight()) {
                    case Compact: {
                        listDetails = Details.Short;
                        itemDetails = Details.Short;
                        break;
                    }
                    case Medium: {
                        listDetails = Details.Normal;
                        itemDetails = Details.Normal;
                        break;
                    }
                    default: {
                        // ScreenSize.Value.Expanded and up
                        listDetails = Details.Full;
                        itemDetails = Details.Full;
                        break;
                    }
                }
            }

        } else {
            listDetails = details;
            itemDetails = details;
        }

        switch (listDetails) {
            case Full: {
                return rawValue.stream()
                               .map(entity -> entity.getLabel(context, itemDetails, style))
                               .map(s -> "<li>" + s + "</li>")
                               .collect(Collectors.joining("", "<ul>", "</ul>"));
            }
            case Normal: {
                return rawValue.stream()
                               .map(entity -> entity.getLabel(context, itemDetails, style))
                               .collect(Collectors.joining(delimiter));
            }
            case Short: {
                if (rawValue.size() > 1) {
                    // special case, we only show the first element using Details.Normal,
                    // and the "and_others" suffix
                    return context.getString(R.string.and_others_plus,
                                             rawValue.get(0)
                                                     .getLabel(context, Details.Normal, style),
                                             rawValue.size() - 1);
                } else {
                    return rawValue.get(0).getLabel(context, itemDetails, style);
                }
            }

            case AutoSelect:
            default:
                throw new IllegalArgumentException("listDetails=" + listDetails);
        }
    }
}

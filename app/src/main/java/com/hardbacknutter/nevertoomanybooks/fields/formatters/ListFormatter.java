/*
 * @Copyright 2018-2022 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

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
    private final String delimiter;
    @Nullable
    private final Style style;

    /**
     * Constructor.
     *
     * @param details how much details to show
     * @param style   (optional) to use
     */
    public ListFormatter(@NonNull final Details details,
                         @Nullable final Style style) {
        this(details, DEFAULT_DELIMITER, style);
    }

    /**
     * Constructor.
     *
     * @param details   how much details to show
     * @param delimiter to use if details is {@link Details#Normal}
     * @param style     (optional) to use
     */
    @SuppressWarnings("WeakerAccess")
    public ListFormatter(@NonNull final Details details,
                         @NonNull final String delimiter,
                         @Nullable final Style style) {
        this.details = details;
        this.delimiter = delimiter;
        this.style = style;
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
        if (details == Details.Auto) {
            //TODO: simplify the decision logic...
            final WindowSizeClass wsc;
            final int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // In landscape
                wsc = WindowSizeClass.getWidth(context);
                switch (wsc) {
                    case EXPANDED:
                    case MEDIUM:
                        listDetails = Details.Normal;
                        itemDetails = Details.Full;
                        break;

                    case COMPACT:
                        listDetails = Details.Short;
                        itemDetails = Details.Short;
                        break;

                    default:
                        throw new IllegalArgumentException("WindowSizeClass=" + wsc);
                }
            } else {
                // In portrait
                wsc = WindowSizeClass.getHeight(context);
                switch (wsc) {
                    case EXPANDED:
                        listDetails = Details.Full;
                        itemDetails = Details.Full;
                        break;

                    case MEDIUM:
                        listDetails = Details.Normal;
                        itemDetails = Details.Normal;
                        break;

                    case COMPACT:
                        listDetails = Details.Short;
                        itemDetails = Details.Short;
                        break;

                    default:
                        throw new IllegalArgumentException("WindowSizeClass=" + wsc);
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
                               .map(s -> "<li>" + escapeBedetheque(s) + "</li>")
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

            case Auto:
            default:
                throw new IllegalArgumentException("listDetails=" + listDetails);
        }
    }

    // FIXME: quick hack to escape the 'generic' authors from bedetheque
    private String escapeBedetheque(@NonNull final String s) {
        if (s.startsWith("<") && s.endsWith(">")) {
            return "&lt;" + s.substring(1, s.length() - 1) + "&gt;";
        }
        return s;
    }
}

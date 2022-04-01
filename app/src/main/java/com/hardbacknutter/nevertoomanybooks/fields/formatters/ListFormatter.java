/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> if they use the same details/delimiter</li>
 * </ul>
 */
public class ListFormatter<T extends Entity>
        extends HtmlFormatter<List<T>> {

    @NonNull
    private final Details mDetails;

    @NonNull
    private final String mDelimiter;

    /**
     * Constructor.
     * <p>
     * Use {@link Details#Normal} with {@code "; "} as delimiter.
     */
    public ListFormatter() {
        this(Details.Normal, "; ");
    }

    /**
     * Constructor.
     *
     * @param details how much details to show
     */
    public ListFormatter(@NonNull final Details details) {
        this(details, "; ");
    }

    /**
     * Use {@link Details#Normal} with the given delimiter.
     *
     * @param delimiter to use
     */
    public ListFormatter(@NonNull final String delimiter) {
        this(Details.Normal, delimiter);
    }

    /**
     * Constructor.
     *
     * @param details   how much details to show
     * @param delimiter to use if details is {@link Details#Normal}
     */
    @SuppressWarnings("WeakerAccess")
    public ListFormatter(@NonNull final Details details,
                         @NonNull final String delimiter) {
        mDetails = details;
        mDelimiter = delimiter;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final List<T> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }

        switch (mDetails) {
            case Full: {
                return rawValue.stream()
                               .map(entity -> entity.getLabel(context, mDetails))
                               .map(s -> "<li>" + s + "</li>")
                               .collect(Collectors.joining("", "<ul>", "</ul>"));
            }
            case Normal: {
                return rawValue.stream()
                               .map(entity -> entity.getLabel(context, mDetails))
                               .collect(Collectors.joining(mDelimiter));
            }
            case Short: {
                if (rawValue.size() > 1) {
                    // special case, we use the Normal setting and use the "and_others" suffix
                    return context.getString(R.string.and_others_plus,
                                             rawValue.get(0).getLabel(context, Details.Normal),
                                             rawValue.size() - 1);
                } else {
                    return rawValue.get(0).getLabel(context, mDetails);
                }
            }
        }
        return "";
    }
}

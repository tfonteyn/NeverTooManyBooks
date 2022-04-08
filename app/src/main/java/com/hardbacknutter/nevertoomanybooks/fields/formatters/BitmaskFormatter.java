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
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Details;

/**
 * <ul>
 *      <li>Multiple fields: <strong>no</strong></li>
 * </ul>
 * <p>
 * Why Long? See Sqlite docs... storage class INTEGER.
 * TLDR: we always get a long from the database even if the column stores an int.
 */
public class BitmaskFormatter
        extends HtmlFormatter<Long> {

    @NonNull
    private final Map<Integer, String> mValues;

    @NonNull
    private final Details mDetails;

    @NonNull
    private final String mDelimiter;

    /**
     * Constructor.
     *
     * @param details how much details to show
     * @param values  the full list of possible values
     */
    public BitmaskFormatter(@NonNull final Details details,
                            @NonNull final Map<Integer, String> values) {
        this(details, "; ", values);
    }

    /**
     * Use {@link Details#Normal} with the given delimiter.
     *
     * @param delimiter to use
     * @param values    the full list of possible values
     */
    public BitmaskFormatter(@NonNull final String delimiter,
                            @NonNull final Map<Integer, String> values) {
        this(Details.Normal, delimiter, values);
    }

    /**
     * Constructor.
     *
     * @param details   how much details to show
     * @param delimiter to use if details is {@link Details#Normal}
     * @param values    the full list of possible values
     */
    public BitmaskFormatter(@NonNull final Details details,
                            @NonNull final String delimiter,
                            @NonNull final Map<Integer, String> values) {
        mDetails = details;
        mValues = values;
        mDelimiter = delimiter;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Long rawValue) {
        if (rawValue == null || rawValue == 0) {
            return "";
        }
        final int value = rawValue.intValue();

        final List<String> list = mValues.entrySet()
                                         .stream()
                                         .filter(entry -> (entry.getKey() & value) != 0)
                                         .map(Map.Entry::getValue)
                                         .collect(Collectors.toList());
        switch (mDetails) {
            case Full: {
                return list.stream()
                           .map(s -> "<li>" + s + "</li>")
                           .collect(Collectors.joining("", "<ul>", "</ul>"));
            }
            case Normal: {
                return String.join(mDelimiter, list);
            }
            case Short: {
                if (list.size() > 1) {
                    // special case, we use the Normal setting and use the "and_others" suffix
                    return context.getString(R.string.and_others_plus, list.get(0),
                                             list.size() - 1);
                } else {
                    return list.get(0);
                }
            }
        }
        return "";
    }
}
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
    private final Supplier<Map<Integer, Integer>> mapSupplier;

    @NonNull
    private final Details details;

    @NonNull
    private final String delimiter;

    /**
     * Constructor.
     *
     * @param details     how much details to show
     * @param mapSupplier for a Map with all <strong>possible</strong> values
     */
    public BitmaskFormatter(@NonNull final Details details,
                            @NonNull final Supplier<Map<Integer, Integer>> mapSupplier) {
        this(details, "; ", mapSupplier);
    }

    /**
     * Constructor.
     *
     * @param details     how much details to show
     * @param delimiter   to use if details is {@link Details#Normal}
     * @param mapSupplier for a Map with all <strong>possible</strong> values
     */
    private BitmaskFormatter(@NonNull final Details details,
                             @NonNull final String delimiter,
                             @NonNull final Supplier<Map<Integer, Integer>> mapSupplier) {
        this.details = details;
        this.mapSupplier = mapSupplier;
        this.delimiter = delimiter;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Long rawValue) {
        if (rawValue == null || rawValue == 0) {
            return "";
        }
        final int value = rawValue.intValue();

        final List<String> list = mapSupplier.get()
                                             .entrySet()
                                             .stream()
                                             .filter(entry -> (entry.getKey() & value) != 0)
                                             .map(Map.Entry::getValue)
                                             .map(context::getString)
                                             .collect(Collectors.toList());
        switch (details) {
            case Full: {
                return list.stream()
                           .map(s -> "<li>" + s + "</li>")
                           .collect(Collectors.joining("", "<ul>", "</ul>"));
            }
            case AutoSelect:
            case Normal: {
                return String.join(delimiter, list);
            }
            case Short: {
                if (list.size() > 1) {
                    // special case, we only show the first element and the "and_others" suffix
                    return context.getString(R.string.and_others_plus, list.get(0),
                                             list.size() - 1);
                } else {
                    return list.get(0);
                }
            }


            default:
                throw new IllegalArgumentException("details=" + details);
        }
    }
}

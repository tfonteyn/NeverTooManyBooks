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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

public class SeriesListFormatter
        extends HtmlFormatter<List<Series>> {

    @NonNull
    private final Series.Details mDetails;
    private final boolean mSingleLine;

    /**
     * Constructor.
     *
     * @param details     how much details to show
     * @param singleLine  If set, then the format will use a single line, with the elements
     *                    separated by a ';'. Otherwise it will use an HTML list.
     * @param enableLinks {@code true} to enable links.
     *                    Ignored if the View has an onClickListener
     */
    public SeriesListFormatter(@NonNull final Series.Details details,
                               final boolean singleLine,
                               final boolean enableLinks) {
        super(enableLinks);
        mDetails = details;
        mSingleLine = singleLine;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final List<Series> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }

        switch (mDetails) {
            case Full:
            case Normal:
                if (mSingleLine) {
                    return Csv.join("; ", rawValue, true, null,
                                    element -> element.getLabel(context));
                } else {
                    return Csv.htmlList(context, rawValue, element -> element.getLabel(context));
                }

            case Short:
                if (rawValue.size() > 1) {
                    return context.getString(R.string.and_others_plus,
                                             rawValue.get(0).getLabel(context),
                                             rawValue.size() - 1);
                } else {
                    return rawValue.get(0).getLabel(context);
                }
        }
        return "";
    }
}

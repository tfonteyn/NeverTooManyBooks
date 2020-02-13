/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

public class SeriesListFormatter
        extends HtmlFormatter<List<Series>> {

    private final Series.Details mDetails;

    /**
     * Constructor.
     *
     * @param details     how much details to show
     * @param enableLinks {@code true} to enable links.
     *                    Do not enable if the View has an onClickListener
     */
    public SeriesListFormatter(@NonNull final Series.Details details,
                               final boolean enableLinks) {
        super(enableLinks);
        mDetails = details;
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
                return Csv.join("<br>", rawValue, true, "• ",
                                series -> series.getLabel(context));

            case Short:
                if (rawValue.size() > 1) {
                    return rawValue.get(0).getLabel(context)
                           + ' ' + context.getString(R.string.and_others);
                } else {
                    return rawValue.get(0).getLabel(context);
                }
        }
        return "";
    }
}

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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * StringList factory for a Series.
 * <p>
 * Format:
 * <ul>
 *      <li>title (number) * {json}</li>
 *      <li>title * {json}</li>
 * </ul>
 * number: alpha-numeric, a proposed format is "1","1.0","1a", "1|omnibus" etc.
 * i.e. starting with a number (int or float) with optional alphanumeric characters trailing.
 * <p>
 * <strong>Note:</strong> the " * {json}" suffix is optional and can be missing.
 */
public class SeriesCoder
        implements StringList.Coder<Series> {

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public SeriesCoder() {
    }

    @Override
    @NonNull
    public Series decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Series series = Series.from(parts.get(0));
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));
                if (details.has(DBKey.SERIES_IS_COMPLETE)) {
                    series.setComplete(details.optBoolean(DBKey.SERIES_IS_COMPLETE));
                } else if (details.has("complete")) {
                    series.setComplete(details.optBoolean("complete"));
                }
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return series;
    }
}

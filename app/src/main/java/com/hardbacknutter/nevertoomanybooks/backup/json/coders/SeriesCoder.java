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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class SeriesCoder
        implements JsonCoder<Series> {

    SeriesCoder() {
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Series series)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKeys.KEY_PK_ID, series.getId());
        out.put(DBKeys.KEY_SERIES_TITLE, series.getTitle());

        if (!series.getNumber().isEmpty()) {
            out.put(DBKeys.KEY_BOOK_NUM_IN_SERIES, series.getNumber());
        }
        if (series.isComplete()) {
            out.put(DBKeys.KEY_SERIES_IS_COMPLETE, true);
        }
        return out;
    }

    @Override
    @NonNull
    public Series decode(@NonNull final JSONObject data)
            throws JSONException {

        final Series series = new Series(data.getString(DBKeys.KEY_SERIES_TITLE));
        series.setId(data.getLong(DBKeys.KEY_PK_ID));

        if (data.has(DBKeys.KEY_BOOK_NUM_IN_SERIES)) {
            series.setNumber(data.getString(DBKeys.KEY_BOOK_NUM_IN_SERIES));
        }
        if (data.has(DBKeys.KEY_SERIES_IS_COMPLETE)) {
            series.setComplete(data.getBoolean(DBKeys.KEY_SERIES_IS_COMPLETE));
        } else if (data.has("complete")) {
            series.setComplete(data.getBoolean("complete"));
        }

        return series;
    }
}

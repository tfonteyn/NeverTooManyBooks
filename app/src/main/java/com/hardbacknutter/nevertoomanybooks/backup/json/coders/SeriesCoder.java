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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

public class SeriesCoder
        extends JsonCoderBase<Series> {

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Series series) {
        final JSONObject data = new JSONObject();
        try {
            data.put(DBDefinitions.KEY_PK_ID, series.getId());
            data.put(DBDefinitions.KEY_SERIES_TITLE, series.getTitle());
            if (!series.getNumber().isEmpty()) {
                data.put(DBDefinitions.KEY_BOOK_NUM_IN_SERIES, series.getNumber());
            }
            if (series.isComplete()) {
                data.put(DBDefinitions.KEY_SERIES_IS_COMPLETE, true);
            }
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }

        return data;
    }

    @Override
    @NonNull
    public Series decode(@NonNull final JSONObject data)
            throws JSONException {

        final Series series = new Series(data.getString(DBDefinitions.KEY_SERIES_TITLE));
        if (data.has(DBDefinitions.KEY_BOOK_NUM_IN_SERIES)) {
            series.setNumber(data.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES));
        }
        if (data.has(DBDefinitions.KEY_SERIES_IS_COMPLETE)) {
            series.setComplete(data.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE));
        } else if (data.has("complete")) {
            series.setComplete(data.getBoolean("complete"));
        }

        return series;
    }
}

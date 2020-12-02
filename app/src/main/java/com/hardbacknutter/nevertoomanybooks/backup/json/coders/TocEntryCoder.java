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
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class TocEntryCoder
        implements JsonCoder<TocEntry> {

    private final AuthorCoder mAuthorCoder = new AuthorCoder();

    TocEntryCoder() {
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final TocEntry tocEntry)
            throws JSONException {
        final JSONObject data = new JSONObject();

        data.put(DBDefinitions.KEY_PK_ID, tocEntry.getId());
        data.put(DBDefinitions.KEY_TITLE, tocEntry.getTitle());
        data.put(DBDefinitions.KEY_FK_AUTHOR, mAuthorCoder.encode(tocEntry.getPrimaryAuthor()));

        if (!tocEntry.getFirstPublicationDate().isEmpty()) {
            data.put(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                     tocEntry.getFirstPublicationDate().getIsoString());
        }

        return data;
    }

    @Override
    @NonNull
    public TocEntry decode(@NonNull final JSONObject data)
            throws JSONException {

        final TocEntry tocEntry = new TocEntry(
                mAuthorCoder.decode(data.getJSONObject(DBDefinitions.KEY_FK_AUTHOR)),
                data.getString(DBDefinitions.KEY_TITLE),
                // optional
                data.optString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION));

        tocEntry.setId(data.getLong(DBDefinitions.KEY_PK_ID));

        return tocEntry;
    }
}

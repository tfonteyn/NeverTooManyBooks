/*
 * @Copyright 2018-2024 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class TocEntryCoder
        implements JsonCoder<TocEntry> {

    private final AuthorCoder authorCoder = new AuthorCoder();

    TocEntryCoder() {
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final TocEntry tocEntry)
            throws JSONException {
        final JSONObject data = new JSONObject();

        data.put(DBKey.PK_ID, tocEntry.getId());
        data.put(DBKey.TITLE, tocEntry.getTitle());
        data.put(DBKey.FK_AUTHOR, authorCoder.encode(tocEntry.getPrimaryAuthor()));

        final PartialDate firstPublicationDate = tocEntry.getFirstPublicationDate();
        if (firstPublicationDate.isPresent()) {
            data.put(DBKey.FIRST_PUBLICATION__DATE, firstPublicationDate.getIsoString());
        }

        return data;
    }

    @Override
    @NonNull
    public TocEntry decode(@NonNull final JSONObject data)
            throws JSONException {

        final TocEntry tocEntry = new TocEntry(
                authorCoder.decode(data.getJSONObject(DBKey.FK_AUTHOR)),
                data.getString(DBKey.TITLE),
                new PartialDate(data.optString(DBKey.FIRST_PUBLICATION__DATE)));

        tocEntry.setId(data.getLong(DBKey.PK_ID));

        return tocEntry;
    }
}

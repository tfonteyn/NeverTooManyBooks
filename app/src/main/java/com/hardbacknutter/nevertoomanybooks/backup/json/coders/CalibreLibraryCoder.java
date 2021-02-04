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

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

class CalibreLibraryCoder
        implements JsonCoder<CalibreLibrary> {

    @NonNull
    @Override
    public JSONObject encode(@NonNull final CalibreLibrary library)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBDefinitions.KEY_PK_ID, library.getId());
        out.put(DBDefinitions.KEY_CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        out.put(DBDefinitions.KEY_CALIBRE_LIBRARY_UUID, library.getUuid());
        out.put(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME, library.getName());
        out.put(DBDefinitions.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE,
                library.getLastSyncDateAsString());
        out.put(DBDefinitions.KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        return out;
    }

    @NonNull
    @Override
    public CalibreLibrary decode(@NonNull final JSONObject data)
            throws JSONException {

        final CalibreLibrary library = new CalibreLibrary(
                data.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_UUID),
                data.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_STRING_ID),
                data.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME),
                data.getLong(DBDefinitions.KEY_FK_BOOKSHELF));

        library.setId(data.getLong(DBDefinitions.KEY_PK_ID));
        library.setLastSyncDate(data.getString(
                DBDefinitions.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE));

        return library;
    }
}

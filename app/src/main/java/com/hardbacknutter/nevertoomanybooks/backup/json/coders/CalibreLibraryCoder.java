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

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreVirtualLibrary;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

class CalibreLibraryCoder
        implements JsonCoder<CalibreLibrary> {

    private static final String TAG_VL = "virtual_libraries";

    @NonNull
    @Override
    public JSONObject encode(@NonNull final CalibreLibrary library)
            throws JSONException {
        final JSONObject data = new JSONObject();

        data.put(DBKeys.KEY_PK_ID, library.getId());
        data.put(DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        data.put(DBKeys.KEY_CALIBRE_LIBRARY_UUID, library.getUuid());
        data.put(DBKeys.KEY_CALIBRE_LIBRARY_NAME, library.getName());
        data.put(DBKeys.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE,
                 library.getLastSyncDateAsString());
        data.put(DBKeys.KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        final ArrayList<CalibreVirtualLibrary> vlibs = library.getVirtualLibraries();
        if (!vlibs.isEmpty()) {
            final JSONArray vlArray = new JSONArray();
            for (final CalibreVirtualLibrary vlib : vlibs) {
                final JSONObject vlData = new JSONObject();
                vlData.put(DBKeys.KEY_PK_ID, vlib.getId());
                vlData.put(DBKeys.KEY_CALIBRE_LIBRARY_NAME, vlib.getName());
                vlData.put(DBKeys.KEY_CALIBRE_VIRT_LIB_EXPR, vlib.getExpr());
                vlData.put(DBKeys.KEY_FK_BOOKSHELF, vlib.getMappedBookshelfId());

                vlArray.put(vlData);
            }
            data.put(TAG_VL, vlArray);
        }
        return data;
    }

    @NonNull
    @Override
    public CalibreLibrary decode(@NonNull final JSONObject data)
            throws JSONException {

        final CalibreLibrary library = new CalibreLibrary(
                data.getString(DBKeys.KEY_CALIBRE_LIBRARY_UUID),
                data.getString(DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID),
                data.getString(DBKeys.KEY_CALIBRE_LIBRARY_NAME),
                data.getLong(DBKeys.KEY_FK_BOOKSHELF));
        library.setId(data.getLong(DBKeys.KEY_PK_ID));

        library.setLastSyncDate(data.getString(
                DBKeys.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE));

        final JSONArray vlArray = data.optJSONArray(TAG_VL);
        if (vlArray != null) {
            final List<CalibreVirtualLibrary> vlibs = new ArrayList<>();
            for (int i = 0; i < vlArray.length(); i++) {
                final JSONObject vlData = vlArray.getJSONObject(i);
                final CalibreVirtualLibrary vlib = new CalibreVirtualLibrary(
                        library.getId(),
                        vlData.getString(DBKeys.KEY_CALIBRE_LIBRARY_NAME),
                        vlData.getString(DBKeys.KEY_CALIBRE_VIRT_LIB_EXPR),
                        vlData.getLong(DBKeys.KEY_FK_BOOKSHELF));
                vlib.setId(vlData.getLong(DBKeys.KEY_PK_ID));

                vlibs.add(vlib);
            }
            library.setVirtualLibraries(vlibs);
        }
        return library;
    }
}

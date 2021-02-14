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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreVirtualLibrary;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

class CalibreLibraryCoder
        implements JsonCoder<CalibreLibrary> {

    private static final String TAG_VL = "virtual_libraries";

    @NonNull
    @Override
    public JSONObject encode(@NonNull final CalibreLibrary library)
            throws JSONException {
        final JSONObject data = new JSONObject();

        data.put(DBDefinitions.KEY_PK_ID, library.getId());
        data.put(DBDefinitions.KEY_CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        data.put(DBDefinitions.KEY_CALIBRE_LIBRARY_UUID, library.getUuid());
        data.put(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME, library.getName());
        data.put(DBDefinitions.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE,
                 library.getLastSyncDateAsString());
        data.put(DBDefinitions.KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        final ArrayList<CalibreVirtualLibrary> vlibs = library.getVirtualLibraries();
        if (!vlibs.isEmpty()) {
            final JSONArray vlArray = new JSONArray();
            for (final CalibreVirtualLibrary vlib : vlibs) {
                final JSONObject vlData = new JSONObject();
                vlData.put(DBDefinitions.KEY_PK_ID, vlib.getId());
                vlData.put(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME, vlib.getName());
                vlData.put(DBDefinitions.KEY_CALIBRE_VIRT_LIB_EXPR, vlib.getExpr());
                vlData.put(DBDefinitions.KEY_FK_BOOKSHELF, vlib.getMappedBookshelfId());

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
                data.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_UUID),
                data.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_STRING_ID),
                data.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME),
                data.getLong(DBDefinitions.KEY_FK_BOOKSHELF));
        library.setId(data.getLong(DBDefinitions.KEY_PK_ID));

        library.setLastSyncDate(data.getString(
                DBDefinitions.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE));

        final JSONArray vlArray = data.optJSONArray(TAG_VL);
        if (vlArray != null) {
            final List<CalibreVirtualLibrary> vlibs = new ArrayList<>();
            for (int i = 0; i < vlArray.length(); i++) {
                final JSONObject vlData = vlArray.getJSONObject(i);
                final CalibreVirtualLibrary vlib = new CalibreVirtualLibrary(
                        library.getId(),
                        vlData.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME),
                        vlData.getString(DBDefinitions.KEY_CALIBRE_VIRT_LIB_EXPR),
                        vlData.getLong(DBDefinitions.KEY_FK_BOOKSHELF));
                vlib.setId(vlData.getLong(DBDefinitions.KEY_PK_ID));

                vlibs.add(vlib);
            }
            library.setVirtualLibraries(vlibs);
        }
        return library;
    }
}

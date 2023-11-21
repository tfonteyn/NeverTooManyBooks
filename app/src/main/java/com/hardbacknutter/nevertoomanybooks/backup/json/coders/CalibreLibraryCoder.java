/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreVirtualLibrary;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class CalibreLibraryCoder
        implements JsonCoder<CalibreLibrary> {

    private static final String TAG_VL = "virtual_libraries";
    @NonNull
    private final Context context;
    @NonNull
    private final JsonCoder<Bookshelf> bookshelfCoder;

    @NonNull
    private final BookshelfDao bookshelfDao;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param defaultStyle the default style to use for {@link Bookshelf}s
     */
    public CalibreLibraryCoder(@NonNull final Context context,
                               @NonNull final Style defaultStyle) {
        this.context = context;
        bookshelfCoder = new BookshelfCoder(context, defaultStyle);
        bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final CalibreLibrary library)
            throws JSONException {

        final JSONObject data = new JSONObject();
        data.put(DBKey.PK_ID, library.getId());
        data.put(DBKey.CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        data.put(DBKey.CALIBRE_LIBRARY_UUID, library.getUuid());
        data.put(DBKey.CALIBRE_LIBRARY_NAME, library.getName());
        data.put(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC, library.getLastSyncDateAsString());

        final Bookshelf libraryBookshelf = bookshelfDao
                .getBookshelf(context,
                              library.getMappedBookshelfId(),
                              Bookshelf.USER_DEFAULT,
                              Bookshelf.HARD_DEFAULT)
                .orElseThrow();

        // We could just encode a reference to the bookshelf,
        // but the space-saving would be minuscule.
        data.put(DBKey.FK_BOOKSHELF, bookshelfCoder.encode(libraryBookshelf));

        final List<CalibreVirtualLibrary> vLibs = library.getVirtualLibraries();
        if (!vLibs.isEmpty()) {
            final JSONArray vlArray = new JSONArray();
            for (final CalibreVirtualLibrary vLib : vLibs) {
                final JSONObject vlData = new JSONObject();
                vlData.put(DBKey.PK_ID, vLib.getId());
                vlData.put(DBKey.CALIBRE_LIBRARY_NAME, vLib.getName());
                vlData.put(DBKey.CALIBRE_VIRT_LIB_EXPR, vLib.getExpr());

                final Bookshelf vlibBookshelf = bookshelfDao
                        .getBookshelf(context, vLib.getMappedBookshelfId())
                        .orElse(libraryBookshelf);

                vlData.put(DBKey.FK_BOOKSHELF, bookshelfCoder.encode(vlibBookshelf));

                vlArray.put(vlData);
            }
            data.put(TAG_VL, vlArray);
        }
        return data;
    }

    /**
     * Encode a reference to the given {@link CalibreLibrary}.
     * <p>
     * Reference is {@link DBKey#CALIBRE_LIBRARY_UUID} <strong>OR</strong>,
     * if not available, the {@link DBKey#CALIBRE_LIBRARY_STRING_ID}.
     *
     * @param library to encode
     *
     * @return the encoded CalibreLibrary
     *
     * @see #decodeReference(JSONObject)
     */
    @NonNull
    @Override
    public JSONObject encodeReference(@NonNull final CalibreLibrary library)
            throws JSONException {
        final JSONObject data = new JSONObject();
        final String uuid = library.getUuid();
        if (uuid.isEmpty()) {
            data.put(DBKey.CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        } else {
            // The UUID is only present if our extension is installed on the CCS
            data.put(DBKey.CALIBRE_LIBRARY_UUID, uuid);
        }
        return data;
    }

    /**
     * Decode a {@link CalibreLibrary} referenced by {@link DBKey#CALIBRE_LIBRARY_UUID}
     * <strong>OR</strong> the {@link DBKey#CALIBRE_LIBRARY_STRING_ID}.
     *
     * @param data json object
     *
     * @return the resolved library
     *
     * @see #encodeReference(CalibreLibrary)
     */
    @NonNull
    @Override
    public Optional<CalibreLibrary> decodeReference(@NonNull final JSONObject data)
            throws JSONException {

        Optional<CalibreLibrary> library;
        String s = data.optString(DBKey.CALIBRE_LIBRARY_UUID);
        if (s != null && !s.isEmpty()) {
            library = ServiceLocator.getInstance().getCalibreLibraryDao().findLibraryByUuid(s);
            if (library.isPresent()) {
                return library;
            }
        }

        s = data.optString(DBKey.CALIBRE_LIBRARY_STRING_ID);
        if (s != null && !s.isEmpty()) {
            library = ServiceLocator.getInstance().getCalibreLibraryDao().findLibraryByStringId(s);
            if (library.isPresent()) {
                return library;
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public CalibreLibrary decode(@NonNull final JSONObject data)
            throws JSONException {

        final Object tmpBS = data.opt(DBKey.FK_BOOKSHELF);
        if (tmpBS == null || tmpBS instanceof Number) {
            try {
                return v3decode(data);
            } catch (@NonNull final DaoWriteException e) {
                throw new JSONException(e);
            }
        }

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        final Bookshelf libraryBookshelf = bookshelfCoder
                .decode(data.getJSONObject(DBKey.FK_BOOKSHELF));
        bookshelfDao.fixId(context, libraryBookshelf, locale);

        final CalibreLibrary library = new CalibreLibrary(
                data.getString(DBKey.CALIBRE_LIBRARY_UUID),
                data.getString(DBKey.CALIBRE_LIBRARY_STRING_ID),
                data.getString(DBKey.CALIBRE_LIBRARY_NAME),
                libraryBookshelf);
        library.setId(data.getLong(DBKey.PK_ID));

        library.setLastSyncDate(data.getString(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC));

        final JSONArray vlArray = data.optJSONArray(TAG_VL);
        if (vlArray != null) {
            final List<CalibreVirtualLibrary> vLibs = new ArrayList<>();
            for (int i = 0; i < vlArray.length(); i++) {
                final JSONObject vlData = vlArray.getJSONObject(i);

                final Bookshelf vlibBookshelf = bookshelfCoder
                        .decode(vlData.getJSONObject(DBKey.FK_BOOKSHELF));
                bookshelfDao.fixId(context, vlibBookshelf, locale);

                final CalibreVirtualLibrary vLib = new CalibreVirtualLibrary(
                        library.getId(),
                        vlData.getString(DBKey.CALIBRE_LIBRARY_NAME),
                        vlData.getString(DBKey.CALIBRE_VIRT_LIB_EXPR),
                        vlibBookshelf);
                vLib.setId(vlData.getLong(DBKey.PK_ID));

                vLibs.add(vLib);
            }
            library.setVirtualLibraries(vLibs);
        }
        return library;
    }


    // ok, this is nasty... up-to and including Backup format v3 we wrote
    // only the bookshelf id to the data object.
    // Importing that data ONLY works IF the bookshelf
    // a) exists and b) has the same id. This led to data loss on full imports to a clean
    // installation.
    // There is no real (simple) recovery solution to that. So....
    @NonNull
    private CalibreLibrary v3decode(@NonNull final JSONObject data)
            throws DaoWriteException {

        final String libName = data.getString(DBKey.CALIBRE_LIBRARY_NAME);

        final long libBookshelfId = v3resolveBookshelf(data, libName);

        final CalibreLibrary library = new CalibreLibrary(
                data.getString(DBKey.CALIBRE_LIBRARY_UUID),
                data.getString(DBKey.CALIBRE_LIBRARY_STRING_ID),
                libName,
                libBookshelfId);
        library.setId(data.getLong(DBKey.PK_ID));

        library.setLastSyncDate(data.getString(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC));

        final JSONArray vlArray = data.optJSONArray(TAG_VL);
        if (vlArray != null) {
            final List<CalibreVirtualLibrary> vLibs = new ArrayList<>();
            for (int i = 0; i < vlArray.length(); i++) {
                final JSONObject vlData = vlArray.getJSONObject(i);
                final String vLibName = vlData.getString(DBKey.CALIBRE_LIBRARY_NAME);
                final long vLibBookshelfId = v3resolveBookshelf(vlData, "v-" + libName);

                final CalibreVirtualLibrary vLib = new CalibreVirtualLibrary(
                        library.getId(),
                        vLibName,
                        vlData.getString(DBKey.CALIBRE_VIRT_LIB_EXPR),
                        vLibBookshelfId);
                vLib.setId(vlData.getLong(DBKey.PK_ID));

                vLibs.add(vLib);
            }
            library.setVirtualLibraries(vLibs);
        }

        return library;
    }

    private long v3resolveBookshelf(@NonNull final JSONObject data,
                                    @NonNull final String libName)
            throws DaoWriteException {

        // try original
        Bookshelf bookshelf = bookshelfDao.getBookshelf(context, data.getLong(DBKey.FK_BOOKSHELF))
                                          .orElse(null);
        if (bookshelf == null) {
            // have we created the workaround before?
            final String name = "Calibre '" + libName + "'";
            bookshelf = bookshelfDao.findByName(name).orElse(null);
            if (bookshelf == null) {
                // make a new one
                bookshelf = new Bookshelf(name, BuiltinStyle.HARD_DEFAULT_UUID);
                bookshelfDao.insert(context, bookshelf);
            }
        }
        return bookshelf.getId();
    }
}

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
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
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
    private final Supplier<BookshelfDao> bookshelfDaoSupplier;
    @NonNull
    private final Supplier<CalibreLibraryDao> calibreLibraryDaoSupplier;

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

        bookshelfDaoSupplier = ServiceLocator.getInstance()::getBookshelfDao;
        calibreLibraryDaoSupplier = ServiceLocator.getInstance()::getCalibreLibraryDao;
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

        final Bookshelf libraryBookshelf = Bookshelf
                .getBookshelf(context, library.getMappedBookshelfId())
                .orElseGet(() -> Bookshelf
                        .getBookshelf(context, Bookshelf.PREFERRED)
                        .orElseGet(() -> Bookshelf
                                .getBookshelf(context, Bookshelf.DEFAULT)
                                .orElseThrow()));

        data.put(DBKey.FK_BOOKSHELF, bookshelfCoder.encode(libraryBookshelf));

        final ArrayList<CalibreVirtualLibrary> vlibs = library.getVirtualLibraries();
        if (!vlibs.isEmpty()) {
            final JSONArray vlArray = new JSONArray();
            for (final CalibreVirtualLibrary vlib : vlibs) {
                final JSONObject vlData = new JSONObject();
                vlData.put(DBKey.PK_ID, vlib.getId());
                vlData.put(DBKey.CALIBRE_LIBRARY_NAME, vlib.getName());
                vlData.put(DBKey.CALIBRE_VIRT_LIB_EXPR, vlib.getExpr());

                final Bookshelf vlibBookshelf = Bookshelf
                        .getBookshelf(context, vlib.getMappedBookshelfId())
                        .orElse(libraryBookshelf);

                vlData.put(DBKey.FK_BOOKSHELF, bookshelfCoder.encode(vlibBookshelf));

                vlArray.put(vlData);
            }
            data.put(TAG_VL, vlArray);
        }
        return data;
    }

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

    @NonNull
    @Override
    public Optional<CalibreLibrary> decodeReference(@NonNull final JSONObject data)
            throws JSONException {

        CalibreLibrary library;
        String s = data.optString(DBKey.CALIBRE_LIBRARY_UUID);
        if (s != null && !s.isEmpty()) {
            library = calibreLibraryDaoSupplier.get().findLibraryByUuid(s);
            if (library != null) {
                return Optional.of(library);
            }
        }

        s = data.optString(DBKey.CALIBRE_LIBRARY_STRING_ID);
        if (s != null && !s.isEmpty()) {
            library = calibreLibraryDaoSupplier.get().findLibraryByStringId(s);
            if (library != null) {
                return Optional.of(library);
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

        final BookshelfDao bookshelfDao = bookshelfDaoSupplier.get();

        final Bookshelf libraryBookshelf = bookshelfCoder
                .decode(data.getJSONObject(DBKey.FK_BOOKSHELF));
        bookshelfDao.fixId(libraryBookshelf);

        final CalibreLibrary library = new CalibreLibrary(
                data.getString(DBKey.CALIBRE_LIBRARY_UUID),
                data.getString(DBKey.CALIBRE_LIBRARY_STRING_ID),
                data.getString(DBKey.CALIBRE_LIBRARY_NAME),
                libraryBookshelf);
        library.setId(data.getLong(DBKey.PK_ID));

        library.setLastSyncDate(data.getString(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC));

        final JSONArray vlArray = data.optJSONArray(TAG_VL);
        if (vlArray != null) {
            final List<CalibreVirtualLibrary> vlibs = new ArrayList<>();
            for (int i = 0; i < vlArray.length(); i++) {
                final JSONObject vlData = vlArray.getJSONObject(i);

                final Bookshelf vlibBookshelf = bookshelfCoder
                        .decode(data.getJSONObject(DBKey.FK_BOOKSHELF));
                bookshelfDao.fixId(vlibBookshelf);

                final CalibreVirtualLibrary vlib = new CalibreVirtualLibrary(
                        library.getId(),
                        vlData.getString(DBKey.CALIBRE_LIBRARY_NAME),
                        vlData.getString(DBKey.CALIBRE_VIRT_LIB_EXPR),
                        vlibBookshelf);
                vlib.setId(vlData.getLong(DBKey.PK_ID));

                vlibs.add(vlib);
            }
            library.setVirtualLibraries(vlibs);
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
            final List<CalibreVirtualLibrary> vlibs = new ArrayList<>();
            for (int i = 0; i < vlArray.length(); i++) {
                final JSONObject vlData = vlArray.getJSONObject(i);
                final String vlibName = vlData.getString(DBKey.CALIBRE_LIBRARY_NAME);
                final long vlibBookshelfId = v3resolveBookshelf(vlData, "v-" + libName);

                final CalibreVirtualLibrary vlib = new CalibreVirtualLibrary(
                        library.getId(),
                        vlibName,
                        vlData.getString(DBKey.CALIBRE_VIRT_LIB_EXPR),
                        vlibBookshelfId);
                vlib.setId(vlData.getLong(DBKey.PK_ID));

                vlibs.add(vlib);
            }
            library.setVirtualLibraries(vlibs);
        }

        return library;
    }

    private long v3resolveBookshelf(@NonNull final JSONObject data,
                                    @NonNull final String libName)
            throws DaoWriteException {
        final BookshelfDao bookshelfDao = bookshelfDaoSupplier.get();

        // try original
        Bookshelf bookshelf = Bookshelf.getBookshelf(context, data.getLong(DBKey.FK_BOOKSHELF))
                                       .orElse(null);
        if (bookshelf == null) {
            // have we created the workaround before?
            final String name = "Calibre '" + libName + "'";
            bookshelf = bookshelfDao.findByName(name).orElse(null);
            if (bookshelf == null) {
                // make a new one
                bookshelf = new Bookshelf(name, BuiltinStyle.DEFAULT_UUID);
                bookshelfDao.insert(context, bookshelf);
            }
        }
        return bookshelf.getId();
    }
}

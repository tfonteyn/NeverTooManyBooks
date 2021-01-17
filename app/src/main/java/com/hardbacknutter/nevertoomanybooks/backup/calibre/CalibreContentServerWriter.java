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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Dev Note: while most plumbing to use multiple libraries is in place,
 * right now we only support the default library.
 * TODO: read the list of libs and prompt the user, BEFORE starting a sync
 * <p>
 * https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/cdb.py
 */
public class CalibreContentServerWriter
        implements ArchiveWriter {

    private static final String TAG = "CalibreCSWriter";
    @NonNull
    private final CalibreContentServer mServer;
    @NonNull
    private final DAO mDb;

    /** Export configuration. */
    @NonNull
    private final ExportHelper mHelper;
    @NonNull
    private String mLibraryId;

    private ExportResults mExportResults;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     *
     * @throws IOException          on failures
     * @throws CertificateException on failures related to the user installed CA.
     */
    public CalibreContentServerWriter(@NonNull final Context context,
                                      @NonNull final ExportHelper helper)
            throws GeneralParsingException, IOException, CertificateException {
        mHelper = helper;

        mDb = new DAO(TAG);

        mServer = new CalibreContentServer(context, helper.getUri());
        mLibraryId = mServer.loadLibraries();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    // ENHANCE: allow passing in of the DESIRED library to use.
    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws GeneralParsingException, IOException {

        mExportResults = new ExportResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgressStep(
                0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            mServer.readMetaData(mLibraryId);
        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        final LocalDateTime dateSince;
        if (mHelper.isIncremental()) {
            dateSince = CalibreContentServer.getLastSyncDate(context);
        } else {
            dateSince = null;
        }

        int delta = 0;
        long lastUpdate = 0;

        try (DAO db = new DAO(TAG);
             Cursor cursor = db.fetchBooksForExportToCalibre(mLibraryId, dateSince)) {

            progressListener.setMaxPos(cursor.getCount());

            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                final Book book = Book.from(cursor, mDb);

                final int calibreId = book.getInt(DBDefinitions.KEY_CALIBRE_BOOK_ID);
                final String calibreUuid = book.getString(DBDefinitions.KEY_CALIBRE_BOOK_UUID);

                try {
                    // ENHANCE: full sync in one go.
                    final JSONObject calibreBook =
                            mServer.getBook(mLibraryId, calibreUuid);

                    // sanity check, the remote should always have this date field.
                    if (calibreBook != null && !calibreBook.isNull("last_modified")) {
                        final LocalDateTime remote = DateParser.getInstance(context).parseISO(
                                calibreBook.getString("last_modified"));
                        if (remote != null) {
                            // is our data newer then the server data ?
                            final LocalDateTime local = book.getLastUpdateUtcDate(context);
                            if (local != null && local.isAfter(remote)) {
                                // finally, we can push updates to the remote
                                sendUpdates(context, book, calibreId);
                                mExportResults.addBook(book.getId());
                            }
                        }
                    }
                } catch (@NonNull final JSONException e) {
                    // ignore, just move on to the next book
                    Logger.error(context, TAG, e, "bookId=" + book.getId());
                }

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                    progressListener.publishProgressStep(delta, book.getTitle());
                    lastUpdate = now;
                    delta = 0;
                }
            }
        }

        // always set the sync date!
        CalibreContentServer.setLastSyncDate(context, LocalDateTime.now(ZoneOffset.UTC));

        return mExportResults;
    }

    private void sendUpdates(@NonNull final Context context,
                             @NonNull final Book book,
                             final int calibreId)
            throws JSONException, IOException {

        // Empty fields MUST be send to make the server remove the data.

        final JSONObject changes = new JSONObject();
        changes.put("title", book.getTitle());
        changes.put("comments", book.getString(DBDefinitions.KEY_DESCRIPTION));
        // we don't read this field, but we DO write it.
        changes.put("pubdate", book.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        changes.put("last_modified", book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED));

        final JSONArray authors = new JSONArray();
        for (final Author author
                : book.<Author>getParcelableArrayList(Book.BKEY_AUTHOR_LIST)) {
            final String name = author.getFormattedName(true);
            authors.put(name);
        }
        changes.put("authors", authors);

        final Series series = book.getPrimarySeries();
        if (series != null) {
            changes.put("series", series.getTitle());
            try {
                // Calibre can only accept Floats
                final float number = Float.parseFloat(series.getNumber());
                changes.put("series_index", number);
            } catch (@NonNull final NumberFormatException ignore) {
                // send '1' as that is the Calibre default
                changes.put("series_index", 1);
            }
        } else {
            changes.put("series", "");
            // send '1' as that is the Calibre default
            changes.put("series_index", 1);
        }

        final Publisher publisher = book.getPrimaryPublisher();
        if (publisher != null) {
            changes.put("publisher", publisher.getName());
        } else {
            changes.put("publisher", "");
        }

        final JSONArray languages = new JSONArray();
        final String language = book.getString(DBDefinitions.KEY_LANGUAGE);
        if (!language.isEmpty()) {
            languages.put(language);
        }
        changes.put("languages", languages);

        // URGENT: any identifier WE don't store are being removed on the server side!
        final JSONObject identifiers = collectIdentifiers(book);
        if (identifiers.length() > 0) {
            changes.put("identifiers", identifiers);
        }

        for (final CalibreCustomFields.Field cf : mServer.getCustomFields()) {
            switch (cf.type) {
                case CalibreCustomFields.TYPE_BOOL: {
                    changes.put(cf.calibreKey, book.getBoolean(cf.dbKey));
                    break;
                }
                case CalibreCustomFields.TYPE_DATETIME:
                case CalibreCustomFields.TYPE_COMMENTS:
                case CalibreCustomFields.TYPE_TEXT: {
                    changes.put(cf.calibreKey, book.getString(cf.dbKey));
                    break;
                }
                default:
                    throw new IllegalArgumentException(cf.type);
            }
        }

        final File coverFile = book.getCoverFile(context, 0);
        if (coverFile != null) {
            final byte[] bFile = new byte[(int) coverFile.length()];
            try (FileInputStream is = new FileInputStream(coverFile)) {
                //noinspection ResultOfMethodCallIgnored
                is.read(bFile);
            }
            changes.put("cover", Base64.encodeToString(bFile, 0));
            // FIXME: collecting the names is in fact NOT NEEDED here,
            //  but the current logic 'ExportResults' requires it,
            //  or we won't get the **number** of covers exported.
            mExportResults.addCover(coverFile.getName());

        } else {
            changes.put("cover", "");
        }

        // do the update
        mServer.pushChanges(mLibraryId, calibreId, changes);
    }

    private JSONObject collectIdentifiers(@NonNull final Book book)
            throws JSONException {

        final JSONObject identifiers = new JSONObject();
        long l;
        String s;

        l = book.getLong(DBDefinitions.KEY_ESID_GOODREADS_BOOK);
        if (l != 0) {
            identifiers.put("goodreads", String.valueOf(l));
        }
        l = book.getLong(DBDefinitions.KEY_ESID_ISFDB);
        if (l != 0) {
            identifiers.put("isfdb", String.valueOf(l));
        }
        l = book.getLong(DBDefinitions.KEY_ESID_LIBRARY_THING);
        if (l != 0) {
            identifiers.put("librarything", String.valueOf(l));
        }

        s = book.getString(DBDefinitions.KEY_ISBN);
        if (!s.isEmpty()) {
            identifiers.put("isbn", s);
        }
        s = book.getString(DBDefinitions.KEY_ESID_OPEN_LIBRARY);
        if (!s.isEmpty()) {
            identifiers.put("openlibrary", s);
        }

//        s = book.getString(DBDefinitions.KEY_ESID_ASIN);
//        if (!s.isEmpty()) {
//            identifiers.put("amazon", s);
//        }
//        s = book.getString(DBDefinitions.KEY_ESID_GOOGLE);
//        if (!s.isEmpty()) {
//            identifiers.put("google", s);
//        }
//        s = book.getString(DBDefinitions.KEY_ESID_WORLDCAT);
//        if (!s.isEmpty()) {
//            identifiers.put("oclc", s);
//        }
//        s = book.getString(DBDefinitions.KEY_ESID_LCCN);
//        if (!s.isEmpty()) {
//            identifiers.put("lccn", s);
//        }

        return identifiers;
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }
}

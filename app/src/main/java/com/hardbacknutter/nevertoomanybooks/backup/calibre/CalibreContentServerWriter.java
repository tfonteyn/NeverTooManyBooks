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
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
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
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.HttpNotFoundException;

/**
 * Dev Note: while most plumbing to use multiple libraries is in place,
 * right now we only support the default library.
 */
public class CalibreContentServerWriter
        implements ArchiveWriter {

    /** Log tag. */
    private static final String TAG = "CalibreServerWriter";
    @NonNull
    private final CalibreContentServer mServer;
    @NonNull
    private final DAO mDb;
    /** Export configuration. */
    @NonNull
    private final ExportHelper mHelper;
    private final boolean mDoCovers;
    @SuppressWarnings("FieldCanBeLocal")
    private CalibreLibrary mLibrary;
    private ExportResults mResults;

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
            throws IOException, CertificateException {

        mDb = new DAO(TAG);
        mHelper = helper;
        mServer = new CalibreContentServer(context, mHelper.getUri());

        mDoCovers = mHelper.getExporterEntries().contains(RecordType.Cover);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws GeneralParsingException, IOException {

        mResults = new ExportResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgressStep(
                0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            // Always read the meta data here.
            mServer.readMetaData(context, mDb);
            mLibrary = mServer.getDefaultLibrary();

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
             Cursor cursor = db.fetchBooksForExportToCalibre(mLibrary.getLibraryId(), dateSince)) {

            progressListener.setMaxPos(cursor.getCount());

            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                final Book localBook = Book.from(cursor, mDb);

                final int calibreId = localBook.getInt(DBDefinitions.KEY_CALIBRE_BOOK_ID);
                final String calibreUuid = localBook.getString(DBDefinitions.KEY_CALIBRE_BOOK_UUID);

                try {
                    // ENHANCE: full sync in one go.
                    //  The logic below is TO SLOW as we fetch each book individually
                    final CalibreBook calibreBook = mServer.getBook(mLibrary.getLibraryId(),
                                                                    calibreUuid);
                    final LocalDateTime remoteTime = DateParser.getInstance(context).parseISO(
                            calibreBook.getString(CalibreBook.LAST_MODIFIED));
                    // sanity check, the remote should always have this date field.
                    if (remoteTime != null) {
                        // is our data newer then the server data ?
                        final LocalDateTime local = localBook.getLastUpdateUtcDate(context);
                        if (local != null && local.isAfter(remoteTime)) {

                            final JSONObject changes =
                                    collectChanges(context, calibreBook, localBook);
                            mServer.pushChanges(mLibrary.getLibraryId(), calibreId, changes);

                            mResults.addBook(localBook.getId());
                        }
                    }
                } catch (@NonNull final HttpNotFoundException e404) {
                    // The book no longer exists on the server.
                    //FIXME: skipping for now, but we should remove the calibre data
                    // from the local book
                } catch (@NonNull final JSONException e) {
                    // ignore, just move on to the next book
                    Logger.error(context, TAG, e, "bookId=" + localBook.getId());
                }

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                    progressListener.publishProgressStep(delta, localBook.getTitle());
                    lastUpdate = now;
                    delta = 0;
                }
            }
        }

        // always set the sync date!
        CalibreContentServer.setLastSyncDate(context, LocalDateTime.now(ZoneOffset.UTC));

        return mResults;
    }

    private JSONObject collectChanges(@NonNull final Context context,
                                      @NonNull final CalibreBook calibreBook,
                                      @NonNull final Book localBook)
            throws JSONException, IOException {

        // Empty fields MUST be send to make the server remove the data.

        final JSONObject changes = new JSONObject();
        changes.put(CalibreBook.TITLE, localBook.getTitle());
        changes.put(CalibreBook.DESCRIPTION,
                    localBook.getString(DBDefinitions.KEY_DESCRIPTION));
        // we don't read this field, but we DO write it.
        changes.put(CalibreBook.DATE_PUBLISHED,
                    localBook.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        changes.put(CalibreBook.LAST_MODIFIED,
                    localBook.getString(DBDefinitions.KEY_UTC_LAST_UPDATED));

        final JSONArray authors = new JSONArray();
        for (final Author author
                : localBook.<Author>getParcelableArrayList(Book.BKEY_AUTHOR_LIST)) {
            authors.put(author.getFormattedName(true));
        }
        changes.put(CalibreBook.AUTHOR_ARRAY, authors);

        final Series series = localBook.getPrimarySeries();
        if (series != null) {
            changes.put(CalibreBook.SERIES, series.getTitle());
            try {
                // Calibre can only accept Floats
                final float number = Float.parseFloat(series.getNumber());
                changes.put(CalibreBook.SERIES_INDEX, number);
            } catch (@NonNull final NumberFormatException ignore) {
                // send '1' as that is the Calibre default
                changes.put(CalibreBook.SERIES_INDEX, 1);
            }
        } else {
            changes.put(CalibreBook.SERIES, "");
            // send '1' as that is the Calibre default
            changes.put(CalibreBook.SERIES_INDEX, 1);
        }

        final Publisher publisher = localBook.getPrimaryPublisher();
        if (publisher != null) {
            changes.put(CalibreBook.PUBLISHER, publisher.getName());
        } else {
            changes.put(CalibreBook.PUBLISHER, "");
        }

        changes.put(CalibreBook.RATING, ((int) localBook.getDouble(DBDefinitions.KEY_RATING)));

        final JSONArray languages = new JSONArray();
        final String language = localBook.getString(DBDefinitions.KEY_LANGUAGE);
        if (!language.isEmpty()) {
            languages.put(language);
        }
        changes.put(CalibreBook.LANGUAGES_ARRAY, languages);

        // The server expects a FULL set of identifiers. Any not present, will be deleted.
        // https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/write.py#L480
        // So, we send a combination of changes + the identifiers we don't know back to the server.
        final JSONObject localIdentifiers = collectIdentifiers(localBook);
        if (localIdentifiers.length() > 0) {
            final JSONObject identifiers = calibreBook.optJSONObject(CalibreBook.IDENTIFIERS);
            if (identifiers != null) {
                // overwrite remotes with locals
                final Iterator<String> it = localIdentifiers.keys();
                while (it.hasNext()) {
                    final String key = it.next();
                    identifiers.put(key, localIdentifiers.get(key));
                }
                changes.put(CalibreBook.IDENTIFIERS, identifiers);

            } else {
                // no remotes, just send all locals
                changes.put(CalibreBook.IDENTIFIERS, localIdentifiers);
            }
        }

        for (final CustomFields.Field cf : mServer.getCustomFields()) {
            switch (cf.type) {
                case CustomFields.TYPE_BOOL: {
                    changes.put(cf.calibreKey, localBook.getBoolean(cf.dbKey));
                    break;
                }
                case CustomFields.TYPE_DATETIME:
                case CustomFields.TYPE_COMMENTS:
                case CustomFields.TYPE_TEXT: {
                    changes.put(cf.calibreKey, localBook.getString(cf.dbKey));
                    break;
                }
                default:
                    throw new IllegalArgumentException(cf.type);
            }
        }

        if (mDoCovers) {
            final File coverFile = localBook.getCoverFile(context, 0);
            if (coverFile != null) {
                final byte[] bFile = new byte[(int) coverFile.length()];
                try (FileInputStream is = new FileInputStream(coverFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    is.read(bFile);
                }
                changes.put(CalibreBook.COVER, Base64.encodeToString(bFile, 0));
                // FIXME: collecting the names is in fact NOT NEEDED here,
                //  but the current logic 'ExportResults' requires it,
                //  or we won't get the **number** of covers exported.
                mResults.addCover(coverFile.getName());

            } else {
                changes.put(CalibreBook.COVER, "");
            }
        }

        return changes;
    }

    @NonNull
    private JSONObject collectIdentifiers(@NonNull final Book localBook)
            throws JSONException {

        final JSONObject collection = new JSONObject();

        for (final Identifier identifier : Identifier.MAP.values()) {
            if (identifier.isStoredLocally) {
                if (identifier.isLocalLong) {
                    final long v = localBook.getLong(identifier.local);
                    collection.put(identifier.remote, v != 0 ? String.valueOf(v) : "");
                } else {
                    final String s = localBook.getString(identifier.local);
                    collection.put(identifier.remote, s);
                }
            }
        }
        return collection;
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }
}

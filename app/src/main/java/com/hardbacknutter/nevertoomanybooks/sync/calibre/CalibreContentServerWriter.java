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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterHelper;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Export <strong>all</strong> libraries currently present on the server.
 * <p>
 * If the user asked for "new and updated books" only,
 * the 'last-sync-date' from the library is used to only fetch books added/modified
 * later then this timestamp FROM THE LOCAL DATABASE.
 * <p>
 * Each local book is compared to the remote book 'last-modified' date to
 * decide to update it or not.
 * <p>
 * We only UPDATE books which exist on the server.
 * We're not pushing new books to the server !
 * If a book no longer exists on the server, {@link SyncWriterHelper#isDeleteLocalBooks()} decides.
 */
public class CalibreContentServerWriter
        implements DataWriter<SyncWriterResults> {

    /** Log tag. */
    private static final String TAG = "CalibreServerWriter";

    @NonNull
    private final CalibreContentServer server;
    /** Export configuration. */
    @NonNull
    private final SyncWriterHelper helper;
    private final boolean doCovers;
    private final boolean deleteLocalBook;

    @NonNull
    private final DateParser dateParser;
    private final RealNumberParser realNumberParser;

    private SyncWriterResults results;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     * @param helper       export configuration
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreContentServerWriter(@NonNull final Context context,
                                      @NonNull final SyncWriterHelper helper,
                                      @NonNull final Locale systemLocale)
            throws CertificateException {

        this.helper = helper;

        server = new CalibreContentServer.Builder(context).build();
        doCovers = this.helper.getRecordTypes().contains(RecordType.Cover);
        deleteLocalBook = this.helper.isDeleteLocalBooks();

        dateParser = new ISODateParser(systemLocale);
        realNumberParser = new RealNumberParser(LocaleListUtils.asList(context));
    }

    @Override
    public void cancel() {
        server.cancel();
    }

    @NonNull
    @Override
    public SyncWriterResults write(@NonNull final Context context,
                                   @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   StorageException,
                   IOException {

        results = new SyncWriterResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(
                0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            server.readMetaData(context);
            final CalibreLibraryDao libraryDao = ServiceLocator.getInstance()
                                                               .getCalibreLibraryDao();
            for (final CalibreLibrary library : server.getLibraries()) {

                @Nullable
                final LocalDateTime dateSince;
                if (helper.isIncremental()) {
                    dateSince = dateParser.parse(library.getLastSyncDateAsString());
                } else {
                    dateSince = null;
                }

                // sanity check, we only update existing books... no books -> skip library.
                if (library.getTotalBooks() > 0) {
                    syncLibrary(library, dateSince, progressListener);
                }
                // always set the sync date!
                library.setLastSyncDate(LocalDateTime.now(ZoneOffset.UTC));
                libraryDao.update(library);
            }
        } catch (@NonNull final JSONException e) {
            throw new DataWriterException(e);
        }
        return results;
    }

    private void syncLibrary(@NonNull final CalibreLibrary library,
                             @Nullable final LocalDateTime dateSince,
                             @NonNull final ProgressListener progressListener)
            throws StorageException, IOException {
        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
        try (Cursor cursor = bookDao.fetchBooksForExportToCalibre(library.getId(), dateSince)) {

            int delta = 0;
            long lastUpdate = 0;
            progressListener.setMaxPos(cursor.getCount());

            final CalibreDao calibreDao = ServiceLocator.getInstance().getCalibreDao();

            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                final Book book = Book.from(cursor);
                try {
                    syncBook(library, book);

                } catch (@NonNull final HttpNotFoundException e404) {
                    // The book no longer exists on the server.
                    if (deleteLocalBook) {
                        bookDao.delete(book);
                    } else {
                        // keep the book but remove the calibre data for it
                        calibreDao.delete(book);
                        book.setCalibreLibrary(null);
                    }
                } catch (@NonNull final JSONException e) {
                    // ignore, just move on to the next book
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "bookId=" + book.getId());
                }

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                    progressListener.publishProgress(delta, book.getTitle());
                    lastUpdate = now;
                    delta = 0;
                }
            }
        }
    }

    private void syncBook(@NonNull final CalibreLibrary library,
                          @NonNull final Book book)
            throws IOException, StorageException, JSONException {

        final int calibreId = book.getInt(DBKey.CALIBRE_BOOK_ID);
        final String calibreUuid = book.getString(DBKey.CALIBRE_BOOK_UUID);

        // ENHANCE: full sync in one go.
        //  The logic below is TO SLOW as we fetch each book individually
        final JSONObject calibreBook = server.getBook(library.getLibraryStringId(), calibreUuid);

        String dateStr = null;
        if (!calibreBook.isNull(CalibreBook.LAST_MODIFIED)) {
            try {
                dateStr = calibreBook.getString(CalibreBook.LAST_MODIFIED);
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }

        final LocalDateTime remoteTime = dateParser.parse(dateStr);
        // sanity check, the remote should always have this date field.
        if (remoteTime != null) {
            // is our data newer then the server data ?
            final LocalDateTime localTime =
                    dateParser.parse(book.getString(DBKey.DATE_LAST_UPDATED__UTC, null));
            if (localTime != null && localTime.isAfter(remoteTime)) {
                final JSONObject identifiers = calibreBook.optJSONObject(CalibreBook.IDENTIFIERS);
                final JSONObject changes = collectChanges(library, identifiers, book);
                server.pushChanges(library.getLibraryStringId(), calibreId, changes);
                results.addBook(book.getId());
            }
        }
    }

    /**
     * Transform a {@link Book} to a Calibre {@link JSONObject}.
     * <p>
     * Copy the wanted fields from the local {@link Book} into a {@link JSONObject}
     * with field names {@link CalibreBook} as needed by Calibre.
     *
     * @param library                the library to which the given books belongs
     * @param calibreBookIdentifiers the <strong>full</strong> list of identifiers for this
     *                               book as <strong>fetched from the Calibre server</strong>
     * @param localBook              the Book we're syncing
     *
     * @return the JSON data to send to the Calibre server
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    private JSONObject collectChanges(@NonNull final CalibreLibrary library,
                                      @Nullable final JSONObject calibreBookIdentifiers,
                                      @NonNull final Book localBook)
            throws JSONException, IOException {

        // Empty fields MUST be send to make the server remove the data.

        final JSONObject changes = new JSONObject();
        changes.put(CalibreBook.TITLE, localBook.getTitle());
        changes.put(CalibreBook.DESCRIPTION, localBook.getString(DBKey.DESCRIPTION));
        // we don't read this field, but we DO write it.
        changes.put(CalibreBook.DATE_PUBLISHED,
                    localBook.getString(DBKey.BOOK_PUBLICATION__DATE));
        changes.put(CalibreBook.LAST_MODIFIED,
                    localBook.getString(DBKey.DATE_LAST_UPDATED__UTC));

        final JSONArray authors = new JSONArray();
        localBook.getAuthors()
                 .stream()
                 .map(author -> author.getFormattedName(true))
                 .forEach(authors::put);
        changes.put(CalibreBook.AUTHOR_ARRAY, authors);

        final Optional<Series> optSeries = localBook.getPrimarySeries();

        final String seriesTitle = optSeries.map(Series::getTitle).orElse("");
        // Calibre can only accept Floats; send '1' on any error, as that is the Calibre default
        float number = 1;
        if (optSeries.isPresent()) {
            try {
                number = Float.parseFloat(optSeries.get().getNumber());
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }

        changes.put(CalibreBook.SERIES, seriesTitle);
        changes.put(CalibreBook.SERIES_INDEX, number);

        changes.put(CalibreBook.PUBLISHER, localBook.getPrimaryPublisher()
                                                    .map(Publisher::getName)
                                                    .orElse(""));

        changes.put(CalibreBook.RATING, (int) localBook.getFloat(DBKey.RATING, realNumberParser));

        final JSONArray languages = new JSONArray();
        final String language = localBook.getString(DBKey.LANGUAGE, null);
        if (language != null && !language.isEmpty()) {
            languages.put(language);
        }
        changes.put(CalibreBook.LANGUAGES_ARRAY, languages);

        // The server expects a FULL set of identifiers. Any not present, will be deleted.
        // https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/write.py#L480
        // So, we send a combination of changes + the identifiers we don't know back to the server.
        final JSONObject localIdentifiers = collectIdentifiers(localBook);
        if (!localIdentifiers.isEmpty()) {
            if (calibreBookIdentifiers != null) {
                // overwrite remotes with locals
                final Iterator<String> it = localIdentifiers.keys();
                while (it.hasNext()) {
                    final String key = it.next();
                    calibreBookIdentifiers.put(key, localIdentifiers.get(key));
                }
                changes.put(CalibreBook.IDENTIFIERS, calibreBookIdentifiers);

            } else {
                // no remotes, just send all locals
                changes.put(CalibreBook.IDENTIFIERS, localIdentifiers);
            }
        }

        for (final CalibreCustomField cf : library.getCustomFields()) {
            switch (cf.getType()) {
                case CalibreCustomField.TYPE_BOOL: {
                    changes.put(cf.getCalibreKey(), localBook.getBoolean(cf.getDbKey()));
                    break;
                }
                case CalibreCustomField.TYPE_DATETIME:
                case CalibreCustomField.TYPE_COMMENTS:
                case CalibreCustomField.TYPE_TEXT: {
                    changes.put(cf.getCalibreKey(), localBook.getString(cf.getDbKey()));
                    break;
                }
                default:
                    throw new IllegalArgumentException(cf.getType());
            }
        }

        if (doCovers) {
            final Optional<File> coverFile = localBook.getCover(0);
            if (coverFile.isPresent()) {
                final File file = coverFile.get();
                final byte[] bFile = new byte[(int) file.length()];
                try (FileInputStream is = new FileInputStream(file)) {
                    //noinspection ResultOfMethodCallIgnored
                    is.read(bFile);
                }
                changes.put(CalibreBook.COVER, Base64.encodeToString(bFile, 0));
                results.addCover(file);

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
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }
}

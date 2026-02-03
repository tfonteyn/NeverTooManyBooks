/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.os.LocaleList;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.cleaning.Purger;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterResults;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Export <strong>all</strong> libraries currently present on the server.
 * <p>
 * If the user asked for "new and updated books" only,
 * the 'last-sync-date' from the library is used to only fetch books added/modified
 * later than this timestamp FROM THE LOCAL DATABASE.
 * <p>
 * Each local book is compared to the remote book 'last-modified' date to
 * decide to update it or not.
 * <p>
 * We only UPDATE books which exist on the server.
 * We're not pushing new books to the server !
 */
public class CalibreContentServerWriter
        implements DataWriter<SyncWriterResults> {

    /** Log tag. */
    private static final String TAG = "CalibreServerWriter";

    @NonNull
    private final CalibreContentServer server;
    /** Export configuration. */
    private final boolean doCovers;
    /** Export configuration. */
    private final boolean deleteLocalBook;
    /** Export configuration. */
    private final boolean incremental;

    @NonNull
    private final DateParser<LocalDateTime> dateParser;
    private final RealNumberParser realNumberParser;
    private final BookDao bookDao;
    private final CalibreDao calibreDao;
    private final CalibreLibraryDao calibreLibraryDao;
    private SyncWriterResults results;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param recordTypes     the record types to write
     * @param incremental     flag: if {@link CalibreLibrary#getLastSyncDateAsString()} should
     *                        be used to do an incremental write
     * @param deleteLocalBook flag: delete the local book if it no longer exists on the remote
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreContentServerWriter(@NonNull final Context context,
                                      @NonNull final Set<RecordType> recordTypes,
                                      final boolean incremental,
                                      final boolean deleteLocalBook)
            throws CertificateException {

        this.doCovers = recordTypes.contains(RecordType.Cover);
        this.incremental = incremental;
        this.deleteLocalBook = deleteLocalBook;

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        bookDao = serviceLocator.getBookDao();
        calibreDao = serviceLocator.getCalibreDao();
        calibreLibraryDao = serviceLocator.getCalibreLibraryDao();

        server = new CalibreContentServer.Builder(context).build();
        dateParser = new ISODateParser(serviceLocator.getSystemLocaleList().get(0));
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        realNumberParser = new RealNumberParser(allLocales);
    }

    @Override
    public void cancel() {
        server.cancel();
    }

    @WorkerThread
    @Override
    @NonNull
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
            server.readMetaData();
            for (final CalibreLibrary library : server.getLibraries()) {

                @Nullable
                final LocalDateTime dateSince;
                if (incremental) {
                    dateSince = dateParser.parse(library.getLastSyncDateAsString()).orElse(null);
                } else {
                    dateSince = null;
                }

                // sanity check, we only update existing books... no books -> skip library.
                if (library.getTotalBooks() > 0) {
                    syncLibrary(context, library, dateSince, progressListener);
                }
                // always set the sync date!
                library.setLastSyncDate(LocalDateTime.now(ZoneOffset.UTC));
                calibreLibraryDao.update(library);
            }
        } catch (@NonNull final JSONException | DaoWriteException e) {
            throw new DataWriterException(e);
        }
        return results;
    }

    private void syncLibrary(@NonNull final Context context,
                             @NonNull final CalibreLibrary library,
                             @Nullable final LocalDateTime dateSince,
                             @NonNull final ProgressListener progressListener)
            throws StorageException, IOException {
        try (Cursor cursor = bookDao.fetchBooksForExportToCalibre(library.getId(), dateSince)) {

            int delta = 0;
            long lastUpdate = 0;
            progressListener.setMaxPos(cursor.getCount());

            while (cursor.moveToNext() && !progressListener.isCancelled()) {
                final Book book = Book.from(cursor);
                try {
                    syncBook(context, library, book);

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

    private void syncBook(@NonNull final Context context,
                          @NonNull final CalibreLibrary library,
                          @NonNull final Book book)
            throws IOException, StorageException, JSONException {

        final int calibreId = book.getInt(DBKey.CALIBRE.BOOK_ID);
        final String calibreUuid = book.getString(DBKey.CALIBRE.BOOK_UUID);

        // ENHANCE: full sync in one go.
        //  The logic below is slow as we fetch each book individually
        //  but on a local/home network it's good enough
        final JSONObject calibreBook = server.getBook(library.getLibraryStringId(), calibreUuid);

        Optional<LocalDateTime> remoteDate = Optional.empty();
        if (!calibreBook.isNull(CalibreBookJsonKey.LAST_MODIFIED)) {
            try {
                final String dateStr = calibreBook.getString(CalibreBookJsonKey.LAST_MODIFIED);
                remoteDate = dateParser.parse(dateStr);
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }

        final Optional<LocalDateTime> localDate = book.getLastModified(dateParser);

        // Both should always be present, but paranoia...
        final boolean isNewer = localDate.isPresent() && remoteDate.isPresent()
                                // is our data newer then the server data ?
                                && localDate.get().isAfter(remoteDate.get());
        if (isNewer) {
            final JSONObject calibreBookIdentifiers =
                    calibreBook.optJSONObject(CalibreBookJsonKey.IDENTIFIERS);
            final JSONObject changes = collectChanges(context, library, calibreBookIdentifiers,
                                                      book);
            server.pushChanges(library.getLibraryStringId(), calibreId, changes);
            results.addBook(book.getId());
        }
    }

    /**
     * Transform a {@link Book} to a Calibre {@link JSONObject}.
     * <p>
     * Copy the wanted fields from the local {@link Book} into a {@link JSONObject}
     * with field names {@link CalibreBookJsonKey} as needed by Calibre.
     *
     * @param context                Current context
     * @param library                the library to which the given books belong
     * @param calibreBookIdentifiers the <strong>full</strong> list of identifiers for this
     *                               book as <strong>fetched from the Calibre server</strong>
     * @param localBook              the Book we're syncing
     *
     * @return the JSON data to send to the Calibre server
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException on any failure constructing JSON objects
     */
    @NonNull
    private JSONObject collectChanges(@NonNull final Context context,
                                      @NonNull final CalibreLibrary library,
                                      @Nullable final JSONObject calibreBookIdentifiers,
                                      @NonNull final Book localBook)
            throws JSONException, IOException {

        // Empty fields MUST be included to make the server remove the data.

        final JSONObject changes = new JSONObject();
        changes.put(CalibreBookJsonKey.TITLE,
                    localBook.getTitle());
        changes.put(CalibreBookJsonKey.DESCRIPTION,
                    localBook.getDescription());
        // we don't read this field, but we DO write it.
        changes.put(CalibreBookJsonKey.DATE_PUBLISHED,
                    localBook.getString(DBKey.PUBLICATION_DATE));
        changes.put(CalibreBookJsonKey.LAST_MODIFIED,
                    localBook.getString(DBKey.DATE_LAST_UPDATED__UTC));
        changes.put(CalibreBookJsonKey.RATING,
                    (int) localBook.getRating(realNumberParser));

        changes.put(CalibreBookJsonKey.AUTHOR_ARRAY,
                    collectAuthors(localBook));

        collectSeries(localBook, changes);

        changes.put(CalibreBookJsonKey.PUBLISHER, localBook.getPrimaryPublisher()
                                                           .map(Publisher::getName)
                                                           .orElse(""));

        changes.put(CalibreBookJsonKey.TAGS_ARRAY,
                    collectTags(localBook));

        changes.put(CalibreBookJsonKey.LANGUAGES_ARRAY,
                    collectLanguages(localBook));

        changes.put(CalibreBookJsonKey.IDENTIFIERS,
                    collectIdentifiers(calibreBookIdentifiers, localBook));

        collectCustomFields(library, localBook, changes);

        if (doCovers) {
            collectCovers(context, localBook, changes);
        }

        return changes;
    }

    @NonNull
    private JSONArray collectAuthors(@NonNull final Book localBook) {
        final JSONArray authors = new JSONArray();
        localBook.getAuthors()
                 .stream()
                 .map(author -> author.getFormattedName(true))
                 .forEach(authors::put);
        return authors;
    }

    private void collectSeries(@NonNull final Book localBook,
                               @NonNull final JSONObject changes) {
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

        changes.put(CalibreBookJsonKey.SERIES, seriesTitle);
        changes.put(CalibreBookJsonKey.SERIES_INDEX, number);
    }

    @NonNull
    private JSONArray collectTags(@NonNull final Book localBook) {
        return new JSONArray(localBook.getTags().stream().map(Tag::getName)
                                      .collect(Collectors.toList()));
    }

    @NonNull
    private JSONArray collectLanguages(@NonNull final Book localBook) {
        final JSONArray languages = new JSONArray();
        final String language = localBook.getLanguage();
        if (!language.isEmpty()) {
            languages.put(language);
        }
        return languages;
    }

    @Nullable
    private JSONObject collectIdentifiers(@Nullable final JSONObject calibreBookIdentifiers,
                                          @NonNull final Book localBook) {
        // The server expects a FULL set of identifiers. Any not present, will be deleted.
        // https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/write.py#L480
        // So, we send a combination of changes + the identifiers we don't know back to the server.

        // Collect all known local Identifiers
        final JSONObject localIdentifiers = new JSONObject();
        localBook.getIdentifiers().forEach(iv -> {
            // Map our key to the calibre key, or if not found, just use the key itself
            String calKey = CalibreContentServer.IDENTIFIER_MAPPING_WRITER.get(iv.getKey());
            if (calKey == null) {
                calKey = iv.getKey();
            }
            localIdentifiers.put(calKey, iv.getSid());
        });

        // add the ISBN which Calibre treats as just another identifier
        final String isbn = localBook.getIsbn();
        if (!isbn.isEmpty()) {
            localIdentifiers.put(CalibreContentServer.IDENTIFIER_ISBN, isbn);
        }

        // add the remotes, overwriting with locals as needed.
        if (!localIdentifiers.isEmpty()) {
            if (calibreBookIdentifiers == null) {
                // no remotes, just send all locals
                return localIdentifiers;
            } else {
                // overwrite remotes with locals
                final Iterator<String> it = localIdentifiers.keys();
                while (it.hasNext()) {
                    final String key = it.next();
                    calibreBookIdentifiers.put(key, localIdentifiers.get(key));
                }
            }
        }
        // send the combined set
        return calibreBookIdentifiers;
    }

    private void collectCustomFields(@NonNull final CalibreLibrary library,
                                     @NonNull final Book localBook,
                                     @NonNull final JSONObject changes) {
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
    }

    private void collectCovers(@NonNull final Context context,
                               @NonNull final Book localBook,
                               @NonNull final JSONObject changes)
            throws IOException {
        final Optional<File> coverFile = localBook.getImage(context, 0);
        if (coverFile.isPresent()) {
            final File file = coverFile.get();
            final byte[] bFile = new byte[(int) file.length()];
            try (FileInputStream is = new FileInputStream(file)) {
                //noinspection ResultOfMethodCallIgnored
                is.read(bFile);
            }
            changes.put(CalibreBookJsonKey.COVER, Base64.encodeToString(bFile, 0));
            results.addCover(file);

        } else {
            changes.put(CalibreBookJsonKey.COVER, "");
        }
    }

    @Override
    public void close() {
        new Purger().purge();
    }
}

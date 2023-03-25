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
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderHelper;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Import books from the <strong>given/single</strong> library.
 * <p>
 * If the user asked for "new and updated books" only,
 * the 'last-sync-date' from the library is used to only fetch books added/modified
 * later then this timestamp FROM THE SERVER.
 * <p>
 * Each remote book is compared to the local book 'last-modified' date to
 * decide to update it or not.
 *
 * <p>
 *
 * <ul>Supports custom columns:
 *     <li>read (boolean)</li>
 *     <li>read_start (datetime)</li>
 *     <li>read_end (datetime)</li>
 *     <li>date_read (datetime) -> read_end</li>
 *     <li>notes (text)</li>
 * </ul>
 *
 * <p>
 * Note we're not taking "books.pubdate"; most metadata downloaded by Calibre contains
 * bad/incorrect dates (at least the ones we've seen)
 * <p>
 * ENHANCE: tags... this would require implementing a full tag system in our own database.
 * => bookshelves 'are' tags? redefine the meaning of bookshelf as a 'tag'?
 * => and then define some tags as being shelves ?
 * <p>
 * ENHANCE: implement {@link SyncAction}.
 * For now overwrite/skip is a bit ad-hoc.
 */
public class CalibreContentServerReader
        implements DataReader<SyncReaderMetaData, ReaderResults> {

    public static final String SYNC_PROCESSOR_PREFIX =
            CalibreContentServer.PREF_KEY + ".fields.update.";
    /** Log tag. */
    private static final String TAG = "CalibreServerReader";
    private static final String BKEY_VIRTUAL_LIBRARY_LIST = TAG + ":vlibs";

    /** The number of books we fetch per request. Tested with CCS running on a RaspberryPi 1b+. */
    private static final int NUM = 10;
    /** error text for {@link #VALUE_IS_NULL}. */
    private static final String ERROR_NULL_STRING = "'null' string";

    /** A text "null" as value. Should be considered an error. */
    private static final String VALUE_IS_NULL = "null";

    @NonNull
    private final Updates updateOption;
    /**
     * If we want new-books-only {@link Updates#Skip}
     * or new-books-and-updates {@link Updates#OnlyNewer},
     * we limit the fetch to the sync-date.
     */
    @Nullable
    private final LocalDateTime syncDate;

    private final boolean doCovers;

    /** cached localized "eBooks" string. */
    @NonNull
    private final String eBookString;
    /** cached localized progress string. */
    @NonNull
    private final String booksString;

    @NonNull
    private final BookDao bookDao;
    @NonNull
    private final CalibreLibraryDao calibreLibraryDao;

    @NonNull
    private final CalibreContentServer server;
    @NonNull
    private final DateParser dateParser;

    /** The physical library from which we'll be importing. */
    @Nullable
    private CalibreLibrary library;
    private ReaderResults results;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     * @param helper       reader configuration
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreContentServerReader(@NonNull final Context context,
                                      @NonNull final Locale systemLocale,
                                      @NonNull final SyncReaderHelper helper)
            throws CertificateException {

        updateOption = helper.getUpdateOption();
        syncDate = helper.getSyncDate();

        //ENHANCE: add support for SyncProcessor

        doCovers = helper.getRecordTypes().contains(RecordType.Cover);
        library = helper.getExtraArgs().getParcelable(CalibreContentServer.BKEY_LIBRARY);

        server = new CalibreContentServer(context);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        bookDao = serviceLocator.getBookDao();
        calibreLibraryDao = serviceLocator.getCalibreLibraryDao();

        dateParser = new ISODateParser(systemLocale);

        eBookString = context.getString(R.string.book_format_ebook);
        booksString = context.getString(R.string.lbl_books);
    }

    // This needs thought/work....
//    @NonNull
//    public static SyncReaderProcessor getDefaultSyncProcessor() {
//        return new SyncReaderProcessor.Builder(SYNC_PROCESSOR_PREFIX)
//
//                .add(R.string.lbl_cover_front, DBKey.COVER_IS_USED[0])
//                .addRelatedField(DBKey.COVER_IS_USED[0], Book.BKEY_TMP_FILE_SPEC[0])
//
//                .add(R.string.lbl_cover_back, DBKey.COVER_IS_USED[1])
//                .addRelatedField(DBKey.COVER_IS_USED[1], Book.BKEY_TMP_FILE_SPEC[1])
//
//                .add(R.string.lbl_title, DBKey.KEY_TITLE,
//                     SyncAction.Overwrite)
//
//                .addList(R.string.lbl_authors, DBKey.FK_AUTHOR, Book.BKEY_AUTHOR_LIST)
//                .addList(R.string.lbl_series_multiple, DBKey.KEY_SERIES_TITLE,
//                         Book.BKEY_SERIES_LIST)
//
//                .add(R.string.lbl_description, DBKey.KEY_DESCRIPTION,
//                     SyncAction.Overwrite)
//
//                .addList(R.string.lbl_publishers, DBKey.KEY_PUBLISHER_NAME,
//                         Book.BKEY_PUBLISHER_LIST)
//                .add(R.string.lbl_date_published, DBKey.DATE_BOOK_PUBLICATION)
//
//
//
//                .add(R.string.site_calibre, DBKey.KEY_CALIBRE_BOOK_ID)
//                .addRelatedField(DBKey.KEY_CALIBRE_BOOK_UUID, DBKey.KEY_CALIBRE_BOOK_ID)
//
//                .add(R.string.lbl_date_last_updated, DBKey.UTC_DATE_LAST_UPDATED,
//                     SyncAction.Overwrite)
//
//
//
//                .add(R.string.lbl_format, DBKey.KEY_FORMAT)
//                .add(R.string.lbl_language, DBKey.KEY_LANGUAGE)
//
//                .add(R.string.lbl_rating, DBKey.KEY_RATING)
//
//
//                // CustomFields
//                .add(R.string.lbl_read, DBKey.BOOL_READ)
//                .add(R.string.lbl_read_start, DBKey.DATE_READ_START)
//                .add(R.string.lbl_read_end, DBKey.DATE_READ_END)
//                .add(R.string.lbl_personal_notes, DBKey.KEY_PRIVATE_NOTES)
//                .build();
//    }

    @Override
    public void cancel() {
        server.cancel();
    }

    private void readLibraryMetaData(@NonNull final Context context)
            throws StorageException,
                   IOException,
                   JSONException {

        server.readMetaData(context);
        if (library == null) {
            library = server.getDefaultLibrary();
        }
    }

    @NonNull
    @Override
    @WorkerThread
    public Optional<SyncReaderMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException,
                   StorageException,
                   IOException {

        try {
            readLibraryMetaData(context);
        } catch (@NonNull final JSONException e) {
            throw new DataReaderException(e);
        }

        final Bundle args = ServiceLocator.getInstance().newBundle();
        // the requested (or default) library
        args.putParcelable(CalibreContentServer.BKEY_LIBRARY, library);
        // and the full list
        args.putParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST, server.getLibraries());

        args.putBoolean(CalibreContentServer.BKEY_EXT_INSTALLED, server.isExtensionInstalled());

        return Optional.of(new SyncReaderMetaData(args));
    }

    @NonNull
    @Override
    @WorkerThread
    public ReaderResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   StorageException,
                   IOException {

        final String progressMessage =
                context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);

        results = new ReaderResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            // Always (re)read the meta data here.
            // Don't assume we still have the same instance as when readMetaData was called.
            readLibraryMetaData(context);

            //noinspection ConstantConditions
            final int totalNum = library.getTotalBooks();

            int num = 0;
            int offset = 0;
            boolean valid;

            String query = null;
            // If we want new-books-only (Updates.Skip)
            // or new-books-and-updates (Updates.OnlyNewer),
            // we limit the fetch to the sync-date. This speeds up the process.
            if (updateOption == DataReader.Updates.Skip
                || updateOption == DataReader.Updates.OnlyNewer) {

                // last_modified:">2021-01-15", so we do a "minusDays(1)" first
                // Due to rounding, we might get some books we don't need, but that's ok.
                if (syncDate != null) {
                    query = CalibreBook.LAST_MODIFIED + ":%22%3E"
                            + syncDate.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            + "%22";
                }
            }

            do {
                // Reminder: the NUM for this first call might seem very low,
                // but the full book data for each of the id's (max == NUM)
                // will be fetched in ONE GO in the second call further below.
                final JSONObject root;
                if (query == null) {
                    // all-books
                    root = server.getBookIds(library.getLibraryStringId(), NUM, offset);
                } else {
                    // search based on the last-sync-date
                    root = server.search(library.getLibraryStringId(), NUM, offset, query);
                }

                // assume valid result if at least the "total_num" param is there.
                valid = root.has(CalibreContentServer.RESPONSE_TAG_TOTAL_NUM);
                if (valid) {
                    // yes, we're reading/setting this on every iteration... less code.
                    progressListener.setMaxPos(root.getInt(
                            CalibreContentServer.RESPONSE_TAG_TOTAL_NUM));

                    num = root.getInt(CalibreContentServer.RESPONSE_TAG_NUM);
                    // the list of books (id only) returned by the server
                    final JSONArray bookIds = root.optJSONArray(
                            CalibreContentServer.RESPONSE_TAG_BOOK_IDS);

                    valid = bookIds != null && !bookIds.isEmpty();
                    if (valid) {
                        // with the above book-ids, get the full book objects
                        final JSONObject bookList = server.getBooks(library.getLibraryStringId(),
                                                                    bookIds);
                        final JSONObject bookListVirtualLibs =
                                server.getVirtualLibrariesForBooks(library.getLibraryStringId(),
                                                                   bookIds);

                        final Iterator<String> it = bookList.keys();
                        while (it.hasNext() && !progressListener.isCancelled()) {
                            final String key = it.next();
                            final JSONObject calibreBook = bookList.getJSONObject(key);

                            // inject the virtual library list into the main book object
                            if (bookListVirtualLibs != null) {
                                calibreBook.put(BKEY_VIRTUAL_LIBRARY_LIST,
                                                bookListVirtualLibs.getJSONArray(key));
                            }

                            handleBook(context, calibreBook);

                            results.booksProcessed++;
                            final String msg = String.format(progressMessage,
                                                             booksString,
                                                             results.booksCreated,
                                                             results.booksUpdated,
                                                             results.booksSkipped);
                            progressListener.publishProgress(1, msg);
                        }
                    }
                    offset += num;
                }
            } while (valid && num > 0 && totalNum > offset + num
                     && !progressListener.isCancelled());

        } catch (@NonNull final JSONException e) {
            throw new DataReaderException(e);
        }

        // always set the sync date!
        library.setLastSyncDate(LocalDateTime.now(ZoneOffset.UTC));

        calibreLibraryDao.update(library);

        return results;
    }

    /**
     * Process the book, and update the local data if allowed, or insert if not present.
     *
     * @param context     Current context
     * @param calibreBook the book data to import
     *
     * @throws StorageException The covers directory is not available
     */
    private void handleBook(@NonNull final Context context,
                            @NonNull final JSONObject calibreBook)
            throws StorageException, IOException {
        try {
            final String calibreUuid = calibreBook.getString(CalibreBook.UUID);
            // check if we already have the calibre book in the local database
            final long databaseBookId = calibreLibraryDao.getBookIdFromCalibreUuid(calibreUuid);
            if (databaseBookId > 0) {
                // yes, we do - handle the update according to the users choice
                switch (updateOption) {
                    case Overwrite: {
                        // Get the full local book data; overwrite it with remote data
                        // as needed, and update. We don't use a delta.
                        final Book book = Book.from(databaseBookId);
                        updateBook(context, calibreBook, book);
                        break;
                    }
                    case OnlyNewer: {
                        // Get the full local book data; overwrite it with remote data
                        // as needed, and update. We don't use a delta.
                        final Book book = Book.from(databaseBookId);
                        // Should always be Non Null
                        final LocalDateTime localDate = dateParser.parse(
                                book.getString(DBKey.DATE_LAST_UPDATED__UTC, null));
                        // Should not be null, but paranoia
                        final LocalDateTime remoteDate = dateParser.parse(
                                calibreBook.getString(CalibreBook.LAST_MODIFIED));

                        if (localDate != null && remoteDate != null
                            && remoteDate.isAfter(localDate)) {

                            updateBook(context, calibreBook, book);

                        } else {
                            results.booksFailed++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                Log.d(TAG, "calibreUuid=" + calibreBook.getString(CalibreBook.UUID)
                                           + "|" + updateOption
                                           + "|FAIL|book=" + book.getId() + "|" + book.getTitle());
                            }
                        }
                        break;
                    }
                    case Skip: {
                        results.booksSkipped++;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                            Log.d(TAG, "calibreUuid=" + calibreBook.getString(CalibreBook.UUID)
                                       + "|" + updateOption);
                        }
                        break;
                    }
                }
            } else {
                insertBook(context, calibreBook);
            }
        } catch (@NonNull final DaoWriteException | SQLiteDoneException | JSONException e) {
            // log, but don't fail
            LoggerFactory.getLogger().e(TAG, e);
            results.booksFailed++;
        }
    }

    private void updateBook(@NonNull final Context context,
                            @NonNull final JSONObject calibreBook,
                            @NonNull final Book book)
            throws StorageException, IOException, DaoWriteException {

        book.setStage(EntityStage.Stage.Dirty);
        copyCalibreData(context, calibreBook, book);

        bookDao.update(context, book, Set.of(BookDao.BookFlag.RunInBatch,
                                             BookDao.BookFlag.UseUpdateDateIfPresent));
        results.booksUpdated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
            Log.d(TAG, "calibreUuid=" + calibreBook.getString(CalibreBook.UUID)
                       + "|" + updateOption
                       + "|UPDATE|book=" + book.getId() + "|" + book.getTitle());
        }
    }

    private void insertBook(@NonNull final Context context,
                            @NonNull final JSONObject calibreBook)
            throws StorageException, IOException, DaoWriteException {

        // It's a new book; construct it using the calibre server data and insert it into the db
        final Book book = new Book();
        book.setStage(EntityStage.Stage.Dirty);
        // it's an eBook - duh!
        book.putString(DBKey.FORMAT, eBookString);
        copyCalibreData(context, calibreBook, book);
        // sanity check, the book should always/already be on the mapped shelf.
        book.ensureBookshelf(context);

        bookDao.insert(context, book, Set.of(BookDao.BookFlag.RunInBatch));
        results.booksCreated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
            Log.d(TAG, "calibreUuid=" + calibreBook.getString(CalibreBook.UUID)
                       + "|" + updateOption
                       + "|INSERT|book=" + book.getId() + "|" + book.getTitle());
        }
    }

    /**
     * Copy or add data from Calibre to the local Book object.
     *
     * @param context     Current context
     * @param calibreBook the book data to import
     * @param localBook   the local book to add the data to
     *
     * @throws StorageException The covers directory is not available
     * @throws JSONException    upon any parsing error
     */
    private void copyCalibreData(@NonNull final Context context,
                                 @NonNull final JSONObject calibreBook,
                                 @NonNull final Book localBook)
            throws StorageException, IOException, JSONException {

        final int calibreBookId = calibreBook.getInt(CalibreBook.ID);
        localBook.putInt(DBKey.CALIBRE_BOOK_ID, calibreBookId);
        localBook.putString(DBKey.CALIBRE_BOOK_UUID,
                            calibreBook.getString(CalibreBook.UUID));

        // "last_modified": "2020-11-20T11:17:51+00:00",
        // Note we copy the string as-is, and leave the normalisation to the book DAO routines.
        // Reminder: we already checked this date being newer then the local one before we got here
        localBook.putString(DBKey.DATE_LAST_UPDATED__UTC,
                            calibreBook.getString(CalibreBook.LAST_MODIFIED));

        // paranoia ...
        if (!calibreBook.isNull(CalibreBook.TITLE)) {
            // always overwrite
            localBook.putString(DBKey.TITLE, calibreBook.getString(CalibreBook.TITLE));
        }

        if (!calibreBook.isNull(CalibreBook.DESCRIPTION)) {
            // always overwrite
            localBook.putString(DBKey.DESCRIPTION,
                                calibreBook.getString(CalibreBook.DESCRIPTION));
        }

        if (!calibreBook.isNull(CalibreBook.RATING)) {
            final int rating = calibreBook.getInt(CalibreBook.RATING);
            // don't overwrite the local value with a remote 'not-set' value
            if (rating > 0) {
                localBook.putFloat(DBKey.RATING, rating);
            }
        }

        // "languages": [
        //      "eng"
        // ],
        if (!calibreBook.isNull(CalibreBook.LANGUAGES_ARRAY)) {
            final JSONArray languages = calibreBook.optJSONArray(CalibreBook.LANGUAGES_ARRAY);
            if (languages != null && !languages.isEmpty()) {
                // We only support one language, so grab the first one
                final String lang = languages.optString(0);
                // don't overwrite the local value with a remote 'not-set' value
                if (lang != null && !lang.isEmpty()) {
                    localBook.putString(DBKey.LANGUAGE, lang);
                }
            }
        }

        // "authors": [
        //      "Charles Stross"
        // ],
        final List<Author> bookAuthors = new ArrayList<>();
        if (!calibreBook.isNull(CalibreBook.AUTHOR_ARRAY)) {
            final JSONArray authors = calibreBook.optJSONArray(CalibreBook.AUTHOR_ARRAY);
            if (authors != null && !authors.isEmpty()) {

                for (int i = 0; i < authors.length(); i++) {
                    final String author = authors.optString(i);
                    if (author != null && !author.isEmpty()) {
                        bookAuthors.add(Author.from(author));
                    }
                }
            }
        }
        if (bookAuthors.isEmpty()) {
            bookAuthors.add(Author.createUnknownAuthor(context));
        }
        localBook.setAuthors(bookAuthors);

        // "series": null,
        // "series": "Argos Mythos / The devil is dead",
        if (!calibreBook.isNull(CalibreBook.SERIES)) {
            final String seriesName = calibreBook.optString(CalibreBook.SERIES);
            if (seriesName != null && !seriesName.isEmpty()) {
                if (VALUE_IS_NULL.equals(seriesName)) {
                    throw new IllegalArgumentException(ERROR_NULL_STRING);
                }
                final Series series = Series.from(seriesName);
                // "series_index": null,
                // "series_index": 2,  --> it's a float, but we grab it as a string
                String seriesNr = calibreBook.optString(CalibreBook.SERIES_INDEX);
                if (seriesNr != null && !seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                    // transform "3.0" to just "3" (and similar) but leave "3.1" alone
                    if (seriesNr.endsWith(".0")) {
                        seriesNr = seriesNr.substring(0, seriesNr.length() - 2);
                    }
                    series.setNumber(seriesNr);
                }
                final List<Series> bookSeries = new ArrayList<>();
                bookSeries.add(series);
                localBook.setSeries(bookSeries);
            }
        }

        if (!calibreBook.isNull(CalibreBook.PUBLISHER)) {
            final String publisherName = calibreBook.optString(CalibreBook.PUBLISHER);
            if (publisherName != null && !publisherName.isEmpty()) {
                if (VALUE_IS_NULL.equals(publisherName)) {
                    throw new IllegalArgumentException(ERROR_NULL_STRING);
                }

                final List<Publisher> bookPublishers = new ArrayList<>();
                bookPublishers.add(Publisher.from(publisherName));
                localBook.setPublishers(bookPublishers);
            }
        }

        if (!calibreBook.isNull(CalibreBook.IDENTIFIERS)) {
            final JSONObject remotes = calibreBook.optJSONObject(CalibreBook.IDENTIFIERS);
            if (remotes != null) {
                final Iterator<String> it = remotes.keys();
                while (it.hasNext()) {
                    final String key = it.next();
                    // always overwrite
                    if (!remotes.isNull(key)) {
                        final Identifier identifier = Identifier.MAP.get(key);
                        final String idStr = remotes.optString(key);
                        if (idStr != null && !idStr.isEmpty()) {
                            if (identifier != null) {
                                if (identifier.isLocalLong) {
                                    try {
                                        localBook.putLong(identifier.local,
                                                          Long.parseLong(idStr));
                                    } catch (@NonNull final NumberFormatException ignore) {
                                        // ignore
                                    }
                                } else {
                                    localBook.putString(identifier.local, idStr);
                                }

                            } else if (key.startsWith(Identifier.AMAZON)) {
                                // Other than strict "amazon", there are variants
                                // for local sites; e.g. "amazon_nl", "amazon_fr",...
                                // We always use the first one found.
                                if (!localBook.contains(DBKey.SID_ASIN)) {
                                    localBook.putString(DBKey.SID_ASIN, idStr);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (doCovers) {
            if (!calibreBook.isNull(CalibreBook.COVER)) {
                final String coverUrl = calibreBook.optString(CalibreBook.COVER);
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    final File file = server.getCover(calibreBookId, coverUrl)
                                            .orElse(null);
                    try {
                        localBook.setCover(0, file);
                    } catch (@NonNull final IOException ignore) {
                        // ignore
                    }
                }
            }
        }

        if (!calibreBook.isNull(CalibreBook.USER_METADATA)) {
            final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBook.USER_METADATA);
            if (userMetaData != null) {
                //noinspection ConstantConditions
                for (final CalibreCustomField cf : library.getCustomFields()) {
                    final JSONObject data = userMetaData.optJSONObject(cf.getCalibreKey());

                    // Sanity check, at this point it should always be true
                    if (data != null && cf.getType().equals(data.getString(
                            CalibreCustomField.METADATA_DATATYPE))) {

                        if (!data.isNull(CalibreCustomField.VALUE)) {
                            switch (cf.getType()) {
                                case CalibreCustomField.TYPE_BOOL: {
                                    // always overwrite
                                    localBook.putBoolean(cf.getDbKey(),
                                                         data.getBoolean(CalibreCustomField.VALUE));
                                    break;
                                }
                                case CalibreCustomField.TYPE_DATETIME:
                                case CalibreCustomField.TYPE_COMMENTS:
                                case CalibreCustomField.TYPE_TEXT: {
                                    final String value = data.getString(CalibreCustomField.VALUE);
                                    // don't overwrite the local value with a remote 'not-set' value
                                    if (!CalibreContentServer.VALUE_IS_NONE.equals(value)) {
                                        localBook.putString(cf.getDbKey(), value);
                                    }
                                    break;
                                }
                                default:
                                    throw new IllegalArgumentException(cf.getType());
                            }
                        }
                    }
                }
            }
        }

        if (!calibreBook.isNull(CalibreBook.EBOOK_FORMAT)) {
            final JSONObject mainFormat = calibreBook.optJSONObject(CalibreBook.EBOOK_FORMAT);
            if (mainFormat != null) {
                final Iterator<String> it = mainFormat.keys();
                if (it.hasNext()) {
                    final String format = it.next();
                    if (format != null && !format.isEmpty()) {
                        localBook.putString(DBKey.CALIBRE_BOOK_MAIN_FORMAT, format);
                    }
                }
            }
        }


        // Bookshelves / Calibre Library & Virtual libraries.

        // Always add the current library; i.e. the library the book came from.
        localBook.setCalibreLibrary(library);

        // Current list, will be empty for new books
        final List<Bookshelf> bookShelves = localBook.getBookshelves();

        // Add the physical library mapped Bookshelf
        //noinspection ConstantConditions
        final Bookshelf mappedBookshelf = Bookshelf.getBookshelf(context,
                                                                 library.getMappedBookshelfId(),
                                                                 Bookshelf.PREFERRED,
                                                                 Bookshelf.DEFAULT)
                                                   .orElseThrow();

        if (bookShelves.isEmpty()) {
            // new book
            bookShelves.add(mappedBookshelf);
        } else {
            // updating, check before adding
            if (bookShelves.stream()
                           .map(Bookshelf::getId)
                           .noneMatch(id -> id == mappedBookshelf.getId())) {
                bookShelves.add(mappedBookshelf);
            }
        }

        final JSONArray virtualLibs = calibreBook.optJSONArray(BKEY_VIRTUAL_LIBRARY_LIST);
        if (virtualLibs != null && !virtualLibs.isEmpty()) {
            for (int i = 0; i < virtualLibs.length(); i++) {
                final String name = virtualLibs.getString(i);

                // lookup the matching virtual library.
                library.getVirtualLibraries()
                       .stream()
                       .filter(vlib -> vlib.getName().equals(name))
                       .findFirst()
                       // it will always be present of course.
                       .ifPresent(vlib -> {
                           final Bookshelf vlibMappedBookshelf = Bookshelf
                                   .getBookshelf(context,
                                                 vlib.getMappedBookshelfId(),
                                                 library.getMappedBookshelfId())
                                   .orElseThrow();

                           // add the vlib mapped bookshelf if not already present.
                           if (bookShelves.stream()
                                          .map(Bookshelf::getId)
                                          .noneMatch(id -> id == vlibMappedBookshelf.getId())) {
                               bookShelves.add(vlibMappedBookshelf);
                           }
                       });
            }

            localBook.setBookshelves(bookShelves);
        }
    }

    @Override
    public void close() {
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }
}

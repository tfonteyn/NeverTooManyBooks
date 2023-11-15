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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
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
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
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
 * <p>
 * Supports custom columns:
 * <ul>
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

    private static final String SYNC_PROCESSOR_PREFIX =
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

    @NonNull
    private final BookDao bookDao;
    @NonNull
    private final CalibreLibraryDao calibreLibraryDao;

    @NonNull
    private final CalibreContentServer server;

    /** Which fields and how to process them for existing books. */
    @NonNull
    private final SyncReaderProcessor syncProcessor;

    /** Reused for each call to the {@link SyncReaderProcessor#process}. */
    private final RealNumberParser realNumberParser;

    @NonNull
    private final DateParser dateParser;

    /** The physical library from which we'll be importing. */
    @Nullable
    private CalibreLibrary library;
    private ReaderResults results;

    /**
     * Constructor.
     *
     * @param context       Current context
     * @param systemLocale  to use for ISO date parsing
     * @param updateOption  options
     * @param recordTypes   the record types to accept and read
     * @param syncProcessor synchronization configuration
     * @param syncDate      optional cut-off date
     * @param extraArgs     Bundle with reader specific arguments
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreContentServerReader(@NonNull final Context context,
                                      @NonNull final Locale systemLocale,
                                      @NonNull final DataReader.Updates updateOption,
                                      @NonNull final Set<RecordType> recordTypes,
                                      @Nullable final SyncReaderProcessor syncProcessor,
                                      @Nullable final LocalDateTime syncDate,
                                      @NonNull final Bundle extraArgs)
            throws CertificateException {

        this.updateOption = updateOption;
        this.syncDate = syncDate;

        // Get either the custom passed-in, or the builtin default.
        this.syncProcessor = syncProcessor != null ? syncProcessor
                                                   : getDefaultSyncProcessor(context);

        doCovers = recordTypes.contains(RecordType.Cover);
        library = extraArgs.getParcelable(CalibreContentServer.BKEY_LIBRARY);

        server = new CalibreContentServer.Builder(context).build();

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        bookDao = serviceLocator.getBookDao();
        calibreLibraryDao = serviceLocator.getCalibreLibraryDao();

        dateParser = new ISODateParser(systemLocale);

        final List<Locale> locales = LocaleListUtils.asList(context);
        realNumberParser = new RealNumberParser(locales);

        eBookString = context.getString(R.string.book_format_ebook);
    }

    /**
     * Get the default {@link SyncReaderProcessor}.
     * <p>
     * Simple fields are set to {@link SyncAction#CopyIfBlank}.
     * List fields are set to {@link SyncAction#Append}.
     *
     * @param context Current context
     *
     * @return a {@link SyncReaderProcessor}
     */
    @NonNull
    private static SyncReaderProcessor getDefaultSyncProcessor(@NonNull final Context context) {
        final SortedMap<String, String[]> map = new TreeMap<>();
        map.put(context.getString(R.string.site_calibre),
                new String[]{DBKey.CALIBRE_BOOK_ID});

        map.put(context.getString(R.string.lbl_date_last_updated),
                new String[]{DBKey.DATE_LAST_UPDATED__UTC});

        map.put(context.getString(R.string.lbl_cover_front),
                new String[]{DBKey.COVER[0]});
        map.put(context.getString(R.string.lbl_cover_back),
                new String[]{DBKey.COVER[1]});

        map.put(context.getString(R.string.lbl_title),
                new String[]{DBKey.TITLE});
        map.put(context.getString(R.string.lbl_authors),
                new String[]{DBKey.FK_AUTHOR, Book.BKEY_AUTHOR_LIST});
        map.put(context.getString(R.string.lbl_series_multiple),
                new String[]{DBKey.FK_SERIES, Book.BKEY_SERIES_LIST});
        map.put(context.getString(R.string.lbl_description),
                new String[]{DBKey.DESCRIPTION});
        map.put(context.getString(R.string.lbl_publishers),
                new String[]{DBKey.FK_PUBLISHER, Book.BKEY_PUBLISHER_LIST});

        map.put(context.getString(R.string.lbl_date_published),
                new String[]{DBKey.BOOK_PUBLICATION__DATE});

        map.put(context.getString(R.string.lbl_format),
                new String[]{DBKey.FORMAT});
        map.put(context.getString(R.string.lbl_language),
                new String[]{DBKey.LANGUAGE});
        map.put(context.getString(R.string.lbl_rating),
                new String[]{DBKey.RATING});

        // The site specific fields
        map.put(context.getString(R.string.lbl_ebook_file_type),
                new String[]{DBKey.CALIBRE_BOOK_MAIN_FORMAT});

        // The site specific CustomFields
        ServiceLocator.getInstance()
                      .getCalibreCustomFieldDao()
                      .getCustomFields().stream()
                      .map(CalibreCustomField::getDbKey)
                      .forEach(dbKey -> {
                          try {
                              map.put(MapDBKey.getLabel(context, dbKey), new String[]{dbKey});
                          } catch (@NonNull final IllegalArgumentException ignore) {
                              // will currently never fail, as all custom fields are
                              // hardcoded.
                          }
                      });

        final SyncReaderProcessor.Builder builder =
                new SyncReaderProcessor.Builder(context, SYNC_PROCESSOR_PREFIX);

        // add the sorted fields
        map.forEach((label, keys) -> builder.add(context, label, keys));

        builder.addRelatedField(DBKey.COVER[0], Book.BKEY_TMP_FILE_SPEC[0])
               .addRelatedField(DBKey.COVER[1], Book.BKEY_TMP_FILE_SPEC[1])
               .addRelatedField(DBKey.CALIBRE_BOOK_ID, DBKey.CALIBRE_BOOK_UUID);

        // The (locally sorted) external-id fields are added at the end of the list.
        builder.addSidFields(context);

        return builder.build();
    }

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
        args.putParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST,
                                    new ArrayList<>(server.getLibraries()));

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

        results = new ReaderResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgress(0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            // Always (re)read the meta data here.
            // Don't assume we still have the same instance as when readMetaData was called.
            readLibraryMetaData(context);

            //noinspection DataFlowIssue
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
                    query = CalibreBookJsonKey.LAST_MODIFIED + ":%22%3E"
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

                            handleBook(context, convert(context, calibreBook));

                            results.booksProcessed++;
                            // Due to the network access, we're not adding
                            // any additional interval/delay for each message
                            progressListener.publishProgress(
                                    1, results.createBooksSummaryLine(context));
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
     * @throws IOException      on generic/other IO failures
     */
    private void handleBook(@NonNull final Context context,
                            @NonNull final Book calibreBook)
            throws StorageException, IOException {
        try {
            final String calibreUuid = calibreBook.getString(DBKey.CALIBRE_BOOK_UUID);
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
                        final Optional<LocalDateTime> localDate = book.getLastModified(dateParser);
                        final Optional<LocalDateTime> remoteDate = calibreBook.getLastModified(
                                dateParser);

                        // Both should always be present, but paranoia...
                        final boolean isNewer = localDate.isPresent() && remoteDate.isPresent()
                                                // is the server data newer then our data ?
                                                && remoteDate.get().isAfter(localDate.get());
                        if (isNewer) {
                            updateBook(context, calibreBook, book);

                        } else {
                            results.booksSkipped++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                LoggerFactory.getLogger()
                                             .d(TAG, "handleBook", updateOption,
                                                "Skip",
                                                "calibreUuid=" + calibreBook.getString(
                                                        DBKey.CALIBRE_BOOK_UUID),
                                                "book=" + book.getId(),
                                                book.getTitle());
                            }
                        }
                        break;
                    }
                    case Skip: {
                        results.booksSkipped++;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                            LoggerFactory.getLogger()
                                         .d(TAG, "handleBook", updateOption,
                                            "Skip",
                                            "calibreUuid=" + calibreBook.getString(
                                                    DBKey.CALIBRE_BOOK_UUID),
                                            calibreBook.getString(CalibreBookJsonKey.TITLE));
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
                            @NonNull final Book calibreBook,
                            @NonNull final Book book)
            throws StorageException, IOException, DaoWriteException {

        // The delta values we'll be updating
        final Book delta;

        final Map<String, SyncField> fieldsWanted = syncProcessor.filter(book);

        // Extract the delta from the dataToMerge
        delta = syncProcessor.process(context, book.getId(), book,
                                      fieldsWanted, calibreBook,
                                      realNumberParser);

        if (delta != null) {
            bookDao.update(context, delta, Set.of(BookDao.BookFlag.RunInBatch,
                                                  BookDao.BookFlag.UseUpdateDateIfPresent));
            results.booksUpdated++;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                LoggerFactory.getLogger()
                             .d(TAG, "updateBook", updateOption,
                                "calibreUuid=" + calibreBook.getString(DBKey.CALIBRE_BOOK_UUID),
                                "book=" + book.getId(),
                                book.getTitle());
            }
        }
    }

    private void insertBook(@NonNull final Context context,
                            @NonNull final Book book)
            throws StorageException,
                   DaoWriteException {

        // it's an eBook - duh!
        book.putString(DBKey.FORMAT, eBookString);

        // sanity check, the book should always/already be on the mapped shelf.
        book.ensureBookshelf(context);

        bookDao.insert(context, book, Set.of(BookDao.BookFlag.RunInBatch));
        results.booksCreated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
            LoggerFactory.getLogger()
                         .d(TAG, "insertBook", updateOption,
                            "calibreUuid=" + book.getString(DBKey.CALIBRE_BOOK_UUID),
                            "book=" + book.getId(),
                            book.getTitle());
        }
    }

    /**
     * Convert the given JSON data to a {@link Book}.
     *
     * @param context     Current context
     * @param calibreBook to convert
     *
     * @return a Book
     *
     * @throws StorageException         The covers directory is not available
     * @throws JSONException            upon any parsing error
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if a value has unexpectedly the text "null".
     */
    private Book convert(@NonNull final Context context,
                         @NonNull final JSONObject calibreBook)
            throws IOException, StorageException {
        final Book book = new Book();
        book.setStage(EntityStage.Stage.Dirty);

        final int calibreBookId = calibreBook.getInt(CalibreBookJsonKey.ID);
        book.putInt(DBKey.CALIBRE_BOOK_ID, calibreBookId);
        book.putString(DBKey.CALIBRE_BOOK_UUID, calibreBook.getString(CalibreBookJsonKey.UUID));

        // Always add the current library; i.e. the library the book came from.
        book.setCalibreLibrary(library);

        // "last_modified": "2020-11-20T11:17:51+00:00",
        final String calibreLastModified = calibreBook.getString(CalibreBookJsonKey.LAST_MODIFIED);
        dateParser.parse(calibreLastModified).ifPresent(book::setLastModified);

        // paranoia ...
        if (!calibreBook.isNull(CalibreBookJsonKey.TITLE)) {
            // always overwrite
            book.putString(DBKey.TITLE,
                           calibreBook.getString(CalibreBookJsonKey.TITLE));
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.DESCRIPTION)) {
            // always overwrite
            book.putString(DBKey.DESCRIPTION,
                           calibreBook.getString(CalibreBookJsonKey.DESCRIPTION));
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.RATING)) {
            final int rating = calibreBook.getInt(CalibreBookJsonKey.RATING);
            // don't overwrite the local value with a remote 'not-set' value
            if (rating > 0) {
                book.putFloat(DBKey.RATING, rating);
            }
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.LANGUAGES_ARRAY)) {
            convertLanguages(calibreBook, book);
        }

        convertAuthors(context, calibreBook, book);

        if (!calibreBook.isNull(CalibreBookJsonKey.SERIES)) {
            convertSeries(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.PUBLISHER)) {
            convertPublisher(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.IDENTIFIERS)) {
            convertIdentifiers(calibreBook, book);
        }

        if (doCovers) {
            convertCovers(calibreBook, book, calibreBookId);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.USER_METADATA)) {
            convertCustomFields(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.EBOOK_FORMAT)) {
            convertFormat(calibreBook, book);
        }

        convertVirtualLibrariesToBookshelves(context, calibreBook, book);

        return book;
    }

    // "languages": [
    //      "eng"
    // ],
    private void convertLanguages(@NonNull final JSONObject calibreBook,
                                  @NonNull final Book book) {
        final JSONArray languages = calibreBook.optJSONArray(CalibreBookJsonKey.LANGUAGES_ARRAY);
        if (languages != null && !languages.isEmpty()) {
            // We only support one language, so grab the first one
            final String lang = languages.optString(0);
            if (lang != null && !lang.isEmpty()) {
                book.putString(DBKey.LANGUAGE, lang);
            }
        }
    }

    // "authors": [
    //      "Charles Stross"
    // ],
    private void convertAuthors(@NonNull final Context context,
                                @NonNull final JSONObject calibreBook,
                                @NonNull final Book book) {
        final List<Author> bookAuthors = new ArrayList<>();
        if (!calibreBook.isNull(CalibreBookJsonKey.AUTHOR_ARRAY)) {
            final JSONArray authors = calibreBook.optJSONArray(CalibreBookJsonKey.AUTHOR_ARRAY);
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
        book.setAuthors(bookAuthors);
    }

    // "series": null,
    // "series": "Argos Mythos / The devil is dead",
    private void convertSeries(@NonNull final JSONObject calibreBook,
                               @NonNull final Book book) {
        final String seriesName = calibreBook.optString(CalibreBookJsonKey.SERIES);
        if (seriesName != null && !seriesName.isEmpty()) {
            if (VALUE_IS_NULL.equals(seriesName)) {
                throw new IllegalArgumentException(ERROR_NULL_STRING);
            }
            final Series series = Series.from(seriesName);
            // "series_index": null,
            // "series_index": 2,  --> it's a float, but we grab it as a string
            String seriesNr = calibreBook.optString(CalibreBookJsonKey.SERIES_INDEX);
            if (seriesNr != null && !seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                // transform "3.0" to just "3" (and similar) but leave "3.1" alone
                if (seriesNr.endsWith(".0")) {
                    seriesNr = seriesNr.substring(0, seriesNr.length() - 2);
                }
                series.setNumber(seriesNr);
            }
            final List<Series> bookSeries = new ArrayList<>();
            bookSeries.add(series);
            book.setSeries(bookSeries);
        }
    }

    private void convertPublisher(@NonNull final JSONObject calibreBook,
                                  @NonNull final Book book) {
        final String publisherName = calibreBook.optString(CalibreBookJsonKey.PUBLISHER);
        if (publisherName != null && !publisherName.isEmpty()) {
            if (VALUE_IS_NULL.equals(publisherName)) {
                throw new IllegalArgumentException(ERROR_NULL_STRING);
            }

            final List<Publisher> bookPublishers = new ArrayList<>();
            bookPublishers.add(Publisher.from(publisherName));
            book.setPublishers(bookPublishers);
        }
    }

    private void convertIdentifiers(@NonNull final JSONObject calibreBook,
                                    @NonNull final Book book) {
        final JSONObject remotes = calibreBook.optJSONObject(CalibreBookJsonKey.IDENTIFIERS);
        if (remotes != null) {
            final Iterator<String> it = remotes.keys();
            while (it.hasNext()) {
                final String key = it.next();
                if (!remotes.isNull(key)) {
                    final Identifier identifier = Identifier.MAP.get(key);
                    final String idStr = remotes.optString(key);
                    if (idStr != null && !idStr.isEmpty()) {
                        if (identifier != null) {
                            if (identifier.isLocalLong) {
                                try {
                                    book.putLong(identifier.local,
                                                 Long.parseLong(idStr));
                                } catch (@NonNull final NumberFormatException ignore) {
                                    // ignore
                                }
                            } else {
                                book.putString(identifier.local, idStr);
                            }

                        } else if (key.startsWith(Identifier.AMAZON)) {
                            // Other than strict "amazon", there are variants
                            // for local sites; e.g. "amazon_nl", "amazon_fr",...
                            // We always use the first one found.
                            if (!book.contains(DBKey.SID_ASIN)) {
                                book.putString(DBKey.SID_ASIN, idStr);
                            }
                        }
                    }
                }
            }
        }
    }

    private void convertCustomFields(@NonNull final JSONObject calibreBook,
                                     @NonNull final Book book) {
        final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBookJsonKey.USER_METADATA);
        if (userMetaData != null && library != null) {
            for (final CalibreCustomField cf : library.getCustomFields()) {
                final JSONObject data = userMetaData.optJSONObject(cf.getCalibreKey());

                // Sanity check, at this point it should always be true
                if (data != null && cf.getType().equals(data.getString(
                        CalibreCustomField.METADATA_DATATYPE))) {

                    if (!data.isNull(CalibreCustomField.VALUE)) {
                        switch (cf.getType()) {
                            case CalibreCustomField.TYPE_BOOL: {
                                // always overwrite
                                book.putBoolean(cf.getDbKey(),
                                                data.getBoolean(CalibreCustomField.VALUE));
                                break;
                            }
                            case CalibreCustomField.TYPE_DATETIME:
                            case CalibreCustomField.TYPE_COMMENTS:
                            case CalibreCustomField.TYPE_TEXT: {
                                final String value = data.getString(CalibreCustomField.VALUE);
                                // don't overwrite the local value with a remote 'not-set' value
                                if (!CalibreContentServer.VALUE_IS_NONE.equals(value)) {
                                    book.putString(cf.getDbKey(), value);
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

    private void convertFormat(@NonNull final JSONObject calibreBook,
                               @NonNull final Book book) {
        final JSONObject mainFormat = calibreBook.optJSONObject(CalibreBookJsonKey.EBOOK_FORMAT);
        if (mainFormat != null) {
            final Iterator<String> it = mainFormat.keys();
            if (it.hasNext()) {
                final String format = it.next();
                if (format != null && !format.isEmpty()) {
                    book.putString(DBKey.CALIBRE_BOOK_MAIN_FORMAT, format);
                }
            }
        }
    }

    private void convertVirtualLibrariesToBookshelves(@NonNull final Context context,
                                                      @NonNull final JSONObject calibreBook,
                                                      @NonNull final Book book) {
        // Current list, will be empty for new books
        final List<Bookshelf> bookShelves = book.getBookshelves();

        // Add the physical library mapped Bookshelf
        //noinspection DataFlowIssue
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

            book.setBookshelves(bookShelves);
        }
    }

    private void convertCovers(@NonNull final JSONObject calibreBook,
                               @NonNull final Book book,
                               final int calibreBookId)
            throws StorageException, IOException {
        if (!calibreBook.isNull(CalibreBookJsonKey.COVER)) {
            final String coverUrl = calibreBook.optString(CalibreBookJsonKey.COVER);
            if (coverUrl != null && !coverUrl.isEmpty()) {
                final File file = server.getCover(calibreBookId, coverUrl)
                                        .orElse(null);
                try {
                    book.setCover(0, file);
                } catch (@NonNull final IOException ignore) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void close() {
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }
}

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
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Dev Note: while most plumbing to use multiple libraries is in place,
 * right now we only support the default library.
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
 * ENHANCE: implement {@link com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage}.
 * For now overwrite/skip is a bit ad-hoc.
 */
public class CalibreContentServerReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CalibreServerReader";

    /** Custom field for {@link ArchiveMetaData}. */
    public static final String ARCH_MD_DEFAULT_LIBRARY = TAG + ":defLibId";
    /** Custom field for {@link ArchiveMetaData}. */
    public static final String ARCH_MD_LIBRARY_LIST = TAG + ":libs";

    /** The number of books we fetch per request. Tested with CCS running on a RaspberryPi 1b+. */
    private static final int NUM = 10;
    /** error text for {@link #VALUE_IS_NULL}. */
    private static final String ERROR_NULL_STRING = "'null' string";

    /** A text "null" as value. Should be considered an error. */
    private static final String VALUE_IS_NULL = "null";
    /** A text "None" as value. Can/will be seen. This is the python equivalent of {@code null}. */
    private static final String VALUE_IS_NONE = "None";
    /** Key is the remote (Calibre) identifier. */
    private static final Map<String, Identifier> IDENTIFIER_MAP = new HashMap<>();

    static {
        Identifier identifier;

        identifier = new Identifier("isbn", DBDefinitions.KEY_ISBN, false);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("openlibrary", DBDefinitions.KEY_ESID_OPEN_LIBRARY, false);
        IDENTIFIER_MAP.put(identifier.remote, identifier);

        identifier = new Identifier("goodreads", DBDefinitions.KEY_ESID_GOODREADS_BOOK, true);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("isfdb", DBDefinitions.KEY_ESID_ISFDB, true);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("librarything", DBDefinitions.KEY_ESID_LIBRARY_THING, true);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("stripinfo", DBDefinitions.KEY_ESID_STRIP_INFO_BE, true);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("lastdodo", DBDefinitions.KEY_ESID_LAST_DODO_NL, true);
        IDENTIFIER_MAP.put(identifier.remote, identifier);


        identifier = new Identifier("amazon", DBDefinitions.KEY_ESID_ASIN, false);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("google", DBDefinitions.KEY_ESID_GOOGLE, false);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("oclc", DBDefinitions.KEY_ESID_WORLDCAT, false);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
        identifier = new Identifier("lccn", DBDefinitions.KEY_ESID_LCCN, false);
        IDENTIFIER_MAP.put(identifier.remote, identifier);
    }

    @NonNull
    private final CalibreContentServer mServer;
    @NonNull
    private final DAO mDb;
    private final boolean mDoNewAndUpdatedBooks;
    private final boolean mDoAllBooks;
    private final boolean mFetchCovers;
    /** The default Bookshelf to which all new books will be added. */
    private final Bookshelf mBookshelf;
    /** cached localized "eBooks" string. */
    @NonNull
    private final String mEBookString;
    /** cached localized progress string. */
    @NonNull
    private final String mProgressMessage;
    /** cached localized progress string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mLibraryId;
    private ImportResults mResults;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @throws IOException          on failures
     * @throws CertificateException on failures related to the user installed CA.
     */
    public CalibreContentServerReader(@NonNull final Context context,
                                      @NonNull final ImportHelper helper)
            throws GeneralParsingException, IOException, CertificateException {

        mDb = new DAO(TAG);

        mDoNewAndUpdatedBooks = helper.isNewAndUpdatedBooks();
        mDoAllBooks = helper.isAllBooks();
        mFetchCovers = helper.getImportEntries().contains(RecordType.Cover);

        mServer = new CalibreContentServer(context, helper.getUri());
        mLibraryId = mServer.loadLibraries();

        mEBookString = context.getString(R.string.book_format_ebook);
        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);

        mBookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.DEFAULT);
    }

    @Nullable
    @Override
    @WorkerThread
    public ArchiveMetaData readMetaData(@NonNull final Context context)
            throws GeneralParsingException, IOException {

        final ArchiveMetaData archiveMetaData = new ArchiveMetaData();

        try {
            mServer.readMetaData(mLibraryId);

        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        archiveMetaData.setBookCount(mServer.getTotalBooks(mLibraryId));
        final Bundle bundle = archiveMetaData.getBundle();
        bundle.putParcelable(ARCH_MD_DEFAULT_LIBRARY, mServer.getLibrary(mLibraryId));
        bundle.putParcelableArrayList(ARCH_MD_LIBRARY_LIST,
                                      new ArrayList<>(mServer.getLibraries().values()));
        return archiveMetaData;
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws GeneralParsingException, IOException {

        mResults = new ImportResults();

        progressListener.setIndeterminate(true);
        progressListener.publishProgressStep(
                0, context.getString(R.string.progress_msg_connecting));
        // reset; won't take effect until the next publish call.
        progressListener.setIndeterminate(null);

        try {
            // Always read the meta data here.
            // Don't assume we still have the same instance as when readMetaData was called.
            mServer.readMetaData(mLibraryId);
            final int totalNum = mServer.getTotalBooks(mLibraryId);

            int num = 0;
            int offset = 0;
            boolean valid;

            do {
                //URGENT: we always read all books.... should limit to the ones we need
                // i.e. new books only / new and updated / all books
                // based on the last sync date: need to look at the ajax search query syntax
                final JSONObject root = mServer.getBookIds(mLibraryId, NUM, offset);
                // assume valid result if at least the "total_num" param is there.
                valid = root.has("total_num");
                if (valid) {
                    progressListener.setMaxPos(mServer.getTotalBooks(mLibraryId));
                    num = root.getInt("num");

                    final JSONArray bookIds = root.optJSONArray("book_ids");
                    valid = bookIds != null && bookIds.length() > 0;
                    if (valid) {
                        final JSONObject bookList = mServer.getBooks(mLibraryId, bookIds);

                        final Iterator<String> it = bookList.keys();
                        while (it.hasNext() && !progressListener.isCancelled()) {
                            handleBook(context, bookList.getJSONObject(it.next()));
                            mResults.booksProcessed++;

                            final String msg = String.format(mProgressMessage,
                                                             mBooksString,
                                                             mResults.booksCreated,
                                                             mResults.booksUpdated,
                                                             mResults.booksSkipped);
                            progressListener.publishProgressStep(1, msg);
                        }
                    }
                    offset += num;
                }
            } while (valid && num > 0 && (totalNum > offset + num)
                     && !progressListener.isCancelled());

        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        // always set the sync date!
        CalibreContentServer.setLastSyncDate(context, LocalDateTime.now(ZoneOffset.UTC));

        return mResults;
    }

    /**
     * Process the book, and update the local data if allowed, or insert if not present.
     *
     * @param context     Current context
     * @param calibreBook the book data to import
     */
    private void handleBook(@NonNull final Context context,
                            @NonNull final JSONObject calibreBook) {
        try {
            final String calibreUuid = calibreBook.getString(CalibreBook.UUID);
            // check if the book exists in our database, and fetch it's id.
            final long bookId = mDb.getBookIdFromCalibreUuid(calibreUuid);
            if (bookId > 0) {
                final Book book = Book.from(bookId, mDb);
                // UPDATE the existing book (if allowed). Check the sync option FIRST!
                if ((mDoNewAndUpdatedBooks
                     && isImportNewer(context, bookId, book.getLastUpdateUtcDate(context)))
                    ||
                    mDoAllBooks) {
                    book.setStage(EntityStage.Stage.Dirty);
                    copyCalibreData(context, calibreBook, book);

                    mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                              | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);

                    mResults.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                        Log.d(TAG, "calibreUuid=" + calibreUuid
                                   + "|databaseBookId=" + bookId
                                   + "|update|" + book.getTitle());
                    }

                } else {
                    mResults.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                        Log.d(TAG, "calibreUuid=" + calibreUuid
                                   + "|databaseBookId=" + bookId
                                   + "|skipped|" + book.getTitle());
                    }
                }
            } else {
                final Book book = new Book();
                book.setStage(EntityStage.Stage.Dirty);
                // assign to current shelf.
                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST).add(mBookshelf);
                // it's an eBook - duh!
                book.putString(DBDefinitions.KEY_FORMAT, mEBookString);
                copyCalibreData(context, calibreBook, book);

                final long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION);
                mResults.booksCreated++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                    Log.d(TAG, "calibreUuid=" + calibreUuid
                               + "|insert=" + insId
                               + "|" + book.getTitle());
                }
            }

        } catch (@NonNull final DAO.DaoWriteException | SQLiteDoneException | JSONException e) {
            // log, but don't fail
            Logger.error(context, TAG, e);
            mResults.booksSkipped++;
        }
    }

    /**
     * Copy or add data from Calibre to the local Book object.
     *
     * @param context     Current context
     * @param calibreBook the book data to import
     * @param localBook   the local book to add the data to
     *
     * @throws JSONException upon any error
     */
    private void copyCalibreData(@NonNull final Context context,
                                 @NonNull final JSONObject calibreBook,
                                 @NonNull final Book localBook)
            throws JSONException {

        localBook.putString(DBDefinitions.KEY_CALIBRE_BOOK_LIBRARY_ID, mLibraryId);

        final int calibreBookId = calibreBook.getInt(CalibreBook.ID);
        localBook.putInt(DBDefinitions.KEY_CALIBRE_BOOK_ID, calibreBookId);

        localBook.putString(DBDefinitions.KEY_CALIBRE_BOOK_UUID,
                            calibreBook.getString(CalibreBook.UUID));

        // "last_modified": "2020-11-20T11:17:51+00:00",
        // Note we copy the string as-is, and leave the normalisation to the book DAO routines.
        // Reminder: we already checked this date being newer then the local one before we got here
        localBook.putString(DBDefinitions.KEY_UTC_LAST_UPDATED,
                            calibreBook.getString(CalibreBook.LAST_MODIFIED));

        // paranoia ...
        if (!calibreBook.isNull(CalibreBook.TITLE)) {
            // always overwrite
            localBook.putString(DBDefinitions.KEY_TITLE,
                                calibreBook.getString(CalibreBook.TITLE));
        }

        if (!calibreBook.isNull(CalibreBook.DESCRIPTION)) {
            // always overwrite
            localBook.putString(DBDefinitions.KEY_DESCRIPTION,
                                calibreBook.getString(CalibreBook.DESCRIPTION));
        }

        if (!calibreBook.isNull(CalibreBook.RATING)) {
            final int rating = calibreBook.getInt(CalibreBook.RATING);
            // don't overwrite the local value with a remote 'not-set' value
            if (rating > 0) {
                localBook.putDouble(DBDefinitions.KEY_RATING, rating);
            }
        }

        // "languages": [
        //      "eng"
        // ],
        if (!calibreBook.isNull(CalibreBook.LANGUAGES_ARRAY)) {
            final JSONArray languages = calibreBook.optJSONArray(CalibreBook.LANGUAGES_ARRAY);
            if (languages != null && languages.length() > 0) {
                // We only support one language, so grab the first one
                final String lang = languages.optString(0);
                // don't overwrite the local value with a remote 'not-set' value
                if (lang != null && !lang.isEmpty()) {
                    localBook.putString(DBDefinitions.KEY_LANGUAGE, lang);
                }
            }
        }

        // "authors": [
        //      "Charles Stross"
        // ],
        final ArrayList<Author> bookAuthors = new ArrayList<>();
        if (!calibreBook.isNull(CalibreBook.AUTHOR_ARRAY)) {
            final JSONArray authors = calibreBook.optJSONArray(CalibreBook.AUTHOR_ARRAY);
            if (authors != null && authors.length() > 0) {

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
        // Always overwrite
        localBook.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, bookAuthors);

        // "series": null,
        // "series": "Argos Mythos / The devil is dead",
        if (!calibreBook.isNull(CalibreBook.SERIES)) {
            final String seriesName = calibreBook.optString(CalibreBook.SERIES);
            if (!seriesName.isEmpty()) {
                if (VALUE_IS_NULL.equals(seriesName)) {
                    throw new IllegalArgumentException(ERROR_NULL_STRING);
                }
                final Series series = Series.from(seriesName);
                // "series_index": null,
                // "series_index": 2,  --> it's a float, but we grab it as a string
                final String seriesNr = calibreBook.optString(CalibreBook.SERIES_INDEX);
                if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                    series.setNumber(seriesNr);
                }
                final ArrayList<Series> bookSeries = new ArrayList<>();
                bookSeries.add(series);
                // always overwrite
                localBook.putParcelableArrayList(Book.BKEY_SERIES_LIST, bookSeries);
            }
        }

        if (!calibreBook.isNull(CalibreBook.PUBLISHER)) {
            final String publisherName = calibreBook.optString(CalibreBook.PUBLISHER);
            if (!publisherName.isEmpty()) {
                if (VALUE_IS_NULL.equals(publisherName)) {
                    throw new IllegalArgumentException(ERROR_NULL_STRING);
                }

                final ArrayList<Publisher> bookPublishers = new ArrayList<>();
                bookPublishers.add(Publisher.from(publisherName));
                // always overwrite
                localBook.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, bookPublishers);
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
                        final Identifier identifier = IDENTIFIER_MAP.get(key);
                        if (identifier != null) {
                            if (identifier.isLocalLong) {
                                try {
                                    localBook.putLong(identifier.local,
                                                      Long.parseLong(remotes.optString(key)));
                                } catch (@NonNull final NumberFormatException ignore) {
                                    // ignore
                                }
                            } else {
                                localBook.putString(identifier.local, remotes.optString(key));
                            }

                        } else if (key.startsWith("amazon")) {
                            // Other than strict "amazon", there are variants
                            // for local sites; e.g. "amazon_nl", "amazon_fr",...
                            // Note if there is more then one, we end up with the 'last' one.
                            localBook.putString(DBDefinitions.KEY_ESID_ASIN,
                                                remotes.optString(key));
                        }
                    }
                }
            }
        }

        if (mFetchCovers) {
            if (!calibreBook.isNull(CalibreBook.COVER)) {
                final String coverUrl = calibreBook.optString(CalibreBook.COVER);
                if (!coverUrl.isEmpty()) {
                    final File file = mServer.getCover(context, calibreBookId, coverUrl);
                    localBook.setCover(context, mDb, 0, file);
                }
            }
        }

        if (!calibreBook.isNull(CalibreBook.USER_METADATA)) {
            final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBook.USER_METADATA);
            if (userMetaData != null) {
                for (final CustomFields.Field cf : mServer.getCustomFields()) {
                    final JSONObject data = userMetaData.optJSONObject(cf.calibreKey);

                    // Sanity check, at this point it should always be true
                    if (data != null && cf.type.equals(data.getString(
                            CustomFields.METADATA_DATATYPE))) {

                        if (!data.isNull(CustomFields.VALUE)) {
                            switch (cf.type) {
                                case CustomFields.TYPE_BOOL: {
                                    // always overwrite
                                    localBook.putBoolean(cf.dbKey,
                                                         data.getBoolean(CustomFields.VALUE));
                                    break;
                                }
                                case CustomFields.TYPE_DATETIME:
                                case CustomFields.TYPE_COMMENTS:
                                case CustomFields.TYPE_TEXT: {
                                    final String value = data.getString(CustomFields.VALUE);
                                    // don't overwrite the local value with a remote 'not-set' value
                                    if (!VALUE_IS_NONE.equals(value)) {
                                        localBook.putString(cf.dbKey, value);
                                    }
                                    break;
                                }
                                default:
                                    throw new IllegalArgumentException(cf.type);
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
                        localBook.putString(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT, format);
                    }
                }
            }
        }
    }

    /**
     * Check if the incoming book is newer than the stored book data.
     *
     * @param context              Current context
     * @param bookId               the local book id to lookup in our database
     * @param importLastUpdateDate to check
     *
     * @return {@code true} if the imported data is newer then the local data.
     */
    private boolean isImportNewer(@NonNull final Context context,
                                  @IntRange(from = 1) final long bookId,
                                  @Nullable final LocalDateTime importLastUpdateDate) {
        if (importLastUpdateDate == null) {
            return false;
        }

        final LocalDateTime utcLastUpdated = mDb.getBookLastUpdateUtcDate(context, bookId);
        return utcLastUpdated == null || importLastUpdateDate.isAfter(utcLastUpdated);
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }

    public static class Identifier {

        @NonNull
        public final String remote;

        @NonNull
        public final String local;

        public final boolean isLocalLong;

        public Identifier(@NonNull final String remote,
                          @NonNull final String local,
                          final boolean isLocalLong) {
            this.remote = remote;
            this.local = local;
            this.isLocalLong = isLocalLong;
        }
    }
}

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
import java.util.ArrayList;
import java.util.Iterator;

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
 * TODO: read the list of libs and prompt the user, BEFORE starting a sync
 *
 * <ul>Calibre specific fields:
 *     <li>book id</li>
 *     <li>book uuid</li>
 *     <li>book library</li>
 *     <li>book file URL for the default format</li>
 * </ul>
 *
 * <ul>Standard fields:
 *     <li>cover</li>
 *     <li>title</li>
 *     <li>authors</li>
 *     <li>series</li>
 *     <li>publisher</li>
 *     <li>comments -> description</li>
 *     <li>last_modified</li>
 *     <li>languages -> single language</li>
 *     <li>isbn</li>
 *     <li>amazon asin</li>
 *     <li>goodreads id</li>
 *     <li>google id</li>
 * </ul>
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
 * Not copying "ratings" either. The source is rather unclear. Amazon or Goodreads? or?
 * <p>
 * ENHANCE: tags... this would require implementing a full tag system in our own database.
 * => bookshelves 'are' tags? redefine the meaning of bookshelf as a 'tag'?
 * => and then define some tags as being shelves ?
 */
public class CalibreContentServerReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CalibreCSReader";

    /** Custom field for {@link ArchiveMetaData}. */
    public static final String ARCH_MD_DEFAULT_LIBRARY = TAG + ":defLibId";
    /** Custom field for {@link ArchiveMetaData}. */
    public static final String ARCH_MD_LIBRARY_LIST = TAG + ":libs";

    /** The number of books we fetch per request. Tested with CCS running on a RaspberryPi 1b+. */
    private static final int NUM = 10;
    @NonNull
    private final CalibreContentServer mServer;

    @NonNull
    private final DAO mDb;

    private final boolean mDoNewAndUpdatedBooks;
    private final boolean mDoAllBooks;

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
    private final boolean mFetchCovers;
    private ImportResults mResults;

    @NonNull
    private String mLibraryId;

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

    // ENHANCE: allow passing in of the DESIRED library to use.
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

        try {
            // Always read them here as well. Don't assume we still have the same instance
            mServer.readMetaData(mLibraryId);
            final int totalNum = mServer.getTotalBooks(mLibraryId);

            int num = 0;
            int offset = 0;
            boolean valid;

            do {
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
            final String calibreUuid = calibreBook.getString("uuid");
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

        } catch (@NonNull final DAO.DaoWriteException | SQLiteDoneException
                | JSONException e) {
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
     * @param book        the local book to add the data to
     *
     * @throws JSONException upon any error
     */
    private void copyCalibreData(@NonNull final Context context,
                                 @NonNull final JSONObject calibreBook,
                                 @NonNull final Book book)
            throws JSONException {

        book.putString(DBDefinitions.KEY_CALIBRE_BOOK_LIBRARY_ID, mLibraryId);

        // "application_id": 6,
        final int calibreBookId = calibreBook.getInt("application_id");
        book.putInt(DBDefinitions.KEY_CALIBRE_BOOK_ID, calibreBookId);
        // "uuid": "4ec36562-d8e8-4499-9c6c-d1e7ae2af42f",
        book.putString(DBDefinitions.KEY_CALIBRE_BOOK_UUID, calibreBook.getString("uuid"));

        // "last_modified": "2020-11-20T11:17:51+00:00",
        // Reminder: we already checked this date being newer then the local one before we got here
        book.putString(DBDefinitions.KEY_UTC_LAST_UPDATED, calibreBook.getString("last_modified"));

        // paranoia ...
        if (!calibreBook.isNull("title")) {
            // "title": "Accelerando",
            book.putString(DBDefinitions.KEY_TITLE, calibreBook.getString("title"));
        }

        if (!calibreBook.isNull("comments")) {
            // "comments": "<div>\n<p>The Singularity. blah blah...",
            book.putString(DBDefinitions.KEY_DESCRIPTION, calibreBook.getString("comments"));
        }

        // "languages": [
        //      "eng"
        // ],
        if (!calibreBook.isNull("languages")) {
            final JSONArray languages = calibreBook.optJSONArray("languages");
            if (languages != null && languages.length() > 0) {
                // We only support one language, so grab the first one
                final String lang = languages.optString(0);
                if (lang != null && !lang.isEmpty()) {
                    book.putString(DBDefinitions.KEY_LANGUAGE, lang);
                }
            }
        }

        // "authors": [
        //      "Charles Stross"
        // ],
        final ArrayList<Author> bookAuthors = new ArrayList<>();
        if (!calibreBook.isNull("authors")) {
            final JSONArray authors = calibreBook.optJSONArray("authors");
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
        book.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, bookAuthors);


        // "series": null,
        // "series": "Argos Mythos / The devil is dead",
        if (!calibreBook.isNull("series")) {
            final String seriesName = calibreBook.optString("series");
            if (!seriesName.isEmpty()) {
                if ("null".equals(seriesName)) {
                    throw new IllegalArgumentException("BLEEP");
                }
                final Series series = Series.from(seriesName);
                // "series_index": null,
                // "series_index": 2,  --> it's a float, but we grab it as a string
                final String seriesNr = calibreBook.optString("series_index");
                if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                    series.setNumber(seriesNr);
                }
                final ArrayList<Series> bookSeries = new ArrayList<>();
                bookSeries.add(series);
                book.putParcelableArrayList(Book.BKEY_SERIES_LIST, bookSeries);
            }
        }

        // "publisher": "Ace"
        if (!calibreBook.isNull("publisher")) {
            final String publisherName = calibreBook.optString("publisher");
            if (!publisherName.isEmpty()) {
                if ("null".equals(publisherName)) {
                    throw new IllegalArgumentException("BLEEP");
                }

                final ArrayList<Publisher> bookPublishers = new ArrayList<>();
                bookPublishers.add(Publisher.from(publisherName));
                book.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, bookPublishers);
            }
        }

        if (!calibreBook.isNull("identifiers")) {
            final JSONObject identifiers = calibreBook.optJSONObject("identifiers");
            if (identifiers != null) {
                final Iterator<String> it = identifiers.keys();
                while (it.hasNext()) {
                    final String key = it.next();
                    if (!identifiers.isNull(key)) {
                        switch (key) {
                            case "isbn":
                                book.putString(DBDefinitions.KEY_ISBN,
                                               identifiers.optString(key));
                                break;
                            case "amazon":
                                book.putString(DBDefinitions.KEY_ESID_ASIN,
                                               identifiers.optString(key));
                                break;
                            case "goodreads":
                                try {
                                    book.putLong(DBDefinitions.KEY_ESID_GOODREADS_BOOK,
                                                 Long.parseLong(identifiers.optString(key)));
                                } catch (@NonNull final NumberFormatException ignore) {
                                    // ignore
                                }
                                break;
                            case "google":
                                book.putString(DBDefinitions.KEY_ESID_GOOGLE,
                                               identifiers.optString(key));
                                break;

                            // There are many more (free-form) ...
                            default:
                                // Other than strict "amazon", there are variants
                                // for local sites; e.g. "amazon_nl", "amazon_fr",...
                                if (key.startsWith("amazon")) {
                                    book.putString(DBDefinitions.KEY_ESID_ASIN,
                                                   identifiers.optString(key));
                                }
                                break;
                        }
                    }
                }
            }
        }

        if (!calibreBook.isNull("cover")) {
            final String coverUrl = calibreBook.optString("cover");
            if (!coverUrl.isEmpty() && mFetchCovers) {
                final File file = mServer.getCover(context, calibreBookId, coverUrl);
                book.setCover(context, mDb, 0, file);
            }
        }

        if (!calibreBook.isNull("user_metadata")) {
            final JSONObject userMetaData = calibreBook.optJSONObject("user_metadata");
            if (userMetaData != null) {
                for (final CalibreCustomFields.Field cf : mServer.getCustomFields()) {
                    final JSONObject data = userMetaData.optJSONObject(cf.calibreKey);

                    // Sanity check, at this point it should always be true
                    if (data != null && cf.type.equals(data.getString(
                            CalibreCustomFields.METADATA_DATATYPE))) {

                        if (!data.isNull("#value#")) {
                            switch (cf.type) {
                                case CalibreCustomFields.TYPE_BOOL: {
                                    book.putBoolean(cf.dbKey, data.getBoolean("#value#"));
                                    break;
                                }
                                case CalibreCustomFields.TYPE_DATETIME:
                                case CalibreCustomFields.TYPE_COMMENTS:
                                case CalibreCustomFields.TYPE_TEXT: {
                                    final String value = data.getString("#value#");
                                    if (!"None".equals(value)) {
                                        book.putString(cf.dbKey, value);
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

        if (!calibreBook.isNull("main_format")) {
            final JSONObject mainFormat = calibreBook.optJSONObject("main_format");
            if (mainFormat != null) {
                final Iterator<String> it = mainFormat.keys();
                if (it.hasNext()) {
                    final String format = it.next();
                    if (format != null && !format.isEmpty()) {
                        book.putString(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT, format);
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
}

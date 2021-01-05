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
package com.hardbacknutter.nevertoomanybooks.backup.url;

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
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

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
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Copies the standard fields (book, author, publisher, series).
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
 * Not copying "ratings" either. The source is rather unclear. Amazon or Goodreads? or?
 * <p>
 * ENHANCE: tags... this would require implementing a full tag system in our own database.
 * => bookshelves 'are' tags? redefine the meaning of bookshelf as a 'tag'?
 * => and then define some tags as being shelves ?
 */
public class CalibreContentServerReader
        implements ArchiveReader {

    private static final String META_DATA_DATATYPE = "datatype";
    /**
     * The 'label' is the actual column/lookup name.
     * Not to be confused with the 'name' which is the displayed string!
     */
    private static final String META_DATA_LABEL = "label";
    private static final String META_DATA_VALUE = "#value#";
    private static final String META_DATA_VALUE_NONE = "None";

    private static final String CALIBRE_BOOL = "bool";
    private static final String CALIBRE_DATETIME = "datetime";
    private static final String CALIBRE_COMMENTS = "comments";
    private static final String CALIBRE_TEXT = "text";

    /** Log tag. */
    private static final String TAG = "CalibreContentServerRdr";

    public static final String ARCH_MD_DEFAULT_LIBRARY = TAG + ":defLibId";
    public static final String ARCH_MD_LIBRARY_LIST = TAG + ":libs";

    /** The number of books we fetch per request. Tested with CCS running on a RaspberryPi 1b+. */
    private static final int NUM = 10;

    @NonNull
    private final CalibreContentServer mServer;
    @NonNull
    private final DAO mDb;

    private final boolean mSyncBooks;
    private final boolean mOverwriteBooks;

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

    /**
     * Constructor.
     * <p>
     * Dev Note: while most plumbing to use multiple libraries is in place,
     * right now we only support the default library.
     * TODO: read the list of libs and prompt the user, BEFORE starting a sync
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @throws IOException  on failure to read the server libraries or other failures
     * @throws SSLException if a secure connection failed
     */
    public CalibreContentServerReader(@NonNull final Context context,
                                      @NonNull final ImportHelper helper)
            throws IOException, GeneralParsingException, SSLException,
                   CertificateException, NoSuchAlgorithmException,
                   KeyStoreException, KeyManagementException {
        mDb = new DAO(TAG);

        mSyncBooks = helper.isNewAndUpdatedBooks();
        mOverwriteBooks = helper.isAllBooks();
        mFetchCovers = helper.getImportEntries().contains(RecordType.Cover);

        // ENHANCE: allow passing in of the desired library to use.
        mServer = new CalibreContentServer(context, helper.getUri());
        mServer.ensureLibraries();
        //mServer.setCurrentLibrary(id);

        mEBookString = context.getString(R.string.book_format_ebook);
        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);

        mBookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.DEFAULT);
    }

    @Nullable
    @Override
    @WorkerThread
    public ArchiveMetaData readMetaData(@NonNull final Context context)
            throws IOException, GeneralParsingException {

        final ArchiveMetaData archiveMetaData = new ArchiveMetaData();

        try {
            // read the first book id available, this will give us the total
            final JSONObject result = mServer.getBookIds(1, 0);
            archiveMetaData.setBookCount(result.optInt("total_num"));

        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        final Bundle bundle = archiveMetaData.getBundle();
        bundle.putString(ARCH_MD_DEFAULT_LIBRARY, mServer.getCurrentLibrary().getName());
        bundle.putStringArrayList(ARCH_MD_LIBRARY_LIST,
                                  mServer.getLibraries()
                                         .values()
                                         .stream()
                                         .map(CalibreLibrary::getName)
                                         .collect(Collectors.toCollection(ArrayList::new)));
        return archiveMetaData;
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, GeneralParsingException {

        mResults = new ImportResults();

        try {
            int totalNum = 0;
            int num = 0;
            int offset = 0;
            boolean valid;

            do {
                final JSONObject result = mServer.getBookIds(NUM, offset);
                // assume valid result if at least the "total_num" param is there.
                valid = result.has("total_num");
                if (valid) {
                    if (totalNum == 0) {
                        totalNum = result.getInt("total_num");
                        progressListener.setMaxPos(totalNum);
                    }
                    num = result.getInt("num");

                    final JSONArray bookIds = result.optJSONArray("book_ids");
                    valid = bookIds != null && bookIds.length() > 0;
                    if (valid) {
                        final JSONObject books = mServer.getBooks(bookIds);
                        final Iterator<String> it = books.keys();
                        while (it.hasNext()) {
                            handleBook(context, books.getJSONObject(it.next()));
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

        } catch (@NonNull final DAO.DaoWriteException
                | SQLiteDoneException e) {
            mResults.booksSkipped++;

        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        return mResults;
    }

    private void handleBook(@NonNull final Context context,
                            @NonNull final JSONObject calibreBook)
            throws DAO.DaoWriteException {

        try {
            final String calibreUuid = calibreBook.getString("uuid");
            // check if the book exists in our database, and fetch it's id.
            final long databaseBookId = mDb.getBookIdFromCalibreUuid(calibreUuid);
            if (databaseBookId > 0) {
                final Book book = Book.from(databaseBookId, mDb);
                // UPDATE the existing book (if allowed). Check the sync option FIRST!
                if ((mSyncBooks && isImportNewer(context, mDb, book, databaseBookId))
                    || mOverwriteBooks) {
                    book.setStage(EntityStage.Stage.Dirty);
                    copyCalibreData(context, book, calibreBook);

                    mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                              | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);

                    mResults.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                        Log.d(TAG, "calibreUuid=" + calibreUuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|update|" + book.getTitle());
                    }

                } else {
                    mResults.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                        Log.d(TAG, "calibreUuid=" + calibreUuid
                                   + "|databaseBookId=" + databaseBookId
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
                copyCalibreData(context, book, calibreBook);

                final long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION);
                mResults.booksCreated++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                    Log.d(TAG, "calibreUuid=" + calibreUuid
                               + "|insert=" + insId
                               + "|" + book.getTitle());
                }
            }

        } catch (@NonNull final JSONException e) {
            // log, but don't fail
            Logger.error(context, TAG, e);
            mResults.booksSkipped++;
        }
    }

    private void copyCalibreData(@NonNull final Context context,
                                 @NonNull final Book book,
                                 @NonNull final JSONObject source)
            throws JSONException {

        // "application_id": 6,
        final int calibreBookId = source.getInt("application_id");
        book.putInt(DBDefinitions.KEY_CALIBRE_ID, calibreBookId);
        // "uuid": "4ec36562-d8e8-4499-9c6c-d1e7ae2af42f",
        book.putString(DBDefinitions.KEY_CALIBRE_UUID, source.getString("uuid"));

        // "title": "Accelerando",
        book.putString(DBDefinitions.KEY_TITLE, source.getString("title"));
        // "comments": "<div>\n<p>The Singularity. blah blah...",
        book.putString(DBDefinitions.KEY_DESCRIPTION, source.getString("comments"));
        // "last_modified": "2020-11-20T11:17:51+00:00",
        book.putString(DBDefinitions.KEY_UTC_LAST_UPDATED, source.getString("last_modified"));

        // "languages": [
        //      "eng"
        // ],
        if (!source.isNull("languages")) {
            final JSONArray languages = source.optJSONArray("languages");
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
        if (!source.isNull("authors")) {
            final JSONArray authors = source.optJSONArray("authors");
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
        if (!source.isNull("series")) {
            final String seriesName = source.optString("series");
            if (!seriesName.isEmpty()) {
                if ("null".equals(seriesName)) {
                    throw new IllegalArgumentException("BLEEP");
                }
                final Series series = Series.from(seriesName);
                // "series_index": null,
                // "series_index": 2,  --> it's a float, but we grab it as a string
                final String seriesNr = source.optString("series_index");
                if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                    series.setNumber(seriesNr);
                }
                final ArrayList<Series> bookSeries = new ArrayList<>();
                bookSeries.add(series);
                book.putParcelableArrayList(Book.BKEY_SERIES_LIST, bookSeries);
            }
        }

        // "publisher": "Ace"
        if (!source.isNull("publisher")) {
            final String publisherName = source.optString("publisher");
            if (!publisherName.isEmpty()) {
                if ("null".equals(publisherName)) {
                    throw new IllegalArgumentException("BLEEP");
                }

                final ArrayList<Publisher> bookPublishers = new ArrayList<>();
                bookPublishers.add(Publisher.from(publisherName));
                book.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, bookPublishers);
            }
        }

        if (!source.isNull("identifiers")) {
            final JSONObject identifiers = source.optJSONObject("identifiers");
            if (identifiers != null) {
                final Iterator<String> it = identifiers.keys();
                while (it.hasNext()) {
                    final String key = it.next();
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

        if (!source.isNull("cover")) {
            final String coverUrl = source.optString("cover");
            if (!coverUrl.isEmpty() && mFetchCovers) {
                final File file = mServer.getCover(context, calibreBookId, coverUrl);
                book.setCover(context, mDb, 0, file);
            }
        }

        if (!source.isNull("user_metadata")) {
            final JSONObject userMetaData = source.optJSONObject("user_metadata");
            if (userMetaData != null) {
                final Iterator<String> it = userMetaData.keys();
                while (it.hasNext()) {
                    final JSONObject metadata = userMetaData.getJSONObject(it.next());
                    final String label = metadata.getString(META_DATA_LABEL);
                    final String datatype = metadata.getString(META_DATA_DATATYPE);

                    if (!metadata.isNull(META_DATA_VALUE)) {
                        switch (label) {
                            case DBDefinitions.KEY_READ:
                                if (CALIBRE_BOOL.equals(datatype)) {
                                    book.putBoolean(label, metadata.getBoolean(META_DATA_VALUE));
                                }
                                break;

                            case DBDefinitions.KEY_READ_START:
                            case DBDefinitions.KEY_READ_END:
                            case "date_read":
                                if (CALIBRE_DATETIME.equals(datatype)) {
                                    final String s = metadata.getString(META_DATA_VALUE);
                                    if (!META_DATA_VALUE_NONE.equals(s)) {
                                        book.putString(label, s);
                                    }
                                }
                                break;

                            case DBDefinitions.KEY_PRIVATE_NOTES:
                                if (CALIBRE_COMMENTS.equals(datatype)
                                    || CALIBRE_TEXT.equals(datatype)) {
                                    final String s = metadata.getString(META_DATA_VALUE);
                                    if (!META_DATA_VALUE_NONE.equals(s)) {
                                        book.putString(label, s);
                                    }
                                }
                                break;

                            default:
                                // skip others
                                break;
                        }
                    }
                }
            }
        }

        if (!source.isNull("main_format")) {
            final JSONObject mainFormat = source.optJSONObject("main_format");
            if (mainFormat != null) {
                final Iterator<String> it = mainFormat.keys();
                if (it.hasNext()) {
                    final String format = it.next();
                    if (format != null && !format.isEmpty()) {
                        book.putString(DBDefinitions.KEY_CALIBRE_FILE_URL,
                                       mainFormat.getString(format));
                    }
                }
            }
        }
    }

    /**
     * Check if the incoming book is newer than the stored book data.
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the book we're updating
     * @param bookId  the book id to lookup in our database
     *
     * @return {@code true} if the imported data is newer then the local data.
     */
    private boolean isImportNewer(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final Book book,
                                  @IntRange(from = 1) final long bookId) {
        final LocalDateTime utcImportDate =
                DateParser.getInstance(context)
                          .parseISO(book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED));
        if (utcImportDate == null) {
            return false;
        }

        final LocalDateTime utcLastUpdated = db.getBookLastUpdateUtcDate(context, bookId);

        return utcLastUpdated == null || utcImportDate.isAfter(utcLastUpdated);
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }
}

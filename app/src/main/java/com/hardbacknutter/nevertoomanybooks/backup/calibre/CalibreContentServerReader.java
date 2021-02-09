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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;

import javax.net.ssl.SSLException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
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
 * ENHANCE: tags... this would require implementing a full tag system in our own database.
 * => bookshelves 'are' tags? redefine the meaning of bookshelf as a 'tag'?
 * => and then define some tags as being shelves ?
 * <p>
 * ENHANCE: implement {@link com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage}.
 * For now overwrite/skip is a bit ad-hoc.
 */
public class CalibreContentServerReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CalibreServerReader";

    private static final String BKEY_VIRTUAL_LIBRARY_LIST = TAG + ":vlibs";

    /** The number of books we fetch per request. Tested with CCS running on a RaspberryPi 1b+. */
    private static final int NUM = 10;
    /** error text for {@link #VALUE_IS_NULL}. */
    private static final String ERROR_NULL_STRING = "'null' string";

    /** A text "null" as value. Should be considered an error. */
    private static final String VALUE_IS_NULL = "null";

    private final boolean mDoNewAndUpdatedBooks;
    private final boolean mDoAllBooks;
    private final boolean mDoCovers;
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
    private final DAO mDb;

    @NonNull
    private final CalibreContentServer mServer;
    @NonNull
    private final ImportHelper mHelper;
    /** The physical library from which we'll be importing. */
    @Nullable
    private CalibreLibrary mLibrary;

    private ImportResults mResults;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @throws SSLException         on secure connection failures
     * @throws CertificateException on failures related to a user installed CA.
     */
    public CalibreContentServerReader(@NonNull final Context context,
                                      @NonNull final ImportHelper helper)
            throws CertificateException, SSLException {

        mDb = new DAO(TAG);

        mHelper = helper;
        mServer = new CalibreContentServer(context, mHelper.getUri());

        mDoCovers = mHelper.getImportEntries().contains(RecordType.Cover);

        mDoNewAndUpdatedBooks = mHelper.isNewAndUpdatedBooks();
        mDoAllBooks = mHelper.isAllBooks();

        mEBookString = context.getString(R.string.book_format_ebook);
        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    private void initServer(@NonNull final Context context)
            throws IOException, JSONException {

        mServer.readMetaData(context, mDb);

        mLibrary = mHelper.getExtraArgs().getParcelable(CalibreContentServer.BKEY_LIBRARY);
        if (mLibrary == null) {
            mLibrary = mServer.getDefaultLibrary();
        }
    }

    @Nullable
    @Override
    @WorkerThread
    public ArchiveMetaData readMetaData(@NonNull final Context context)
            throws GeneralParsingException, IOException {

        try {
            initServer(context);
        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        final ArchiveMetaData archiveMetaData = new ArchiveMetaData();

        final Bundle bundle = archiveMetaData.getBundle();
        // the requested (or default) library
        bundle.putParcelable(CalibreContentServer.BKEY_LIBRARY, mLibrary);
        // and the full list
        bundle.putParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST,
                                      mServer.getLibraries());

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
            // Always (re)read the meta data here.
            // Don't assume we still have the same instance as when readMetaData was called.
            initServer(context);

            //noinspection ConstantConditions
            final int totalNum = mLibrary.getTotalBooks();

            int num = 0;
            int offset = 0;
            boolean valid;

            String query = null;
            if (!mHelper.isAllBooks()) {
                // last_modified:">2021-01-15"
                // Due to rounding, we might get some books we don't need, but that's ok.
                final LocalDateTime lastSyncDate = mLibrary.getLastSyncDate(context);
                if (lastSyncDate != null) {
                    query = CalibreBook.LAST_MODIFIED + ":%22%3E" + lastSyncDate
                            .format(DateTimeFormatter.ISO_LOCAL_DATE) + "%22";
                }
            }

            do {
                final JSONObject root;
                if (query == null) {
                    root = mServer.getBookIds(mLibrary.getLibraryStringId(), NUM, offset);
                } else {
                    root = mServer.search(mLibrary.getLibraryStringId(), NUM, offset, query);
                }

                // assume valid result if at least the "total_num" param is there.
                valid = root.has(CalibreContentServer.RESPONSE_TAG_TOTAL_NUM);
                if (valid) {
                    // yes, we're reading/setting this on every iteration... less code.
                    progressListener.setMaxPos(root.getInt(
                            CalibreContentServer.RESPONSE_TAG_TOTAL_NUM));

                    num = root.getInt(CalibreContentServer.RESPONSE_TAG_NUM);
                    final JSONArray bookIds = root.optJSONArray(
                            CalibreContentServer.RESPONSE_TAG_BOOK_IDS);

                    valid = bookIds != null && bookIds.length() > 0;
                    if (valid) {
                        final JSONObject bookList = mServer.getBooks(mLibrary.getLibraryStringId(),
                                                                     bookIds);
                        final JSONObject bookListVirtualLibs =
                                mServer.getVirtualLibrariesForBooks(mLibrary.getLibraryStringId(),
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
        mLibrary.setLastSyncDate(LocalDateTime.now(ZoneOffset.UTC));
        mDb.update(mLibrary);

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
                        final Identifier identifier = Identifier.MAP.get(key);
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

                        } else if (key.startsWith(Identifier.AMAZON)) {
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

        if (mDoCovers) {
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
                //noinspection ConstantConditions
                for (final CustomFields.Field cf : mLibrary.getCustomFields()) {
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
                                    if (!CalibreContentServer.VALUE_IS_NONE.equals(value)) {
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


        // Bookshelves / Calibre Library & Virtual libraries.

        // Always add the current library; i.e. the library the book came from.
        localBook.setCalibreLibrary(mLibrary);

        // Current list, will be empty for new books
        final ArrayList<Bookshelf> bookShelves = localBook
                .getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);

        // Add the physical library mapped Bookshelf
        //noinspection ConstantConditions
        final Bookshelf mappedBookshelf = Bookshelf
                .getBookshelf(context, mDb, mLibrary.getMappedBookshelfId(), Bookshelf.PREFERRED);
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
        if (virtualLibs != null && virtualLibs.length() > 0) {
            for (int i = 0; i < virtualLibs.length(); i++) {
                final String name = virtualLibs.getString(i);

                // lookup the matching virtual library.
                mLibrary.getVirtualLibraries()
                        .stream()
                        .filter(vlib -> vlib.getName().equals(name))
                        .findFirst()
                        // it will always be present of course.
                        .ifPresent(vlib -> {
                            final Bookshelf vlibMappedBookshelf =
                                    Bookshelf.getBookshelf(context, mDb,
                                                           vlib.getMappedBookshelfId(),
                                                           mLibrary.getMappedBookshelfId());

                            // add the vlib mapped bookshelf if not already present.
                            if (bookShelves.stream()
                                           .map(Bookshelf::getId)
                                           .noneMatch(id -> id == vlibMappedBookshelf.getId())) {
                                bookShelves.add(vlibMappedBookshelf);
                            }
                        });
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

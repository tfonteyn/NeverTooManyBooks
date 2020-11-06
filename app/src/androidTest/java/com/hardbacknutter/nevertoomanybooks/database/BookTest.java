/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

import static com.hardbacknutter.nevertoomanybooks.database.Constants.AuthorFullName;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.BOOK_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.COVER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Do NOT extend BaseSetup ! */
@MediumTest
public class BookTest {

    private static final String NEED_TWO_FILE =
            "pictures directory must contain at least two file to use for testing";
    private static final String NEED_A_PICTURES_DIRECTORY = "Need a pictures directory";
    private static final String NEED_A_CACHE_DIRECTORY = "Need a cache directory";
    private static final String EXT_JPG = ".jpg";

    private final Bookshelf[] mBookshelf = new Bookshelf[5];
    private final long[] mBookshelfId = new long[5];
    private final ArrayList<Bookshelf> mBookshelfList = new ArrayList<>();

    private final Author[] mAuthor = new Author[5];
    private final long[] mAuthorId = new long[5];
    private final ArrayList<Author> mAuthorList = new ArrayList<>();

    private final Publisher[] mPublisher = new Publisher[5];
    private final long[] mPublisherId = new long[5];
    private final ArrayList<Publisher> mPublisherList = new ArrayList<>();

    private final TocEntry[] mTocEntry = new TocEntry[5];
    private final long[] mTocEntryId = new long[5];
    private final ArrayList<TocEntry> mTocEntryList = new ArrayList<>();

    private final Book[] mBook = new Book[5];
    private final long[] mBookId = new long[5];

    private final String[] mOriginalImageFileName = new String[2];
    private final long[] mOriginalImageSize = new long[2];
    private final FilenameFilter mJpgFilter = (dir, name) -> name.endsWith(EXT_JPG);
    private File mExternalFilesDir;
    private File mExternalCacheDir;

    /**
     * Clean the database.
     * Empty the cache directory.
     * Copy two pictures from the Pictures directory to the cache directory.
     */
    @Before
    public void setup()
            throws IOException {

        mBookshelfList.clear();
        mAuthorList.clear();
        mPublisherList.clear();
        mTocEntryList.clear();

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "setup")) {

            Constants.deleteTocs(db);
            Constants.deleteBooks(db);
            Constants.deleteAuthors(db);
            Constants.deletePublishers(db);

            mBookshelf[0] = Bookshelf.getBookshelf(context, db, Bookshelf.DEFAULT);
            mBookshelfList.clear();
            mBookshelfList.add(mBookshelf[0]);

            mAuthor[0] = Author.from(AuthorFullName(0));
            mAuthor[1] = Author.from(AuthorFullName(1));

            // insert author[0] but do NOT insert author[1]
            mAuthorId[0] = db.insert(context, mAuthor[0]);
            mAuthorList.clear();
            mAuthorList.add(mAuthor[0]);

            mPublisher[0] = Publisher.from(Constants.PUBLISHER + "0");
            mPublisher[1] = Publisher.from(Constants.PUBLISHER + "1");

            // insert publisher[0] but do NOT publisher author[1]
            mPublisherId[0] = db.insert(context, mPublisher[0], Locale.getDefault());
            mPublisherList.clear();
            mPublisherList.add(mPublisher[0]);
        }

        initCacheDirectory(context);
        final File[] files = initPicturesDirectory(context);
        prepareCover(files, 0);
        prepareCover(files, 1);

        assertTrue(mBookshelf[0].getId() > 0);
        assertTrue(mAuthor[0].getId() > 0);
        assertTrue(mPublisher[0].getId() > 0);
    }

    /**
     * Init the Pictures directory and check we have at least 2 pictures.
     *
     * @param context Current context
     *
     * @return array of picture files
     */
    private File[] initPicturesDirectory(final Context context) {
        //noinspection ConstantConditions
        mExternalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        assertNotNull(NEED_A_PICTURES_DIRECTORY, mExternalFilesDir);
        final File[] files = mExternalFilesDir.listFiles(mJpgFilter);
        assertNotNull(NEED_TWO_FILE, files);
        assertTrue(NEED_TWO_FILE, files.length > 1);
        return files;
    }

    /**
     * Init the Cache directory and delete all pictures in it.
     *
     * @param context Current context
     */
    private void initCacheDirectory(final Context context) {
        //noinspection ConstantConditions
        mExternalCacheDir = context.getExternalCacheDir();
        assertNotNull(NEED_A_CACHE_DIRECTORY, mExternalCacheDir);
        final File[] cachedFiles = mExternalCacheDir.listFiles(mJpgFilter);
        if (cachedFiles != null) {
            //noinspection ResultOfMethodCallIgnored
            Arrays.stream(cachedFiles).forEach(File::delete);
        }
    }

    /**
     * Copy a file from the Pictures directory to the cache with a new/temp name.
     *
     * @param files from the Pictures directory
     * @param cIdx  cover index to create
     *
     * @throws IOException on failure
     */
    private void prepareCover(@NonNull final File[] files,
                              final int cIdx)
            throws IOException {
        mOriginalImageFileName[cIdx] = files[cIdx].getAbsolutePath();
        final File srcFile = new File(mOriginalImageFileName[cIdx]);
        mOriginalImageSize[cIdx] = srcFile.length();
        FileUtils.copy(srcFile, new File(mExternalCacheDir, Constants.COVER[cIdx]));
    }

    /**
     * <ol>
     *     <li>Insert a book on the default bookshelf, with 1 author,
     *          1 publisher, 1 front cover image.</li>
     *     <li>Retrieve it by id and test.</li>
     *     <li>update the retrieved book, change title, add author, add back cover</li>
     *     <li>Retrieve it by id and test.</li>
     *     <li>Delete the back cover while the book is {@link EntityStage.Stage#Clean}</li>
     *
     * </ol>
     */
    @Test
    public void book()
            throws DAO.DaoWriteException, IOException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "book")) {

            mBook[0] = prepareBook(context, db);
            mBookId[0] = mBook[0].getId();

            /*
             * test the inserted book
             */
            Book book = Book.from(mBookId[0], db);
            assertEquals(mBookId[0], book.getId());
            checkBookAfterInitialInsert(context, book);

            ArrayList<Author> authors;
            String uuid;
            final ArrayList<Bookshelf> bookshelves;
            final ArrayList<Publisher> publishers;
            File cover;
            final File[] cachedFiles;

            /*
             * update the stored book
             */
            book.setStage(EntityStage.Stage.WriteAble);
            book.putString(KEY_TITLE, BOOK_TITLE + "0_upd");
            book.setStage(EntityStage.Stage.Dirty);

            authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            authors.add(mAuthor[1]);

            book.setCover(context, db, 1, AppDir.Cache.getFile(context, Constants.COVER[1]));

            assertEquals(mExternalCacheDir.getAbsolutePath()
                         + File.separatorChar + Constants.COVER[1],
                         book.getString(Book.BKEY_TMP_FILE_SPEC[1]));

            assertEquals(EntityStage.Stage.Dirty, book.getStage());
            db.update(context, book, 0);
            book.setStage(EntityStage.Stage.Clean);

            /*
             * test the updated book
             */
            book = Book.from(mBookId[0], db);
            assertEquals(mBookId[0], book.getId());

            uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);

            assertEquals(BOOK_TITLE + "0_upd", book.getString(KEY_TITLE));
            bookshelves = book.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
            assertEquals(1, bookshelves.size());
            assertEquals(mBookshelf[0], bookshelves.get(0));
            authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            assertEquals(2, authors.size());
            assertEquals(mAuthor[0], authors.get(0));
            assertEquals(mAuthor[1], authors.get(1));
            publishers = book.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            assertEquals(1, publishers.size());
            assertEquals(mPublisher[0], publishers.get(0));

            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            cover = book.getCoverFile(context, 0);
            assertNotNull(cover);
            assertEquals(mOriginalImageSize[0], cover.length());
            assertEquals(uuid + EXT_JPG, cover.getName());

            cover = book.getCoverFile(context, 1);
            assertNotNull(cover);
            assertEquals(mOriginalImageSize[1], cover.length());
            assertEquals(uuid + "_1" + EXT_JPG, cover.getName());

            cachedFiles = mExternalCacheDir.listFiles(mJpgFilter);
            assertNotNull(cachedFiles);
            // both files should be gone now
            assertEquals(0, cachedFiles.length);


            /*
             * Delete the second cover of the read-only book
             */
            book = Book.from(mBookId[0], db);
            assertEquals(mBookId[0], book.getId());

            uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);

            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            // sanity check the cover is really there
            cover = new File(mExternalFilesDir, uuid + "_1" + EXT_JPG);
            assertTrue(cover.exists());

            book.setCover(context, db, 1, null);

            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            // setCover(.. null) should have deleted the file
            cover = new File(mExternalFilesDir, uuid + "_1" + EXT_JPG);
            assertFalse(cover.exists());

            cover = book.getCoverFile(context, 1);
            assertNull(cover);

            /*
             * Add the second cover of the read-only book
             */
            final File[] files = mExternalFilesDir.listFiles(mJpgFilter);
            assertNotNull(files);
            prepareCover(files, 1);

            book.setCover(context, db, 1, AppDir.Cache.getFile(context, Constants.COVER[1]));

            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            cover = new File(mExternalFilesDir, uuid + "_1" + EXT_JPG);
            assertTrue(cover.exists());

            cover = book.getCoverFile(context, 1);
            assertNotNull(cover);
            assertEquals(uuid + "_1" + EXT_JPG, cover.getName());
            assertTrue(cover.exists());
        }
    }

    @Test
    public void bookViewModelReadOnly()
            throws DAO.DaoWriteException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "bookViewModel-prep")) {
            mBook[0] = prepareBook(context, db);
            mBookId[0] = mBook[0].getId();
        }

        final BookViewModel vm = new BookViewModel();
        final Bundle args = new Bundle();
        args.putLong(DBDefinitions.KEY_PK_ID, mBookId[0]);
        try {
            vm.init(context, args, false);
            final Book retrieved = vm.getBook();
            assertEquals(mBookId[0], retrieved.getId());
            checkBookAfterInitialInsert(context, retrieved);

        } finally {
            vm.onCleared();
        }
    }

    /*
     * Create and insert a book.
     */
    private Book prepareBook(@NonNull final Context context,
                             @NonNull final DAO db)
            throws DAO.DaoWriteException {

        final Book book = new Book();
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(KEY_TITLE, Constants.BOOK_TITLE + "0");
        book.setStage(EntityStage.Stage.Dirty);

        book.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, mBookshelfList);
        book.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, mAuthorList);
        book.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, mPublisherList);

        book.setCover(context, db, 0, AppDir.Cache.getFile(context, Constants.COVER[0]));

        assertEquals(mExternalCacheDir.getAbsolutePath()
                     + File.separatorChar + Constants.COVER[0],
                     book.getString(Book.BKEY_TMP_FILE_SPEC[0]));

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        final long bookId = db.insert(context, book, 0);
        book.setStage(EntityStage.Stage.Clean);

        assertTrue(bookId > 0);
        assertEquals(book.getId(), bookId);

        return book;
    }

    private void checkBookAfterInitialInsert(@NonNull final Context context,
                                             final Book book) {
        assertEquals(EntityStage.Stage.Clean, book.getStage());

        final String uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);
        assertFalse(uuid.isEmpty());
        assertEquals(BOOK_TITLE + "0", book.getString(KEY_TITLE));

        final ArrayList<Bookshelf> bookshelves = book
                .getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
        assertEquals(1, bookshelves.size());
        assertEquals(mBookshelf[0], bookshelves.get(0));

        final ArrayList<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertEquals(1, authors.size());
        assertEquals(mAuthor[0], authors.get(0));

        final ArrayList<Publisher> publishers = book
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertEquals(1, publishers.size());
        assertEquals(mPublisher[0], publishers.get(0));

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        final File cover = book.getCoverFile(context, 0);
        assertNotNull(cover);
        assertEquals(mOriginalImageSize[0], cover.length());
        assertEquals(uuid + EXT_JPG, cover.getName());

        final File[] cachedFiles = mExternalCacheDir.listFiles(mJpgFilter);
        assertNotNull(cachedFiles);
        // expected: 1: because "0.jpg" should be gone, but "1.jpg" will still be there
        assertEquals(1, cachedFiles.length);
        assertEquals(COVER[1], cachedFiles[0].getName());
    }
}

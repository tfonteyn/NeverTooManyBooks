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
import com.hardbacknutter.nevertoomanybooks.entities.EntityStatus;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

import static com.hardbacknutter.nevertoomanybooks.database.Constants.AuthorFullName;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.BOOK_TITLE;
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

    private final Bookshelf[] bookshelf = new Bookshelf[5];
    private final long[] bookshelfId = new long[5];
    private final ArrayList<Bookshelf> bookshelfList = new ArrayList<>();

    private final Author[] author = new Author[5];
    private final long[] authorId = new long[5];
    private final ArrayList<Author> authorList = new ArrayList<>();

    private final Publisher[] publisher = new Publisher[5];
    private final long[] publisherId = new long[5];
    private final ArrayList<Publisher> publisherList = new ArrayList<>();

    private final TocEntry[] tocEntry = new TocEntry[5];
    private final long[] tocEntryId = new long[5];
    private final ArrayList<TocEntry> tocEntryList = new ArrayList<>();

    private final Book[] book = new Book[5];
    private final long[] bookId = new long[5];

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

        bookshelfList.clear();
        authorList.clear();
        publisherList.clear();
        tocEntryList.clear();

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "setup")) {

            Constants.deleteTocs(db);
            Constants.deleteBooks(db);
            Constants.deleteAuthors(db);
            Constants.deletePublishers(db);
        }

        final File[] files = initPicturesDirectory(context);

        initCacheDirectory(context);

        prepareCover(files, 0);
        prepareCover(files, 1);

        author[0] = Author.from(AuthorFullName(0));
        author[1] = Author.from(AuthorFullName(1));

        publisher[0] = Publisher.from(Constants.PUBLISHER + "0");
        publisher[1] = Publisher.from(Constants.PUBLISHER + "1");
    }

    /**
     * Init the Pictures directory and check we have at least 2 pictures.
     *
     * @param context Current context
     *
     * @return array of picture files
     */
    private File[] initPicturesDirectory(final Context context) {
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
     *     <li>Delete the back cover while the book is ReadOnly</li>
     *
     * </ol>
     */
    @Test
    public void book()
            throws DAO.DaoWriteException, IOException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "book")) {

            bookshelf[0] = Bookshelf.getBookshelf(context, db, Bookshelf.DEFAULT);
            bookshelfList.clear();
            bookshelfList.add(bookshelf[0]);

            // insert author[0] but do NOT insert author[1]
            authorId[0] = db.insert(context, author[0]);
            authorList.clear();
            authorList.add(author[0]);

            // insert publisher[0] but do NOT publisher author[1]
            publisherId[0] = db.insert(context, publisher[0], Locale.getDefault());
            publisherList.clear();
            publisherList.add(publisher[0]);

            book[0] = new Book();
            book[0].setStage(EntityStatus.Stage.WriteAble);
            book[0].putString(KEY_TITLE, Constants.BOOK_TITLE + "0");
            book[0].setStage(EntityStatus.Stage.Dirty);

            book[0].putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
            book[0].putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
            book[0].putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);

            book[0].setCover(context, db, 0, AppDir.Cache.getFile(context, Constants.COVER[0]));

            assertEquals(mExternalCacheDir.getAbsolutePath() + File.separatorChar
                         + Constants.COVER[0], book[0].getString(Book.BKEY_TMP_FILE_SPEC[0]));

            /*
             * insert the book
             */
            assertEquals(EntityStatus.Stage.Dirty, book[0].getStage());
            bookId[0] = db.insert(context, book[0], 0);
            book[0].setStage(EntityStatus.Stage.Saved);

            assertTrue(bookId[0] > 0);
            assertEquals(book[0].getId(), bookId[0]);
            assertTrue(author[0].getId() > 0);
            assertTrue(publisher[0].getId() > 0);

            /*
             * test the inserted book
             */
            Book retrieved;
            String uuid;
            ArrayList<Bookshelf> bookshelves;
            ArrayList<Author> authors;
            ArrayList<Publisher> publishers;
            File cover;
            File[] cachedFiles;

            retrieved = new Book();
            retrieved.load(bookId[0], db);
            assertEquals(bookId[0], retrieved.getId());

            assertEquals(EntityStatus.Stage.ReadOnly, retrieved.getStage());

            uuid = retrieved.getString(DBDefinitions.KEY_BOOK_UUID);

            assertEquals(BOOK_TITLE + "0", retrieved.getString(KEY_TITLE));
            bookshelves = retrieved.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
            assertEquals(1, bookshelves.size());
            assertEquals(bookshelf[0], bookshelves.get(0));
            authors = retrieved.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            assertEquals(1, authors.size());
            assertEquals(author[0], authors.get(0));
            publishers = retrieved.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            assertEquals(1, publishers.size());
            assertEquals(publisher[0], publishers.get(0));

            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            cover = retrieved.getUuidCoverFile(context, 0);
            assertNotNull(cover);
            assertEquals(mOriginalImageSize[0], cover.length());
            assertEquals(uuid + EXT_JPG, cover.getName());

            cachedFiles = mExternalCacheDir.listFiles(mJpgFilter);
            assertNotNull(cachedFiles);
            // 1: because "0.jpg" should be gone, but "1.jpg" will still be there
            assertEquals(1, cachedFiles.length);

            /*
             * update the stored book
             */
            retrieved.setStage(EntityStatus.Stage.WriteAble);
            retrieved.putString(KEY_TITLE, BOOK_TITLE + "0_upd");
            retrieved.setStage(EntityStatus.Stage.Dirty);

            authors = retrieved.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            authors.add(author[1]);

            retrieved.setCover(context, db, 1, AppDir.Cache.getFile(context, Constants.COVER[1]));

            assertEquals(mExternalCacheDir.getAbsolutePath() + File.separatorChar
                         + Constants.COVER[1], retrieved.getString(Book.BKEY_TMP_FILE_SPEC[1]));

            assertEquals(EntityStatus.Stage.Dirty, retrieved.getStage());
            db.update(context, retrieved, 0);
            retrieved.setStage(EntityStatus.Stage.Saved);

            /*
             * test the updated book
             */
            retrieved = new Book();
            retrieved.load(bookId[0], db);
            assertEquals(bookId[0], retrieved.getId());

            uuid = retrieved.getString(DBDefinitions.KEY_BOOK_UUID);

            assertEquals(BOOK_TITLE + "0_upd", retrieved.getString(KEY_TITLE));
            bookshelves = retrieved.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
            assertEquals(1, bookshelves.size());
            assertEquals(bookshelf[0], bookshelves.get(0));
            authors = retrieved.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            assertEquals(2, authors.size());
            assertEquals(author[0], authors.get(0));
            assertEquals(author[1], authors.get(1));
            publishers = retrieved.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            assertEquals(1, publishers.size());
            assertEquals(publisher[0], publishers.get(0));

            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            cover = retrieved.getUuidCoverFile(context, 0);
            assertNotNull(cover);
            assertEquals(mOriginalImageSize[0], cover.length());
            assertEquals(uuid + EXT_JPG, cover.getName());

            cover = retrieved.getUuidCoverFile(context, 1);
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
            retrieved = new Book();
            retrieved.load(bookId[0], db);
            assertEquals(bookId[0], retrieved.getId());

            uuid = retrieved.getString(DBDefinitions.KEY_BOOK_UUID);

            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            // sanity check the cover is really there
            cover = new File(mExternalFilesDir, uuid + "_1" + EXT_JPG);
            assertTrue(cover.exists());

            retrieved.setCover(context, db, 1, null);

            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            // setCover(.. null) should have deleted the file
            cover = new File(mExternalFilesDir, uuid + "_1" + EXT_JPG);
            assertFalse(cover.exists());

            cover = retrieved.getUuidCoverFile(context, 1);
            assertNull(cover);

            /*
             * Add the second cover of the read-only book
             */
            final File[] files = mExternalFilesDir.listFiles(mJpgFilter);
            assertNotNull(files);
            prepareCover(files, 1);

            retrieved.setCover(context, db, 1, AppDir.Cache.getFile(context, Constants.COVER[1]));

            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[0]));
            assertFalse(retrieved.contains(Book.BKEY_TMP_FILE_SPEC[1]));

            cover = new File(mExternalFilesDir, uuid + "_1" + EXT_JPG);
            assertTrue(cover.exists());

            cover = retrieved.getUuidCoverFile(context, 1);
            assertNotNull(cover);
            assertEquals(uuid + "_1" + EXT_JPG, cover.getName());
            assertTrue(cover.exists());
        }
    }
}

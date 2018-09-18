/*
* @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.database;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BooksRow;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.EditBookFieldsFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_BOOKSHELF_WEAK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_STYLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.COLLATION;

/**
 *
 * TODO:
 * - sql statements
 *      - quotes use around 'id' ?
 *      - table aliases hardcoded
 *      - bind variables versus string concat
 *
 * TODO: Long.toString(authorId)   why are some stored as String ?
 *
 *
 *
 * FIXME: some SQLiteDoneException , not all of the callers can deal with
 *  that (but maybe they never will have to?). Those are marked with FIXME
 *  All others are either ok, or the caller catches them.
 *
 *
 *
 *
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the
 * ability to list all books as well as retrieve or modify a specific book.
 *
 * NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES.
 * Use the UpgradeMessageManager class.
 * This change separated messages from DB changes (most releases do not involve DB upgrades).
 *
 * ENHANCE: Use date_added to add 'Recent Acquisitions' virtual shelf; need to resolve how this may relate to date_purchased and 'I own this book'...
 *
 */
public class CatalogueDBAdapter {

    // stop myself from doing a Studio 'reformat code' ...
    //@formatter:off

    private static final String META_EMPTY_SERIES = "<Empty Series>";
    private static final String META_EMPTY_GENRE = "<Empty Genre>";
    private static final String META_EMPTY_DATE_PUBLISHED = "<No Valid Published Date>";

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync */
    private static final Synchronizer mSynchronizer = new Synchronizer();

    private static DatabaseHelper mDbHelper;
    private static SynchronizedDb mSyncedDb;

    private final Context mContext;

    private TableInfo mBooksInfo = null;

	/**
	 * Constructor - takes the context to allow the database to be opened/created
	 *
	 * @param ctx the Context within which to work
	 */
	public CatalogueDBAdapter(@NonNull final Context ctx) {
        mContext = ctx;

        if (mDbHelper == null) {
            mDbHelper = new DatabaseHelper(mContext, mTrackedCursorFactory, mSynchronizer);
        }


		if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
			synchronized(mInstanceCount) {
				mInstanceCount++;
				System.out.println("CatDBA instances: " + mInstanceCount);
				//addInstance(this);
			}
		}
	}

    /** Static Factory object to create the custom cursor */
    private static final CursorFactory mTrackedCursorFactory = new CursorFactory() {
        @Override
        @NonNull
        public Cursor newCursor(
                @NonNull final SQLiteDatabase db,
                @NonNull final SQLiteCursorDriver masterQuery,
                @NonNull final String editTable,
                @NonNull final SQLiteQuery query)
        {
            return new TrackedCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    public void analyzeDb() {
        try {
            mSyncedDb.execSQL("analyze");
        } catch (Exception e) {
            Logger.logError(e, "Analyze failed");
        }
    }

    /**
     * Get the local database.
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return	Database connection
     */
    @NonNull
    public SynchronizedDb getDbIfYouAreSureWhatYouAreDoing() {
        if (mSyncedDb == null || !mSyncedDb.isOpen()) {
            this.open();
        }
        return mSyncedDb;
    }

    /**
     * Get the synchronizer object for this database in case there is some other activity
     * that needs to be synced.
     *
     * Note: Cursor requery() is the only thing found so far.
     */
    @NonNull
    public static Synchronizer getSynchronizer() {
        return mSynchronizer;
    }

    /**
     * Return a boolean indicating this instance was a new installation
     */
    public boolean isNewInstall() {
        return mDbHelper.isNewInstall();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Anthology">

    /**
     * Create an anthology title for a book.
     *
     * @param bookId		id of book
     * @param author		author
     * @param title			title of anthology title
     * @param returnDupId	If title already exists then if true, will return existing ID, if false, will thrown an error
     *
     * @return				ID of anthology title record, or -1 if title was empty
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertAnthologyTitle(final long bookId,
                                     @NonNull final Author author,
                                     @NonNull final String title,
                                     final boolean returnDupId,
                                     final boolean dirtyBookIfNecessary) {
        if (!title.isEmpty()) {
            long authorId = getAuthorIdOrInsert(author);
            return insertAnthologyTitle(bookId, authorId, title, returnDupId, dirtyBookIfNecessary);
        } else {
            return -1L;
        }
    }

    /**
     * Create an anthology title for a book.
     *
     * @param bookId		id of book
     * @param authorId		id of author
     * @param title			title of anthology title
     * @param returnDupId	If title already exists then if true, will return existing ID, if false, will thrown an error
     *
     * @return				ID of anthology title record, or -1 if title was empty
     */
    @SuppressWarnings("WeakerAccess")
    public long insertAnthologyTitle(final long bookId,
                                     final long authorId,
                                     @NonNull final String title,
                                     final boolean returnDupId,
                                     final boolean dirtyBookIfNecessary) {
        if (!title.isEmpty()) {
            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }

            int position = getAnthologyPositionByBook(bookId) + 1;
            ContentValues values = new ContentValues();
            values.put(DOM_BOOK_ID.name, bookId);
            values.put(DOM_AUTHOR_ID.name, authorId);
            values.put(DOM_TITLE.name, title);
            values.put(DOM_POSITION.name, position);
            long result = getAnthologyTitleId(bookId, authorId, title);
            if (result < 0) {
                result = mSyncedDb.insert(DB_TB_ANTHOLOGY, null, values);
            } else {
                if (!returnDupId)
                    throw new AnthologyTitleExistsException();
            }

            return result;
        } else {
            return -1;
        }
    }

    /**
     * Delete ALL the anthology records for a given book, if any
     *
     * @param id id of the book
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteAnthologyTitlesByBook(final long id,
                                               final boolean dirtyBookIfNecessary) {
        boolean success = mSyncedDb.delete(DB_TB_ANTHOLOGY, DOM_BOOK_ID + "=" + id, null) > 0;
        if (dirtyBookIfNecessary) {
            setBookDirty(id);
        }
        purgeAuthors();
        return success;
    }

    /**
     * Delete the anthology record with the given rowId (not to be confused with the book rowId
     *
     * @param id id of the anthology to delete
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("unused")
    public boolean deleteAnthologyTitle(final long id,
                                        final boolean dirtyBookIfNecessary) {
        // Find the soon to be deleted title position#
        int position;
        int book;
        try (Cursor anthology = fetchAnthologyTitleById(id)) {
            anthology.moveToFirst();
            position = anthology.getInt(anthology.getColumnIndexOrThrow(DOM_POSITION.name));
            book = anthology.getInt(anthology.getColumnIndexOrThrow(DOM_BOOK_ID.name));
        }

        // Delete the title
        boolean success = mSyncedDb.delete(DB_TB_ANTHOLOGY, DOM_ID + "=" + id, null) > 0;
        purgeAuthors();
        // Move all titles past the deleted book up one position
        String sql = "UPDATE " + DB_TB_ANTHOLOGY +
                " SET " + DOM_POSITION + "=" + DOM_POSITION + "-1" +
                " WHERE " + DOM_POSITION + ">" + position + " AND " + DOM_BOOK_ID + "=" + book + "";
        mSyncedDb.execSQL(sql);

        if (dirtyBookIfNecessary) {
            setBookDirty(book);
        }

        return success;
    }

    /** Statements used by {@link #getAnthologyTitleId } */
    private SynchronizedStatement mGetAnthologyTitleIdStmt = null;

    /**
     * Return the AnthologyTitle ID for a given book/author/title
     *
     * @param bookId		id of book
     * @param authorId		id of author
     * @param title			title
     *
     * @return				ID, or -1 if it does not exist
     */
    private long getAnthologyTitleId(final long bookId,
                                     final long authorId,
                                     @NonNull final String title) {
        if (mGetAnthologyTitleIdStmt == null) {
            // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
            String sql = "Select Coalesce( Min(" + DOM_ID + "),-1) from " + DB_TB_ANTHOLOGY	+
                    " Where " + DOM_BOOK_ID + " = ?" +
                    " and " + DOM_AUTHOR_ID + " = ?" +
                    " and " + DOM_TITLE + " = ? " +
                    COLLATION;
            mGetAnthologyTitleIdStmt = mStatements.add("mGetAnthologyTitleIdStmt", sql);
        }
        mGetAnthologyTitleIdStmt.bindLong(1, bookId);
        mGetAnthologyTitleIdStmt.bindLong(2, authorId);
        mGetAnthologyTitleIdStmt.bindString(3, title);
        // sql subst. -1 if not exists, so no SQLiteDoneException
        return mGetAnthologyTitleIdStmt.simpleQueryForLong();
    }

    /**
     * Return the largest anthology position (usually used for adding new titles)
     *
     * @param id id of book to retrieve
     * @return An integer of the highest position. 0 if it is not an anthology
     */
    private int getAnthologyPositionByBook(final long id) {
        String sql = "SELECT max(" + DOM_POSITION + ") FROM " + DB_TB_ANTHOLOGY +
                " WHERE " + DOM_BOOK_ID + "='" + id + "'";
        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            return getIntValue(cursor);
        }
    }

    /**
	 * Update the anthology title in the database
	 *
	 * @param id The rowId of the anthology title
	 * @param bookId The id of the book
	 * @param author The author
	 * @param title The title of the anthology story
	 * @return true/false on success
	 */
	@SuppressWarnings("unused")
    public boolean updateAnthologyTitle(final long id,
                                        final long bookId,
                                        @NonNull final Author author,
                                        @NonNull final String title,
                                        final boolean dirtyBookIfNecessary) {

		final long authorId = getAuthorIdOrInsert(author);

        final long existingId = getAnthologyTitleId(bookId, authorId, title);
		if (existingId >= 0 && existingId != id) {
            throw new AnthologyTitleExistsException();
        }

        final ContentValues values = new ContentValues();
		values.put(DOM_BOOK_ID.name, bookId);
		values.put(DOM_AUTHOR_ID.name, authorId);
		values.put(DOM_TITLE.name, title);
		boolean success = mSyncedDb.update(DB_TB_ANTHOLOGY, values, DOM_ID + "=" + id, null) > 0;
		purgeAuthors();

		if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

		return success;
	}

    /**
     * Return the AnthologyTitle ID
     *
     * @return	ID, or -1 if it does not exist
     */
    public long getAnthologyTitleId(@NonNull final AnthologyTitle a) {
        return getAnthologyTitleId(a.getBookId(), a.getAuthor().getId(), a.getTitle());
    }

    /**
	 * Move the given title up/down one position
	 *
	 * @param id The rowId of the title
	 * @param up true if going up, false if going down
     *
	 * @return new position
	 */
	@SuppressWarnings("unused")
    public int updateAnthologyTitlePosition(final long id,
                                            final boolean up,
                                            final boolean dirtyBookIfNecessary) {
        int book;
        int position;
		try (Cursor title = fetchAnthologyTitleById(id)) {
            title.moveToFirst();
            book = title.getInt(title.getColumnIndexOrThrow(DOM_BOOK_ID.name));
            position = title.getInt(title.getColumnIndexOrThrow(DOM_POSITION.name));
        }

		int max_position = getAnthologyPositionByBook(id);

		if ((position == 1 && up) || (position == max_position && !up)) {
			return 0;
		}

		String dir;
		String opp_dir;
		if (up) {
			dir = "-1";
			opp_dir = "+1";
		} else {
			dir = "+1";
			opp_dir = "-1";
		}
        String sql = "UPDATE " + DB_TB_ANTHOLOGY +
                " SET " + DOM_POSITION + "=" + DOM_POSITION + opp_dir +
                " WHERE " + DOM_BOOK_ID + "='" + book + "' AND " + DOM_POSITION + "=" + position + dir;
		mSyncedDb.execSQL(sql);

		sql = "UPDATE " + DB_TB_ANTHOLOGY +
                " SET " + DOM_POSITION + "=" + DOM_POSITION + dir +
		        " WHERE " + DOM_BOOK_ID + "='" + book + "' AND " + DOM_ID + "=" + id;
		mSyncedDb.execSQL(sql);

		if (dirtyBookIfNecessary) {
            setBookDirty(book);
        }

		return position;
	}

    public class AnthologyTitleExistsException extends RuntimeException {
        private static final long serialVersionUID = -9052087086134217566L;

        AnthologyTitleExistsException() {
            super("Anthology title already exists");
        }
    }

    /**
     * Return all the anthology titles and authors recorded for book
     *
     * @param id id of book to retrieve
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchAnthologyTitlesByBook(final long id) {
        String sql = "SELECT an." + DOM_ID + " as " + DOM_ID + "," +
                " an." + DOM_TITLE + " as " + DOM_TITLE + "," +
                " an." + DOM_POSITION + " as " + DOM_POSITION + "," +
                " au." + DOM_AUTHOR_FAMILY_NAME + " as " + DOM_AUTHOR_FAMILY_NAME + "," +
                " au." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_GIVEN_NAMES + "," +
                " au." + DOM_AUTHOR_FAMILY_NAME + " || ', ' || au." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_NAME + "," +
                " an." + DOM_BOOK_ID + " as " + DOM_BOOK_ID + "," +
                " an." + DOM_AUTHOR_ID + " as " + DOM_AUTHOR_ID + "," +
                " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au," +
                " WHERE an." + DOM_AUTHOR_ID + "=au." + DOM_ID + " AND an." + DOM_BOOK_ID + "='" + id + "'," +
                " ORDER BY an." + DOM_POSITION + "";
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return a specific anthology titles and authors recorded for book
     *
     * @param id id of anthology to retrieve
     * @return Cursor containing all records, if any
     */
    @NonNull
    private Cursor fetchAnthologyTitleById(final long id) {
        String sql = "SELECT an." + DOM_ID + " as " + DOM_ID + "," +
                " an." + DOM_TITLE + " as " + DOM_TITLE + "," +
                " an." + DOM_POSITION + " as " + DOM_POSITION  + "," +
                " au." + DOM_AUTHOR_FAMILY_NAME + " || ', ' || au." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_NAME + "," +
                " an." + DOM_BOOK_ID + " as " + DOM_BOOK_ID + "," +
                " an." + DOM_AUTHOR_ID + " as " + DOM_AUTHOR_ID + "," +
                " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au," +
                " WHERE an." + DOM_AUTHOR_ID + "=au." + DOM_ID + " AND an." + DOM_ID + "='" + id + "'";
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Author">

    /**
     * Create a new author in the database
     *
     * @return author id
     */
    private long insertAuthor(@NonNull final Author author) {
        ContentValues values = new ContentValues();
        values.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        values.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.insert(DB_TB_AUTHORS, null, values);
    }

    /**
     *@return the number of rows affected
     */
    private int updateAuthor(@NonNull final Author author) {
        ContentValues v = new ContentValues();
        v.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        v.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.update(DB_TB_AUTHORS, v, DOM_ID + " = " + author.id, null);
    }

    /**
     * Add or update the passed author, depending whether author.id == 0.
     *
     * @return true when update/create was done + the updated Author
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean updateOrInsertAuthor(@NonNull final /* out */ Author author) {
        if (author.id != 0) {
            Author previous = this.getAuthorById(author.id);
            if (author.equals(previous)) {
                return true;
            }
            if (1 == updateAuthor(author)) {
                setBooksDirtyByAuthor(author.id);
                return true;
            } else {
                return false;
            }
        } else {
            author.id = insertAuthor(author);
            return true;
        }
    }

    /**
     * This will return the author based on the ID.
     *
     * @return the author, or null if not found
     */
    @Nullable
    public Author getAuthorById(final long id) {
        String sql = "Select " + DOM_AUTHOR_FAMILY_NAME + ", " + DOM_AUTHOR_GIVEN_NAMES +
                " From " + DB_TB_AUTHORS +
                " Where " + DOM_ID + " = " + id;
        try (Cursor c = mSyncedDb.rawQuery(sql, null)) {
            if (!c.moveToFirst())
                return null;
            return new Author(id, c.getString(0), c.getString(1));
        }
    }

    /** Statements used by {@link #getAuthorIdByName } */
    private SynchronizedStatement mGetAuthorIdStmt = null;

    /**
     * @return author id, or 0 when not found
     */
    public long getAuthorIdByName(@NonNull final Author author) {
        if (mGetAuthorIdStmt == null) {
            mGetAuthorIdStmt = mStatements.add("mGetAuthorIdStmt",
                    "Select " + DOM_ID + " From " + DB_TB_AUTHORS +
                            " Where Upper(" + DOM_AUTHOR_FAMILY_NAME + ") = Upper(?) " + COLLATION +
                            " And Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") = Upper(?)" + COLLATION);
        }

        mGetAuthorIdStmt.bindString(1, author.familyName);
        mGetAuthorIdStmt.bindString(2, author.givenNames);
        try {
            return mGetAuthorIdStmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignore) {
            return 0;
        }
    }

    private long getAuthorIdOrInsert(@NonNull final Author author) {
        long id = getAuthorIdByName(author);
        return id == 0 ? insertAuthor(author) : id;
    }

    /**
     * @param tableAlias    alias to apply to the table
     * @param idAlias       alias to apply to the ID column
     *
     * @return a string with all the fields concatenated, suitable for a select
     */
    private static String getAuthorFields(@SuppressWarnings("SameParameterValue") @NonNull final String tableAlias, @Nullable final String idAlias) {
        String sql;
        if (idAlias != null && !idAlias.isEmpty()) {
            sql = " " + tableAlias + "." + DOM_ID + " as " + idAlias + ", ";
        } else {
            sql = " ";
        }

        return sql + tableAlias + "." + DOM_AUTHOR_FAMILY_NAME + " as " + DOM_AUTHOR_FAMILY_NAME + "," +
                " " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_GIVEN_NAMES + "," +
                "  Case When " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME +
                "        Else " + tableAlias + "." + DOM_AUTHOR_FAMILY_NAME + " || ', ' || " +
                "             " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " End as " + DOM_AUTHOR_FORMATTED +
                ",  Case When " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME +
                "        Else " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " || ' ' || " +
                tableAlias + "." + DOM_AUTHOR_FAMILY_NAME + " End as " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST + " ";
    }


    /**
     * Refresh the passed author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the author.
     */
    public void refreshAuthor(@NonNull final Author author) {
        if (author.id == 0) {
            // It wasn't a known author; see if it is now. If so, update ID.
            long id = getAuthorIdByName(author);
            // If we have a match, just update the object
            if (id != 0) {
                author.id = id;
            }
        } else {
            // It was a known author, see if it still is and update fields.
            Author dbAuthor = this.getAuthorById(author.id);
            if (dbAuthor != null) {
                // id stays obv.
                author.familyName = dbAuthor.familyName;
                author.givenNames = dbAuthor.givenNames;
            } else {
                // gone, set the author as 'new'
                author.id = 0;
            }
        }
    }

    /**
     * Update or create the passed author.
     * First tries to find the id, based on the name
     * if found , update the id
     * calls {@link #updateOrInsertAuthor}
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertAuthorByName(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author);
        }
        return updateOrInsertAuthor(author);
    }

    /**
     *
     * @throws SQLiteDoneException FIXME
     */
    public void globalReplaceAuthor(@NonNull final Author from, @NonNull final Author to) throws SQLiteDoneException{
        // Create or update the new author
        if (to.id == 0) {
            long id = getAuthorIdByName(to);
            // If we have a match, just update the object
            if (id != 0) {
                to.id = id;
            }
            updateOrInsertAuthor(to);
        } else
            updateOrInsertAuthorByName(to);

        // Do some basic sanity checks
        if (from.id == 0) {
            from.id = getAuthorIdByName(from);
        }
        if (from.id == 0) {
            throw new RuntimeException("Old Author is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyByAuthor(from.id);

            // First handle anthologies; they have a single author and are easy
            String sql = "Update " + DB_TB_ANTHOLOGY + " set " + DOM_AUTHOR_ID + " = " + to.id
                    + " Where " + DOM_AUTHOR_ID + " = " + from.id;
            mSyncedDb.execSQL(sql);

            globalReplacePositionedBookItem(DB_TB_BOOK_AUTHOR, DOM_AUTHOR_ID.name, DOM_AUTHOR_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * @return a complete list of author names from the database; used for AutoComplete.
     */
    @NonNull
    public ArrayList<String> getAllAuthors() {
        ArrayList<String> list = new ArrayList<>();
        try(Cursor cursor = classicFetchAllAuthors())  {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DOM_AUTHOR_FORMATTED.name));
                list.add(name);
            }
            return list;
        }
    }

    /** Statements for {@link #purgeAuthors} */
    private SynchronizedStatement mPurgeBookAuthorsStmt = null;
    /** Statements for {@link #purgeAuthors} */
    private SynchronizedStatement mPurgeAuthorsStmt = null;
    /**
     * Delete the author with the given rowId
     *
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean purgeAuthors() {
        // Delete DB_TB_BOOK_AUTHOR with no books
        if (mPurgeBookAuthorsStmt == null) {
            mPurgeBookAuthorsStmt = mStatements.add("mPurgeBookAuthorsStmt",
                    "Delete from " + DB_TB_BOOK_AUTHOR + " Where " +
                            DOM_BOOK_ID + " Not In (SELECT DISTINCT " + DOM_ID + " FROM " + DB_TB_BOOKS + ") ");
        }

        boolean success;
        try {
            mPurgeBookAuthorsStmt.execute();
            success = true;
        } catch (Exception ignore) {
            Logger.logError(ignore, "Failed to purge Book Authors");
            success = false;
        }
        // even if above purge failed, do try the next one
        if (mPurgeAuthorsStmt == null) {
            mPurgeAuthorsStmt = mStatements.add("mPurgeAuthorsStmt",
                    "Delete from " + DB_TB_AUTHORS + " Where " +
                    DOM_ID + " Not In (SELECT DISTINCT " + DOM_AUTHOR_ID + " FROM " + DB_TB_BOOK_AUTHOR + ")" +
                    " And " + DOM_ID + " Not In (SELECT DISTINCT " + DOM_AUTHOR_ID + " FROM " + DB_TB_ANTHOLOGY + ")");
        }

        try {
            mPurgeAuthorsStmt.execute();
        } catch (Exception ignore) {
            Logger.logError(ignore, "Failed to purge Authors");
            return false;
        }

        return success;
    }

    /**
     * Return a Cursor over the list of all authors  in the database for the given book
     *
     * @param bookId the rowId of the book
     * @return Cursor over all authors
     */
    @NonNull
    private Cursor fetchAllAuthorsByBook(final long bookId) {
        String sql = "SELECT DISTINCT a." + DOM_ID + " as " + DOM_ID + "," +
                " a." + DOM_AUTHOR_FAMILY_NAME + " as " + DOM_AUTHOR_FAMILY_NAME + "," +
                " a." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_GIVEN_NAMES + "," +
                " Case When a." + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME +
                "  Else " + authorFormattedSource("") +
                " End as " + DOM_AUTHOR_FORMATTED + "," +
                "  ba." + DOM_AUTHOR_POSITION +
                " FROM " + DB_TB_BOOK_AUTHOR + " ba JOIN " + DB_TB_AUTHORS + " a ON a." + DOM_ID + " = ba." + DOM_AUTHOR_ID +
                " WHERE ba." + DOM_BOOK_ID + "=" + bookId + " " +
                " ORDER BY ba." + DOM_AUTHOR_POSITION + " Asc, Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + " ASC," +
                " Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + " ASC";

        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    @NonNull
    private String authorOnBookshelfSql(@NonNull final String bookshelf, @NonNull final String authorIdSpec, final boolean first) {
        StringBuilder sql = new StringBuilder(" Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba" +
                " JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs ON bbs." + DOM_BOOK_ID + " = ba." + DOM_BOOK_ID +
                " JOIN " + DB_TB_BOOKSHELF + " bs ON bs." + DOM_ID + " = bbs." + DOM_BOOKSHELF_NAME +
                " WHERE ba." + DOM_AUTHOR_ID + " = " + authorIdSpec +
                "   AND " + makeTextTerm("bs." + DOM_BOOKSHELF_NAME, "=", bookshelf));
        if (first) {
            sql.append("AND ba.").append(DOM_AUTHOR_POSITION).append("=1");
        }
        sql.append(")");
        return sql.toString();
    }

    @NonNull
    private String authorFormattedSource(@SuppressWarnings("SameParameterValue") @NonNull String alias) {
        if (!alias.isEmpty()) {
            alias += ".";
        }
        return alias + DOM_AUTHOR_FAMILY_NAME + "||', '||" + DOM_AUTHOR_GIVEN_NAMES;
    }

    /** used in {@link #getAuthorBookCount} */
    private SynchronizedStatement mGetAuthorBookCountQuery = null;
    public long getAuthorBookCount(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author);
        }

        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorBookCountQuery == null) {
            String sql = "Select Count(" + DOM_BOOK_ID + ") From " + DB_TB_BOOK_AUTHOR + " Where " + DOM_AUTHOR_ID + "=?";
            mGetAuthorBookCountQuery = mStatements.add("mGetAuthorBookCountQuery", sql);
        }
        // Be cautious
        synchronized(mGetAuthorBookCountQuery) {
            mGetAuthorBookCountQuery.bindLong(1, author.id);
            // count, so no SQLiteDoneException
            return mGetAuthorBookCountQuery.simpleQueryForLong();
        }
    }

    /** used in {@link #getAuthorAnthologyCount} */
    private SynchronizedStatement mGetAuthorAnthologyCountQuery = null;
    public long getAuthorAnthologyCount(@NonNull final Author a)  {
        if (a.id == 0) {
            a.id = getAuthorIdByName(a);
        }
        if (a.id == 0) {
            return 0;
        }

        if (mGetAuthorAnthologyCountQuery == null) {
            String sql = "Select Count(" + DOM_ID + ") From " + DB_TB_ANTHOLOGY + " Where " + DOM_AUTHOR_ID + "=?";
            mGetAuthorAnthologyCountQuery = mStatements.add("mGetAuthorAnthologyCountQuery", sql);
        }
        // Be cautious
        synchronized(mGetAuthorAnthologyCountQuery) {
            mGetAuthorAnthologyCountQuery.bindLong(1, a.id);
            // count, so no SQLiteDoneException
            return mGetAuthorAnthologyCountQuery.simpleQueryForLong();
        }

    }
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Classic Author">

    /**
     * Return the position of an author in a list of all authors (within a bookshelf)
     *
     * @param author    The author to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     *
     * @return The position of the author, or 0 when not found
     */
    public int classicGetAuthorPositionByGivenName(@NonNull final Author author, @NonNull final String bookshelf) {

        String where = null;
        if (!bookshelf.isEmpty()) {
            where = authorOnBookshelfSql(bookshelf, "a." + DOM_ID, false);
        }
        if (where != null && !where.isEmpty())
            where = " and " + where;

        String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a" +
                " WHERE ( " + makeTextTerm("a." + DOM_AUTHOR_GIVEN_NAMES, "<", author.familyName) +
                " OR ( " + makeTextTerm("a." + DOM_AUTHOR_GIVEN_NAMES, "=", author.familyName) +
                "     AND " + makeTextTerm("a." + DOM_AUTHOR_FAMILY_NAME, "<", author.givenNames) + "))" +
                " " + where +
                " ORDER BY Upper(a." + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + ", Upper(a." + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return getIntValue(cursor);
        }
    }

    /**
     * Return the position of an author in a list of all authors (within a bookshelf)
     *
     * @param author    The author to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return The position of the author, or 0 when not found
     */
    public int classicGetAuthorPositionByName(@NonNull final Author author, @NonNull final String bookshelf) {

        String where = "";
        if (!bookshelf.isEmpty()) {
            where += authorOnBookshelfSql(bookshelf, "a." + DOM_ID, false);
        }
        if (!where.isEmpty())
            where = " and " + where;

        String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a" +
                " WHERE ( " + makeTextTerm("a." + DOM_AUTHOR_FAMILY_NAME, "<", author.familyName) +
                " OR ( " + makeTextTerm("a." + DOM_AUTHOR_FAMILY_NAME, "=", author.familyName) +
                "     AND " + makeTextTerm("a." + DOM_AUTHOR_GIVEN_NAMES, "<", author.givenNames) + "))" +
                " " + where +
                " ORDER BY Upper(a." + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", Upper(a." + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return getIntValue(cursor);
        }
    }

    /**
     * Return a Cursor over the list of all books in the database
     *
     * @return Cursor over all notes
     */
    @NonNull
    private Cursor classicFetchAllAuthors() {
        return classicFetchAllAuthors(true, false);
    }

    /**
     * Return a Cursor over the list of all books in the database
     *
     * @param sortByFamily		flag
     * @param firstOnly			flag
     * @return Cursor over all notes
     */
    @NonNull
    private Cursor classicFetchAllAuthors(final boolean sortByFamily, final boolean firstOnly) {
        String order;
        if (sortByFamily) {
            order = " ORDER BY Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
        } else {
            order = " ORDER BY Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION;
        }

        String sql = "SELECT DISTINCT " + getAuthorFields("a", DOM_ID.name) +
                " FROM " + DB_TB_AUTHORS + " a, " + DB_TB_BOOK_AUTHOR + " ab " +
                " WHERE a." + DOM_ID + "=ab." + DOM_AUTHOR_ID + " ";
        if (firstOnly) {
            sql += " AND ab." + DOM_AUTHOR_POSITION + "=1 ";
        }

        return mSyncedDb.rawQuery(sql + order, new String[]{});
    }

    /**
     * Return a Cursor over the list of all authors in the database
     *
     * @param bookshelf Which bookshelf is it in. Can be "All Books"
     *
     * @return Cursor over all notes
     */
    @NonNull
    public Cursor classicFetchAllAuthors(String bookshelf) {
        return classicFetchAllAuthors(bookshelf, true, false);
    }

    /**
     * Return a Cursor over the list of all authors in the database
     *
     * @param bookshelf Which bookshelf is it in. Can be "All Books"
     *
     * @return Cursor over all notes
     */
    @NonNull
    public Cursor classicFetchAllAuthors(@Nullable final String bookshelf, final boolean sortByFamily, final boolean firstOnly) {
        if (bookshelf == null || bookshelf.isEmpty()) {
            return classicFetchAllAuthors(sortByFamily, firstOnly);
        }

        String order;
        if (sortByFamily) {
            order = " ORDER BY Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
        } else {
            order = " ORDER BY Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION;
        }

        String sql = "SELECT " + getAuthorFields("a", DOM_ID.name)
                + " FROM " + DB_TB_AUTHORS + " a "
                + " WHERE " + authorOnBookshelfSql(bookshelf, "a." + DOM_ID, firstOnly)
                + order;

        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return a Cursor over the author in the database which meet the provided search criteria
     *
     * @param searchText The search query
     * @param bookshelf The bookshelf to search within
     * @return Cursor over all authors
     */
    @NonNull
    public Cursor classicFetchAuthors(@NonNull final String searchText, @NonNull final String bookshelf) {
        return classicFetchAuthors(searchText, bookshelf, true, false);
    }

    /**
     * Return a Cursor over the author in the database which meet the provided search criteria
     *
     * @param searchText The search query
     * @param bookshelf The bookshelf to search within
     * @return Cursor over all authors
     */
    @NonNull
    public Cursor classicFetchAuthors(@NonNull String searchText, @NonNull final String bookshelf,
                                      final boolean sortByFamily, final boolean firstOnly) {
        String where = "";
        String baWhere = "";
        searchText = encodeString(searchText);
        if (!bookshelf.isEmpty()) {
            where += " AND " + this.authorOnBookshelfSql(bookshelf, "a." + DOM_ID, false);
        }
        if (firstOnly) {
            baWhere += " AND ba." + DOM_AUTHOR_POSITION + "=1 ";
        }
        //if (where != null && where.trim().length() > 0)
        //	where = " and " + where;

        final String order;
        if (sortByFamily) {
            order = " ORDER BY Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
        } else {
            order = " ORDER BY Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION;
        }

        String sql = "SELECT " + getAuthorFields("a", DOM_ID.name) +
                " FROM " + DB_TB_AUTHORS + " a" + " " +
                "WHERE (" + authorSearchPredicate(searchText) +  " OR " +
                "a." + DOM_ID + " IN (SELECT ba." + DOM_AUTHOR_ID +
                " FROM " + DB_TB_BOOKS + " b Join " + DB_TB_BOOK_AUTHOR + " ba " +
                " On ba." + DOM_BOOK_ID + " = b." + DOM_ID + " " + baWhere +
                "WHERE (" + bookSearchPredicate(searchText)  + ") ) )" +
                where + order;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Book">

    /**
     * Examine the values and make any changes necessary before writing the data.
     *
     * @param bookData	Collection of field values.
     */
    private void preprocessOutput(final boolean isNew, @NonNull final BookData bookData) {

        // Handle AUTHOR
        {
            // If present, get the author ID from the author name (it may have changed with a name change)
            if (bookData.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {
                Author author = Author.toAuthor(bookData.getString(UniqueId.KEY_AUTHOR_FORMATTED));
                bookData.putLong(UniqueId.KEY_AUTHOR_ID, getAuthorIdOrInsert(author));
            } else {
                if (bookData.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
                    String family = bookData.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
                    String given;
                    if (bookData.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                        given = bookData.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
                    } else {
                        given = "";
                    }
                    bookData.putLong(UniqueId.KEY_AUTHOR_ID, getAuthorIdOrInsert(new Author(family, given)));
                }
            }
        }

        // Handle TITLE; but only for new books
        {
            if (isNew && bookData.containsKey(UniqueId.KEY_TITLE)) {
                /* Move "The, A, An" to the end of the string */
                String title = bookData.getString(UniqueId.KEY_TITLE);
                StringBuilder newTitle = new StringBuilder();
                String[] title_words = title.split(" ");
                try {
                    if (title_words[0].matches(mContext.getResources().getString(R.string.title_reorder))) {
                        for (int i = 1; i < title_words.length; i++) {
                            if (i != 1) {
                                newTitle.append(" ");
                            }
                            newTitle.append(title_words[i]);
                        }
                        newTitle.append(", ").append(title_words[0]);
                        bookData.putString(UniqueId.KEY_TITLE, newTitle.toString());
                    }
                } catch (Exception ignore) {
                    //do nothing. Title stays the same
                }
            }
        }

        // Remove blank/null fields that have default values defined in the database
        // or which should never be blank.
        for (String name : new String[] {
                UniqueId.KEY_BOOK_UUID,
                UniqueId.KEY_ANTHOLOGY_MASK,
                UniqueId.KEY_RATING,
                UniqueId.KEY_BOOK_READ,
                UniqueId.KEY_BOOK_SIGNED,
                UniqueId.KEY_BOOK_DATE_ADDED,
                UniqueId.KEY_GOODREADS_LAST_SYNC_DATE,
                UniqueId.KEY_LAST_UPDATE_DATE }) {
            if (bookData.containsKey(name)) {
                Object o = bookData.get(name);
                // Need to allow for the possibility the stored value is not
                // a string, in which case getString() would return a NULL.
                if (o == null || o.toString().isEmpty()) {
                    bookData.remove(name);
                }
            }
        }
    }


    /** Used in {@link #getBookUuid} */
    private SynchronizedStatement mGetBookUuidQuery = null;

    /**
     * Utility routine to return the book uuid based on the id.
     *
     * @param id of the book
     *
     * @return the book uuid
     *
     * @throws SQLiteDoneException if not found FIXME
     */
    @NonNull
    public String getBookUuid(final long id) throws SQLiteDoneException {
        if (mGetBookUuidQuery == null) {
            String sql = "Select " + DOM_BOOK_UUID + " From " + DB_TB_BOOKS + " Where " + DOM_ID + "=?";
            mGetBookUuidQuery = mStatements.add("mGetBookUuidQuery", sql);
        }
        // Be cautious; other threads may call this and set parameters.
        synchronized(mGetBookUuidQuery) {
            mGetBookUuidQuery.bindLong(1, id);
            return mGetBookUuidQuery.simpleQueryForString();
        }
    }

    /** Used in {@link #getBookIdFromUuid} */
    private SynchronizedStatement mGetBookIdFromUuidStmt = null;

    /**
     * Check that a book with the passed UUID exists and return the ID of the book, or zero
     *
     * @param uuid		UUID of book
     *
     * @return			ID of the book, or zero if not found
     */
    public long getBookIdFromUuid(@NonNull final String uuid) {
        if (mGetBookIdFromUuidStmt == null) {
            mGetBookIdFromUuidStmt = mStatements.add("mGetBookIdFromUuidStmt",
                    "Select " + DOM_ID + " From " + DB_TB_BOOKS + " Where " + DOM_BOOK_UUID + " = ?");
        }
        mGetBookIdFromUuidStmt.bindString(1, uuid);
        try {
            return mGetBookIdFromUuidStmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignore) {
            Logger.logError(ignore);
            return 0;
        }
    }

    /** Used in {@link #getBookUpdateDate} */
    private SynchronizedStatement mGetBookUpdateDateQuery = null;
    /**
     * Utility routine to return the book title based on the id.
     *
     * @throws SQLiteDoneException if not found
     */
    @NonNull
    public String getBookUpdateDate(final long bookId) throws SQLiteDoneException {
        if (mGetBookUpdateDateQuery == null) {
            mGetBookUpdateDateQuery = mStatements.add("mGetBookUpdateDateQuery",
                    "Select " + DOM_LAST_UPDATE_DATE + " From " + DB_TB_BOOKS + " Where " + DOM_ID + "=?");
        }
        // Be cautious
        synchronized(mGetBookUpdateDateQuery) {
            mGetBookUpdateDateQuery.bindLong(1, bookId);
            return mGetBookUpdateDateQuery.simpleQueryForString();
        }
    }

    /** Used in {@link #getBookTitle} */
    private SynchronizedStatement mGetBookTitleQuery = null;
    /**
     * Utility routine to return the book title based on the id.
     *
     * @throws SQLiteDoneException if not found
     */
    @NonNull
    public String getBookTitle(final long id) throws SQLiteDoneException {
        if (mGetBookTitleQuery == null) {
            mGetBookTitleQuery = mStatements.add("mGetBookTitleQuery",
                    "Select " + DOM_TITLE + " From " + DB_TB_BOOKS + " Where " + DOM_ID + "=?");
        }
        // Be cautious
        synchronized(mGetBookTitleQuery) {
            mGetBookTitleQuery.bindLong(1, id);
            return mGetBookTitleQuery.simpleQueryForString();
        }
    }

    /**
     *
     * @return  set of fields suitable for a select
     */
    @NonNull
    private static String getBookFields(@SuppressWarnings("SameParameterValue") @NonNull final String tableAlias, @Nullable final String idAlias) {
        String sql;
        if (idAlias != null && !idAlias.isEmpty()) {
            sql = tableAlias + "." + DOM_ID + " as " + idAlias + ", ";
        } else {
            sql = "";
        }
        return sql + tableAlias + "." + DOM_TITLE + " as " + DOM_TITLE + ", " +
                // Find FIRST series ID.
                "(Select " + DOM_SERIES_ID + " From " + DB_TB_BOOK_SERIES + " bs " +
                " where bs." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID + " Order by " + DOM_SERIES_POSITION + " asc  Limit 1) as " + DOM_SERIES_ID + ", " +
                // Find FIRST series NUM.
                "(Select " + DOM_SERIES_NUM + " From " + DB_TB_BOOK_SERIES + " bs " +
                " where bs." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID + " Order by " + DOM_SERIES_POSITION + " asc  Limit 1) as " + DOM_SERIES_NUM + ", " +
                // Get the total series count
                "(Select Count(*) from " + DB_TB_BOOK_SERIES + " bs Where bs." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID + ") as _num_series," +
                // Find the first AUTHOR ID
                "(Select " + DOM_AUTHOR_ID + " From " + DB_TB_BOOK_AUTHOR + " ba " +
                "   where ba." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID +
                "   order by " + DOM_AUTHOR_POSITION + ", ba." + DOM_AUTHOR_ID + " Limit 1) as " + DOM_AUTHOR_ID + ", " +
                // Get the total author count
                "(Select Count(*) from " + DB_TB_BOOK_AUTHOR + " ba Where ba." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID + ") as _num_authors," +

                tableAlias + "." + DOM_ISBN + " as " + DOM_ISBN + ", " +
                tableAlias + "." + DOM_PUBLISHER + " as " + DOM_PUBLISHER + ", " +
                tableAlias + "." + DOM_BOOK_DATE_PUBLISHED + " as " + DOM_BOOK_DATE_PUBLISHED + ", " +
                tableAlias + "." + DOM_BOOK_RATING + " as " + DOM_BOOK_RATING + ", " +
                tableAlias + "." + DOM_BOOK_READ + " as " + DOM_BOOK_READ + ", " +
                tableAlias + "." + DOM_BOOK_PAGES + " as " + DOM_BOOK_PAGES + ", " +
                tableAlias + "." + DOM_NOTES + " as " + DOM_NOTES + ", " +
                tableAlias + "." + DOM_BOOK_LIST_PRICE + " as " + DOM_BOOK_LIST_PRICE + ", " +
                tableAlias + "." + DOM_ANTHOLOGY_MASK + " as " + DOM_ANTHOLOGY_MASK + ", " +
                tableAlias + "." + DOM_BOOK_LOCATION + " as " + DOM_BOOK_LOCATION + ", " +
                tableAlias + "." + DOM_BOOK_READ_START + " as " + DOM_BOOK_READ_START + ", " +
                tableAlias + "." + DOM_BOOK_READ_END + " as " + DOM_BOOK_READ_END + ", " +
                tableAlias + "." + DOM_BOOK_FORMAT + " as " + DOM_BOOK_FORMAT + ", " +
                tableAlias + "." + DOM_BOOK_SIGNED + " as " + DOM_BOOK_SIGNED + ", " +
                tableAlias + "." + DOM_DESCRIPTION + " as " + DOM_DESCRIPTION + ", " +
                tableAlias + "." + DOM_BOOK_GENRE + " as " + DOM_BOOK_GENRE + ", " +
                tableAlias + "." + DOM_BOOK_LANGUAGE + " as " + DOM_BOOK_LANGUAGE + ", " +
                tableAlias + "." + DOM_BOOK_DATE_ADDED + " as " + DOM_BOOK_DATE_ADDED + ", " +
                tableAlias + "." + DOM_GOODREADS_BOOK_ID  + " as " + DOM_GOODREADS_BOOK_ID + ", " +
                tableAlias + "." + DOM_GOODREADS_LAST_SYNC_DATE + " as " + DOM_GOODREADS_LAST_SYNC_DATE + ", " +
                tableAlias + "." + DOM_LAST_UPDATE_DATE  + " as " + DOM_LAST_UPDATE_DATE + ", " +
                tableAlias + "." + DOM_BOOK_UUID  + " as " + DOM_BOOK_UUID;
    }

    /**
     * Delete the book with the given rowId
     *
     * @param bookId id of book to delete
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteBook(final long bookId) {
        String uuid = null;
        try {
            uuid = this.getBookUuid(bookId);
        } catch (Exception e) {
            Logger.logError(e, "Failed to get book UUID");
        }

        boolean success = mSyncedDb.delete(DB_TB_BOOKS, DOM_ID + "=" + bookId, null) > 0;
        purgeAuthors();

        try {
            deleteFts(bookId);
        } catch (Exception e) {
            Logger.logError(e, "Failed to delete FTS");
        }
        // Delete thumbnail(s)
        if (uuid != null) {
            try {
                File f = StorageUtils.getThumbnailByUuid(uuid);
                while (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    f = StorageUtils.getThumbnailByUuid(uuid);
                }
            } catch (Exception e) {
                Logger.logError(e, "Failed to delete cover thumbnail");
            }
        }

        if (uuid != null && !uuid.isEmpty()) {
            try(CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(mContext)) {
                coversDbHelper.eraseCachedBookCover(uuid);
            }
        }

        return success;
    }

    /**
     * Create a new book using the details provided. If the book is
     * successfully created return the new rowId for that book, otherwise return
     * a -1 to indicate failure.
     *
     * @param bookData 	A ContentValues collection with the columns to be updated. May contain extrat data.
     * @param flags  	See BOOK_UPDATE_* flag definitions
     *
     * @return rowId or -1 if failed
     */
    public long insertBook(@NonNull final BookData bookData, final int flags) {
        return insertBook(0, bookData, flags);
    }

    /**
     * Create a new book using the details provided. If the book is
     * successfully created return the new rowId for that book, otherwise return
     * a -1 to indicate failure.
     *
     * @param bookId 		The ID of the book to insert (this will overwrite the normal autoIncrement)
     * @param bookData 	    A ContentValues collection with the columns to be updated. May contain extra data.
     * @param flags  	See BOOK_UPDATE_* flag definitions
     *
     * @return rowId or -1 if failed
     */
    public long insertBook(final long bookId, @NonNull final BookData bookData, @SuppressWarnings("unused") final int flags) {

        try {
            // Make sure we have the target table details
            if (mBooksInfo == null) {
                mBooksInfo = new TableInfo(mSyncedDb, DB_TB_BOOKS);
            }

            // Cleanup fields (author, series, title and remove blank fields for which we have defaults)
            preprocessOutput(bookId == 0, bookData);

            /* TODO We may want to provide default values for these fields:
             * KEY_RATING, KEY_BOOK_READ, KEY_NOTES, KEY_BOOK_LOCATION, KEY_BOOK_READ_START, KEY_BOOK_READ_END, KEY_BOOK_SIGNED, & DATE_ADDED
             */
            if (!bookData.containsKey(UniqueId.KEY_BOOK_DATE_ADDED)) {
                bookData.putString(UniqueId.KEY_BOOK_DATE_ADDED, DateUtils.toSqlDateTime(new Date()));
            }

            // Make sure we have an author
            ArrayList<Author> authors = bookData.getAuthors();
            if (authors.size() == 0) {
                throw new IllegalArgumentException();
            }

            ContentValues values = filterValues(bookData);
            if (bookId > 0) {
                values.put(DOM_ID.name, bookId);
            }

            if (!values.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                values.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(new Date()));
            }

            // This was commented out in the original code... so much for ALWAYS ?
            // ALWAYS set the INSTANCE_UPDATE_DATE; this is used for backups
            //values.put(DOM_INSTANCE_UPDATE_DATE.name, Utils.toSqlDateTime(Calendar.getInstance().getTime()));

            long newBookId = mSyncedDb.insert(DB_TB_BOOKS, null, values);

            String bookshelf = bookData.getBookshelfList();
            if (bookshelf != null && !bookshelf.trim().isEmpty()) {
                insertBookBookshelf(newBookId, ArrayUtils.decodeList(EditBookFieldsFragment.BOOKSHELF_SEPARATOR, bookshelf), false);
            }

            insertBookAuthors(newBookId, authors, false);
            insertBookSeries(newBookId, bookData.getSeries(), false);
            insertBookAnthologyTitles(newBookId, bookData.getAnthologyTitles(), false);

            try {
                insertFts(newBookId);
            } catch (Exception e) {
                Logger.logError(e, "Failed to update FTS");
            }

            return newBookId;
        } catch (Exception e) {
            Logger.logError(e);
            throw new RuntimeException("Error creating book from " + bookData + ": " + e.getMessage(), e);
        }
    }
    /** Flag indicating the UPDATE_DATE field from the bundle should be truested. If this flag is not set, the UPDATE_DATE will be set based on the current time */
    public static final int BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT = 1;
    /** Flag indicating to skip doing the 'purge' step; mainly used in batch operations. */
    public static final int BOOK_UPDATE_SKIP_PURGE_REFERENCES = 2;

    /**
     * Update the book using the details provided. The book to be updated is
     * specified using the rowId, and it is altered to use values passed in
     *
     * @param bookId The id of the book in the database
     * @param bookData A ContentValues collection with the columns to be updated. May contain extra data.
     * @param flags  See BOOK_UPDATE_* flag definitions
     *
     * @return true if the note was successfully updated, false otherwise
     */
    public boolean updateBook(final long bookId, @NonNull final BookData bookData, final int flags) {
        try {
            // Make sure we have the target table details
            if (mBooksInfo == null) {
                mBooksInfo = new TableInfo(mSyncedDb, DB_TB_BOOKS);
            }

            // Cleanup fields (author, series, title and remove blank fields for which we have defaults)
            preprocessOutput(bookId == 0, bookData);

            ContentValues values = filterValues(bookData);

            // Disallow UUID updates
            if (values.containsKey(DOM_BOOK_UUID.name)) {
                values.remove(DOM_BOOK_UUID.name);
            }

            // We may be just updating series, or author lists but we still update the last_update_date.
            if ((flags & BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT) == 0 || !values.containsKey(DOM_LAST_UPDATE_DATE.name))
                values.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(Calendar.getInstance().getTime()));
            // ALWAYS set the INSTANCE_UPDATE_DATE; this is used for backups
            //args.put(DOM_INSTANCE_UPDATE_DATE.name, Utils.toSqlDateTime(Calendar.getInstance().getTime()));
            boolean success = mSyncedDb.update(DB_TB_BOOKS, values, DOM_ID + "=" + bookId, null) > 0;

            String bookshelf = bookData.getBookshelfList();
            if (bookshelf != null && !bookshelf.trim().isEmpty()) {
                insertBookBookshelf(bookId, ArrayUtils.decodeList(EditBookFieldsFragment.BOOKSHELF_SEPARATOR, bookshelf), false);
            }

            if (bookData.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)) {
                insertBookAuthors(bookId, bookData.getAuthors(), false);
            }
            if (bookData.containsKey(UniqueId.BKEY_SERIES_ARRAY)) {
                insertBookSeries(bookId, bookData.getSeries(), false);
            }

            if (bookData.containsKey(UniqueId.BKEY_ANTHOLOGY_TITLE_ARRAY)) {
                insertBookAnthologyTitles(bookId, bookData.getAnthologyTitles(), false);
            }

            // Only really skip the purge if a batch update of multiple books is being done.
            if ( (flags & BOOK_UPDATE_SKIP_PURGE_REFERENCES) == 0) {
                purgeAuthors();
                purgeSeries();
            }

            try {
                updateFts(bookId);
            } catch (Exception ignore) {
                Logger.logError(ignore, "Failed to update FTS");
            }
            return success;
        } catch (Exception e) {
            Logger.logError(e);
            throw new RuntimeException("Error updating book from " + bookData + ": " + e.getMessage(), e);
        }
    }

    /**
     * Return the number of books
     *
     * @return int The number of books
     */
    private int countBooks() {
        try (Cursor count = mSyncedDb.rawQuery("SELECT count(*) as count FROM " + DB_TB_BOOKS + " b ", new String[]{})){
            count.moveToNext();
            return count.getInt(0);
        } catch (IllegalStateException e) {
            Logger.logError(e);
            return 0;
        }
    }

    /**
     * Return the number of books
     *
     * @param bookshelf     the bookshelf to search within
     * @return              The number of books
     */
    public int countBooks(@NonNull final String bookshelf) {
        int result = 0;
        try {
            if (bookshelf.isEmpty()) {
                return countBooks();
            }
            final String sql = "SELECT count(DISTINCT b._id) as count " +
                    " FROM " + DB_TB_BOOKSHELF + " bs " +
                    " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs " +
                    "     On bbs." + DOM_BOOKSHELF_NAME + " = bs." + DOM_ID +
                    " Join " + DB_TB_BOOKS + " b " +
                    "     On bbs." + DOM_BOOK_ID + " = b." + DOM_ID +
                    " WHERE " + makeTextTerm("bs." + DOM_BOOKSHELF_NAME, "=", bookshelf);
            try (Cursor count = mSyncedDb.rawQuery(sql, new String[]{})) {
                count.moveToNext();
                result = count.getInt(0);
            }
        } catch (IllegalStateException e) {
            Logger.logError(e);
        }
        return result;
    }

    public long getBookCount() {
        String sql = "select Count(*) From " + TBL_BOOKS.ref();
        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    @NonNull
    public Cursor getBookUuidList() {
        String sql = "select " + DOM_BOOK_UUID + " as " + DOM_BOOK_UUID + " From " + TBL_BOOKS.ref();
        return mSyncedDb.rawQuery(sql);
    }

    /** used in {@link #getIdFromIsbn} */
    private SynchronizedStatement mGetIdFromIsbn1Stmt = null;
    /** used in {@link #getIdFromIsbn} */
    private SynchronizedStatement mGetIdFromIsbn2Stmt = null;

    /**
     * @param isbn The isbn to search by
     *
     * @return  book id, or 0 if not found
     */
    public long getIdFromIsbn(@NonNull final String isbn, final boolean checkAltIsbn) {
        SynchronizedStatement stmt;
        if (checkAltIsbn && IsbnUtils.isValid(isbn)) {
            if (mGetIdFromIsbn2Stmt == null) {
                mGetIdFromIsbn2Stmt = mStatements.add("mGetIdFromIsbn2Stmt",
                        "Select Coalesce(max(" + DOM_ID + "), -1) From " + DB_TB_BOOKS +
                                " Where Upper(" + DOM_ISBN + ") in (Upper(?), Upper(?))");
            }
            stmt = mGetIdFromIsbn2Stmt;
            stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
        } else {
            if (mGetIdFromIsbn1Stmt == null) {
                mGetIdFromIsbn1Stmt = mStatements.add("mGetIdFromIsbn1Stmt",
                        "Select Coalesce(max(" + DOM_ID + "), -1) From " + DB_TB_BOOKS +
                                " Where Upper(" + DOM_ISBN + ") = Upper(?)");
            }
            stmt = mGetIdFromIsbn1Stmt;
        }
        stmt.bindString(1, isbn);
        try {
            return stmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignore) {
            return 0;
        }
    }

    /**
     *
     * @param isbn The isbn to search by
     * @return boolean indicating ISBN already in DB
     */
    public boolean isbnExists(@NonNull final String isbn, final boolean checkAltIsbn) {
        return getIdFromIsbn(isbn, checkAltIsbn) > 0;
    }

    /** used in {@link #bookExists} */
    private SynchronizedStatement mCheckBookExistsStmt = null;

    /**
     * Check that a book with the passed ID exists
     *
     * @param bookId    of book
     *
     * @return	true if exists
     */
    public boolean bookExists(final long bookId) {
        if (mCheckBookExistsStmt == null) {
            mCheckBookExistsStmt = mStatements.add("mCheckBookExistsStmt",
                    "Select " + DOM_ID + " From " + DB_TB_BOOKS + " Where " + DOM_ID + " = ?");
        }
        mCheckBookExistsStmt.bindLong(1, bookId);
        try {
            mCheckBookExistsStmt.simpleQueryForLong();
            return true;
        } catch (SQLiteDoneException e) {
            return false;
        }
    }

    /**
     * Set a book as in need of backup if any ancillary data has changed.
     */
    private void setBookDirty(final long bookId) {
        String sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where " +
                TBL_BOOKS + "." + DOM_ID + " = " + bookId;
        mSyncedDb.execSQL(sql);
    }

    /**
     * Set all books referencing a given author as dirty.
     */
    private void setBooksDirtyByAuthor(final long authorId) {
        // Mark all related books based on anthology author as dirty
        String sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where" +
                " Exists(Select * From " + TBL_ANTHOLOGY.ref() + " Where " + TBL_ANTHOLOGY.dot(DOM_AUTHOR_ID) + " = " + authorId +
                " and " + TBL_ANTHOLOGY.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);

        // Mark all related books based on series as dirty
        sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where" +
                " Exists(Select * From " + TBL_BOOK_AUTHOR.ref() + " Where " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID) + " = " + authorId +
                " and " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Set all books referencing a given series as dirty.
     */
    private void setBooksDirtyBySeries(final long seriesId) {
        String sql;
        sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where" +
                " Exists(Select * From " + TBL_BOOK_SERIES.ref() + " Where " + TBL_BOOK_SERIES.dot(DOM_SERIES_ID) + " = " + seriesId +
                " and " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Set all books referencing a given bookshelf as dirty.
     */
    private void setBooksDirtyByBookshelf(final long bookshelfId) {
        String sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where" +
                " Exists(Select * From " + TBL_BOOK_BOOKSHELF.ref() + " Where " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOKSHELF_ID) + " = " + bookshelfId +
                " and " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);
    }

    /** Statement used in {@link #insertBookSeries} */
    private SynchronizedStatement mDeleteBookSeriesStmt = null;
    /** Statement used in {@link #insertBookSeries} */
    private SynchronizedStatement mAddBookSeriesStmt = null;

    private void insertBookSeries(long bookId,
                                  @Nullable final ArrayList<Series> series,
                                  @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // If we have series details, save them.
        if (series != null) {
            if (mDeleteBookSeriesStmt == null) {
                mDeleteBookSeriesStmt = mStatements.add("mDeleteBookSeriesStmt",
                        "Delete from " + DB_TB_BOOK_SERIES + " Where " + DOM_BOOK_ID + " = ?");
            }
            // Delete the current series
            mDeleteBookSeriesStmt.bindLong(1, bookId);
            mDeleteBookSeriesStmt.execute();


            if (mAddBookSeriesStmt == null) {
                mAddBookSeriesStmt = mStatements.add("mAddBookSeriesStmt",
                        "Insert Into " + DB_TB_BOOK_SERIES
                                + "(" + DOM_BOOK_ID + "," + DOM_SERIES_ID + "," + DOM_SERIES_NUM + "," + DOM_SERIES_POSITION + ")"
                                + " Values(?,?,?,?)");
            }

            //FIXME: is this still true ?
            // Setup the book in the ADD statement. This was once good enough, but
            // Android 4 (at least) causes the bindings to clean when executed. So
            // now we do it each time in loop.
            // mAddBookSeriesStmt.bindLong(1, bookId);
            // See call to releaseAndUnlock in:
            // 		http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.0.1_r1/android/database/sqlite/SQLiteStatement.java#SQLiteStatement.executeUpdateDelete%28%29
            // which calls clearBindings():
            //		http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.0.1_r1/android/database/sqlite/SQLiteStatement.java#SQLiteStatement.releaseAndUnlock%28%29
            //

            // Get the authors and turn into a list of names
            // The list MAY contain duplicates (eg. from Internet lookups of multiple
            // sources), so we track them in a hash map
            final HashMap<String, Boolean> idHash = new HashMap<>();
            int pos = 0;
            for (Series entry : series) {
                long seriesId = 0;
                try {
                    seriesId = getSeriesIdOrInsert(entry.name);
                    String uniqueId = seriesId + "(" + entry.number.trim().toUpperCase() + ")";
                    if (!idHash.containsKey(uniqueId)) {
                        idHash.put(uniqueId, true);
                        pos++;
                        mAddBookSeriesStmt.bindLong(1, bookId);
                        mAddBookSeriesStmt.bindLong(2, seriesId);
                        mAddBookSeriesStmt.bindString(3, entry.number);
                        mAddBookSeriesStmt.bindLong(4, pos);
                        mAddBookSeriesStmt.execute();
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                    throw new RuntimeException("Error adding series '" + entry.name +
                            "' {" + seriesId + "} to book " + bookId + ": " + e.getMessage(), e);
                }
            }
        }
    }


    private void insertBookAnthologyTitles(final long bookId,
                                           @NonNull final ArrayList<AnthologyTitle> list,
                                           @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        deleteAnthologyTitlesByBook(bookId, false);

        for (AnthologyTitle anthologyTitle : list) {
            long authorId = getAuthorIdOrInsert(anthologyTitle.getAuthor());
            insertAnthologyTitle(bookId, authorId, anthologyTitle.getTitle(), true, false);
        }
    }

    /** Statements used by {@link #insertBookAuthors} */
    private SynchronizedStatement mDeleteBookAuthorsStmt = null;
    /** Statements used by {@link #insertBookAuthors} */
    private SynchronizedStatement mAddBookAuthorsStmt = null;

    /**
     * If the passed ContentValues contains KEY_AUTHOR_LIST, parse them
     * and add the authors.
     *
     * @param bookId		            ID of book
     * @param dirtyBookIfNecessary      flag to set book dirty or not (for now, always false...)
     */
    private void insertBookAuthors(final long  bookId,
                                   @Nullable final ArrayList<Author> authors,
                                   @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // If we have author details, save them.
        if (authors != null) {
            if (mDeleteBookAuthorsStmt == null) {
                mDeleteBookAuthorsStmt = mStatements.add("mDeleteBookAuthorsStmt", "Delete from " + DB_TB_BOOK_AUTHOR + " Where " + DOM_BOOK_ID + " = ?");
            }
            // Need to delete the current records because they may have been reordered and a simple set of updates
            // could result in unique key or index violations.
            mDeleteBookAuthorsStmt.bindLong(1, bookId);
            mDeleteBookAuthorsStmt.execute();

            if (mAddBookAuthorsStmt == null) {
                mAddBookAuthorsStmt = mStatements.add("mAddBookAuthorsStmt", "Insert Into " + DB_TB_BOOK_AUTHOR
                        + "(" + DOM_BOOK_ID + "," + DOM_AUTHOR_ID + "," + DOM_AUTHOR_POSITION + ")"
                        + "Values(?,?,?)");
            }

            // The list MAY contain duplicates (eg. from Internet lookups of multiple
            // sources), so we track them in a hash table
            final Map<String, Boolean> idHash = new HashMap<>();
            int pos = 0;
            for (Author author : authors) {
                long authorId = 0;
                try {
                    // Get the name and find/add the author
                    authorId = getAuthorIdOrInsert(author);
                    // we use the id as the KEY here, so yes, a String.
                    String authorIdStr = Long.toString(authorId);
                    if (!idHash.containsKey(authorIdStr)) {
                        // indicate this author(id) is already present... but override, so we get elimination of duplicates.
                        idHash.put(authorIdStr, true);
                        pos++;
                        mAddBookAuthorsStmt.bindLong(1, bookId);
                        mAddBookAuthorsStmt.bindLong(2, authorId);
                        mAddBookAuthorsStmt.bindLong(3, pos);
                        mAddBookAuthorsStmt.executeInsert();
                        mAddBookAuthorsStmt.clearBindings();
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                    throw new RuntimeException("Error adding author '" + author + "' {" + authorId + "} to book " + bookId + ": " + e.getMessage(), e);
                }
            }
        }
    }

    /** Statements used by {@link #insertBookBookshelf} */
    private SynchronizedStatement mDeleteBookBookshelfStmt = null;
    /** Statements used by {@link #insertBookBookshelf} */
    private SynchronizedStatement mInsertBookBookshelfStmt = null;

    /**
     * Create each book/bookshelf combo in the weak entity
     *
     * @param bookId                The book id
     * @param bookshelves           A separated string of bookshelf names
     * @param dirtyBookIfNecessary  flag to set book dirty or not
     */
    private void insertBookBookshelf(final long bookId,
                                     @NonNull final ArrayList<String> bookshelves,
                                     @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {

        if (mDeleteBookBookshelfStmt == null) {
            mDeleteBookBookshelfStmt = mStatements.add("mDeleteBookBookshelfStmt",
                    "Delete from " + DB_TB_BOOK_BOOKSHELF_WEAK + " Where " + DOM_BOOK_ID + " = ?");
        }
        mDeleteBookBookshelfStmt.bindLong(1, bookId);
        mDeleteBookBookshelfStmt.execute();

        if (mInsertBookBookshelfStmt == null) {
            mInsertBookBookshelfStmt = mStatements.add("mInsertBookBookshelfStmt",
                    "Insert Into " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" + DOM_BOOK_ID + ", " + DOM_BOOKSHELF_NAME + ")"
                            + " Values (?,?)");
        }

        for (String bookshelf : bookshelves) {
            String name = bookshelf.trim();
            if (name.isEmpty()) {
                continue;
            }

            long bookshelfId = getBookshelfIdByName(name);
            if (bookshelfId == 0) {
                bookshelfId = insertBookshelf(name);
            }
            if (bookshelfId == 0) {
                bookshelfId = 1;
            }

            try {
                mInsertBookBookshelfStmt.bindLong(1, bookId);
                mInsertBookBookshelfStmt.bindLong(2, bookshelfId);
                mInsertBookBookshelfStmt.execute();
            } catch (Exception ignore) {
                Logger.logError(ignore, "Error assigning a book to a bookshelf.");
            }
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }
    }

    /**
     *
     *
     * @throws SQLiteDoneException FIXME
     */
    private void globalReplacePositionedBookItem(@NonNull final String tableName,
                                                 @NonNull final String objectIdField,
                                                 @NonNull final String positionField,
                                                 final long from, final long to) throws SQLiteDoneException{
        if ( !mSyncedDb.inTransaction() ) {
            throw new RuntimeException("globalReplacePositionedBookItem must be called in a transaction");
        }

        // Update books but prevent duplicate index errors - update books for which the new ID is not already present
        String sql = "Update " + tableName + " Set " + objectIdField + " = " + to +
                " Where " + objectIdField + " = " + from +
                " and Not Exists(Select NULL From " + tableName + " ba Where " +
                "   ba." + DOM_BOOK_ID + " = " + tableName + "." + DOM_BOOK_ID +
                "   and ba." + objectIdField + " = " + to + ")";
        mSyncedDb.execSQL(sql);

        // Finally, delete the rows that would have caused duplicates. Be cautious by using the
        // EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
        // loss if one of the prior statements failed silently.
        //
        // We also move remaining items up one place to ensure positions remain correct
        //
        sql = "select * from " + tableName + " Where " + objectIdField + " = " + from
                + " And Exists(Select NULL From " + tableName + " ba Where "
                + "   ba." + DOM_BOOK_ID + " = " + tableName + "." + DOM_BOOK_ID
                + "   and ba." + objectIdField + " = " + to + ")";

        SynchronizedStatement delStmt = null;
        SynchronizedStatement replacementIdPosStmt = null;
        SynchronizedStatement checkMinStmt = null;
        SynchronizedStatement moveStmt = null;
        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            // Get the column indexes we need
            final int bookCol = c.getColumnIndexOrThrow(DOM_BOOK_ID.name);
            final int posCol = c.getColumnIndexOrThrow(positionField);

            // Statement to delete a specific object record
            sql = "Delete from " + tableName + " Where " + objectIdField + " = ? and " + DOM_BOOK_ID + " = ?";
            delStmt = mSyncedDb.compileStatement(sql);

            // Statement to get the position of the already-existing 'new/replacement' object
            sql = "Select " + positionField + " From " + tableName + " Where " + DOM_BOOK_ID + " = ? and " + objectIdField + " = " + to;
            replacementIdPosStmt = mSyncedDb.compileStatement(sql);

            // Move statement; move a single entry to a new position
            sql = "Update " + tableName + " Set " + positionField + " = ? Where " + DOM_BOOK_ID + " = ? and " + positionField + " = ?";
            moveStmt = mSyncedDb.compileStatement(sql);

            // Sanity check to deal with legacy bad data
            sql = "Select min(" + positionField + ") From " + tableName + " Where " + DOM_BOOK_ID + " = ?";
            checkMinStmt = mSyncedDb.compileStatement(sql);

            // Loop through all instances of the old author appearing
            while (c.moveToNext()) {
                // Get the details of the old object
                long bookId = c.getLong(bookCol);
                long pos = c.getLong(posCol);

                // Get the position of the new/replacement object
                replacementIdPosStmt.bindLong(1, bookId);
                long replacementIdPos = replacementIdPosStmt.simpleQueryForLong();

                // Delete the old record
                delStmt.bindLong(1, from);
                delStmt.bindLong(2, bookId);
                delStmt.execute();

                // If the deleted object was more prominent than the new object, move the new one up
                if (replacementIdPos > pos) {
                    moveStmt.bindLong(1, pos);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, replacementIdPos);
                    moveStmt.execute();
                }

                //
                // It is tempting to move all rows up by one when we delete something, but that would have to be
                // done in another sorted cursor in order to prevent duplicate index errors. So we just make
                // sure we have something in position 1.
                //

                // Get the minimum position
                checkMinStmt.bindLong(1, bookId);
                long minPos = checkMinStmt.simpleQueryForLong();
                // If it's > 1, move it to 1
                if (minPos > 1) {
                    moveStmt.bindLong(1, 1);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, minPos);
                    moveStmt.execute();
                }
            }
        } finally {
            if (delStmt != null)
                delStmt.close();
            if (moveStmt != null)
                moveStmt.close();
            if (checkMinStmt != null)
                checkMinStmt.close();
            if (replacementIdPosStmt != null)
                replacementIdPosStmt.close();
        }
    }

    /**
     * @param bookId id of book
     *
     * @return list of AnthologyTitle for this book
     */
    @NonNull
    public ArrayList<AnthologyTitle> getBookAnthologyTitleList(final long bookId) {
        ArrayList<AnthologyTitle> list = new ArrayList<>();
        try (Cursor cursor = this.fetchAnthologyTitlesByBook(bookId)) {
            if (cursor.getCount() == 0) {
                return list;
            }

            final int familyNameCol = cursor.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
            final int givenNameCol = cursor.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);
            final int authorIdCol = cursor.getColumnIndex(DOM_AUTHOR_ID.name);
            final int titleCol = cursor.getColumnIndex(DOM_TITLE.name);

            while (cursor.moveToNext()) {
                Author author = new Author(
                        cursor.getLong(authorIdCol),
                        cursor.getString(familyNameCol),
                        cursor.getString(givenNameCol));

                list.add(new AnthologyTitle(bookId, author, cursor.getString(titleCol)));
            }
        }
        return list;
    }

    @NonNull
    public ArrayList<Author> getBookAuthorList(long rowId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor authors = fetchAllAuthorsByBook(rowId)) {
            if (authors.getCount() == 0) {
                return list;
            }

            int idCol = authors.getColumnIndex(DOM_ID.name);
            int familyCol = authors.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
            int givenCol = authors.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);

            while (authors.moveToNext()) {
                list.add(new Author(authors.getLong(idCol), authors.getString(familyCol), authors.getString(givenCol)));
            }
        }
        return list;
    }

    public ArrayList<Series> getBookSeriesList(long rowId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor series = fetchAllSeriesByBook(rowId)) {
            if (series.getCount() == 0) {
                return list;
            }

            int idCol = series.getColumnIndex(DOM_ID.name);
            int nameCol = series.getColumnIndex(DOM_SERIES_NAME.name);
            int numCol = series.getColumnIndex(DOM_SERIES_NUM.name);

            while (series.moveToNext()) {
                list.add(new Series(series.getLong(idCol), series.getString(nameCol), series.getString(numCol)));
            }
        }
        return list;
    }

    /**
     * Return a list of all books in the database
     *
     * @param order What order to return the books
     * @param bookshelf Which bookshelf is it in. Can be "All Books"
     * @return Cursor over all Books
     */
    public BooksCursor fetchAllBooks(@Nullable final String order,
                                     @NonNull final String bookshelf,
                                     @NonNull final String authorWhere,
                                     @NonNull final String bookWhere,
                                     @NonNull final String searchText,
                                     @NonNull final String loaned_to,
                                     @NonNull final String seriesName) {
        // Get the SQL
        String fullSql = fetchAllBooksSql( order, bookshelf, authorWhere, bookWhere, searchText, loaned_to, seriesName);
        return fetchBooks(fullSql, new String[]{});
    }

    /**
     * Return a book (Cursor) that matches the given rowId
     *
     * @param rowId id of book to retrieve
     * @return Cursor positioned to matching book, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public BooksCursor fetchBookById(final long rowId) throws SQLException {
        String where = "b." + DOM_ID + "=" + rowId;
        return fetchAllBooks("", "", "", where, "", "", "");
    }


    /**
     * Return a book (Cursor) that matches the given ISBN.
     * Note: MAYBE RETURN MORE THAN ONE BOOK
     *
     * @param isbns ISBN of book(s) to retrieve
     * @return Cursor positioned to matching book, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public BooksCursor fetchBooksByIsbns(@NonNull final ArrayList<String> isbns) throws SQLException {
        if (isbns.size() == 0)
            throw new RuntimeException("No ISBNs specified in lookup");

        StringBuilder where = new StringBuilder(TBL_BOOKS.dot(DOM_ISBN));
        if (isbns.size() == 1) {
            where.append(" = '").append(encodeString(isbns.get(0))).append("'");
        } else {
            where.append(" in (");
            boolean first = true;
            for(String isbn: isbns) {
                if (first) {
                    first = false;
                }
                else {
                    where.append(",");
                }
                where.append("'").append(encodeString(isbn)).append("'");
            }
            where.append(")");
        }
        return fetchAllBooks("", "", "", where.toString(), "", "", "");
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Classic Book">
    @NonNull
    private String classicSqlAllSeries() {
        return "select distinct s." + DOM_ID + " as " + DOM_ID + ", s."+ DOM_SERIES_NAME + " as " + DOM_SERIES_NAME
                //+ ", s." + KEY_SERIES_NAME + " as series_sort "
                + " From " + DB_TB_SERIES + " s ";
    }

    @NonNull
    private String classicSqlAllSeriesOnBookshelf(@NonNull final String bookshelf) {
        return "select distinct s." + DOM_ID + " as " + DOM_ID + ", s." + DOM_SERIES_NAME +
                " as " + DOM_SERIES_NAME//+ ", s." + KEY_SERIES_NAME + " as series_sort "
                + " From " + DB_TB_SERIES + " s "
                + " join " + DB_TB_BOOK_SERIES + " bsw "
                + "    on bsw." + DOM_SERIES_ID + " = s." + DOM_ID
                + " join " + DB_TB_BOOKS + " b "
                + "    on b." + DOM_ID + " = bsw." + DOM_BOOK_ID
                + " join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbw"
                + "    on bbw." + DOM_BOOK_ID + " = b." + DOM_ID
                + " join " + DB_TB_BOOKSHELF + " bs "
                + "    on bs." + DOM_ID + " = bbw." + DOM_BOOKSHELF_NAME
                + " where " + makeTextTerm("bs." + DOM_BOOKSHELF_NAME, "=", bookshelf);
    }

    /**
     * Return a list of all the first characters for book titles in the database
     *
     * @param bookshelf Which bookshelf is it in. Can be "All Books"
     * @return Cursor over all Books
     */
    @NonNull
    public Cursor classicFetchAllBookChars(@NonNull final String bookshelf) {
        String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "",
                "", "", "", "");
        String sql = "SELECT DISTINCT upper(substr(b." + DOM_TITLE + ", 1, 1)) AS " + DOM_ID + " " + baseSql;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return a list of all books in the database by author
     *
     * @param author The author to search for
     * @param bookshelf Which bookshelf is it in. Can be "All Books"
     * @return Cursor over all Books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksByAuthor(final int author,
                                                    @NonNull final String bookshelf,
                                                    @NonNull final String search_term,
                                                    final boolean firstOnly) {
        String where = " a._id=" + author;
        if (firstOnly) {
            where += " AND ba." + DOM_AUTHOR_POSITION + "=1 ";
        }
        String order = "s." + DOM_SERIES_NAME + ", substr('0000000000' || s." + DOM_SERIES_NUM + ", -10, 10), lower(b." + DOM_TITLE + ") ASC";
        return fetchAllBooks(order, bookshelf, where, "", search_term, "", "");
    }

    /**
     * This will return a list of all books by a given first title character
     *
     * @param first_char The first title character
     * @return Cursor over all books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksByChar(@NonNull final String first_char,
                                                  @NonNull final String bookshelf,
                                                  @NonNull final String search_term) {
        String where = " " + makeTextTerm("substr(b." + DOM_TITLE + ",1,1)", "=", first_char);
        return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
    }

    /**
     * Return a Cursor over the list of all books in the database by genre
     *
     * @param date          date published
     * @param bookshelf     The bookshelf to search within. Can be the string "All Books"
     * @param search_term   text to search for
     *
     * @return Cursor over all books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksByDatePublished(@Nullable String date,
                                                           @NonNull final String bookshelf,
                                                           @NonNull final String search_term) {
        String where;
        if (date == null) {
            date = META_EMPTY_DATE_PUBLISHED;
        }

        if (date.equals(META_EMPTY_DATE_PUBLISHED)) {
            where = "(b." + DOM_BOOK_DATE_PUBLISHED + "='' OR b." + DOM_BOOK_DATE_PUBLISHED + " IS NULL" +
                    " or cast(strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") as int)<0" +
                    " or cast(strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") as int) is null)";
        } else {
            where = makeTextTerm("strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ")", "=", date);
        }
        return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
    }

    /**
     * Return a Cursor over the list of all books in the database by genre
     *
     * @param genre The genre name to search by
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksByGenre(@NonNull final String genre,
                                                   @NonNull final String bookshelf,
                                                   @NonNull final String search_term) {
        String where;
        if (genre.equals(META_EMPTY_GENRE)) {
            where = "(b." + DOM_BOOK_GENRE + "='' OR b." + DOM_BOOK_GENRE + " IS NULL)";
        } else {
            where = makeTextTerm("b." + DOM_BOOK_GENRE, "=", genre);
        }
        return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
    }

    /**
     * This will return a list of all books loaned to a given person
     *
     * @param loaned_to The person who had books loaned to
     * @return Cursor over all books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksByLoan(@NonNull final String loaned_to,
                                                  @NonNull final String search_term) {
        return fetchAllBooks("", "", "", "", search_term, loaned_to, "");
    }

    /**
     * This will return a list of all books either read or unread
     *
     * @param read "Read" or "Unread"
     * @return Cursor over all books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksByRead(@NonNull final String read,
                                                  @NonNull final String bookshelf,
                                                  @NonNull final String search_term) {
        String where = "";
        if ("Read".equals(read)) {
            where += " b." + DOM_BOOK_READ + "=1";
        } else {
            where += " b." + DOM_BOOK_READ + "!=1";
        }
        return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
    }

    /**
     * Return a Cursor over the list of all books in the database by series
     *
     * @param series The series name to search by
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all books
     */
    @NonNull
    public BooksCursor classicFetchAllBooksBySeries(@NonNull final String series,
                                                    @NonNull final String bookshelf,
                                                    @NonNull final String search_term) {
        if (series.isEmpty() || series.equals(META_EMPTY_SERIES)) {
            return fetchAllBooks("", bookshelf, "", "", search_term, "", META_EMPTY_SERIES);
        } else {
            String order = "substr('0000000000' || s." + DOM_SERIES_NUM + ", -10, 10), b." + DOM_TITLE + " " + COLLATION + " ASC";
            return fetchAllBooks(order, bookshelf, "", "", search_term, "", series);
        }
    }

    /**
     * This will return a list of all date published years within the given bookshelf
     *
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all series
     */
    @NonNull
    public Cursor classicFetchAllDatePublished(@NonNull final String bookshelf) {
        // Null 'order' to suppress ordering
        String baseSql = fetchAllBooksInnerSql(null, bookshelf, "", "", "", "", "");

        String sql = "SELECT DISTINCT "
                + " Case When (b." + DOM_BOOK_DATE_PUBLISHED + " = ''" +
                " or b." + DOM_BOOK_DATE_PUBLISHED + " is NULL" +
                " or cast(strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") as int)<0" +
                " or cast(strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") as int) is null)" +
                " Then '" + META_EMPTY_DATE_PUBLISHED + "'" +
                " Else strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") End as " + DOM_ID + baseSql +
                " ORDER BY strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }


    /**
     * Returns a list of books, similar to {@link #fetchAllBooks} but restricted by a
     * search string. The query will be applied to author, title, and series
     *
     * @param searchText	The search string to restrict the output by
     * @param first_char    The character to search for
     * @param bookshelf		The bookshelf to search within. Can be the string "All Books"
     *
     * @return A Cursor of book meeting the search criteria
     */
    @NonNull
    public BooksCursor classicFetchBooksByChar(@NonNull final String searchText,
                                               @NonNull final String first_char,
                                               @NonNull final String bookshelf) {
        return fetchAllBooks("", bookshelf, "",
                " " + makeTextTerm("substr(b." + DOM_TITLE + ",1,1)", "=", first_char),
                searchText, "", "");
    }

    @NonNull
    public BooksCursor classicFetchBooksByDatePublished(@NonNull final String searchText,
                                                        @NonNull final String date,
                                                        @NonNull final String bookshelf) {
        return fetchAllBooks("", bookshelf, "",
                " strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ")='" + date + "' " + COLLATION + " ",
                searchText, "", "");
    }

    @NonNull
    public BooksCursor classicFetchBooksByGenre(@NonNull final String searchText,
                                                @NonNull final String genre,
                                                @NonNull final String bookshelf) {
        return fetchAllBooks("", bookshelf, "",
                " " + DOM_BOOK_GENRE.name + "='" + genre + "' " + COLLATION + " ",
                searchText, "", "");
    }

    /**
     * Returns a list of books title characters, similar to {@link #classicFetchAllBookChars} but
     * restricted by a search string. The query will be applied to author, title, and series
     *
     * @param searchText The search string to restrict the output by
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     *
     * @return A Cursor of book titles meeting the search criteria
     */
    @NonNull
    public Cursor classicFetchBooksChars(@NonNull final String searchText,
                                         @NonNull final String bookshelf) {
        String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");
        String sql = "SELECT DISTINCT upper(substr(b." + DOM_TITLE + ", 1, 1)) " + COLLATION + " AS " + DOM_ID + " " + baseSql;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * This will return a list of all date published years within the given bookshelf where the
     * series, title or author meet the search string
     *
     * @param searchText The query string to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     *
     * @return Cursor over all notes
     */
    @NonNull
    public Cursor classicFetchDatePublished(@NonNull final String searchText,
                                            @NonNull final String bookshelf) {
        String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");
        String sql = "SELECT DISTINCT Case When " + DOM_BOOK_DATE_PUBLISHED + " = '' Then '" + META_EMPTY_DATE_PUBLISHED + "' else strftime('%Y', b." + DOM_BOOK_DATE_PUBLISHED + ") End " + COLLATION + " AS " + DOM_ID + " " + baseSql;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * This will return a list of all series within the given bookshelf
     *
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all series
     */
    @NonNull
    public Cursor classicFetchAllSeries(@NonNull final String bookshelf) {
        String series;
        if (bookshelf.isEmpty()) {
            series = classicSqlAllSeries();
        } else {
            series = classicSqlAllSeriesOnBookshelf(bookshelf);
        }
        // Display blank series as '<Empty Series>' BUT sort as ''. Using a UNION
        // seems to make ordering fail.
        String sql = "Select " + DOM_ID + ", Case When " + DOM_SERIES_NAME + " = ''" +
                " Then '" + META_EMPTY_SERIES + "' Else " + DOM_SERIES_NAME + " End  as " + DOM_SERIES_NAME +
                " From ( " + series +
                "   UNION Select -1 as " + DOM_ID + ", '' as " + DOM_SERIES_NAME + ") s" +
                " Order by Upper(s." + DOM_SERIES_NAME + ") " + COLLATION + " asc ";

        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * This will return a list consisting of "Read" and "Unread"
     *
     * @return Cursor over all the pseudo list
     */
    @NonNull
    public Cursor classicFetchAllUnreadPseudo() {
        String sql = "SELECT 'Unread' as " + DOM_ID + " UNION SELECT 'Read' as " + DOM_ID + "";
        return mSyncedDb.rawQuery(sql, new String[]{});
    }


    /**
     * Return the position of a book in a list of all books (within a bookshelf)
     *
     * @param seriesName The book title to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return The position of the book, or 0 when not found
     */
     public int classicGetSeriesPositionBySeries(@NonNull String seriesName,
                                                 @NonNull final String bookshelf) {
        String seriesSql;
        if (bookshelf.isEmpty()) {
            seriesSql = classicSqlAllSeries();
        } else {
            seriesSql = classicSqlAllSeriesOnBookshelf(bookshelf);
        }
        if (seriesName.equals(META_EMPTY_SERIES)) {
            seriesName = "";
        }

        // Display blank series as '<Empty Series>' BUT sort as ''. Using a UNION
        // seems to make ordering fail.
        String sql = "Select Count(Distinct " + DOM_SERIES_NAME + ") as count"
                + " From ( " + seriesSql
                + "       UNION Select -1 as " + DOM_ID + ", '' as " +  DOM_SERIES_NAME
                + "       ) s "
                + " WHERE " + makeTextTerm("s." + DOM_SERIES_NAME, "<", seriesName)
                + " Order by s." + DOM_SERIES_NAME + COLLATION + " asc ";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return getIntValue(cursor);
        }
    }

    /**
     * Return the position of a book in a list of all books (within a bookshelf)
     *
     * @param title The book title to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     *
     * @return The position of the book, or 0 when not found
     */
    public int classicFetchBookPositionByTitle(@NonNull final String title, @NonNull final String bookshelf) {
        String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", makeTextTerm("Substr(b." + DOM_TITLE + ",1,1)", "<", title.substring(0,1)), "", "", "");
        String sql = "SELECT Count(Distinct Upper(Substr(" + DOM_TITLE + ",1,1))" + COLLATION + ") as count " + baseSql;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return getIntValue(cursor);
        }
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Bookshelf">

    /**
     * This function will create a new bookshelf in the database
     *
     * @param name The bookshelf name
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookshelf(@NonNull final String name) {
        // TODO: Decide if we need to backup EMPTY bookshelves...
        ContentValues values = new ContentValues();
        values.put(DOM_BOOKSHELF_NAME.name, name);
        return mSyncedDb.insert(DB_TB_BOOKSHELF, null, values);
    }

    private SynchronizedStatement mGetBookshelfNameStmt = null;
    /**
     * Return a Cursor positioned at the bookshelf that matches the given rowId
     *
     * @param id of bookshelf to retrieve
     *
     * @return Name of bookshelf
     *
     * @throws SQLiteDoneException if not found FIXME, but it seems there is always a bookshelf '1'
     */
    @NonNull
    public String getBookshelfName(final long id) throws SQLiteDoneException {
        if (mGetBookshelfNameStmt == null) {
            mGetBookshelfNameStmt = mStatements.add("mGetBookshelfNameStmt",
                    "Select " + DOM_BOOKSHELF_NAME + " From " + DB_TB_BOOKSHELF + " Where " + DOM_ID + " = ?");
        }
        mGetBookshelfNameStmt.bindLong(1, id);
        return mGetBookshelfNameStmt.simpleQueryForString();
    }

    /**
     * Delete the bookshelf with the given rowId
     *
     * @param id id of bookshelf to delete
     *
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteBookshelf(final long id) {
        setBooksDirtyByBookshelf(id);
        return mSyncedDb.delete(DB_TB_BOOK_BOOKSHELF_WEAK, DOM_BOOKSHELF_NAME + "=" + id, null) > 0
                        && mSyncedDb.delete(DB_TB_BOOKSHELF, DOM_ID + "=" + id, null) > 0;
    }


    /** used in {@link #getBookshelfId}*/
    private SynchronizedStatement mGetBookshelfIdStmt = null;

    /**
     *
     * @param bookshelf to find
     * @return the id
     *
     * @throws SQLiteDoneException if not found FIXME
     */
    public long getBookshelfId(@NonNull final Bookshelf bookshelf) throws SQLiteDoneException{
        if (mGetBookshelfIdStmt == null) {
            mGetBookshelfIdStmt = mStatements.add("mGetBookshelfIdStmt",
                    "Select " + DOM_ID + " From " + DB_TB_BOOKSHELF +
                            " Where Upper(" + DOM_BOOKSHELF_NAME + ") = Upper(?)" + COLLATION);
        }

        mGetBookshelfIdStmt.bindString(1, bookshelf.name);
        return mGetBookshelfIdStmt.simpleQueryForLong();
    }

    /**
     * Update the bookshelf name
     *
     * @param id id of bookshelf to update
     * @param name value to set bookshelf name to
     * @return true if successfully updated, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateBookshelf(final long id, @NonNull final String name) {
        ContentValues values = new ContentValues();
        values.put(DOM_BOOKSHELF_NAME.name, name);
        boolean success = mSyncedDb.update(DB_TB_BOOKSHELF, values, DOM_ID + "=" + id, null) > 0;

        purgeAuthors();
        setBooksDirtyByBookshelf(id);

        return success;
    }

    /** used in {@link #getBookshelfIdByName}*/
    private SynchronizedStatement mFetchBookshelfIdByNameStmt = null;

    /**
     * This will return JUST the bookshelf id based on the name.
     *
     * @param name bookshelf to search for
     * @return bookshelf id, or 0 when not found
     */
    private long getBookshelfIdByName(@NonNull final String name) {
        if (mFetchBookshelfIdByNameStmt == null) {
            mFetchBookshelfIdByNameStmt = mStatements.add("mFetchBookshelfIdByNameStmt",
                    "Select " + DOM_ID + " From " + DOM_BOOKSHELF_NAME +
                    " Where Upper(" + DOM_BOOKSHELF_NAME + ") = Upper(?)" + COLLATION);
        }
        mFetchBookshelfIdByNameStmt.bindString(1, name);
        try {
            return mFetchBookshelfIdByNameStmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignore) {
            return 0;
        }
    }

    /**
     * Return a Cursor over the list of all bookshelves
     *
     * @return Cursor over all bookshelves
     */
    @NonNull
    public Cursor fetchAllBookshelves() {
        String sql = "SELECT DISTINCT bs." + DOM_ID + " as " + DOM_ID + "," +
                " bs." + DOM_BOOKSHELF_NAME + " as " + DOM_BOOKSHELF_NAME + "," +
                " 0 as " + DOM_BOOK_ID +
                " FROM " + DB_TB_BOOKSHELF + " bs" +
                " ORDER BY Upper(bs." + DOM_BOOKSHELF_NAME + ") " + COLLATION ;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return a Cursor over the list of all bookshelves in the database
     *
     * @param bookId the book, which in turn adds a new field on each row as to the active state of that bookshelf for the book
     * @return Cursor over all bookshelves
     */
    @NonNull
    public Cursor fetchAllBookshelves(final long bookId) {
        String sql = "SELECT DISTINCT bs." + DOM_ID + " as " + DOM_ID + "," +
                " bs." + DOM_BOOKSHELF_NAME + " as " + DOM_BOOKSHELF_NAME + "," +
                " CASE WHEN w." + DOM_BOOK_ID + " IS NULL THEN 0 ELSE 1 END as " + DOM_BOOK_ID +

                " FROM " + DB_TB_BOOKSHELF + " bs LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w" +
                " ON (w." + DOM_BOOKSHELF_NAME + "=bs." + DOM_ID + " AND w." + DOM_BOOK_ID + "=" + bookId + ") " +
                " ORDER BY Upper(bs." + DOM_BOOKSHELF_NAME + ") " + COLLATION;
        try {
            return mSyncedDb.rawQuery(sql, new String[]{});
        } catch (NullPointerException ignore) {
            // there is no bookId
            return fetchAllBookshelves();
        }
    }

    /**
     * Return a Cursor over the list of all bookshelves in the database for the given book
     *
     * @param bookId the book
     * @return Cursor over all bookshelves
     */
    @NonNull
    public Cursor fetchAllBookshelvesByBook(final long bookId) {
        String sql = "SELECT DISTINCT bs." + DOM_ID + " as " + DOM_ID + "," +
                " bs." + DOM_BOOKSHELF_NAME + " as " + DOM_BOOKSHELF_NAME +

                " FROM " + DB_TB_BOOKSHELF + " bs, " + DB_TB_BOOK_BOOKSHELF_WEAK + " w " +
                " WHERE w." + DOM_BOOKSHELF_NAME + "=bs." + DOM_ID + " AND w." + DOM_BOOK_ID + "=" + bookId + " " +
                " ORDER BY Upper(bs." + DOM_BOOKSHELF_NAME + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="BooklistStyle">
    /**
     * @return a list of all defined styles in the database
     */
    @NonNull
    public Cursor getBooklistStyles() {
        final String sql = "Select " + TBL_BOOK_LIST_STYLES.ref(DOM_ID, DOM_STYLE) +
                " From " +  TBL_BOOK_LIST_STYLES.ref();
        // + " Order By " + TBL_BOOK_LIST_STYLES.ref(DOM_POSITION, DOM_ID);
        return mSyncedDb.rawQuery(sql);
    }

    /**
     * Create a new booklist style
     *
     * @return the id of the new BooklistStyle, or -1 if failure
     */
    private SynchronizedStatement mInsertBooklistStyleStmt = null;
    public long insertBooklistStyle(@NonNull final BooklistStyle style) {
        if (mInsertBooklistStyleStmt == null) {
            mInsertBooklistStyleStmt = mStatements.add("mInsertBooklistStyleStmt",
                    TBL_BOOK_LIST_STYLES.getInsert(DOM_STYLE) + " Values (?)");
        }
        final byte[] blob = SerializationUtils.serializeObject(style);
        mInsertBooklistStyleStmt.bindBlob(1, blob);
        return mInsertBooklistStyleStmt.executeInsert();
    }

    /**
     * Update an existing booklist style
     */
    private SynchronizedStatement mUpdateBooklistStyleStmt = null;
    public void updateBooklistStyle(@NonNull final BooklistStyle style) {
        if (mUpdateBooklistStyleStmt == null) {
            mUpdateBooklistStyleStmt = mStatements.add("mUpdateBooklistStyleStmt",
                    TBL_BOOK_LIST_STYLES.getInsertOrReplaceValues(DOM_ID, DOM_STYLE));
        }
        byte[] blob = SerializationUtils.serializeObject(style);
        mUpdateBooklistStyleStmt.bindLong(1, style.getRowId());
        mUpdateBooklistStyleStmt.bindBlob(2, blob);
        mUpdateBooklistStyleStmt.execute();
    }

    /**
     * Delete an existing booklist style
     */
    private SynchronizedStatement mDeleteBooklistStyleStmt = null;
    public void deleteBooklistStyle(final long id) {
        if (mDeleteBooklistStyleStmt == null) {
            mDeleteBooklistStyleStmt = mStatements.add("mDeleteBooklistStyleStmt",
                    "Delete from " + TBL_BOOK_LIST_STYLES + " Where " +  DOM_ID + " = ?");
        }
        mDeleteBooklistStyleStmt.bindLong( 1, id );
        mDeleteBooklistStyleStmt.execute();
    }
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="SearchSuggestionProvider">

    /**
     *
     * @param query The query string
     * @return Cursor of search suggestions
     */
    @NonNull
    public Cursor fetchSearchSuggestions(@NonNull final String query) {
        String sql = "Select * From (SELECT \"BK\" || b." + DOM_ID + " as " + BaseColumns._ID
                + ", b." + DOM_TITLE + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", b." + DOM_TITLE + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_BOOKS + " b" +
                " WHERE b." + DOM_TITLE + " LIKE '"+query+"%'" +
                " UNION " +
                " SELECT \"AF\" || a." + DOM_ID + " as " + BaseColumns._ID
                + ", a." + DOM_AUTHOR_FAMILY_NAME + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", a." + DOM_AUTHOR_FAMILY_NAME + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_AUTHORS + " a" +
                " WHERE a." + DOM_AUTHOR_FAMILY_NAME + " LIKE '"+query+"%'" +
                " UNION " +
                " SELECT \"AG\" || a." + DOM_ID + " as " + BaseColumns._ID
                + ", a." + DOM_AUTHOR_GIVEN_NAMES + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", a." + DOM_AUTHOR_GIVEN_NAMES + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_AUTHORS + " a" +
                " WHERE a." + DOM_AUTHOR_GIVEN_NAMES + " LIKE '"+query+"%'" +
                " UNION " +
                " SELECT \"BK\" || b." + DOM_ID + " as " + BaseColumns._ID
                + ", b." + DOM_ISBN + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", b." + DOM_ISBN + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_BOOKS + " b" +
                " WHERE b." + DOM_ISBN + " LIKE '"+query+"%'" +
                " ) as zzz " +
                " ORDER BY Upper(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, null);
    }


    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Format">
    /**
	 * Returns a unique list of all formats in the database; uses the pre-defined ones if none.
	 *
	 * @return The list
	 */
    @NonNull
	public ArrayList<String> getFormats() {
		String sql = "Select distinct " + DOM_BOOK_FORMAT + " from " + DB_TB_BOOKS
				+ " Order by lower(" + DOM_BOOK_FORMAT + ") " + COLLATION;

		try (Cursor c = mSyncedDb.rawQuery(sql)) {
			ArrayList<String> list = getFirstColumnAsList(c);
			if (list.size() == 0) {
				Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_formats));
			}
			return list;
		}
	}

    public void globalReplaceFormat(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to))
            return;

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            // Update books but prevent duplicate index errors
            String sql = "Update " + DB_TB_BOOKS +
                    " Set " + DOM_BOOK_FORMAT + " = '" + encodeString(to) + "," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                    " Where " + DOM_BOOK_FORMAT + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Genre">
    /**
     * Returns a unique list of all locations in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        String sql = "Select distinct " + DOM_BOOK_GENRE + " from " + DB_TB_BOOKS
                + " Order by lower(" + DOM_BOOK_GENRE + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            ArrayList<String> list = getFirstColumnAsList(c);
            if (list.size() == 0) {
                Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_genres));
            }
            return list;
        }
    }
    public void globalReplaceGenre(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to))
            return;

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "Update " + DB_TB_BOOKS +
                    " Set " + DOM_BOOK_GENRE + " = '" + encodeString(to) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                    " Where " + DOM_BOOK_GENRE + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * This will return a list of all genres within the given bookshelf
     *
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchGenresByBookshelf(@NonNull final String bookshelf) {
        // Null 'order' to suppress ordering
        String baseSql = fetchAllBooksInnerSql(null, bookshelf, "", "", "", "", "");

        String sql = "SELECT DISTINCT " +
                " Case When (b." + DOM_BOOK_GENRE + " = '' or b." + DOM_BOOK_GENRE + " is NULL) Then '" + META_EMPTY_GENRE + "'" +
                " Else b." + DOM_BOOK_GENRE + " End as " + DOM_ID + baseSql +
                " ORDER BY Upper(b." + DOM_BOOK_GENRE + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return the position of a book in a list of all books (within a bookshelf)
     *
     * @param genre The book genre to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return The position of the book, or 0 when not found
     */
    public int getGenrePositionByGenre(@NonNull final String genre, @NonNull final String bookshelf) {
        if (genre.equals(META_EMPTY_GENRE))
            return 0;

        String where = makeTextTerm("b." + DOM_BOOK_GENRE, "<", genre);
        String baseSql = fetchAllBooksInnerSql("", bookshelf, "", where, "", "", "");

        String sql = "SELECT Count(DISTINCT Upper(" + DOM_BOOK_GENRE + "))" + baseSql;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return  getIntValue(cursor);
        }
    }

    /**
     * This will return a list of all genres within the given bookshelf where the
     * series, title or author meet the search string
     *
     * @param searchText The query string to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all notes
     */
    @NonNull
    public Cursor fetchGenres(@NonNull final String searchText, @NonNull final String bookshelf) {
        String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");
        String sql = "SELECT DISTINCT Case When " + DOM_BOOK_GENRE + " = '' Then '" + META_EMPTY_GENRE + "' else " + DOM_BOOK_GENRE + " End " + COLLATION +
                " AS " + DOM_ID + " " + baseSql;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Language">
    /**
	 * Returns a unique list of all languages in the database; uses the pre-defined ones if none.
	 *
	 * @return The list
	 */
    @NonNull
	public ArrayList<String> getLanguages() {
		String sql = "Select distinct " + DOM_BOOK_LANGUAGE + " from " + DB_TB_BOOKS
				+ " Order by lower(" + DOM_BOOK_LANGUAGE + ") " + COLLATION;

		try (Cursor c = mSyncedDb.rawQuery(sql)) {
			ArrayList<String> list = getFirstColumnAsList(c);
			if (list.size() == 0) {
				Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_languages));
			}
			return list;
		}
	}

    public void globalReplaceLanguage(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to))
            return;

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "Update " + DB_TB_BOOKS + " Set " + DOM_BOOK_LANGUAGE + " = '" + encodeString(to) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp"
                    + " Where " + DOM_BOOK_LANGUAGE + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Loans">

    /**
     * This function will create a new loan in the database
     *
     * @param bookData the book
     * @param dirtyBookIfNecessary    flag to set book dirty or not (for now, always false...)
     *
     * @return the ID of the loan
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertLoan(@NonNull final BookData bookData, final boolean dirtyBookIfNecessary) {
        ContentValues values = new ContentValues();
        values.put(DOM_BOOK_ID.name, bookData.getRowId());
        values.put(DOM_LOANED_TO.name, bookData.getString(DOM_LOANED_TO.name));
        long result = mSyncedDb.insert(DB_TB_LOAN, null, values);
        this.deleteLoansWithoutBooks();
        if (dirtyBookIfNecessary) {
            setBookDirty(bookData.getRowId());
        }

        return result;
    }

    /**
     * Delete the loan with the given rowId
     *
     * @param bookId 	id of book whose loan is to be deleted
     *
     * @return 			true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteLoan(final long bookId, final boolean dirtyBookIfNecessary) {
        boolean success = mSyncedDb.delete(DB_TB_LOAN, DOM_BOOK_ID + "=" + bookId, null) > 0;
        this.deleteLoansWithoutBooks();
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }
        return success;
    }

    /**
     * Delete all loan without a book or loanee
     *
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings({"UnusedReturnValue"})
    private boolean deleteLoansWithoutBooks() {
        return mSyncedDb.delete(DB_TB_LOAN,
                "("+ DOM_BOOK_ID + "=''" +
                " OR " + DOM_BOOK_ID + "=null" +
                " OR " + DOM_LOANED_TO + "=''" +
                " OR " + DOM_LOANED_TO + "=null) ", null) > 0;
    }

    /**
     * This will return the borrower (curses on him/her if the book is not returned)
     * for a given book, if any
     *
     * @param id book to search for
     *
     * @return Who the book is loaned to, can be blank/null
     */
    @Nullable
    public String getLoanByBook(final long id) {
        try (Cursor cursor = mSyncedDb.query(DB_TB_LOAN,
                new String[] {DOM_BOOK_ID.name, DOM_LOANED_TO.name},
                DOM_BOOK_ID + "=" + id + "",
                null, null, null, null)) {

                cursor.moveToFirst();
                return cursor.getString(1); // 1= DOM_LOANED_TO.name
        }
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Classic Loan">
    /**
     * This will return a list of all loans
     *
     * @return Cursor over all loans
     */
    @NonNull
    public Cursor classicFetchAllLoans() {
        String sql = "SELECT DISTINCT l." + DOM_LOANED_TO + " as " + DOM_ID +
                " FROM " + DB_TB_LOAN + " l " +
                " ORDER BY Upper(l." + DOM_LOANED_TO + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Location">
    /**
     * Returns a unique list of all locations in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLocations() {
        String sql = "Select distinct " + DOM_BOOK_LOCATION + " from " + DB_TB_BOOKS
                + " Order by lower(" + DOM_BOOK_LOCATION + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            ArrayList<String> list = getFirstColumnAsList(c);
            if (list.size() == 0) {
                Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_locations));
            }
            return list;
        }
    }

    public void globalReplaceLocation(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to))
            return;

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "Update " + DB_TB_BOOKS +
                    " Set " + DOM_BOOK_LOCATION + " = '" + encodeString(to) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp "
                    + " Where " + DOM_BOOK_LOCATION + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Publisher">
    /**
     * Returns a unique list of all publishers in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublishers() {
        String sql = "Select distinct " + DOM_PUBLISHER + " from " + DB_TB_BOOKS
                + " Order by lower(" + DOM_PUBLISHER + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplacePublisher(@NonNull final Publisher from, @NonNull final Publisher to) {
        if (Objects.equals(from, to))
            return;

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "Update " + DB_TB_BOOKS + " Set " + DOM_PUBLISHER + " = '" + encodeString(to.name) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp"
                    + " Where " + DOM_PUBLISHER + " = '" + encodeString(from.name) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Series">

    /**
     * Create a new series in the database
     *
     * @param name 	A string containing the series name
     *
     * @return the ID of the new series
     */
    private long insertSeries(@NonNull final String name) {
        ContentValues values = new ContentValues();
        values.put(DOM_SERIES_NAME.name, name);
        return mSyncedDb.insert(DB_TB_SERIES, null, values);
    }

    /**
     * Delete the passed series
     *
     * @param series 	series to delete
     * @return true 	if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteSeries(@NonNull final Series series) {
        if (series.id == 0) {
            series.id = getSeriesId(series);
        }
        if (series.id == 0) {
            return false;
        }

        setBooksDirtyBySeries(series.id);

        // Delete DB_TB_BOOK_SERIES for this series
        boolean success = (0 < mSyncedDb.delete(DB_TB_BOOK_SERIES, DOM_SERIES_ID + " = " + series.id, null));

        if (success) {
            purgeSeries();
        }
        return success;
    }


    /*
     * This will return the series based on the ID.
     * FIXME: this could return a Series with a non-zero id, which does NOT exist in the database !
     */
    @Nullable
    public Series getSeriesById(final long id) {
        String sql = "Select " + DOM_SERIES_NAME + " From " + DB_TB_SERIES
                + " Where " + DOM_ID + " = " + id;
        try (Cursor c = mSyncedDb.rawQuery(sql, null)) {
            if (!c.moveToFirst()) {
                return null;
            }
            return new Series(id, c.getString(0), "");
        }
    }


    /**
     * Add or update the passed series, depending whether s.id == 0.
     */
    private void insertOrUpdateSeries(@NonNull final /* out */ Series s) {
        if (s.id != 0) {
            // Get the old author
            Series oldS = this.getSeriesById(s.id);
            // Update if changed (case SENSITIVE)
            if (oldS != null && !s.name.equals(oldS.name)) {
                ContentValues values = new ContentValues();
                values.put(DOM_SERIES_NAME.name, s.name);
                mSyncedDb.update(DB_TB_SERIES, values, DOM_ID + " = " + s.id, null);

                // Mark all books referencing this series as dirty
                this.setBooksDirtyBySeries(s.id);
            }
        } else {
            s.id = insertSeries(s.name);
        }
    }


    public long getSeriesId(@NonNull final Series s) {
        return getSeriesId(s.name);
    }

    /** {@link #getSeriesId} */
    private SynchronizedStatement mGetSeriesIdStmt = null;

    /**
     *
     * @return the id, or 0 when not found
     */
    public long getSeriesId(@NonNull final String name) {
        if (mGetSeriesIdStmt == null) {
            mGetSeriesIdStmt = mStatements.add("mGetSeriesIdStmt",
                    "Select " + DOM_ID + " From " + DB_TB_SERIES +
                        " Where Upper(" + DOM_SERIES_NAME + ") = Upper(?)" + COLLATION);
        }
        mGetSeriesIdStmt.bindString(1, name);
        try {
            return mGetSeriesIdStmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignore) {
            return 0;
        }
    }

    private long getSeriesIdOrInsert(@NonNull final String name) {
        long id = getSeriesId(name);
        if (id == 0) {
            id = insertSeries(name);
        }
        return id;
    }

    /**
     *
     * @throws SQLiteDoneException FIXME
     */
    public void globalReplaceSeries(@NonNull final Series from,
                                    @NonNull final Series to) throws SQLiteDoneException{
        if (to.id == 0) {
            long id = getSeriesId(to);
            if (id != 0) {
                to.id = id;
            } else {
                insertOrUpdateSeries(to);
            }
        }
        else
            sendSeries(to);

        // Do some basic sanity checks
        if (from.id == 0) {
            from.id = getSeriesId(from);
        }
        if (from.id == 0) {
            throw new RuntimeException("Old Series is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyBySeries(from.id);

            // Update books but prevent duplicate index errors
            String sql = "Update " + DB_TB_BOOK_SERIES + " Set " + DOM_SERIES_ID + " = " + to.id +
                    " Where " + DOM_SERIES_ID + " = " + from.id +
                    " and Not Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where " +
                    "  bs." + DOM_BOOK_ID + " = " + DB_TB_BOOK_SERIES + "." + DOM_BOOK_ID +
                    "   and bs." + DOM_SERIES_ID + " = " + to.id + ")";
            mSyncedDb.execSQL(sql);

            globalReplacePositionedBookItem(DB_TB_BOOK_SERIES,DOM_SERIES_ID.name, DOM_SERIES_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Update or create the passed series.
     */
    public void sendSeries(@NonNull final Series series) {
        if (series.id == 0) {
            series.id = getSeriesId(series);
        }
        insertOrUpdateSeries(series);
        return;
    }

    /** {@link #purgeSeries} */
    private SynchronizedStatement mPurgeBookSeriesStmt = null;
    /** {@link #purgeSeries} */
    private SynchronizedStatement mPurgeSeriesStmt = null;

    /**
     * Delete the series with no related books
     *
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean purgeSeries() {
        if (mPurgeBookSeriesStmt == null) {
            mPurgeBookSeriesStmt = mStatements.add("mPurgeBookSeriesStmt", "Delete From "+ DB_TB_BOOK_SERIES + " Where "
                    + DOM_BOOK_ID + " NOT IN (SELECT DISTINCT " + DOM_ID + " FROM " + DB_TB_BOOKS + ")");
        }
        boolean success;
        // Delete DB_TB_BOOK_SERIES with no books
        try {
            mPurgeBookSeriesStmt.execute();
            success = true;
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge Book Authors");
            success = false;
        }

        if (mPurgeSeriesStmt == null) {
            mPurgeSeriesStmt = mStatements.add("mPurgeSeriesStmt", "Delete from " + DB_TB_SERIES + " Where "
                    + DOM_ID + " NOT IN (SELECT DISTINCT " + DOM_SERIES_ID + " FROM " + DB_TB_BOOK_SERIES + ") ");
        }
        // Delete series entries with no Book_Series
        try {
            mPurgeSeriesStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge Book Authors");
            success = false;
        }
        return success;
    }

    /** {@link #getSeriesBookCount} */
    private SynchronizedStatement mGetSeriesBookCountQuery = null;

    /**
     *
     * @param series to find
     * @return id, or 0 when not found
     */
    public long getSeriesBookCount(@NonNull final Series series) {
        if (series.id == 0) {
            series.id = getSeriesId(series);
        }
        if (series.id == 0) {
            return 0;
        }

        if (mGetSeriesBookCountQuery == null) {
            mGetSeriesBookCountQuery = mStatements.add("mGetSeriesBookCountQuery",
                    "Select Count(" + DOM_BOOK_ID + ") From " + DB_TB_BOOK_SERIES + " Where " + DOM_SERIES_ID + "=?");
        }
        // Be cautious
        synchronized(mGetSeriesBookCountQuery) {
            mGetSeriesBookCountQuery.bindLong(1, series.id);
            try {
                return mGetSeriesBookCountQuery.simpleQueryForLong();
            } catch (SQLiteDoneException ignore) {
                return 0;
            }
        }
    }

    /**
     * Utility routine to build an arrayList of all series names.
     */
    @NonNull
    public ArrayList<String> getAllSeries() {
        String sql = "SELECT DISTINCT " + DOM_SERIES_NAME + " FROM " + DB_TB_SERIES + "" +
                " ORDER BY Upper(" + DOM_SERIES_NAME + ") " + COLLATION;
        return getColumnAsList(sql, DOM_SERIES_NAME.name);
    }


    /**
     * Return a Cursor over the list of all authors  in the database for the given book
     *
     * @param rowId the rowId of the book
     * @return Cursor over all authors
     */
    private Cursor fetchAllSeriesByBook(long rowId) {
        String sql = "SELECT DISTINCT s." + DOM_ID + " as " + DOM_ID
                + ", s." + DOM_SERIES_NAME + " as " + DOM_SERIES_NAME
                + ", bs." + DOM_SERIES_NUM + " as " + DOM_SERIES_NUM
                + ", bs." + DOM_SERIES_POSITION + " as " + DOM_SERIES_POSITION
                + ", " + DOM_SERIES_NAME + "||' ('||" + DOM_SERIES_NUM + "||')' as " + DOM_SERIES_FORMATTED
                + " FROM " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s "
                + "       On s." + DOM_ID + " = bs." + DOM_SERIES_ID
                + " WHERE bs." + DOM_BOOK_ID + "=" + rowId + " "
                + " ORDER BY bs." + DOM_SERIES_POSITION + ", Upper(s." + DOM_SERIES_NAME + ") " + COLLATION + " ASC";
        return mSyncedDb.rawQuery(sql, new String[]{});
    }


    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Classic Series">

    /**
     * This will return a list of all series within the given bookshelf where the
     * series, title or author meet the search string
     *
     * @param searchText The query string to search for
     * @param bookshelf The bookshelf to search within. Can be the string "All Books"
     * @return Cursor over all notes
     */
    @NonNull
    public Cursor classicFetchSeries(@NonNull final String searchText, @NonNull final String bookshelf) {
        /// Need to know when to add the 'no series' series...
        String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");

        String sql = "Select DISTINCT Case When s." + DOM_ID + " is NULL Then -1 Else s." + DOM_ID + " End as " + DOM_ID + ","
                + " Case When s." + DOM_SERIES_NAME + " is NULL Then '" + META_EMPTY_SERIES + "'"
                + "               Else " + DOM_SERIES_NAME + " End AS " + DOM_SERIES_NAME
                + " From (Select b." + DOM_ID + " as " + DOM_ID + " " + baseSql + " ) MatchingBooks"
                + " Left Outer Join " + DB_TB_BOOK_SERIES + " bs "
                + "     On bs." + DOM_BOOK_ID + " = MatchingBooks." + DOM_ID
                + " Left Outer Join " + DB_TB_SERIES + " s "
                + "     On s." + DOM_ID + " = bs." + DOM_SERIES_ID
                + " Order by Upper(s." + DOM_SERIES_NAME + ") " + COLLATION + " ASC ";

        return mSyncedDb.rawQuery(sql, new String[]{});
    }


    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="SQL String creation">

    private String makeSearchTerm(@NonNull final String key, @NonNull final String text) {
        return "Upper(" + key + ") LIKE Upper('%" + text + "%') " + COLLATION;
    }

    private String makeEqualFieldsTerm(@NonNull final String v1, @NonNull final String v2) {
        return "Upper(" + v1 + ") = Upper(" + v2 + ") " + COLLATION;
    }

    private String makeTextTerm(@NonNull final String field, @NonNull final String op, @NonNull final String text) {
        return "Upper(" + field + ") " + op + " Upper('" + encodeString(text) + "') " + COLLATION;
    }

    private String authorSearchPredicate(@NonNull final String search_term) {
        return "(" + makeSearchTerm(DOM_AUTHOR_FAMILY_NAME.name, search_term) + " OR " +
                makeSearchTerm(DOM_AUTHOR_GIVEN_NAMES.name, search_term) + ")";
    }

    private String bookSearchPredicate(@NonNull final String search_term) {
        StringBuilder result = new StringBuilder("(");

        // Just do a simple search of a bunch of fields.
        final String[] keys = new String[] {DOM_TITLE.name, DOM_ISBN.name, DOM_PUBLISHER.name, DOM_NOTES.name, DOM_BOOK_LOCATION.name, DOM_DESCRIPTION.name};
        for(String k : keys) {
            result.append(makeSearchTerm(k, search_term)).append(" OR ");
        }

        // And check the series too.
        result.append(" Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bsw " + " Join " + DB_TB_SERIES + " s " +
                "     On s.").append(DOM_ID).append(" = bsw.").append(DOM_SERIES_ID)
                .append("         And ").append(makeSearchTerm("s." + DOM_SERIES_NAME, search_term))
                .append(" Where bsw.").append(DOM_BOOK_ID).append(" = b.").append(DOM_ID)
                .append(") ");

        //and check the anthologies too.
        result.append(" OR Exists (SELECT NULL FROM  " + DB_TB_ANTHOLOGY + " bsan, " + DB_TB_AUTHORS + " bsau " +
                " WHERE bsan.").append(DOM_AUTHOR_ID).append("= bsau.").append(DOM_ID)
                .append(" AND bsan.").append(DOM_BOOK_ID).append(" = b.").append(DOM_ID)
                .append(" AND ").append("(").append(makeSearchTerm("bsan." + DOM_TITLE, search_term))
                .append(" OR ").append(makeSearchTerm("bsau." + DOM_AUTHOR_FAMILY_NAME, search_term))
                .append(" OR ").append(makeSearchTerm("bsau." + DOM_AUTHOR_GIVEN_NAMES, search_term))
                .append(")) ");

        result.append( ")") ;

        return result.toString();
    }

    /**
     * Return the SQL for a list of all books in the database matching the passed criteria. The SQL
     * returned can only be assumed to have the books table with alias 'b' and starts at the 'FROM'
     * clause.
     *
     * A possible addition would be to specify the conditions under which other data may be present.
     *
     * @param order What order to return the books
     * @param bookshelf Which bookshelf is it in. Can be "All Books"
     * @param authorWhere 	Extra SQL pertaining to author predicate to be applied
     * @param bookWhere 	Extra SQL pertaining to book predicate to be applied
     * @param searchText	Raw text string to search for
     * @param loaned_to		Name of person to whom book was loaned
     * @param seriesName	Name of series to match
     *
     * @return SQL text for basic lookup
     *
     * ENHANCE: Replace exact-match String parameters with long parameters containing the ID.
     */
    private String fetchAllBooksInnerSql(@Nullable final String order, @NonNull final String bookshelf,
                                         @NonNull final String authorWhere, @NonNull final String bookWhere,
                                         @NonNull String searchText, @NonNull final String loaned_to,
                                         @NonNull final String seriesName) {
        String where = "";

        if (!bookWhere.isEmpty()) {
            if (!where.isEmpty())
                where += " and";
            where += " (" + bookWhere + ")";
        }

        if (!searchText.isEmpty()) {
            searchText = encodeString(searchText);
            if (!where.isEmpty())
                where += " and";
            where += "( (" + bookSearchPredicate(searchText) + ") "
                    + " OR Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba"
                    + "            Join " + DB_TB_AUTHORS + " a On a." + DOM_ID + " = ba." + DOM_AUTHOR_ID
                    + "           Where " + authorSearchPredicate(searchText) + " and ba." + DOM_BOOK_ID + " = b." + DOM_ID + ")"
                    + ")";
            // This is done in bookSearchPredicate().
            //+ " OR Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs"
            //+ "            Join " + DB_TB_SERIES + " s On s." + KEY_ID + " = bs." + KEY_SERIES_ID
            //+ "           Where s." + KEY_SERIES_NAME + " Like '%" + searchText + "' and bs." + KEY_BOOK_ID + " = b." + KEY_ID + ")"
            //+ ")";
        }

        if (!authorWhere.isEmpty()) {
            if (!where.isEmpty())
                where += " and";
            where += " Exists(Select NULL From " + DB_TB_AUTHORS + " a "
                    + " Join " + DB_TB_BOOK_AUTHOR + " ba "
                    + "     On ba." + DOM_AUTHOR_ID + " = a." + DOM_ID
                    + " Where " + authorWhere + " And ba." + DOM_BOOK_ID + " = b." + DOM_ID
                    + ")";
        }

        if (!loaned_to.isEmpty()) {
            if (!where.isEmpty())
                where += " and";
            where += " Exists(Select NULL From " + DB_TB_LOAN + " l Where "
                    + " l." + DOM_BOOK_ID + "=b." + DOM_ID
                    + " And " + makeTextTerm("l." + DOM_LOANED_TO, "=", loaned_to) + ")";
        }

        if (!seriesName.isEmpty() && seriesName.equals(META_EMPTY_SERIES)) {
            if (!where.isEmpty())
                where += " and";
            where += " Not Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
                    + " bs." + DOM_BOOK_ID + "=b." + DOM_ID + ")";
        }

        String sql = " FROM " + DB_TB_BOOKS + " b";

        if (!bookshelf.isEmpty() && !bookshelf.trim().isEmpty()) {
            // Join with specific bookshelf
            sql += " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbsx On bbsx." + DOM_BOOK_ID + " = b." + DOM_ID;
            sql += " Join " + DB_TB_BOOKSHELF + " bsx On bsx." + DOM_ID + " = bbsx." + DOM_BOOKSHELF_NAME
                    + " and " + makeTextTerm("bsx." + DOM_BOOKSHELF_NAME, "=", bookshelf);
        }

        if (!seriesName.isEmpty() && !seriesName.equals(META_EMPTY_SERIES))
            sql += " Join " + DB_TB_BOOK_SERIES + " bs On (bs." + DOM_BOOK_ID + " = b." + DOM_ID + ")"
                    + " Join " + DB_TB_SERIES + " s On (s." + DOM_ID + " = bs." + DOM_SERIES_ID
                    + " and " + makeTextTerm("s." + DOM_SERIES_NAME, "=", seriesName) + ")";


        if (!where.isEmpty())
            sql += " WHERE " + where;

        // NULL order suppresses order-by
        if (order != null) {
            if (!order.isEmpty())
                // TODO Assess if ORDER is used and how
                sql += " ORDER BY " + order + "";
            else
                sql += " ORDER BY Upper(b." + DOM_TITLE + ") " + COLLATION + " ASC";
        }

        return sql;
    }

    /**
     * Return the SQL for a list of all books in the database
     *
     * @param order 		What order to return the books; comman separated list of column names
     * @param bookshelf 	Which bookshelf is it in. Can be "All Books"
     * @param authorWhere	clause to add to author search criteria
     * @param bookWhere		clause to add to books search criteria
     * @param searchText	text to search for
     * @param loaned_to		name of person to whom book is loaned
     * @param seriesName	name of series to look for
     *
     * @return				A full piece of SQL to perform the search
     */
    private String fetchAllBooksSql(@Nullable final String order, @NonNull final String bookshelf,
                                    @NonNull final String authorWhere, @NonNull final String bookWhere,
                                    @NonNull final String searchText, @NonNull final String loaned_to,
                                    @NonNull final String seriesName) {
        String baseSql = this.fetchAllBooksInnerSql("", bookshelf, authorWhere, bookWhere, searchText, loaned_to, seriesName);

        // Get the basic query; we will use it as a sub-query
        String sql = "SELECT DISTINCT " + getBookFields("b", DOM_ID.name) + baseSql;
        String fullSql = "Select b.*, " + getAuthorFields("a", "") + ", " +
                "a." + DOM_AUTHOR_ID + ", " +
                "Coalesce(s." + DOM_SERIES_ID + ", 0) as " + DOM_SERIES_ID + ", " +
                "Coalesce(s." + DOM_SERIES_NAME + ", '') as " + DOM_SERIES_NAME + ", " +
                "Coalesce(s." + DOM_SERIES_NUM + ", '') as " + DOM_SERIES_NUM + ", " +
                " Case When _num_series < 2 Then Coalesce(s." + DOM_SERIES_FORMATTED + ", '')" +
                " Else " + DOM_SERIES_FORMATTED + "||' et. al.' End as " + DOM_SERIES_FORMATTED + " " +
                " from (" + sql + ") b";

        // Get the 'default' author...defined in getBookFields()
        fullSql += " Join (Select "
                + DOM_AUTHOR_ID + ", "
                + DOM_AUTHOR_FAMILY_NAME + ", "
                + DOM_AUTHOR_GIVEN_NAMES + ", "
                + "ba." + DOM_BOOK_ID + " as " + DOM_BOOK_ID + ", "
                + " Case When " + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME
                + " Else " + authorFormattedSource("") + " End as " + DOM_AUTHOR_FORMATTED
                + " From " + DB_TB_BOOK_AUTHOR + " ba Join " + DB_TB_AUTHORS + " a"
                + "    On ba." + DOM_AUTHOR_ID + " = a." + DOM_ID + ") a "
                + " On a." + DOM_BOOK_ID + " = b." + DOM_ID + " and a." + DOM_AUTHOR_ID + " = b." + DOM_AUTHOR_ID;

        if (!seriesName.isEmpty() && !seriesName.equals(META_EMPTY_SERIES)) {
            // Get the specified series...
            fullSql += " Left Outer Join (Select "
                    + DOM_SERIES_ID + ", "
                    + DOM_SERIES_NAME + ", "
                    + DOM_SERIES_NUM  + ", "
                    + "bs." + DOM_BOOK_ID + " as " + DOM_BOOK_ID + ", "
                    + " Case When " + DOM_SERIES_NUM + " = '' Then " + DOM_SERIES_NAME
                    + " Else " + DOM_SERIES_NAME + "||' #'||" + DOM_SERIES_NUM + " End as " + DOM_SERIES_FORMATTED
                    + " From " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
                    + "    On bs." + DOM_SERIES_ID + " = s." + DOM_ID + ") s "
                    + " On s." + DOM_BOOK_ID + " = b." + DOM_ID
                    + " and " + makeTextTerm("s." + DOM_SERIES_NAME, "=", seriesName);
            //+ " and " + this.makeEqualFieldsTerm("s." + KEY_SERIES_NUM, "b." + KEY_SERIES_NUM);
        } else {
            // Get the 'default' series...defined in getBookFields()
            fullSql += " Left Outer Join (Select "
                    + DOM_SERIES_ID + ", "
                    + DOM_SERIES_NAME + ", "
                    + DOM_SERIES_NUM  + ", "
                    + "bs." + DOM_BOOK_ID + " as " + DOM_BOOK_ID + ", "
                    + " Case When " + DOM_SERIES_NUM + " = '' Then " + DOM_SERIES_NAME
                    + " Else " + DOM_SERIES_NAME + "||' #'||" + DOM_SERIES_NUM + " End as " + DOM_SERIES_FORMATTED
                    + " From " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
                    + "    On bs." + DOM_SERIES_ID + " = s." + DOM_ID + ") s "
                    + " On s." + DOM_BOOK_ID + " = b." + DOM_ID
                    + " and s." + DOM_SERIES_ID + " = b." + DOM_SERIES_ID
                    + " and " + this.makeEqualFieldsTerm("s." + DOM_SERIES_NUM, "b." + DOM_SERIES_NUM);
        }
        if (order != null && !order.isEmpty()) {
            fullSql += " ORDER BY " + order;
        }
        return fullSql;
    }


    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Helpers">

    /**
     * Utility routine to fill an array with the specified column from the passed SQL.
     *
     * @see #getFirstColumnAsList
     *
     * @param sql			SQL to execute
     * @param columnName	Column to fetch
     *
     * @return				List of values
     */
    @NonNull
    private ArrayList<String> getColumnAsList(@NonNull final String sql, @NonNull final String columnName) {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            int column = cursor.getColumnIndexOrThrow(columnName);
            while (cursor.moveToNext()) {
                String name = cursor.getString(column);
                list.add(name);
            }
            return list;
        }
    }

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList
     *
     * @see #getColumnAsList
     *
     * @param cursor     cursor
     *
     * @return  List of values
     */
    @NonNull
    private ArrayList<String> getFirstColumnAsList(@NonNull final Cursor cursor) {
        ArrayList<String> list = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null) {
                    // Hash to *try* to avoid duplicates
                    Set<String> foundSoFar = new HashSet<>();
                    if (!name.isEmpty() && !foundSoFar.contains(name.toLowerCase())) {
                        foundSoFar.add(name.toLowerCase());
                        list.add(name);
                    }
                }
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    /**
     * Return a ContentValues collection containing only those values from 'source' that match columns in 'dest'.
     * - Exclude the primary key from the list of columns.
     * - data will be transformed based on the intended type of the underlying column based on column definition.
     *
     * @param bookData	Source column data
     * @return New, filtered, collection
     */
    @NonNull
    private ContentValues filterValues(@NonNull final BookData bookData) {
        ContentValues values = new ContentValues();
        // Create the arguments
        for (String key : bookData.keySet()) {
            // Get column info for this column.
            TableInfo.ColumnInfo columnInfo = mBooksInfo.getColumn(key);
            // Check if we actually have a matching column.
            if (columnInfo != null) {
                // Never update PK.
                if (!columnInfo.isPrimaryKey) {

                    Object entry = bookData.get(key);

                    // Try to set the appropriate value, but if that fails, just use TEXT...
                    try {

                        switch (columnInfo.typeClass) {

                            case TableInfo.CLASS_REAL:
                                if (entry instanceof Float)
                                    values.put(columnInfo.name, (Float) entry);
                                else
                                    values.put(columnInfo.name, Float.parseFloat(entry.toString()));
                                break;

                            case TableInfo.CLASS_INTEGER:
                                if (entry instanceof Boolean) {
                                    if ((Boolean) entry) {
                                        values.put(columnInfo.name, 1);
                                    } else {
                                        values.put(columnInfo.name, 0);
                                    }
                                } else if (entry instanceof Integer) {
                                    values.put(columnInfo.name, (Integer) entry);
                                } else {
                                    values.put(columnInfo.name, Integer.parseInt(entry.toString()));
                                }
                                break;

                            case TableInfo.CLASS_TEXT:
                                if (entry instanceof String)
                                    values.put(columnInfo.name, ((String) entry));
                                else
                                    values.put(columnInfo.name, entry.toString());
                                break;
                        }

                    } catch (Exception e) {
                        if (entry != null)
                            values.put(columnInfo.name, entry.toString());
                    }
                }
            }
        }
        return values;
    }

    /**
     * A helper function to get a single int value (from the first row) from a cursor
     *
     * @param cursor to extract from
     *
     * @return the value, or 0 when not found
     */
    private int getIntValue(@NonNull final Cursor cursor) {
        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } catch (CursorIndexOutOfBoundsException e) {
            return 0;
        }
    }

    @NonNull
    public static String encodeString(@NonNull final String value) {
    	return value.replace("'", "''");
    }

    /** Static Factory object to create the custom cursor */
    @NonNull
    private static final CursorFactory mBooksFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                @NonNull final SQLiteDatabase db,
                @NonNull final SQLiteCursorDriver masterQuery,
                @NonNull final String editTable,
                @NonNull final SQLiteQuery query)
        {
            return new BooksCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    /**
     * The passed sql should at least return some of the fields from the books table!
     * If a method call is made to retrieve a column that does not exists, an exception
     * will be thrown.
     *
     * @return	A new BooksCursor
     */
    private BooksCursor fetchBooks(@NonNull final String sql,
                                   @NonNull final String[] selectionArgs) {
        return (BooksCursor) mSyncedDb.rawQueryWithFactory(mBooksFactory, sql, selectionArgs, "");
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Goodreads support">

    /**
     * Return a book (Cursor) that matches the given goodreads book Id.
     * Note: MAYE RETURN MORE THAN ONE BOOK
     *
     * @param grId Goodreads id of book(s) to retrieve
     *
     * @return Cursor positioned to matching book, if found
     *
     * @throws SQLException if note could not be found/retrieved
     */
    public BooksCursor fetchBooksByGoodreadsBookId(long grId) throws SQLException {
        String where = TBL_BOOKS.dot(DOM_GOODREADS_BOOK_ID) + "=" + grId;
        return fetchAllBooks("", "", "", where, "", "", "");
    }

    /**
     * Query to get all book IDs and ISBN for sending to goodreads.
     */
    @NonNull
    public BooksCursor getAllBooksForGoodreadsCursor(final long startId, final boolean updatesOnly) {
        String sql = "Select " + DOM_ISBN + ", " + DOM_ID + ", " + DOM_GOODREADS_BOOK_ID + "," +
                " " + DOM_NOTES + ", " + DOM_BOOK_READ + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_RATING +
                " from " + DB_TB_BOOKS + " Where " + DOM_ID + " > " + startId;
        if (updatesOnly) {
            sql += " and " + DOM_LAST_UPDATE_DATE + " > " + DOM_GOODREADS_LAST_SYNC_DATE;
        }
        sql += " Order by " + DOM_ID;

        return fetchBooks(sql, new String[]{});
    }

    /**
     * Query to get a specific book ISBN from the ID for sending to goodreads.
     */
    @NonNull
    public BooksCursor getBookForGoodreadsCursor(final long bookId) {
        String sql = "Select " + DOM_ID + ", " + DOM_ISBN + ", " + DOM_GOODREADS_BOOK_ID +
                ", " + DOM_NOTES + ", " + DOM_BOOK_READ + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_RATING +
                " from " + DB_TB_BOOKS + " Where " + DOM_ID + " = " + bookId + " Order by " + DOM_ID;
        return fetchBooks(sql, new String[]{});
    }

    /**
     * Query to get a all bookshelves for a book, for sending to goodreads.
     */
    @NonNull
    public Cursor getAllBookBookshelvesForGoodreadsCursor(final long bookId) {
        String sql = "Select s." + DOM_BOOKSHELF_NAME + " from " + DB_TB_BOOKSHELF + " s"
                + " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs On bbs." + DOM_BOOKSHELF_NAME + " = s." + DOM_ID
                + " and bbs." + DOM_BOOK_ID + " = " + bookId + " Order by s." + DOM_BOOKSHELF_NAME;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /** {@link #setGoodreadsBookId} */
    private SynchronizedStatement mSetGoodreadsBookIdStmt = null;

    /**
     * Set the goodreads book id for this passed book. This is used by other goodreads-related
     * functions.
     *
     * @param bookId			Book to update
     * @param goodreadsBookId	GR book id
     */
    public void setGoodreadsBookId(final long bookId, final long goodreadsBookId) {
        if (mSetGoodreadsBookIdStmt == null ) {
            String sql = "Update " + TBL_BOOKS + " Set " + DOM_GOODREADS_BOOK_ID + " = ? Where " + DOM_ID + " = ?";
            mSetGoodreadsBookIdStmt = mStatements.add("mSetGoodreadsBookIdStmt", sql);
        }
        mSetGoodreadsBookIdStmt.bindLong(1, goodreadsBookId);
        mSetGoodreadsBookIdStmt.bindLong(2, bookId);
        mSetGoodreadsBookIdStmt.execute();
    }

    /** {@link #setGoodreadsSyncDate} */
    private SynchronizedStatement mSetGoodreadsSyncDateStmt = null;

    /**
     * Set the goodreads sync date to the current time
     */
    public void setGoodreadsSyncDate(final long bookId) {
        if (mSetGoodreadsSyncDateStmt == null) {
            String sql = "Update " + DB_TB_BOOKS + " Set " + DOM_GOODREADS_LAST_SYNC_DATE + " = current_timestamp Where " + DOM_ID + " = ?";
            mSetGoodreadsSyncDateStmt = mStatements.add("mSetGoodreadsSyncDateStmt", sql);
        }
        mSetGoodreadsSyncDateStmt.bindLong(1, bookId);
        mSetGoodreadsSyncDateStmt.execute();
    }

	/** Support statement for {@link #getGoodreadsSyncDate} */
	private SynchronizedStatement mGetGoodreadsSyncDateStmt = null;
	/**
	 * Set the goodreads sync date to the current time
	 *
	 * @param bookId if found
     *
     * @throws SQLiteDoneException if not found
	 */
    @SuppressWarnings("unused")
    @NonNull
	public String getGoodreadsSyncDate(final long bookId)  throws SQLiteDoneException{
		if (mGetGoodreadsSyncDateStmt == null) {
			String sql = "Select " + DOM_GOODREADS_LAST_SYNC_DATE + " From " + DB_TB_BOOKS + " Where " + DOM_ID + " = ?";
			mGetGoodreadsSyncDateStmt = mStatements.add("mGetGoodreadsSyncDateStmt", sql);
		}
		mGetGoodreadsSyncDateStmt.bindLong(1, bookId);
		return mGetGoodreadsSyncDateStmt.simpleQueryForString();
	}
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Database house keeping">

    /** Flag indicating {@link #close()} has been called */
    private boolean mCloseWasCalled = false;

    private SqlStatementManager mStatements;

    /**
     * Open the books database. If it cannot be opened, try to create a new instance of the
     * database. If it cannot be created, throw an exception to signal the failure
     *
     * @throws SQLException if the database could be neither opened or created
     */
    public void open() throws SQLException {

        if (mSyncedDb == null) {
            // Get the DB wrapper
            mSyncedDb = new SynchronizedDb(mDbHelper, mSynchronizer);
            // Turn on foreign key support so that CASCADE works.
            mSyncedDb.execSQL("PRAGMA foreign_keys = ON");
            // Turn on recursive triggers; not strictly necessary
            mSyncedDb.execSQL("PRAGMA recursive_triggers = ON");
        }
        //mSyncedDb.execSQL("PRAGMA temp_store = FILE");
        mStatements = new SqlStatementManager(mSyncedDb);
    }


    /**
     * Generic function to close the database
     */
    public void close() {

        if (!mCloseWasCalled) {
            mCloseWasCalled = true;

            if (mStatements != null) {
                mStatements.close();
            }

            if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
                synchronized(mInstanceCount) {
                    mInstanceCount--;
                    System.out.println("CatDBA instances: " + mInstanceCount);
                    //removeInstance(this);
                }
            }
        }
    }

    @Override
    protected void finalize() {
        close();
    }

    public SyncLock startTransaction(boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }
    public void endTransaction(SyncLock lock) {
        mSyncedDb.endTransaction(lock);
    }
    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="FTS Support">
    /**
	 * Send the book details from the cursor to the passed fts query.
	 *
	 * NOTE: This assumes a specific order for query parameters.
	 *
	 * @param books		Cursor of books to update
	 * @param stmt		Statement to execute
	 *
	 */
    private void ftsSendBooks(@NonNull final BooksCursor books, @NonNull final SynchronizedStatement stmt) {

		final BooksRow book = books.getRowView();
		// Build the SQL to get author details for a book.
		// ... all authors
		final String authorBaseSql = "Select " + TBL_AUTHORS.dot("*") + " from " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
				" Where " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + " = ";
		// ... all series
		final String seriesBaseSql = "Select " + TBL_SERIES.dot(DOM_SERIES_NAME) + " || ' ' || Coalesce(" + TBL_BOOK_SERIES.dot(DOM_SERIES_NUM) + ",'') as seriesInfo from " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
				" Where " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = ";
		// ... all anthology titles
		final String anthologyBaseSql = "Select " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " as anthologyAuthorInfo, " + DOM_TITLE + " as anthologyTitleInfo "
				+ " from " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_AUTHORS) +
				" Where " + TBL_ANTHOLOGY.dot(DOM_BOOK_ID) + " = ";

		// Accumulator for author names for each book
		StringBuilder authorText = new StringBuilder();
		// Accumulator for series names for each book
		StringBuilder seriesText = new StringBuilder();
		// Accumulator for title names for each anthology
		StringBuilder titleText = new StringBuilder();
		// Indexes of author name fields.
		int colGivenNames = -1;
		int colFamilyName = -1;
		int colSeriesInfo = -1;
		int colAnthologyAuthorInfo = -1;
		int colAnthologyTitleInfo = -1;

		// Process each book
		while (books.moveToNext()) {
			// Reset authors/series/title
			authorText.setLength(0);
			seriesText.setLength(0);
			titleText.setLength(0);
			// Get list of authors
			try (Cursor c = mSyncedDb.rawQuery(authorBaseSql + book.getId())) {
				// Get column indexes, if not already got
				if (colGivenNames < 0) {
                    colGivenNames = c.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);
                }
				if (colFamilyName < 0) {
                    colFamilyName = c.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
                }
				// Append each author
				while (c.moveToNext()) {
					authorText.append(c.getString(colGivenNames));
					authorText.append(" ");
					authorText.append(c.getString(colFamilyName));
					authorText.append(";");
				}
			}

			// Get list of series
			try (Cursor c = mSyncedDb.rawQuery(seriesBaseSql + book.getId())) {
				// Get column indexes, if not already got
				if (colSeriesInfo < 0) {
                    colSeriesInfo = c.getColumnIndex("seriesInfo");
                }
				// Append each series
				while (c.moveToNext()) {
					seriesText.append(c.getString(colSeriesInfo));
					seriesText.append(";");
				}
			}

			// Get list of anthology data (author and title)
			try (Cursor c = mSyncedDb.rawQuery(anthologyBaseSql + book.getId())) {
				// Get column indexes, if not already got
				if (colAnthologyAuthorInfo < 0) {
                    colAnthologyAuthorInfo = c.getColumnIndex("anthologyAuthorInfo");
                }
				if (colAnthologyTitleInfo < 0) {
                    colAnthologyTitleInfo = c.getColumnIndex("anthologyTitleInfo");
                }
				// Append each series
				while (c.moveToNext()) {
					authorText.append(c.getString(colAnthologyAuthorInfo));
					authorText.append(";");
					titleText.append(c.getString(colAnthologyTitleInfo));
					titleText.append(";");
				}
			}

			// Set the parameters and call
			bindStringOrNull(stmt, 1, authorText.toString());
			// Titles should only contain title, not SERIES
			bindStringOrNull(stmt, 2, book.getTitle() + "; " + titleText);
			// We could add a 'series' column, or just add it as part of the description
			bindStringOrNull(stmt, 3, book.getDescription() + seriesText);
			bindStringOrNull(stmt, 4, book.getNotes());
			bindStringOrNull(stmt, 5, book.getPublisher());
			bindStringOrNull(stmt, 6, book.getGenre());
			bindStringOrNull(stmt, 7, book.getLocation());
			bindStringOrNull(stmt, 8, book.getIsbn());
			stmt.bindLong(9, book.getId());

			stmt.execute();
		}
	}

	/** {@link #insertFts} */
    private SynchronizedStatement mInsertFtsStmt = null;

	/**
	 * Insert a book into the FTS. Assumes book does not already exist in FTS.
	 */
	private void insertFts(final long bookId) {
		long t0 = System.currentTimeMillis();

		if (mInsertFtsStmt == null) {
			// Build the FTS insert statement base. The parameter order MUST match the order expected in ftsSendBooks().
			String sql = TBL_BOOKS_FTS.getInsert(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_NOTES,
												DOM_PUBLISHER, DOM_BOOK_GENRE, DOM_BOOK_LOCATION, DOM_ISBN, DOM_DOCID)
												+ " Values (?,?,?,?,?,?,?,?,?)";
			mInsertFtsStmt = mStatements.add("mInsertFtsStmt", sql);
		}

		BooksCursor books = null;

		// Start an update TX
		SyncLock l = null;
		if (!mSyncedDb.inTransaction()) {
            l = mSyncedDb.beginTransaction(true);
        }
		try {
			// Compile statement and get books cursor
			books = fetchBooks("select * from " + TBL_BOOKS + " where " + DOM_ID + " = " + bookId, new String[]{});
			// Send the book
			ftsSendBooks(books, mInsertFtsStmt);
			if (l != null) {
                mSyncedDb.setTransactionSuccessful();
            }
		} finally {
			if (books != null) {
                try { books.close(); } catch (Exception ignore) {}
            }
            if (l != null) {
                mSyncedDb.endTransaction(l);
            }
			if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                long t1 = System.currentTimeMillis();
                System.out.println("Inserted FTS in " + (t1-t0) + "ms");
            }
		}
	}

	/** {@link #updateFts} */
    private SynchronizedStatement mUpdateFtsStmt = null;

	/**
	 * Update an existing FTS record.
	 */
	private void updateFts(final long bookId) {
		long t0 = System.currentTimeMillis();

		if (mUpdateFtsStmt == null) {
			// Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
			String sql = TBL_BOOKS_FTS.getUpdate(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_NOTES,
												DOM_PUBLISHER, DOM_BOOK_GENRE, DOM_BOOK_LOCATION, DOM_ISBN)
												+ " Where " + DOM_DOCID + " = ?";
			mUpdateFtsStmt = mStatements.add("mUpdateFtsStmt", sql);
		}
		BooksCursor books = null;

		SyncLock l = null;
		if (!mSyncedDb.inTransaction()) {
            l = mSyncedDb.beginTransaction(true);
        }
		try {
			// Compile statement and get cursor
			books = fetchBooks("select * from " + TBL_BOOKS + " where " + DOM_ID + " = " + bookId, new String[]{});
			ftsSendBooks(books, mUpdateFtsStmt);
			if (l != null) {
                mSyncedDb.setTransactionSuccessful();
            }
		} finally {
			if (books != null) {
                try { books.close(); } catch (Exception ignore) {}
            }
            if (l != null) {
                mSyncedDb.endTransaction(l);
            }
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                long t1 = System.currentTimeMillis();
                System.out.println("Updated FTS in " + (t1-t0) + "ms");
            }
		}
	}

    /** {@link #deleteFts} */
    private SynchronizedStatement mDeleteFtsStmt = null;

    /**
	 * Delete an existing FTS record
	 */
	private void deleteFts(final long bookId) {
		long t0 = System.currentTimeMillis();
		if (mDeleteFtsStmt == null) {
			String sql = "Delete from " + TBL_BOOKS_FTS + " Where " + DOM_DOCID + " = ?";
			mDeleteFtsStmt = mStatements.add("mDeleteFtsStmt", sql);
		}
		mDeleteFtsStmt.bindLong(1, bookId);
		mDeleteFtsStmt.execute();
		if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            long t1 = System.currentTimeMillis();
            System.out.println("Deleted from FTS in " + (t1-t0) + "ms");
        }
	}

	/**
	 * Rebuild the entire FTS database. This can take several seconds with many books or a slow phone.
	 */
	public void rebuildFts() {
		long t0;
		if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
			t0 = System.currentTimeMillis();
		}
		boolean gotError = false;

		// Make a copy of the FTS table definition for our temp table.
		TableDefinition ftsTemp = TBL_BOOKS_FTS.clone();
		// Give it a new name
		ftsTemp.setName(ftsTemp.getName() + "_temp");

		SynchronizedStatement insert = null;
		BooksCursor cursor = null;

		SyncLock l = null;
		if (!mSyncedDb.inTransaction()) {
            l = mSyncedDb.beginTransaction(true);
        }
		try {
			// Drop and recreate our temp copy
			ftsTemp.drop(mSyncedDb);
			ftsTemp.create(mSyncedDb, false);

			// Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
			final String sql = ftsTemp.getInsert(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_NOTES, DOM_PUBLISHER, DOM_BOOK_GENRE, DOM_BOOK_LOCATION, DOM_ISBN, DOM_DOCID)
							+ " values (?,?,?,?,?,?,?,?,?)";

			// Compile an INSERT statement
			insert = mSyncedDb.compileStatement(sql);

			// Get ALL books in the DB
			cursor = fetchBooks("select * from " + TBL_BOOKS, new String[]{});
			// Send each book
			ftsSendBooks(cursor, insert);
			// Drop old table, ready for rename
			TBL_BOOKS_FTS.drop(mSyncedDb);
			// Done
			if (l != null) {
                mSyncedDb.setTransactionSuccessful();
            }
		} catch (Exception ignore) {
			Logger.logError(ignore);
			gotError = true;
		} finally {
			if (cursor != null)
				try { cursor.close(); } catch (Exception ignore) {}
            if (insert != null)
				try { insert.close(); } catch (Exception ignore) {}
            if (l != null) {
                mSyncedDb.endTransaction(l);
            }

			/* According to this:
			    http://old.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td29474500.html

			  FTS tables should only be renamed outside of transactions. Which is a pain.

			   => makes sense... this is DDL after all. DDL is not covered by most/all databases for transactions.
			   => very few (IMHO) programs manipulate tables themselves. The DBA's don't allow it.
			*/

			//  Delete old table and rename the new table
			if (!gotError) {
                mSyncedDb.execSQL("Alter Table " + ftsTemp + " rename to " + TBL_BOOKS_FTS);
            }
		}

		if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
			System.out.println("books reindexed in " + (System.currentTimeMillis() - t0) + "ms");
		}
	}

    /**
     * Utility function to bind a string or NULL value to a parameter since binding a NULL
     * in bindString produces an error.
     *
     * NOTE: We specifically want to use the default locale for this.
     */
    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt, int position, @Nullable final String s) {
        if (s == null) {
            stmt.bindNull(position);
        } else {
            //
            // Because FTS does not understand locales in all android up to 4.2,
            // we do case folding here using the default locale. TODO: check if still so.
            //
            stmt.bindString(position, s.toLowerCase(Locale.getDefault()));
        }
    }

    /**
     * Cleanup a search string to remove all quotes etc.
     *
     * Remove punctuation from the search string to TRY to match the tokenizer. The only punctuation we
     * allow is a hyphen preceded by a space. Everything else is translated to a space.
     *
     * TODO: Consider making '*' to the end of all words a preference item.
     *
     * @param search	Search criteria to clean
     * @return			Clean string
     */
    @NonNull
    public static String cleanupFtsCriterion(@NonNull final String search) {
        //return s.replace("'", " ").replace("\"", " ").trim();

        if (search.isEmpty()) {
            return search;
        }

        // Output buffer
        final StringBuilder out = new StringBuilder();
        // Array (convenience)
        final char[] chars = search.toCharArray();
        // Cached length
        final int len = chars.length;
        // Initial position
        int pos = 0;
        // Dummy 'previous' character
        char prev = ' ';

        // Loop over array
        while(pos < len) {
            char curr = chars[pos];
            // If current is letter or ...use it.
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
            } else if (curr == '-' && Character.isWhitespace(prev)) {
                // Allow negation if preceded by space
                out.append(curr);
            } else {
                // Everything else is whitespace
                curr = ' ';
                if (!Character.isWhitespace(prev)) {
                    // If prev character was non-ws, and not negation, make wildcard
                    if (prev != '-') {
                        // Make every token a wildcard
                        // TODO: Make this a preference
                        out.append('*');
                    }
                    // Append a whitespace only when last char was not a whitespace
                    out.append(' ');
                }
            }
            prev = curr;
            pos++;
        }
        if (!Character.isWhitespace(prev) && (prev != '-')) {
            // Make every token a wildcard
            // TODO: Make this a preference
            out.append('*');
        }
        return out.toString();
    }

    /**
     * Search the FTS table and return a cursor.
     *
     * ENHANCE: Integrate with existing search code, if we keep it.
     *
     * @param author		Author-related keywords to find
     * @param title			Title-related keywords to find
     * @param keywords		Keywords to find anywhere in book
     * @return				a cursor
     */
    @Nullable
    public Cursor searchFts(@NonNull String author, @NonNull String title, @NonNull String keywords) {
        author = cleanupFtsCriterion(author);
        title = cleanupFtsCriterion(title);
        keywords = cleanupFtsCriterion(keywords);

        if ((author.length() + title.length() + keywords.length()) == 0) {
            return null;
        }

        StringBuilder sql = new StringBuilder(
                "select " + DOM_DOCID + " from " + TBL_BOOKS_FTS + " where " + TBL_BOOKS_FTS + " match '" + keywords);
        for(String w : author.split(" ")) {
            if (!w.isEmpty()) {
                sql.append(" ").append(DOM_AUTHOR_NAME).append(":").append(w);
            }
        }
        for(String w : title.split(" ")) {
            if (!w.isEmpty()) {
                sql.append(" ").append(DOM_TITLE).append(":").append(w);
            }
        }
        sql.append("'");

        return mSyncedDb.rawQuery(sql.toString(), new String[]{});
    }

    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Backup & Export" >
    /**
     * Backup database file using default file name
     */
    public void backupDbFile() {
        backupDbFile("DbExport.db");
    }

    /**
     * Backup database file to the specified filename
     */
    public void backupDbFile(@NonNull final String suffix) {
        StorageUtils.backupDbFile(mSyncedDb.getUnderlyingDatabase(), suffix);
    }

    /**
     * A complete export of all tables (flattened) in the database
     *
     * @return BooksCursor over all books, authors, etc
     */
    @NonNull
    public BooksCursor exportBooks(@Nullable final Date sinceDate) {
        String sinceClause;
        if (sinceDate == null) {
            sinceClause = "";
        } else {
            sinceClause = " Where b." + DOM_LAST_UPDATE_DATE + " > '" + DateUtils.toSqlDateTime(sinceDate) + "' ";
        }

        String sql = "SELECT DISTINCT " +
                getBookFields("b", DOM_ID.name) + ", " +
                "l." +DOM_LOANED_TO + " as " + DOM_LOANED_TO + " " +
                " FROM " + DB_TB_BOOKS + " b" +
                " LEFT OUTER JOIN " + DB_TB_LOAN +" l ON (l." + DOM_BOOK_ID + "=b." + DOM_ID + ") " +
                sinceClause +
                " ORDER BY b._id";
        return fetchBooks(sql, new String[]{});
    }
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Cleanup routines v4">
    /**
     * Data cleanup routine called on upgrade to v4.0.3 to cleanup data integrity issues cased
     * by earlier merge code that could have left the first author or series for a book having
     * a position number > 1.
     */
    public void fixupAuthorsAndSeries() {
        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            fixupPositionedBookItems(DB_TB_BOOK_AUTHOR, DOM_AUTHOR_POSITION.name);
            fixupPositionedBookItems(DB_TB_BOOK_SERIES, DOM_SERIES_POSITION.name);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }

    }

    /**
     * Data cleaning routine for upgrade to version 4.0.3 to cleanup any books that have no primary author/series.
     */
    private void fixupPositionedBookItems(@NonNull final String tableName,
                                          @NonNull final String positionField) {
        String sql = "select b." + DOM_ID + " as " + DOM_ID + ", min(o." + positionField + ") as pos" +
                " from " + TBL_BOOKS + " b join " + tableName + " o On o." + DOM_BOOK_ID + " = b." + DOM_ID +
                " Group by b." + DOM_ID;

        SynchronizedStatement moveStmt = null;
        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            // Get the column indexes we need
            final int bookCol = c.getColumnIndexOrThrow(DOM_ID.name);
            final int posCol = c.getColumnIndexOrThrow("pos");
            // Loop through all instances of the old author appearing
            while (c.moveToNext()) {
                // Get the details
                long pos = c.getLong(posCol);
                if (pos > 1) {
                    if (moveStmt == null) {
                        // Statement to move records up by a given offset
                        sql = "Update " + tableName + " Set " + positionField + " = 1 Where " + DOM_BOOK_ID + " = ? and " + positionField + " = ?";
                        moveStmt = mSyncedDb.compileStatement(sql);
                    }

                    long book = c.getLong(bookCol);
                    // Move subsequent records up by one
                    moveStmt.bindLong(1, book);
                    moveStmt.bindLong(2, pos);
                    moveStmt.execute();
                }
            }
        } finally {
            if (moveStmt != null)
                moveStmt.close();
        }
    }
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Debug internals">

    private static final ArrayList<InstanceRef> mInstances = new ArrayList<>();

    /** Debug counter */
    private static Integer mInstanceCount = 0;

    private static class InstanceRef extends WeakReference<CatalogueDBAdapter> {
        private final Exception mCreationException;

        InstanceRef(@NonNull final CatalogueDBAdapter db) {
            super(db);
            mCreationException = new RuntimeException();
        }
        @NonNull
        Exception getCreationException() {
            return mCreationException;
        }
    }

    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    private static void addInstance(@NonNull final CatalogueDBAdapter db) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            mInstances.add(new InstanceRef(db));
        }
    }

    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    private static void removeInstance(@NonNull final CatalogueDBAdapter db) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            ArrayList<InstanceRef> toDelete = new ArrayList<>();
            for (InstanceRef ref : mInstances) {
                CatalogueDBAdapter refDb = ref.get();
                if (refDb == null) {
                    System.out.println("<-- **** Missing ref (not closed?) **** vvvvvvv");
                    Logger.logError(ref.getCreationException());
                    System.out.println("--> **** Missing ref (not closed?) **** ^^^^^^^");
                } else {
                    if (refDb == db) {
                        toDelete.add(ref);
                    }
                }
            }
            for (InstanceRef ref : toDelete) {
                mInstances.remove(ref);
            }
        }
    }

    /**
     * DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
     * there are still non-fatal anomalies.
     */
    @SuppressWarnings("unused")
    public static void printReferenceCount(@Nullable final String msg) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            if (mSyncedDb != null) {
                SynchronizedDb.printRefCount(msg, mSyncedDb.getUnderlyingDatabase());
            }
        }
    }

    @SuppressWarnings("unused")
    public static void dumpInstances() {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            for (InstanceRef ref : mInstances) {
                if (ref.get() == null) {
                    System.out.println("<-- **** Missing ref (not closed?) **** vvvvvvv");
                    Logger.logError(ref.getCreationException());
                    System.out.println("--> **** Missing ref (not closed?) **** ^^^^^^^");
                } else {
                    Logger.logError(ref.getCreationException());
                }
            }
        }
    }

    //</editor-fold>
}


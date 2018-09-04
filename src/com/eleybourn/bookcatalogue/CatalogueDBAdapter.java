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
package com.eleybourn.bookcatalogue;

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

import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.database.TrackedCursor;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper;
import com.eleybourn.bookcatalogue.database.dbaadapter.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_DOCID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ISBN;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_NOTES;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_STYLE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_LIST_STYLES;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_BOOK;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_FORMAT;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_GENRE;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_ISBN;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_LOCATION;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_NOTES;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_PAGES;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_POSITION;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_RATING;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_READ;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_READ_END;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_READ_START;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_ROWID;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_SIGNED;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.KEY_TITLE;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.COLLATION;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_AUTHORS;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_BOOKS;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_BOOK_BOOKSHELF_WEAK;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_LOAN;
import static com.eleybourn.bookcatalogue.database.dbaadapter.DatabaseHelper.DB_TB_SERIES;

/**
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the 
 * ability to list all books as well as retrieve or modify a specific book.
 *
 * NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES. Use the UpgradeMessageManager class
 * This change separated messages from DB changes (most releases do not involve DB upgrades).
 * 
 * ENHANCE: Use date_added to add 'Recent Acquisitions' virtual shelf; need to resolve how this may relate to date_purchased and 'I own this book'...
 * 
 */
public class CatalogueDBAdapter {

    /**
     *  Used as: if (DEBUG && BuildConfig.DEBUG) { ... }
     */
    private static final boolean DEBUG = false;

	/** Debug counter */
	private static Integer mInstanceCount = 0;

	private final Context mContext;

	private SqlStatementManager mStatements;

	/** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync */
	private static final Synchronizer mSynchronizer = new Synchronizer();

	/** Convenience to avoid writing "String[] {}" in many DB routines */
	public static final String[]  EMPTY_STRING_ARRAY = new String[]{};
	private static final String[] EMPTY_STRNG_ARRAY  = new String[]{}; //FIXME: surely this is a typo ?

	private static DatabaseHelper mDbHelper;
	private static SynchronizedDb mDb;

    /** Flag indicating close() has been called */
	private boolean mCloseWasCalled = false;

	private static final String META_EMPTY_SERIES = "<Empty Series>";
	private static final String META_EMPTY_GENRE = "<Empty Genre>";
	private static final String META_EMPTY_DATE_PUBLISHED = "<No Valid Published Date>";

//	private static String AUTHOR_FIELDS = "a." + KEY_ROWID + " as " + KEY_AUTHOR_NAME + ", a." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME 
//						+ ", a." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES 
//						+ ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_FORMATTED;

	private static String getAuthorFields(String alias, String idName) {
			String sql;
			if (idName != null && !idName.isEmpty()) {
				sql = " " + alias + "." + KEY_ROWID + " as " + idName + ", ";
			} else {
				sql = " ";
			}
			sql += alias + "." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME 
				+ ", " + alias + "." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES 
				+ ",  Case When " + alias + "." + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME 
				+ "        Else " + alias + "." + KEY_FAMILY_NAME + " || ', ' || " 
						+ alias + "." + KEY_GIVEN_NAMES + " End as " + KEY_AUTHOR_FORMATTED 
				+ ",  Case When " + alias + "." + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME 
				+ "        Else " + alias + "." + KEY_GIVEN_NAMES + " || ' ' || " 
						+ alias + "." + KEY_FAMILY_NAME + " End as " + KEY_AUTHOR_FORMATTED_GIVEN_FIRST + " ";
			return sql;
		}

	//		private static String BOOK_FIELDS =
//		"b." + KEY_TITLE + " as " + KEY_TITLE + ", " +
//		"b." + KEY_ISBN + " as " + KEY_ISBN + ", " +
//		"b." + KEY_PUBLISHER + " as " + KEY_PUBLISHER + ", " +
//		"b." + KEY_DATE_PUBLISHED + " as " + KEY_DATE_PUBLISHED + ", " +
//		"b." + KEY_RATING + " as " + KEY_RATING + ", " +
//		"b." + KEY_READ + " as " + KEY_READ + ", " +
//		"b." + KEY_PAGES + " as " + KEY_PAGES + ", " +
//		"b." + KEY_NOTES + " as " + KEY_NOTES + ", " +
//		"b." + KEY_LIST_PRICE + " as " + KEY_LIST_PRICE + ", " +
//		"b." + KEY_ANTHOLOGY + " as " + KEY_ANTHOLOGY + ", " +
//		"b." + KEY_LOCATION + " as " + KEY_LOCATION + ", " +
//		"b." + KEY_READ_START + " as " + KEY_READ_START + ", " +
//		"b." + KEY_READ_END + " as " + KEY_READ_END + ", " +
//		"b." + KEY_FORMAT + " as " + KEY_FORMAT + ", " +
//		"b." + KEY_SIGNED + " as " + KEY_SIGNED + ", " + 
//		"b." + KEY_DESCRIPTION + " as " + KEY_DESCRIPTION + ", " + 
//		"b." + KEY_GENRE  + " as " + KEY_GENRE;

	private static String getBookFields(String alias, String idName) {
			String sql;
			if (idName != null && !idName.isEmpty()) {
				sql = alias + "." + KEY_ROWID + " as " + idName + ", ";
			} else {
				sql = "";
			}
			return sql + alias + "." + KEY_TITLE + " as " + KEY_TITLE + ", " +

			// Find FIRST series ID. 
			"(Select " + KEY_SERIES_ID + " From " + DB_TB_BOOK_SERIES + " bs " +
			" where bs." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + " Order by " + KEY_SERIES_POSITION + " asc  Limit 1) as " + KEY_SERIES_ID + ", " +
			// Find FIRST series NUM. 
			"(Select " + KEY_SERIES_NUM + " From " + DB_TB_BOOK_SERIES + " bs " +
			" where bs." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + " Order by " + KEY_SERIES_POSITION + " asc  Limit 1) as " + KEY_SERIES_NUM + ", " +
			// Get the total series count
			"(Select Count(*) from " + DB_TB_BOOK_SERIES + " bs Where bs." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + ") as _num_series," + 
			// Find the first AUTHOR ID
			"(Select " + KEY_AUTHOR_ID + " From " + DB_TB_BOOK_AUTHOR + " ba " + 
			"   where ba." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + 
			"   order by " + KEY_AUTHOR_POSITION + ", ba." + KEY_AUTHOR_ID + " Limit 1) as " + KEY_AUTHOR_ID + ", " +
			// Get the total author count
			"(Select Count(*) from " + DB_TB_BOOK_AUTHOR + " ba Where ba." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + ") as _num_authors," + 

			alias + "." + KEY_ISBN + " as " + KEY_ISBN + ", " +
			alias + "." + KEY_PUBLISHER + " as " + KEY_PUBLISHER + ", " +
			alias + "." + KEY_DATE_PUBLISHED + " as " + KEY_DATE_PUBLISHED + ", " +
			alias + "." + KEY_RATING + " as " + KEY_RATING + ", " +
			alias + "." + KEY_READ + " as " + KEY_READ + ", " +
			alias + "." + KEY_PAGES + " as " + KEY_PAGES + ", " +
			alias + "." + KEY_NOTES + " as " + KEY_NOTES + ", " +
			alias + "." + KEY_LIST_PRICE + " as " + KEY_LIST_PRICE + ", " +
			alias + "." + KEY_ANTHOLOGY_MASK + " as " + KEY_ANTHOLOGY_MASK + ", " +
			alias + "." + KEY_LOCATION + " as " + KEY_LOCATION + ", " +
			alias + "." + KEY_READ_START + " as " + KEY_READ_START + ", " +
			alias + "." + KEY_READ_END + " as " + KEY_READ_END + ", " +
			alias + "." + KEY_FORMAT + " as " + KEY_FORMAT + ", " +
			alias + "." + KEY_SIGNED + " as " + KEY_SIGNED + ", " + 
			alias + "." + KEY_DESCRIPTION + " as " + KEY_DESCRIPTION + ", " + 
			alias + "." + KEY_GENRE  + " as " + KEY_GENRE + ", " +
			alias + "." + DOM_LANGUAGE  + " as " + DOM_LANGUAGE + ", " +
			alias + "." + KEY_DATE_ADDED  + " as " + KEY_DATE_ADDED + ", " +
			alias + "." + DOM_GOODREADS_BOOK_ID  + " as " + DOM_GOODREADS_BOOK_ID + ", " +
			alias + "." + DOM_LAST_GOODREADS_SYNC_DATE  + " as " + DOM_LAST_GOODREADS_SYNC_DATE + ", " +
			alias + "." + DOM_LAST_UPDATE_DATE  + " as " + DOM_LAST_UPDATE_DATE + ", " +
			alias + "." + DOM_BOOK_UUID  + " as " + DOM_BOOK_UUID;
		}

//	private static String SERIES_FIELDS = "s." + KEY_ROWID + " as " + KEY_SERIES_ID
//		+ " CASE WHEN s." + KEY_SERIES_NAME + "='' THEN '' ELSE s." + KEY_SERIES_NAME + " || CASE WHEN s." + KEY_SERIES_NUM + "='' THEN '' ELSE ' #' || s." + KEY_SERIES_NUM + " END END AS " + KEY_SERIES_FORMATTED;
//
//	private static String BOOKSHELF_TABLES = DB_TB_BOOKS + " b LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w ON (b." + KEY_ROWID + "=w." + KEY_BOOK + ") LEFT OUTER JOIN " + DB_TB_BOOKSHELF + " bs ON (bs." + KEY_ROWID + "=w." + KEY_BOOKSHELF + ") ";
//	private static String SERIES_TABLES = DB_TB_BOOKS 
//						+ " b LEFT OUTER JOIN " + DB_TB_BOOK_SERIES + " w "
//						+ " ON (b." + KEY_ROWID + "=w." + KEY_BOOK + ") "
//						+ " LEFT OUTER JOIN " + DB_TB_SERIES + " s ON (s." + KEY_ROWID + "=w." + KEY_SERIES_ID + ") ";


	private TableInfo mBooksInfo = null;

	/** Static Factory object to create the custom cursor */
	private static final CursorFactory mTrackedCursorFactory = new CursorFactory() {
			@Override
			public Cursor newCursor(
					SQLiteDatabase db,
					SQLiteCursorDriver masterQuery, 
					String editTable,
					SQLiteQuery query) 
			{
				return new TrackedCursor(masterQuery, editTable, query, mSynchronizer);
			}
	};

	private static class InstanceRef extends WeakReference<CatalogueDBAdapter> {
		private final Exception mCreationException;
		InstanceRef(CatalogueDBAdapter r) {
			super(r);
			mCreationException = new RuntimeException();
		}
		Exception getCreationException() {
			return mCreationException;
		}		
	}

	private static final ArrayList< InstanceRef > mInstances = new ArrayList<>();

    /**
     * DEBUG only
     */
	private static void addInstance(CatalogueDBAdapter db) {
		mInstances.add(new InstanceRef(db));
	}

    /**
     * DEBUG only
     */
	private static void removeInstance(CatalogueDBAdapter db) {
		ArrayList< InstanceRef > toDelete = new ArrayList<>();
		for( InstanceRef ref: mInstances) {
			CatalogueDBAdapter refDb = ref.get();
			if (refDb == null) {
				System.out.println("<-- **** Missing ref (not closed?) **** vvvvvvv");
				ref.getCreationException().printStackTrace();
				System.out.println("--> **** Missing ref (not closed?) **** ^^^^^^^");
			} else {
				if (refDb == db) {
					toDelete.add(ref);
				}
			}
		}
		for( WeakReference<CatalogueDBAdapter> ref: toDelete) {
			mInstances.remove(ref);
		}
	}
	public static void dumpInstances() {
		for( InstanceRef ref: mInstances) {
			CatalogueDBAdapter db = ref.get();
			if (db == null) {
				System.out.println("<-- **** Missing ref (not closed?) **** vvvvvvv");
				ref.getCreationException().printStackTrace();
				System.out.println("--> **** Missing ref (not closed?) **** ^^^^^^^");
			} else {
				ref.getCreationException().printStackTrace();
			}
		}
	}
	
	
	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param ctx the Context within which to work
	 */
	public CatalogueDBAdapter(Context ctx) {
		if (DEBUG && BuildConfig.DEBUG) {
			synchronized(mInstanceCount) {
				mInstanceCount++;
				System.out.println("CatDBA instances: " + mInstanceCount);
				//addInstance(this); FIXME this crashes ? ...
			}
		}

        mContext = ctx;

        if (mDbHelper == null)
			mDbHelper = new DatabaseHelper(mContext, mTrackedCursorFactory, mSynchronizer);
	}
	
	/**
	 * Open the books database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an initialisation call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public CatalogueDBAdapter open() throws SQLException {

		if (mDb == null) {
			// Get the DB wrapper
			mDb = new SynchronizedDb(mDbHelper, mSynchronizer);
			// Turn on foreign key support so that CASCADE works.
			mDb.execSQL("PRAGMA foreign_keys = ON");
			// Turn on recursive triggers; not strictly necessary
			mDb.execSQL("PRAGMA recursive_triggers = ON");
		}
		//mDb.execSQL("PRAGMA temp_store = FILE");
		mStatements = new SqlStatementManager(mDb);

		return this;
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

			if (DEBUG && BuildConfig.DEBUG) {
				synchronized(mInstanceCount) {
					mInstanceCount--;
					System.out.println("CatDBA instances: " + mInstanceCount);
					//removeInstance(this); FIXME: something wrong with the addInstance
				}
			}
		}
	}
	
	private String authorFormattedSource(String alias) {
		if (alias == null)
			alias = "";
		else if (!alias.isEmpty())
			alias += ".";

		return alias + KEY_FAMILY_NAME + "||', '||" + KEY_GIVEN_NAMES;
		//return alias + KEY_GIVEN_NAMES + "||' '||" + KEY_FAMILY_NAME;
	}
	
	/**
	 * Backup database file using default file name
	 */
	public void backupDbFile() {
		backupDbFile("DbExport.db");
	}
	
	/**
	 * Backup database file to the specified filename
	 */
	public void backupDbFile(String suffix) {
		try {
			StorageUtils.backupDbFile(mDb.getUnderlyingDatabase(), suffix);
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

    /**
	 * This will return the parsed author name based on a String. 
	 * The name can be in either "family, given" or "given family" format.
	 *
	 * @param name a String containing the name e.g. "Isaac Asimov" or "Asimov, Isaac"
	 * @return a String array containing the family and given names. e.g. ['Asimov', 'Isaac']
	 */
	static public String[] processAuthorName(String name) {
		String[] author = {"", ""};
		String family = "";
		StringBuilder given = new StringBuilder();
		String names[];
		int commaIndex = name.indexOf(",");
		if (commaIndex > 0) {
			family = name.substring(0, commaIndex);
			given.append(name.substring(commaIndex+1));
		} else {
			names = name.split(" ");
			int flen = 1;
			if (names.length > 2) {
				String sname = names[names.length-2];
				/* e.g. Ursula Le Guin or Marianne De Pierres */
				if (sname.matches("[LlDd]e")) {
					family = names[names.length-2] + " ";
					flen = 2;
				}
				sname = names[names.length-1];
				/* e.g. Foo Bar Jr*/
				if (sname.matches("[Jj]r|[Jj]unior|[Ss]r|[Ss]enior")) {
					family = names[names.length-2] + " ";
					flen = 2;
				}
			}
			family += names[names.length-1];
			for (int i=0; i<names.length-flen; i++) {
				given.append(names[i]).append(" ");
			}
		}
		author[0] = family.trim();
		author[1] = given.toString().trim();
		return author;
	}
	
	/**
	 * A helper function to get a single int value (from the first row) from a cursor
	 * 
	 * @param results The Cursor the extract from
	 * @param index The index, or column, to extract from
	 */
	private int getIntValue(Cursor results, int index) {
		int value = 0;
		try {
			if (results != null) {
				results.moveToFirst();
				value = results.getInt(index);
			}
		} catch (CursorIndexOutOfBoundsException e) {
			value = 0;
		}
		return value;
		
	}
	
	/**
	 * A helper function to get a single string value (from the first row) from a cursor
	 * 
	 * @param results The Cursor the extract from
	 * @param index The index, or column, to extract from
	 */
	private String getStringValue(Cursor results, int index) {
		String value = null;
		try {
			if (results != null) {
				results.moveToFirst();
				value = results.getString(index);
			}
		} catch (CursorIndexOutOfBoundsException e) {
			value = null;
		}
		return value;
		
	}

	
	/**
	 * Return the number of books
	 * 
	 * @return int The number of books
	 */
	public int countBooks() {
		int result = 0;
		try (Cursor count = mDb.rawQuery("SELECT count(*) as count FROM " + DB_TB_BOOKS + " b ", new String[]{})){
			count.moveToNext();
			result = count.getInt(0);
		} catch (IllegalStateException e) {
			Logger.logError(e);
		}
		return result;
	}
	
	/**
	 * Return the number of books
	 * 
	 * @param bookshelf     the bookshelf the search within
	 * @return              The number of books
	 */
	public int countBooks(String bookshelf) {
		int result = 0;
		try {
			if (bookshelf.isEmpty()) {
				return countBooks();
			}
			String sql = "SELECT count(DISTINCT b._id) as count " + 
				" FROM " + DB_TB_BOOKSHELF + " bs " +
				" Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs " +
				"     On bbs." + KEY_BOOKSHELF + " = bs." + KEY_ROWID +
				" Join " + DB_TB_BOOKS + " b " +
				"     On bbs." + KEY_BOOK + " = b." + KEY_ROWID + 
				" WHERE " + makeTextTerm("bs." + KEY_BOOKSHELF, "=", bookshelf);
			try (Cursor count = mDb.rawQuery(sql, new String[]{})) {
				count.moveToNext();
				result = count.getInt(0);
			}
		} catch (IllegalStateException e) {
			Logger.logError(e);
		}
		return result;
	}
	
	/**
	 * A complete export of all tables (flattened) in the database 
	 * 
	 * @return BooksCursor over all books, authors, etc
	 */
	public BooksCursor exportBooks(Date sinceDate) {
		String sinceClause;
		if (sinceDate == null) {
			sinceClause = ""; 
		} else {
			sinceClause = " Where b." + DOM_LAST_UPDATE_DATE + " > '" + DateUtils.toSqlDateTime(sinceDate) + "' ";
		}

		String sql = "SELECT DISTINCT " +
				getBookFields("b",KEY_ROWID) + ", " +
				"l." + KEY_LOANED_TO + " as " + KEY_LOANED_TO + " " +  
			" FROM " + DB_TB_BOOKS + " b" +
				" LEFT OUTER JOIN " + DB_TB_LOAN +" l ON (l." + KEY_BOOK + "=b." + KEY_ROWID + ") " +
			sinceClause + 
			" ORDER BY b._id";
		return fetchBooks(sql, EMPTY_STRING_ARRAY);
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors() {
		return fetchAllAuthors(true, false);
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 *
	 * @param sortByFamily		flag
	 * @param firstOnly			flag
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(boolean sortByFamily, boolean firstOnly) {
		String order;
		if (sortByFamily) {
			order = " ORDER BY Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION;
		} else {
			order = " ORDER BY Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + KEY_FAMILY_NAME + ") " + COLLATION;
		}
		
		String sql = "SELECT DISTINCT " + getAuthorFields("a", KEY_ROWID) + 
			" FROM " + DB_TB_AUTHORS + " a, " + DB_TB_BOOK_AUTHOR + " ab " + 
			" WHERE a." + KEY_ROWID + "=ab." + KEY_AUTHOR_ID + " ";
		if (firstOnly) {
			sql += " AND ab." + KEY_AUTHOR_POSITION + "=1 ";
		}
		sql += order;
		//FIXME cleanup .. there are more similar code snippets
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
			Logger.logError(e);
		} catch (Exception e) {
			Logger.logError(e, "fetchAllAuthors catchall");
		}
		return returnable;
	}

	/**
	 * @return a complete list of author names from the database; used for AutoComplete.
	 */
	protected ArrayList<String> getAllAuthors() {
		ArrayList<String> author_list = new ArrayList<>();
		try(Cursor author_cur = fetchAllAuthorsIgnoreBooks())  {
			while (author_cur.moveToNext()) {
				String name = author_cur.getString(author_cur.getColumnIndexOrThrow(KEY_AUTHOR_FORMATTED));
				author_list.add(name);
			}
			return author_list;			
		}
	}

	private String authorOnBookshelfSql(String bookshelf, String authorIdSpec, boolean first) {
		String sql = " Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba"
		+ "               JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs"
		+ "                  ON bbs." + KEY_BOOK + " = ba." + KEY_BOOK
		+ "               JOIN " + DB_TB_BOOKSHELF + " bs"
		+ "                  ON bs." + KEY_ROWID + " = bbs." + KEY_BOOKSHELF
		+ "               WHERE ba." + KEY_AUTHOR_ID + " = " + authorIdSpec
		+ "               	AND " + makeTextTerm("bs." + KEY_BOOKSHELF, "=", bookshelf);
		if (first) {
			sql += "AND ba." + KEY_AUTHOR_POSITION + "=1";
		}
		sql += "              )";
		return sql;
		
	}

	/**
	 * Return a Cursor over the list of all authors in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(String bookshelf) {
		return fetchAllAuthors(bookshelf, true, false);
	}

	/**
	 * Return a Cursor over the list of all authors in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(String bookshelf, boolean sortByFamily, boolean firstOnly) {
		if (bookshelf.isEmpty()) {
			return fetchAllAuthors(sortByFamily, firstOnly);
		}
		String order;
		if (sortByFamily) {
			order = " ORDER BY Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION;
		} else {
			order = " ORDER BY Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + KEY_FAMILY_NAME + ") " + COLLATION;
		}
		
		String sql = "SELECT " + getAuthorFields("a", KEY_ROWID)
		+ " FROM " + DB_TB_AUTHORS + " a "
		+ " WHERE " + authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID, firstOnly)
		+ order;
		
		Cursor returnable;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
			Logger.logError(e);
		}
		return returnable;
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * Deprecated: Since authors only exist if they have a book in the database, 
	 * the book check is no longer made.
	 * 
	 * @return Cursor over all notes
	 */
	private Cursor fetchAllAuthorsIgnoreBooks() {
		return fetchAllAuthors();
	}
	
	/**
	 * Return a list of all the first characters for book titles in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public Cursor fetchAllBookChars(String bookshelf) {
		String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", "", "", "");
		String sql = "SELECT DISTINCT upper(substr(b." + KEY_TITLE + ", 1, 1)) AS " + KEY_ROWID + " " + baseSql;

		Cursor returnable;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
			Logger.logError(e);
		}
		returnable.moveToFirst();
		return returnable;
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
	public String fetchAllBooksInnerSql(String order, String bookshelf, String authorWhere, String bookWhere, String searchText, String loaned_to, String seriesName) {
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
			+ "            Join " + DB_TB_AUTHORS + " a On a." + KEY_ROWID + " = ba." + KEY_AUTHOR_ID
			+ "           Where " + authorSearchPredicate(searchText) + " and ba." + KEY_BOOK + " = b." + KEY_ROWID + ")"
			+ ")";
			// This is done in bookSearchPredicate().
			//+ " OR Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs"
			//+ "            Join " + DB_TB_SERIES + " s On s." + KEY_ROWID + " = bs." + KEY_SERIES_ID
			//+ "           Where s." + KEY_SERIES_NAME + " Like '%" + searchText + "' and bs." + KEY_BOOK + " = b." + KEY_ROWID + ")"
			//+ ")";
		}

		if (!authorWhere.isEmpty()) {
			if (!where.isEmpty())
				where += " and";
			where += " Exists(Select NULL From " + DB_TB_AUTHORS + " a "
					+ " Join " + DB_TB_BOOK_AUTHOR + " ba "
					+ "     On ba." + KEY_AUTHOR_ID + " = a." + KEY_ROWID
					+ " Where " + authorWhere + " And ba." + KEY_BOOK + " = b." + KEY_ROWID
					+ ")";
		}

		if (!loaned_to.isEmpty()) {
			if (!where.isEmpty())
				where += " and";
			where += " Exists(Select NULL From " + DB_TB_LOAN + " l Where "
					+ " l." + KEY_BOOK + "=b." + KEY_ROWID
					+ " And " + makeTextTerm("l." + KEY_LOANED_TO, "=", loaned_to) + ")";
		}

		if (!seriesName.isEmpty() && seriesName.equals(META_EMPTY_SERIES)) {
			if (!where.isEmpty())
				where += " and";
			where += " Not Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
					+ " bs." + KEY_BOOK + "=b." + KEY_ROWID + ")";
		}

		String sql = " FROM " + DB_TB_BOOKS + " b";

		if (!bookshelf.isEmpty() && !bookshelf.trim().isEmpty()) {
			// Join with specific bookshelf
			sql += " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbsx On bbsx." + KEY_BOOK + " = b." + KEY_ROWID;
			sql += " Join " + DB_TB_BOOKSHELF + " bsx On bsx." + KEY_ROWID + " = bbsx." + KEY_BOOKSHELF
					+ " and " + makeTextTerm("bsx." + KEY_BOOKSHELF, "=", bookshelf);
		}

		if (!seriesName.isEmpty() && !seriesName.equals(META_EMPTY_SERIES))
			sql += " Join " + DB_TB_BOOK_SERIES + " bs On (bs." + KEY_BOOK + " = b." + KEY_ROWID + ")"
					+ " Join " + DB_TB_SERIES + " s On (s." + KEY_ROWID + " = bs." + KEY_SERIES_ID 
					+ " and " + makeTextTerm("s." + KEY_SERIES_NAME, "=", seriesName) + ")";


		if (!where.isEmpty())
			sql += " WHERE " + where;

		// NULL order suppresses order-by
		if (order != null) {
			if (!order.isEmpty())
				// TODO Assess if ORDER is used and how
				sql += " ORDER BY " + order + "";
			else
				sql += " ORDER BY Upper(b." + KEY_TITLE + ") " + COLLATION + " ASC";
		}

		return sql;
	}

	public static String join(String[] strings) {
		if (strings == null || strings.length ==0)
			return null;
		StringBuilder s = new StringBuilder(strings[0]);
		final int len = strings.length;
		for(int i = 1; i < len; i++)
			s.append(",").append(strings[i]);
		return s.toString();
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
	public String fetchAllBooksSql(String order, String bookshelf, String authorWhere, String bookWhere, String searchText, String loaned_to, String seriesName) {
		String baseSql = this.fetchAllBooksInnerSql("", bookshelf, authorWhere, bookWhere, searchText, loaned_to, seriesName);

		// Get the basic query; we will use it as a sub-query
		String sql = "SELECT DISTINCT " + getBookFields("b", KEY_ROWID) + baseSql;
		String fullSql = "Select b.*, " + getAuthorFields("a", "") + ", " +  
		"a." + KEY_AUTHOR_ID + ", " +
		"Coalesce(s." + KEY_SERIES_ID + ", 0) as " + KEY_SERIES_ID + ", " +
		"Coalesce(s." + KEY_SERIES_NAME + ", '') as " + KEY_SERIES_NAME + ", " +
		"Coalesce(s." + KEY_SERIES_NUM + ", '') as " + KEY_SERIES_NUM + ", " +
		" Case When _num_series < 2 Then Coalesce(s." + KEY_SERIES_FORMATTED + ", '')" +
		" Else " + KEY_SERIES_FORMATTED + "||' et. al.' End as " + KEY_SERIES_FORMATTED + " " +
		" from (" + sql + ") b";

		// Get the 'default' author...defined in getBookFields()
		fullSql += " Join (Select " 
			+ KEY_AUTHOR_ID + ", " 
			+ KEY_FAMILY_NAME + ", " 
			+ KEY_GIVEN_NAMES + ", " 
			+ "ba." + KEY_BOOK + " as " + KEY_BOOK + ", "
			+ " Case When " + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME
			+ " Else " + authorFormattedSource("") + " End as " + KEY_AUTHOR_FORMATTED
			+ " From " + DB_TB_BOOK_AUTHOR + " ba Join " + DB_TB_AUTHORS + " a"
			+ "    On ba." + KEY_AUTHOR_ID + " = a." + KEY_ROWID + ") a "
			+ " On a." + KEY_BOOK + " = b." + KEY_ROWID + " and a." + KEY_AUTHOR_ID + " = b." + KEY_AUTHOR_ID;

		if (!seriesName.isEmpty() && !seriesName.equals(META_EMPTY_SERIES)) {
			// Get the specified series...
			fullSql += " Left Outer Join (Select " 
				+ KEY_SERIES_ID + ", " 
				+ KEY_SERIES_NAME + ", " 
				+ KEY_SERIES_NUM  + ", "
				+ "bs." + KEY_BOOK + " as " + KEY_BOOK + ", "
				+ " Case When " + KEY_SERIES_NUM + " = '' Then " + KEY_SERIES_NAME 
				+ " Else " + KEY_SERIES_NAME + "||' #'||" + KEY_SERIES_NUM + " End as " + KEY_SERIES_FORMATTED
				+ " From " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
				+ "    On bs." + KEY_SERIES_ID + " = s." + KEY_ROWID + ") s "
				+ " On s." + KEY_BOOK + " = b." + KEY_ROWID 
				+ " and " + makeTextTerm("s." + KEY_SERIES_NAME, "=", seriesName); 
				//+ " and " + this.makeEqualFieldsTerm("s." + KEY_SERIES_NUM, "b." + KEY_SERIES_NUM);
		} else {
			// Get the 'default' series...defined in getBookFields()
			fullSql += " Left Outer Join (Select " 
				+ KEY_SERIES_ID + ", " 
				+ KEY_SERIES_NAME + ", " 
				+ KEY_SERIES_NUM  + ", "
				+ "bs." + KEY_BOOK + " as " + KEY_BOOK + ", "
				+ " Case When " + KEY_SERIES_NUM + " = '' Then " + KEY_SERIES_NAME 
				+ " Else " + KEY_SERIES_NAME + "||' #'||" + KEY_SERIES_NUM + " End as " + KEY_SERIES_FORMATTED
				+ " From " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
				+ "    On bs." + KEY_SERIES_ID + " = s." + KEY_ROWID + ") s "
				+ " On s." + KEY_BOOK + " = b." + KEY_ROWID 
				+ " and s." + KEY_SERIES_ID + " = b." + KEY_SERIES_ID
				+ " and " + this.makeEqualFieldsTerm("s." + KEY_SERIES_NUM, "b." + KEY_SERIES_NUM);
		}
		if (!order.isEmpty()) {
			fullSql += " ORDER BY " + order;
		}
		return fullSql;
	}

	/**
	 * Return a list of all books in the database
	 * 
	 * @param order What order to return the books
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public BooksCursor fetchAllBooks(String order, String bookshelf, String authorWhere, String bookWhere, String searchText, String loaned_to, String seriesName) {
		// Get the SQL
		String fullSql = fetchAllBooksSql( order, bookshelf, authorWhere, bookWhere, searchText, loaned_to, seriesName);

		// Build and return a cursor.
		BooksCursor returnable;
		try {
			returnable = fetchBooks(fullSql, EMPTY_STRING_ARRAY);
		} catch (IllegalStateException e) {
			open();
			returnable = fetchBooks(fullSql, EMPTY_STRING_ARRAY);
			Logger.logError(e);
		}
		return returnable;
	}

	/**
	 * Return a list of all books in the database by author
	 * 
	 * @param author The author to search for
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public BooksCursor fetchAllBooksByAuthor(int author, String bookshelf, String search_term, boolean firstOnly) {
		String where = " a._id=" + author;
		if (firstOnly) {
			where += " AND ba." + KEY_AUTHOR_POSITION + "=1 ";
		}
		String order = "s." + KEY_SERIES_NAME + ", substr('0000000000' || s." + KEY_SERIES_NUM + ", -10, 10), lower(b." + KEY_TITLE + ") ASC";
		return fetchAllBooks(order, bookshelf, where, "", search_term, "", "");
	}
	
	/**
	 * This will return a list of all books by a given first title character
	 * 
	 * @param first_char The first title character
	 * @return Cursor over all books
	 */
	public BooksCursor fetchAllBooksByChar(String first_char, String bookshelf, String search_term) {
		String where = " " + makeTextTerm("substr(b." + KEY_TITLE + ",1,1)", "=", first_char);
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
	public BooksCursor fetchAllBooksByDatePublished(String date, String bookshelf, String search_term) {
		String where;
		if (date == null) {
			date = META_EMPTY_DATE_PUBLISHED;
		}
		if (date.equals(META_EMPTY_DATE_PUBLISHED)) {
			where = "(b." + KEY_DATE_PUBLISHED + "='' OR b." + KEY_DATE_PUBLISHED + " IS NULL or cast(strftime('%Y', b." + KEY_DATE_PUBLISHED + ") as int)<0 or cast(strftime('%Y', b." + KEY_DATE_PUBLISHED + ") as int) is null)";
		} else {
			where = makeTextTerm("strftime('%Y', b." + KEY_DATE_PUBLISHED + ")", "=", date);
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
	public BooksCursor fetchAllBooksByGenre(String genre, String bookshelf, String search_term) {
		String where;
		if (genre.equals(META_EMPTY_GENRE)) {
			where = "(b." + KEY_GENRE + "='' OR b." + KEY_GENRE + " IS NULL)";
		} else {
			where = makeTextTerm("b." + KEY_GENRE, "=", genre);
		}
		return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
	}
	
	/**
	 * This will return a list of all books loaned to a given person
	 * 
	 * @param loaned_to The person who had books loaned to
	 * @return Cursor over all books
	 */
	public BooksCursor fetchAllBooksByLoan(String loaned_to, String search_term) {
		return fetchAllBooks("", "", "", "", search_term, loaned_to, "");
	}
	
	/**
	 * This will return a list of all books either read or unread
	 * 
	 * @param read "Read" or "Unread"
	 * @return Cursor over all books
	 */
	public BooksCursor fetchAllBooksByRead(String read, String bookshelf, String search_term) {
		String where = "";
		if (read.equals("Read")) {
			where += " b." + KEY_READ + "=1";
		} else {
			where += " b." + KEY_READ + "!=1";
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
	public BooksCursor fetchAllBooksBySeries(String series, String bookshelf, String search_term) {
		if (series.isEmpty() || series.equals(META_EMPTY_SERIES)) {
			return fetchAllBooks("", bookshelf, "", "", search_term, "", META_EMPTY_SERIES);
		} else {
			String order = "substr('0000000000' || s." + KEY_SERIES_NUM + ", -10, 10), b." + KEY_TITLE + " " + COLLATION + " ASC";
			return fetchAllBooks(order, bookshelf, "", "", search_term, "", series);
		}
	}

	/**
	 * Return a Cursor over the list of all bookshelves in the database
	 * 
	 * @return Cursor over all bookshelves
	 */
	public Cursor fetchAllBookshelves() {
		String sql = "SELECT DISTINCT bs." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				"bs." + KEY_BOOKSHELF + " as " + KEY_BOOKSHELF + ", " +
				"0 as " + KEY_BOOK + 
			" FROM " + DB_TB_BOOKSHELF + " bs" + 
			" ORDER BY Upper(bs." + KEY_BOOKSHELF + ") " + COLLATION ;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all bookshelves in the database
	 * 
	 * @param rowId the rowId of a book, which in turn adds a new field on each row as to the active state of that bookshelf for the book
	 * @return Cursor over all bookshelves
	 */
	public Cursor fetchAllBookshelves(long rowId) {
		String sql = "SELECT DISTINCT bs." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				"bs." + KEY_BOOKSHELF + " as " + KEY_BOOKSHELF + ", " +
				"CASE WHEN w." + KEY_BOOK + " IS NULL THEN 0 ELSE 1 END as " + KEY_BOOK + 
			" FROM " + DB_TB_BOOKSHELF + " bs LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w ON (w." + KEY_BOOKSHELF + "=bs." + KEY_ROWID + " AND w." + KEY_BOOK + "=" + rowId + ") " + 
			" ORDER BY Upper(bs." + KEY_BOOKSHELF + ") " + COLLATION;
		try {
			return mDb.rawQuery(sql, new String[]{});
		} catch (NullPointerException e) {
			// there is now rowId
			return fetchAllBookshelves();
		}
	}
	
	/**
	 * Return a Cursor over the list of all bookshelves in the database for the given book
	 * 
	 * @param rowId the rowId of the book
	 * @return Cursor over all bookshelves
	 */
	public Cursor fetchAllBookshelvesByBook(long rowId) {
		String sql = "SELECT DISTINCT bs." + KEY_ROWID + " as " + KEY_ROWID + ", bs." + KEY_BOOKSHELF + " as " + KEY_BOOKSHELF + 
			" FROM " + DB_TB_BOOKSHELF + " bs, " + DB_TB_BOOK_BOOKSHELF_WEAK + " w " +
			" WHERE w." + KEY_BOOKSHELF + "=bs." + KEY_ROWID + " AND w." + KEY_BOOK + "=" + rowId + " " + 
			" ORDER BY Upper(bs." + KEY_BOOKSHELF + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all date published years within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllDatePublished(String bookshelf) {
		// Null 'order' to suppress ordering
		String baseSql = fetchAllBooksInnerSql(null, bookshelf, "", "", "", "", "");

		String sql = "SELECT DISTINCT "
				+ " Case When (b." + KEY_DATE_PUBLISHED + " = '' or b." + KEY_DATE_PUBLISHED + " is NULL or cast(strftime('%Y', b." + KEY_DATE_PUBLISHED + ") as int)<0 or cast(strftime('%Y', b." + KEY_DATE_PUBLISHED + ") as int) is null) Then '" + META_EMPTY_DATE_PUBLISHED + "'"
				+ " Else strftime('%Y', b." + KEY_DATE_PUBLISHED + ") End as " + KEY_ROWID + baseSql +
		" ORDER BY strftime('%Y', b." + KEY_DATE_PUBLISHED + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all genres within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllGenres(String bookshelf) {
		// Null 'order' to suppress ordering
		String baseSql = fetchAllBooksInnerSql(null, bookshelf, "", "", "", "", "");

		String sql = "SELECT DISTINCT "
				+ " Case When (b." + KEY_GENRE + " = '' or b." + KEY_GENRE + " is NULL) Then '" + META_EMPTY_GENRE + "'"
				+ " Else b." + KEY_GENRE + " End as " + KEY_ROWID + baseSql +
		" ORDER BY Upper(b." + KEY_GENRE + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all languages within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all languages
	 */
	public Cursor fetchAllLanguages(String bookshelf) {
		// Null 'order' to suppress ordering
		String baseSql = fetchAllBooksInnerSql(null, bookshelf, "", "", "", "", "");

		String sql = "SELECT DISTINCT "
				+ " Case When (b." + DOM_LANGUAGE + " = '' or b." + DOM_LANGUAGE + " is NULL) Then ''"
				+ " Else b." + DOM_LANGUAGE + " End as " + KEY_ROWID + baseSql +
		" ORDER BY Upper(b." + DOM_LANGUAGE + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all loans
	 * 
	 * @return Cursor over all series
	 */
	public Cursor fetchAllLoans() {
		//cleanup SQL
		//String cleanup = "DELETE FROM " + DATABASE_TABLE_LOAN + " " +
		//		" WHERE " + KEY_BOOK + " NOT IN (SELECT " + KEY_ROWID + " FROM " + DATABASE_TABLE_BOOKS + ") ";
		//mDb.rawQuery(cleanup, new String[]{});
		
		//fetch books
		String sql = "SELECT DISTINCT l." + KEY_LOANED_TO + " as " + KEY_ROWID + 
		" FROM " + DB_TB_LOAN + " l " + 
		" ORDER BY Upper(l." + KEY_LOANED_TO + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all locations in the database
	 * 
	 * @return Cursor over all locations
	 */
	public Cursor fetchAllLocations() {
		String sql = "SELECT DISTINCT " + KEY_LOCATION +  
			" FROM " + DB_TB_BOOKS + "" +  
			" ORDER BY Upper(" + KEY_LOCATION + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all publishers in the database
	 * 
	 * @return Cursor over all publisher
	 */
	public Cursor fetchAllPublishers() {
		String sql = "SELECT DISTINCT " + KEY_PUBLISHER +  
			" FROM " + DB_TB_BOOKS + "" +  
			" ORDER BY Upper(" + KEY_PUBLISHER + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	
	/**
	 * This will return a list of all series within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllSeries(String bookshelf) {
		return fetchAllSeries(bookshelf, false);
	}

	private String sqlAllSeriesOnBookshelf(String bookshelf) {
		return "select distinct s." + KEY_ROWID + " as " + KEY_ROWID + ", s." + KEY_SERIES_NAME + " as " + KEY_SERIES_NAME//+ ", s." + KEY_SERIES_NAME + " as series_sort "
				 + " From " + DB_TB_SERIES + " s "
				 + " join " + DB_TB_BOOK_SERIES + " bsw "
				 + "    on bsw." + KEY_SERIES_ID + " = s." + KEY_ROWID 
				 + " join " + DB_TB_BOOKS + " b "
				 + "    on b." + KEY_ROWID + " = bsw." + KEY_BOOK
				 + " join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbw"
				 + "    on bbw." + KEY_BOOK + " = b." + KEY_ROWID 
				 + " join " + DB_TB_BOOKSHELF + " bs "
				 + "    on bs." + KEY_ROWID + " = bbw." + KEY_BOOKSHELF
				 + " where " + makeTextTerm("bs." + KEY_BOOKSHELF, "=", bookshelf);
	}
	private String sqlAllSeries() {
		return "select distinct s." + KEY_ROWID + " as " + KEY_ROWID + ", s."+ KEY_SERIES_NAME + " as " + KEY_SERIES_NAME //+ ", s." + KEY_SERIES_NAME + " as series_sort "
				 + " From " + DB_TB_SERIES + " s ";
	}
	/**
	 * This will return a list of all series within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllSeries(String bookshelf, boolean include_blank) {
		String series;
		if (bookshelf.isEmpty()) {
			series = sqlAllSeries();
		} else {
			series = sqlAllSeriesOnBookshelf(bookshelf);
		}
		// Display blank series as '<Empty Series>' BUT sort as ''. Using a UNION
		// seems to make ordering fail.
		String sql = "Select " + KEY_ROWID + ", Case When " + KEY_SERIES_NAME + " = '' Then '" + META_EMPTY_SERIES + "' Else " + KEY_SERIES_NAME + " End  as " + KEY_SERIES_NAME
					+ " From ( " + series 
					+ "       UNION Select -1 as " + KEY_ROWID + ", '' as " + KEY_SERIES_NAME
					+ "       ) s"
					+ " Order by Upper(s." + KEY_SERIES_NAME + ") " + COLLATION + " asc ";

		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list consisting of "Read" and "Unread"
	 * 
	 * @return Cursor over all the psuedo list
	 */
	public Cursor fetchAllUnreadPsuedo() {
		String sql = "SELECT 'Unread' as " + KEY_ROWID + "" +
				" UNION SELECT 'Read' as " + KEY_ROWID + "";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return all the anthology titles and authors recorded for book
	 * 
	 * @param rowId id of book to retrieve
	 * @return Cursor containing all records, if found
	 */
	public Cursor fetchAnthologyTitlesByBook(long rowId) {
		String sql = "SELECT an." + KEY_ROWID + " as " + KEY_ROWID 
				+ ", an." + KEY_TITLE + " as " + KEY_TITLE 
				+ ", an." + KEY_POSITION + " as " + KEY_POSITION 
				+ ", au." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME
				+ ", au." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES
				+ ", au." + KEY_FAMILY_NAME + " || ', ' || au." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_NAME 
				+ ", an." + KEY_BOOK + " as " + KEY_BOOK
				+ ", an." + KEY_AUTHOR_ID + " as " + KEY_AUTHOR_ID
			+ " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au "
			+ " WHERE an." + KEY_AUTHOR_ID + "=au." + KEY_ROWID + " AND an." + KEY_BOOK + "='" + rowId + "'"
			+ " ORDER BY an." + KEY_POSITION + "";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a specific anthology titles and authors recorded for book
	 * 
	 * @param rowId id of anthology to retrieve
	 * @return Cursor containing all records, if found
	 */
	public Cursor fetchAnthologyTitleById(long rowId) {
		String sql = "SELECT an." + KEY_ROWID + " as " + KEY_ROWID 
			+ ", an." + KEY_TITLE + " as " + KEY_TITLE 
			+ ", an." + KEY_POSITION + " as " + KEY_POSITION + 
			", au." + KEY_FAMILY_NAME + " || ', ' || au." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_NAME 
			+ ", an." + KEY_BOOK + " as " + KEY_BOOK
			+ ", an." + KEY_AUTHOR_ID + " as " + KEY_AUTHOR_ID
			+ " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au "
			+ " WHERE an." + KEY_AUTHOR_ID + "=au." + KEY_ROWID + " AND an." + KEY_ROWID + "='" + rowId + "'";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return the largest anthology position (usually used for adding new titles)
	 * 
	 * @param rowId id of book to retrieve
	 * @return An integer of the highest position. 0 if it is not an anthology
	 */
	public int fetchAnthologyPositionByBook(long rowId) {
		String sql = "SELECT max(" + KEY_POSITION + ") FROM " + DB_TB_ANTHOLOGY +
			" WHERE " + KEY_BOOK + "='" + rowId + "'";
		try (Cursor mCursor = mDb.rawQuery(sql, new String[]{})) {
			return getIntValue(mCursor, 0);
		}
	}
	
	/**
	 * Return the position of an author in a list of all authors (within a bookshelf)
	 *  
	 * @param name The author to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the author
	 */
	public int fetchAuthorPositionByGivenName(String name, String bookshelf) {

		String where = null;
		String[] names = processAuthorName(name);
		if (!bookshelf.isEmpty()) {
			where = authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID, false);
		}
		if (where != null && !where.isEmpty())
			where = " and " + where;

		String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a " +
			"WHERE ( " + makeTextTerm("a." + KEY_GIVEN_NAMES, "<", names[0]) +
			"OR ( " + makeTextTerm("a." + KEY_GIVEN_NAMES, "=", names[0]) + 
			"     AND " + makeTextTerm("a." + KEY_FAMILY_NAME, "<", names[1]) + ")) " + 
			where + 
			" ORDER BY Upper(a." + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(a." + KEY_FAMILY_NAME + ") " + COLLATION;
		try (Cursor results = mDb.rawQuery(sql, null)) {
			return getIntValue(results, 0);
		}
	}
	
	/**
	 * Return the position of an author in a list of all authors (within a bookshelf)
	 *  
	 * @param name The author to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the author
	 */
	public int fetchAuthorPositionByName(String name, String bookshelf) {

		String where = "";
		String[] names = processAuthorName(name);
		if (!bookshelf.isEmpty()) {
			where += authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID, false);
		}
		if (!where.isEmpty())
			where = " and " + where;

		String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a " +
			"WHERE ( " + makeTextTerm("a." + KEY_FAMILY_NAME, "<", names[0]) +
			"OR ( " + makeTextTerm("a." + KEY_FAMILY_NAME, "=", names[0]) + 
			"     AND " + makeTextTerm("a." + KEY_GIVEN_NAMES, "<", names[1]) + ")) " + 
			where + 
			" ORDER BY Upper(a." + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(a." + KEY_GIVEN_NAMES + ") " + COLLATION;
		try (Cursor results = mDb.rawQuery(sql, null)) {
			return getIntValue(results, 0);
		}
	}
	
	/**
	 * Return a book (Cursor) that matches the given rowId
	 * 
	 * @param rowId id of book to retrieve
	 * @return Cursor positioned to matching book, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	public BooksCursor fetchBookById(long rowId) throws SQLException {
		String where = "b." + KEY_ROWID + "=" + rowId;
		return fetchAllBooks("", "", "", where, "", "", "");
	}
	
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
	 * Return a book (Cursor) that matches the given ISBN.
	 * Note: MAYBE RETURN MORE THAN ONE BOOK
	 * 
	 * @param isbns ISBN of book(s) to retrieve
	 * @return Cursor positioned to matching book, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	public BooksCursor fetchBooksByIsbns(ArrayList<String> isbns) throws SQLException {
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
	
	private SynchronizedStatement mCheckBookExistsStmt = null;

	/**
	 * Check that a book with the passed ID exists
	 * 
	 * @param rowId		id of book
	 * 
	 * @return			Boolean indicating it exists
	 */
	public boolean checkBookExists(long rowId) {
		if (mCheckBookExistsStmt == null) {
			mCheckBookExistsStmt = mStatements.add("mCheckBookExistsStmt", "Select " + KEY_ROWID + " From " + DB_TB_BOOKS + " Where " + KEY_ROWID + " = ?");
		}
		mCheckBookExistsStmt.bindLong(1, rowId);
		try {
			mCheckBookExistsStmt.simpleQueryForLong();
			return true;
		} catch (SQLiteDoneException e) {
			return false;
		}
	}
	
	/**
	 * Check that a book with the passed UUID exists and return the ID of the book, or zero
	 * 
	 * @param rowId		UUID of book
	 * 
	 * @return			Boolean indicating it exists
	 */
	private SynchronizedStatement mGetBookIdFromUuidStmt = null;
	public long getBookIdFromUuid(String uuid) {
		if (mGetBookIdFromUuidStmt == null) {
			mGetBookIdFromUuidStmt = mStatements.add("mGetBookIdFromUuidStmt", "Select " + KEY_ROWID + " From " + DB_TB_BOOKS + " Where " + DOM_BOOK_UUID + " = ?");
		}
		mGetBookIdFromUuidStmt.bindString(1, uuid);
		try {
			return mGetBookIdFromUuidStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			return 0L;
		}
	}

	private SynchronizedStatement mGetIdFromIsbn1Stmt = null;
	private SynchronizedStatement mGetIdFromIsbn2Stmt = null;
	/**
	 * 
	 * @param isbn The isbn to search by
	 * @return boolean indicating ISBN already in DB
	 */
	public long getIdFromIsbn(String isbn, boolean checkAltIsbn) {
		SynchronizedStatement stmt;
		if (checkAltIsbn && IsbnUtils.isValid(isbn)) {
			if (mGetIdFromIsbn2Stmt == null) {
				mGetIdFromIsbn2Stmt = mStatements.add("mGetIdFromIsbn2Stmt", "Select Coalesce(max(" + KEY_ROWID + "), -1) From " + DB_TB_BOOKS + " Where Upper(" + KEY_ISBN + ") in (Upper(?), Upper(?))");
			}
			stmt = mGetIdFromIsbn2Stmt;
			stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
		} else {
			if (mGetIdFromIsbn1Stmt == null) {
				mGetIdFromIsbn1Stmt = mStatements.add("mGetIdFromIsbn1Stmt", "Select Coalesce(max(" + KEY_ROWID + "), -1) From " + DB_TB_BOOKS + " Where Upper(" + KEY_ISBN + ") = Upper(?)");
			}
			stmt = mGetIdFromIsbn1Stmt;
		}
		stmt.bindString(1, isbn);
		return stmt.simpleQueryForLong();
	}
	
	/**
	 * 
	 * @param isbn The isbn to search by
	 * @return boolean indicating ISBN already in DB
	 */
	public boolean checkIsbnExists(String isbn, boolean checkAltIsbn) {
		return getIdFromIsbn(isbn, checkAltIsbn) > 0;
	}
	
	/**
	 *
	 * @param family Family name of author
	 * @param given Given name of author
	 * @param title Title of book
	 * @return Cursor of the book
	 */
	public BooksCursor fetchByAuthorAndTitle(String family, String given, String title) {
		String authorWhere = makeTextTerm("a." + KEY_FAMILY_NAME, "=", family) 
							+ " AND " + makeTextTerm("a." + KEY_GIVEN_NAMES, "=", given);
		String bookWhere = makeTextTerm("b." + KEY_TITLE, "=", title);
		return fetchAllBooks("", "", authorWhere, bookWhere, "", "", "" );
	}

	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param title The book title to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchBookPositionByTitle(String title, String bookshelf) {
		String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", makeTextTerm("Substr(b." + KEY_TITLE + ",1,1)", "<", title.substring(0,1)), "", "", "");
		String sql = "SELECT Count(Distinct Upper(Substr(" + KEY_TITLE + ",1,1))" + COLLATION + ") as count " + baseSql;

		try (Cursor results = mDb.rawQuery(sql, null)) {
			return getIntValue(results, 0);
		}
	}

	private SynchronizedStatement mGetBookshelfNameStmt = null;
	/**
	 * Return a Cursor positioned at the bookshelf that matches the given rowId
	 * 
	 * @param rowId id of bookshelf to retrieve
	 * @return Name of bookshelf, if found, or throws SQLiteDoneException
	 */
	public String getBookshelfName(long rowId) throws SQLiteDoneException {
		if (mGetBookshelfNameStmt == null) {
			mGetBookshelfNameStmt = mStatements.add("mGetBookshelfNameStmt", "Select " + KEY_BOOKSHELF + " From " + DB_TB_BOOKSHELF + " Where " + KEY_ROWID + " = ?");
		}
		mGetBookshelfNameStmt.bindLong(1, rowId);
		return mGetBookshelfNameStmt.simpleQueryForString();
	}
	
//	/**
//	 * Return a Cursor positioned at the bookshelf that matches the given rowId
//	 * 
//	 * @param rowId id of bookshelf to retrieve
//	 * @return Cursor positioned to matching note, if found
//	 * @throws SQLException if note could not be found/retrieved
//	 */
//	public Cursor fetchBookshelf(long rowId) throws SQLException {
//		String sql = "SELECT bs." + KEY_ROWID + ", bs." + KEY_BOOKSHELF + 
//		" FROM " + DB_TB_BOOKSHELF + " bs " +  
//		" WHERE bs." + KEY_ROWID + "=" + rowId + "";
//		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
//		if (mCursor != null) {
//			mCursor.moveToFirst();
//		}
//		return mCursor;
//	}
//	
//	/**
//	 * This will return the bookshelf id based on the name. 
//	 * 
//	 * @param name The bookshelf name to search for
//	 * @return A cursor containing all bookshelves with the given name
//	 */
//	public Cursor fetchBookshelfByName(String name) {
//		String sql = "";
//		sql = makeTextTerm(KEY_BOOKSHELF, "=", name);
//		return mDb.query(DB_TB_BOOKSHELF, new String[] {"_id", KEY_BOOKSHELF}, sql, null, null, null, null);
//	}
	
	/**
	 * This will return JUST the bookshelf id based on the name. 
	 * The name can be in either "family, given" or "given family" format.
	 * 
	 * @param name The bookshelf name to search for
	 * @return A cursor containing all bookshelves with the given name
	 */
	private SynchronizedStatement mFetchBookshelfIdByNameStmt = null;
	public long fetchBookshelfIdByName(String name) {
		if (mFetchBookshelfIdByNameStmt == null) {
			mFetchBookshelfIdByNameStmt = mStatements.add("mFetchBookshelfIdByNameStmt", "Select " + KEY_ROWID + " From " + KEY_BOOKSHELF 
											+ " Where Upper(" + KEY_BOOKSHELF + ") = Upper(?)" + COLLATION);
		}
		mFetchBookshelfIdByNameStmt.bindString(1, name);
		long id;
		try {
			id = mFetchBookshelfIdByNameStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			id = 0L;
		}
		return id;
	}
	
	/**
	 * This will return the borrower for a given book, if any
	 * 
	 * @param mRowId The book id to search for
	 * @return Who the book is loaned to, can be blank.
	 */
	public String fetchLoanByBook(Long mRowId) {
		String sql;
		sql = KEY_BOOK + "=" + mRowId + "";
		try (Cursor results = mDb.query(DB_TB_LOAN, new String[] {KEY_BOOK, KEY_LOANED_TO}, sql, null, null, null, null)) {
			return getStringValue(results, 1);
		}
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param genre The book genre to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchGenrePositionByGenre(String genre, String bookshelf) {
		if (genre.equals(META_EMPTY_GENRE))
			return 0;

		String where = makeTextTerm("b." + KEY_GENRE, "<", genre);
		String baseSql = fetchAllBooksInnerSql("", bookshelf, "", where, "", "", "");

		String sql = "SELECT Count(DISTINCT Upper(" + KEY_GENRE + "))" + baseSql;
		try (Cursor results = mDb.rawQuery(sql, null)) {
			return  getIntValue(results, 0);
		}
	}
	
	/**
	 * 
	 * @param query The query string
	 * @return Cursor of search suggestions
	 */
	public Cursor fetchSearchSuggestions(String query) {
		String sql = "Select * From (SELECT \"BK\" || b." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", b." + KEY_TITLE + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", b." + KEY_TITLE + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_BOOKS + " b" + 
			" WHERE b." + KEY_TITLE + " LIKE '"+query+"%'" +
			" UNION " + 
			" SELECT \"AF\" || a." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", a." + KEY_FAMILY_NAME + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", a." + KEY_FAMILY_NAME + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_AUTHORS + " a" + 
			" WHERE a." + KEY_FAMILY_NAME + " LIKE '"+query+"%'" +
			" UNION " + 
			" SELECT \"AG\" || a." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", a." + KEY_GIVEN_NAMES + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", a." + KEY_GIVEN_NAMES + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_AUTHORS + " a" + 
			" WHERE a." + KEY_GIVEN_NAMES + " LIKE '"+query+"%'" +
			" UNION " + 
			" SELECT \"BK\" || b." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", b." + KEY_ISBN + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", b." + KEY_ISBN + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_BOOKS + " b" + 
			" WHERE b." + KEY_ISBN + " LIKE '"+query+"%'" +
			" ) as zzz " + 
			" ORDER BY Upper(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") " + COLLATION;
		return mDb.rawQuery(sql, null);
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param seriesName The book title to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchSeriesPositionBySeries(String seriesName, String bookshelf) {
		String seriesSql;
		if (bookshelf.isEmpty()) {
			seriesSql = sqlAllSeries();
		} else {
			seriesSql = sqlAllSeriesOnBookshelf(bookshelf);
		}
		if (seriesName.equals(META_EMPTY_SERIES))
			seriesName = "";

		// Display blank series as '<Empty Series>' BUT sort as ''. Using a UNION
		// seems to make ordering fail.
		String sql = "Select Count(Distinct " + KEY_SERIES_NAME + ") as count"
					+ " From ( " + seriesSql 
					+ "       UNION Select -1 as " + KEY_ROWID + ", '' as " +  KEY_SERIES_NAME
					+ "       ) s "
					+ " WHERE " + makeTextTerm("s." + KEY_SERIES_NAME, "<", seriesName)
					+ " Order by s." + KEY_SERIES_NAME + COLLATION + " asc ";

		try (Cursor results = mDb.rawQuery(sql, null)) {
			return getIntValue(results, 0);
		}
	}
	
	/**
	 * Return a Cursor over the author in the database which meet the provided search criteria
	 * 
	 * @param searchText The search query
	 * @param bookshelf The bookshelf to search within
	 * @return Cursor over all authors
	 */
	public Cursor searchAuthors(String searchText, String bookshelf) {
		return searchAuthors(searchText, bookshelf, true, false);
	}
	
	/**
	 * Return a Cursor over the author in the database which meet the provided search criteria
	 * 
	 * @param searchText The search query
	 * @param bookshelf The bookshelf to search within
	 * @return Cursor over all authors
	 */
	public Cursor searchAuthors(String searchText, String bookshelf, boolean sortByFamily, boolean firstOnly) {
		String where = "";
		String baWhere = "";
		searchText = encodeString(searchText);
		if (!bookshelf.isEmpty()) {
			where += " AND " + this.authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID, false);
		}
		if (firstOnly) {
			baWhere += " AND ba." + KEY_AUTHOR_POSITION + "=1 ";
		}
		//if (where != null && where.trim().length() > 0)
		//	where = " and " + where;
		
		String order;
		if (sortByFamily) {
			order = " ORDER BY Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION;
		} else {
			order = " ORDER BY Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + KEY_FAMILY_NAME + ") " + COLLATION;
		}
		
		String sql = "SELECT " + getAuthorFields("a", KEY_ROWID) +
			" FROM " + DB_TB_AUTHORS + " a" + " " +
			"WHERE (" + authorSearchPredicate(searchText) +  " OR " +
				"a." + KEY_ROWID + " IN (SELECT ba." + KEY_AUTHOR_ID + 
				" FROM " + DB_TB_BOOKS + " b Join " + DB_TB_BOOK_AUTHOR + " ba " + 
				 		" On ba." + KEY_BOOK + " = b." + KEY_ROWID + " " + baWhere + 
					"WHERE (" + bookSearchPredicate(searchText)  + ") ) )" + 
				where + order;
		return mDb.rawQuery(sql, new String[]{});
	}

	private String makeSearchTerm(String key, String text) {
		return "Upper(" + key + ") LIKE Upper('%" + text + "%') " + COLLATION;
	}

	private String makeEqualFieldsTerm(String v1, String v2) {
		return "Upper(" + v1 + ") = Upper(" + v2 + ") " + COLLATION;
	}

	private String makeTextTerm(String field, String op, String text) {
		return "Upper(" + field + ") " + op + " Upper('" + encodeString(text) + "') " + COLLATION;
	}

	private String authorSearchPredicate(String search_term) {
		return "(" + makeSearchTerm(KEY_FAMILY_NAME, search_term) + " OR " +
				makeSearchTerm(KEY_GIVEN_NAMES, search_term) + ")";
	}

	private String bookSearchPredicate(String search_term) {
		StringBuilder result = new StringBuilder("(");

		// Just do a simple search of a bunch of fields.
		String[] keys = new String[] {KEY_TITLE, KEY_ISBN, KEY_PUBLISHER, KEY_NOTES, KEY_LOCATION, KEY_DESCRIPTION};
		for(String k : keys)
			result.append(makeSearchTerm(k, search_term)).append(" OR ");

		// And check the series too.
		result.append(" Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bsw " + " Join " + DB_TB_SERIES + " s " + "     On s." + KEY_ROWID + " = bsw." + KEY_SERIES_ID + "         And ")
            .append(makeSearchTerm("s." + KEY_SERIES_NAME, search_term))
            .append(" Where bsw.")
            .append(KEY_BOOK)
            .append(" = b.")
            .append(KEY_ROWID)
            .append(") ");

		//and check the anthologies too.
		result.append(" OR Exists (SELECT NULL FROM  " + DB_TB_ANTHOLOGY + " bsan, " + DB_TB_AUTHORS + " bsau " + " WHERE bsan." + KEY_AUTHOR_ID + "= bsau." + KEY_ROWID + " AND bsan." + KEY_BOOK + " = b." + KEY_ROWID + " AND " + "(")
            .append(makeSearchTerm("bsan." + KEY_TITLE, search_term))
            .append(" OR ")
            .append(makeSearchTerm("bsau." + KEY_FAMILY_NAME, search_term))
            .append(" OR ")
            .append(makeSearchTerm("bsau." + KEY_GIVEN_NAMES, search_term))
            .append(")) ");

		result.append( ")") ;

		return result.toString();
	}

	/**
	 * Returns a list of books, similar to fetchAllBooks but restricted by a search string. The
	 * query will be applied to author, title, and series
	 * 
	 * @param searchText	The search string to restrict the output by
	 * @param first_char    The character to search for
	 * @param bookshelf		The bookshelf to search within. Can be the string "All Books"
	 *
	 * @return A Cursor of book meeting the search criteria
	 */
	public BooksCursor searchBooksByChar(String searchText, String first_char, String bookshelf) {
		String where = " " + makeTextTerm("substr(b." + KEY_TITLE + ",1,1)", "=", first_char);
		return fetchAllBooks("", bookshelf, "", where, searchText, "", "");
	}
	
	public BooksCursor searchBooksByDatePublished(String searchText, String date, String bookshelf) {
		return fetchAllBooks("", bookshelf, "", " strftime('%Y', b." + KEY_DATE_PUBLISHED + ")='" + date + "' " + COLLATION + " ", searchText, "", "");
	}
	
	public BooksCursor searchBooksByGenre(String searchText, String genre, String bookshelf) {
		return fetchAllBooks("", bookshelf, "", " " + KEY_GENRE + "='" + genre + "' " + COLLATION + " ", searchText, "", "");
	}
	
	/**
	 * Returns a list of books title characters, similar to fetchAllBookChars but restricted by a search string. The
	 * query will be applied to author, title, and series
	 * 
	 * @param searchText The search string to restrict the output by
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return A Cursor of book meeting the search criteria
	 */
	public Cursor searchBooksChars(String searchText, String bookshelf) {
		String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");
		String sql = "SELECT DISTINCT upper(substr(b." + KEY_TITLE + ", 1, 1)) " + COLLATION + " AS " + KEY_ROWID + " " + baseSql;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all date published years within the given bookshelf where the
	 * series, title or author meet the search string
	 * 
	 * @param searchText The query string to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor searchDatePublished(String searchText, String bookshelf) {
		String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");
		String sql = "SELECT DISTINCT Case When " + KEY_DATE_PUBLISHED + " = '' Then '" + META_EMPTY_DATE_PUBLISHED + "' else strftime('%Y', b." + KEY_DATE_PUBLISHED + ") End " + COLLATION + " AS " + KEY_ROWID + " " + baseSql;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all genres within the given bookshelf where the
	 * series, title or author meet the search string
	 * 
	 * @param searchText The query string to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor searchGenres(String searchText, String bookshelf) {
		String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");
		String sql = "SELECT DISTINCT Case When " + KEY_GENRE + " = '' Then '" + META_EMPTY_GENRE + "' else " + KEY_GENRE + " End " + COLLATION + " AS " + KEY_ROWID + " " + baseSql;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all series within the given bookshelf where the
	 * series, title or author meet the search string
	 * 
	 * @param searchText The query string to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor searchSeries(String searchText, String bookshelf) {
		/// Need to know when to add the 'no series' series...
		String sql;
		String baseSql = this.fetchAllBooksInnerSql("1", bookshelf, "", "", searchText, "", "");

		sql = "Select DISTINCT Case When s." + KEY_ROWID + " is NULL Then -1 Else s." + KEY_ROWID + " End as " + KEY_ROWID + ","
			+ " Case When s." + KEY_SERIES_NAME + " is NULL Then '" + META_EMPTY_SERIES + "'"
			+ "               Else " + KEY_SERIES_NAME + " End AS " + KEY_SERIES_NAME
			+ " From (Select b." + KEY_ROWID + " as " + KEY_ROWID + " " + baseSql + " ) MatchingBooks"
			+ " Left Outer Join " + DB_TB_BOOK_SERIES + " bs "
			+ "     On bs." + KEY_BOOK + " = MatchingBooks." + KEY_ROWID
			+ " Left Outer Join " + DB_TB_SERIES + " s "
			+ "     On s." + KEY_ROWID + " = bs." + KEY_SERIES_ID
			+ " Order by Upper(s." + KEY_SERIES_NAME + ") " + COLLATION + " ASC ";

		return mDb.rawQuery(sql, new String[]{});
	}
	
	public class AnthologyTitleExistsException extends RuntimeException {
		private static final long serialVersionUID = -9052087086134217566L;

		AnthologyTitleExistsException() {
			super("Anthology title already exists");
		}
	}
	/**
	 * Create an anthology title for a book.
	 * 
	 * @param book			id of book
	 * @param author		name of author
	 * @param title			title of anthology title
	 * @param returnDupId	If title already exists then if true, will return existing ID, if false, will thrown an error
	 * 
	 * @return				ID of anthology title record
	 */
	public long createAnthologyTitle(long book, String author, String title, boolean returnDupId, boolean dirtyBookIfNecessary) {
		if (!title.isEmpty()) {
			String[] names = processAuthorName(author);
			long authorId = Long.parseLong(getAuthorIdOrCreate(names));
			return createAnthologyTitle(book, authorId, title, returnDupId, dirtyBookIfNecessary);
		} else {
			return -1;
		}
	}

	/**
	 * Create an anthology title for a book.
	 * 
	 * @param book			id of book
	 * @param authorId		id of author
	 * @param title			title of anthology title
	 * @param returnDupId	If title already exists then if true, will return existing ID, if false, will thrown an error
	 * 
	 * @return				ID of anthology title record
	 */
	public long createAnthologyTitle(long book, long authorId, String title, boolean returnDupId, boolean dirtyBookIfNecessary) {
		if (!title.isEmpty()) {
			if (dirtyBookIfNecessary)
				setBookDirty(book);

			ContentValues initialValues = new ContentValues();
			int position = fetchAnthologyPositionByBook(book) + 1;

			initialValues.put(KEY_BOOK, book);
			initialValues.put(KEY_AUTHOR_ID, authorId);
			initialValues.put(KEY_TITLE, title);
			initialValues.put(KEY_POSITION, position);
			long result = getAnthologyTitleId(book, authorId, title);
			if (result < 0) {
				result = mDb.insert(DB_TB_ANTHOLOGY, null, initialValues);
			} else {
				if (!returnDupId)
					throw new AnthologyTitleExistsException();
			}
				
			return result;
		} else {
			return -1;
		}
	}

	private SynchronizedStatement mGetAnthologyTitleIdStmt = null;
	/**
	 * Return the antholgy title ID for a given book/author/title
	 * 
	 * @param bookId		id of book
	 * @param authorId		id of author
	 * @param title			title
	 * 
	 * @return				ID, or -1 if it does not exist
	 */
	private long getAnthologyTitleId(long bookId, long authorId, String title) {
		if (mGetAnthologyTitleIdStmt == null) {
			// Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
			String sql = "Select Coalesce( Min(" + KEY_ROWID + "),-1) from " + DB_TB_ANTHOLOGY + " Where " + KEY_BOOK + " = ? and " + KEY_AUTHOR_ID + " = ? and " + KEY_TITLE + " = ? " + COLLATION;
			mGetAnthologyTitleIdStmt = mStatements.add("mGetAnthologyTitleIdStmt", sql);
		}
		mGetAnthologyTitleIdStmt.bindLong(1, bookId);
		mGetAnthologyTitleIdStmt.bindLong(2, authorId);
		mGetAnthologyTitleIdStmt.bindString(3, title);
		return mGetAnthologyTitleIdStmt.simpleQueryForLong();
	}

	/**
	 * This function will create a new author in the database
	 * 
	 * @param family_name A string containing the family name
	 * @param given_names A string containing the given names
	 * @return the ID of the author
	 */
	public long createAuthor(String family_name, String given_names) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_FAMILY_NAME, family_name);
		initialValues.put(KEY_GIVEN_NAMES, given_names);
		return mDb.insert(DB_TB_AUTHORS, null, initialValues);
	}
	
	/**
	 * This function will create a new series in the database
	 * 
	 * @param seriesName 	A string containing the series name
	 * @return the ID of the new series
	 */
	public long createSeries(String seriesName) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_SERIES_NAME, seriesName);
		return mDb.insert(DB_TB_SERIES, null, initialValues);
	}
	
	/**
	 * Create a new book using the details provided. If the book is
	 * successfully created return the new rowId for that book, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param values 	A ContentValues collection with the columns to be updated. May contain extrat data.
	 * @param flags  	See BOOK_UPDATE_* flag definitions
	 *
	 * @return rowId or -1 if failed
	 */
	public long createBook(BookData values, int flags) {
		return createBook(0, values, flags);
	}
	
	/**
	 * Create a new book using the details provided. If the book is
	 * successfully created return the new rowId for that book, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id 		The ID of the book to insert (this will overwrite the normal autoIncrement)
	 * @param values 	A ContentValues collection with the columns to be updated. May contain extrat data.
	 * @param flags  	See BOOK_UPDATE_* flag definitions
	 *
	 * @return rowId or -1 if failed
	 */
	//public long createBook(long id, String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, Boolean read, String series, int pages, String series_num, String notes, String list_price, int anthology, String location, String read_start, String read_end, String format, boolean signed, String description, String genre) {
	public long createBook(long id, BookData values, int flags) {

		try {
			// Make sure we have the target table details
			if (mBooksInfo == null)
				mBooksInfo = new TableInfo(mDb, DB_TB_BOOKS);

			// Cleanup fields (author, series, title and remove blank fields for which we have defaults)
			preprocessOutput(id == 0, values);

			/* We may want to provide default values for these fields:
			 * KEY_RATING, KEY_READ, KEY_NOTES, KEY_LOCATION, KEY_READ_START, KEY_READ_END, KEY_SIGNED, & DATE_ADDED
			 */
			if (!values.containsKey(KEY_DATE_ADDED))
				values.putString(KEY_DATE_ADDED, DateUtils.toSqlDateTime(new Date()));

			// Make sure we have an author
			ArrayList<Author> authors = values.getAuthors();
			if (authors == null || authors.size() == 0)
				throw new IllegalArgumentException();
			ContentValues initialValues = filterValues(values, mBooksInfo);

			if (id > 0) {
				initialValues.put(KEY_ROWID, id);
			}

			if (!initialValues.containsKey(DOM_LAST_UPDATE_DATE.name))
				initialValues.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(new Date()));

			// ALWAYS set the INSTANCE_UPDATE_DATE; this is used for backups
			//initialValues.put(DOM_INSTANCE_UPDATE_DATE.name, Utils.toSqlDateTime(Calendar.getInstance().getTime()));

			long rowId = mDb.insert(DB_TB_BOOKS, null, initialValues);

			String bookshelf = values.getBookshelfList();
			if (bookshelf != null && !bookshelf.trim().isEmpty()) {
				createBookshelfBooks(rowId, ArrayUtils.decodeList(bookshelf, BookEditFields.BOOKSHELF_SEPARATOR), false);
			}

			createBookAuthors(rowId, authors, false);

			ArrayList<Series> series = values.getSeries();
			createBookSeries(rowId, series, false);

			ArrayList<AnthologyTitle> anthologyTitles = values.getAnthologyTitles();
			createBookAnthologyTitles(rowId, anthologyTitles, false);

			try {
				insertFts(rowId);
			} catch (Exception e) {
				Logger.logError(e, "Failed to update FTS");
			}

			return rowId;
		} catch (Exception e) {
			Logger.logError(e);
			throw new RuntimeException("Error creating book from " + values.getDataAsString() + ": " + e.getMessage(), e);
		}
	}
	
	/**
	 * This function will create a new bookshelf in the database
	 * 
	 * @param bookshelf The bookshelf name
     *
	 * @return the row ID of the newly inserted row, or -1 if an error occurred
	 */
	public long createBookshelf(String bookshelf) {
		// TODO: Decide if we need to backup EMPTY bookshelves...
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_BOOKSHELF, bookshelf);
		return mDb.insert(DB_TB_BOOKSHELF, null, initialValues);
	}

	// Statements used by createBookshelfBooks
	private SynchronizedStatement mDeleteBookshelfBooksStmt = null;
	private SynchronizedStatement mInsertBookshelfBooksStmt = null;

	/**
	 * Create each book/bookshelf combo in the weak entity
	 * 
	 * @param bookId                The book id
	 * @param bookshelves           A separated string of bookshelf names
	 * @param dirtyBookIfNecessary  flag to set book dirty or not (for now, always false...)
	 */
	private void createBookshelfBooks(long bookId, ArrayList<String> bookshelves,
                                      @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
		if (mDeleteBookshelfBooksStmt == null) {
			mDeleteBookshelfBooksStmt = mStatements.add("mDeleteBookshelfBooksStmt",
                    "Delete from " + DB_TB_BOOK_BOOKSHELF_WEAK + " Where " + KEY_BOOK + " = ?");
		}
		mDeleteBookshelfBooksStmt.bindLong(1, bookId);
		mDeleteBookshelfBooksStmt.execute();

		if (mInsertBookshelfBooksStmt == null) {
			mInsertBookshelfBooksStmt = mStatements.add("mInsertBookshelfBooksStmt",
                    "Insert Into " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" + KEY_BOOK + ", " + KEY_BOOKSHELF + ")"
								+ " Values (?,?)");
		}

		//Insert the new ones
		//String[] bookshelves = bookshelf.split(BookEditFields.BOOKSHELF_SEPARATOR.toString());
		for (int i = 0; i < bookshelves.size(); i++) {
			String name = bookshelves.get(i).trim();
			if (name.isEmpty()) {
				continue;
			}
			
			//ContentValues initialValues = new ContentValues();
			long bookshelfId = fetchBookshelfIdByName(name);
			if (bookshelfId == 0) {
				bookshelfId = createBookshelf(name);
			}
			if (bookshelfId == 0)
				bookshelfId = 1;

			try {
				mInsertBookshelfBooksStmt.bindLong(1, bookId);
				mInsertBookshelfBooksStmt.bindLong(2, bookshelfId);
				mInsertBookshelfBooksStmt.execute();
			} catch (Exception e) {
				Logger.logError(e, "Error assigning a book to a bookshelf.");
			}
		}

		if (dirtyBookIfNecessary)
			setBookDirty(bookId);
	}
	
	/**
	 * This function will create a new loan in the database
	 *
	 * @param values the book
	 * @param dirtyBookIfNecessary    flag to set book dirty or not (for now, always false...)
	 * @return the ID of the loan
	 */
	public long createLoan(BookData values, boolean dirtyBookIfNecessary) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_BOOK, values.getRowId());
		initialValues.put(KEY_LOANED_TO, values.getString(KEY_LOANED_TO));
		long result = mDb.insert(DB_TB_LOAN, null, initialValues);
		//Special cleanup step - Delete all loans without books
		this.deleteLoanInvalids();

		if (dirtyBookIfNecessary)
			setBookDirty(values.getRowId());

		return result;
	}

	/**
	 * Update the anthology title in the database
	 * 
	 * @param rowId The rowId of the anthology title 
	 * @param book The id of the book 
	 * @param author The author name
	 * @param title The title of the anthology story
	 * @return true/false on success
	 */
	public boolean updateAnthologyTitle(long rowId, long book, String author, String title, boolean dirtyBookIfNecessary) {
		ContentValues args = new ContentValues();
		String[] names = processAuthorName(author);
		long authorId = Long.parseLong(getAuthorIdOrCreate(names));

		long existingId = getAnthologyTitleId(book, authorId, title);
		if (existingId >= 0 && existingId != rowId)
			throw new AnthologyTitleExistsException();

		args.put(KEY_BOOK, book);
		args.put(KEY_AUTHOR_ID, authorId);
		args.put(KEY_TITLE, title);
		boolean success = mDb.update(DB_TB_ANTHOLOGY, args, KEY_ROWID + "=" + rowId, null) > 0;
		purgeAuthors();

		if (dirtyBookIfNecessary)
			setBookDirty(book);

		return success;
	}
	
	/**
	 * Move the given title up/down one position
	 * 
	 * @param rowId The rowId of the title 
	 * @param up true if going up, false if going down
	 * @return true/false on success
	 */
	public int updateAnthologyTitlePosition(long rowId, boolean up, boolean dirtyBookIfNecessary) {
        int book;
        int position;
		try (Cursor title = fetchAnthologyTitleById(rowId)) {
            title.moveToFirst();
            book = title.getInt(title.getColumnIndexOrThrow(KEY_BOOK));
            position = title.getInt(title.getColumnIndexOrThrow(KEY_POSITION));
        }

		int max_position = fetchAnthologyPositionByBook(rowId);

		if (position == 1 && up) {
			return 0;
		}
		if (position == max_position && !up) {
			return 0;
		}
		String sql;
		String dir;
		String opp_dir;
		if (up) {
			dir = "-1";
			opp_dir = "+1";
		} else {
			dir = "+1";
			opp_dir = "-1";
		}
		sql = "UPDATE " + DB_TB_ANTHOLOGY + " SET " + KEY_POSITION + "=" + KEY_POSITION + opp_dir + " " +
			" WHERE " + KEY_BOOK + "='" + book + "' AND " + KEY_POSITION + "=" + position + dir + " ";
		mDb.execSQL(sql);
		sql = "UPDATE " + DB_TB_ANTHOLOGY + " SET " + KEY_POSITION + "=" + KEY_POSITION + dir + " " +
		" WHERE " + KEY_BOOK + "='" + book + "' AND " + KEY_ROWID + "=" + rowId+ " ";
		mDb.execSQL(sql);

		if (dirtyBookIfNecessary)
			setBookDirty(book);

		return position;
	}

	private String getAuthorId(String name) {
		String[] names = processAuthorName(name);
		return getAuthorIdOrCreate(names);
	}

	private SynchronizedStatement mGetSeriesIdStmt = null;

	private long getSeriesId(String name) {
		if (mGetSeriesIdStmt == null) {
			mGetSeriesIdStmt = mStatements.add("mGetSeriesIdStmt", "Select " + KEY_ROWID + " From " + DB_TB_SERIES 
								+ " Where Upper(" + KEY_SERIES_NAME + ") = Upper(?)" + COLLATION);
		}
		long id;
		mGetSeriesIdStmt.bindString(1, name);
		try {
			id = mGetSeriesIdStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			id = 0;
		}
		return id;
	}

	private String getSeriesIdOrCreate(String name) {
		long id = getSeriesId(name);
		if (id == 0)
			id = createSeries(name);

		return Long.toString(id);
	}

	// Statements used by getAuthorId
	private SynchronizedStatement mGetAuthorIdStmt = null;
	private long getAuthorId(String[] names) {
		if (mGetAuthorIdStmt == null) {
			mGetAuthorIdStmt = mStatements.add("mGetAuthorIdStmt", "Select " + KEY_ROWID + " From " + DB_TB_AUTHORS 
								+ " Where Upper(" + KEY_FAMILY_NAME + ") = Upper(?) " + COLLATION
								+ " And Upper(" + KEY_GIVEN_NAMES + ") = Upper(?)" + COLLATION);
		}
		long id;
		try {
			mGetAuthorIdStmt.bindString(1, names[0]);
			mGetAuthorIdStmt.bindString(2, names[1]);
			id = mGetAuthorIdStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			id = 0;
		}
		return id;
	}

	private String getAuthorIdOrCreate(String[] names) {
		long id = getAuthorId(names);
		if (id == 0)
			id = createAuthor(names[0], names[1]);

		return Long.toString(id);
	}

	public long lookupAuthorId(Author a) {
		return getAuthorId(new String[] {a.familyName, a.givenNames});
	}

	public long lookupSeriesId(Series s) {
		return getSeriesId(s.name);
	}

	/**
	 * Return a Cursor over the list of all authors  in the database for the given book
	 * 
	 * @param rowId the rowId of the book
	 * @return Cursor over all authors
	 */
	public Cursor fetchAllAuthorsByBook(long rowId) {
		String sql = "SELECT DISTINCT a." + KEY_ROWID + " as " + KEY_ROWID 
			+ ", a." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME
			+ ", a." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES
			+ ", Case When a." + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME
			+ "  Else " + authorFormattedSource("") 
			+ " End as " + KEY_AUTHOR_FORMATTED
			+ ", ba." + KEY_AUTHOR_POSITION
			+ " FROM " + DB_TB_BOOK_AUTHOR + " ba Join " + DB_TB_AUTHORS + " a "
			+ "       On a." + KEY_ROWID + " = ba." + KEY_AUTHOR_ID
			+ " WHERE ba." + KEY_BOOK + "=" + rowId + " "
			+ " ORDER BY ba." + KEY_AUTHOR_POSITION + " Asc, Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + " ASC,"
			+ " Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + " ASC";
		return mDb.rawQuery(sql, new String[]{});
	}

	/**
	 * Returns a unique list of all formats in the database; uses the pre-defined ones if none.
	 *
	 * @return The list
	 */
	protected ArrayList<String> getFormats() {
		String sql = "Select distinct " + KEY_FORMAT + " from " + DB_TB_BOOKS
				+ " Order by lower(" + KEY_FORMAT + ") " + COLLATION;

		try (Cursor c = mDb.rawQuery(sql)) {
			ArrayList<String> list = singleColumnCursorToArrayList(c);
			if (list.size() == 0) {
				Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_formats));
			}
			return list;
		}
	}

	/**
	 * Returns a unique list of all languages in the database; uses the pre-defined ones if none.
	 *
	 * @return The list
	 */
	protected ArrayList<String> getLanguages() {
		String sql = "Select distinct " + DOM_LANGUAGE + " from " + DB_TB_BOOKS
				+ " Order by lower(" + DOM_LANGUAGE + ") " + COLLATION;

		try (Cursor c = mDb.rawQuery(sql)) {
			ArrayList<String> list = singleColumnCursorToArrayList(c);
			if (list.size() == 0) {
				Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_languages));
			}
			return list;
		}
	}

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList
     *
     * @param c     cursor
     * @return      the ArrayList
     */
    @NonNull
    private ArrayList<String> singleColumnCursorToArrayList(Cursor c) {
        ArrayList<String> list = new ArrayList<>();
        try {
			while (c.moveToNext()) {
				String name = c.getString(0);
				if (name != null)
					try {
                        // Hash to *try* to avoid duplicates
                        HashSet<String> foundSoFar = new HashSet<>();
						if (!name.isEmpty() && !foundSoFar.contains(name.toLowerCase())) {
							foundSoFar.add(name.toLowerCase());
							list.add(name);
						}
					} catch (NullPointerException ignore) {
						// do nothing
					}
			}
		} finally {
			c.close();
		}
        return list;
    }

    public ArrayList<AnthologyTitle> getBookAnthologyTitleList(long id) {
		ArrayList<AnthologyTitle> list = new ArrayList<>();
		try (Cursor cursor = this.fetchAnthologyTitlesByBook(id)) {

			int count = cursor.getCount();

			if (count == 0)
				return list;

			final int familyNameCol = cursor.getColumnIndex(KEY_FAMILY_NAME);
			final int givenNameCol = cursor.getColumnIndex(KEY_GIVEN_NAMES);
			final int authorIdCol = cursor.getColumnIndex(KEY_AUTHOR_ID);
			final int titleCol = cursor.getColumnIndex(KEY_TITLE);

			while (cursor.moveToNext()) {
				Author a = new Author(cursor.getLong(authorIdCol), cursor.getString(familyNameCol), cursor.getString(givenNameCol));
				list.add(new AnthologyTitle(a, cursor.getString(titleCol)));
			}			
		}
		return list;
	}

	public ArrayList<Author> getBookAuthorList(long id) {
		ArrayList<Author> authorList = new ArrayList<>();
		try (Cursor authors = fetchAllAuthorsByBook(id)) {

			int count = authors.getCount();

			if (count == 0)
				return authorList;

			int idCol = authors.getColumnIndex(KEY_ROWID);
			int familyCol = authors.getColumnIndex(KEY_FAMILY_NAME);
			int givenCol = authors.getColumnIndex(KEY_GIVEN_NAMES);

			while (authors.moveToNext()) {
				authorList.add(new Author(authors.getLong(idCol), authors.getString(familyCol), authors.getString(givenCol)));
			}			
		}
		return authorList;
	}

	public ArrayList<Series> getBookSeriesList(long id) {
		ArrayList<Series> seriesList = new ArrayList<>();
		try (Cursor series = fetchAllSeriesByBook(id)) {

			int count = series.getCount();

			if (count == 0)
				return seriesList;

			int idCol = series.getColumnIndex(KEY_ROWID);
			int nameCol = series.getColumnIndex(KEY_SERIES_NAME);
			int numCol = series.getColumnIndex(KEY_SERIES_NUM);

			while (series.moveToNext()) {
				seriesList.add(new Series(series.getLong(idCol), series.getString(nameCol), series.getString(numCol)));
			}			
		}
		return seriesList;
	}

	/**
	 * Return a Cursor over the list of all authors  in the database for the given book
	 * 
	 * @param rowId the rowId of the book
	 * @return Cursor over all authors
	 */
	public Cursor fetchAllSeriesByBook(long rowId) {
		String sql = "SELECT DISTINCT s." + KEY_ROWID + " as " + KEY_ROWID 
			+ ", s." + KEY_SERIES_NAME + " as " + KEY_SERIES_NAME
			+ ", bs." + KEY_SERIES_NUM + " as " + KEY_SERIES_NUM
			+ ", bs." + KEY_SERIES_POSITION + " as " + KEY_SERIES_POSITION
			+ ", " + KEY_SERIES_NAME + "||' ('||" + KEY_SERIES_NUM + "||')' as " + KEY_SERIES_FORMATTED 
			+ " FROM " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s "
			+ "       On s." + KEY_ROWID + " = bs." + KEY_SERIES_ID
			+ " WHERE bs." + KEY_BOOK + "=" + rowId + " "
			+ " ORDER BY bs." + KEY_SERIES_POSITION + ", Upper(s." + KEY_SERIES_NAME + ") " + COLLATION + " ASC";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a ContentValues collection containing only those values from 'source' that match columns in 'dest'.
	 * - Exclude the primary key from the list of columns.
	 * - data will be transformed based on the intended type of the underlying column based on column definition.
	 *  
	 * @param source	Source column data
	 * @param dest		Destination table definition
	 * 
	 * @return New, filtered, collection
	 */
	ContentValues filterValues(BookData source, TableInfo dest) {
		ContentValues args = new ContentValues();

		Set<String> keys = source.keySet();
		// Create the arguments
		for (String key : keys) {
			// Get column info for this column.
			TableInfo.ColumnInfo c = mBooksInfo.getColumn(key);
			// Check if we actually have a matching column.
			if (c != null) {
				// Never update PK.
				if (!c.isPrimaryKey) {

					Object v = source.get(key);

					// Try to set the appropriate value, but if that fails, just use TEXT...
					try {

						switch(c.typeClass) {

						case TableInfo.CLASS_REAL:
							if (v instanceof Float)
								args.put(c.name, (Float)v);
							else
								args.put(c.name, Float.parseFloat(v.toString()));
							break;

						case TableInfo.CLASS_INTEGER:
							if (v instanceof Boolean) {
								if ((Boolean)v) {
									args.put(c.name, 1);
								} else {
									args.put(c.name, 0);
								}
							} else if (v instanceof Integer) {
								args.put(c.name, (Integer)v);
							} else {
								args.put(c.name, Integer.parseInt(v.toString()));
							}
							break;

						case TableInfo.CLASS_TEXT:
							if (v instanceof String)
								args.put(c.name, (String) v);
							else							
								args.put(c.name, v.toString());
							break;
						}

					} catch (Exception e) {
						if (v != null)
							args.put(c.name, v.toString());						
					}
				}
			}
		}
		return args;
	}

	/**
	 * Examine the values and make any changes necessary before writing the data.
	 * 
	 * @param values	Collection of field values.
	 */
	private void preprocessOutput(boolean isNew, BookData values) {
		String authorId;

		// Handle AUTHOR
		// If present, get the author ID from the author name (it may have changed with a name change)
		if (values.containsKey(KEY_AUTHOR_FORMATTED)) {
			authorId = getAuthorId(values.getString(KEY_AUTHOR_FORMATTED));
			values.putString(KEY_AUTHOR_ID, authorId);
		} else {
			if (values.containsKey(KEY_FAMILY_NAME)) {
				String family = values.getString(KEY_FAMILY_NAME);
				String given;
				if (values.containsKey(KEY_GIVEN_NAMES)) {
					given = values.getString(KEY_GIVEN_NAMES);
				} else {
					given = "";
				}
				authorId = getAuthorIdOrCreate(new String[] {family, given});
				values.putString(KEY_AUTHOR_ID, authorId);
			}
		}

		// Handle TITLE; but only for new books
		if (isNew && values.containsKey(KEY_TITLE)) {
			/* Move "The, A, An" to the end of the string */
			String title = values.getString(KEY_TITLE);
			StringBuilder newTitle = new StringBuilder();
			String[] title_words = title.split(" ");
			try {
				if (title_words[0].matches(BookCatalogueApp.getResourceString(R.string.title_reorder))) {
					for (int i = 1; i < title_words.length; i++) {
						if (i != 1) {
							newTitle.append(" ");
						}
						newTitle.append(title_words[i]);
					}
					newTitle.append(", ").append(title_words[0]);
					values.putString(KEY_TITLE, newTitle.toString());
				}
			} catch (Exception e) {
				//do nothing. Title stays the same
			}
		}

		// Remove blank/null fields that have default values defined in the database or which should
		// never be blank.
		for (String name : new String[] {
				DatabaseDefinitions.DOM_BOOK_UUID.name, KEY_ANTHOLOGY_MASK,
				KEY_RATING, KEY_READ, KEY_SIGNED, KEY_DATE_ADDED,
				DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE.name,
				DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name }) {
			if (values.containsKey(name)) {
				Object o = values.get(name);
				// Need to allow for the possibility the stored value is not
				// a string, in which case getString() would return a NULL.
				if (o == null || o.toString().isEmpty())
					values.remove(name);
			}
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
	 * @param rowId The id of the book in the database
	 * @param values A ContentValues collection with the columns to be updated. May contain extra data.
	 * @param flags  See BOOK_UPDATE_* flag definitions
	 * 
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updateBook(long rowId, BookData values, int flags) {
		boolean success;

		try {
			// Make sure we have the target table details
			if (mBooksInfo == null)
				mBooksInfo = new TableInfo(mDb, DB_TB_BOOKS);

			// Cleanup fields (author, series, title and remove blank fields for which we have defaults)
			preprocessOutput(rowId == 0, values);

			ContentValues args = filterValues(values, mBooksInfo);

			// Disallow UUID updates
			if (args.containsKey(DOM_BOOK_UUID.name))
				args.remove(DOM_BOOK_UUID.name);

			// We may be just updating series, or author lists but we still update the last_update_date.
			if ((flags & BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT) == 0 || !args.containsKey(DOM_LAST_UPDATE_DATE.name))
				args.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(Calendar.getInstance().getTime()));
			// ALWAYS set the INSTANCE_UPDATE_DATE; this is used for backups
			//args.put(DOM_INSTANCE_UPDATE_DATE.name, Utils.toSqlDateTime(Calendar.getInstance().getTime()));
			success = mDb.update(DB_TB_BOOKS, args, KEY_ROWID + "=" + rowId, null) > 0;

			String bookshelf = values.getBookshelfList();
			if (bookshelf != null && !bookshelf.trim().isEmpty()) {
				createBookshelfBooks(rowId, ArrayUtils.decodeList(bookshelf, BookEditFields.BOOKSHELF_SEPARATOR), false);
			}

			if (values.containsKey(ColumnNames.KEY_AUTHOR_ARRAY)) {
				ArrayList<Author> authors = values.getAuthors();
				createBookAuthors(rowId, authors, false);			
			}
			if (values.containsKey(ColumnNames.KEY_SERIES_ARRAY)) {
				ArrayList<Series> series = values.getSeries();
				createBookSeries(rowId, series, false);			
			}

			if (values.containsKey(ColumnNames.KEY_ANTHOLOGY_TITLE_ARRAY)) {
				ArrayList<AnthologyTitle> anthologyTitles = values.getAnthologyTitles();
				createBookAnthologyTitles(rowId, anthologyTitles, false);				
			}

			// Only really skip the purge if a batch update of multiple books is being done.
			if ( (flags & BOOK_UPDATE_SKIP_PURGE_REFERENCES) == 0) {
				// Delete any unused authors
				purgeAuthors();
				// Delete any unused series
				purgeSeries();			
			}

			try {
				updateFts(rowId);
			} catch (Exception e) {
				Logger.logError(e, "Failed to update FTS");
			}
			return success;
		} catch (Exception e) {
			Logger.logError(e);
			throw new RuntimeException("Error updating book from " + values.getDataAsString() + ": " + e.getMessage(), e);
		}
	}

//	private static final String NEXT_STMT_NAME = "next";
	private void createBookAnthologyTitles(long bookId, ArrayList<AnthologyTitle> list, boolean dirtyBookIfNecessary) {
		if (dirtyBookIfNecessary)
			setBookDirty(bookId);

		this.deleteAnthologyTitles(bookId, false);
//		SynchronizedStatement stmt = mStatements.get(NEXT_STMT_NAME);
		for(int i = 0; i < list.size(); i++) {
			AnthologyTitle at = list.get(i);
			Author a = at.getAuthor();
			String authorIdStr = getAuthorIdOrCreate(new String[] {a.familyName, a.givenNames});
			long authorId = Long.parseLong(authorIdStr);
			this.createAnthologyTitle(bookId, authorId, at.getTitle(), true, false);
		}
	}

	// Statements used by createBookAuthors
	private SynchronizedStatement mDeleteBookAuthorsStmt = null;
	private SynchronizedStatement mAddBookAuthorsStmt = null;

	/**
	 * If the passed ContentValues contains KEY_AUTHOR_LIST, parse them
	 * and add the authors.
	 * 
	 * @param bookId		            ID of book
	 * @param dirtyBookIfNecessary      flag to set book dirty or not (for now, always false...)
	 */
	private void createBookAuthors(long bookId, ArrayList<Author> authors,
                                   @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
		if (dirtyBookIfNecessary)
			setBookDirty(bookId);

		// If we have AUTHOR_DETAILS, same them.
		if (authors != null) {
			if (mDeleteBookAuthorsStmt == null) {
				mDeleteBookAuthorsStmt = mStatements.add("mDeleteBookAuthorsStmt", "Delete from " + DB_TB_BOOK_AUTHOR + " Where " + KEY_BOOK + " = ?");
			}
			if (mAddBookAuthorsStmt == null) {
				mAddBookAuthorsStmt = mStatements.add("mAddBookAuthorsStmt", "Insert Into " + DB_TB_BOOK_AUTHOR 
															+ "(" + KEY_BOOK + "," + KEY_AUTHOR_ID + "," + KEY_AUTHOR_POSITION + ")"
															+ "Values(?,?,?)");
			}
			// Need to delete the current records because they may have been reordered and a simple set of updates
			// could result in unique key or index violations.
			mDeleteBookAuthorsStmt.bindLong(1, bookId);
			mDeleteBookAuthorsStmt.execute();

			// The list MAY contain duplicates (eg. from Internet lookups of multiple
			// sources), so we track them in a hash table
			Hashtable<String, Boolean> idHash = new Hashtable<>();
			int pos = 0;
			for (Author a : authors) {
				String authorIdStr = null;
				try {
					// Get the name and find/add the author
					authorIdStr = getAuthorIdOrCreate(new String[] {a.familyName, a.givenNames});
					long authorId = Long.parseLong(authorIdStr);
					if (!idHash.containsKey(authorIdStr)) {
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
					throw new RuntimeException("Error adding author '" + a.familyName + "," + a.givenNames + "' {" + authorIdStr + "} to book " + bookId + ": " + e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * If the passed ContentValues contains KEY_SERIES_DETAILS, parse them
	 * and add the series.
	 * 
	 * @param bookId		ID of book
	 * @param bookData		Book fields
	 */
	//
	private SynchronizedStatement mDeleteBookSeriesStmt = null;
	private SynchronizedStatement mAddBookSeriesStmt = null;
	private void createBookSeries(long bookId, ArrayList<Series> series, boolean dirtyBookIfNecessary) {
		if (dirtyBookIfNecessary)
			setBookDirty(bookId);
		// If we have SERIES_DETAILS, same them.
		if (series != null) {
			if (mDeleteBookSeriesStmt == null) {
				mDeleteBookSeriesStmt = mStatements.add("mDeleteBookSeriesStmt", "Delete from " + DB_TB_BOOK_SERIES + " Where " + KEY_BOOK + " = ?");
			}
			if (mAddBookSeriesStmt == null) {
				mAddBookSeriesStmt = mStatements.add("mAddBookSeriesStmt", "Insert Into " + DB_TB_BOOK_SERIES 
															+ "(" + KEY_BOOK + "," + KEY_SERIES_ID + "," + KEY_SERIES_NUM + "," + KEY_SERIES_POSITION + ")"
															+ " Values(?,?,?,?)");

			}

			// Delete the current series
			mDeleteBookSeriesStmt.bindLong(1, bookId);
			mDeleteBookSeriesStmt.execute();

			//
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
			Iterator<Series> i = series.iterator();
			// The list MAY contain duplicates (eg. from Internet lookups of multiple
			// sources), so we track them in a hash table
			Hashtable<String, Boolean> idHash = new Hashtable<>();
			int pos = 0;
			while (i.hasNext()) {
				Series s = null;
				String seriesIdTxt = null;
				try {
					// Get the name and find/add the author
					s = i.next();
					String seriesName = s.name;
					seriesIdTxt = getSeriesIdOrCreate(seriesName);
					long seriesId = Long.parseLong(seriesIdTxt);
					String uniqueId = seriesIdTxt + "(" + s.number.trim().toUpperCase() + ")";
					if (!idHash.containsKey(uniqueId)) {
						idHash.put(uniqueId, true);
						pos++;
						mAddBookSeriesStmt.bindLong(1, bookId);
						mAddBookSeriesStmt.bindLong(2, seriesId);
						mAddBookSeriesStmt.bindString(3, s.number);
						mAddBookSeriesStmt.bindLong(4, pos);
						mAddBookSeriesStmt.execute();
					}					
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Error adding series '" + (s == null ? "" : s.name) + "' {" + seriesIdTxt + "} to book " + bookId + ": " + e.getMessage(), e);
				}
			}
		}		
	}

	/**
	 * Update the bookshelf name
	 * 
	 * @param bookshelfId id of bookshelf to update
	 * @param bookshelf value to set bookshelf name to
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updateBookshelf(long bookshelfId, String bookshelf) {
		boolean success;
		ContentValues args = new ContentValues();
		args.put(KEY_BOOKSHELF, bookshelf);
		success = mDb.update(DB_TB_BOOKSHELF, args, KEY_ROWID + "=" + bookshelfId, null) > 0;
		purgeAuthors();

		// Mark all related book as dirty
		setBooksDirtyByBookshelf(bookshelfId);

		return success;
	}

	/**
	 * Utility routine to set a book as in need of backup if any ancillary data has changed.
	 */
	private void setBookDirty(long bookId) {
		// Mark specific book as dirty
		String sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where "
				+ TBL_BOOKS + "." + DOM_ID + " = " + bookId;
		mDb.execSQL(sql);
	}
	
	/**
	 * Utility routine to set all books referencing a given author as dirty.
	 */
	private void setBooksDirtyByAuthor(long authorId) {
		// Mark all related books based on anthology author as dirty
		String sql = "Update " + TBL_BOOKS  + " set " +  DOM_LAST_UPDATE_DATE + " = current_timestamp where "
				+ " Exists(Select * From " + TBL_ANTHOLOGY.ref() + " Where " + TBL_ANTHOLOGY.dot(DOM_AUTHOR_ID) + " = " + authorId
				+ " and " + TBL_ANTHOLOGY.dot(DOM_BOOK) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
		mDb.execSQL(sql);

		// Mark all related books based on series as dirty
		sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where "
				+ " Exists(Select * From " + TBL_BOOK_AUTHOR.ref() + " Where " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID) + " = " + authorId
				+ " and " + TBL_BOOK_AUTHOR.dot(DOM_BOOK) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
		mDb.execSQL(sql);
	}
	
	private void setBooksDirtyBySeries(long seriesId) {
		// Mark all related books based on series as dirty
		String sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where "
				+ " Exists(Select * From " + TBL_BOOK_SERIES.ref() + " Where " + TBL_BOOK_SERIES.dot(DOM_SERIES_ID) + " = " + seriesId
				+ " and " + TBL_BOOK_SERIES.dot(DOM_BOOK) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
		mDb.execSQL(sql);		
	}

	private void setBooksDirtyByBookshelf(long bookshelfId) {
		// Mark all related books as dirty
		String sql = "Update " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp where "
				+ " Exists(Select * From " + TBL_BOOK_BOOKSHELF.ref() + " Where " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOKSHELF_ID) + " = " + bookshelfId
				+ " and " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOK) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
		mDb.execSQL(sql);		
	}

	/**
	 * Update the author names or create a new one or update the passed object ID
	 * 
	 * @param a		Author in question
	 */
	public void syncAuthor(Author a) {
		long id = lookupAuthorId(a);

		// If we have a match, just update the object
		if (id != 0) {
			a.id = id;
			return;
		}

		addOrUpdateAuthor(a);

		return;
	}

	/**
	 * Update or create the passed author.
	 * 
	 * @param a		Author in question

	 */
	public void sendAuthor(Author a) {
		if (a.id == 0) {
			a.id = lookupAuthorId(a);
		}

		addOrUpdateAuthor(a);

		return;
	}

	/**
	 * Add or update the passed author, depending whether a.id == 0.
	 */
	private void addOrUpdateAuthor(Author a) {
		if (a.id != 0) {
			// Get the old author
			Author oldA = this.getAuthorById(a.id);
			// Update if changed (case SENSITIVE)
			if (!a.familyName.equals(oldA.familyName) || a.givenNames.equals(oldA.givenNames)) {
				ContentValues v = new ContentValues();
				v.put(KEY_FAMILY_NAME, a.familyName);
				v.put(KEY_GIVEN_NAMES, a.givenNames);
				mDb.update(DB_TB_AUTHORS, v, KEY_ROWID + " = " + a.id, null);
				// Mark any book referencing this author as dirty.
				setBooksDirtyByAuthor(a.id);
			}
		} else {
			a.id = createAuthor(a.familyName, a.givenNames);
		}		
	}


	/**
	 * Refresh the passed author from the database, if present. Used to ensure that
	 * the current record matches the current DB if some other task may have 
	 * changed the author.
	 * 
	 * @param a		Author in question
	 */
	public void refreshAuthor(Author a) {
		if (a.id == 0) {
			// It wasn't a known author; see if it is now. If so, update ID.
			long id = lookupAuthorId(a);
			// If we have a match, just update the object
			if (id != 0)
				a.id = id;
			return;
		} else {
			// It was a known author, see if it still is and update fields.
			Author newA = this.getAuthorById(a.id);
			if (newA != null) {
				a.familyName = newA.familyName;
				a.givenNames = newA.givenNames;
			} else {
				a.id = 0;
			}
		}
	}	

	/**
	 * Update the series name or create a new one or update the passed object ID
	 * 
	 * @param s		Series in question

	 */
	public void syncSeries(Series s) {
		long id = lookupSeriesId(s);
		// If we have a match, just update the object
		if (id != 0) {
			s.id = id;
			return;
		}

		addOrUpdateSeries(s);

		return;
	}

	/**
	 * Update or create the passed series.
	 * 
	 * @param s		Author in question

	 */
	public void sendSeries(Series s) {
		if (s.id == 0) {
			s.id = lookupSeriesId(s);
		}

		addOrUpdateSeries(s);

		return;
	}

	/**
	 * Add or update the passed series, depending whether s.id == 0.
	 */
	private void addOrUpdateSeries(Series s) {
		if (s.id != 0) {
			// Get the old author
			Series oldS = this.getSeriesById(s.id);
			// Update if changed (case SENSITIVE)
			if (!s.name.equals(oldS.name)) {
				ContentValues v = new ContentValues();
				v.put(KEY_SERIES_NAME, s.name);
				mDb.update(DB_TB_SERIES, v, KEY_ROWID + " = " + s.id, null);

				// Mark all books referencing this series as dirty
				this.setBooksDirtyBySeries(s.id);
			}
		} else {
			s.id = createSeries(s.name);
		}		
	}

	/**
	 * Delete ALL the anthology records for a given book rowId, if any
	 * 
	 * @param bookRowId id of the book
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteAnthologyTitles(long bookRowId, boolean dirtyBookIfNecessary) {
		boolean success;
		// Delete the anthology entries for the book
		success = mDb.delete(DB_TB_ANTHOLOGY, KEY_BOOK + "=" + bookRowId, null) > 0;
		// Mark book dirty
		if (dirtyBookIfNecessary)
			setBookDirty(bookRowId);
		// Cleanup the author list, if necessary (we may have deleted the only work by an author)
		purgeAuthors();
		return success;
	}

	/**
	 * Delete the anthology record with the given rowId (not to be confused with the book rowId
	 * 
	 * @param anthRowId id of the anthology to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteAnthologyTitle(long anthRowId, boolean dirtyBookIfNecessary) {
		// Find the soon to be deleted title position#
		int position;
		int book;
		try (Cursor anthology = fetchAnthologyTitleById(anthRowId)) {
			anthology.moveToFirst();
			position = anthology.getInt(anthology.getColumnIndexOrThrow(KEY_POSITION));
			book = anthology.getInt(anthology.getColumnIndexOrThrow(KEY_BOOK));
		}

		boolean success;
		// Delete the title
		success = mDb.delete(DB_TB_ANTHOLOGY, KEY_ROWID + "=" + anthRowId, null) > 0;
		purgeAuthors();
		// Move all titles past the deleted book up one position
		String sql = "UPDATE " + DB_TB_ANTHOLOGY + 
			" SET " + KEY_POSITION + "=" + KEY_POSITION + "-1" +
			" WHERE " + KEY_POSITION + ">" + position + " AND " + KEY_BOOK + "=" + book + "";
		mDb.execSQL(sql);

		if (dirtyBookIfNecessary)
			setBookDirty(book);

		return success;
	}

	// Statements for purgeAuthors
	private SynchronizedStatement mPurgeBookAuthorsStmt = null;
	private SynchronizedStatement mPurgeAuthorsStmt = null;
	/** 
	 * Delete the author with the given rowId
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean purgeAuthors() {
		// Delete DB_TB_BOOK_AUTHOR with no books
		if (mPurgeBookAuthorsStmt == null) {
			mPurgeBookAuthorsStmt = mStatements.add("mPurgeBookAuthorsStmt", "Delete from " + DB_TB_BOOK_AUTHOR + " Where " + KEY_BOOK 
									+ " Not In (SELECT DISTINCT " + KEY_ROWID + " FROM " + DB_TB_BOOKS + ") ");
		}

		boolean success;
		try {
			mPurgeBookAuthorsStmt.execute();
			success = true;
		} catch (Exception e) {
			Logger.logError(e, "Failed to purge Book Authors");
			success = false;
		}

		if (mPurgeAuthorsStmt == null) {
			mPurgeAuthorsStmt = mStatements.add("mPurgeAuthorsStmt", "Delete from " + DB_TB_AUTHORS + " Where "
					 			+ KEY_ROWID + " Not In (SELECT DISTINCT " + KEY_AUTHOR_ID + " FROM " + DB_TB_BOOK_AUTHOR + ")"
								+ " And " + KEY_ROWID + " Not In (SELECT DISTINCT " + KEY_AUTHOR_ID + " FROM " + DB_TB_ANTHOLOGY + ")");
		}
		
		try {
			mPurgeAuthorsStmt.execute();
		} catch (Exception e) {
			Logger.logError(e, "Failed to purge Authors");
			success = false;
		}

		return success;
	}

	// Statements for purgeSeries
	private SynchronizedStatement mPurgeBookSeriesStmt = null;
	private SynchronizedStatement mPurgeSeriesStmt = null;

	/** 
	 * Delete the series with no related books
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean purgeSeries() {
		if (mPurgeBookSeriesStmt == null) {
			mPurgeBookSeriesStmt = mStatements.add("mPurgeBookSeriesStmt", "Delete From "+ DB_TB_BOOK_SERIES + " Where " 
									+ KEY_BOOK + " NOT IN (SELECT DISTINCT " + KEY_ROWID + " FROM " + DB_TB_BOOKS + ")");
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
					+ KEY_ROWID + " NOT IN (SELECT DISTINCT " + KEY_SERIES_ID + " FROM " + DB_TB_BOOK_SERIES + ") ");
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

	/** 
	 * Delete the passed series
	 * 
	 * @param series 	series to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteSeries(Series series) {
		try {
			if (series.id == 0)
				series.id = lookupSeriesId(series);
			if (series.id == 0)
				return false;
		} catch (NullPointerException e) {
			Logger.logError(e);
			return false;
		}

		// Mark all related books dirty
		setBooksDirtyBySeries(series.id);

		// Delete DB_TB_BOOK_SERIES for this series
		boolean success1 = mDb.delete(DB_TB_BOOK_SERIES, KEY_SERIES_ID + " = " + series.id, null) > 0;
		
		boolean success2 = false;
		if (success1)
			// Cleanup all series
			success2 = purgeSeries();
		
		return success1 || success2;
	}
	
	/** 
	 * Delete the book with the given rowId
	 * 
	 * @param rowId id of book to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteBook(long rowId) {
		boolean success;
		String uuid = null;
		try {
			uuid = this.getBookUuid(rowId);
		} catch (Exception e) {
			Logger.logError(e, "Failed to get book UUID");
		}

		success = mDb.delete(DB_TB_BOOKS, KEY_ROWID + "=" + rowId, null) > 0;
		purgeAuthors();

		try {
			deleteFts(rowId);
		} catch (Exception e) {
			Logger.logError(e, "Failed to delete FTS");
		}
		// Delete thumbnail(s)
		if (uuid != null) {
			try {
				File f = ImageUtils.fetchThumbnailByUuid(uuid);
				while (f.exists()) {
					//noinspection ResultOfMethodCallIgnored
					f.delete();
					f = ImageUtils.fetchThumbnailByUuid(uuid);
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
	 * Delete the bookshelf with the given rowId
	 * 
	 * @param bookshelfId id of bookshelf to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteBookshelf(long bookshelfId) {

		setBooksDirtyByBookshelf(bookshelfId);

		boolean deleteSuccess;
		//String sql = "UPDATE " + DB_TB_BOOKS + " SET " + KEY_BOOKSHELF + "=1 WHERE " + KEY_BOOKSHELF + "='" + rowId + "'";
		//mDb.execSQL(sql);
		deleteSuccess = mDb.delete(DB_TB_BOOK_BOOKSHELF_WEAK, KEY_BOOKSHELF + "=" + bookshelfId, null) > 0;
		deleteSuccess = deleteSuccess && mDb.delete(DB_TB_BOOKSHELF, KEY_ROWID + "=" + bookshelfId, null) > 0;
		return deleteSuccess;
	}
	
	/** 
	 * Delete the loan with the given rowId
	 * 
	 * @param bookId 	id of book whose loan is to be deleted
	 * 
	 * @return 			true if deleted, false otherwise
	 */
	public boolean deleteLoan(long bookId, boolean dirtyBookIfNecessary) {
		boolean success;
		if (dirtyBookIfNecessary)
			setBookDirty(bookId);
		success = mDb.delete(DB_TB_LOAN, KEY_BOOK+ "=" + bookId, null) > 0;
		//Special cleanup step - Delete all loans without books
		this.deleteLoanInvalids();
		return success;
	}
	
	/** 
	 * Delete all loan without a book or loanee
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteLoanInvalids() {
		boolean success;
		success = mDb.delete(DB_TB_LOAN, "("+KEY_BOOK+ "='' OR " + KEY_BOOK+ "=null OR " + KEY_LOANED_TO + "='' OR " + KEY_LOANED_TO + "=null) ", null) > 0;
		return success;
	}


//////////////////////////////////////////
 // GoodReads support
//////////////////////////////////////////

	/** Static Factory object to create the custom cursor */
	private static final CursorFactory mBooksFactory = new CursorFactory() {
			@Override
			public Cursor newCursor(
					SQLiteDatabase db,
					SQLiteCursorDriver masterQuery, 
					String editTable,
					SQLiteQuery query) 
			{
				return new BooksCursor(masterQuery, editTable, query, mSynchronizer);
			}
	};

	/**
	 * Static method to get a BooksCursor Cursor. The passed sql should at least return 
	 * some of the fields from the books table! If a method call is made to retrieve 
	 * a column that does not exists, an exception will be thrown.
	 *
	 * @return			A new TaskExceptionsCursor
	 */
	public BooksCursor fetchBooks(String sql, String[] selectionArgs) {
		return (BooksCursor) mDb.rawQueryWithFactory(mBooksFactory, sql, selectionArgs, "");
	}

	/**
	 * Query to get all book IDs and ISBN for sending to goodreads.
	 */
	public BooksCursor getAllBooksForGoodreadsCursor(long startId, boolean updatesOnly) {
		String sql = "Select " + KEY_ISBN + ", " + KEY_ROWID + ", " + DOM_GOODREADS_BOOK_ID +
				", " + KEY_NOTES + ", " + KEY_READ + ", " + KEY_READ_END + ", " + KEY_RATING + 
				" from " + DB_TB_BOOKS + " Where " + KEY_ROWID + " > " + startId;
		if (updatesOnly) {
			sql += " and " + DOM_LAST_UPDATE_DATE + " > " + DOM_LAST_GOODREADS_SYNC_DATE;
		}
		sql += " Order by " + KEY_ROWID;

		return fetchBooks(sql, EMPTY_STRNG_ARRAY);
	}

	/**
	 * Query to get a specific book ISBN from the ID for sending to goodreads.
	 */
	public BooksCursor getBookForGoodreadsCursor(long bookId) {
		String sql = "Select " + KEY_ROWID + ", " + KEY_ISBN + ", " + DOM_GOODREADS_BOOK_ID + 
				", " + KEY_NOTES + ", " + KEY_READ + ", " + KEY_READ_END + ", " + KEY_RATING + 
				" from " + DB_TB_BOOKS + " Where " + KEY_ROWID + " = " + bookId + " Order by " + KEY_ROWID;
		return fetchBooks(sql, EMPTY_STRNG_ARRAY);
	}

	/**
	 * Query to get a all bookshelves for a book, for sending to goodreads.
	 */
	public Cursor getAllBookBookshelvesForGoodreadsCursor(long book) {
		String sql = "Select s." + KEY_BOOKSHELF + " from " + DB_TB_BOOKSHELF + " s"
				 + " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs On bbs." + KEY_BOOKSHELF + " = s." + KEY_ROWID
				 + " and bbs." + KEY_BOOK + " = " + book + " Order by s." + KEY_BOOKSHELF;
		return mDb.rawQuery(sql, EMPTY_STRING_ARRAY);
	}

	/** Support statement for setGoodreadsBookId() */
	private SynchronizedStatement mSetGoodreadsBookIdStmt = null;
	/**
	 * Set the goodreads book id for this passed book. This is used by other goodreads-related 
	 * functions.
	 * 
	 * @param bookId			Book to update
	 * @param goodreadsBookId	GR book id
	 */
	public void setGoodreadsBookId(long bookId, long goodreadsBookId) {
		if (mSetGoodreadsBookIdStmt == null ) {
			String sql = "Update " + TBL_BOOKS + " Set " + DOM_GOODREADS_BOOK_ID + " = ? Where " + DOM_ID + " = ?";
			mSetGoodreadsBookIdStmt = mStatements.add("mSetGoodreadsBookIdStmt", sql);
		}
		mSetGoodreadsBookIdStmt.bindLong(1, goodreadsBookId);
		mSetGoodreadsBookIdStmt.bindLong(2, bookId);
		mSetGoodreadsBookIdStmt.execute();
	}

	/** Support statement for setGoodreadsSyncDate() */
	private SynchronizedStatement mSetGoodreadsSyncDateStmt = null;

	/** 
	 * Set the goodreads sync date to the current time
	 */
	public void setGoodreadsSyncDate(long bookId) {
		if (mSetGoodreadsSyncDateStmt == null) {
			String sql = "Update " + DB_TB_BOOKS + " Set " + DOM_LAST_GOODREADS_SYNC_DATE + " = current_timestamp Where " + KEY_ROWID + " = ?";
			mSetGoodreadsSyncDateStmt = mStatements.add("mSetGoodreadsSyncDateStmt", sql);			
		}
		mSetGoodreadsSyncDateStmt.bindLong(1, bookId);
		mSetGoodreadsSyncDateStmt.execute();		
	}
	
//	/** Support statement for getGoodreadsSyncDate() */
//	private SynchronizedStatement mGetGoodreadsSyncDateStmt = null;
//	/** 
//	 * Set the goodreads sync date to the current time
//	 * 
//	 * @param bookId
//	 */
//	public String getGoodreadsSyncDate(long bookId) {
//		if (mGetGoodreadsSyncDateStmt == null) {
//			String sql = "Select " + DOM_LAST_GOODREADS_SYNC_DATE + " From " + DB_TB_BOOKS + " Where " + KEY_ROWID + " = ?";
//			mGetGoodreadsSyncDateStmt = mStatements.add("mGetGoodreadsSyncDateStmt", sql);			
//		}
//		mGetGoodreadsSyncDateStmt.bindLong(1, bookId);
//		return mGetGoodreadsSyncDateStmt.simpleQueryForString();			
//	}
	
/**************************************************************************************/
	
    /*
     * This will return the author based on the ID.
     */
    public Author getAuthorById(long id) {
		String sql = "Select " + KEY_FAMILY_NAME + ", " + KEY_GIVEN_NAMES + " From " + DB_TB_AUTHORS
				+ " Where " + KEY_ROWID + " = " + id;
     	try (Cursor c = mDb.rawQuery(sql, null)) {
             if (!c.moveToFirst())
            	return null;
            return new Author(id, c.getString(0), c.getString(1)); 
    	}
    }
 
    /*
     * This will return the series based on the ID.
     */
    public Series getSeriesById(long id) {
		String sql = "Select " + KEY_SERIES_NAME + " From " + DB_TB_SERIES
				+ " Where " + KEY_ROWID + " = " + id;
    	try (Cursor c = mDb.rawQuery(sql, null)) {
            if (!c.moveToFirst())
            	return null;
            return new Series(id, c.getString(0), ""); 
    	}
    }
 
    private SynchronizedStatement mGetAuthorBookCountQuery = null;
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public long getAuthorBookCount(Author a) {
    	if (a.id == 0)
    		a.id = lookupAuthorId(a);
    	if (a.id == 0)
    		return 0;
 
    	if (mGetAuthorBookCountQuery == null) {
        	String sql = "Select Count(" + KEY_BOOK + ") From " + DB_TB_BOOK_AUTHOR + " Where " + KEY_AUTHOR_ID + "=?";
        	mGetAuthorBookCountQuery = mStatements.add("mGetAuthorBookCountQuery", sql);
    	}
    	// Be cautious
    	synchronized(mGetAuthorBookCountQuery) {
        	mGetAuthorBookCountQuery.bindLong(1, a.id);
        	return mGetAuthorBookCountQuery.simpleQueryForLong();
    	}

    }
    
    private SynchronizedStatement mGetAuthorAnthologyCountQuery = null;
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public long getAuthorAnthologyCount(Author a) {
    	if (a.id == 0)
    		a.id = lookupAuthorId(a);
    	if (a.id == 0)
    		return 0;
 
    	if (mGetAuthorAnthologyCountQuery == null) {
        	String sql = "Select Count(" + KEY_ROWID + ") From " + DB_TB_ANTHOLOGY + " Where " + KEY_AUTHOR_ID + "=?";
        	mGetAuthorAnthologyCountQuery = mStatements.add("mGetAuthorAnthologyCountQuery", sql);
    	}
    	// Be cautious
    	synchronized(mGetAuthorAnthologyCountQuery) {
    		mGetAuthorAnthologyCountQuery.bindLong(1, a.id);
        	return mGetAuthorAnthologyCountQuery.simpleQueryForLong();
    	}

    }

    private SynchronizedStatement mGetSeriesBookCountQuery = null;
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public long getSeriesBookCount(Series s) {
    	if (s.id == 0)
    		s.id = lookupSeriesId(s);
    	if (s.id == 0)
    		return 0;
 
    	if (mGetSeriesBookCountQuery == null) {
        	String sql = "Select Count(" + KEY_BOOK + ") From " + DB_TB_BOOK_SERIES + " Where " + KEY_SERIES_ID + "=?";
        	mGetSeriesBookCountQuery = mStatements.add("mGetSeriesBookCountQuery", sql);
    	}
    	// Be cautious
    	synchronized(mGetSeriesBookCountQuery) {
    		mGetSeriesBookCountQuery.bindLong(1, s.id);
        	return mGetSeriesBookCountQuery.simpleQueryForLong();
    	}

    }
    
    public void globalReplaceAuthor(Author oldAuthor, Author newAuthor) {
		// Create or update the new author
		if (newAuthor.id == 0)
			syncAuthor(newAuthor);
		else
			sendAuthor(newAuthor);

		// Do some basic sanity checks
		if (oldAuthor.id == 0)
			oldAuthor.id = lookupAuthorId(oldAuthor);
		if (oldAuthor.id == 0)
			throw new RuntimeException("Old Author is not defined");

		if (oldAuthor.id == newAuthor.id)
			return;

		SyncLock l = mDb.beginTransaction(true);
		try {
			setBooksDirtyByAuthor(oldAuthor.id);

			// First handle anthologies; they have a single author and are easy
			String sql = "Update " + DB_TB_ANTHOLOGY + " set " + KEY_AUTHOR_ID + " = " + newAuthor.id
						+ " Where " + KEY_AUTHOR_ID + " = " + oldAuthor.id;
			mDb.execSQL(sql);

			globalReplacePositionedBookItem(DB_TB_BOOK_AUTHOR, KEY_AUTHOR_ID, KEY_AUTHOR_POSITION, oldAuthor.id, newAuthor.id);

			//sql = "Delete from " + DB_TB_BOOK_AUTHOR + " Where " + KEY_AUTHOR_ID + " = " + oldAuthor.id 
			//+ " And Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba Where "
			//+ "                 ba." + KEY_BOOK + " = " + DB_TB_BOOK_AUTHOR + "." + KEY_BOOK
			//+ "                 and ba." + KEY_AUTHOR_ID + " = " + newAuthor.id + ")";
			//mDb.execSQL(sql);

			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction(l);
		}
	}
    
    public void globalReplaceSeries(Series oldSeries, Series newSeries) {
		// Create or update the new author
		if (newSeries.id == 0)
			syncSeries(newSeries);
		else
			sendSeries(newSeries);

		// Do some basic sanity checks
		if (oldSeries.id == 0)
			oldSeries.id = lookupSeriesId(oldSeries);
		if (oldSeries.id == 0)
			throw new RuntimeException("Old Series is not defined");

		if (oldSeries.id == newSeries.id)
			return;

		SyncLock l = mDb.beginTransaction(true);
		try {
			setBooksDirtyBySeries(oldSeries.id);

			// Update books but prevent duplicate index errors
			String sql = "Update " + DB_TB_BOOK_SERIES + " Set " + KEY_SERIES_ID + " = " + newSeries.id 
					+ " Where " + KEY_SERIES_ID + " = " + oldSeries.id
					+ " and Not Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
					+ "                 bs." + KEY_BOOK + " = " + DB_TB_BOOK_SERIES + "." + KEY_BOOK
					+ "                 and bs." + KEY_SERIES_ID + " = " + newSeries.id + ")";
			mDb.execSQL(sql);	

			globalReplacePositionedBookItem(DB_TB_BOOK_SERIES, KEY_SERIES_ID, KEY_SERIES_POSITION, oldSeries.id, newSeries.id);

			//sql = "Delete from " + DB_TB_BOOK_SERIES + " Where " + KEY_SERIES_ID + " = " + oldSeries.id 
			//+ " and Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
			//+ "                 bs." + KEY_BOOK + " = " + DB_TB_BOOK_SERIES + "." + KEY_BOOK
			//+ "                 and bs." + KEY_SERIES_ID + " = " + newSeries.id + ")";
			//mDb.execSQL(sql);

			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction(l);
		}
	}

    private void globalReplacePositionedBookItem(String tableName, String objectIdField, String positionField, long oldId, long newId) {
    	String sql;
 
    	if ( !mDb.inTransaction() )
    		throw new RuntimeException("globalReplacePositionedBookItem must be called in a transaction");

		// Update books but prevent duplicate index errors - update books for which the new ID is not already present
		sql = "Update " + tableName + " Set " + objectIdField + " = " + newId
				+ " Where " + objectIdField + " = " + oldId
				+ " and Not Exists(Select NULL From " + tableName + " ba Where "
				+ "                 ba." + KEY_BOOK + " = " + tableName + "." + KEY_BOOK
				+ "                 and ba." + objectIdField + " = " + newId + ")";
		mDb.execSQL(sql);	
		
		// Finally, delete the rows that would have caused duplicates. Be cautious by using the 
		// EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
		// loss if one of the prior statements failed silently.
		//
		// We also move remaining items up one place to ensure positions remain correct
		//
		sql = "select * from " + tableName + " Where " + objectIdField + " = " + oldId
		+ " And Exists(Select NULL From " + tableName + " ba Where "
		+ "                 ba." + KEY_BOOK + " = " + tableName + "." + KEY_BOOK
		+ "                 and ba." + objectIdField + " = " + newId + ")";			

		SynchronizedStatement delStmt = null;
		SynchronizedStatement replacementIdPosStmt = null;
		SynchronizedStatement checkMinStmt = null;
		SynchronizedStatement moveStmt = null;
		try (Cursor c = mDb.rawQuery(sql)) {
			// Get the column indexes we need
			final int bookCol = c.getColumnIndexOrThrow(KEY_BOOK);
			final int posCol = c.getColumnIndexOrThrow(positionField);

			// Statement to delete a specific object record
			sql = "Delete from " + tableName + " Where " + objectIdField + " = ? and " + KEY_BOOK + " = ?";
			delStmt = mDb.compileStatement(sql);

			// Statement to get the position of the already-existing 'new/replacement' object
			sql = "Select " + positionField + " From " + tableName + " Where " + KEY_BOOK + " = ? and " + objectIdField + " = " + newId;
			replacementIdPosStmt = mDb.compileStatement(sql);

			// Move statement; move a single entry to a new position
			sql = "Update " + tableName + " Set " + positionField + " = ? Where " + KEY_BOOK + " = ? and " + positionField + " = ?";
			moveStmt = mDb.compileStatement(sql);

			// Sanity check to deal with legacy bad data
			sql = "Select min(" + positionField + ") From " + tableName + " Where " + KEY_BOOK + " = ?";
			checkMinStmt = mDb.compileStatement(sql);

			// Loop through all instances of the old author appearing
			while (c.moveToNext()) {
				// Get the details of the old object
				long book = c.getLong(bookCol);
				long pos = c.getLong(posCol);

				// Get the position of the new/replacement object
				replacementIdPosStmt.bindLong(1, book);
				long replacementIdPos = replacementIdPosStmt.simpleQueryForLong();

				// Delete the old record
				delStmt.bindLong(1, oldId);
				delStmt.bindLong(2, book);
				delStmt.execute();

				// If the deleted object was more prominent than the new object, move the new one up
				if (replacementIdPos > pos) {
					moveStmt.bindLong(1, pos);
					moveStmt.bindLong(2, book);
					moveStmt.bindLong(3, replacementIdPos);
					moveStmt.execute();
				}
					
				//
				// It is tempting to move all rows up by one when we delete something, but that would have to be 
				// done in another sorted cursor in order to prevent duplicate index errors. So we just make
				// sure we have something in position 1.
				//

				// Get the minimum position
				checkMinStmt.bindLong(1, book);
				long minPos = checkMinStmt.simpleQueryForLong();
				// If it's > 1, move it to 1
				if (minPos > 1) {
					moveStmt.bindLong(1, 1);
					moveStmt.bindLong(2, book);
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
     * Data cleanup routine called on upgrade to v4.0.3 to cleanup data integrity issues cased 
     * by earlier merge code that could have left the first author or series for a book having
     * a position number > 1.
     */
    public void fixupAuthorsAndSeries() {
		SyncLock l = mDb.beginTransaction(true);
		try {
			fixupPositionedBookItems(DB_TB_BOOK_AUTHOR, KEY_AUTHOR_ID, KEY_AUTHOR_POSITION);
			fixupPositionedBookItems(DB_TB_BOOK_SERIES, KEY_SERIES_ID, KEY_SERIES_POSITION);
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction(l);
		}
    	
    }

    /**
     * Data cleaning routine for upgrade to version 4.0.3 to cleanup any books that have no primary author/series.
     */
    private void fixupPositionedBookItems(String tableName, String objectIdField, String positionField) {
		String sql = "select b." + KEY_ROWID + " as " + KEY_ROWID + ", min(o." + positionField + ") as pos" +
				" from " + TBL_BOOKS + " b join " + tableName + " o On o." + KEY_BOOK + " = b." + KEY_ROWID +
				" Group by b." + KEY_ROWID;
		SynchronizedStatement moveStmt = null;
		try (Cursor c = mDb.rawQuery(sql)) {
			// Get the column indexes we need
			final int bookCol = c.getColumnIndexOrThrow(KEY_ROWID);
			final int posCol = c.getColumnIndexOrThrow("pos");
			// Loop through all instances of the old author appearing
			while (c.moveToNext()) {
				// Get the details
				long pos = c.getLong(posCol);
				if (pos > 1) {
					if (moveStmt == null) {
						// Statement to move records up by a given offset
						sql = "Update " + tableName + " Set " + positionField + " = 1 Where " + KEY_BOOK + " = ? and " + positionField + " = ?";
						moveStmt = mDb.compileStatement(sql);						
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
    
    public void globalReplaceFormat(String oldFormat, String newFormat) {

		SyncLock l = mDb.beginTransaction(true);
		try {
			String sql;

			// Update books but prevent duplicate index errors
			sql = "Update " + DB_TB_BOOKS + " Set " + KEY_FORMAT + " = '" + encodeString(newFormat) 
					+ "', " + DOM_LAST_UPDATE_DATE + " = current_timestamp "
					+ " Where " + KEY_FORMAT + " = '" + encodeString(oldFormat) + "'";
			mDb.execSQL(sql);	
			
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction(l);
		}
	}

    private SynchronizedStatement mGetBookUuidQuery = null;
    /**
     * Utility routine to return the book title based on the id. 
     */
    public String getBookUuid(long id) {
    	if (mGetBookUuidQuery == null) {
        	String sql = "Select " + DOM_BOOK_UUID + " From " + DB_TB_BOOKS + " Where " + KEY_ROWID + "=?";
        	mGetBookUuidQuery = mStatements.add("mGetBookUuidQuery", sql);
    	}
    	// Be cautious; other threads may call this and set parameters.
    	synchronized(mGetBookUuidQuery) {
    		mGetBookUuidQuery.bindLong(1, id);
    		//try {
            	return mGetBookUuidQuery.simpleQueryForString();    			
    		//} catch (SQLiteDoneException e) {
    		//	return null;
    		//}
    	}
    }
    
    private SynchronizedStatement mGetBookUpdateDateQuery = null;
    /**
     * Utility routine to return the book title based on the id. 
     */
    public String getBookUpdateDate(long bookId) {
    	if (mGetBookUpdateDateQuery == null) {
        	String sql = "Select " + DOM_LAST_UPDATE_DATE + " From " + DB_TB_BOOKS + " Where " + KEY_ROWID + "=?";
        	mGetBookUpdateDateQuery = mStatements.add("mGetBookUpdateDateQuery", sql);
    	}
    	// Be cautious; other threads may call this and set parameters.
    	synchronized(mGetBookUpdateDateQuery) {
    		mGetBookUpdateDateQuery.bindLong(1, bookId);
    		//try {
            	return mGetBookUpdateDateQuery.simpleQueryForString();    			
    		//} catch (SQLiteDoneException e) {
    		//	return null;
    		//}
    	}
    }
    
    private SynchronizedStatement mGetBookTitleQuery = null;
    /**
     * Utility routine to return the book title based on the id. 
     */
    public String getBookTitle(long id) {
    	if (mGetBookTitleQuery == null) {
        	String sql = "Select " + KEY_TITLE + " From " + DB_TB_BOOKS + " Where " + KEY_ROWID + "=?";
        	mGetBookTitleQuery = mStatements.add("mGetBookTitleQuery", sql);
    	}
    	// Be cautious; other threads may call this and set parameters.
    	synchronized(mGetBookTitleQuery) {
    		mGetBookTitleQuery.bindLong(1, id);
        	return mGetBookTitleQuery.simpleQueryForString();
    	}

    }
    
    /**
     * Utility routine to fill an array with the specified column from the passed SQL.
     * 
     * @param sql			SQL to execute
     * @param columnName	Column to fetch
     * 
     * @return				List of values
     */
    private ArrayList<String> fetchArray(String sql, String columnName) {
		ArrayList<String> list = new ArrayList<>();
		try (Cursor cursor = mDb.rawQuery(sql, new String[]{})) {
			int column = cursor.getColumnIndexOrThrow(columnName);
			while (cursor.moveToNext()) {
				String name = cursor.getString(column);
				list.add(name);
			}
			return list;			
		}
    }

    /**
     * Utility routine to build an arrayList of all series names.
     */
	public ArrayList<String> fetchAllSeriesArray() {
    	String sql = "SELECT DISTINCT " + KEY_SERIES_NAME +  
					" FROM " + DB_TB_SERIES + "" +  
					" ORDER BY Upper(" + KEY_SERIES_NAME + ") " + COLLATION;
    	return fetchArray(sql, KEY_SERIES_NAME);
	}

    /**
     * Utility routine to build an arrayList of all author names.
     */
	public ArrayList<String> fetchAllAuthorsArray() {

    	String sql = "SELECT DISTINCT Case When " + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME +  
    				" Else " + KEY_FAMILY_NAME + "||', '||" + KEY_GIVEN_NAMES +
    				" End as " + KEY_AUTHOR_FORMATTED + 
					" FROM " + DB_TB_AUTHORS + "" +  
					" ORDER BY Upper(" + KEY_AUTHOR_FORMATTED + ") " + COLLATION;
    	return fetchArray(sql, KEY_AUTHOR_FORMATTED);
	}

    public static String encodeString(String value) {
    	return value.replace("'", "''");
    }
	
	public SyncLock startTransaction(boolean isUpdate) {
		return mDb.beginTransaction(isUpdate);
	}
	public void endTransaction(SyncLock lock) {
		mDb.endTransaction(lock);
	}
	public void setTransactionSuccessful() {
		mDb.setTransactionSuccessful();
	}
	
	public void analyzeDb() {
		try {
			mDb.execSQL("analyze");    		
		} catch (Exception e) {
			Logger.logError(e, "Analyze failed");
		}
	}
	
	/**
	 * @return a list of all defined styles in the database
	 */
	public Cursor getBooklistStyles() {
		final String sql = "Select " + TBL_BOOK_LIST_STYLES.ref(DOM_ID, DOM_STYLE) 
						+ " From " +  TBL_BOOK_LIST_STYLES.ref();
						// + " Order By " + TBL_BOOK_LIST_STYLES.ref(DOM_POSITION, DOM_ID);
		return mDb.rawQuery(sql);
	}

	/**
	 * Create a new booklist style
	 */
	private SynchronizedStatement mInsertBooklistStyleStmt = null;
	public long insertBooklistStyle(BooklistStyle style) {
		if (mInsertBooklistStyleStmt == null) {
			final String sql = TBL_BOOK_LIST_STYLES.getInsert(DOM_STYLE) 
						+ " Values (?)";
			mInsertBooklistStyleStmt = mStatements.add("mInsertBooklistStyleStmt", sql);
		}
		byte[] blob = SerializationUtils.serializeObject(style);
		mInsertBooklistStyleStmt.bindBlob(1, blob);
		return mInsertBooklistStyleStmt.executeInsert();
	}

	/**
	 * Update an existing booklist style
	 */
	private SynchronizedStatement mUpdateBooklistStyleStmt = null;
	public void updateBooklistStyle(BooklistStyle style) {
		if (mUpdateBooklistStyleStmt == null) {
			final String sql = TBL_BOOK_LIST_STYLES.getInsertOrReplaceValues(DOM_ID, DOM_STYLE);
			mUpdateBooklistStyleStmt = mStatements.add("mUpdateBooklistStyleStmt", sql);
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
	public void deleteBooklistStyle(long id) {
		if (mDeleteBooklistStyleStmt == null) {
			final String sql = "Delete from " + TBL_BOOK_LIST_STYLES 
						+ " Where " +  DOM_ID + " = ?";
			mDeleteBooklistStyleStmt = mStatements.add("mDeleteBooklistStyleStmt", sql);
		}
		mDeleteBooklistStyleStmt.bindLong( 1, id );
		mDeleteBooklistStyleStmt.execute();
	}

	///////////////////////////////////////////
	 // FTS Support
	//////////////////////////////////////////


	/**
	 * Send the book details from the cursor to the passed fts query. 
	 * 
	 * NOTE: This assumes a specific order for query parameters.
	 * 
	 * @param books		Cursor of books to update
	 * @param stmt		Statement to execute
	 * 
	 */
	public void ftsSendBooks(BooksCursor books, SynchronizedStatement stmt) {

		final BooksRowView book = books.getRowView();
		// Build the SQL to get author details for a book.
		// ... all authors
		final String authorBaseSql = "Select " + TBL_AUTHORS.dot("*") + " from " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
				" Where " + TBL_BOOK_AUTHOR.dot(DOM_BOOK) + " = ";
		// ... all series
		final String seriesBaseSql = "Select " + TBL_SERIES.dot(DOM_SERIES_NAME) + " || ' ' || Coalesce(" + TBL_BOOK_SERIES.dot(DOM_SERIES_NUM) + ",'') as seriesInfo from " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
				" Where " + TBL_BOOK_SERIES.dot(DOM_BOOK) + " = ";
		// ... all anthology titles
		final String anthologyBaseSql = "Select " + TBL_AUTHORS.dot(KEY_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(KEY_FAMILY_NAME) + " as anthologyAuthorInfo, " + DOM_TITLE + " as anthologyTitleInfo "
				+ " from " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_AUTHORS) +
				" Where " + TBL_ANTHOLOGY.dot(DOM_BOOK) + " = ";
		
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
			try (Cursor c = mDb.rawQuery(authorBaseSql + book.getId())) {
				// Get column indexes, if not already got
				if (colGivenNames < 0)
					colGivenNames = c.getColumnIndex(KEY_GIVEN_NAMES);
				if (colFamilyName < 0)
					colFamilyName = c.getColumnIndex(KEY_FAMILY_NAME);
				// Append each author
				while (c.moveToNext()) {
					authorText.append(c.getString(colGivenNames));
					authorText.append(" ");
					authorText.append(c.getString(colFamilyName));
					authorText.append(";");
				}
			}

			// Get list of series
			try (Cursor c = mDb.rawQuery(seriesBaseSql + book.getId())) {
				// Get column indexes, if not already got
				if (colSeriesInfo < 0)
					colSeriesInfo = c.getColumnIndex("seriesInfo");
				// Append each series
				while (c.moveToNext()) {
					seriesText.append(c.getString(colSeriesInfo));
					seriesText.append(";");
				}
			}

			// Get list of anthology data (author and title)
			try (Cursor c = mDb.rawQuery(anthologyBaseSql + book.getId())) {
				// Get column indexes, if not already got
				if (colAnthologyAuthorInfo < 0)
					colAnthologyAuthorInfo = c.getColumnIndex("anthologyAuthorInfo");
				if (colAnthologyTitleInfo < 0)
					colAnthologyTitleInfo = c.getColumnIndex("anthologyTitleInfo");
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
			bindStringOrNull(stmt, 2, book.getTitle() + "; " + titleText.toString());
			// We could add a 'series' column, or just add it as part of the desciption
			bindStringOrNull(stmt, 3, book.getDescription() + seriesText.toString());
			bindStringOrNull(stmt, 4, book.getNotes());
			bindStringOrNull(stmt, 5, book.getPublisher());
			bindStringOrNull(stmt, 6, book.getGenre());
			bindStringOrNull(stmt, 7, book.getLocation());
			bindStringOrNull(stmt, 8, book.getIsbn());
			stmt.bindLong(9, book.getId());

			stmt.execute();
		}
	}
	
	/**
	 * Insert a book into the FTS. Assumes book does not already exist in FTS.
	 * 
	 * @param bookId
	 */
	private SynchronizedStatement mInsertFtsStmt = null;
	public void insertFts(long bookId) {
		//long t0 = System.currentTimeMillis();
		
		if (mInsertFtsStmt == null) {
			// Build the FTS insert statement base. The parameter order MUST match the order expected in ftsSendBooks().
			String sql = TBL_BOOKS_FTS.getInsert(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_NOTES, 
												DOM_PUBLISHER, DOM_GENRE, DOM_LOCATION, DOM_ISBN, DOM_DOCID)
												+ " Values (?,?,?,?,?,?,?,?,?)";
			mInsertFtsStmt = mStatements.add("mInsertFtsStmt", sql);
		}

		BooksCursor books = null;

		// Start an update TX
		SyncLock l = null;
		if (!mDb.inTransaction())
			l = mDb.beginTransaction(true);
		try {
			// Compile statement and get books cursor
			books = fetchBooks("select * from " + TBL_BOOKS + " where " + DOM_ID + " = " + bookId, EMPTY_STRING_ARRAY);
			// Send the book
			ftsSendBooks(books, mInsertFtsStmt);
			if (l != null)
				mDb.setTransactionSuccessful();
		} finally {
			// Cleanup
			if (books != null)
				try { books.close(); } catch (Exception ignored) {}
            if (l != null)
				mDb.endTransaction(l);
			//long t1 = System.currentTimeMillis();
			//System.out.println("Inserted FTS in " + (t1-t0) + "ms");
		}
	}
	
	/**
	 * Update an existing FTS record.
	 * 
	 * @param bookId
	 */
	private SynchronizedStatement mUpdateFtsStmt = null;
	private void updateFts(long bookId) {
		//long t0 = System.currentTimeMillis();

		if (mUpdateFtsStmt == null) {
			// Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
			String sql = TBL_BOOKS_FTS.getUpdate(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_NOTES, 
												DOM_PUBLISHER, DOM_GENRE, DOM_LOCATION, DOM_ISBN)
												+ " Where " + DOM_DOCID + " = ?";
			mUpdateFtsStmt = mStatements.add("mUpdateFtsStmt", sql);
		}
		BooksCursor books = null;

		SyncLock l = null;
		if (!mDb.inTransaction())
			l = mDb.beginTransaction(true);
		try {
			// Compile statement and get cursor
			books = fetchBooks("select * from " + TBL_BOOKS + " where " + DOM_ID + " = " + bookId, EMPTY_STRING_ARRAY);
			ftsSendBooks(books, mUpdateFtsStmt);
			if (l != null)
				mDb.setTransactionSuccessful();
		} finally {
			// Cleanup
			if (books != null)
				try { books.close(); } catch (Exception ignored) {}
            if (l != null)
				mDb.endTransaction(l);
			//long t1 = System.currentTimeMillis();
			//System.out.println("Updated FTS in " + (t1-t0) + "ms");
		}
	}

	/**
	 * Delete an existing FTS record
	 * 
	 * @param bookId
	 */
	private SynchronizedStatement mDeleteFtsStmt = null;
	private void deleteFts(long bookId) {
		//long t0 = System.currentTimeMillis();
		if (mDeleteFtsStmt == null) {
			String sql = "Delete from " + TBL_BOOKS_FTS + " Where " + DOM_DOCID + " = ?";
			mDeleteFtsStmt = mStatements.add("mDeleteFtsStmt", sql);
		}
		mDeleteFtsStmt.bindLong(1, bookId);
		mDeleteFtsStmt.execute();
		//long t1 = System.currentTimeMillis();
		//System.out.println("Deleted from FTS in " + (t1-t0) + "ms");
	}
	
	/**
	 * Rebuild the entire FTS database. This can take several seconds with many books or a slow phone.
	 * 
	 */
	public void rebuildFts() {
		long t0 = System.currentTimeMillis();
		boolean gotError = false;

		// Make a copy of the FTS table definition for our temp table.
		TableDefinition ftsTemp = TBL_BOOKS_FTS.clone();
		// Give it a new name
		ftsTemp.setName(ftsTemp.getName() + "_temp");

		SynchronizedStatement insert = null;
		BooksCursor c = null;

		SyncLock l = null;
		if (!mDb.inTransaction())
			l = mDb.beginTransaction(true);
		try {
			// Drop and recreate our temp copy
			ftsTemp.drop(mDb);
			ftsTemp.create(mDb, false);

			// Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
			final String sql = ftsTemp.getInsert(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_NOTES, DOM_PUBLISHER, DOM_GENRE, DOM_LOCATION, DOM_ISBN, DOM_DOCID)
							+ " values (?,?,?,?,?,?,?,?,?)";

			// Compile an INSERT statement
			insert = mDb.compileStatement(sql);

			// Get ALL books in the DB
			c = fetchBooks("select * from " + TBL_BOOKS, EMPTY_STRING_ARRAY);
			// Send each book
			ftsSendBooks(c, insert);
			// Drop old table, ready for rename
			TBL_BOOKS_FTS.drop(mDb);
			// Done
			if (l != null)
				mDb.setTransactionSuccessful();
		} catch (Exception e) {
			Logger.logError(e);
			gotError = true;
		} finally {
			// Cleanup
			if (c != null) 
				try { c.close(); } catch (Exception ignored) {}
            if (insert != null)
				try { insert.close(); } catch (Exception ignored) {}
            if (l != null)
				mDb.endTransaction(l);

			// According to this:
			//
			//    http://old.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td29474500.html
			//
			// FTS tables should only be renamed outside of transactions. Which is a pain.
			//
			// Delete old table and rename the new table
			//
			if (!gotError)
				mDb.execSQL("Alter Table " + ftsTemp + " rename to " + TBL_BOOKS_FTS);
		}

		if (DEBUG && BuildConfig.DEBUG) {
			long t1 = System.currentTimeMillis();
			System.out.println("books reindexed in " + (t1 - t0) + "ms");
		}
	}

	/**
	 * Utility function to bind a string or NULL value to a parameter since binding a NULL
	 * in bindString produces an error.
	 * 
	 * NOTE: We specifically want to use the default locale for this.
	 */
	private void bindStringOrNull(SynchronizedStatement stmt, int position, String s) {
		if (s == null) {
			stmt.bindNull(position);
		} else {
			//
			// Because FTS does not understand locales in all android up to 4.2,
			// we do case folding here using the default locale.
			//
			stmt.bindString(position, s.toLowerCase(Locale.getDefault()));
		}
	}

	/**
	 * Cleanup a search string to remove all quotes etc.
	 * 
	 * Remove punctuation from the search string to TRY to match the tokenizer. The only punctuation we
	 * allow is a hypen preceded by a space. Everything else is translated to a space.
	 * 
	 * TODO: Consider making '*' to the end of all words a preference item.
	 * 
	 * @param search	Search criteria to clean
	 * @return			Clean string
	 */
	public static String cleanupFtsCriterion(String search) {
		//return s.replace("'", " ").replace("\"", " ").trim();

		if (search.isEmpty())
			return search;

		// Output bufgfer
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
	 * @param anywhere		Keywords to find anywhere in book
	 * @return				a cursor
	 */
	public Cursor searchFts(String author, String title, String anywhere) {
		author = cleanupFtsCriterion(author);
		title = cleanupFtsCriterion(title);
		anywhere = cleanupFtsCriterion(anywhere);

		if ( (author.length() + title.length() + anywhere.length()) == 0)
			return null;

		String[] authorWords = author.split(" ");
		String[] titleWords = title.split(" ");

		StringBuilder sql = new StringBuilder("select " + DOM_DOCID + " from " + TBL_BOOKS_FTS + " where " + TBL_BOOKS_FTS + " match '" + anywhere);
		for(String w : authorWords) {
			if (!w.isEmpty())
				sql.append(" ").append(DOM_AUTHOR_NAME).append(":").append(w);
		}
		for(String w : titleWords) {
			if (!w.isEmpty())
				sql.append(" ").append(DOM_TITLE).append(":").append(w);
		}
		sql.append("'");

		return mDb.rawQuery(sql.toString(), EMPTY_STRING_ARRAY);
	}
	
	/**
	 * Get the local database.
	 * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
	 * 
	 * @return	Database connection
	 */
	public SynchronizedDb getDb() {
		if (mDb == null || !mDb.isOpen())
			this.open();
		return mDb;
	}

	/**
	 * Get the synchronizer object for this database in case there is some other activity that needs to
	 * be synced.
	 * 
	 * Note: Cursor requery() is the only thing found so far.
	 */
	public static Synchronizer getSynchronizer() {
		return mSynchronizer;
	}

	/**
	 * Return a boolean indicating this instance was a new installation
	 */
	public boolean isNewInstall() {
		return mDbHelper.isNewInstall();
	}
	
	public Cursor getUuidList() {
		String sql = "select " + DatabaseDefinitions.DOM_BOOK_UUID + " as " + DatabaseDefinitions.DOM_BOOK_UUID + " From " + DatabaseDefinitions.TBL_BOOKS.ref();
		return mDb.rawQuery(sql);
	}
	
	public long getBookCount() {
		String sql = "select Count(*) From " + DatabaseDefinitions.TBL_BOOKS.ref();
		try (Cursor c = mDb.rawQuery(sql)) {
			c.moveToFirst();
			return c.getLong(0);
		}
	}
	
	/**
	 * DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
	 * there are still non-fatal anomalies.
	 */
	public static void printReferenceCount(String msg) {
		if (DEBUG && BuildConfig.DEBUG) {
			if (mDb != null) {
                SynchronizedDb.printRefCount(msg, mDb.getUnderlyingDatabase());
            }
		}
    }
}


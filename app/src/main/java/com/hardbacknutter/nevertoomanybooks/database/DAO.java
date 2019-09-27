/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.AutoCompleteTextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.database.cursors.TrackedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Format;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.CurrencyUtils;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FAMILY_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_GIVEN_NAMES_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GOODREADS_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISFDB_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PAGES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PRICE_PAID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_STYLE_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FTS_AUTHOR_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_DOCID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TOC_TYPE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS_FTS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Database access helper class.
 * <p>
 * This class is 'context-free'. KEEP IT THAT WAY. Passing in a context is fine, but NO caching.
 * We need to use this in background tasks and ViewModel classes.
 * Using {@link App#getLocalizedAppContext}} or {@link App#getAppContext} is however allowed.
 *
 * <p>
 * insert:
 * * return new id, or -1 for error.
 * <p>
 * update:
 * * return rows affected, can be 0.
 * <p>
 * updateOrInsert:
 * * return true for success (either insert or update with rowsAffected > 0)
 * <p>
 * TODO: caching of statements forces synchronisation ... is it worth it ?
 * There is an explicit warning that {@link SQLiteStatement} is not thread safe!
 */
public class DAO
        implements AutoCloseable {

    /**
     * Flag indicating the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
    public static final int BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT = 1;

    /**
     * In addition to SQLite's default BINARY collator (others: NOCASE and RTRIM),
     * Android supplies two more.
     * LOCALIZED: using the system's current Locale,
     * UNICODE  : Unicode Collation Algorithm and not tailored to the current Locale.
     * <p>
     * We tried 'Collate UNICODE' but it seemed to be case sensitive.
     * We ended up with 'Ursula Le Guin' and 'Ursula le Guin'.
     * <p>
     * We now use Collate LOCALE and check to see if it is case sensitive.
     * We *hope* in the future Android will add LOCALE_CI (or equivalent).
     * <p>
     * public static final String COLLATION = " Collate NOCASE ";
     * public static final String COLLATION = " Collate UNICODE ";
     * <p>
     * <strong>Note:</strong> Important to have start/end spaces!
     */
    public static final String COLLATION = " Collate LOCALIZED ";
    /** log error string. */
    private static final String ERROR_FAILED_CREATING_BOOK_FROM = "Failed creating book from\n";

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create our custom cursor. */
    private static final CursorFactory CURSOR_FACTORY =
            (db, masterQuery, editTable, query) ->
                    new TrackedCursor(masterQuery, editTable, query, SYNCHRONIZER);

    /** Static Factory object to create our custom cursor. */
    @NonNull
    private static final CursorFactory BOOKS_CURSOR_FACTORY =
            (db, masterQuery, editTable, query) ->
                    new BookCursor(masterQuery, editTable, query, SYNCHRONIZER);

    /** DEBUG only. */
    private static final ArrayList<InstanceRefDebug> INSTANCES = new ArrayList<>();
    /** DEBUG instance counter. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNT = new AtomicInteger();
    /** Column alias. */
    private static final String COLUMN_ALIAS_NR_OF_SERIES = "_num_series";
    /** Column alias. */
    private static final String COLUMN_ALIAS_NR_OF_AUTHORS = "_num_authors";
    /** statement names; keys into the cache map. */
    private static final String STMT_CHECK_BOOK_EXISTS = "CheckBookExists";
    private static final String STMT_GET_AUTHOR_ID = "GetAuthorId";
    private static final String STMT_GET_SERIES_ID = "GetSeriesId";
    private static final String STMT_GET_TOC_ENTRY_ID = "GetTOCEntryId";
    private static final String STMT_GET_BOOK_ISBN = "GetBookIsbn";
    private static final String STMT_GET_BOOK_TITLE = "GetBookTitle";
    private static final String STMT_GET_BOOK_UPDATE_DATE = "GetBookUpdateDate";
    private static final String STMT_GET_BOOK_UUID = "GetBookUuid";
    private static final String STMT_GET_BOOK_ID_FROM_ISBN_2 = "GetIdFromIsbn2";
    private static final String STMT_GET_BOOK_ID_FROM_ISBN_1 = "GetIdFromIsbn1";
    private static final String STMT_GET_BOOK_ID_FROM_UUID = "GetBookIdFromUuid";
    private static final String STMT_GET_BOOKSHELF_ID_BY_NAME = "GetBookshelfIdByName";
    private static final String STMT_GET_LOANEE_BY_BOOK_ID = "GetLoaneeByBookId";
    private static final String STMT_GET_BOOKLIST_STYLE = "GetBooklistStyle";
    private static final String STMT_INSERT_BOOK_SERIES = "InsertBookSeries";
    private static final String STMT_INSERT_BOOK_TOC_ENTRY = "InsertBookTOCEntry";
    private static final String STMT_INSERT_BOOK_AUTHORS = "InsertBookAuthors";
    private static final String STMT_INSERT_BOOK_BOOKSHELF = "InsertBookBookshelf";
    private static final String STMT_INSERT_AUTHOR = "InsertAuthor";
    private static final String STMT_INSERT_TOC_ENTRY = "InsertTOCEntry";
    private static final String STMT_INSERT_FTS = "InsertFts";
    private static final String STMT_INSERT_SERIES = "InsertSeries";
    private static final String STMT_DELETE_BOOK = "DeleteBook";
    private static final String STMT_DELETE_SERIES = "DeleteSeries";
    private static final String STMT_DELETE_TOC_ENTRY = "DeleteTocEntry";
    private static final String STMT_DELETE_BOOK_TOC_ENTRIES = "DeleteBookTocEntries";
    private static final String STMT_DELETE_BOOK_AUTHORS = "DeleteBookAuthors";
    private static final String STMT_DELETE_BOOK_BOOKSHELF = "DeleteBookBookshelf";
    private static final String STMT_DELETE_BOOK_SERIES = "DeleteBookSeries";
    private static final String STMT_UPDATE_GOODREADS_BOOK_ID = "UpdateGoodreadsBookId";
    private static final String STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES = "UpdateAuthorOnTocEntry";
    private static final String STMT_UPDATE_GOODREADS_SYNC_DATE = "UpdateGoodreadsSyncDate";
    private static final String STMT_UPDATE_FTS = "UpdateFts";
    /** log error string. */
    private static final String ERROR_NEEDS_TRANSACTION = "Needs transaction";
    /** log error string. */
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to update FTS";
    /** See {@link #encodeString(String)}. */
    private static final Pattern ENCODE_STRING = Pattern.compile("'", Pattern.LITERAL);
    /** any non-word character. */
    private static final Pattern ENCODE_ORDERBY_PATTERN = Pattern.compile("\\W");
    private static final Pattern ASCII_REGEX = Pattern.compile("[^\\p{ASCII}]");
    /** Actual SQLiteOpenHelper. */
    private static DBHelper sDbHelper;
    /** Synchronization wrapper around the real database. */
    private static SynchronizedDb sSyncedDb;

    // curiosity... check when the JDK loads this class.
    static {
        Logger.debug(DAO.class, "DAO static init");
    }

    /** a cache for statements, where they are pre-compiled. */
    private final SqlStatementManager mStatements;
    /** used by finalize so close does not get called twice. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     * <p>
     * <strong>Note:</strong> don't be tempted to turn this into a singleton...
     * this class is not fully thread safe (in contrast to the covers dba which is).
     */
    public DAO() {
        // the SQLiteOpenHelper
        if (sDbHelper == null) {
            sDbHelper = DBHelper.getInstance(CURSOR_FACTORY, SYNCHRONIZER);
        }

        // Get the DB wrapper
        if (sSyncedDb == null) {
            sSyncedDb = new SynchronizedDb(sDbHelper, SYNCHRONIZER);
        }

        // statements are instance based/managed
        mStatements = new SqlStatementManager(sSyncedDb);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_INSTANCE_COUNT) {
            debugAddInstance(this);
        }
    }

    /**
     * Get the Synchronizer object for this database in case there is some other activity
     * that needs to be synced.
     * <p>
     * <strong>Note:</strong> {@link Cursor#requery()} is the only thing found so far.
     *
     * @return the synchronizer
     */
    @NonNull
    public static Synchronizer getSynchronizer() {
        return SYNCHRONIZER;
    }

    /**
     * Escape single quotation marks by doubling them (standard SQL escape).
     *
     * @param value to encode
     *
     * @return escaped value.
     * <p>
     * <strong>Note:</strong> Using the compiled pattern is theoretically faster than using
     * {@link String#replace(CharSequence, CharSequence)}.
     */
    @NonNull
    public static String encodeString(@NonNull final String value) {
        return ENCODE_STRING.matcher(value).replaceAll(Matcher.quoteReplacement("''"));
    }

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book: strip spaces etc, make lowercase,...
     *
     * @param value  to encode
     * @param locale to use for case manipulation
     *
     * @return the encoded value
     */
    static String encodeOrderByColumn(@NonNull final String value,
                                      @NonNull final Locale locale) {

        // remove all non-word characters. i.e. all characters not in [a-zA-Z_0-9]
        return ENCODE_ORDERBY_PATTERN.matcher(value).replaceAll("").toLowerCase(locale);
    }

    /**
     * DEBUG only.
     *
     * @param db Database Access
     */
    private static void debugAddInstance(@NonNull final DAO db) {
        Logger.debug(db, "debugAddInstance",
                     "count=" + DEBUG_INSTANCE_COUNT.incrementAndGet());

        INSTANCES.add(new InstanceRefDebug(db));

    }

    /**
     * DEBUG only.
     *
     * @param db Database Access
     */
    private static void debugRemoveInstance(@NonNull final DAO db) {
        Logger.debug(db, "debugRemoveInstance",
                     "count=" + DEBUG_INSTANCE_COUNT.decrementAndGet());

        Iterator<InstanceRefDebug> it = INSTANCES.iterator();
        while (it.hasNext()) {
            InstanceRefDebug ref = it.next();
            DAO refDb = ref.get();
            if (refDb == null) {
                Logger.debug(DAO.class, "debugRemoveInstance",
                             "**** Missing ref (not closed?) ****", ref);
            } else {
                if (refDb == db) {
                    it.remove();
                }
            }
        }

    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    public static void debugDumpInstances() {
        for (InstanceRefDebug ref : INSTANCES) {
            if (ref.get() == null) {
                Logger.debug(DAO.class, "debugDumpInstances",
                             "**** Missing ref (not closed?) ****", ref);
            } else {
                Logger.debug(DAO.class, "debugDumpInstances", ref);
            }
        }
    }

    /**
     * DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
     * there are still non-fatal anomalies.
     */
    @SuppressWarnings("unused")
    public static void debugPrintReferenceCount(@Nullable final String message) {
        if (sSyncedDb != null) {
            SynchronizedDb.printRefCount(message, sSyncedDb.getUnderlyingDatabase());
        }
    }

    /**
     * Cleanup a search string to remove all quotes etc.
     * <p>
     * Remove punctuation from the search string to TRY to match the tokenizer.
     * The only punctuation we allow is a hyphen preceded by a space => negate the next word.
     * Everything else is translated to a space.
     *
     * @param searchText Search criteria to clean
     * @param domain     (optional) domain to prefix the searchText or {@code null} for none
     *
     * @return Clean string
     */
    @NonNull
    private String cleanupFtsCriterion(@Nullable final String searchText,
                                       @Nullable final DomainDefinition domain) {

        if (searchText == null || searchText.isEmpty()) {
            return "";
        }

        // Convert the text to pure ASCII. We'll use an array to loop over it.
        final char[] chars = toAscii(searchText).toCharArray();
        // Cached length
        final int len = chars.length;
        // Initial position
        int pos = 0;
        // Dummy 'previous' character
        char prev = ' ';

        // Output buffer
        final StringBuilder parameter = new StringBuilder();

        // Loop over array
        while (pos < len) {
            char current = chars[pos];
            // If current is letter or ...use it.
            if (Character.isLetterOrDigit(current)) {
                parameter.append(current);
            } else if (current == '-' && Character.isWhitespace(prev)) {
                // Allow negation if preceded by space
                parameter.append(current);
            } else {
                // Everything else is whitespace
                current = ' ';
                if (!Character.isWhitespace(prev)) {
                    // If prev character was non-ws, and not negation, make wildcard
                    if (prev != '-') {
                        // Make every token a wildcard
                        parameter.append('*');
                    }
                    // Append a whitespace only when last char was not a whitespace
                    parameter.append(' ');
                }
            }
            prev = current;
            pos++;
        }
        if (!Character.isWhitespace(prev) && (prev != '-')) {
            // Make every token a wildcard
            parameter.append('*');
        }

        String cleanedText = parameter.toString().trim();

        // finally create the actual parameter String optionally with the domain name.
        if (domain == null) {
            return cleanedText;

        } else {
            StringBuilder result = new StringBuilder();
            for (String word : cleanedText.split(" ")) {
                if (!word.isEmpty()) {
                    result.append(' ').append(domain).append(':').append(word);
                }
            }
            return result.toString();
        }
    }


    /**
     * @param author      Author related keywords to find
     * @param title       Title related keywords to find
     * @param seriesTitle Series title related keywords to find
     * @param keywords    Keywords to find anywhere in book; this includes titles and authors
     *
     * @return an SQL query string suited to search FTS for the specified parameters,
     * or {@code null} if all input was empty
     */
    @Nullable
    public String getFtsSearchSQL(@Nullable final String author,
                                  @Nullable final String title,
                                  @Nullable final String seriesTitle,
                                  @Nullable final String keywords) {

        StringBuilder parameters = new StringBuilder()
                .append(cleanupFtsCriterion(keywords, null))
                .append(cleanupFtsCriterion(author, DOM_FTS_AUTHOR_NAME))
                .append(cleanupFtsCriterion(title, DOM_TITLE))
                .append(cleanupFtsCriterion(seriesTitle, DOM_SERIES_TITLE));

        // do we have anything to search for?
        if (parameters.length() == 0) {
            return null;
        }

        return "SELECT " + DOM_PK_DOCID
               + " FROM " + TBL_BOOKS_FTS
               + " WHERE " + TBL_BOOKS_FTS
               + " MATCH '" + parameters.toString().trim() + '\'';
    }

    public void recreateTriggers() {
        sDbHelper.createTriggers(sSyncedDb);
    }

    public void analyze() {
        sSyncedDb.analyze();
    }

    /**
     * Get the local database.
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return Database connection
     */
    @NonNull
    public SynchronizedDb getUnderlyingDatabase() {
        return sSyncedDb;
    }

    /**
     * Set the Goodreads sync date to the current time.
     *
     * @param bookId the book
     */
    public void setGoodreadsSyncDate(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_GOODREADS_SYNC_DATE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_GOODREADS_SYNC_DATE,
                                   SqlUpdate.GOODREADS_LAST_SYNC_DATE);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Generic function to close the database.
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements
     * <p>
     * So it should really be called cleanup()
     * But it allows us to use try-with-resources.
     * <p>
     * Consequently, there is no need to 'open' anything before running further operations.
     */
    @Override
    public void close() {
        if (mStatements != null) {
            // the close() will perform a clear, ready to be re-used.
            mStatements.close();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_INSTANCE_COUNT) {
            debugRemoveInstance(this);
        }
        mCloseWasCalled = true;
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseWasCalled) {
            Logger.warn(this, "finalize",
                        "Leaking instances: " + DEBUG_INSTANCE_COUNT.get());
            close();
        }
        super.finalize();
    }

    /**
     * Used by {@link CsvImporter}.
     */
    @NonNull
    public SyncLock startTransaction(final boolean isUpdate) {
        return sSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public void endTransaction(@NonNull final SyncLock txLock) {
        sSyncedDb.endTransaction(txLock);
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public boolean inTransaction() {
        return sSyncedDb.inTransaction();
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public void setTransactionSuccessful() {
        sSyncedDb.setTransactionSuccessful();
    }

    /**
     * Delete an individual TocEntry.
     *
     * @param id to delete.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteTocEntry(final long id) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_TOC_ENTRY);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_TOC_ENTRY, SqlDelete.TOC_ENTRY);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, id);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Delete the link between TocEntry's and the given Book.
     * Note that the actual TocEntry's are NOT deleted here.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookTocEntryByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_TOC_ENTRIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_TOC_ENTRIES,
                                   SqlDelete.BOOK_TOC_ENTRIES_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Return the TocEntry id. The incoming object is not modified.
     * (note that publication year is NOT used for comparing, under the assumption that
     * two search-sources can give different dates by mistake).
     *
     * @param context    Current context
     * @param tocEntry   tocEntry to search for
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getTocEntryId(@NonNull final Context context,
                              @NonNull final TocEntry tocEntry,
                              @NonNull final Locale bookLocale) {

        Locale tocLocale = tocEntry.getLocale(bookLocale);

        SynchronizedStatement stmt = mStatements.get(STMT_GET_TOC_ENTRY_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_TOC_ENTRY_ID, SqlGet.TOC_ENTRY_ID);
        }

        String obTitle = tocEntry.preprocessTitle(context, tocLocale);

        // Be cautious; other threads may use the cached stmt, and set parameters.
        // the check of preprocessTitle is unconditional as it's an OR.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, tocEntry.getAuthor().getId());
            stmt.bindString(2, encodeOrderByColumn(tocEntry.getTitle(), tocLocale));
            stmt.bindString(3, encodeOrderByColumn(obTitle, tocLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Return all the {@link TocEntry} for the given {@link Author}.
     *
     * @param author         to retrieve
     * @param withTocEntries add the toc entries
     * @param withBooks      add books without TOC as well; i.e. the toc of a book without a toc,
     *                       is the book title itself. (makes sense?)
     *
     * @return List of {@link TocEntry} for this {@link Author}
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByAuthor(@NonNull final Author author,
                                                   final boolean withTocEntries,
                                                   final boolean withBooks) {

        // rawQuery wants String[] as bind parameters
        String authorIdStr = String.valueOf(author.getId());
        String sql;
        String[] params;

        if (withBooks && withTocEntries) {
            sql = SqlSelectList.WORKS_BY_AUTHOR_ID;
            params = new String[]{authorIdStr, authorIdStr};

        } else if (withTocEntries) {
            sql = SqlSelectList.TOC_ENTRIES_BY_AUTHOR_ID;
            params = new String[]{authorIdStr};

        } else if (withBooks) {
            sql = SqlSelectList.BOOK_TITLES_BY_AUTHOR_ID;
            params = new String[]{authorIdStr};

        } else {
            throw new IllegalArgumentException("Must specify what to fetch");
        }

        sql += " ORDER BY " + DOM_TITLE_OB + COLLATION;

        ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(sql, params)) {
            if (cursor.getCount() == 0) {
                return list;
            }

            CursorMapper mapper = new CursorMapper(cursor);

            while (cursor.moveToNext()) {
                TocEntry tocEntry =
                        new TocEntry(mapper.getLong(DOM_PK_ID.getName()),
                                     author,
                                     mapper.getString(DOM_TITLE.getName()),
                                     mapper.getString(DOM_DATE_FIRST_PUBLICATION.getName()),
                                     mapper.getString(DOM_TOC_TYPE.getName()).charAt(0),
                                     mapper.getInt(DOM_BL_BOOK_COUNT.getName()));
                list.add(tocEntry);
            }
        }
        return list;
    }

    /**
     * Creates a new {@link Author}.
     *
     * @param author object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted Author, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertAuthor(@NonNull final Author /* in/out */ author) {

        Locale authorLocale = author.getLocale(Locale.getDefault());

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_AUTHOR);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_AUTHOR, SqlInsert.AUTHOR);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, author.getFamilyName());
            stmt.bindString(2, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(3, author.getGivenNames());
            stmt.bindString(4, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            stmt.bindLong(5, author.isComplete() ? 1 : 0);
            long iId = stmt.executeInsert();
            if (iId > 0) {
                author.setId(iId);
            }
            return iId;
        }
    }

    /**
     * @param author to update
     *
     * @return rows affected, should be 1 for success
     */
    private int updateAuthor(@NonNull final Author author) {

        Locale authorLocale = author.getLocale(Locale.getDefault());

        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_FAMILY_NAME.getName(), author.getFamilyName());
        cv.put(DOM_AUTHOR_FAMILY_NAME_OB.getName(),
               encodeOrderByColumn(author.getFamilyName(), authorLocale));
        cv.put(DOM_AUTHOR_GIVEN_NAMES.getName(), author.getGivenNames());
        cv.put(DOM_AUTHOR_GIVEN_NAMES_OB.getName(),
               encodeOrderByColumn(author.getGivenNames(), authorLocale));
        cv.put(DOM_AUTHOR_IS_COMPLETE.getName(), author.isComplete());

        return sSyncedDb.update(TBL_AUTHORS.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(author.getId())});
    }

    /**
     * Add or update the passed {@link Author}, depending whether author.id == 0.
     *
     * @param context Current context
     * @param author  object to insert or update. Will be updated with the id.
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertAuthor(@NonNull final Context context,
                                        @NonNull final /* in/out */ Author author) {

        if (author.getId() != 0) {
            return updateAuthor(author) > 0;
        } else {
            // try to find first.
            if (author.fixId(context, this, Locale.getDefault()) == 0) {
                return insertAuthor(author) > 0;
            }
        }
        return false;
    }

    /**
     * Get all Authors; mainly for the purpose of backups.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    public Cursor fetchAuthors() {
        return sSyncedDb.rawQuery(SqlSelectFullTable.AUTHORS, null);
    }

    /**
     * Get the {@link Author} based on the ID.
     *
     * @return the author, or {@code null} if not found
     */
    @Nullable
    public Author getAuthor(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.AUTHOR_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            CursorMapper mapper = new CursorMapper(cursor);
            if (cursor.moveToFirst()) {
                return new Author(id, mapper);
            } else {
                return null;
            }
        }
    }

    /**
     * Find an Author, and return its ID. The incoming object is not modified.
     *
     * @param author to find the id of
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getAuthorId(@NonNull final Author author,
                            @NonNull final Locale userLocale) {

        Locale authorLocale = author.getLocale(userLocale);

        SynchronizedStatement stmt = mStatements.get(STMT_GET_AUTHOR_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_AUTHOR_ID, SqlGet.AUTHOR_ID_BY_NAME);
        }

        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(2, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Refresh the passed Author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Author.
     * <p>
     * Will NOT insert a new Author if not found.
     *
     * @param context Current context
     * @param author  to refresh
     */
    public void refreshAuthor(@NonNull final Context context,
                              @NonNull final Author /* out */ author) {

        if (author.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            author.fixId(context, this, Locale.getDefault());

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            Author dbAuthor = getAuthor(author.getId());
            if (dbAuthor != null) {
                // copy any updated fields
                author.copyFrom(dbAuthor, false);
            } else {
                // not found?, set as 'new'
                author.setId(0);
            }
        }
    }

    /**
     * Globally replace the Author data. This does <strong>not</strong> copy book related fields.
     *
     * @param context Current context
     * @param from    Author to replace
     * @param to      Author to use
     *
     * @return {@code true} for success.
     */
    public boolean globalReplace(@NonNull final Context context,
                                 @NonNull final Author from,
                                 @NonNull final Author to) {

        // process the destination Author
        if (!updateOrInsertAuthor(context, to)) {
            Logger.warnWithStackTrace(this, "Could not update", "author=" + to);
            return false;
        }

        // Do some basic sanity checks.
        if (from.getId() == 0 && from.fixId(context, this, Locale.getDefault()) == 0) {
            Logger.warnWithStackTrace(this, "Old Author is not defined");
            return false;
        }

        if (from.getId() == to.getId()) {
            return true;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_GLOBAL_REPLACE) {
            Logger.debug(this, "globalReplaceAuthor",
                         "from=" + from.getId(), "to=" + to.getId());
        }

        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            // replace the old id with the new id on the TOC entries
            updateAuthorOnTocEntry(from.getId(), to.getId());

            // update books for which the new id is not already present
            globalReplaceId(TBL_BOOK_AUTHOR, DOM_FK_AUTHOR, from.getId(), to.getId());

            globalReplacePositionedBookItem(TBL_BOOK_AUTHOR,
                                            DOM_FK_AUTHOR,
                                            DOM_BOOK_AUTHOR_POSITION,
                                            from.getId(), to.getId());

            sSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            return false;
        } finally {
            sSyncedDb.endTransaction(txLock);
        }
        return true;
    }

    /**
     * Update the author id on TocEntry's.
     *
     * @param from source id
     * @param to   destination id
     *
     * @return rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int updateAuthorOnTocEntry(final long from,
                                       final long to) {
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES,
                                   SqlUpdate.AUTHOR_ON_TOC_ENTRIES);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, to);
            stmt.bindLong(2, from);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Get authors names for filling an {@link AutoCompleteTextView}.
     *
     * @param key type of name wanted, one of
     *            {@link DBDefinitions#KEY_AUTHOR_FAMILY_NAME},
     *            {@link DBDefinitions#KEY_AUTHOR_GIVEN_NAMES},
     *            {@link DBDefinitions#KEY_AUTHOR_FORMATTED},
     *            {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST}
     *
     * @return list of all author names.
     */
    @NonNull
    public ArrayList<String> getAuthorNames(@NonNull final String key) {
        switch (key) {
            case KEY_AUTHOR_FAMILY_NAME:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_FAMILY_NAMES, key);

            case KEY_AUTHOR_GIVEN_NAMES:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_GIVEN_NAMES, key);

            case KEY_AUTHOR_FORMATTED:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_FORMATTED_NAMES, key);

            case KEY_AUTHOR_FORMATTED_GIVEN_FIRST:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_FORMATTED_NAMES_GIVEN_FIRST, key);

            default:
                throw new UnexpectedValueException(key);
        }
    }

    /**
     * Purge anything that is no longer in use.
     * <p>
     * Purging is no longer done at every occasion where it *might* be needed.
     * It was noticed (in the logs) that it was done far to often.
     * It is now called only:
     * <p>
     * Before a backup (but not before a CSV export)
     * After an import of data; includes after Archive, CSV, XML imports.
     * <p>
     * So orphaned data will stay around a little longer which in fact may be beneficial
     * while entering/correcting a book collection.
     */
    public void purge() {
        // Note: purging TocEntry's is automatic due to foreign key cascading.
        // i.e. a TocEntry is linked directly with authors; and linked with books via a link table.
        purgeAuthors();
        purgeSeries();

        analyze();
    }

    /**
     * Delete all Authors without related books / TocEntry's.
     */
    private void purgeAuthors() {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.PURGE_AUTHORS)) {
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Delete all Series without related books.
     */
    private void purgeSeries() {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.PURGE_SERIES)) {
            stmt.executeUpdateDelete();
        }
    }

    /**
     * @param context Current context
     * @param author  to retrieve
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countBooksByAuthor(@NonNull final Context context,
                                   @NonNull final Author author) {
        if (author.getId() == 0 && author.fixId(context, this, Locale.getDefault()) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt =
                     sSyncedDb.compileStatement(SqlSelect.COUNT_BOOKS_BY_AUTHOR)) {
            stmt.bindLong(1, author.getId());
            return stmt.count();
        }
    }

    /**
     * @param context Current context
     * @param author  to count the TocEntries of
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    public long countTocEntryByAuthor(@NonNull final Context context,
                                      @NonNull final Author author) {
        if (author.getId() == 0 && author.fixId(context, this, Locale.getDefault()) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt =
                     sSyncedDb.compileStatement(SqlSelect.COUNT_TOC_ENTRIES_BY_AUTHOR)) {
            stmt.bindLong(1, author.getId());
            return stmt.count();
        }
    }

    /**
     * Examine the values and make any changes necessary before writing the data.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void preprocessBook(@NonNull final Context context,
                                @NonNull final Book book) {

        Locale bookLocale = book.getLocale();

        // Handle AUTHOR. When is this needed? Legacy archive import ?
        if (book.containsKey(KEY_AUTHOR_FORMATTED)
            || book.containsKey(KEY_AUTHOR_FAMILY_NAME)) {
            preprocessLegacyAuthor(context, book);
        }

        // Handle TITLE
        if (book.containsKey(DBDefinitions.KEY_TITLE)) {
            String obTitle = book.preprocessTitle(context, bookLocale);
            book.putString(DOM_TITLE_OB.getName(), encodeOrderByColumn(obTitle, bookLocale));
        }

        // Handle TOC_BITMASK only, no handling of actual titles here,
        // making sure TOC_MULTIPLE_AUTHORS is correct.
        ArrayList<TocEntry> tocEntries = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
        if (!tocEntries.isEmpty()) {
            long type = book.getLong(DBDefinitions.KEY_TOC_BITMASK);
            if (TocEntry.hasMultipleAuthors(tocEntries)) {
                type |= Book.TOC_MULTIPLE_AUTHORS;
            }
            book.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        // Handle all price related fields.
        preprocessPrices(book);

        // Map website formats to standard ones if enabled by the user.
        if (book.containsKey(DBDefinitions.KEY_FORMAT)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_reformat_formats, false)) {
            String format = Format.map(context, book.getString(DBDefinitions.KEY_FORMAT));
            book.putString(DBDefinitions.KEY_FORMAT, format);
        }

        // Remove blank external ID's
        for (DomainDefinition domain : new DomainDefinition[]{
                DBDefinitions.DOM_ASIN,
                DOM_BOOK_ISFDB_ID,
                DOM_BOOK_OPEN_LIBRARY_ID,
                DOM_BOOK_LIBRARY_THING_ID,
                DOM_BOOK_GOODREADS_ID,
                }) {
            if (book.containsKey(domain.getName())) {
                switch (domain.getType()) {
                    case ColumnInfo.TYPE_INTEGER:
                        long v = book.getLong(domain.getName());
                        if (v < 1) {
                            book.remove(domain.getName());
                        }
                        break;

                    case ColumnInfo.TYPE_TEXT:
                        Object o = book.get(domain.getName());
                        if (o == null || o.toString().isEmpty()) {
                            book.remove(domain.getName());
                        }
                        break;

                    default:
                        Logger.warnWithStackTrace(this, "type=" + domain.getType());
                        break;
                }
            }
        }

        // Remove {@code null} fields that should never be {@code null}.
        for (String name : new String[]{
                //ENHANCE: can we automate this list ? maybe by looping over the table def. ?
                // Basically we want "NOT NULL" fields which have STRING default.
                DBDefinitions.KEY_ISBN,
                DBDefinitions.KEY_PUBLISHER,
                DBDefinitions.KEY_DATE_PUBLISHED,
                DBDefinitions.KEY_DATE_FIRST_PUBLICATION,

                DBDefinitions.KEY_PRICE_LISTED,
                DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                DBDefinitions.KEY_PRICE_PAID,
                DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                DBDefinitions.KEY_DATE_ACQUIRED,

                DBDefinitions.KEY_FORMAT,
                DBDefinitions.KEY_GENRE,
                DBDefinitions.KEY_LANGUAGE,
                DBDefinitions.KEY_LOCATION,
                DBDefinitions.KEY_PAGES,

                DBDefinitions.KEY_READ_START,
                DBDefinitions.KEY_READ_END,

                DBDefinitions.KEY_DESCRIPTION,
                DBDefinitions.KEY_NOTES,
                }) {
            if (book.containsKey(name)) {
                if (book.get(name) == null) {
                    book.remove(name);
                }
            }
        }

        // Remove {@code null}/blank fields that should never be {@code null}/blank.
        for (String name : new String[]{
                // auto-generated in the database
                DBDefinitions.KEY_BOOK_UUID,
                // number
                DBDefinitions.KEY_EDITION_BITMASK,
                DBDefinitions.KEY_TOC_BITMASK,
                DBDefinitions.KEY_RATING,
                // boolean
                DBDefinitions.KEY_SIGNED,
                DBDefinitions.KEY_READ,
                // dates with defaults
                DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE,
                DBDefinitions.KEY_DATE_ADDED,
                DBDefinitions.KEY_DATE_LAST_UPDATED,
                }) {
            if (book.containsKey(name)) {
                Object o = book.get(name);
                if (o == null || o.toString().isEmpty()) {
                    book.remove(name);
                }
            }
        }
    }

    private void preprocessPrices(@NonNull final Book book) {

        // handle a price without a currency.
        if (book.containsKey(DBDefinitions.KEY_PRICE_LISTED)
            && !book.containsKey(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)) {
            preprocessPrice(book,
                            DBDefinitions.KEY_PRICE_LISTED,
                            DBDefinitions.KEY_PRICE_LISTED,
                            DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        }

        // handle a price without a currency.
        if (book.containsKey(DBDefinitions.KEY_PRICE_PAID)
            && !book.containsKey(DBDefinitions.KEY_PRICE_PAID_CURRENCY)) {
            // we presume the user bought the book in their own currency.
            preprocessPrice(book, DBDefinitions.KEY_PRICE_PAID,
                            DBDefinitions.KEY_PRICE_PAID,
                            DBDefinitions.KEY_PRICE_PAID_CURRENCY);
        }

        // Handle currencies making sure they are uppercase
        if (book.containsKey(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)) {
            book.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                           book.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)
                               .toUpperCase(book.getLocale()));
        }
        if (book.containsKey(DBDefinitions.KEY_PRICE_PAID_CURRENCY)) {
            book.putString(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                           book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)
                               .toUpperCase(book.getLocale()));
        }
    }

    /**
     * Preprocess a price field, splitting it into value and currency fields.
     * On any failure, no changes are made.
     * On success, the 'keyPriceWithCurrency' is removed.
     *
     * @param book                 with price fields / where to store the result.
     * @param keyPriceWithCurrency key to get the combined field
     * @param keyPrice             key to store the value
     * @param keyPriceCurrency     key to store the currency
     */
    private void preprocessPrice(@NonNull final Book book,
                                 @NonNull final String keyPriceWithCurrency,
                                 @NonNull final String keyPrice,
                                 @NonNull final String keyPriceCurrency) {
        Bundle dest = new Bundle();
        CurrencyUtils.splitPrice(book.getLocale(), book.getString(keyPriceWithCurrency),
                                 keyPrice, keyPriceCurrency, dest);
        String price = dest.getString(keyPrice);
        if (price != null) {
            book.remove(keyPriceWithCurrency);
            book.putString(keyPrice, price);
            String currency = dest.getString(keyPriceCurrency);
            book.putString(keyPriceCurrency, currency != null ? currency : "");
        }
    }

    /**
     * Needed for reading from legacy archive versions... I think?
     *
     * @param context Current context
     */
    private void preprocessLegacyAuthor(@NonNull final Context context,
                                        @NonNull final Book book) {

        // If present, get the author id from the author name
        // (it may have changed with a name change)
        if (book.containsKey(KEY_AUTHOR_FORMATTED)) {

            Author author = Author.fromString(book.getString(KEY_AUTHOR_FORMATTED));
            if (author.fixId(context, this, Locale.getDefault()) == 0) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debug(this, "preprocessLegacyAuthor",
                                 "KEY_AUTHOR_FORMATTED",
                                 "inserting author: " + author);
                }
                insertAuthor(author);
            }
            book.putLong(DOM_FK_AUTHOR.getName(), author.getId());

        } else if (book.containsKey(KEY_AUTHOR_FAMILY_NAME)) {
            String family = book.getString(KEY_AUTHOR_FAMILY_NAME);
            String given;
            if (book.containsKey(KEY_AUTHOR_GIVEN_NAMES)) {
                given = book.getString(KEY_AUTHOR_GIVEN_NAMES);
            } else {
                given = "";
            }

            Author author = new Author(family, given);
            if (author.fixId(context, this, Locale.getDefault()) == 0) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debug(this, "preprocessLegacyAuthor",
                                 "KEY_AUTHOR_FAMILY_NAME",
                                 "inserting author: " + author);
                }
                insertAuthor(author);
            }
            book.putLong(DOM_FK_AUTHOR.getName(), author.getId());
        }
    }

    /**
     * Check that a book with the passed UUID exists and return the id of the book, or zero.
     *
     * @param uuid of book
     *
     * @return id of the book, or 0 'new' if not found
     */
    public long getBookIdFromUuid(@NonNull final String uuid) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_ID_FROM_UUID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_ID_FROM_UUID, SqlGet.BOOK_ID_BY_UUID);
        }

        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /***
     * @param bookId of the book
     *
     * @return the last update date as a standard sql date string
     */
    @Nullable
    public String getBookLastUpdateDate(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_UPDATE_DATE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_UPDATE_DATE,
                                   SqlSelect.LAST_UPDATE_DATE_BY_BOOK_ID);
        }

        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Return the book UUID based on the id.
     *
     * @param bookId of the book
     *
     * @return the book UUID
     *
     * @throws IllegalArgumentException if the bookId==0
     * @throws SQLiteDoneException      if zero rows found, which should never happen... flw.
     */
    @NonNull
    public String getBookUuid(final long bookId)
            throws SQLiteDoneException, IllegalArgumentException {
        // sanity check
        if (bookId == 0) {
            throw new IllegalArgumentException("cannot get uuid for id==0");
        }
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_UUID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_UUID, SqlGet.BOOK_UUID_BY_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForString();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return the title, or {@code null} if not found
     */
    @Nullable
    public String getBookTitle(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_TITLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_TITLE,
                                   SqlGet.BOOK_TITLE_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return the ISBN, or {@code null} if not found
     */
    @Nullable
    public String getBookIsbn(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_ISBN);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_ISBN,
                                   SqlGet.BOOK_ISBN_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId of the book.
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBook(final long bookId) {
        String uuid = null;
        try {
            // need the UUID to delete the thumbnail.
            uuid = getBookUuid(bookId);
        } catch (@NonNull final SQLiteDoneException e) {
            Logger.error(this, e, "Failed to get book UUID");
        }

        int rowsAffected = 0;
        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK);
            if (stmt == null) {
                stmt = mStatements.add(STMT_DELETE_BOOK, SqlDelete.BOOK_BY_ID);
            }
            // Be cautious; other threads may use the cached stmt, and set parameters.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                deleteThumbnail(uuid);
            }
            sSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e, "Failed to delete book");
        } finally {
            sSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Given a book's uuid, delete the thumbnail (if any).
     *
     * @param uuid of the book
     */
    private void deleteThumbnail(@Nullable final String uuid) {
        if (uuid != null) {
            // remove from file system
            StorageUtils.deleteFile(StorageUtils.getCoverFileForUuid(uuid));
            // remove from cache
            if (!uuid.isEmpty()) {
                CoversDAO.delete(uuid);
            }
        }
    }

    /**
     * Create a new book using the details provided.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     *
     * @return the row id of the newly inserted row, or -1 if an error occurred
     */
    public long insertBook(@NonNull final Context context,
                           @NonNull final Book book) {
        return insertBook(context, 0, book);
    }

    /**
     * Create a new book using the details provided.
     * <p>
     * Transaction: participate, or run in new.
     *
     * @param context Current context
     * @param bookId  of the book
     *                zero: a new book
     *                non-zero: will override the autoIncrement,
     *                only an Import should use this
     * @param book    A collection with the columns to be set. May contain extra data.
     *                The id will be updated.
     *
     * @return the row id of the newly inserted row, or -1 if an error occurred
     */
    public long insertBook(@NonNull final Context context,
                           final long bookId,
                           @NonNull final Book /* in/out */ book) {

        SyncLock txLock = null;
        if (!sSyncedDb.inTransaction()) {
            txLock = sSyncedDb.beginTransaction(true);
        }

        // Handle Language field FIRST, we might need it for 'ORDER BY' fields.
        book.updateLocale();

        try {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_BOOK_BUNDLE_AT_INSERT) {
                Logger.debug(this, "insertBook", book);
            }
            // Cleanup fields (Author, Series, title and remove blank fields, etc...)
            preprocessBook(context, book);

            // Make sure we have an author
            List<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
            if (authors.isEmpty()) {
                Logger.warnWithStackTrace(this, "No authors\n", book);
                return -1L;
            }

            // correct field types if needed, and filter out fields we don't have in the db table.
            ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale());

            // if we have an id, use it.
            if (bookId > 0) {
                cv.put(DOM_PK_ID.getName(), bookId);
            }

            // if we do NOT have a date set, then use TODAY
            if (!cv.containsKey(DBDefinitions.KEY_DATE_ADDED)) {
                cv.put(DBDefinitions.KEY_DATE_ADDED, DateUtils.utcSqlDateTimeForToday());
            }

            // if we do NOT have a date set, then use TODAY
            if (!cv.containsKey(DBDefinitions.KEY_DATE_LAST_UPDATED)) {
                cv.put(DBDefinitions.KEY_DATE_LAST_UPDATED, DateUtils.utcSqlDateTimeForToday());
            }

            long newBookId = sSyncedDb.insert(TBL_BOOKS.getName(), null, cv);
            if (newBookId > 0) {
                insertBookDependents(context, newBookId, book);
                insertFts(newBookId);
                // it's an insert, success only if we really inserted.
                if (txLock != null) {
                    sSyncedDb.setTransactionSuccessful();
                }
            }

            // set the new id on the Book itself
            book.putLong(DOM_PK_ID.getName(), newBookId);
            // and return it
            return newBookId;

        } catch (@NonNull final NumberFormatException e) {
            Logger.error(this, e, ERROR_FAILED_CREATING_BOOK_FROM + book);
            return -1L;
        } finally {
            if (txLock != null) {
                sSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Transaction: participate, or run in new.
     *
     * @param context Current context
     * @param bookId  of the book; takes precedence over the id of the book itself.
     * @param book    A collection with the columns to be set. May contain extra data.
     * @param flags   See {@link #BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT} for flag definition
     *
     * @return the number of rows affected, should be 1 for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int updateBook(@NonNull final Context context,
                          final long bookId,
                          @NonNull final Book book,
                          final int flags) {

        SyncLock txLock = null;
        if (!sSyncedDb.inTransaction()) {
            txLock = sSyncedDb.beginTransaction(true);
        }

        // Handle Language field FIRST, we might need it for 'ORDER BY' fields.
        book.updateLocale();

        try {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_BOOK_BUNDLE_AT_UPDATE) {
                Logger.debug(this, "updateBook", book);
            }

            // Cleanup fields (Author, Series, title, 'sameAuthor' if anthology,
            // and remove blank fields for which we have defaults)
            preprocessBook(context, book);

            ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale());

            // Disallow UUID updates
            if (cv.containsKey(DOM_BOOK_UUID.getName())) {
                cv.remove(DOM_BOOK_UUID.getName());
            }

            // set the DOM_DATE_LAST_UPDATED to 'now' if we're allowed,
            // or if it's not present already.
            if ((flags & BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT) == 0
                || !cv.containsKey(DOM_DATE_LAST_UPDATED.getName())) {
                cv.put(DOM_DATE_LAST_UPDATED.getName(), DateUtils.utcSqlDateTimeForToday());
            }

            // go !
            // A prepared statement would be faster for importing books....
            // but we don't know what columns are provided in the bundle....
            int rowsAffected = sSyncedDb.update(TBL_BOOKS.getName(), cv, DOM_PK_ID + "=?",
                                                new String[]{String.valueOf(bookId)});

            insertBookDependents(context, bookId, book);
            updateFts(bookId);

            if (txLock != null) {
                sSyncedDb.setTransactionSuccessful();
            }
            // make sure the Book has the correct id.
            book.putLong(DOM_PK_ID.getName(), bookId);

            return rowsAffected;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            throw new RuntimeException(
                    "Error updating book from " + book + ": " + e.getLocalizedMessage(), e);
        } finally {
            if (txLock != null) {
                sSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Update the 'read' status of the book.
     *
     * @param id   book to update
     * @param read the status to set
     *
     * @return {@code true} for success.
     */
    public boolean setBookRead(final long id,
                               final boolean read) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOK_READ.getName(), read);
        if (read) {
            cv.put(DOM_BOOK_READ_END.getName(), DateUtils.localSqlDateForToday());
        } else {
            cv.put(DOM_BOOK_READ_END.getName(), "");
        }

        return 0 < sSyncedDb.update(TBL_BOOKS.getName(), cv, DOM_PK_ID + "=?",
                                    new String[]{String.valueOf(id)});
    }

    /**
     * Update the 'complete' status of an Author.
     *
     * @param authorId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    public boolean setAuthorComplete(final long authorId,
                                     final boolean isComplete) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_IS_COMPLETE.getName(), isComplete);

        return 0 < sSyncedDb.update(TBL_AUTHORS.getName(), cv, DOM_PK_ID + "=?",
                                    new String[]{String.valueOf(authorId)});
    }

    /**
     * Update the 'complete' status of a Series.
     *
     * @param seriesId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    public boolean setSeriesComplete(final long seriesId,
                                     final boolean isComplete) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_SERIES_IS_COMPLETE.getName(), isComplete);

        return 0 < sSyncedDb.update(TBL_SERIES.getName(), cv, DOM_PK_ID + "=?",
                                    new String[]{String.valueOf(seriesId)});
    }

    /**
     * shared between book insert & update.
     * All of these will first delete all entries in the Book-[tableX] table for this bookId,
     * and then insert the new rows.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void insertBookDependents(@NonNull final Context context,
                                      final long bookId,
                                      @NonNull final Book book) {

        if (book.containsKey(UniqueId.BKEY_BOOKSHELF_ARRAY)) {
            insertBookBookshelf(context, bookId, book);
        }

        if (book.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)) {
            insertBookAuthors(context, bookId, book);
        }

        if (book.containsKey(UniqueId.BKEY_SERIES_ARRAY)) {
            insertBookSeries(context, bookId, book);
        }

        if (book.containsKey(UniqueId.BKEY_TOC_ENTRY_ARRAY)) {
            // update: toc entries are two steps away; they can exist in other books
            updateOrInsertTOC(context, bookId, book);
        }

        if (book.containsKey(DBDefinitions.KEY_LOANEE)
            && !book.getString(DBDefinitions.KEY_LOANEE).isEmpty()) {
            updateOrInsertLoan(bookId, book.getString(DOM_LOANEE.getName()));
        }
    }

    /**
     * Return a {@link Cursor} with a single column, the UUID of all {@link Book}.
     * Used to loop across all books during backup to save the cover images.
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookUuidList() {
        return sSyncedDb.rawQuery(SqlSelectFullTable.BOOK_ALL_UUID, null);
    }

    /**
     * @param isbn to search for (10 or 13)
     * @param both set to {@code true} to search for both isbn 10 and 13.
     *
     * @return book id, or 0 if not found
     */
    public long getBookIdFromIsbn(@NonNull final String isbn,
                                  final boolean both) {
        SynchronizedStatement stmt;
        if (both && ISBN.isValid(isbn)) {
            stmt = mStatements.get(STMT_GET_BOOK_ID_FROM_ISBN_2);
            if (stmt == null) {
                stmt = mStatements.add(STMT_GET_BOOK_ID_FROM_ISBN_2, SqlGet.BOOK_ID_BY_ISBN2);
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindString(1, isbn);
                stmt.bindString(2, ISBN.isbn2isbn(isbn));
                return stmt.simpleQueryForLongOrZero();
            }
        } else {
            stmt = mStatements.get(STMT_GET_BOOK_ID_FROM_ISBN_1);
            if (stmt == null) {
                stmt = mStatements.add(STMT_GET_BOOK_ID_FROM_ISBN_1, SqlGet.BOOK_ID_BY_ISBN);
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindString(1, isbn);
                return stmt.simpleQueryForLongOrZero();
            }
        }
    }

    /**
     * Check that a book with the passed id exists.
     *
     * @param bookId of the book
     *
     * @return {@code true} if exists
     */
    public boolean bookExists(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_CHECK_BOOK_EXISTS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_CHECK_BOOK_EXISTS, SqlSelect.BOOK_EXISTS);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.count() == 1;
        }
    }

    /**
     * Create the link between {@link Book} and {@link Series}.
     * <p>
     * {@link DBDefinitions#TBL_BOOK_SERIES}
     * <p>
     * Note that {@link DBDefinitions#DOM_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void insertBookSeries(@NonNull final Context context,
                                  final long bookId,
                                  @NonNull final Book book) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

        Locale bookLocale = book.getLocale();

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookSeriesByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_BOOK_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_BOOK_SERIES, SqlInsert.BOOK_SERIES);
        }

        // The list MAY contain duplicates (e.g. from Internet lookups of multiple
        // sources), so we track them in a hash map
        final Map<String, Boolean> idHash = new HashMap<>();
        int position = 0;
        for (Series series : list) {
            if (series.fixId(context, this, bookLocale) == 0) {
                if (insertSeries(context, series, bookLocale) <= 0) {
                    Logger.warnWithStackTrace(this, "insertSeries failed??");
                }
            } else {
                if (updateSeries(context, series, bookLocale) <= 0) {
                    Logger.warnWithStackTrace(this, "updateSeries failed??");
                }
            }

            Locale seriesLocale = series.getLocale(bookLocale);
            String uniqueId = series.getId() + '_' + series.getNumber().toLowerCase(seriesLocale);
            if (!idHash.containsKey(uniqueId)) {
                idHash.put(uniqueId, true);
                position++;
                // Be cautious; other threads may use the cached stmt, and set parameters.
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (stmt) {
                    stmt.bindLong(1, bookId);
                    stmt.bindLong(2, series.getId());
                    stmt.bindString(3, series.getNumber());
                    stmt.bindLong(4, position);
                    stmt.executeInsert();
                }
            }

        }
    }

    /**
     * Delete the link between Series and the given Book.
     * Note that the actual Series are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookSeriesByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_SERIES, SqlDelete.BOOK_SERIES_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Insert a List of TocEntry's for the given book.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void updateOrInsertTOC(@NonNull final Context context,
                                   final long bookId,
                                   @NonNull final Book book) {
        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        ArrayList<TocEntry> list = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookTocEntryByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        long position = 0;

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_BOOK_TOC_ENTRY);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_BOOK_TOC_ENTRY, SqlInsert.BOOK_TOC_ENTRY);
        }

        for (TocEntry tocEntry : list) {
            // handle the author.
            Author author = tocEntry.getAuthor();
            if (author.fixId(context, this, Locale.getDefault()) == 0) {
                insertAuthor(author);
            }

            Locale bookLocale = book.getLocale();
            // As an entry can exist in multiple books, try to find the entry.
            if (tocEntry.fixId(context, this, bookLocale) == 0) {
                insertTocEntry(context, tocEntry, bookLocale);
            } else {
                // It's an existing entry.
                // We cannot update the author (we never even get here if the author was changed)
                // We *do* update the title to allow corrections of case,
                // as the find was done on the DOM_TITLE_OB field.
                // and we update the DOM_TITLE_OB as well obviously.
                String obTitle = tocEntry.preprocessTitle(context, bookLocale);

                ContentValues cv = new ContentValues();
                cv.put(DOM_TITLE.getName(), tocEntry.getTitle());
                cv.put(DOM_TITLE_OB.getName(), encodeOrderByColumn(obTitle, bookLocale));
                cv.put(DOM_DATE_FIRST_PUBLICATION.getName(), tocEntry.getFirstPublication());

                sSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv,
                                 DOM_PK_ID + "=?",
                                 new String[]{String.valueOf(tocEntry.getId())});
            }

            // create the book<->TocEntry link.
            //
            // As we delete all links before insert/update'ng above, we normally
            // *always* need to re-create the link here.
            // However, this will fail if we inserted "The Universe" and updated "Universe, The"
            // as the former will be stored as "Universe, The".
            // We tried to mitigate this conflict before it could trigger an issue here, but it
            // complicated the code and frankly ended in a chain of special condition code branches
            // during processing of internet search data.
            // So... let's just catch the SQL constraint exception and ignore it.
            // (do not use the sql 'REPLACE' command! We want to keep the original position)

            try {
                position++;
                // Be cautious; other threads may use the cached stmt, and set parameters.
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (stmt) {
                    stmt.bindLong(1, tocEntry.getId());
                    stmt.bindLong(2, bookId);
                    stmt.bindLong(3, position);
                    stmt.executeInsert();
                }

            } catch (@NonNull final SQLiteConstraintException e) {
                // ignore and reset the position counter.
                position--;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_TOC) {
                    Logger.debug(this, "updateOrInsertTOC", e);
                }
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_TOC) {
                Logger.debug(this, "updateOrInsertTOC",
                             "\n     bookId   : " + bookId,
                             "\n     authorId : " + author.getId(),
                             "\n     position : " + position);
            }
        }
    }

    /**
     * Creates a new TocEntry in the database.
     *
     * @param context    Current context
     * @param tocEntry   object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertTocEntry(@NonNull final Context context,
                                @NonNull final TocEntry tocEntry,
                                @NonNull final Locale bookLocale) {

        Locale tocLocale = tocEntry.getLocale(bookLocale);

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_TOC_ENTRY);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_TOC_ENTRY, SqlInsert.TOC_ENTRY);
        }

        String obTitle = tocEntry.preprocessTitle(context, tocLocale);
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, tocEntry.getAuthor().getId());
            stmt.bindString(2, tocEntry.getTitle());
            stmt.bindString(3, encodeOrderByColumn(obTitle, tocLocale));
            stmt.bindString(4, tocEntry.getFirstPublication());
            long iId = stmt.executeInsert();
            if (iId > 0) {
                tocEntry.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Create the link between {@link Book} and {@link Author}.
     * <p>
     * {@link DBDefinitions#TBL_BOOK_AUTHOR}
     * <p>
     * Note that {@link DBDefinitions#DOM_BOOK_AUTHOR_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void insertBookAuthors(@NonNull final Context context,
                                   final long bookId,
                                   @NonNull final Book book) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        ArrayList<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookAuthorByBookId(bookId);

        // anything to insert ?
        if (authors.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_BOOK_AUTHORS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_BOOK_AUTHORS, SqlInsert.BOOK_AUTHOR);
        }

        // The list MAY contain duplicates (e.g. from Internet lookups of multiple
        // sources), so we track them in a hash table
        final Map<String, Boolean> idHash = new HashMap<>();
        int position = 0;
        for (Author author : authors) {
            // find/insert the author
            if (author.fixId(context, this, Locale.getDefault()) == 0) {
                insertAuthor(author);
            }

            // we use the id as the KEY here, so yes, a String.
            String authorIdStr = String.valueOf(author.getId());
            if (!idHash.containsKey(authorIdStr)) {
                // indicate this author(id) is already present...
                // but override, so we get elimination of duplicates.
                idHash.put(authorIdStr, true);

                position++;
                // Be cautious; other threads may use the cached stmt, and set parameters.
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (stmt) {
                    stmt.bindLong(1, bookId);
                    stmt.bindLong(2, author.getId());
                    stmt.bindLong(3, position);
                    stmt.bindLong(4, author.getType());
                    stmt.executeInsert();
                }

            }
        }
    }

    /**
     * Delete the link between Authors and the given Book.
     * Note that the actual Authors are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookAuthorByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_AUTHORS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_AUTHORS, SqlDelete.BOOK_AUTHOR_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     * <p>
     * {@link DBDefinitions#TBL_BOOK_BOOKSHELF}
     * <p>
     * Note that {@link DBDefinitions#DOM_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void insertBookBookshelf(@NonNull final Context context,
                                     final long bookId,
                                     @NonNull final Book book) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        ArrayList<Bookshelf> bookshelves =
                book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookBookshelfByBookId(bookId);

        // anything to insert ?
        if (bookshelves.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_BOOK_BOOKSHELF);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_BOOK_BOOKSHELF, SqlInsert.BOOK_BOOKSHELF);
        }

        for (Bookshelf bookshelf : bookshelves) {
            if (bookshelf.getName().isEmpty()) {
                continue;
            }

            // validate the style first
            long styleId = bookshelf.getStyle(this).getId();

            if (bookshelf.fixId(context, this, Locale.getDefault()) == 0) {
                insertBookshelf(bookshelf, styleId);
            }

            // Be cautious; other threads may use the cached stmt, and set parameters.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, bookshelf.getId());
                stmt.executeInsert();
            }
        }
    }

    /**
     * Delete the link between Bookshelves and the given Book.
     * Note that the actual Bookshelves are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookBookshelfByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_BOOKSHELF);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_BOOKSHELF,
                                   SqlDelete.BOOK_BOOKSHELF_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * update books for which the new id is not already present.
     * <p>
     * In other words: replace the id for books that are not already linked with the new one
     *
     * @param table  TBL_BOOK_AUTHORS or TBL_BOOK_SERIES
     * @param fk     DOM_FK_AUTHOR or DOM_FK_SERIES
     * @param fromId the id to replace
     * @param toId   the new id to use
     */
    private void globalReplaceId(@NonNull final TableDefinition table,
                                 @NonNull final DomainDefinition fk,
                                 final long fromId,
                                 final long toId) {

        SynchronizedStatement stmt = sSyncedDb.compileStatement(
                "UPDATE " + table + " SET " + fk + "=? WHERE " + fk + "=?"
                + " AND NOT EXISTS"
                + " (SELECT NULL FROM " + table.ref() + " WHERE "
                // left: the aliased table, right the actual table
                + table.dot(DOM_FK_BOOK) + '=' + table + '.' + DOM_FK_BOOK
                // left: the aliased table
                + " AND " + table.dot(fk) + "=?)");

        stmt.bindLong(1, toId);
        stmt.bindLong(2, fromId);
        stmt.bindLong(3, toId);
        stmt.executeUpdateDelete();
        stmt.close();
    }

    /**
     * Books use an ordered list of Authors and Series (custom order by the end-user).
     * When replacing one of them, lists have to be adjusted.
     * <p>
     * transaction: needs.
     * <p>
     * throws exceptions, caller must handle
     *
     * @param table     TBL_BOOK_AUTHORS or TBL_BOOK_SERIES
     * @param fk        DOM_FK_AUTHOR or DOM_FK_SERIES
     * @param posDomain DOM_BOOK_AUTHOR_POSITION or DOM_BOOK_SERIES_POSITION
     * @param fromId    the id to replace
     * @param toId      the new id to use
     */
    private void globalReplacePositionedBookItem(@NonNull final TableDefinition table,
                                                 @NonNull final DomainDefinition fk,
                                                 @NonNull final DomainDefinition posDomain,
                                                 final long fromId,
                                                 final long toId) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        // Get the position of the already-existing 'new/replacement' object
        SynchronizedStatement replacementIdPosStmt = sSyncedDb.compileStatement(
                "SELECT " + posDomain + " FROM " + table
                + " WHERE " + DOM_FK_BOOK + "=? AND " + fk + "=?");

        // Delete a specific object record
        SynchronizedStatement delStmt = sSyncedDb.compileStatement(
                "DELETE FROM " + table
                + " WHERE " + DOM_FK_BOOK + "=? AND " + fk + "=?");

        // Move a single entry to a new position
        SynchronizedStatement moveStmt = sSyncedDb.compileStatement(
                "UPDATE " + table + " SET " + posDomain + "=?"
                + " WHERE " + DOM_FK_BOOK + "=? AND " + posDomain + "=?");

        // Sanity check to deal with bad data
        SynchronizedStatement checkMinStmt = sSyncedDb.compileStatement(
                "SELECT MIN(" + posDomain + ") FROM " + table
                + " WHERE " + DOM_FK_BOOK + "=?");

        // Delete the rows that would have caused duplicates. Be cautious by using the
        // EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
        // loss if one of the prior statements failed silently.
        //
        // We also move remaining items up one place to ensure positions remain correct
        //
        String sql = "SELECT " + DOM_FK_BOOK + ',' + posDomain + " FROM " + table
                     + " WHERE " + fk + "=?"
                     + " AND EXISTS"
                     + " (SELECT NULL FROM " + table.ref() + " WHERE "
                     // left: the aliased table, right the actual table
                     + table.dot(DOM_FK_BOOK) + '=' + table + '.' + DOM_FK_BOOK
                     // left: the aliased table
                     + " AND " + table.dot(fk) + "=?)";

        //noinspection TryFinallyCanBeTryWithResources
        try (Cursor cursor = sSyncedDb.rawQuery(sql, new String[]{String.valueOf(fromId),
                                                                  String.valueOf(toId)})) {
            // Get the column indexes we need
            int bookCol = cursor.getColumnIndexOrThrow(DOM_FK_BOOK.getName());
            int posCol = cursor.getColumnIndexOrThrow(posDomain.getName());

            // Loop through all instances of the old object appearing
            while (cursor.moveToNext()) {
                // Get the details of the old object
                long bookId = cursor.getLong(bookCol);
                long pos = cursor.getLong(posCol);

                // Get the position of the new/replacement object
                replacementIdPosStmt.bindLong(1, bookId);
                replacementIdPosStmt.bindLong(2, toId);
                long replacementIdPos = replacementIdPosStmt.simpleQueryForLong();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_GLOBAL_REPLACE) {
                    Logger.debug(this, "globalReplacePositionedBookItem",
                                 "bookId=" + bookId,
                                 "toId=" + toId,
                                 "replacementIdPos=" + replacementIdPos);
                }

                // Delete the old record
                delStmt.bindLong(1, bookId);
                delStmt.bindLong(2, fromId);
                delStmt.executeUpdateDelete();

                // If the deleted object was more prominent than the new object,
                // move the new one up
                if (replacementIdPos > pos) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_GLOBAL_REPLACE) {
                        Logger.debug(this, "globalReplacePositionedBookItem",
                                     "bookId=" + bookId,
                                     "pos=" + pos,
                                     "replacementIdPos=" + replacementIdPos);
                    }
                    moveStmt.bindLong(1, pos);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, replacementIdPos);
                    moveStmt.executeUpdateDelete();
                }

                // It is tempting to move all rows up by one when we delete something, but that
                // would have to be done in another sorted cursor in order to prevent duplicate
                // index errors. So we just make sure we have something in position 1.

                // Get the minimum position
                checkMinStmt.bindLong(1, bookId);
                long minPos = checkMinStmt.simpleQueryForLong();
                // If it's > 1, move it to 1
                if (minPos > 1) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_GLOBAL_REPLACE) {
                        Logger.debug(this, "globalReplacePositionedBookItem",
                                     "bookId=" + bookId,
                                     "minPos=" + minPos);
                    }
                    moveStmt.bindLong(1, 1);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, minPos);
                    moveStmt.executeUpdateDelete();
                }
            }
        } finally {
            delStmt.close();
            moveStmt.close();
            checkMinStmt.close();
            replacementIdPosStmt.close();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return list of TocEntry for this book
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByBook(final long bookId) {
        ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.TOC_ENTRIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            if (cursor.getCount() == 0) {
                return list;
            }
            CursorMapper mapper = new CursorMapper(cursor);

            while (cursor.moveToNext()) {
                Author author = new Author(mapper.getLong(DOM_FK_AUTHOR.getName()), mapper);

                list.add(new TocEntry(mapper.getLong(DOM_PK_ID.getName()),
                                      author,
                                      mapper.getString(DOM_TITLE.getName()),
                                      mapper.getString(DOM_DATE_FIRST_PUBLICATION.getName()),
                                      TocEntry.Type.TYPE_BOOK, 1));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's (most often just the one) in which this TocEntry (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return id-of-book list
     */
    public ArrayList<Long> getBookIdsByTocEntry(final long tocId) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlGet.BOOK_ID_BY_TOC_ENTRY_ID,
                                                new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /*
     * Bad idea. Instead use: Book book = Book.getBook(mDb, bookId);
     * So you never get a {@code null} object!
     *
     * Leaving commented as a reminder
     *
     * @param bookId of the book
     *
     * @return the fully populated Book, or {@code null} if not found
     *
     * @see #fetchBookById(long) which allows a partial retrieval
     */
//    @Nullable
//    public Book getBookById(final long bookId) {
//
//        try (Cursor cursor = fetchBookById(bookId)) {
//            if (cursor.moveToFirst()) {
//                // Put all cursor fields in collection
//                Book book = new Book(cursor);
//
//                // load lists (or init with empty lists)
//                book.putBookshelfList(getBookshelvesByBookId(bookId));
//                book.putAuthorList(getAuthorsByBookId(bookId));
//                book.putSeriesList(getSeriesByBookId(bookId));
//                book.putTOC(getTocEntryByBook(bookId));
//
//                return book;
//            }
//        }
//        return null;
//    }

    /**
     * Get a list of book ID's for the given Author.
     *
     * @param authorId id of the author
     *
     * @return id-of-book list
     */
    public ArrayList<Long> getBookIdsByAuthor(final long authorId) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOK_IDS_BY_AUTHOR_ID,
                                                new String[]{String.valueOf(authorId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Series.
     *
     * @param seriesId id of the Series
     *
     * @return id-of-book list
     */
    public ArrayList<Long> getBookIdsBySeries(final long seriesId) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOK_IDS_BY_SERIES_ID,
                                                new String[]{String.valueOf(seriesId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Publisher.
     *
     * @param publisher name of the Publisher
     *
     * @return id-of-book list
     */
    public ArrayList<Long> getBookIdsByPublisher(@NonNull final String publisher) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOK_IDS_BY_PUBLISHER,
                                                new String[]{publisher})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of the authors for a book.
     *
     * @param bookId of the book
     *
     * @return list of authors
     */
    @NonNull
    public ArrayList<Author> getAuthorsByBookId(final long bookId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.AUTHORS_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                list.add(new Author(mapper.getLong(DOM_PK_ID.getName()), mapper));
            }
        }
        return list;
    }

    /**
     * Get a list of the Series a book belongs to.
     *
     * @param bookId of the book
     *
     * @return list of Series
     */
    @NonNull
    public ArrayList<Series> getSeriesByBookId(final long bookId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.SERIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            if (cursor.getCount() == 0) {
                return list;
            }
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                list.add(new Series(mapper.getLong(DOM_PK_ID.getName()), mapper));
            }
        }
        return list;
    }

    /**
     * Return the SQL for a list of all books in the database.
     * <p>
     * TODO: redo this so the sql becomes static
     *
     * @param whereClause to add to books search criteria (without the keyword 'WHERE')
     *
     * @return A full piece of SQL to perform the search
     */
    @NonNull
    private String getBookSql(@NonNull final String whereClause) {

        Context context = App.getLocalizedAppContext();
        String andOthersText = context.getString(R.string.and_others);

        // "a." (TBL_AUTHOR), "b." (TBL_BOOKS), "s." (TBL_SERIES}
        // BUT... here they have double-use:
        // in SQL macros -> the tables.
        // in the sql -> the query/sub-query.
        //
        // so DO NOT replace them with table.dot() etc... !
        return SqlAllBooks.PREFIX
               + ",CASE WHEN " + COLUMN_ALIAS_NR_OF_SERIES + "<2"
               + " THEN COALESCE(s." + DOM_SERIES_FORMATTED + ",'')"
               + " ELSE " + DOM_SERIES_FORMATTED + " || ' " + andOthersText + '\''
               + " END AS " + DOM_SERIES_FORMATTED
               + " FROM ("
               + /* */ SqlAllBooks.ALL_BOOKS
               + /* */ (!whereClause.isEmpty() ? " WHERE " + " (" + whereClause + ')' : "")
               + /* */ " ORDER BY " + TBL_BOOKS.dot(DOM_TITLE_OB) + ' ' + COLLATION + " ASC"
               + ") b" + SqlAllBooks.SUFFIX;
    }

    /**
     * Return a {@link BookCursor} for the given {@link Book} id.
     * The caller can then retrieve columns as needed.
     *
     * @param bookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBookById(final long bookId) {
        return (BookCursor)
                sSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                              getBookSql(
                                                      TBL_BOOKS.dot(DOM_PK_ID) + "=?"),
                                              new String[]{String.valueOf(bookId)},
                                              "");
    }

    /**
     * Return a {@link BookCursor} for the given list of {@link Book} ID's.
     * The caller can then retrieve columns as needed.
     *
     * @param bookIds           List of book ID's to update, {@code null} for all books.
     * @param fromBookIdOnwards the lowest book id to start from.
     *                          This allows to fetch a subset of the requested set.
     *                          Pass in 0 to get the full set.
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooks(@Nullable final List<Long> bookIds,
                                 final long fromBookIdOnwards) {
        String whereClause;
        if (bookIds == null || bookIds.isEmpty()) {
            whereClause = TBL_BOOKS.dot(DOM_PK_ID)
                          + ">=" + fromBookIdOnwards;

        } else if (bookIds.size() == 1) {
            whereClause = TBL_BOOKS.dot(DOM_PK_ID)
                          + '=' + bookIds.get(0);

        } else {
            whereClause = TBL_BOOKS.dot(DOM_PK_ID)
                          + " IN (" + TextUtils.join(",", bookIds) + ')'
                          + " AND (" + TBL_BOOKS.dot(DOM_PK_ID)
                          + ">=" + fromBookIdOnwards + ')';
        }

        // the order by is used to be able to restart the update.
        String sql = getBookSql(whereClause) + " ORDER BY " + TBL_BOOKS.dot(DOM_PK_ID);

        return (BookCursor) sSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                          sql, null, "");
    }

    /**
     * Return a {@link BookCursor} for the given ISBN.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param isbnList list of ISBN(s) to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksByIsbnList(@NonNull final List<String> isbnList) {
        if (isbnList.isEmpty()) {
            throw new IllegalArgumentException("isbnList was empty");
        }

        StringBuilder where = new StringBuilder(TBL_BOOKS.dot(DOM_BOOK_ISBN));
        if (isbnList.size() == 1) {
            // single ISBN
            where.append("='").append(encodeString(isbnList.get(0))).append('\'');
        } else {
            where.append(" IN (")
                 .append(Csv.join(",", isbnList, element -> '\'' + encodeString(element) + '\''))
                 .append(')');
        }
        return (BookCursor) sSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                          where.toString(),
                                                          null, "");
    }

    /**
     * A complete export of all tables (flattened) in the database.
     *
     * @param sinceDate to select all books added/modified since that date.
     *                  Set to {@code null} for *all* books.
     *
     * @return BookCursor over all books, authors, etc
     */
    @NonNull
    public BookCursor fetchBooksForExport(@Nullable final Date sinceDate) {
        String whereClause;
        if (sinceDate == null) {
            whereClause = "";
        } else {
            whereClause = " WHERE " + TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED)
                          + ">'" + DateUtils.utcSqlDateTime(sinceDate) + '\'';
        }

        String sql = SqlAllBooks.ALL_BOOKS + whereClause + " ORDER BY " + TBL_BOOKS.dot(DOM_PK_ID);

        return (BookCursor) sSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                          sql, null, "");
    }

    /**
     * Return a {@link Cursor} for the given {@link Book} id.
     * <p>
     * The columns fetched are limited to what is needed for the
     * {@link BooksOnBookshelf} so called "extras" fields.
     *
     * @param bookId to retrieve
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookExtrasById(final long bookId) {
        //A performance run (in UIThread!) on 983 books showed:
        // 1. withBookshelves==false; 799ms
        // 2. withBookshelves==true and complex SQL; 806ms
        // 3. withBookshelves==true, simpler SQL,
        // and an extra getBookshelvesByBookId call; 1254ms
        //
        // so nothing spectacular between 1/2,
        // but avoiding the extra fetch of option 3. is worth it.

        return sSyncedDb.rawQuery(SqlSelect.BOOK_EXTRAS, new String[]{String.valueOf(bookId)});
    }

    /**
     * Creates a new bookshelf in the database.
     *
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertBookshelf(@NonNull final Bookshelf /* in/out */ bookshelf,
                                 final long styleId) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlInsert.BOOKSHELF)) {
            stmt.bindString(1, bookshelf.getName());
            stmt.bindLong(2, styleId);
            long iId = stmt.executeInsert();
            if (iId > 0) {
                bookshelf.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Takes all books from Bookshelf 'sourceId', and puts them onto Bookshelf 'destId',
     * then deletes Bookshelf 'sourceId'.
     * <p>
     * The style of the bookshelf will not be changed.
     *
     * @return the amount of books moved.
     */
    public int mergeBookshelves(final long sourceId,
                                final long destId) {

        int rowsAffected;

        ContentValues cv = new ContentValues();
        cv.put(DOM_FK_BOOKSHELF.getName(), destId);

        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            rowsAffected = sSyncedDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                            DOM_FK_BOOKSHELF + "=?",
                                            new String[]{String.valueOf(sourceId)});

            // delete the now empty shelf.
            deleteBookshelf(sourceId);

            sSyncedDb.setTransactionSuccessful();
        } finally {
            sSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Delete the bookshelf with the given rowId.
     *
     * @param id of bookshelf to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBookshelf(final long id) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.BOOKSHELF_BY_ID)) {
            stmt.bindLong(1, id);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Return the bookshelf id. The incoming object is not modified.
     *
     * @param bookshelf bookshelf to search for
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getBookshelfId(@NonNull final Bookshelf bookshelf) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOKSHELF_ID_BY_NAME);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOKSHELF_ID_BY_NAME, SqlGet.BOOKSHELF_ID_BY_NAME);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, bookshelf.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    public Bookshelf getBookshelfByName(@NonNull final String name) {

        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.BOOKSHELF_BY_NAME,
                                                new String[]{name})) {
            CursorMapper mapper = new CursorMapper(cursor);
            if (cursor.moveToFirst()) {
                return new Bookshelf(mapper.getLong(DOM_PK_ID.getName()), mapper);
            }
            return null;
        }
    }

    /**
     * @param id of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    public Bookshelf getBookshelf(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.BOOKSHELF_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            CursorMapper mapper = new CursorMapper(cursor);
            if (cursor.moveToFirst()) {
                return new Bookshelf(mapper.getLong(DOM_PK_ID.getName()), mapper);
            }
            return null;
        }
    }

    /**
     * Update a bookshelf.
     *
     * @param bookshelf to update
     * @param styleId   a valid style id, no checks are done in this method
     *
     * @return rows affected, should be 1 for success
     */
    @SuppressWarnings("UnusedReturnValue")
    public int updateBookshelf(@NonNull final Bookshelf bookshelf,
                               final long styleId) {

        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOKSHELF.getName(), bookshelf.getName());
        cv.put(DOM_FK_STYLE_ID.getName(), styleId);

        return sSyncedDb.update(TBL_BOOKSHELF.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(bookshelf.getId())});
    }

    /**
     * Add or update the passed Bookshelf, depending whether bookshelf.id == 0.
     *
     * @param context   Current context
     * @param bookshelf object to insert or update. Will be updated with the id.
     *
     * @return {@code true} for success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertBookshelf(@NonNull final Context context,
                                           @NonNull final /* in/out */ Bookshelf bookshelf,
                                           final long styleId) {
        if (bookshelf.getId() != 0) {
            return updateBookshelf(bookshelf, styleId) > 0;
        } else {
            // try to find first.
            if (bookshelf.fixId(context, this, Locale.getDefault()) == 0) {
                return insertBookshelf(bookshelf, styleId) > 0;
            }
        }
        return false;
    }

    /**
     * Returns a list of all bookshelves in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        ArrayList<Bookshelf> list = new ArrayList<>();

        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.BOOKSHELVES_ORDERED, null)) {
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(mapper.getLong(DOM_PK_ID.getName()), mapper));
            }
        }
        return list;
    }

    /**
     * Get a list of all the bookshelves this book is on.
     *
     * @param bookId to use
     *
     * @return the list
     */
    public ArrayList<Bookshelf> getBookshelvesByBookId(final long bookId) {
        ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOKSHELVES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(mapper.getLong(DOM_PK_ID.getName()), mapper));
            }
            return list;
        }
    }

    /**
     * Get a list of all user defined {@link BooklistStyle}, arranged in a lookup map.
     *
     * @return ordered map, with the uuid as key
     */
    @NonNull
    public Map<String, BooklistStyle> getUserStyles() {
        Map<String, BooklistStyle> list = new LinkedHashMap<>();

        String sql = SqlSelectFullTable.BOOKLIST_STYLES
                     + " WHERE " + DOM_STYLE_IS_BUILTIN + "=0"
                     // We order by the id, i.e. in the order the styles were created.
                     // This is only done to get a reproducible and consistent order.
                     + " ORDER BY " + DOM_PK_ID;

        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
            if (cursor.getCount() == 0) {
                return list;
            }
            CursorMapper mapper = new CursorMapper(cursor);
            while (cursor.moveToNext()) {
                long id = mapper.getLong(DOM_PK_ID.getName());
                String uuid = mapper.getString(DOM_UUID.getName());
                list.put(uuid, new BooklistStyle(id, uuid));
            }
        }
        return list;
    }

    /**
     * Get the id of a {@link BooklistStyle} with matching UUID.
     *
     * @param uuid to find
     *
     * @return id
     */
    public long getStyleIdByUuid(@NonNull final String uuid) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOKLIST_STYLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOKLIST_STYLE, SqlGet.BOOKLIST_STYLE_ID_BY_UUID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Create a new {@link BooklistStyle}.
     *
     * @param style object to insert. Will be updated with the id.
     *
     * @return the row id of the last row inserted, if this insert is successful. -1 otherwise.
     */
    public long insertStyle(@NonNull final BooklistStyle /* in/out */ style) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlInsert.BOOKLIST_STYLE)) {
            stmt.bindString(1, style.getUuid());
            stmt.bindLong(2, style.isUserDefined() ? 0 : 1);
            long iId = stmt.executeInsert();
            if (iId > 0) {
                style.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Delete a {@link BooklistStyle}.
     *
     * @param uuid of style to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteStyle(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.STYLE_BY_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Return a {@link Cursor} for the given query string.
     *
     * @param query string
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchSearchSuggestions(@NonNull final String query) {
        String q = '%' + query + '%';
        return sSyncedDb.rawQuery(SqlSelect.SEARCH_SUGGESTIONS, new String[]{q, q, q, q});
    }

    /**
     * Returns a unique list of all currencies in the database for the specified domain.
     *
     * @param domainName for which to collect the used currency codes
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getCurrencyCodes(@NonNull final String domainName) {
        String column;
        if (DBDefinitions.KEY_PRICE_LISTED_CURRENCY.equals(domainName)) {
            column = DBDefinitions.KEY_PRICE_LISTED_CURRENCY;
//        } else if (DBDefinitions.KEY_PRICE_PAID_CURRENCY.equals(type)) {
        } else {
            column = DBDefinitions.KEY_PRICE_PAID_CURRENCY;
        }

        String sql = "SELECT DISTINCT upper(" + column + ") FROM " + TBL_BOOKS
                     + " ORDER BY upper(" + column + ") " + COLLATION;

        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
            ArrayList<String> list = getFirstColumnAsList(cursor);
            if (list.isEmpty()) {
                // sure, this is very crude and discriminating.
                // But it will only ever be used *once* per currency column
                list.add("EUR");
                list.add("GBP");
                list.add("USD");
            }
            return list;
        }
    }

    /**
     * Returns a unique list of all formats in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getFormats() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.FORMATS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateFormat(@NonNull final String from,
                             @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.FORMAT)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all genres in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.GENRES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateGenre(@NonNull final String from,
                            @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.GENRE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all languages in the database.
     *
     * @return The list; normally all ISO codes
     */
    @NonNull
    public ArrayList<String> getLanguageCodes() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.LANGUAGES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateLanguage(@NonNull final String from,
                               @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.LANGUAGE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Get the name of the loanee for a given book, if any.
     *
     * @param bookId book to search for
     *
     * @return Who the book is lend to, or {@code null} when not lend out
     */
    @Nullable
    public String getLoaneeByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_LOANEE_BY_BOOK_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_LOANEE_BY_BOOK_ID, SqlGet.LOANEE_BY_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId book to lend
     * @param loanee person to lend to
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertLoan(final long bookId,
                                      @NonNull final String loanee) {
        String current = getLoaneeByBookId(bookId);
        if (current == null || current.isEmpty()) {
            return insertLoan(bookId, loanee);
        } else if (!loanee.equals(current)) {
            return updateLoan(bookId, loanee);
        }

        return true;
    }

    /**
     * Creates a new loan.
     *
     * @param bookId the book we're lending
     * @param loanee name of the person we're loaning to.
     *
     * @return {@code true} for success.
     */
    private boolean insertLoan(final long bookId,
                               @NonNull final String loanee) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlInsert.BOOK_LOANEE)) {
            stmt.bindLong(1, bookId);
            stmt.bindString(2, loanee);
            return stmt.executeInsert() > 0;
        }
    }

    /**
     * Update a loan.
     *
     * @param bookId the book we're lending
     * @param loanee name of the person we're loaning to.
     *
     * @return {@code true} for success.
     */
    private boolean updateLoan(final long bookId,
                               @NonNull final String loanee) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_LOANEE.getName(), loanee);
        int rowsAffected = sSyncedDb.update(TBL_BOOK_LOANEE.getName(), cv,
                                            DOM_FK_BOOK + "=?",
                                            new String[]{String.valueOf(bookId)});
        return rowsAffected > 0;
    }

    /**
     * Delete the loan for the given book.
     *
     * @param bookId id of book whose loan is to be deleted
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteLoan(final long bookId) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(
                SqlDelete.BOOK_LOANEE_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * @return a unique list of all locations in the database
     */
    @NonNull
    public ArrayList<String> getLocations() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.LOCATIONS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Rename a Location.
     */
    public void updateLocation(@NonNull final String from,
                               @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.LOCATION)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all publishers in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublisherNames() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.PUBLISHERS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Rename a Publisher.
     */
    public void updatePublisher(@NonNull final String from,
                                @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.PUBLISHER)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Creates a new Series in the database.
     *
     * @param context    Current context
     * @param series     object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or -1 if an error occurred
     */
    private long insertSeries(@NonNull final Context context,
                              @NonNull final Series /* in/out */ series,
                              @NonNull final Locale bookLocale) {

        Locale seriesLocale = series.getLocale(bookLocale);

        SynchronizedStatement stmt = mStatements.get(STMT_INSERT_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_INSERT_SERIES, SqlInsert.SERIES);
        }

        String obTitle = series.preprocessTitle(context, seriesLocale);

        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            stmt.bindLong(3, series.isComplete() ? 1 : 0);
            long iId = stmt.executeInsert();
            if (iId > 0) {
                series.setId(iId);
            }
            return iId;
        }
    }

    /**
     * @param context    Current context
     * @param series     to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return rows affected, should be 1 for success
     */
    private int updateSeries(@NonNull final Context context,
                             @NonNull final Series series,
                             @NonNull final Locale bookLocale) {

        Locale seriesLocale = series.getLocale(bookLocale);

        String obTitle = series.preprocessTitle(context, seriesLocale);

        ContentValues cv = new ContentValues();
        cv.put(DOM_SERIES_TITLE.getName(), series.getTitle());
        cv.put(DOM_SERIES_TITLE_OB.getName(), encodeOrderByColumn(obTitle, seriesLocale));
        cv.put(DOM_SERIES_IS_COMPLETE.getName(), series.isComplete());

        return sSyncedDb.update(TBL_SERIES.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(series.getId())});
    }

    /**
     * Add or update the passed Series, depending whether series.id == 0.
     *
     * @param context    Current context
     * @param bookLocale Locale to use if the item has none set
     * @param series     object to insert or update. Will be updated with the id.
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertSeries(@NonNull final Context context,
                                        @NonNull final Locale bookLocale,
                                        @NonNull final /* in/out */ Series series) {

        if (series.getId() != 0) {
            return updateSeries(context, series, bookLocale) > 0;
        } else {
            // try to find first.
            if (series.fixId(context, this, bookLocale) == 0) {
                return insertSeries(context, series, bookLocale) > 0;
            } else {
                return updateSeries(context, series, bookLocale) > 0;
            }
        }
    }

    /**
     * Delete the passed series.
     *
     * @param id series to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteSeries(final long id) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_SERIES, SqlDelete.SERIES_BY_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, id);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Get all series; mainly for the purpose of backups.
     *
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchSeries() {
        return sSyncedDb.rawQuery(SqlSelectFullTable.SERIES, null);
    }

    /**
     * Return the Series based on the ID.
     *
     * @return the Series, or {@code null} if not found
     */
    @Nullable
    public Series getSeries(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.SERIES_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            CursorMapper mapper = new CursorMapper(cursor);
            if (cursor.moveToFirst()) {
                return new Series(id, mapper);
            }
            return null;
        }
    }

    /**
     * Find a Series, and return its ID. The incoming object is not modified.
     *
     * @param context    Current context
     * @param series     to find
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getSeriesId(@NonNull final Context context,
                            @NonNull final Series series,
                            @NonNull final Locale bookLocale) {

        Locale seriesLocale = series.getLocale(bookLocale);

        SynchronizedStatement stmt = mStatements.get(STMT_GET_SERIES_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_SERIES_ID, SqlGet.SERIES_ID_BY_NAME);
        }

        String obTitle = series.preprocessTitle(context, seriesLocale);

        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(series.getTitle(), seriesLocale));
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Refresh the passed Series from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Series.
     * <p>
     * Will NOT insert a new Series if not found.
     *
     * @param context    Current context
     * @param series     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refreshSeries(@NonNull final Context context,
                              @NonNull final Series /* out */ series,
                              @NonNull final Locale bookLocale) {

        if (series.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            series.fixId(context, this, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            Series dbSeries = getSeries(series.getId());
            if (dbSeries != null) {
                // copy any updated fields
                series.copyFrom(dbSeries, false);
            } else {
                // not found?, set as 'new'
                series.setId(0);
            }
        }
    }

    /**
     * Globally replace the Series data. This does <strong>not</strong> copy book related fields.
     *
     * @param context    Current context
     * @param bookLocale the Locale to use if the "from" Series has none set
     * @param from       Series to replace
     * @param to         Series to use
     *
     * @return {@code true} for success.
     */
    public boolean globalReplace(@NonNull final Context context,
                                 @NonNull final Locale bookLocale,
                                 @NonNull final Series from,
                                 @NonNull final Series to) {

        // process the destination Series.
        if (!updateOrInsertSeries(context, bookLocale, to)) {
            Logger.warnWithStackTrace(this, "Could not update", "series=" + to);
            return false;
        }

        // sanity check
        if (from.getId() == 0 && from.fixId(context, this, bookLocale) == 0) {
            Logger.warnWithStackTrace(this, "Old Series is not defined");
            return false;
        }

        // sanity check
        if (from.getId() == to.getId()) {
            return true;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DAO_GLOBAL_REPLACE) {
            Logger.debug(this, "globalReplaceSeries",
                         "from=" + from.getId() + ", to=" + to.getId());
        }

        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            // update books for which the new id is not already present
            globalReplaceId(TBL_BOOK_SERIES, DOM_FK_SERIES, from.getId(), to.getId());

            globalReplacePositionedBookItem(TBL_BOOK_SERIES,
                                            DOM_FK_SERIES,
                                            DOM_BOOK_SERIES_POSITION,
                                            from.getId(), to.getId());

            sSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            return false;
        } finally {
            sSyncedDb.endTransaction(txLock);
        }
        return true;
    }

    /**
     * @param context    Current context
     * @param series     to count the books in
     * @param bookLocale Locale to use if the item has none set
     *
     * @return number of books in series
     */
    public long countBooksInSeries(@NonNull final Context context,
                                   @NonNull final Series series,
                                   @NonNull final Locale bookLocale) {
        if (series.getId() == 0 && series.fixId(context, this, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(
                SqlSelect.COUNT_BOOKS_IN_SERIES)) {
            stmt.bindLong(1, series.getId());
            return stmt.count();
        }
    }

    /**
     * Builds an arrayList of all series names.
     * Used for AutoCompleteTextView
     *
     * @return the list
     */
    @NonNull
    public ArrayList<String> getSeriesTitles() {
        return getColumnAsList(SqlSelectFullTable.SERIES_NAME, DOM_SERIES_TITLE.getName());
    }

    /**
     * Set the Goodreads book id for this book.
     */
    public void setGoodreadsBookId(final long bookId,
                                   final long goodreadsBookId) {

        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_GOODREADS_BOOK_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_GOODREADS_BOOK_ID, SqlUpdate.GOODREADS_BOOK_ID);
        }
        // Be cautious; other threads may use the cached stmt, and set parameters.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, goodreadsBookId);
            stmt.bindLong(2, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Return a {@link BookCursor} for the given Goodreads book Id.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param grBookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksByGoodreadsBookId(final long grBookId) {
        String sql = getBookSql(TBL_BOOKS.dot(DOM_BOOK_GOODREADS_ID) + "=?");
        return (BookCursor) sSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY, sql,
                                                          new String[]{String.valueOf(grBookId)},
                                                          "");
    }

    /**
     * Query to get relevant {@link Book} columns for sending a set of books to Goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to get
     *                    since the last sync with Goodreads
     * @param updatesOnly true, if we only want the updated records
     *                    since the last sync with Goodreads
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksForExportToGoodreads(final long startId,
                                                     final boolean updatesOnly) {
        String sql = SqlSelectFullTable.GOODREADS_BOOK_DATA_TO_SEND
                     + " WHERE " + DOM_PK_ID + ">?";

        if (updatesOnly) {
            sql += " AND " + DOM_DATE_LAST_UPDATED + '>' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
        }

        // the order by is used to be able to restart an export.
        sql += " ORDER BY " + DOM_PK_ID;

        return (BookCursor) sSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY, sql,
                                                          new String[]{String.valueOf(startId)},
                                                          "");
    }

    /**
     * Query to get the ISBN for the given {@link Book} id, for sending to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBookForExportToGoodreads(final long bookId) {
        return (BookCursor) sSyncedDb.rawQueryWithFactory(
                BOOKS_CURSOR_FACTORY,
                SqlSelect.GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID,
                new String[]{String.valueOf(bookId)},
                "");
    }

    /**
     * Fills an array with the specified column from the passed SQL.
     *
     * @param sql        SQL to execute
     * @param columnName Column to fetch
     *
     * @return List of *all* values
     *
     * @see #getFirstColumnAsList
     */
    @NonNull
    private ArrayList<String> getColumnAsList(@NonNull final String sql,
                                              @NonNull final String columnName) {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
            int column = cursor.getColumnIndexOrThrow(columnName);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(column));
            }
            return list;
        }
    }

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList.
     *
     * @param cursor cursor
     *
     * @return List of unique values (case sensitive)
     *
     * @see #getColumnAsList
     */
    @NonNull
    private ArrayList<String> getFirstColumnAsList(@NonNull final Cursor cursor) {
        // Hash to avoid duplicates
        Set<String> set = new LinkedHashSet<>();
        while (cursor.moveToNext()) {
            String name = cursor.getString(0);
            if (name != null && !name.isEmpty()) {
                set.add(name);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * Return a ContentValues collection containing only those values from 'source'
     * that match columns in 'dest'.
     * <ul>
     * <li>Exclude the primary key from the list of columns.</li>
     * <li>data will be transformed based on the intended type of the underlying column
     * based on column definition (based on actual storage class of SQLite)
     * e.g. if a columns says it's Integer, an incoming boolean will be transformed to 0/1</li>
     * </ul>
     *
     * @param tableDefinition destination table
     * @param data            A collection with the columns to be set. May contain extra data.
     * @param bookLocale      the Locale to use for character case manipulation
     *
     * @return New and filtered ContentValues
     */
    @NonNull
    private ContentValues filterValues(@SuppressWarnings("SameParameterValue")
                                       @NonNull final TableDefinition tableDefinition,
                                       @NonNull final DataManager data,
                                       @NonNull final Locale bookLocale) {

        TableInfo tableInfo = tableDefinition.getTableInfo(sSyncedDb);

        ContentValues cv = new ContentValues();
        // Create the arguments
        for (String key : data.keySet()) {
            // Get column info for this column.
            ColumnInfo columnInfo = tableInfo.getColumn(key);
            // Check if we actually have a matching column.
            if (columnInfo != null) {
                // Never update PK.
                if (!columnInfo.isPrimaryKey) {
                    // Try to set the appropriate value, but if that fails, just use TEXT...
                    Object entry = data.get(key);
                    try {
                        switch (columnInfo.storageClass) {
                            case Real: {
                                if (entry instanceof Float) {
                                    cv.put(columnInfo.name, (Float) entry);
                                } else if (entry instanceof Double) {
                                    cv.put(columnInfo.name, (Double) entry);
                                } else if (entry != null) {
                                    String stringValue = entry.toString().trim();
                                    if (!stringValue.isEmpty()) {
                                        // All Locales taken into account.
                                        cv.put(columnInfo.name,
                                               ParseUtils.parseDouble(stringValue));
                                    } else {
                                        cv.put(columnInfo.name, "");
                                    }
                                }
                                break;
                            }
                            case Integer: {
                                if (entry instanceof Boolean) {
                                    if ((Boolean) entry) {
                                        cv.put(columnInfo.name, 1);
                                    } else {
                                        cv.put(columnInfo.name, 0);
                                    }
                                } else if (entry instanceof Integer) {
                                    cv.put(columnInfo.name, (Integer) entry);
                                } else if (entry instanceof Long) {
                                    cv.put(columnInfo.name, (Long) entry);
                                } else if (entry != null) {
                                    String s = entry.toString().toLowerCase(bookLocale);
                                    if (!s.isEmpty()) {
                                        // It's not strictly needed to do these conversions.
                                        // parseInt/catch works, but it's not elegant...
                                        switch (s) {
                                            case "1":
                                            case "true":
                                            case "t":
                                            case "yes":
                                            case "y":
                                                cv.put(columnInfo.name, 1);
                                                break;

                                            case "0":
                                            case "false":
                                            case "f":
                                            case "no":
                                            case "n":
                                                cv.put(columnInfo.name, 0);
                                                break;

                                            default:
                                                cv.put(columnInfo.name, Integer.parseInt(s));
                                        }

                                    } else {
                                        // s.isEmpty
                                        cv.put(columnInfo.name, "");
                                    }
                                }
                                break;
                            }
                            case Text: {
                                if (entry instanceof String) {
                                    cv.put(columnInfo.name, (String) entry);
                                } else if (entry != null) {
                                    cv.put(columnInfo.name, entry.toString());
                                }
                                break;
                            }
                            case Blob: {
                                if (entry instanceof byte[]) {
                                    cv.put(columnInfo.name, (byte[]) entry);
                                } else if (entry != null) {
                                    throw new IllegalArgumentException(
                                            "non-null Blob but not a byte[] ?"
                                            + " column.name=" + columnInfo.name
                                            + ", key=" + key);
                                }
                                break;
                            }
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        Logger.error(this, e,
                                     "column=" + columnInfo.name,
                                     "stringValue=" + entry.toString());
                        // not really ok, but let's store it anyhow.
                        cv.put(columnInfo.name, entry.toString());
                    }
                }
            }
        }
        return cv;
    }

    /**
     * Send the book details from the cursor to the passed fts query.
     * <p>
     * <strong>Note:</strong> This assumes a specific order for query parameters.
     * If modified, then update {@link SqlFTS#INSERT_BODY}, {@link SqlFTS#UPDATE}
     *
     * @param bookCursor Cursor of books to update
     * @param stmt       Statement to execute (insert or update)
     */
    private void ftsSendBooks(@NonNull final BookCursor bookCursor,
                              @NonNull final SynchronizedStatement stmt) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        // Accumulator for author names for each book
        StringBuilder authorText = new StringBuilder();
        // Accumulator for series titles for each book
        StringBuilder seriesText = new StringBuilder();
        // Accumulator for titles for each anthology
        StringBuilder titleText = new StringBuilder();

        // Indexes of fields in cursor, -2 for 'not initialised yet'
        int colGivenNames = -2;
        int colFamilyName = -2;
        int colSeriesInfo = -2;
        int colTOCEntryAuthorInfo = -2;
        int colTOCEntryInfo = -2;

        // Process each book
        while (bookCursor.moveToNext()) {
            // Reset authors/series/title
            authorText.setLength(0);
            seriesText.setLength(0);
            titleText.setLength(0);

            titleText.append(bookCursor.getString(DOM_TITLE.getName())).append(";");

            // Get list of authors
            try (Cursor authors = sSyncedDb.rawQuery(
                    SqlFTS.GET_AUTHORS_BY_BOOK_ID,
                    new String[]{String.valueOf(bookCursor.getLong(DOM_PK_ID.getName()))})) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = authors.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.getName());
                }
                if (colFamilyName < 0) {
                    colFamilyName = authors.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.getName());
                }
                // Append each author
                while (authors.moveToNext()) {
                    authorText.append(authors.getString(colGivenNames));
                    authorText.append(' ');
                    authorText.append(authors.getString(colFamilyName));
                    authorText.append(';');
                }
            }

            // Get list of series
            try (Cursor series = sSyncedDb.rawQuery(
                    SqlFTS.GET_SERIES_BY_BOOK_ID,
                    new String[]{String.valueOf(bookCursor.getLong(DOM_PK_ID.getName()))})) {
                // Get column indexes, if not already got
                if (colSeriesInfo < 0) {
                    colSeriesInfo = series.getColumnIndexOrThrow(SqlFTS.DOM_SERIES_INFO);
                }
                // Append each series
                while (series.moveToNext()) {
                    seriesText.append(series.getString(colSeriesInfo));
                    seriesText.append(';');
                }
            }

            // Get list of anthology data (author and title)
            try (Cursor tocEntries = sSyncedDb.rawQuery(
                    SqlFTS.GET_TOC_ENTRIES_BY_BOOK_ID,
                    new String[]{String.valueOf(bookCursor.getLong(DOM_PK_ID.getName()))})) {
                // Get column indexes, if not already got
                if (colTOCEntryAuthorInfo < 0) {
                    colTOCEntryAuthorInfo =
                            tocEntries.getColumnIndexOrThrow(SqlFTS.DOM_TOC_ENTRY_AUTHOR_INFO);
                }
                if (colTOCEntryInfo < 0) {
                    colTOCEntryInfo =
                            tocEntries.getColumnIndexOrThrow(SqlFTS.DOM_TOC_ENTRY_TITLE);
                }
                while (tocEntries.moveToNext()) {
                    authorText.append(tocEntries.getString(colTOCEntryAuthorInfo));
                    authorText.append(';');
                    titleText.append(tocEntries.getString(colTOCEntryInfo));
                    titleText.append(';');
                }
            }

            // Be cautious; other threads may use the cached stmt, and set parameters.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                bindStringOrNull(stmt, 1, authorText.toString());
                bindStringOrNull(stmt, 2, titleText.toString());
                bindStringOrNull(stmt, 3, seriesText.toString());
                bindStringOrNull(stmt, 4, bookCursor.getString(DOM_BOOK_DESCRIPTION.getName()));
                bindStringOrNull(stmt, 5, bookCursor.getString(DOM_BOOK_NOTES.getName()));
                bindStringOrNull(stmt, 6, bookCursor.getString(DOM_BOOK_PUBLISHER.getName()));
                bindStringOrNull(stmt, 7, bookCursor.getString(DOM_BOOK_GENRE.getName()));
                bindStringOrNull(stmt, 8, bookCursor.getString(DOM_BOOK_LOCATION.getName()));
                bindStringOrNull(stmt, 9, bookCursor.getString(DOM_BOOK_ISBN.getName()));
                // DOM_PK_DOCID
                stmt.bindLong(10, bookCursor.getLong(DOM_PK_ID.getName()));

                stmt.execute();
            }
        }
    }

    /**
     * Normalize a given string to contain only ASCII characters so we can easily text searches.
     *
     * @param text to normalize
     *
     * @return ascii text
     */
    private String toAscii(@NonNull final CharSequence text) {
        return ASCII_REGEX.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                          .replaceAll("");
    }

    /**
     * Bind a string or {@code null} value to a parameter since binding a {@code null}
     * in bindString produces an error.
     * <p>
     * <strong>Note:</strong> We specifically want to use the default Locale for this.
     */
    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                  final int position,
                                  @Nullable final String text) {
        if (text == null) {
            stmt.bindNull(position);
        } else {
            stmt.bindString(position, toAscii(text));
        }
    }

    /**
     * Insert a book into the FTS. Assumes book does not already exist in FTS.
     * <p>
     * Transaction: required
     */
    private void insertFts(final long bookId) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        try {
            SynchronizedStatement stmt = mStatements.get(STMT_INSERT_FTS);
            if (stmt == null) {
                stmt = mStatements.add(STMT_INSERT_FTS, SqlFTS.INSERT);
            }

            try (BookCursor books = (BookCursor) sSyncedDb.rawQueryWithFactory(
                    BOOKS_CURSOR_FACTORY,
                    SqlSelect.BOOK_BY_ID, new String[]{String.valueOf(bookId)}, "")) {
                ftsSendBooks(books, stmt);
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(this, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Update an existing FTS record.
     * <p>
     * Transaction: required
     */
    private void updateFts(final long bookId) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException();
        }

        try {
            SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_FTS);
            if (stmt == null) {
                stmt = mStatements.add(STMT_UPDATE_FTS, SqlFTS.UPDATE);
            }
            try (BookCursor books = (BookCursor) sSyncedDb.rawQueryWithFactory(
                    BOOKS_CURSOR_FACTORY,
                    SqlSelect.BOOK_BY_ID, new String[]{String.valueOf(bookId)}, "")) {
                ftsSendBooks(books, stmt);
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(this, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Search the FTS table and return a cursor.
     *
     * @param author      Author related keywords to find
     * @param title       Title related keywords to find
     * @param seriesTitle Series title related keywords to find
     * @param keywords    Keywords to find anywhere in book; this includes titles and authors
     *
     * @return a cursor, or {@code null} if all input was empty
     */
    @Nullable
    public Cursor searchFts(@Nullable final String author,
                            @Nullable final String title,
                            @Nullable final String seriesTitle,
                            @Nullable final String keywords) {
        String sql = getFtsSearchSQL(author, title, seriesTitle, keywords);
        if (sql == null) {
            return null;
        }

        return sSyncedDb.rawQuery(sql, null);
    }

    /**
     * Rebuild the entire FTS database.
     * This can take several seconds with many books or a slow phone.
     */
    public void rebuildFts() {
        if (sSyncedDb.inTransaction()) {
            throw new TransactionException();
        }

        long t0;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            t0 = System.nanoTime();
        }
        boolean gotError = false;

        // Make a copy of the FTS table definition for our temporary table.
        TableDefinition ftsTemp = TBL_BOOKS_FTS.clone();
        // Give it a new name
        ftsTemp.setName(ftsTemp.getName() + "_temp");

        SyncLock txLock = sSyncedDb.beginTransaction(true);

        try {
            // Drop and recreate our temp copy
            ftsTemp.drop(sSyncedDb);
            ftsTemp.create(sSyncedDb, false);

            try (SynchronizedStatement insert = sSyncedDb.compileStatement(
                    "INSERT INTO " + ftsTemp.getName() + SqlFTS.INSERT_BODY);
                 BookCursor books = (BookCursor) sSyncedDb.rawQueryWithFactory(
                         BOOKS_CURSOR_FACTORY, SqlSelectFullTable.BOOKS, null, "")) {
                ftsSendBooks(books, insert);
            }

            sSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(this, e);
            gotError = true;
        } finally {
            sSyncedDb.endTransaction(txLock);
            /*
            http://sqlite.1065341.n5.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td11430.html
            FTS tables should only be renamed outside of transactions.
            */
            //  Delete old table and rename the new table
            if (!gotError) {
                // Drop old table, ready for rename
                TBL_BOOKS_FTS.drop(sSyncedDb);
                sSyncedDb.execSQL("ALTER TABLE " + ftsTemp + " RENAME TO " + TBL_BOOKS_FTS);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.debug(this, "rebuildFts",
                         (System.nanoTime() - t0) + "nano");
        }
    }

    /**
     * Repopulate all OrderBy TITLE columns
     * <p>
     * Book + TOCEntry: DOM_TITLE  ==> DOM_TITLE_OB
     * Series:          DOM_SERIES_TITLE => DOM_SERIES_TITLE_OB
     */
    public void rebuildOrderByTitleColumns() {

        Context context = App.getLocalizedAppContext();

        // key: the book language (ISO3)
        Map<String, Locale> locales = new HashMap<>();

        long id;
        String title;
        String language;
        Locale locale;
        String obTitle;

        // Books
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.BOOK_TITLES, null)) {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                title = cursor.getString(1);

                language = cursor.getString(2);
                if (!locales.containsKey(language)) {
                    locale = new Locale(LanguageUtils.iso3ToBibliographic(language));
                    locales.put(language, locale);
                } else {
                    locale = locales.get(language);
                }

                obTitle = LocaleUtils.reorderTitle(context, title, locale);
                if (!title.equals(obTitle)) {
                    ContentValues cv = new ContentValues();
                    //noinspection ConstantConditions
                    cv.put(DOM_TITLE_OB.getName(), encodeOrderByColumn(obTitle, locale));
                    sSyncedDb.update(TBL_BOOKS.getName(), cv, DOM_PK_ID + "=?",
                                     new String[]{String.valueOf(id)});
                }
            }
        }

        locale = Locale.getDefault();

        // Series - using the user Locale.
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.SERIES_TITLES, null)) {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                title = cursor.getString(1);
                // use the user Locale.
                obTitle = LocaleUtils.reorderTitle(context, title, locale);
                if (!title.equals(obTitle)) {
                    ContentValues cv = new ContentValues();
                    cv.put(DOM_SERIES_TITLE_OB.getName(), encodeOrderByColumn(obTitle, locale));
                    sSyncedDb.update(TBL_SERIES.getName(), cv, DOM_PK_ID + "=?",
                                     new String[]{String.valueOf(id)});
                }
            }
        }

        // TOC Entries - should use primary book or Author Locale... but that is a huge overhead.
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.TOC_ENTRY_TITLES, null)) {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                title = cursor.getString(1);
                // use the user Locale.
                obTitle = LocaleUtils.reorderTitle(context, title, locale);
                if (!title.equals(obTitle)) {
                    ContentValues cv = new ContentValues();
                    cv.put(DOM_TITLE_OB.getName(), encodeOrderByColumn(obTitle, locale));
                    sSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv, DOM_PK_ID + "=?",
                                     new String[]{String.valueOf(id)});
                }
            }
        }
    }

    /**
     * DEBUG / TEMPORARY. Will be deleted soon!
     */
    public void tempUnMangle() {

        Context context = App.getLocalizedAppContext();

        // key: the book language (ISO3)
        Map<String, Locale> locales = new HashMap<>();

        long id;
        String title;
        String language;
        Locale locale;
        String obTitle;

        // Books
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.BOOK_TITLES, null)) {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                title = cursor.getString(1);

                language = cursor.getString(2);
                if (!locales.containsKey(language)) {
                    locale = new Locale(LanguageUtils.iso3ToBibliographic(language));
                    locales.put(language, locale);
                } else {
                    locale = locales.get(language);
                }

                title = unmangle(title);
                obTitle = LocaleUtils.reorderTitle(context, title, locale);

                ContentValues cv = new ContentValues();
                cv.put(DOM_TITLE.getName(), title);
                //noinspection ConstantConditions
                cv.put(DOM_TITLE_OB.getName(), encodeOrderByColumn(obTitle, locale));
                sSyncedDb.update(TBL_BOOKS.getName(), cv,
                                 DOM_PK_ID + "=?", new String[]{String.valueOf(id)});
            }
        }

        locale = Locale.getDefault();

        Logger.debug(this, "unmangle", "starting SERIES");
        // Series
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.SERIES_TITLES, null)) {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                title = cursor.getString(1);

                title = unmangle(title);
                obTitle = LocaleUtils.reorderTitle(context, title, locale);

                ContentValues cv = new ContentValues();
                cv.put(DOM_SERIES_TITLE.getName(), title);
                cv.put(DOM_SERIES_TITLE_OB.getName(), encodeOrderByColumn(obTitle, locale));
                sSyncedDb.update(TBL_SERIES.getName(), cv,
                                 DOM_PK_ID + "=?", new String[]{String.valueOf(id)});
            }
        }

        Logger.debug(this, "unmangle", "starting TOCS");

        // TOC Entries - should use the primary book or Author Locale... huge overhead.
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.TOC_ENTRY_TITLES, null)) {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                title = cursor.getString(1);

                title = unmangle(title);
                obTitle = LocaleUtils.reorderTitle(context, title, locale);

                ContentValues cv = new ContentValues();
                cv.put(DOM_TITLE.getName(), title);
                cv.put(DOM_TITLE_OB.getName(), encodeOrderByColumn(obTitle, locale));
                sSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv,
                                 DOM_PK_ID + "=?", new String[]{String.valueOf(id)});
            }
        }
        Logger.debug(this, "unmangle", "DONE");

    }

    /**
     * DEBUG / TEMPORARY. Will be deleted soon!
     */
    @SuppressWarnings("DuplicateExpressions")
    private String unmangle(@NonNull final String title) {
        if (title.endsWith(", A")) {
            return "A " + title.substring(0, title.length() - 3);
        } else if (title.endsWith(", An")) {
            return "An " + title.substring(0, title.length() - 4);
        } else if (title.endsWith(", The")) {
            return "The " + title.substring(0, title.length() - 5);
        } else if (title.endsWith(", De")) {
            return "De " + title.substring(0, title.length() - 4);
        } else if (title.endsWith(", Een")) {
            return "Een " + title.substring(0, title.length() - 5);
        } else if (title.endsWith(", Het")) {
            return "Het " + title.substring(0, title.length() - 5);
        } else {
            return title;
        }
    }

    /**
     * DEBUG only.
     */
    private static class InstanceRefDebug
            extends WeakReference<DAO> {

        @NonNull
        private final Throwable mCreationStackTrace;

        /**
         * Constructor.
         *
         * @param db Database Access
         */
        InstanceRefDebug(@NonNull final DAO db) {
            super(db);
            mCreationStackTrace = new Throwable();
        }

        @Override
        @NonNull
        public String toString() {
            return "DAOInstanceRefDebug{"
                   + "mCreationStackTrace=\n" + Log.getStackTraceString(mCreationStackTrace)
                   + "\n}";
        }
    }

    /**
     * 2 forms of usage.
     * <p>
     * Books linked authors and series
     * PREFIX + ALL_BOOKS [+ where-clause] + SUFFIX
     * <p>
     * Just the books
     * ALL_BOOKS [+ where-clause]
     */
    private static class SqlAllBooks {

        /**
         * {@link #getBookSql(String)}.
         */
        private static final String PREFIX =
                "SELECT b.*"
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                + ','
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN
                + " AS " + DOM_AUTHOR_FORMATTED
                + ','
                + SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                + " AS " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST

                + ',' + "a." + DOM_FK_AUTHOR + " AS " + DOM_FK_AUTHOR

                // use a dummy series for books not in a series
                // (i.e. don't use null's)
                + ',' + "COALESCE(s." + DOM_FK_SERIES + ", 0) AS " + DOM_FK_SERIES
                + ',' + "COALESCE(s." + DOM_SERIES_TITLE + ", '') AS " + DOM_SERIES_TITLE
                + ',' + "COALESCE(s." + DOM_BOOK_NUM_IN_SERIES
                + ", '') AS " + DOM_BOOK_NUM_IN_SERIES;

        /**
         * set of fields suitable for a select of a Book.
         * <p>
         * FIXME: could we not just do books.* ?
         * <b>Developer:</b> adding fields ? Now is a good time to update {@link Book#duplicate}/
         */
        private static final String BOOK =
                TBL_BOOKS.dotAs(DOM_PK_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_UUID)
                + ',' + TBL_BOOKS.dotAs(DOM_TITLE)
                // publication data
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISBN)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PUBLISHER)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_TOC_BITMASK)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_PUBLISHED)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED_CURRENCY)
                + ',' + TBL_BOOKS.dotAs(DOM_DATE_FIRST_PUBLICATION)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_FORMAT)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GENRE)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LANGUAGE)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PAGES)
                // common blurb
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DESCRIPTION)

                // partially edition info, partially user-owned info.
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_EDITION_BITMASK)
                // user data
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_NOTES)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LOCATION)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_SIGNED)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_RATING)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ_START)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ_END)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_ACQUIRED)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID_CURRENCY)
                // added/updated
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_ADDED)
                + ',' + TBL_BOOKS.dotAs(DOM_DATE_LAST_UPDATED)
                // external links
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LIBRARY_THING_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_OPEN_LIBRARY_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISFDB_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_LAST_SYNC_DATE)

                // Find the first (i.e. primary) Series
                + ','
                + "(SELECT " + DOM_FK_SERIES + " FROM " + TBL_BOOK_SERIES.ref()
                + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK) + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                + " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC LIMIT 1)"
                + " AS " + DOM_FK_SERIES

                // Find the first (i.e. primary) Series number
                + ','
                + "(SELECT " + DOM_BOOK_NUM_IN_SERIES + " FROM " + TBL_BOOK_SERIES.ref()
                + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK) + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                + " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC LIMIT 1"
                + ") AS " + DOM_BOOK_NUM_IN_SERIES

                // Get the total series count
                + ','
                + "(SELECT COUNT(*) FROM " + TBL_BOOK_SERIES.ref()
                + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK) + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                + ')' + " AS " + COLUMN_ALIAS_NR_OF_SERIES

                // Find the first (i.e. primary) Author
                + ','
                + "(SELECT " + DOM_FK_AUTHOR + " FROM " + TBL_BOOK_AUTHOR.ref()
                + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK) + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                + " ORDER BY " + DOM_BOOK_AUTHOR_POSITION
                + ',' + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR) + " LIMIT 1"
                + ") AS " + DOM_FK_AUTHOR

//                // Get the total author count. No longer needed, but leaving for future use.
//                + ','
//                + "(SELECT COUNT(*) FROM " + TBL_BOOK_AUTHOR.ref()
//                + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK) + '=' + TBL_BOOKS.dot(DOM_PK_ID)
//                + ')' + " AS " + COLUMN_ALIAS_NR_OF_AUTHORS
                ;

        /**
         * {@link #getBookSql(String)}.
         * {@link #fetchBooksForExport(Date)}.
         */
        private static final String ALL_BOOKS =
                "SELECT DISTINCT " + BOOK
                // use an empty loanee (i.e. don't use null's) if there is no loanee
                + ',' + "COALESCE(" + DOM_LOANEE + ", '') AS " + DOM_LOANEE

                + " FROM " + TBL_BOOKS.ref() + " LEFT OUTER JOIN "
                + TBL_BOOK_LOANEE.ref() + " ON (" + TBL_BOOK_LOANEE.dot(DOM_FK_BOOK) + '='
                + TBL_BOOKS.dot(DOM_PK_ID) + ')';

        /**
         * {@link #getBookSql(String)}.
         */
        private static final String SUFFIX =
                // with their primary author
                " JOIN ("
                + "SELECT " + DOM_FK_AUTHOR
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                + ','
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN
                + " AS " + DOM_AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dotAs(DOM_FK_BOOK)

                + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + ") a ON a." + DOM_FK_BOOK + "=b." + DOM_PK_ID
                + " AND a." + DOM_FK_AUTHOR + "=b." + DOM_FK_AUTHOR

                // and (if they have one) their primary series
                + " LEFT OUTER JOIN ("
                + "SELECT " + DOM_FK_SERIES
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_TITLE)
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(DOM_BOOK_NUM_IN_SERIES)
                + ',' + TBL_BOOK_SERIES.dotAs(DOM_FK_BOOK)
                + ',' + SqlColumns.EXP_SERIES_WITH_NUMBER + " AS "
                + DOM_SERIES_FORMATTED

                + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + ") s ON s." + DOM_FK_BOOK + "=b." + DOM_PK_ID
                + " AND s." + DOM_FK_SERIES + "=b." + DOM_FK_SERIES
                + " AND lower(s." + DOM_BOOK_NUM_IN_SERIES
                + ")=lower(b." + DOM_BOOK_NUM_IN_SERIES + ')'
                + COLLATION;
    }

    /**
     * Commonly used SQL table columns.
     */
    public static final class SqlColumns {

        /**
         * SQL column: SORT author names in 'lastgiven' form.
         * Uses the OB field.
         * Not used for display.
         */
        public static final String EXP_AUTHOR_SORT_LAST_FIRST =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES_OB) + "=''"
                + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME_OB)
                + " ELSE " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME_OB) + "||"
                + /*      */ TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES_OB)
                + " END";

        /**
         * SQL column: SORT author names in 'givenlast' form.
         * Uses the OB field.
         * Not used for display.
         */
        public static final String EXP_AUTHOR_SORT_FIRST_LAST =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES_OB) + "=''"
                + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME_OB)
                + " ELSE " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES_OB) + "||"
                + /*      */ TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME_OB)
                + " END";

        /**
         * Single column, with the formatted name of the Author.
         * <p>
         * If no given name -> "FamilyName"
         * otherwise -> "FamilyName, GivenNames"
         */
        public static final String EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
                + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                + " ELSE " + (TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                              + " || ', ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES))
                + " END";

        /**
         * Single column, with the formatted name of the Author.
         * <p>
         * If no given name -> "FamilyName"
         * otherwise -> "GivenNames FamilyName"
         */
        public static final String EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
                + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                + " ELSE " + (TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
                              + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME))
                + " END";

        public static final String EXP_PRIMARY_SERIES_COUNT_AS_BOOLEAN =
                "CASE"
                + " WHEN COALESCE(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ",1)=1"
                + " THEN 1 ELSE 0"
                + " END";
        /**
         * SQL column: return 1 if the book is available, 0 if not.
         * {@link DBDefinitions#DOM_LOANEE_AS_BOOLEAN}
         */
        public static final String EXP_LOANEE_AS_BOOLEAN =
                "CASE"
                + " WHEN " + TBL_BOOK_LOANEE.dot(DOM_LOANEE) + " IS NULL"
                + " THEN 1 ELSE 0"
                + " END";
        /**
         * SQL column: return "" if the book is available, "loanee name" if not.
         */
        public static final String EXP_BOOK_LOANEE_OR_EMPTY =
                "CASE"
                + " WHEN " + TBL_BOOK_LOANEE.dot(DOM_LOANEE) + " IS NULL"
                + " THEN '' ELSE " + TBL_BOOK_LOANEE.dot(DOM_LOANEE)
                + " END";
        /**
         * Single column, with the formatted name of the Series.
         * <p>
         * If no number -> "SeriesName".
         * otherwise -> "SeriesName #number"
         */
        private static final String EXP_SERIES_WITH_NUMBER =
                "CASE"
                + " WHEN " + DOM_BOOK_NUM_IN_SERIES + "=''"
                + " THEN " + DOM_SERIES_TITLE
                + " ELSE " + DOM_SERIES_TITLE + " || ' #' || " + DOM_BOOK_NUM_IN_SERIES
                + " END";
        /**
         * Single column, with the formatted name of the Series.
         * <p>
         * If no number -> "SeriesName".
         * otherwise -> "SeriesName (number)"
         */
        private static final String EXP_SERIES_WITH_NUMBER_IN_BRACKETS =
                "CASE"
                + " WHEN " + DOM_BOOK_NUM_IN_SERIES + "=''"
                + " THEN " + DOM_SERIES_TITLE
                + " ELSE " + DOM_SERIES_TITLE + " || ' (' || " + DOM_BOOK_NUM_IN_SERIES + " || ')'"
                + " END";
    }

    /**
     * Sql SELECT of a single table, without a WHERE clause.
     */
    private static final class SqlSelectFullTable {

        /**
         * Columns from {@link DBDefinitions#TBL_BOOKS} we need to send a Book to Goodreads.
         * <p>
         * See {@link GoodreadsManager#sendOneBook}
         * -> notes column disabled for now.
         */
        static final String GOODREADS_BOOK_DATA_TO_SEND =
                "SELECT " + DOM_PK_ID
                + ',' + DOM_BOOK_ISBN
                + ',' + DOM_BOOK_GOODREADS_ID
                + ',' + DOM_BOOK_READ
                + ',' + DOM_BOOK_READ_START
                + ',' + DOM_BOOK_READ_END
                + ',' + DOM_BOOK_RATING
                //+ ',' + DOM_BOOK_NOTES
                + " FROM " + TBL_BOOKS;

        /** {@link Book}, all columns. */
        private static final String BOOKS =
                "SELECT * FROM " + TBL_BOOKS;

        /** {@link Author}, all columns. */
        private static final String AUTHORS =
                "SELECT * FROM " + TBL_AUTHORS;

        /** {@link Series}, all columns. */
        private static final String SERIES =
                "SELECT * FROM " + TBL_SERIES;

        /** {@link Bookshelf} all columns. */
        private static final String BOOKSHELVES =
                "SELECT "
                + TBL_BOOKSHELF.dot(DOM_PK_ID)
                + ',' + TBL_BOOKSHELF.dot(DOM_BOOKSHELF)
                + ',' + TBL_BOOKSHELF.dot(DOM_FK_STYLE_ID)
                + ',' + TBL_BOOKLIST_STYLES.dot(DOM_UUID)
                + " FROM " + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES);

        /** {@link Bookshelf} all columns. Ordered, will be displayed to user. */
        private static final String BOOKSHELVES_ORDERED =
                BOOKSHELVES + " ORDER BY lower(" + DOM_BOOKSHELF + ')' + COLLATION;

        /** {@link BooklistStyle} all columns. */
        private static final String BOOKLIST_STYLES =
                "SELECT * FROM " + TBL_BOOKLIST_STYLES;

        /** Book UUID only, for accessing all cover image files. */
        private static final String BOOK_ALL_UUID =
                "SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_FAMILY_NAMES =
                "SELECT DISTINCT "
                + DOM_AUTHOR_FAMILY_NAME
                + ',' + DOM_AUTHOR_FAMILY_NAME_OB
                + " FROM " + TBL_AUTHORS
                + " ORDER BY " + DOM_AUTHOR_FAMILY_NAME_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_GIVEN_NAMES =
                "SELECT DISTINCT "
                + DOM_AUTHOR_GIVEN_NAMES
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB
                + " FROM " + TBL_AUTHORS
                + " ORDER BY " + DOM_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_FORMATTED_NAMES =
                "SELECT "
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN + " AS " + DOM_AUTHOR_FORMATTED
                + ',' + DOM_AUTHOR_FAMILY_NAME_OB
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB
                + " FROM " + TBL_AUTHORS.ref()
                + " ORDER BY " + DOM_AUTHOR_FAMILY_NAME_OB + COLLATION
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_FORMATTED_NAMES_GIVEN_FIRST =
                "SELECT "
                + SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY + " AS "
                + DOM_AUTHOR_FORMATTED_GIVEN_FIRST
                + ',' + DOM_AUTHOR_FAMILY_NAME_OB
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB
                + " FROM " + TBL_AUTHORS.ref()
                + " ORDER BY " + DOM_AUTHOR_FAMILY_NAME_OB + COLLATION
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String SERIES_NAME =
                "SELECT "
                + DOM_SERIES_TITLE
                + ',' + DOM_SERIES_TITLE_OB
                + " FROM " + TBL_SERIES
                + " ORDER BY " + DOM_SERIES_TITLE_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String FORMATS =
                "SELECT DISTINCT " + DOM_BOOK_FORMAT
                + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_FORMAT + ')' + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String GENRES =
                "SELECT DISTINCT " + DOM_BOOK_GENRE
                + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_GENRE + ')' + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String LANGUAGES =
                "SELECT DISTINCT " + DOM_BOOK_LANGUAGE
                + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_LANGUAGE + ')' + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String LOCATIONS =
                "SELECT DISTINCT " + DOM_BOOK_LOCATION
                + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_LOCATION + ')' + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String PUBLISHERS =
                "SELECT DISTINCT " + DOM_BOOK_PUBLISHER
                + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_PUBLISHER + ')' + COLLATION;

        /**
         * All Book titles for a rebuild of the {@link DBDefinitions#DOM_TITLE_OB} column.
         */
        private static final String BOOK_TITLES =
                "SELECT " + DOM_PK_ID + ',' + DOM_TITLE + ',' + DOM_BOOK_LANGUAGE
                + " FROM " + TBL_BOOKS;

        /**
         * All Series for a rebuild of the {@link DBDefinitions#DOM_SERIES_TITLE_OB} column.
         */
        private static final String SERIES_TITLES =
                "SELECT " + DOM_PK_ID + ',' + DOM_SERIES_TITLE + " FROM " + TBL_SERIES;

        /**
         * All Series for a rebuild of the {@link DBDefinitions#DOM_TITLE_OB} column.
         */
        private static final String TOC_ENTRY_TITLES =
                "SELECT " + DOM_PK_ID + ',' + DOM_TITLE + " FROM " + TBL_TOC_ENTRIES;
    }

    /**
     * Sql SELECT returning a list, with a WHERE clause.
     */
    private static final class SqlSelectList {

        /**
         * All Bookshelves for a Book; ordered by name.
         */
        private static final String BOOKSHELVES_BY_BOOK_ID =
                "SELECT DISTINCT "
                + TBL_BOOKSHELF.dotAs(DOM_PK_ID)
                + ',' + TBL_BOOKSHELF.dotAs(DOM_BOOKSHELF)
                + ',' + TBL_BOOKSHELF.dotAs(DOM_FK_STYLE_ID)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(DOM_UUID)
                + " FROM " + TBL_BOOK_BOOKSHELF.ref()
                + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES)
                + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOK) + "=?"
                + " ORDER BY lower(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ')' + COLLATION;

        /**
         * All Authors for a Book; ordered by position, family, given.
         */
        private static final String AUTHORS_BY_BOOK_ID =
                "SELECT DISTINCT " + TBL_AUTHORS.dotAs(DOM_PK_ID)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME_OB)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES_OB)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                + ',' + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN + " AS "
                + DOM_AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION)
                + ',' + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_TYPE_BITMASK)
                + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK) + "=?"
                + " ORDER BY "
                + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + " ASC"
                + ',' + DOM_AUTHOR_FAMILY_NAME_OB + COLLATION + "ASC"
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB + COLLATION + "ASC";

        /**
         * All Series for a Book; ordered by position, name.
         */
        private static final String SERIES_BY_BOOK_ID =
                "SELECT DISTINCT " + TBL_SERIES.dotAs(DOM_PK_ID)
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_TITLE)
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_TITLE_OB)
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(DOM_BOOK_NUM_IN_SERIES)
                + ',' + TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_POSITION)
                + ',' + SqlColumns.EXP_SERIES_WITH_NUMBER_IN_BRACKETS + " AS "
                + DOM_SERIES_FORMATTED
                + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION)
                + ',' + TBL_SERIES.dot(DOM_SERIES_TITLE_OB) + COLLATION + "ASC";

        /**
         * All TocEntry's for a Book; ordered by position in the book.
         */
        private static final String TOC_ENTRIES_BY_BOOK_ID =
                "SELECT " + TBL_TOC_ENTRIES.dotAs(DOM_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_FK_AUTHOR)
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_TITLE)
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_DATE_FIRST_PUBLICATION)
                // for convenience, we fetch the Author here
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)

                + " FROM " + TBL_TOC_ENTRIES.ref()
                + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                + " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOK_TOC_ENTRIES.dot(DOM_BOOK_TOC_ENTRY_POSITION);

        /**
         * All Books (id only!) for a given Author.
         */
        private static final String BOOK_IDS_BY_AUTHOR_ID =
                "SELECT " + TBL_BOOKS.dotAs(DOM_PK_ID)
                + " FROM " + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR)
                + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR) + "=?";

        /**
         * All Books (id only!) for a given Series.
         */
        private static final String BOOK_IDS_BY_SERIES_ID =
                "SELECT " + TBL_BOOKS.dotAs(DOM_PK_ID)
                + " FROM " + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_SERIES)
                + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_SERIES) + "=?";

        /**
         * All Books (id only!) for a given Publisher.
         */
        private static final String BOOK_IDS_BY_PUBLISHER =
                "SELECT " + TBL_BOOKS.dotAs(DOM_PK_ID)
                + " FROM " + TBL_BOOKS.ref()
                + " WHERE " + TBL_BOOKS.dot(DOM_BOOK_PUBLISHER) + "=?";

        /**
         * All TocEntry's for an Author.
         * <p>
         * Order By clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need DOM_TITLE_OB as it will be used to ORDER BY
         */
        private static final String TOC_ENTRIES_BY_AUTHOR_ID =
                "SELECT " + "'" + TocEntry.Type.TYPE_TOC + "' AS " + DOM_TOC_TYPE
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_TITLE)
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_TITLE_OB)
                + ',' + TBL_TOC_ENTRIES.dotAs(DOM_DATE_FIRST_PUBLICATION)
                + ", COUNT(" + TBL_TOC_ENTRIES.dot(DOM_PK_ID) + ") AS " + DOM_BL_BOOK_COUNT
                + " FROM " + TBL_TOC_ENTRIES.ref()
                + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + " WHERE " + TBL_TOC_ENTRIES.dot(DOM_FK_AUTHOR) + "=?"
                + " GROUP BY " + DOM_PK_ID;


        /**
         * All Book titles and their first pub. date, for an Author..
         * <p>
         * Order By clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need DOM_TITLE_OB as it will be used to ORDER BY
         */
        private static final String BOOK_TITLES_BY_AUTHOR_ID =
                "SELECT " + "'" + TocEntry.Type.TYPE_BOOK + "' AS " + DOM_TOC_TYPE
                + ',' + TBL_BOOKS.dotAs(DOM_PK_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_TITLE)
                + ',' + TBL_BOOKS.dotAs(DOM_TITLE_OB)
                + ',' + TBL_BOOKS.dotAs(DOM_DATE_FIRST_PUBLICATION)
                + ",1 AS " + DOM_BL_BOOK_COUNT
                + " FROM " + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR)
                + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR) + "=?";

        /**
         * All TocEntry's + book titles for an Author; ordered by title.
         */
        private static final String WORKS_BY_AUTHOR_ID =
                // MUST be toc first, books second; otherwise the GROUP BY is done on the whole
                // UNION instead of on the toc only
                TOC_ENTRIES_BY_AUTHOR_ID + " UNION " + BOOK_TITLES_BY_AUTHOR_ID;
    }

    /**
     * Sql SELECT that returns a single ID/UUID.
     */
    private static final class SqlGet {

        /**
         * Find the Book id based on a search for the ISBN (10 OR 13).
         */
        static final String BOOK_ID_BY_ISBN =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                + " WHERE lower(" + DOM_BOOK_ISBN + ")=lower(?)";
        /**
         * Find the Book id based on a search for the ISBN (both 10 & 13).
         */
        static final String BOOK_ID_BY_ISBN2 =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                + " WHERE lower(" + DOM_BOOK_ISBN + ") IN (lower(?),lower(?))";

        static final String BOOKLIST_STYLE_ID_BY_UUID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKLIST_STYLES
                + " WHERE " + DOM_UUID + "=?";

        static final String AUTHOR_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_AUTHORS
                + " WHERE " + DOM_AUTHOR_FAMILY_NAME_OB + "=?" + COLLATION
                + " AND " + DOM_AUTHOR_GIVEN_NAMES_OB + "=?" + COLLATION;

        static final String BOOKSHELF_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + DOM_BOOKSHELF
                + " WHERE lower(" + DOM_BOOKSHELF + ")=lower(?)" + COLLATION;

        /**
         * Get the id of a {@link Series} by its Title.
         * Search on both "The Title" and "Title, The"
         */
        static final String SERIES_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_SERIES
                + " WHERE " + DOM_SERIES_TITLE_OB + "=?" + COLLATION
                + " OR " + DOM_SERIES_TITLE_OB + "=?" + COLLATION;

        /**
         * Get the id of a {@link TocEntry} by its Title.
         * Search on both "The Title" and "Title, The"
         */
        static final String TOC_ENTRY_ID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_TOC_ENTRIES
                + " WHERE " + DOM_FK_AUTHOR + "=?"
                + " AND (" + DOM_TITLE_OB + "=? " + COLLATION
                + " OR " + DOM_TITLE_OB + "=?" + COLLATION + ')';

        static final String BOOK_ID_BY_TOC_ENTRY_ID =
                "SELECT " + DOM_FK_BOOK + " FROM " + TBL_BOOK_TOC_ENTRIES
                + " WHERE " + DOM_FK_TOC_ENTRY + "=?";

        /**
         * Get the UUID of a {@link Book} by its id.
         */
        static final String BOOK_UUID_BY_ID =
                "SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS
                + " WHERE " + DOM_PK_ID + "=?";
        /**
         * Get the id of a {@link Book} by its UUID.
         */
        static final String BOOK_ID_BY_UUID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                + " WHERE " + DOM_BOOK_UUID + "=?";
        /**
         * Get the ISBN of a {@link Book} by its id.
         */
        static final String BOOK_ISBN_BY_BOOK_ID =
                "SELECT " + DOM_BOOK_ISBN + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";
        /**
         * Get the title of a {@link Book} by its id.
         */
        static final String BOOK_TITLE_BY_BOOK_ID =
                "SELECT " + DOM_TITLE + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";
        /**
         * Get the name of the loanee of a {@link Book}.
         */
        static final String LOANEE_BY_BOOK_ID =
                "SELECT " + DOM_LOANEE + " FROM " + TBL_BOOK_LOANEE
                + " WHERE " + DOM_FK_BOOK + "=?";
    }

    /**
     * Sql SELECT to get Objects by their id; and related queries.
     */
    private static final class SqlSelect {

        /**
         * Get a {@link Book} by its id.
         */
        static final String BOOK_BY_ID =
                SqlSelectFullTable.BOOKS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get a {@link Bookshelf} by its id.
         */
        static final String BOOKSHELF_BY_ID =
                SqlSelectFullTable.BOOKSHELVES + " WHERE " + TBL_BOOKSHELF.dot(DOM_PK_ID) + "=?";

        /**
         * Get a {@link Bookshelf} by its name.
         */
        static final String BOOKSHELF_BY_NAME =
                SqlSelectFullTable.BOOKSHELVES
                + " WHERE lower(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ")=lower(?)" + COLLATION;

        /**
         * Get an {@link Author} by its id.
         */
        static final String AUTHOR_BY_ID =
                SqlSelectFullTable.AUTHORS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get a {@link Series} by its id.
         */
        static final String SERIES_BY_ID =
                SqlSelectFullTable.SERIES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get the last-update-date for a {@link Book} by its id.
         */
        static final String LAST_UPDATE_DATE_BY_BOOK_ID =
                "SELECT " + DOM_DATE_LAST_UPDATED + " FROM " + TBL_BOOKS
                + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get the booklist extra fields including the bookshelves as a single csv string.
         * <p>
         * GROUP_CONCAT: The order of the concatenated elements is arbitrary.
         */
        static final String BOOK_EXTRAS =
                "SELECT "
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN
                + " AS " + DOM_AUTHOR_FORMATTED
                // no author type for now.
                //+ ',' + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_TYPE_BITMASK)

                + ',' + TBL_BOOKS.dot(DOM_BOOK_LOCATION)
                + ',' + TBL_BOOKS.dot(DOM_BOOK_FORMAT)
                + ',' + TBL_BOOKS.dot(DOM_BOOK_PUBLISHER)
                + ',' + TBL_BOOKS.dot(DOM_BOOK_ISBN)
                + ',' + TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED)

                + ',' + "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ",', ')"
                + " AS " + DOM_BOOKSHELF

                + " FROM " + TBL_BOOKS.ref()
                + TBL_BOOKS.join(TBL_BOOK_AUTHOR) + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + TBL_BOOKS.join(TBL_BOOK_BOOKSHELF) + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                + " WHERE " + TBL_BOOKS.dot(DOM_PK_ID) + "=?"
                // primary author only.
                + " GROUP BY " + DOM_AUTHOR_FORMATTED
                + " ORDER BY " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION)
                + " LIMIT 1";

        /**
         * Check if a {@link Book} exists.
         */
        static final String BOOK_EXISTS =
                "SELECT COUNT(*) " + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Count the number of {@link Book}'s in a {@link Series}.
         */
        static final String COUNT_BOOKS_IN_SERIES =
                "SELECT COUNT(" + DOM_FK_BOOK + ") FROM " + TBL_BOOK_SERIES
                + " WHERE " + DOM_FK_SERIES + "=?";

        /**
         * Count the number of {@link Book}'s by an {@link Author}.
         */
        static final String COUNT_BOOKS_BY_AUTHOR =
                "SELECT COUNT(" + DOM_FK_BOOK + ") FROM " + TBL_BOOK_AUTHOR
                + " WHERE " + DOM_FK_AUTHOR + "=?";

        static final String COUNT_TOC_ENTRIES_BY_AUTHOR =
                "SELECT COUNT(" + DOM_PK_ID + ") FROM " + TBL_TOC_ENTRIES
                + " WHERE " + DOM_FK_AUTHOR + "=?";

        /**
         * Get the needed fields of a {@link Book} to send to Goodreads.
         * <p>
         * param DOM_PK_ID of the Book
         */
        static final String GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID =
                SqlSelectFullTable.GOODREADS_BOOK_DATA_TO_SEND + " WHERE " + DOM_PK_ID + "=?";

        static final String SEARCH_SUGGESTIONS =
                "SELECT * FROM ("
                // Book Title
                + "SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_PK_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_TITLE, SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + TBL_BOOKS.dotAs(DOM_TITLE, SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + " FROM " + TBL_BOOKS.ref()
                + " WHERE " + TBL_BOOKS.dot(DOM_TITLE) + " LIKE ?"

                + " UNION "

                // Author Family Name
                + " SELECT \"AF\" || " + TBL_AUTHORS.dotAs(DOM_PK_ID)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME,
                                          SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME,
                                          SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + " FROM " + TBL_AUTHORS.ref()
                + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                + " LIKE ?"

                + " UNION "

                // Author Given Names
                + " SELECT \"AG\" || " + TBL_AUTHORS.dotAs(DOM_PK_ID)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES,
                                          SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES,
                                          SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + " FROM " + TBL_AUTHORS.ref()
                + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
                + " LIKE ?"

                + " UNION "

                // Book ISBN
                + " SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_PK_ID)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISBN,
                                        SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISBN,
                                        SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + " FROM " + TBL_BOOKS.ref()
                + " WHERE " + TBL_BOOKS.dot(DOM_BOOK_ISBN)
                + " LIKE ?"

                + " ) AS zzz "
                + " ORDER BY lower(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ')' + COLLATION;
    }

    /**
     * Sql INSERT.
     */
    private static final class SqlInsert {

        static final String BOOKSHELF =
                "INSERT INTO " + TBL_BOOKSHELF
                + '(' + DOM_BOOKSHELF
                + ',' + DOM_FK_STYLE_ID
                + ") VALUES (?,?)";

        static final String AUTHOR =
                "INSERT INTO " + TBL_AUTHORS
                + '(' + DOM_AUTHOR_FAMILY_NAME
                + ',' + DOM_AUTHOR_FAMILY_NAME_OB
                + ',' + DOM_AUTHOR_GIVEN_NAMES
                + ',' + DOM_AUTHOR_GIVEN_NAMES_OB
                + ',' + DOM_AUTHOR_IS_COMPLETE
                + ") VALUES (?,?,?,?,?)";

        static final String SERIES =
                "INSERT INTO " + TBL_SERIES
                + '(' + DOM_SERIES_TITLE
                + ',' + DOM_SERIES_TITLE_OB
                + ',' + DOM_SERIES_IS_COMPLETE
                + ") VALUES (?,?,?)";

        static final String TOC_ENTRY =
                "INSERT INTO " + TBL_TOC_ENTRIES
                + '(' + DOM_FK_AUTHOR
                + ',' + DOM_TITLE
                + ',' + DOM_TITLE_OB
                + ',' + DOM_DATE_FIRST_PUBLICATION
                + ") VALUES (?,?,?,?)";


        static final String BOOK_TOC_ENTRY =
                "INSERT INTO " + TBL_BOOK_TOC_ENTRIES
                + '(' + DOM_FK_TOC_ENTRY
                + ',' + DOM_FK_BOOK
                + ',' + DOM_BOOK_TOC_ENTRY_POSITION
                + ") VALUES (?,?,?)";

        static final String BOOK_BOOKSHELF =
                "INSERT INTO " + TBL_BOOK_BOOKSHELF
                + '(' + DOM_FK_BOOK
                + ',' + DOM_BOOKSHELF
                + ") VALUES (?,?)";

        static final String BOOK_AUTHOR =
                "INSERT INTO " + TBL_BOOK_AUTHOR
                + '(' + DOM_FK_BOOK
                + ',' + DOM_FK_AUTHOR
                + ',' + DOM_BOOK_AUTHOR_POSITION
                + ',' + DOM_BOOK_AUTHOR_TYPE_BITMASK
                + ") VALUES(?,?,?,?)";

        static final String BOOK_SERIES =
                "INSERT INTO " + TBL_BOOK_SERIES
                + '(' + DOM_FK_BOOK
                + ',' + DOM_FK_SERIES
                + ',' + DOM_BOOK_NUM_IN_SERIES
                + ',' + DOM_BOOK_SERIES_POSITION
                + ") VALUES(?,?,?,?)";

        static final String BOOK_LOANEE =
                "INSERT INTO " + TBL_BOOK_LOANEE
                + '(' + DOM_FK_BOOK
                + ',' + DOM_LOANEE
                + ") VALUES(?,?)";


        static final String BOOKLIST_STYLE =
                "INSERT INTO " + TBL_BOOKLIST_STYLES
                + '(' + DOM_UUID
                + ',' + DOM_STYLE_IS_BUILTIN
                + ") VALUES (?,?)";
    }

    /**
     * Sql UPDATE. Intention is to only have single-column updates here and do multi-column
     * with the ContentValues based update method.
     */
    private static final class SqlUpdate {

        /**
         * Update a single Book's last sync date with Goodreads.
         */
        static final String GOODREADS_LAST_SYNC_DATE =
                "UPDATE " + TBL_BOOKS + " SET "
                + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Update a single Book's Goodreads id. Do not update the last-update-date!
         */
        static final String GOODREADS_BOOK_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GOODREADS_ID + "=?"
                + " WHERE " + DOM_PK_ID + "=?";

        static final String AUTHOR_ON_TOC_ENTRIES =
                "UPDATE " + TBL_TOC_ENTRIES + " SET " + DOM_FK_AUTHOR + "=?"
                + " WHERE " + DOM_FK_AUTHOR + "=?";

        static final String FORMAT =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + DOM_BOOK_FORMAT + "=?"
                + " WHERE " + DOM_BOOK_FORMAT + "=?";

        static final String GENRE =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + DOM_BOOK_GENRE + "=?"
                + " WHERE " + DOM_BOOK_GENRE + "=?";

        static final String LANGUAGE =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + DOM_BOOK_LANGUAGE + "=?"
                + " WHERE " + DOM_BOOK_LANGUAGE + "=?";

        static final String LOCATION =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + DOM_BOOK_LOCATION + "=?"
                + " WHERE " + DOM_BOOK_LOCATION + "=?";

        static final String PUBLISHER =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + DOM_BOOK_PUBLISHER + "=?"
                + " WHERE " + DOM_BOOK_PUBLISHER + "=?";
    }

    /**
     * Sql DELETE commands.
     * <p>
     * All 'link' tables will be updated due to their FOREIGN KEY constraints.
     * The 'other-side' of a link table is cleaned by triggers.
     */
    static final class SqlDelete {

        /**
         * Delete a {@link Book}.
         */
        static final String BOOK_BY_ID =
                "DELETE FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link Bookshelf}.
         */
        static final String BOOKSHELF_BY_ID =
                "DELETE FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link Series}.
         */
        static final String SERIES_BY_ID =
                "DELETE FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link TocEntry}.
         */
        static final String TOC_ENTRY =
                "DELETE FROM " + TBL_TOC_ENTRIES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link BooklistStyle} by matching the UUID.
         */
        static final String STYLE_BY_UUID =
                "DELETE FROM " + TBL_BOOKLIST_STYLES + " WHERE " + DOM_UUID + "=?";

        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_AUTHOR_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_FK_BOOK + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOK + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Series}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_SERIES_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_BOOK + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link TocEntry}.
         * <p>
         * This is done when a TOC is updated; first delete all links, then re-create them.
         */
        static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_TOC_ENTRIES + " WHERE " + DOM_FK_BOOK + "=?";

        /**
         * Delete the loan of a {@link Book}; i.e. 'return the book'.
         */
        static final String BOOK_LOANEE_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_LOANEE + " WHERE " + DOM_FK_BOOK + "=?";


        /**
         * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
         */
        static final String PURGE_AUTHORS = "DELETE FROM " + TBL_AUTHORS
                                            + " WHERE " + DOM_PK_ID + " NOT IN"
                                            + " (SELECT DISTINCT " + DOM_FK_AUTHOR + " FROM "
                                            + TBL_BOOK_AUTHOR + ')'
                                            + " AND " + DOM_PK_ID + " NOT IN"
                                            + " (SELECT DISTINCT " + DOM_FK_AUTHOR + " FROM "
                                            + TBL_TOC_ENTRIES + ')';

        /**
         * Purge a {@link Series} if no longer in use.
         */
        static final String PURGE_SERIES = "DELETE FROM " + TBL_SERIES
                                           + " WHERE " + DOM_PK_ID + " NOT IN"
                                           + " (SELECT DISTINCT " + DOM_FK_SERIES + " FROM "
                                           + TBL_BOOK_SERIES + ')';

        private SqlDelete() {
        }
    }

    /**
     * Sql specific for FTS.
     */
    private static final class SqlFTS {

        static final String GET_AUTHORS_BY_BOOK_ID =
                "SELECT "
                + TBL_AUTHORS.dot("*")
                + " FROM "
                + TBL_BOOK_AUTHOR.ref()
                + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + " WHERE "
                + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK) + "=?";

        static final String DOM_SERIES_INFO = "seriesInfo";
        static final String GET_SERIES_BY_BOOK_ID =
                "SELECT "
                + TBL_SERIES.dot(DOM_SERIES_TITLE) + " || ' ' || "
                + " COALESCE(" + TBL_BOOK_SERIES.dot(DOM_BOOK_NUM_IN_SERIES) + ",'')"
                + " AS " + DOM_SERIES_INFO
                + " FROM "
                + TBL_BOOK_SERIES.ref()
                + TBL_BOOK_SERIES.join(TBL_SERIES)
                + " WHERE "
                + TBL_BOOK_SERIES.dot(DOM_FK_BOOK) + "=?";

        static final String DOM_TOC_ENTRY_AUTHOR_INFO = "TOCEntryAuthor";
        static final String DOM_TOC_ENTRY_TITLE = "TOCEntryTitle";

        static final String GET_TOC_ENTRIES_BY_BOOK_ID =
                "SELECT "
                + (TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || "
                   + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)) + " AS " + DOM_TOC_ENTRY_AUTHOR_INFO
                + ',' + DOM_TITLE + " AS " + DOM_TOC_ENTRY_TITLE
                + " FROM "
                + TBL_TOC_ENTRIES.ref()
                + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                + " WHERE "
                + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK) + "=?";

        // the body of an INSERT INTO [table]. Used more then once.
        static final String INSERT_BODY =
                " (" + DOM_FTS_AUTHOR_NAME
                + ',' + DOM_TITLE
                + ',' + DOM_SERIES_TITLE
                + ',' + DOM_BOOK_DESCRIPTION
                + ',' + DOM_BOOK_NOTES
                + ',' + DOM_BOOK_PUBLISHER
                + ',' + DOM_BOOK_GENRE
                + ',' + DOM_BOOK_LOCATION
                + ',' + DOM_BOOK_ISBN
                + ',' + DOM_PK_DOCID
                + ") VALUES (?,?,?,?,?,?,?,?,?,?)";

        // The parameter order MUST match the order expected in UPDATE.
        static final String INSERT = "INSERT INTO " + TBL_BOOKS_FTS + INSERT_BODY;

        // The parameter order MUST match the order expected in INSERT.
        static final String UPDATE =
                "UPDATE " + TBL_BOOKS_FTS
                + " SET " + DOM_FTS_AUTHOR_NAME + "=?"
                + ',' + DOM_TITLE + "=?"
                + ',' + DOM_SERIES_TITLE + "=?"
                + ',' + DOM_BOOK_DESCRIPTION + "=?"
                + ',' + DOM_BOOK_NOTES + "=?"
                + ',' + DOM_BOOK_PUBLISHER + "=?"
                + ',' + DOM_BOOK_GENRE + "=?"
                + ',' + DOM_BOOK_LOCATION + "=?"
                + ',' + DOM_BOOK_ISBN + "=?"
                + " WHERE " + DOM_PK_DOCID + "=?";
    }
}

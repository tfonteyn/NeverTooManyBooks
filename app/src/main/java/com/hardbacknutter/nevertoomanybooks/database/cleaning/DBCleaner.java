/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.cleaning;

import android.content.Context;
import android.database.Cursor;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Clean-up routines for some columns/tables which can be run at upgrades, import, startup.
 * <p>
 *  FIXME: implement proper cleaning of ALL orphaned images in the cleaner
 *  - book covers:
 *    - delete all [16-char uuid].ext files where there is no equivalent book
 *  - author pictures:
 *    - delete all uuid.ext files where there is no author with the pictureUuid set to that uuid
 *    - clear authors pictureUuid if set without there being an actual file
 *  - remove rows from the cache db where the referenced book uuid does not exist
 * <p>
 *  HOWEVER: SEE {@link com.hardbacknutter.nevertoomanybooks.settings.MaintenanceFragment}
 *  where we offer a clean-up of orphaned book covers.
 */
public class DBCleaner {

    /** Log tag. */
    private static final String TAG = "DBCleaner";

    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String SELECT_ = "SELECT ";
    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _FROM_ = " FROM ";
    private static final String _IN_ = " IN ";
    private static final String _IS_NOT_NULL = " IS NOT NULL";
    private static final String _IS_NULL = " IS NULL";
    private static final String _SET_ = " SET ";
    private static final String _WHERE_ = " WHERE ";

    private static final String UPDATE_BOOKS_SET =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp";

    private static final Pattern RATING_PATTERN = Pattern.compile("^\\s*\\d*\\.?\\d*\\s*$");

    private static final float FLOAT_EPSILON = 0.1f;

    private final Logger logger;

    /** Database Access. */
    @NonNull
    private final SynchronizedDb db;
    private final BookshelfDao bookshelfDao;
    private final LanguageDao languageDao;


    /**
     * Constructor.
     */
    public DBCleaner() {
        logger = LoggerFactory.getLogger();

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        this.db = serviceLocator.getDb();

        bookshelfDao = serviceLocator.getBookshelfDao();
        languageDao = serviceLocator.getLanguageDao();
    }

    /**
     * Start the cleaning.
     *
     * @param context Current context
     *
     * @throws DaoWriteException on any failure
     */
    @WorkerThread
    public void clean(@NonNull final Context context)
            throws DaoWriteException {

        final Set<CleanOptions> options = CleanOptions.readOptions();

        // do a mass update of any languages not yet converted to ISO 639-2 codes
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        languageDao.bulkUpdate(userLocale);

        // make sure there are no 'T' separators in datetime fields
        datetimeFormat();

        // validate booleans to have 0/1 content (could do just ALL_TABLES)
        booleanColumns(TBL_BOOKS,
                       TBL_AUTHORS,
                       TBL_SERIES);

        //ratingColumn();

        // Validate styles and filters.
        bookshelfDao.validate(context);

        //TEST: we only check & log for now, but don't update yet...
        // we need to test with bad data
        bookBookshelf(true);

        if (!options.isEmpty()) {
            if (options.contains(CleanOptions.ResolveAuthors)) {
                // run the resolve until no more mods are done,
                // but a max of 3 times to prevent endless loops in case of bugs
                int r = 3;
                while (resolveAuthors(context, userLocale) && r > 0) {
                    r--;
                }
            }
            if (options.contains(CleanOptions.Purge)) {
                new Purger().purge();
            }
            final DuplicateRowCleaner drc = new DuplicateRowCleaner(options);
            drc.dedup(context);
        }

        // Lastly, always clear the options
        CleanOptions.clearOptions();
    }

    /**
     * Check any circular references between authors and real-authors.
     *
     * @param context Current context
     * @param locale  to use while updating
     *
     * @return {@code true} if any updates were done
     *
     * @throws DaoWriteException on any failure
     */
    private boolean resolveAuthors(@NonNull final Context context,
                                   @NonNull final Locale locale)
            throws DaoWriteException {

        // collect the authors (id) who have a 'real' author, i.e. are pseudonym names.
        final List<Long> all = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                SELECT_ + TBL_AUTHORS.dot(DBKey.PK_ID)
                + _FROM_ + TBL_AUTHORS.as() + TBL_AUTHORS.leftOuterJoin(TBL_PSEUDONYM_AUTHOR)
                + _WHERE_ + TBL_PSEUDONYM_AUTHOR.dot(DBKey.FK_AUTHOR_REAL_AUTHOR) + ">0",
                null)) {
            while (cursor.moveToNext()) {
                all.add(cursor.getLong(0));
            }
        }

        // We could obviously combine the above with loading the authors,
        // TODO: move the resolve/clean routine to the DAO...
        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        final List<Author> authors = all.stream()
                                        .map(authorDao::findById)
                                        .filter(Optional::isPresent)
                                        .map(Optional::get)
                                        .collect(Collectors.toList());

        final boolean[] updatesDone = new boolean[1];
        for (final Author author : authors) {
            final long tmpRAID = author.getRealAuthorId();
            // resolve
            author.getRealAuthor();
            // did the real-author get modified?
            if (tmpRAID != author.getRealAuthorId()) {
                authorDao.update(context, author, locale);
                updatesDone[0] = true;
            }
        }

        logger.w(TAG, "resolveAuthors",
                 "modified=" + updatesDone[0],
                 "pIds=" + all);
        return updatesDone[0];
    }

    private void ratingColumn() {
        final List<Long> toDelete = new ArrayList<>();
        final Map<Long, String> toUpdate = new ArrayMap<>();
        final RatingParser ratingParser = new RatingParser(5);

        try (Cursor cursor = db.rawQuery(
                SELECT_ + DBKey.PK_ID + ',' + DBKey.RATING
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + DBKey.RATING + _IS_NOT_NULL, null)) {
            while (cursor.moveToNext()) {
                boolean modified = false;
                final long id = cursor.getLong(0);
                // Read as String!
                String s = cursor.getString(1);
                if (s.contains(",")) {
                    // simply substitute the decimal separator as needed
                    s = s.replace(',', '.');
                    modified = true;
                }
                // Now validate it's a an actual floating point.
                final Matcher matcher = RATING_PATTERN.matcher(s);
                if (matcher.find()) {
                    try {
                        final float v = Float.parseFloat(matcher.group());
                        final Optional<Float> v2 = ratingParser.normalise(v);
                        if (v2.isPresent()) {
                            final float normalised = v2.get();
                            // If they differ with a difference equal or larger than the epsilon
                            // OR we previously substituted the decimal separator,
                            // we need to update the database with the new value
                            if (Math.abs(v - normalised) >= FLOAT_EPSILON) {
                                if (modified) {
                                    toUpdate.put(id, String.valueOf(normalised));
                                }
                            }
                        } else {
                            toDelete.add(id);
                        }
                    } catch (@NonNull final NumberFormatException ignore) {
                        toDelete.add(id);
                    }
                } else {
                    toDelete.add(id);
                }
            }
        }

        if (!toDelete.isEmpty()) {
            final StringJoiner sj = new StringJoiner(",", "(", ")");
            toDelete.forEach(id -> sj.add(String.valueOf(id)));
            // just the one execute for performance
            db.execSQL(UPDATE_BOOKS_SET + ',' + DBKey.RATING + "=null"
                       + _WHERE_ + DBKey.PK_ID + _IN_ + sj);
        }
        if (!toUpdate.isEmpty()) {
            try (SynchronizedStatement stmt = db.compileStatement(
                    UPDATE_BOOKS_SET + ',' + DBKey.RATING + "=?"
                    + _WHERE_ + DBKey.PK_ID + "=?")) {

                for (final Map.Entry<Long, String> entry : toUpdate.entrySet()) {
                    stmt.bindLong(1, entry.getKey());
                    stmt.bindString(2, entry.getValue());
                    stmt.executeUpdateDelete();
                }
            }
        }
    }


    /**
     * Replace {@code 'T'} occurrences with {@code ' '}.
     * See package-info docs for {@link FullDateParser}.
     */
    private void datetimeFormat() {
        final Collection<Pair<Long, String>> rows = new ArrayList<>();

        for (final String key : DBKey.getDateTimeKeys()) {
            try (Cursor cursor = db.rawQuery(
                    SELECT_ + DBKey.PK_ID + ',' + key
                    + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + key + " LIKE '%T%'", null)) {
                while (cursor.moveToNext()) {
                    rows.add(new Pair<>(cursor.getLong(0), cursor.getString(1)));
                }
            }

            if (BuildConfig.DEBUG /* always */) {
                logger.d(TAG, "dates",
                         "key=" + key
                         + "|rows.size()=" + rows.size());
            }
            try (SynchronizedStatement stmt = db.compileStatement(
                    UPDATE_BOOKS_SET + ',' + key + "=?" + _WHERE_ + DBKey.PK_ID + "=?")) {

                for (final Pair<Long, String> row : rows) {
                    stmt.bindString(1, SqlEncode.dateTime(row.second));
                    stmt.bindLong(2, row.first);
                    stmt.executeUpdateDelete();
                }
            }
            // reuse for next column
            rows.clear();
        }
    }

    /**
     * Validates all boolean columns to contain '0' or '1'.
     *
     * @param tables list of tables
     */
    private void booleanColumns(@NonNull final TableDefinition... tables) {
        for (final TableDefinition table : tables) {
            table.getDomains()
                 .stream()
                 .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.Boolean)
                 .forEach(domain -> booleanCleanup(table.getName(), domain.getName()));
        }
    }

    /**
     * Enforce boolean columns to 0,1.
     *
     * @param table  to check
     * @param column to check
     */
    private void booleanCleanup(@NonNull final String table,
                                @NonNull final String column) {
        if (BuildConfig.DEBUG /* always */) {
            logger.d(TAG, "booleanCleanup",
                     "table=" + table,
                     "column=" + column);
        }

        final String select = SELECT_DISTINCT_ + column + _FROM_ + table
                              + _WHERE_ + column + " NOT IN ('0','1')";
        toLog("booleanCleanup", select);

        final String update = UPDATE_ + table + _SET_ + column + "=?"
                              + _WHERE_ + "LOWER(" + column + ") IN ";
        String sql;
        sql = update + "('true','t','yes')";
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, 1);
            final int rowsAffected = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                logger.d(TAG, "booleanCleanup", "true=" + rowsAffected);
            }
        }

        sql = update + "('false','f','no')";
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, 0);
            final int rowsAffected = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                logger.d(TAG, "booleanCleanup", "false=" + rowsAffected);
            }
        }
    }

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    private void bookBookshelf(@SuppressWarnings("SameParameterValue") final boolean dryRun) {
        final String select = SELECT_DISTINCT_ + DBKey.FK_BOOK
                              + _FROM_ + TBL_BOOK_BOOKSHELF
                              + _WHERE_ + DBKey.FK_BOOKSHELF + _IS_NULL;

        toLog("bookBookshelf|ENTER", select);
        if (!dryRun) {
            final String sql = DELETE_FROM_ + TBL_BOOK_BOOKSHELF
                               + _WHERE_ + DBKey.FK_BOOKSHELF + _IS_NULL;
            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("bookBookshelf|EXIT", select);
        }
    }

    /**
     * Convert any {@code null} values to an empty string.
     * <p>
     * Used to correct data in columns which have {@code "string default ''"}.
     *
     * @param table  to check
     * @param column to check
     * @param dryRun {@code true} to run the update.
     */
    @SuppressWarnings("unused")
    private void nullString2empty(@NonNull final String table,
                                  @NonNull final String column,
                                  final boolean dryRun) {
        final String select =
                SELECT_DISTINCT_ + column + _FROM_ + table + _WHERE_ + column + _IS_NULL;
        toLog("nullString2empty|ENTER", select);
        if (!dryRun) {
            final String sql =
                    UPDATE_ + table + _SET_ + column + "=''" + _WHERE_ + column + _IS_NULL;
            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("nullString2empty|EXIT", select);
        }
    }

    /**
     * WIP... debug
     * Execute the query and log the results.
     *
     * @param state Enter/Exit
     * @param query to execute
     */
    private void toLog(@NonNull final String state,
                       @NonNull final String query) {
        if (BuildConfig.DEBUG /* always */) {
            try (Cursor cursor = db.rawQuery(query, null)) {
                logger.d(TAG, state, "row count=" + cursor.getCount());
                while (cursor.moveToNext()) {
                    final String field = cursor.getColumnName(0);
                    final String value = cursor.getString(0);

                    logger.d(TAG, state, field + '=' + value);
                }
            }
        }
    }
}

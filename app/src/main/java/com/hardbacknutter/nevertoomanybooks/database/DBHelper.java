/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.UpgradeFailedException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookshelfDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreCustomFieldDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.FieldVisibilityPreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF_FILTERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_CUSTOM_FIELDS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * {@link SQLiteOpenHelper} for the main database.
 * Uses the application context.
 */
public class DBHelper
        extends SQLiteOpenHelper {

    /** Current version. */
    public static final int DATABASE_VERSION = 26;

    /** NEVER change this name. */
    private static final String DATABASE_NAME = "nevertoomanybooks.db";

    /** Log tag. */
    private static final String TAG = "DBHelper";

    /** The database prepared statement cache size (default 25, max 100). */
    private static final String PK_STARTUP_DB_STMT_CACHE_SIZE = "db.stmt.cache.size";

    /** Prefix for the filename of a database backup before doing an upgrade. */
    private static final String DB_UPGRADE_FILE_PREFIX = "DbUpgrade";

    /** SQL to get the names of all indexes. */
    private static final String SQL_GET_INDEX_NAMES =
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL";

    /** Readers/Writer lock for <strong>this</strong> database. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create a {@link SynchronizedCursor} cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, SYNCHRONIZER);

    /** Always use {@link #getCollation(SQLiteDatabase)} to access. */
    @Nullable
    private static Boolean sIsCollationCaseSensitive;

    @IntRange(from = 25, to = SQLiteDatabase.MAX_SQL_CACHE_SIZE)
    private final int stmtCacheSize;

    /** DO NOT USE INSIDE THIS CLASS! ONLY FOR USE BY CLIENTS CALLING {@link #getDb()}. */
    @Nullable
    private SynchronizedDb synchronizedDb;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public DBHelper(@NonNull final Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, CURSOR_FACTORY, DATABASE_VERSION);

        // default 25, see SynchronizedDb javadoc
        final int size = PreferenceManager.getDefaultSharedPreferences(context)
                                          .getInt(PK_STARTUP_DB_STMT_CACHE_SIZE, 25);
        stmtCacheSize = MathUtils.clamp(size, 25, SQLiteDatabase.MAX_SQL_CACHE_SIZE);
    }

    /**
     * Get the physical path of the database file.
     *
     * @param context Current context
     *
     * @return path
     */
    @NonNull
    public static File getDatabasePath(@NonNull final Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }

    /**
     * Wrapper to allow
     * {@link RebuildIndexesTask}.
     * safe access to the database.
     */
    public static void recreateIndices() {
        final SynchronizedDb db = ServiceLocator.getInstance().getDb();
        final Synchronizer.SyncLock txLock = db.beginTransaction(true);
        try {
            // It IS safe here to get the underlying database, as we're in a SyncLock.
            recreateIndices(db.getSQLiteDatabase());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction(txLock);
        }
    }

    /**
     * This method should only be called at the *END* of {@link #onUpgrade}.
     * <p>
     * (re)Creates the indexes as defined on the tables.
     *
     * @param db Underlying database
     */
    private static void recreateIndices(@NonNull final SQLiteDatabase db) {
        // Delete all indices.
        // We read the index names from the database, so we can delete
        // indexes which were removed from the TableDefinition objects.
        try (Cursor current = db.rawQuery(SQL_GET_INDEX_NAMES, null)) {
            while (current.moveToNext()) {
                final String indexName = current.getString(0);
                //noinspection CheckStyle
                try {
                    db.execSQL("DROP INDEX " + indexName);
                } catch (@NonNull final SQLException e) {
                    LoggerFactory.getLogger().e(TAG, e);
                    throw e;
                } catch (@NonNull final RuntimeException e) {
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "DROP INDEX failed: " + indexName);
                }
            }
        }

        // now recreate
        for (final TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
            table.createIndices(db, getCollation(db));
        }

        db.execSQL("analyze");
    }

    /**
     * Migrate and remove all keys which were declared obsolete.
     *
     * @param context Current context
     */
    public static void migratePreferenceKeys(@NonNull final Context context) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // db version 25
        final Pattern dot = Pattern.compile("\\.");
        final FieldVisibility fieldVisibility = new FieldVisibility();
        prefs.getAll()
             .keySet()
             .stream()
             .filter(key -> key.startsWith("fields.visibility."))
             .forEach(oldKey -> {
                 final boolean value = prefs.getBoolean(oldKey, false);
                 final String dbKey = dot.split(oldKey, 3)[2];
                 fieldVisibility.setVisible(dbKey, value);
             });

        prefs.edit()
             .putLong(FieldVisibilityPreferenceFragment.PK_FIELD_VISIBILITY,
                      fieldVisibility.getBitValue())
             .apply();


        // Now remove all obsolete keys.
        final SharedPreferences.Editor editor = prefs.edit();

        prefs.getAll()
             .keySet()
             .stream()
             .filter(key -> key.startsWith("style.booklist.")
                            || key.startsWith("fields.visibility."))
             .forEach(editor::remove);

        editor.remove("tips.tip.BOOKLIST_STYLES_EDITOR")
              .remove("tips.tip.BOOKLIST_STYLE_GROUPS")
              .remove("tips.tip.BOOKLIST_STYLE_PROPERTIES")
              .remove("tips.tip.booklist_style_menu")
              .remove("tips.tip.book_search_by_text")

              .remove("BookList.Style.Preferred.Order")
              .remove("bookList.style.preferred.order")
              .remove("BookList.Style.Current")

              .remove("booklist.top.rowId")
              .remove("booklist.top.row")
              .remove("booklist..top.row")
              .remove("booklist.top.offset")
              .remove("booklist..top.offset")

              .remove("calibre.last.sync.date")
              .remove("camera.id.scan.barcode")
              .remove("compat.booklist.mode")
              .remove("compat.image.cropper.viewlayertype")
              .remove("edit.book.tab.authSer")
              .remove("edit.book.tab.nativeId")
              .remove("goodreads.enabled")
              .remove("goodreads.showMenu")
              .remove("goodreads.search.collect.genre")
              .remove("goodreads.AccessToken.Token")
              .remove("goodreads.AccessToken.Secret")
              .remove("image.cropper.frame.whole")
              .remove("librarything.dev_key")
              .remove("scanner.preferred")
              .remove("search.form.advanced")
              .remove("search.site.goodreads.data.enabled")
              .remove("search.site.goodreads.covers.enabled")
              .remove("startup.lastVersion")
              .remove("tmp.edit.book.tab.authSer")
              .remove("ui.messages.use")

              // Editing the URL for these sites has been removed.
              .remove(EngineId.Isfdb.getPreferenceKey() + '.' + Prefs.pk_host_url)
              .remove(EngineId.LibraryThing.getPreferenceKey() + '.' + Prefs.pk_host_url)

              .apply();
    }

    private static boolean getCollation(@NonNull final SQLiteDatabase db) {
        synchronized (DBHelper.class) {
            if (sIsCollationCaseSensitive == null) {
                sIsCollationCaseSensitive = collationIsCaseSensitive(db);
            }
        }
        return sIsCollationCaseSensitive;
    }

    /**
     * Method to detect if collation implementations are case sensitive.
     * This was built because ICS broke the UNICODE collation (making it case sensitive (CS))
     * and we needed to check for collation case-sensitivity.
     * <p>
     * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
     *
     * @param db Underlying database
     *
     * @return This method is supposed to return {@code false} in normal circumstances.
     */
    private static boolean collationIsCaseSensitive(@NonNull final SQLiteDatabase db) {
        final String dropTable = "DROP TABLE IF EXISTS collation_cs_check";
        try {
            // Drop and create table
            db.execSQL(dropTable);
            db.execSQL("CREATE TEMPORARY TABLE collation_cs_check (t text, i integer)");

            // Row that *should* be returned first assuming 'a' <=> 'A'
            db.execSQL("INSERT INTO collation_cs_check VALUES('a', 1)");
            // Row that *should* be returned second assuming 'a' <=> 'A';
            // will be returned first if 'A' < 'a'.
            db.execSQL("INSERT INTO collation_cs_check VALUES('A', 2)");

            final boolean cs;
            try (Cursor c = db.rawQuery(
                    "SELECT t,i FROM collation_cs_check ORDER BY t COLLATE LOCALIZED,i",
                    null)) {
                c.moveToFirst();
                cs = !"a".equals(c.getString(0));
            }

            if (cs) {
                LoggerFactory.getLogger().w(
                        TAG,
                        "==================== CASE SENSITIVE COLLATION ====================");
            }
            return cs;

        } catch (@NonNull final SQLException e) {
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        } finally {
            try {
                db.execSQL(dropTable);
            } catch (@NonNull final SQLException e) {
                LoggerFactory.getLogger().e(TAG, e);
            }
        }
    }

    /**
     * Get the main database.
     *
     * @return database connection
     *
     * @throws SQLiteException if the database cannot be opened
     */
    @NonNull
    public SynchronizedDb getDb() {
        synchronized (this) {
            if (synchronizedDb == null) {
                // Dev note: don't move this to the constructor, "this" must
                // be fully constructed before we can pass it to the SynchronizedDb constructor
                synchronizedDb = new SynchronizedDb(SYNCHRONIZER, this,
                                                    getCollation(getWritableDatabase()),
                                                    stmtCacheSize);
            }
        }
        return synchronizedDb;
    }

    @Override
    public void close() {
        if (synchronizedDb != null) {
            synchronizedDb.close();
        }
        super.close();
    }

    @Override
    public void onConfigure(@NonNull final SQLiteDatabase db) {
        // Turn OFF recursive triggers;
        db.execSQL("PRAGMA recursive_triggers = OFF");

        // DO NOT ENABLE FOREIGN KEY CONSTRAINTS HERE. Enable them in onOpen instead.
        // WE NEED THIS TO BE false (default) TO ALLOW onUpgrade TO MAKE SCHEMA CHANGES
        // db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * <strong>REMINDER: foreign key constraints are DISABLED here.</strong>
     * <strong>WARNING: do NOT use SynchronizedDb here!</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Create all the app & user data tables in the correct dependency order
        TableDefinition.onCreate(db, getCollation(db), DBDefinitions.ALL_TABLES.values());

        // insert the builtin styles so foreign key rules are possible.
        StyleDaoImpl.onPostCreate(db);
        // and the all/default shelves
        BookshelfDaoImpl.onPostCreate(context, db);

        CalibreCustomFieldDaoImpl.onPostCreate(db);

        //IMPORTANT: withDomainConstraints MUST BE false (FTS columns don't use a type/constraints)
        TBL_FTS_BOOKS.create(db, false);

        Triggers.create(db);
    }

    /**
     * <strong>REMINDER: foreign key constraints are DISABLED here.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final Context context = serviceLocator.getLocalizedAppContext();

        final StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(context.getString(R.string.progress_msg_upgrading));
        }

        // take a backup before modifying the database
        if (oldVersion != newVersion) {
            final String backup = DB_UPGRADE_FILE_PREFIX + "-" + oldVersion + '-' + newVersion;
            try {
                final File destFile = new File(serviceLocator.getUpgradesDir(), backup);
                // rename the existing file if there is one
                if (destFile.exists()) {
                    final File destination = new File(destFile.getPath() + ".bak");
                    try {
                        FileUtils.rename(destFile, destination);
                    } catch (@NonNull final IOException e) {
                        LoggerFactory.getLogger()
                                     .e(TAG, e, "failed to rename source=" + destFile
                                                + " TO destination=" + destination, e);
                    }
                }
                // and create a new copy
                FileUtils.copy(new File(db.getPath()), destFile);
            } catch (@NonNull final IOException e) {
                LoggerFactory.getLogger().e(TAG, e);
            }
        }

        // this is nasty....  onUpgrade always gets executed in a transaction,
        // and deleting an index inside a transaction does not become 'activated'
        // until the transaction is done.
        //
        // We need to drop this particular index due to it having been wrongfully created 'unique'.
        // This MUST be done BEFORE we do anything else (and luckily upgrades 15..22
        // are not conflicting). Even if any of the further upgrades cause a fail,
        // deleting this index is what we want.
        if (oldVersion >= 15 && oldVersion < 23) {
            db.setTransactionSuccessful();
            db.endTransaction();
            db.execSQL("DROP INDEX anthology_IDX_pk_3");
            db.beginTransaction();
        }

        if (oldVersion < 15) {
            throw new UpgradeFailedException(
                    context.getString(R.string.error_upgrade_not_supported, "2.0.0"));
        }
        if (oldVersion < 16) {
            TBL_STRIPINFO_COLLECTION.create(db, true);

            context.deleteDatabase("taskqueue.db");
        }
        if (oldVersion < 17) {
            TBL_CALIBRE_CUSTOM_FIELDS.create(db, true);
            CalibreCustomFieldDaoImpl.onPostCreate(db);
        }
        if (oldVersion < 18) {
            TBL_BOOKSHELF_FILTERS.create(db, true);
        }
        if (oldVersion < 19) {
            final SharedPreferences global = PreferenceManager
                    .getDefaultSharedPreferences(context);
            // change the name of these for easier migration
            final boolean visSeries = global.getBoolean("fields.visibility."
                                                        + DBKey.SERIES_TITLE, true);
            final boolean visPublisher = global.getBoolean("fields.visibility."
                                                           + DBKey.PUBLISHER_NAME, true);
            global.edit()
                  .putBoolean("fields.visibility." + DBKey.FK_SERIES, visSeries)
                  .putBoolean("fields.visibility." + DBKey.FK_PUBLISHER, visPublisher)
                  .apply();


            TBL_BOOKLIST_STYLES.alterTableAddColumns(
                    db,
                    DBDefinitions.DOM_STYLE_NAME,

                    DBDefinitions.DOM_STYLE_GROUPS,
                    DBDefinitions.DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
                    DBDefinitions.DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE,
                    DBDefinitions.DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
                    DBDefinitions.DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                    DBDefinitions.DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,

                    DBDefinitions.DOM_STYLE_EXP_LEVEL,
                    DBDefinitions.DOM_STYLE_ROW_USES_PREF_HEIGHT,
                    DBDefinitions.DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME,
                    DBDefinitions.DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME,
                    DBDefinitions.DOM_STYLE_TEXT_SCALE,
                    DBDefinitions.DOM_STYLE_COVER_SCALE,
                    DBDefinitions.DOM_STYLE_LIST_HEADER,
                    DBDefinitions.DOM_STYLE_BOOK_DETAIL_FIELDS_VISIBILITY,
                    DBDefinitions.DOM_STYLE_BOOK_LEVEL_FIELDS_VISIBILITY);

            final List<String> uuids = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT uuid FROM " + TBL_BOOKLIST_STYLES.getName()
                    + " WHERE " + DBKey.STYLE_IS_BUILTIN + "=0", null)) {
                while (cursor.moveToNext()) {
                    uuids.add(cursor.getString(0));
                }
            }

            try (SQLiteStatement stmt = db.compileStatement(
                    "UPDATE " + TBL_BOOKLIST_STYLES.getName() + " SET "
                    + DBKey.STYLE_NAME + "=?, "

                    + DBKey.STYLE_GROUPS + "=?,"
                    + DBKey.STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH + "=?,"
                    + DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE + "=?,"
                    + DBKey.STYLE_GROUPS_SERIES_SHOW_UNDER_EACH + "=?,"
                    + DBKey.STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH + "=?,"
                    + DBKey.STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH + "=?,"

                    + DBKey.STYLE_EXP_LEVEL + "=?,"
                    + DBKey.STYLE_ROW_USES_PREF_HEIGHT + "=?,"
                    + DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME + "=?,"
                    + DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME + "=?,"
                    + DBKey.STYLE_TEXT_SCALE + "=?,"
                    + DBKey.STYLE_COVER_SCALE + "=?,"
                    + DBKey.STYLE_LIST_HEADER + "=?,"
                    + DBKey.STYLE_DETAILS_SHOW_FIELDS + "=?,"
                    + DBKey.STYLE_BOOK_LEVEL_FIELDS_VISIBILITY + "=?"

                    + " WHERE " + DBKey.STYLE_UUID + "=?")) {

                // Preference keys are hardcoded, as this is for backwards compatibility.
                uuids.forEach(uuid -> {
                    final SharedPreferences stylePrefs = context
                            .getSharedPreferences(uuid, Context.MODE_PRIVATE);

                    int c = 0;

                    stmt.bindString(++c, stylePrefs.getString(
                            "style.booklist.name", null));

                    stmt.bindString(++c, stylePrefs.getString(
                            "style.booklist.groups", null));

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            Style.UnderEach.Author.getPrefKey(), false) ? 1 : 0);

                    stmt.bindLong(++c, StyleDataStore.convert(
                            stylePrefs.getStringSet("style.booklist.group.authors.primary.type",
                                                    null),
                            Author.TYPE_UNKNOWN));

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            Style.UnderEach.Series.getPrefKey(), false) ? 1 : 0);
                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            Style.UnderEach.Publisher.getPrefKey(), false) ? 1 : 0);
                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            Style.UnderEach.Bookshelf.getPrefKey(), false) ? 1 : 0);

                    stmt.bindLong(++c, stylePrefs.getInt(
                            "style.booklist.levels.default", 1));
                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            "style.booklist.group.height", true) ? 1 : 0);

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            "sort.author.name.given_first", false) ? 1 : 0);

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            "show.author.name.given_first", false) ? 1 : 0);


                    stmt.bindLong(++c, stylePrefs.getInt(
                            "style.booklist.scale.font", Style.DEFAULT_TEXT_SCALE));
                    stmt.bindLong(++c, stylePrefs.getInt(
                            "style.booklist.scale.thumbnails", Style.DEFAULT_COVER_SCALE));

                    stmt.bindLong(++c, StyleDataStore.convert(
                            stylePrefs.getStringSet("style.booklist.header", null),
                            BooklistHeader.BITMASK_ALL));


                    final Set<String> detailFields = new HashSet<>();
                    if (stylePrefs.getBoolean("style.details.show.thumbnail.0", true)) {
                        detailFields.add(DBKey.COVER[0]);
                    }
                    if (stylePrefs.getBoolean("style.details.show.thumbnail.1", true)) {
                        detailFields.add(DBKey.COVER[1]);
                    }

                    stmt.bindLong(++c, FieldVisibility.getBitValue(detailFields));

                    final Set<String> listFields = new HashSet<>();
                    listFields.add(DBKey.FK_SERIES);

                    if (stylePrefs.getBoolean("style.booklist.show.thumbnails", true)) {
                        listFields.add(DBKey.COVER[0]);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.author", true)) {
                        listFields.add(DBKey.FK_AUTHOR);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.publisher", true)) {
                        listFields.add(DBKey.FK_PUBLISHER);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.publication.date", true)) {
                        listFields.add(DBKey.BOOK_PUBLICATION__DATE);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.format", true)) {
                        listFields.add(DBKey.FORMAT);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.location", true)) {
                        listFields.add(DBKey.LOCATION);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.rating", true)) {
                        listFields.add(DBKey.RATING);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.bookshelves", true)) {
                        listFields.add(DBKey.FK_BOOKSHELF);
                    }
                    if (stylePrefs.getBoolean("style.booklist.show.isbn", true)) {
                        listFields.add(DBKey.BOOK_ISBN);
                    }

                    stmt.bindLong(++c, FieldVisibility.getBitValue(listFields));

                    stmt.bindString(++c, uuid);
                    stmt.executeUpdateDelete();

                    context.deleteSharedPreferences(uuid);
                });
            }

        }
        if (oldVersion < 20) {
            TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_AUTO_UPDATE);
        }
        if (oldVersion < 21) {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);

            // Note that migratePreferenceKeys() did not exist at this time
            // Not going to bother with retro-active doing this.
            // convert the old bit ids to the preference-key of the engine
            Stream.of("search.siteOrder.data",
                      "search.siteOrder.covers",
                      "search.siteOrder.alted")
                  .forEach(key -> {
                      final String order = Arrays
                              .stream(prefs.getString(key, "").split(","))
                              .map(i -> {
                                  switch (i) {
                                      case "1":
                                          return EngineId.GoogleBooks.getPreferenceKey();
                                      case "2":
                                          return EngineId.Amazon.getPreferenceKey();
                                      case "4":
                                          return EngineId.LibraryThing.getPreferenceKey();
                                      case "8":
                                          return EngineId.Goodreads.getPreferenceKey();
                                      case "16":
                                          return EngineId.Isfdb.getPreferenceKey();
                                      case "32":
                                          return EngineId.OpenLibrary.getPreferenceKey();
                                      case "64":
                                          return EngineId.KbNl.getPreferenceKey();
                                      case "128":
                                          return EngineId.StripInfoBe.getPreferenceKey();
                                      case "256":
                                          return EngineId.LastDodoNl.getPreferenceKey();
                                      default:
                                          return "";
                                  }
                              })
                              .collect(Collectors.joining(","));
                      prefs.edit().putString(key, order).apply();
                  });
        }
        if (oldVersion < 22) {
            // remove builtin style ID_DEPRECATED_1
            db.execSQL("DELETE FROM " + TBL_BOOKLIST_STYLES.getName() + " WHERE _id=-2");
        }
        if (oldVersion < 23) {
            // Up to version 22 we had a bug in how we'd store TOC entries which could create
            // duplicate authors. Fixed in 23 but we need to do a clean up during upgrade.
            removeDuplicateAuthorsV23(db);
            // as a result of the author cleanup, we now might have duplicate toc entries,
            // same algorithm to clean those up
            removeDuplicateTocEntriesV23(db);

            // Add pen-name support
            TBL_PSEUDONYM_AUTHOR.create(db, true);
            // new search-engine added
            TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_ESID_BEDETHEQUE);
        }
        if (oldVersion < 24) {
            TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_TITLE_ORIGINAL_LANG);
        }
        if (oldVersion < 25) {
            TBL_DELETED_BOOKS.create(db, true);
            StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_FTS, true);
        }
        if (oldVersion < 26) {
            TBL_BOOKLIST_STYLES.alterTableAddColumns(
                    db,
                    DBDefinitions.DOM_STYLE_BOOK_LEVEL_FIELDS_ORDER_BY,
                    DBDefinitions.DOM_STYLE_COVER_CLICK_ACTION,
                    DBDefinitions.DOM_STYLE_LAYOUT);
        }

        // SqLite 3.35.0 from 2021-03-12 adds ALTER TABLE DROP COLUMN
        // SqLite 3.25.0 from 2018-09-15 added ALTER TABLE RENAME COLUMN
        // but... https://developer.android.com/reference/android/database/sqlite/package-summary
        // i.e. Android 8.0 == API 26 == SqLite 3.18

        //TODO: the books table MIGHT contain columns "clb_uuid" and "last_goodreads_sync_date"
        // If at a future time we make a change that requires to copy/reload
        // the books table those columns should be removed.

        //NEWTHINGS: adding a new search engine: optional: add external id DOM
        //TBL_BOOKS.alterTableAddColumn(db, DBDefinitions.DOM_your_engine_external_id);

        migratePreferenceKeys(context);

        // Rebuild all indices
        recreateIndices(db);

        // Rebuild all triggers
        Triggers.create(db);
    }

    @Override
    public void onOpen(@NonNull final SQLiteDatabase db) {
        // Turn ON foreign key support so that CASCADE etc. works.
        // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);
    }

    private void removeDuplicateAuthorsV23(@NonNull final SQLiteDatabase db) {

        // find the names for duplicate author; i.e. identical family and given names.
        final List<Pair<String, String>> authors = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT " + DBKey.AUTHOR_FAMILY_NAME + ',' + DBKey.AUTHOR_GIVEN_NAMES
                + " FROM " + TBL_AUTHORS.getName()
                + " GROUP BY " + DBKey.AUTHOR_FAMILY_NAME + ',' + DBKey.AUTHOR_GIVEN_NAMES
                + " HAVING COUNT(" + DBKey.PK_ID + ")>1", null)) {
            while (cursor.moveToNext()) {
                authors.add(new Pair<>(cursor.getString(0), cursor.getString(1)));
            }
        }
        if (authors.isEmpty()) {
            return;
        }

        // use the family and given names to find the id's for each duplication
        final List<List<Long>> authorDuplicates = new ArrayList<>();
        for (final Pair<String, String> a : authors) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + DBKey.PK_ID + " FROM " + TBL_AUTHORS.getName()
                    + " WHERE " + DBKey.AUTHOR_FAMILY_NAME + "=?"
                    + " AND " + DBKey.AUTHOR_GIVEN_NAMES + "=?",
                    new String[]{a.first, a.second})) {
                final List<Long> ids = new ArrayList<>();
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0));
                }
                if (ids.size() > 1) {
                    authorDuplicates.add(ids);
                }
            }
        }
        if (authorDuplicates.isEmpty()) {
            return;
        }

        final Logger logger = LoggerFactory.getLogger();

        // for each duplicate author, weed out the duplicates and delete them
        for (final List<Long> idList : authorDuplicates) {
            final long keep = idList.get(0);
            final List<Long> others = idList.subList(1, idList.size());

            final String ids = others.stream()
                                     .map(String::valueOf)
                                     .collect(Collectors.joining(","));

            String sql;

            sql = "UPDATE " + TBL_BOOK_AUTHOR.getName() + " SET " + DBKey.FK_AUTHOR + "=" + keep
                  + " WHERE " + DBKey.FK_AUTHOR + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Update TBL_BOOK_AUTHOR: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = "UPDATE " + TBL_TOC_ENTRIES.getName() + " SET " + DBKey.FK_AUTHOR + "=" + keep
                  + " WHERE " + DBKey.FK_AUTHOR + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Update TBL_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = "DELETE FROM " + TBL_AUTHORS.getName()
                  + " WHERE " + DBKey.PK_ID + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Delete TBL_AUTHORS: ids=" + ids);
                throw e;
            }
        }
    }

    private void removeDuplicateTocEntriesV23(@NonNull final SQLiteDatabase db) {
        // find the duplicate tocs; i.e. identical author and title.
        final List<Pair<Long, String>> entries = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT " + DBKey.FK_AUTHOR + ',' + DBKey.TITLE
                + " FROM " + TBL_TOC_ENTRIES
                + " GROUP BY " + DBKey.FK_AUTHOR + ',' + DBKey.TITLE
                + " HAVING COUNT(" + DBKey.PK_ID + ")>1", null)) {
            while (cursor.moveToNext()) {
                entries.add(new Pair<>(cursor.getLong(0), cursor.getString(1)));
            }
        }
        if (entries.isEmpty()) {
            return;
        }

        // use the author and title to find the id's for each duplication
        final List<List<Long>> entryDuplicates = new ArrayList<>();
        for (final Pair<Long, String> toc : entries) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + DBKey.PK_ID + " FROM " + TBL_TOC_ENTRIES
                    + " WHERE " + DBKey.FK_AUTHOR + "=?"
                    + " AND " + DBKey.TITLE + "=?",
                    new String[]{String.valueOf(toc.first), toc.second})) {
                final List<Long> ids = new ArrayList<>();
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0));
                }
                if (ids.size() > 1) {
                    entryDuplicates.add(ids);
                }
            }
        }
        if (entryDuplicates.isEmpty()) {
            return;
        }

        final Logger logger = LoggerFactory.getLogger();

        // for each duplicate toc entry, weed out the duplicates and delete them
        for (final List<Long> idList : entryDuplicates) {
            final long keep = idList.get(0);
            final List<Long> others = idList.subList(1, idList.size());

            final String ids = others.stream()
                                     .map(String::valueOf)
                                     .collect(Collectors.joining(","));

            String sql;

            sql = "UPDATE " + TBL_BOOK_TOC_ENTRIES + " SET " + DBKey.FK_TOC_ENTRY + "=" + keep
                  + " WHERE " + DBKey.FK_TOC_ENTRY + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Update TBL_BOOK_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = "DELETE FROM " + TBL_TOC_ENTRIES + " WHERE " + DBKey.PK_ID + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Delete TBL_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }
        }
    }
}

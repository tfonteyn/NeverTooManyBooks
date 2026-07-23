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

package com.hardbacknutter.nevertoomanybooks.database.updates;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.database.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.cleaning.CleanOptions;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.IdentifierDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.TagMappingDaoImpl;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHOR_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_LANG_MAPPINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAG_MAPPINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * IMPORTANT: all previous Identity migration calls have been removed in this class.
 * {@link V8} db52 does a major update.
 * <p>
 * TODO: remove comments about Identity migration AFTER db52+ is released
 */
class V7 {

    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String DROP_TABLE_ = "DROP TABLE ";
    private static final String INSERT_INTO_ = "INSERT INTO ";
    private static final String SELECT_ = "SELECT ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _FROM_ = " FROM ";
    private static final String _SET_ = " SET ";
    private static final String _WHERE_ = " WHERE ";

    @NonNull
    private final Context context;
    @NonNull
    private final SQLiteDatabase db;
    @NonNull
    private final IdentifierMigration identifierMigration;

    /**
     * Constructor.
     *
     * @param context             Current context
     * @param db                  Underlying database
     * @param identifierMigration helper
     */
    V7(@NonNull final Context context,
       @NonNull final SQLiteDatabase db,
       @NonNull final IdentifierMigration identifierMigration) {
        this.context = context;
        this.db = db;
        this.identifierMigration = identifierMigration;
    }

    /**
     * v7.0.0: 35
     * v7.0.3: 36
     * v7.1.0: 38
     * v7.2.0: 39
     * v7.3.0: 40
     * v7.4.0: 41
     * v7.6.0: 42
     * v7.7.0: 43
     * v7.8.2: 44
     * v7.8.3: 45
     * v7.10.0: 46
     * (47 was dev only)
     * v7.11.0: 48
     * v7.12.0: 49
     * v7.13.0: 50
     * v7.16.0: 51
     *
     * @param oldVersion The old database version.
     */
    void update(final int oldVersion) {
        if (oldVersion < 35) {
            db35();
        }
        if (oldVersion < 36) {
            db36();
        }
        if (oldVersion < 37) {
            // CANNOT ROLLBACK
            db37();
        }
        if (oldVersion < 38) {
            db38();
        }
        if (oldVersion < 39) {
            db39();
        }
        if (oldVersion < 40) {
            db40();
        }
        if (oldVersion < 41) {
            db41();
        }
        if (oldVersion < 42) {
            db42();
        }
        if (oldVersion < 43) {
            db43();
        }
        if (oldVersion < 44) {
            db44();
        }
        if (oldVersion < 45) {
            db45();
        }
        if (oldVersion < 46) {
            // CANNOT ROLLBACK
            db46();
        }
        if (oldVersion < 47) {
            db47();
        }
        if (oldVersion < 49) {
            db49();
        }
        if (oldVersion < 50) {
            db50();
        }
    }

    private void db35() {
        db35AddCitationType();
        db35AddIdentifiersTable();
        db35AddMappingTables();

        // The format was changed
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_FTS, true);

        // StripInfo collection support was never finished nor activated in a release build.
        // Furthermore, it turns out each book with a "stripinfo" SID always wrote
        // collection data which obviously always was 'empty'.
        // and we're making a fresh start... drop and recreate the table.
        db.execSQL(DROP_TABLE_ + TBL_STRIPINFO_COLLECTION.getName());
        TBL_STRIPINFO_COLLECTION.create(db, true);
    }

    private void db35AddCitationType() {
        // depending on the installation/upgrade path, we might already have
        // added the column
        final ColumnInfo citationType = TBL_BOOKLIST_STYLES
                .getTableInfo(db).getColumn(DBKey.STYLE.CITATION_TYPE);
        if (citationType == null) {
            TBL_BOOKLIST_STYLES.alterTableAddColumns(
                    db,
                    DBDefinitions.DOM_STYLE_CITATION_TYPE);
        }
    }

    private void db35AddIdentifiersTable() {
        identifierMigration.setIsNewInstall();

        TBL_IDENTIFIERS.create(db, true);
        TBL_BOOK_IDENTIFIER.create(db, true);
        IdentifierDaoImpl.onPostCreate(context, db);
        db35migrateSids();
    }

    private void db35migrateSids() {
        final Set<String> legacyKeys = IdentifierMigration.MAPPINGS.keySet();
        final Collection<String> legacyValues = IdentifierMigration.MAPPINGS.values();

        final Map<String, Integer> preDef = new HashMap<>();
        final String preDefSql = SELECT_ + DBKey.PK_ID + ',' + DBKey.IDENTIFIERS.KEY
                                 + _FROM_ + DBDefinitions.TBL_IDENTIFIERS.getName();
        try (Cursor cursor = db.rawQuery(preDefSql, null)) {
            while (cursor.moveToNext()) {
                final int id = cursor.getInt(0);
                final String name = cursor.getString(1);
                preDef.put(name, id);
            }
        }

        final String sqlSelect = SELECT_ + DBKey.PK_ID
                                 + ',' + String.join(",", legacyKeys)
                                 + _FROM_ + TBL_BOOKS.getName()
                                 + _WHERE_
                                 + legacyKeys.stream()
                                             .map(c -> "(" + c + " IS NOT NULL)")
                                             .collect(Collectors.joining(" OR "));

        final String sqlInsert = INSERT_INTO_ + DBDefinitions.TBL_BOOK_IDENTIFIER.getName()
                                 + '(' + DBKey.FK_BOOK
                                 + ',' + DBKey.FK_IDENTIFIER
                                 + ',' + DBKey.IDENTIFIERS.SID
                                 + ") VALUES(?,?,?)";

        try (Cursor cursor = db.rawQuery(sqlSelect, null);
             SQLiteStatement insert = db.compileStatement(sqlInsert)) {
            while (cursor.moveToNext()) {
                int c = 0;
                final long bookId = cursor.getLong(c);
                for (final String sidName : legacyValues) {
                    ++c;
                    if (!cursor.isNull(c)) {
                        final String sid = cursor.getString(c);
                        insert.bindLong(1, bookId);
                        //noinspection DataFlowIssue
                        insert.bindLong(2, preDef.get(sidName));
                        insert.bindString(3, sid);

                        insert.executeInsert();
                    }
                }
            }
        }

        // null old columns, we'll delete them in a future version
        db.execSQL(UPDATE_ + TBL_BOOKS.getName() + _SET_
                   + legacyKeys.stream().map(domain -> domain + "=NULL")
                               .collect(Collectors.joining(",")));
    }

    private void db35AddMappingTables() {
        TBL_TAG_MAPPINGS.create(db, true);
        TagMappingDaoImpl.onPostCreate(db);

        TBL_TAGS.create(db, true);
        TBL_BOOK_TAG.create(db, true);
        db35MigrateGenres();

        // Override the user should they have hidden the 'genre' field
        final FieldVisibility globalFieldVisibility = ServiceLocator
                .getInstance().getGlobalFieldVisibility();
        globalFieldVisibility.setVisible(DBKey.FK_TAG, true);
        globalFieldVisibility.save();

    }

    private void db35MigrateGenres() {
        // all books with a genre set
        final String sqlSelect = SELECT_ + DBKey.PK_ID + ',' + "genre"
                                 + _FROM_ + TBL_BOOKS.getName()
                                 + _WHERE_ + "genre" + "<>''";

        final String sqlInsertTag =
                INSERT_INTO_ + DBDefinitions.TBL_TAGS.getName()
                + '(' + DBKey.TAGS.TAG + ") VALUES (?)";

        final String sqlLinkBook =
                INSERT_INTO_ + DBDefinitions.TBL_BOOK_TAG.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_TAG
                + ") VALUES(?,?)";

        final Map<String, Long> done = new HashMap<>();
        try (Cursor cursor = db.rawQuery(sqlSelect, null);
             SQLiteStatement insert = db.compileStatement(sqlInsertTag);
             SQLiteStatement linkBook = db.compileStatement(sqlLinkBook)) {
            while (cursor.moveToNext()) {
                final long bookId = cursor.getLong(0);
                final String genre = cursor.getString(1);

                // just convert; NO mapping during this upgrade.
                // Use a Set to eliminate duplicates
                final Set<String> tagNames = Arrays
                        .stream(GenreMigration.GENRE_SPLITTER_PATTERN.split(genre))
                        .map(String::strip)
                        .collect(Collectors.toSet());

                for (final String tagName : tagNames) {
                    final long tagId;
                    if (done.containsKey(tagName)) {
                        // copy the id from a tag we inserted before
                        //noinspection DataFlowIssue
                        tagId = done.get(tagName);
                    } else {
                        // insert tags we don't have yet
                        insert.bindString(1, tagName);
                        tagId = insert.executeInsert();
                        // remember
                        done.put(tagName, tagId);
                    }

                    // link the two
                    linkBook.bindLong(1, bookId);
                    linkBook.bindLong(2, tagId);
                    linkBook.executeInsert();
                }
            }
        }

        // empty old column, we'll delete it in a future version
        db.execSQL(UPDATE_ + TBL_BOOKS.getName() + _SET_ + "genre" + "=''");

        // Remove any genre based filters, they cannot be converted to a tag filter
        db.execSQL(DELETE_FROM_ + DBDefinitions.TBL_BOOKSHELF_FILTERS.getName()
                   + _WHERE_ + DBKey.BOOKSHELF.FILTER_NAME + "='" + "genre" + "'");

    }

    private void db36() {
        // db52 update REMOVED
        // identifierMigration.fixType(Identifier.SID_DNB);
    }

    private void db37() {
        // Recreate tabled with date/datetime fields migrated to "text"
        // Also takes care of adding DOM_TRANSLATION_ORIGINAL_LANGUAGE
        Upgrade.runWithoutConstraints(db, () -> {
            TBL_BOOKS.recreate(db);
            TBL_TOC_ENTRIES.recreate(db);
            TBL_DELETED_BOOKS.recreate(db);
            TBL_STRIPINFO_COLLECTION.recreate(db);
            TBL_CALIBRE_LIBRARIES.recreate(db);
        });

        TBL_LANG_MAPPINGS.create(db, true);
    }

    private void db38() {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db,
                DBDefinitions.DOM_STYLE_SHOW_GROUP_BOOK_COUNT);
    }

    private void db39() {
        // db52 update REMOVED
        // identifierMigration.initAuthorUrl(Set.of());

        TBL_AUTHOR_IDENTIFIER.create(db, true);
    }

    private void db40() {
        // db52 update REMOVED
        // identifierMigration.fixName(Identifier.SID_DNB);
        // identifierMigration.initBookUrl(Set.of(Identifier.SID_BNF, Identifier.SID_PORBASE));
    }

    private void db41() {
        TBL_AUTHORS.alterTableAddColumns(db,
                                         DBDefinitions.DOM_AUTHOR_BIRTH_DATE,
                                         DBDefinitions.DOM_AUTHOR_DEATH_DATE,
                                         DBDefinitions.DOM_AUTHOR_PICTURE_UUID);
    }

    private void db42() {
        // db52 update REMOVED
        // identifierMigration.initWikidataClaim(Set.of());
    }

    private void db43() {
        // enable the cover image 2+3 for ALL styles.
        db.execSQL(UPDATE_ + TBL_BOOKLIST_STYLES.getName()
                   + _SET_ + DBKey.STYLE.BOOK_DETAIL_FIELD_VISIBILITY
                   + '=' + DBKey.STYLE.BOOK_DETAIL_FIELD_VISIBILITY
                   + '|' + FieldVisibility.getBitValue(Set.of(DBKey.COVER[2], DBKey.COVER[3])));
    }

    private void db44() {
        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        // If the user never enabled the zoom-slider, force the default back to zero
        if (!prefs.getBoolean(CameraConfig.PK_CAMERA_ZOOM_CONTROL_SHOW, false)) {
            prefs.edit().putFloat(CameraConfig.PK_CAMERA_ZOOM_CONTROL_VALUE, 0f).apply();
        }

        // we need to rebuild the Author OB columns
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_TITLE_OB, true);
    }

    private void db45() {
        // GitHub #193: rebuild to restore the spaces
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_FTS, true);
    }

    private void db46() {
        // The primary key was expanded from
        // from: setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_AUTHOR_POSITION)
        // to:   setPrimaryKey(DOM_FK_BOOK, DOM_FK_AUTHOR, DOM_BOOK_AUTHOR_POSITION)
        Upgrade.runWithoutConstraints(db, () ->
                DBDefinitions.TBL_BOOK_AUTHOR.recreate(db));

        // GitHub #200: in short: #193 introduced a bug where the order-by column
        // could contain spaces. This led to "mergeable" data not being found,
        // which in turn led to creating duplicates.
        CleanOptions.setOptions(Set.of(
                CleanOptions.RemoveDuplicateAuthors,
                CleanOptions.RemoveDuplicatePublishers,
                CleanOptions.RemoveDuplicateSeries,
                CleanOptions.RemoveDuplicateTocEntries
        ));

        // Run the cleaner to remove duplicates as configured above
        StartupViewModel.schedule(context, StartupViewModel.PK_RUN_MAINTENANCE, true);
        // and rebuild both OB columns and the indexes
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_INDEXES, true);
    }

    private void db47() {
        // GitHub #216 fix/improvements
        CleanOptions.setOptions(Set.of(CleanOptions.ResolveAuthors));
        StartupViewModel.schedule(context, StartupViewModel.PK_RUN_MAINTENANCE, true);
    }

    private void db49() {
        db.execSQL(UPDATE_ + TBL_BOOKLIST_STYLES.getName()
                   + _SET_ + DBKey.STYLE.BOOK_LIST_FIELD_VISIBILITY
                   + '=' + DBKey.STYLE.BOOK_LIST_FIELD_VISIBILITY
                   + '|' + FieldVisibility.getBitValue(Set.of(DBKey.READ__BOOL)));
    }

    private void db50() {
        // GitHub #231: bug in backup/json/coders/IdentifierCoder
        // Backup files could contain the toString representation
        // of the wikidata author claim id, instead of the id itself.
        // Repair ALL builtin Identifiers:

        // db52 update REMOVED
        // identifierMigration.initWikidataClaim(Set.of());
    }
}

/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * A garbage bin with code used only during upgrades.
 */
public final class LegacyUpgrades {

    /** Archive ..7 import. */
    public static final Map<String, String> IDENTIFIERS = Map.of(
            "goodreads_book_id", Identifier.SID_GOODREADS_BOOK,
            "isfdb_book_id", Identifier.SID_ISFDB,
            "lt_book_id", Identifier.SID_LIBRARY_THING,
            "ol_book_id", Identifier.SID_OPEN_LIBRARY,
            "si_book_id", Identifier.SID_STRIP_INFO,
            "ld_book_id", Identifier.SID_LAST_DODO_NL,
            "bdt_book_id", Identifier.SID_BEDETHEQUE
    );
    static final Domain DOM_ESID_BEDETHEQUE =
            new Domain.Builder("bdt_book_id", SqLiteDataType.Integer)
                    .build();
    private static final String TAG = "LegacyUpgrades";
    private static final String DBKEY_GENRE = "genre";

    private static final String PK_SHOW_TITLE_REORDERED = "show.title.reordered";
    private static final String PK_FIELDS_VISIBILITY_KEYS = "fields.visibility.";
    private static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST =
            "show.author.name.given_first";
    private static final String PK_SORT_AUTHOR_NAME_GIVEN_FIRST =
            "sort.author.name.given_first";
    /** Genre string migration splitter characters. */
    private static final Pattern GENRE_SPLITTER_PATTERN = Pattern.compile("[/,;>]");

    private LegacyUpgrades() {
    }

    static void migrateV19Styles(@NonNull final Context context,
                                 @NonNull final SQLiteDatabase db) {
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(context);
        // change the name of these for easier migration
        final boolean visSeries = global.getBoolean(
                PK_FIELDS_VISIBILITY_KEYS + DBKey.SERIES.TITLE, true);
        final boolean visPublisher = global.getBoolean(
                PK_FIELDS_VISIBILITY_KEYS + DBKey.PUBLISHER.NAME, true);

        global.edit()
              .putBoolean(PK_FIELDS_VISIBILITY_KEYS + DBKey.FK_SERIES, visSeries)
              .putBoolean(PK_FIELDS_VISIBILITY_KEYS + DBKey.FK_PUBLISHER, visPublisher)
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
                DBDefinitions.DOM_STYLE_BOOK_DETAIL_FIELD_VISIBILITY,
                DBDefinitions.DOM_STYLE_BOOK_LIST_FIELD_VISIBILITY);

        final List<String> uuids = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT uuid FROM " + TBL_BOOKLIST_STYLES.getName()
                + " WHERE " + DBKey.STYLE.TYPE + "=" + Style.Type.User.getId(), null)) {
            while (cursor.moveToNext()) {
                uuids.add(cursor.getString(0));
            }
        }

        try (SQLiteStatement stmt = db.compileStatement(
                "UPDATE " + TBL_BOOKLIST_STYLES.getName() + " SET "
                + DBKey.STYLE.NAME + "=?, "

                + DBKey.STYLE.GROUPS + "=?,"
                + DBKey.STYLE.GROUPS_AUTHOR_SHOW_UNDER_EACH + "=?,"
                + DBKey.STYLE.GROUPS_AUTHOR_PRIMARY_TYPE + "=?,"
                + DBKey.STYLE.GROUPS_SERIES_SHOW_UNDER_EACH + "=?,"
                + DBKey.STYLE.GROUPS_PUBLISHER_SHOW_UNDER_EACH + "=?,"
                + DBKey.STYLE.GROUPS_BOOKSHELF_SHOW_UNDER_EACH + "=?,"

                + DBKey.STYLE.EXP_LEVEL + "=?,"
                + DBKey.STYLE.ROW_USES_PREF_HEIGHT + "=?,"
                + DBKey.STYLE.AUTHOR_SORT_BY_GIVEN_NAME + "=?,"
                + DBKey.STYLE.AUTHOR_SHOW_BY_GIVEN_NAME + "=?,"
                + DBKey.STYLE.TEXT_SCALE + "=?,"
                + DBKey.STYLE.COVER_SCALE + "=?,"
                + DBKey.STYLE.LIST_HEADER + "=?,"
                + DBKey.STYLE.BOOK_DETAIL_FIELD_VISIBILITY + "=?,"
                + DBKey.STYLE.BOOK_LIST_FIELD_VISIBILITY + "=?"

                + " WHERE " + DBKey.STYLE.UUID + "=?")) {

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
                                                null), Author.TYPE_UNKNOWN));
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
                        PK_SORT_AUTHOR_NAME_GIVEN_FIRST, false) ? 1 : 0);
                stmt.bindLong(++c, stylePrefs.getBoolean(
                        PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false) ? 1 : 0);
                stmt.bindLong(++c, stylePrefs.getInt(
                        "style.booklist.scale.font", TextScale.DEFAULT.getId()));
                stmt.bindLong(++c, stylePrefs.getInt(
                        "style.booklist.scale.thumbnails", CoverScale.DEFAULT.getId()));
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
                    listFields.add(DBKey.PUBLICATION_DATE);
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
                    listFields.add(DBKey.ISBN);
                }

                stmt.bindLong(++c, FieldVisibility.getBitValue(listFields));

                stmt.bindString(++c, uuid);
                stmt.executeUpdateDelete();

                context.deleteSharedPreferences(uuid);
            });
        }
    }

    static void migrateV21SearchEnginePrefs(final Context context) {
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

    static void removeDuplicateAuthorsV23(@NonNull final SQLiteDatabase db) {

        // find the names for duplicate author; i.e. identical family and given names.
        final List<Pair<String, String>> authors = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT " + DBKey.AUTHOR.FAMILY_NAME + ',' + DBKey.AUTHOR.GIVEN_NAMES
                + " FROM " + TBL_AUTHORS.getName()
                + " GROUP BY " + DBKey.AUTHOR.FAMILY_NAME + ',' + DBKey.AUTHOR.GIVEN_NAMES
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
                    + " WHERE " + DBKey.AUTHOR.FAMILY_NAME + "=?"
                    + " AND " + DBKey.AUTHOR.GIVEN_NAMES + "=?",
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

            sql = "UPDATE " + DBDefinitions.TBL_BOOK_AUTHOR.getName()
                  + " SET " + DBKey.FK_AUTHOR + "=" + keep
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

    static void removeDuplicateTocEntriesV23(@NonNull final SQLiteDatabase db) {
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

            sql = "UPDATE " + DBDefinitions.TBL_BOOK_TOC_ENTRIES
                  + " SET " + DBKey.FK_TOC_ENTRY + "=" + keep
                  + " WHERE " + DBKey.FK_TOC_ENTRY + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e,
                         "Update TBL_BOOK_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = "DELETE FROM " + TBL_TOC_ENTRIES
                  + " WHERE " + DBKey.PK_ID + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Delete TBL_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }
        }
    }

    /**
     * Check and migrate pre-db25 global field visibility keys.
     *
     * @param prefs to migrate
     */
    private static void migrateV24GlobalFieldVisibility(@NonNull final SharedPreferences prefs) {
        final Pattern dot = Pattern.compile("\\.");
        final List<String> oldVisKeys = prefs.getAll()
                                             .keySet()
                                             .stream()
                                             .filter(key -> key.startsWith(
                                                     PK_FIELDS_VISIBILITY_KEYS))
                                             .collect(Collectors.toList());
        if (!oldVisKeys.isEmpty()) {
            final FieldVisibility fieldVisibility = new FieldVisibility();
            oldVisKeys.forEach(oldKey -> {
                final boolean value = prefs.getBoolean(oldKey, false);
                final String dbKey = dot.split(oldKey, 3)[2];
                fieldVisibility.setVisible(dbKey, value);
            });

            fieldVisibility.save(prefs);
        }
    }

    static void migrateV28ReorderPref(@NonNull final Context context,
                                      @NonNull final SQLiteDatabase db) {
        final int value =
                PreferenceManager.getDefaultSharedPreferences(context)
                                 .getBoolean(PK_SHOW_TITLE_REORDERED, false)
                ? 1 : 0;

        // We apply the setting to ALL styles as it was the default for all.
        // (including the built-in which is pointless but easier)
        try (SQLiteStatement stmt = db.compileStatement(
                "UPDATE " + TBL_BOOKLIST_STYLES.getName() + " SET "
                + DBKey.STYLE.TITLE_SHOW_REORDERED + "=?")) {
            stmt.bindLong(1, value);
            stmt.executeUpdateDelete();
        }
    }

    static void migrateV35Sids(@NonNull final SQLiteDatabase db) {
        final Set<String> legacyKeys = IDENTIFIERS.keySet();
        final Collection<String> legacyValues = IDENTIFIERS.values();

        final Map<String, Integer> predef = new HashMap<>();
        final String predefSql = "SELECT " + DBKey.PK_ID + ',' + DBKey.IDENTIFIERS.KEY
                                 + " FROM " + DBDefinitions.TBL_IDENTIFIERS.getName();
        try (Cursor cursor = db.rawQuery(predefSql, null)) {
            while (cursor.moveToNext()) {
                final int id = cursor.getInt(0);
                final String name = cursor.getString(1);
                predef.put(name, id);
            }
        }

        final String sqlSelect = "SELECT " + DBKey.PK_ID
                                 + ',' + String.join(",", legacyKeys)
                                 + " FROM " + TBL_BOOKS.getName()
                                 + " WHERE "
                                 + legacyKeys.stream()
                                             .map(c -> "(" + c + " IS NOT NULL)")
                                             .collect(Collectors.joining(" OR "));

        final String sqlInsert = "INSERT INTO " + DBDefinitions.TBL_BOOK_IDENTIFIER.getName()
                                 + '(' + DBKey.FK_BOOK
                                 + ',' + DBKey.FK_IDENTIFIER
                                 + ',' + DBKey.BOOK_IDENTIFIER_SID
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
                        insert.bindLong(2, predef.get(sidName));
                        insert.bindString(3, sid);

                        insert.executeInsert();
                    }
                }
            }
        }

        // null old columns, we'll delete them in a future version
        db.execSQL("UPDATE " + TBL_BOOKS + " SET "
                   + legacyKeys.stream().map(domain -> domain + "=NULL")
                               .collect(Collectors.joining(",")));
    }

    static void migrateV35Genre(@NonNull final SQLiteDatabase db) {

        // all books with a genre set
        final String sqlSelect = "SELECT " + DBKey.PK_ID + ',' + DBKEY_GENRE
                                 + " FROM " + TBL_BOOKS.getName()
                                 + " WHERE " + DBKEY_GENRE + "<>''";

        final String sqlInsertTag =
                "INSERT INTO " + DBDefinitions.TBL_TAGS.getName()
                + '(' + DBKey.TAGS.TAG + ") VALUES (?)";

        final String sqlLinkBook =
                "INSERT INTO " + DBDefinitions.TBL_BOOK_TAG.getName()
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
                final Set<String> tagNames = Arrays.stream(GENRE_SPLITTER_PATTERN.split(genre))
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
        db.execSQL("UPDATE " + TBL_BOOKS.getName() + " SET " + DBKEY_GENRE + "=''");

        // Remove any genre based filters, they cannot be converted to a tag filter
        db.execSQL("DELETE FROM " + DBDefinitions.TBL_BOOKSHELF_FILTERS.getName()
                   + " WHERE " + DBKey.BOOKSHELF.FILTER_NAME + "='" + DBKEY_GENRE + "'");

    }

    /**
     * Convert the {@code genre} string to a list of {@link Tag}s.
     *
     * @param genre to convert
     *
     * @return a list of new Tags, with id {@code 0}
     */
    @NonNull
    public static List<Tag> migrateGenre(@NonNull final String genre) {
        // sanity
        if (genre.isBlank()) {
            return List.of();
        }
        return Arrays.stream(GENRE_SPLITTER_PATTERN.split(genre))
                     .map(String::strip)
                     .map(Tag::new)
                     .collect(Collectors.toList());
    }

    /**
     * Migrate and remove all keys which were declared obsolete.
     *
     * <ul>
     *     <li>migrate pre-db25 global field visibility keys</li>
     *     <li>remove obsolete keys</li>
     * </ul>
     *
     * @param context Current context
     */
    public static void migratePreferenceKeys(@NonNull final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        migrateV24GlobalFieldVisibility(prefs);

        // Now remove all obsolete keys.
        final SharedPreferences.Editor editor = prefs.edit();

        prefs.getAll()
             .keySet()
             .stream()
             .filter(key -> key.startsWith("style.booklist.")
                            || key.startsWith(PK_FIELDS_VISIBILITY_KEYS))
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
              .remove("isfdb.search.uses.publisher")
              .remove("librarything.dev_key")
              .remove("scanner.preferred")
              .remove("search.form.advanced")
              .remove("search.site.goodreads.data.enabled")
              .remove("search.site.goodreads.covers.enabled")
              .remove(PK_SHOW_TITLE_REORDERED)
              .remove(PK_SHOW_AUTHOR_NAME_GIVEN_FIRST)
              .remove(PK_SORT_AUTHOR_NAME_GIVEN_FIRST)
              .remove("startup.lastVersion")
              .remove("tmp.edit.book.tab.authSer")
              .remove("ui.messages.use")

              // Editing the URL for these sites has been removed.
              .remove("isfdb.host.url")
              .remove("librarything.host.url")

              .apply();

        // Copy the legacy key to the bit-value
        final boolean lending = prefs.getBoolean(FieldVisibility.PK_LOANS, false);
        final FieldVisibility fieldVisibility = ServiceLocator.getInstance()
                                                              .getGlobalFieldVisibility();
        fieldVisibility.setVisible(DBKey.LOANEE_NAME, lending);
        fieldVisibility.save(prefs);

        // replaced by a database table in db36
        context.deleteSharedPreferences("language2iso3");
    }

    static void insertGlobalStyleIfNotYetDone(@NonNull final Context context,
                                              @NonNull final SQLiteDatabase db) {

        final boolean install;
        try (SQLiteStatement stmt = db.compileStatement(
                "SELECT COUNT(" + DBKey.STYLE.TYPE + ") FROM " + TBL_BOOKLIST_STYLES
                + " WHERE " + DBKey.STYLE.TYPE + "=2")) {
            install = 0 == stmt.simpleQueryForLong();
        }

        if (install) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final GlobalStyle style = GlobalStyle.createDefault();
            style.setSortAuthorByGivenName(
                    prefs.getBoolean(PK_SORT_AUTHOR_NAME_GIVEN_FIRST, false));
            style.setShowAuthorByGivenName(
                    prefs.getBoolean(PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false));

            StyleDaoImpl.insertGlobalDefaults(db, style);
        }
    }
}

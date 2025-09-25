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
import android.database.sqlite.SQLiteDoneException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.core.database.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISNI;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreCustomFieldDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.IdentifierDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.TagMappingDaoImpl;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih.DatabazeKnihSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.douban.DoubanSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.wikidata.WikidataSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.BNF;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.FantLab;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.FantaScienza;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.NooSFere;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Porbase;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.StoryGraph;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.TerceraFundacion;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.VIAF;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.WorldCat;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHOR_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF_FILTERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_CUSTOM_FIELDS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_LANG_MAPPINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAG_MAPPINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * A garbage bin with code used only during upgrades.
 *
 * @noinspection CheckStyle
 */
public final class LegacyUpgrades {

    /** Archive ..7 import. */
    public static final Map<String, String> IDENTIFIERS = Map.of(
            "goodreads_book_id", Identifier.SID_GOODREADS,
            "isfdb_book_id", Identifier.SID_ISFDB,
            "lt_book_id", Identifier.SID_LIBRARY_THING,
            "ol_book_id", Identifier.SID_OPEN_LIBRARY,
            "si_book_id", Identifier.SID_STRIP_INFO,
            "ld_book_id", Identifier.SID_LAST_DODO_NL,
            "bdt_book_id", Identifier.SID_BEDETHEQUE
    );
    private static final String TAG = "LegacyUpgrades";

    private static final String PK_FIELDS_VISIBILITY_KEYS = "fields.visibility.";
    /** Genre string migration splitter characters. */
    private static final Pattern GENRE_SPLITTER_PATTERN = Pattern.compile("[/,;>]");

    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String INSERT_INTO_ = "INSERT INTO ";
    private static final String SELECT_ = "SELECT ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _AND_ = " AND ";
    private static final String _FROM_ = " FROM ";
    private static final String _GROUP_BY_ = " GROUP BY ";
    private static final String _SET_ = " SET ";
    private static final String _WHERE_ = " WHERE ";

    private LegacyUpgrades() {
    }

    static void v16onUpgrade(@NonNull final Context context,
                             @NonNull final SQLiteDatabase db) {
        TBL_STRIPINFO_COLLECTION.create(db, true);

        context.deleteDatabase("taskqueue.db");
    }

    static void v17onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_CALIBRE_CUSTOM_FIELDS.create(db, true);
        CalibreCustomFieldDaoImpl.onPostCreate(db);
    }

    static void v18onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKSHELF_FILTERS.create(db, true);
    }

    static void v19onUpgrade(@NonNull final Context context,
                             @NonNull final SQLiteDatabase db) {
        // Migrate all styles
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(context);
        // change the name of these for easier migration
        final boolean visSeries = global.getBoolean(
                PK_FIELDS_VISIBILITY_KEYS + "series_name", true);
        final boolean visPublisher = global.getBoolean(
                PK_FIELDS_VISIBILITY_KEYS + "publisher_name", true);

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
                + _WHERE_ + DBKey.STYLE.TYPE + "=" + Style.Type.User.getId(), null)) {
            while (cursor.moveToNext()) {
                uuids.add(cursor.getString(0));
            }
        }

        try (SQLiteStatement stmt = db.compileStatement(
                UPDATE_ + TBL_BOOKLIST_STYLES.getName() + _SET_
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

                + _WHERE_ + DBKey.STYLE.UUID + "=?")) {

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
                        "sort.author.name.given_first", false) ? 1 : 0);
                stmt.bindLong(++c, stylePrefs.getBoolean(
                        "show.author.name.given_first", false) ? 1 : 0);
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

    static void v20onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_AUTO_UPDATE);
    }

    static void v21onUpgrade(final Context context) {
        // migrate SearchEngine Preferences
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

    static void v22onUpgrade(@NonNull final SQLiteDatabase db) {
        // remove built-in style ID_DEPRECATED_1
        db.execSQL("DELETE FROM " + TBL_BOOKLIST_STYLES.getName() + " WHERE _id=-2");
    }

    static void v23onUpgrade(@NonNull final SQLiteDatabase db) {
        // Up to version 22 we had a bug in how we'd store TOC entries which could create
        // duplicate authors. Fixed in 23 but we need to do a clean up during upgrade.
        v23removeDuplicateAuthors(db);
        // as a result of the author cleanup, we now might have duplicate toc entries,
        // same algorithm to clean those up
        v23removeDuplicateTocEntries(db);

        // Add pen-name support
        TBL_PSEUDONYM_AUTHOR.create(db, true);
        // new search-engine added
        TBL_BOOKS.alterTableAddColumns(db, new Domain.Builder(
                "bdt_book_id", SqLiteDataType.Integer).build());
    }

    private static void v23removeDuplicateAuthors(@NonNull final SQLiteDatabase db) {

        // find the names for duplicate author; i.e. identical family and given names.
        final List<Pair<String, String>> authors = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                SELECT_ + DBKey.AUTHOR.FAMILY_NAME + ',' + DBKey.AUTHOR.GIVEN_NAMES
                + _FROM_ + TBL_AUTHORS.getName()
                + _GROUP_BY_ + DBKey.AUTHOR.FAMILY_NAME + ',' + DBKey.AUTHOR.GIVEN_NAMES
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
                    SELECT_ + DBKey.PK_ID + _FROM_ + TBL_AUTHORS.getName()
                    + _WHERE_ + DBKey.AUTHOR.FAMILY_NAME + "=?"
                    + _AND_ + DBKey.AUTHOR.GIVEN_NAMES + "=?",
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

            sql = UPDATE_ + DBDefinitions.TBL_BOOK_AUTHOR.getName()
                  + _SET_ + DBKey.FK_AUTHOR + "=" + keep
                  + _WHERE_ + DBKey.FK_AUTHOR + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Update TBL_BOOK_AUTHOR: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = UPDATE_ + TBL_TOC_ENTRIES.getName() + _SET_ + DBKey.FK_AUTHOR + "=" + keep
                  + _WHERE_ + DBKey.FK_AUTHOR + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Update TBL_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = DELETE_FROM_ + TBL_AUTHORS.getName()
                  + _WHERE_ + DBKey.PK_ID + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Delete TBL_AUTHORS: ids=" + ids);
                throw e;
            }
        }
    }

    private static void v23removeDuplicateTocEntries(@NonNull final SQLiteDatabase db) {
        // find the duplicate tocs; i.e. identical author and title.
        final List<Pair<Long, String>> entries = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                SELECT_ + DBKey.FK_AUTHOR + ',' + DBKey.TITLE
                + _FROM_ + TBL_TOC_ENTRIES.getName()
                + _GROUP_BY_ + DBKey.FK_AUTHOR + ',' + DBKey.TITLE
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
                    SELECT_ + DBKey.PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
                    + _WHERE_ + DBKey.FK_AUTHOR + "=?"
                    + _AND_ + DBKey.TITLE + "=?",
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

            sql = UPDATE_ + DBDefinitions.TBL_BOOK_TOC_ENTRIES.getName()
                  + _SET_ + DBKey.FK_TOC_ENTRY + "=" + keep
                  + _WHERE_ + DBKey.FK_TOC_ENTRY + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e,
                         "Update TBL_BOOK_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }

            sql = DELETE_FROM_ + TBL_TOC_ENTRIES.getName()
                  + _WHERE_ + DBKey.PK_ID + " IN (" + ids + ')';
            //noinspection CheckStyle,OverlyBroadCatchBlock
            try (SQLiteStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            } catch (@NonNull final Exception e) {
                logger.e(TAG, e, "Delete TBL_TOC_ENTRIES: keep=" + keep + ", ids=" + ids);
                throw e;
            }
        }
    }

    static void v24onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_TRANSLATION_ORIGINAL_TITLE);
    }

    static void v25onUpgrade(final Context context,
                             @NonNull final SQLiteDatabase db) {
        TBL_DELETED_BOOKS.create(db, true);
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_FTS, true);
    }

    static void v26onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db,
                DBDefinitions.DOM_STYLE_BOOK_LIST_FIELD_ORDER_BY,
                DBDefinitions.DOM_STYLE_COVER_CLICK_ACTION,
                DBDefinitions.DOM_STYLE_LAYOUT);
    }

    static void v28onUpgrade(@NonNull final Context context,
                             @NonNull final SQLiteDatabase db) {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db,
                DBDefinitions.DOM_STYLE_TITLE_SHOW_REORDERED);

        // migrateReorderPref
        final int value = PreferenceManager.getDefaultSharedPreferences(context)
                                           .getBoolean("show.title.reordered", false)
                          ? 1 : 0;

        // We apply the setting to ALL styles as it was the default for all.
        // (including the built-in which is pointless but easier)
        try (SQLiteStatement stmt = db.compileStatement(
                UPDATE_ + TBL_BOOKLIST_STYLES.getName() + _SET_
                + DBKey.STYLE.TITLE_SHOW_REORDERED + "=?")) {
            stmt.bindLong(1, value);
            stmt.executeUpdateDelete();
        }
    }

    static void v29onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_STRIPINFO_COLLECTION.alterTableAddColumns(
                db, DBDefinitions.DOM_STRIP_INFO_DIGITAL);
    }

    static void v31onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db, DBDefinitions.DOM_STYLE_COVER_LONG_CLICK_ACTION);
    }

    static void v32onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_BOOK_READ_PROGRESS);
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db, DBDefinitions.DOM_STYLE_READ_STATUS_WITH_PROGRESS);
    }

    static void v34onUpgrade(@NonNull final SQLiteDatabase db) {
        // recreate tables due to some columns having their COLLATION changed

        // THIS WILL COMMIT ALL PREVIOUS UPDATES
        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(false);
        db.beginTransaction();

        // DBDefinitions.DOM_STYLE_NAME
        v34RecreateTable(db, TBL_BOOKLIST_STYLES);
        // DBDefinitions.DOM_BOOKSHELF_NAME
        v34RecreateTable(db, TBL_BOOKSHELF);
        // DBDefinitions.DOM_AUTHOR_FAMILY_NAME_OB, DBDefinitions.DOM_AUTHOR_GIVEN_NAMES_OB
        v34RecreateTable(db, TBL_AUTHORS);
        // DBDefinitions.DOM_SERIES_TITLE_OB
        v34RecreateTable(db, TBL_SERIES);
        // DBDefinitions.DOM_PUBLISHER_NAME_OB
        v34RecreateTable(db, TBL_PUBLISHERS);
        // DBDefinitions.DOM_TITLE_OB
        v34RecreateTable(db, TBL_BOOKS);

        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(true);
        db.beginTransaction();
    }

    /**
     * AS USED FOR THE UPGRADE FROM V33 TO V34 ONLY.
     * This creates/expects all columns to be identical except for the sqlite datatype.
     *
     * @param db Database Access
     * @param td table
     */
    private static void v34RecreateTable(@NonNull final SQLiteDatabase db,
                                         @NonNull final TableDefinition td) {
        final String dstTableName = "copyOf" + td.getName();
        db.execSQL(td.getCreateStatement(dstTableName, true));

        final List<String> srcColumns = td.getTableInfo(db)
                                          .getColumns()
                                          .stream()
                                          .map(ColumnInfo::getName)
                                          .collect(Collectors.toList());

        final List<String> dstColumns = new ArrayList<>(srcColumns);

        db.execSQL(
                "INSERT INTO " + dstTableName + " (" + String.join(",", dstColumns) + ")"
                + " SELECT " + String.join(",", srcColumns) + " FROM " + td.getName());

        db.execSQL("DROP TABLE " + td.getName());
        db.execSQL("ALTER TABLE " + dstTableName + " RENAME TO " + td.getName());
    }

    static void v35oUpgrade(@NonNull final Context context,
                            @NonNull final SQLiteDatabase db) {
        v35AddCitationType(db);
        v35AddIdentifiersTable(context, db);
        v35AddMappingTables(context, db);

        // The format was changed
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_FTS, true);

        // StripInfo collection support was never finished nor activated in a release build.
        // Furthermore, it turns out each book with a "stripinfo" SID always wrote
        // collection data which obviously always was 'empty'.
        // and we're making a fresh start... drop and recreate the table.
        db.execSQL("DROP TABLE " + TBL_STRIPINFO_COLLECTION.getName());
        TBL_STRIPINFO_COLLECTION.create(db, true);
    }

    private static void v35AddCitationType(@NonNull final SQLiteDatabase db) {
        // depending on the install/upgrade path, we might already have
        // added the CITATION_TYPE column
        final ColumnInfo citationType = TBL_BOOKLIST_STYLES
                .getTableInfo(db).getColumn(DBKey.STYLE.CITATION_TYPE);
        if (citationType == null) {
            TBL_BOOKLIST_STYLES.alterTableAddColumns(
                    db,
                    DBDefinitions.DOM_STYLE_CITATION_TYPE);
        }
    }

    private static void v35AddIdentifiersTable(@NonNull final Context context,
                                               @NonNull final SQLiteDatabase db) {
        TBL_IDENTIFIERS.create(db, true);
        TBL_BOOK_IDENTIFIER.create(db, true);
        IdentifierDaoImpl.onPostCreate(context, db);
        v35migrateSids(db);
    }

    private static void v35migrateSids(@NonNull final SQLiteDatabase db) {
        final Set<String> legacyKeys = IDENTIFIERS.keySet();
        final Collection<String> legacyValues = IDENTIFIERS.values();

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

    private static void v35AddMappingTables(@NonNull final Context context,
                                            @NonNull final SQLiteDatabase db) {
        TBL_TAG_MAPPINGS.create(db, true);
        TagMappingDaoImpl.onPostCreate(db);

        TBL_TAGS.create(db, true);
        TBL_BOOK_TAG.create(db, true);
        v35migrateGenres(db);

        // Override the user should they have hidden the 'genre' field
        final FieldVisibility globalFieldVisibility = ServiceLocator
                .getInstance().getGlobalFieldVisibility();
        globalFieldVisibility.setVisible(DBKey.FK_TAG, true);
        globalFieldVisibility.save(PreferenceManager.getDefaultSharedPreferences(context));

    }

    private static void v35migrateGenres(@NonNull final SQLiteDatabase db) {

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
        db.execSQL(UPDATE_ + TBL_BOOKS.getName() + _SET_ + "genre" + "=''");

        // Remove any genre based filters, they cannot be converted to a tag filter
        db.execSQL(DELETE_FROM_ + DBDefinitions.TBL_BOOKSHELF_FILTERS.getName()
                   + _WHERE_ + DBKey.BOOKSHELF.FILTER_NAME + "='" + "genre" + "'");

    }

    static void v36onUpgrade(@NonNull final SQLiteDatabase db) {
        db.execSQL(UPDATE_ + TBL_IDENTIFIERS.getName()
                   + _SET_ + DBKey.IDENTIFIERS.TYPE + "='" + Identifier.TYPE_STRING + '\''
                   + _WHERE_ + DBKey.IDENTIFIERS.KEY + "='" + Identifier.SID_DNB + '\'');
    }

    static void v37onUpgrade(@NonNull final SQLiteDatabase db) {
        // Recreate tabled with date/datetime fields migrated to "text"
        // Also takes care of adding DOM_TRANSLATION_ORIGINAL_LANGUAGE

        // THIS WILL COMMIT ALL PREVIOUS UPDATES
        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(false);
        db.beginTransaction();

        TBL_BOOKS.recreate(db);
        TBL_TOC_ENTRIES.recreate(db);
        TBL_DELETED_BOOKS.recreate(db);
        TBL_STRIPINFO_COLLECTION.recreate(db);
        TBL_CALIBRE_LIBRARIES.recreate(db);

        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(true);
        db.beginTransaction();

        TBL_LANG_MAPPINGS.create(db, true);
    }

    static void v38onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_BOOKLIST_STYLES.alterTableAddColumns(
                db,
                DBDefinitions.DOM_STYLE_SHOW_GROUP_BOOK_COUNT);
    }

    static void v39onUpgrade(@NonNull final SQLiteDatabase db) {
        v39AddIdentifierAuthorUrl(db);
        // this is new for this release
        TBL_AUTHOR_IDENTIFIER.create(db, true);
    }

    private static void v39AddIdentifierAuthorUrl(@NonNull final SQLiteDatabase db) {
        // depending on the install/upgrade path, we might already have
        // added the AUTHOR_URI column and the identifier updates.
        final ColumnInfo authorUri = TBL_IDENTIFIERS
                .getTableInfo(db).getColumn(DBKey.IDENTIFIERS.AUTHOR_URI);
        if (authorUri == null) {
            TBL_IDENTIFIERS.alterTableAddColumns(db, DBDefinitions.DOM_IDENTIFIER_AUTHOR_URI);

            // update the Identifiers adding the AuthorUri
            // We don't check success, the row may have been deleted which is fine
            try (SQLiteStatement stmt = db.compileStatement(
                    UPDATE_ + TBL_IDENTIFIERS.getName()
                    + _SET_ + DBKey.IDENTIFIERS.AUTHOR_URI + "=?"
                    + _WHERE_ + DBKey.IDENTIFIERS.KEY + "=?")) {
                // see Identifier#createInitialList
                stmt.bindString(1, BedethequeSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_BEDETHEQUE);
                stmt.executeUpdateDelete();
                stmt.bindString(1, BNF.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_BNF);
                stmt.executeUpdateDelete();
                stmt.bindString(1, DnbSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_DNB);
                stmt.executeUpdateDelete();
                stmt.bindString(1, DoubanSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_DOUBAN);
                stmt.executeUpdateDelete();
                stmt.bindString(1, FantLab.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_FANTLAB);
                stmt.executeUpdateDelete();
                stmt.bindString(1, GoodreadsSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_GOODREADS);
                stmt.executeUpdateDelete();
                stmt.bindString(1, IsfdbSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_ISFDB);
                stmt.executeUpdateDelete();
                stmt.bindString(1, KbNlSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_KBNL);
                stmt.executeUpdateDelete();
                stmt.bindString(1, LastDodoSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_LAST_DODO_NL);
                stmt.executeUpdateDelete();
                stmt.bindString(1, FantaScienza.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_NILF);
                stmt.executeUpdateDelete();
                stmt.bindString(1, NooSFere.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_NOOSFERE);
                stmt.executeUpdateDelete();
                stmt.bindString(1, WorldCat.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_OCLC);
                stmt.executeUpdateDelete();
                stmt.bindString(1, OpenLibrarySearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_OPEN_LIBRARY);
                stmt.executeUpdateDelete();
                stmt.bindString(1, StripInfoSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_STRIP_INFO);
                stmt.executeUpdateDelete();
                stmt.bindString(1, TerceraFundacion.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_TERCERA_FUNDACION);
                stmt.executeUpdateDelete();
                stmt.bindString(1, "%s");
                stmt.bindString(2, Identifier.SID_URI);
                stmt.executeUpdateDelete();
                stmt.bindString(1, WikidataSearchEngine.AUTHOR_URL);
                stmt.bindString(2, Identifier.SID_WIKIDATA);
                stmt.executeUpdateDelete();
            }
        }
    }

    static void v40onUpgrade(@NonNull final Context context,
                             @NonNull final SQLiteDatabase db) {
        // fix urls
        v40updateIdentifierBookUrl(db,
                                   new Pair<>(Identifier.SID_BNF, BNF.BOOK_URL),
                                   new Pair<>(Identifier.SID_PORBASE, Porbase.BOOK_URL));

        // fix name
        try (SQLiteStatement stmt = db.compileStatement(
                "UPDATE " + TBL_IDENTIFIERS
                + " SET " + DBKey.IDENTIFIERS.NAME + "=?"
                + " WHERE " + DBKey.IDENTIFIERS.KEY + "=?")) {
            stmt.bindString(1, context.getString(R.string.identifier_dnb));
            stmt.bindString(2, Identifier.SID_DNB);
            stmt.executeUpdateDelete();
        }
    }

    @SafeVarargs
    private static void v40updateIdentifierBookUrl(@NonNull final SQLiteDatabase db,
                                                   @NonNull final Pair<String, String>...
                                                           keyUrlPairs) {
        try (SQLiteStatement stmt = db.compileStatement(
                "UPDATE " + TBL_IDENTIFIERS
                + " SET " + DBKey.IDENTIFIERS.BOOK_URI + "=?"
                + " WHERE " + DBKey.IDENTIFIERS.KEY + "=?")) {

            for (final Pair<String, String> ku : keyUrlPairs) {
                stmt.bindString(1, ku.second);
                stmt.bindString(2, ku.first);
                stmt.executeUpdateDelete();
            }
        }
    }

    static void v41onUpgrade(@NonNull final SQLiteDatabase db) {
        TBL_AUTHORS.alterTableAddColumns(db,
                                         DBDefinitions.DOM_AUTHOR_BIRTH_DATE,
                                         DBDefinitions.DOM_AUTHOR_DEATH_DATE,
                                         DBDefinitions.DOM_AUTHOR_PICTURE_UUID);
    }

    static void insertGlobalStyleIfNotYetDone(@NonNull final Context context,
                                              @NonNull final SQLiteDatabase db) {
        final boolean install;
        try (SQLiteStatement stmt = db.compileStatement(
                "SELECT COUNT(" + DBKey.STYLE.TYPE + ") FROM " + TBL_BOOKLIST_STYLES.getName()
                + _WHERE_ + DBKey.STYLE.TYPE + "=2")) {
            install = 0 == stmt.simpleQueryForLong();
        }

        if (install) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final GlobalStyle style = GlobalStyle.createDefault();
            style.setSortAuthorByGivenName(
                    prefs.getBoolean("sort.author.name.given_first", false));
            style.setShowAuthorByGivenName(
                    prefs.getBoolean("show.author.name.given_first", false));

            StyleDaoImpl.insertGlobalDefaults(db, style);
        }
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
     * Check and migrate pre-db25 global field visibility keys.
     *
     * @param prefs to migrate
     */
    private static void v24migrateGlobalFieldVisibility(@NonNull final SharedPreferences prefs) {
        final Pattern dot = Pattern.compile("\\.");
        final List<String> oldVisKeys = prefs.getAll()
                                             .keySet()
                                             .stream()
                                             .filter(key -> key.startsWith(
                                                     PK_FIELDS_VISIBILITY_KEYS))
                                             .collect(Collectors.toList());
        if (!oldVisKeys.isEmpty()) {
            final FieldVisibility fieldVisibility = ServiceLocator.getInstance()
                                                                  .getGlobalFieldVisibility();
            oldVisKeys.forEach(oldKey -> {
                final boolean value = prefs.getBoolean(oldKey, false);
                final String dbKey = dot.split(oldKey, 3)[2];
                fieldVisibility.setVisible(dbKey, value);
            });

            fieldVisibility.save(prefs);
        }
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

        // This will take care of old keys in general, but will
        // ALSO copy the FieldVisibility.PK_LOANS which is still in use.
        v24migrateGlobalFieldVisibility(prefs);

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

              .remove("fields.update.usage.Book:author_array")
              .remove("fields.update.usage.Book:author_list")
              .remove("fields.update.usage.Book:fileSpec:0")
              .remove("fields.update.usage.Book:fileSpec:1")
              .remove("fields.update.usage.Book:publisher_array")
              .remove("fields.update.usage.Book:publisher_list")
              .remove("fields.update.usage.Book:series_array")
              .remove("fields.update.usage.Book:series_list")
              .remove("fields.update.usage.Book:toc_array")
              .remove("fields.update.usage.Book:toc_list")
              .remove("fields.update.usage.Book:toc_titles_array")

              .remove("fields.update.usage.author_array")
              .remove("fields.update.usage.publisher_array")
              .remove("fields.update.usage.series_array")
              .remove("fields.update.usage.toc_titles_array")

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
              .remove("show.title.reordered")
              .remove("show.author.name.given_first")
              .remove("sort.author.name.given_first")
              .remove("startup.lastVersion")
              // hardcoded to use 'false'
              .remove("stripweb.search.byIsbn.prefer.10")
              .remove("tmp.edit.book.tab.authSer")
              .remove("ui.messages.use")

              // Editing the URL for these sites has been removed.
              .remove("isfdb.host.url")
              .remove("librarything.host.url")

              .apply();

        // replaced by a database table in db36
        context.deleteSharedPreferences("language2iso3");
    }

    static void addIdentifiersIfNotYetDone(@NonNull final Context context,
                                           @NonNull final SQLiteDatabase db) {

        addIdentifier(context, db, new Identifier(
                Identifier.SID_DATABAZE_KNIH,
                Identifier.TYPE_LONG,
                context.getString(R.string.identifier_databaze_knih),
                "P10387",
                DatabazeKnihSearchEngine.SITE_URL,
                DatabazeKnihSearchEngine.BOOK_URL,
                DatabazeKnihSearchEngine.AUTHOR_URL));
        addIdentifier(context, db, new Identifier(
                Identifier.SID_ISNI,
                Identifier.TYPE_STRING,
                context.getString(R.string.identifier_isni),
                "P213",
                ISNI.SITE_URL,
                null,
                ISNI.AUTHOR_URL));
        addIdentifier(context, db, new Identifier(
                Identifier.SID_STORYGRAPH,
                Identifier.TYPE_STRING,
                context.getString(R.string.identifier_storygraph),
                "P12430",
                StoryGraph.SITE_URL,
                StoryGraph.BOOK_URL,
                StoryGraph.AUTHOR_URL));
        addIdentifier(context, db, new Identifier(
                Identifier.SID_URN,
                Identifier.TYPE_STRING,
                context.getString(R.string.identifier_urn),
                null,
                null,
                null,
                null));
        addIdentifier(context, db, new Identifier(
                Identifier.SID_VIAF,
                Identifier.TYPE_LONG,
                context.getString(R.string.identifier_viaf),
                "P214",
                VIAF.SITE_URL,
                null,
                VIAF.AUTHOR_URL));
    }

    private static void addIdentifier(@NonNull final Context context,
                                      @NonNull final SQLiteDatabase db,
                                      @NonNull final Identifier identifier) {

        // key must be unique
        boolean found = false;
        try (SQLiteStatement stmt = db.compileStatement(
                "SELECT 1 FROM " + TBL_IDENTIFIERS.getName()
                + " WHERE " + DBKey.IDENTIFIERS.KEY + "=?")) {
            stmt.bindString(1, identifier.getKey());
            found = 1 == stmt.simpleQueryForLong();
        } catch (@NonNull final SQLiteDoneException ignore) {
            // ignore
        }
        if (found) {
            // The identifier is already present
            return;
        }

        try (SQLiteStatement stmt = db.compileStatement(
                "INSERT INTO " + TBL_IDENTIFIERS.getName()
                + '(' + DBKey.IDENTIFIERS.KEY
                + ',' + DBKey.IDENTIFIERS.TYPE
                + ',' + DBKey.IDENTIFIERS.NAME
                // Added in db42
                + ',' + DBKey.IDENTIFIERS.WIKIDATA_CLAIM_AUTHOR_ID
                + ',' + DBKey.IDENTIFIERS.SITE_URL
                + ',' + DBKey.IDENTIFIERS.BOOK_URI
                + ',' + DBKey.IDENTIFIERS.AUTHOR_URI
                + ") VALUES(?,?,?,?,?,?,?)")) {
            stmt.bindString(1, identifier.getKey().toLowerCase(Locale.ENGLISH));
            stmt.bindString(2, String.valueOf(identifier.getType()));
            stmt.bindString(3, identifier.getName());

            final String wdc = identifier.getWikidataClaimAuthorId().orElse(null);
            if (wdc == null) {
                stmt.bindNull(4);
            } else {
                stmt.bindString(4, wdc);
            }
            final String siteUrl = identifier.getSiteUrl(context);
            if (siteUrl == null) {
                stmt.bindNull(5);
            } else {
                stmt.bindString(5, siteUrl);
            }
            final String bookUrl = identifier.getBookUri(context).orElse(null);
            if (bookUrl == null) {
                stmt.bindNull(6);
            } else {
                stmt.bindString(6, bookUrl);
            }
            final String authorUrl = identifier.getAuthorUri(context).orElse(null);
            if (authorUrl == null) {
                stmt.bindNull(7);
            } else {
                stmt.bindString(7, authorUrl);
            }
            stmt.executeInsert();
        }
    }
}

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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;

public final class PreferenceKeyMigration {

    private static final String PK_FIELDS_VISIBILITY_KEYS = "fields.visibility.";

    private PreferenceKeyMigration() {
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
    public static void migrate(@NonNull final Context context) {
        // replaced by a database table in db36
        context.deleteSharedPreferences("language2iso3");

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();

        // This will take care of old keys in general, but will
        // ALSO copy the FieldVisibility.PK_LOANS which is still in use.
        migrateGlobalFieldVisibility(prefs);

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
              .remove("goodreads.AccessToken.Secret")
              .remove("goodreads.AccessToken.Token")
              .remove("goodreads.enabled")
              .remove("goodreads.search.collect.genre")
              .remove("goodreads.showMenu")
              .remove("image.cropper.frame.whole")
              .remove("isfdb.search.uses.publisher")
              .remove("librarything.dev_key")
              .remove("scanner.preferred")
              .remove("search.byIsbn.strict")
              .remove("search.form.advanced")
              .remove("search.site.goodreads.covers.enabled")
              .remove("search.site.goodreads.data.enabled")
              .remove("show.author.name.given_first")
              .remove("show.title.reordered")
              .remove("sort.author.name.given_first")
              .remove("startup.lastVersion")
              .remove("stripweb.search.byIsbn.prefer.10")
              .remove("tmp.edit.book.tab.authSer")
              .remove("ui.messages.use")

              // Editing the URL for these sites has been removed.
              .remove("isfdb.host.url")
              .remove("librarything.host.url")

              .apply();
    }

    /**
     * Check and migrate pre-db25 global field visibility keys.
     *
     * @param prefs to migrate
     */
    private static void migrateGlobalFieldVisibility(@NonNull final SharedPreferences prefs) {

        final List<String> oldVisKeys = prefs
                .getAll()
                .keySet()
                .stream()
                .filter(key -> key.startsWith(PK_FIELDS_VISIBILITY_KEYS))
                .collect(Collectors.toList());

        if (!oldVisKeys.isEmpty()) {
            final FieldVisibility fieldVisibility = ServiceLocator.getInstance()
                                                                  .getGlobalFieldVisibility();
            final Pattern dot = Pattern.compile("\\.");
            oldVisKeys.forEach(oldKey -> {
                final boolean value = prefs.getBoolean(oldKey, false);
                final String dbKey = dot.split(oldKey, 3)[2];
                fieldVisibility.setVisible(dbKey, value);
            });

            fieldVisibility.save();
        }
    }
}

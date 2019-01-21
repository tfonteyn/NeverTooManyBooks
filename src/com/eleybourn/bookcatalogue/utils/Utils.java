/*
 * @copyright 2011 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Left over utilities.
 */
public final class Utils {

    private Utils() {
    }

    /**
     * Passed a list of Objects, remove duplicates based on the toString result.
     * (case insensitive + trimmed)
     * <p>
     * ENHANCE: Add author_aliases table to allow further pruning
     * (eg. Joe Haldeman == Joe W Haldeman).
     * ENHANCE: Add series_aliases table to allow further pruning
     * (eg. 'Amber Series' <==> 'Amber').
     *
     * @param db   Database connection to lookup IDs
     * @param list List to clean up
     *
     * @return <tt>true</tt> if the list was modified.
     */
    public static <T extends ItemWithIdFixup> boolean pruneList(@NonNull final DBA db,
                                                                @NonNull final List<T> list) {
        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        // will be set to true if we deleted items.
        boolean didDeletes = false;

        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            T item = it.next();
            Long id = item.fixupId(db);
            String name = item.toString().trim().toUpperCase();

            // Series special case - same name different series number.
            // This means different series positions will have the same ID but will have
            // different names; so ItemWithIdFixup contains the 'isUniqueById()' method.
            if (ids.contains(id) && !names.contains(name) && !item.isUniqueById()) {
                ids.add(id);
                names.add(name);
            } else if (names.contains(name) || (id != 0 && ids.contains(id))) {
                it.remove();
                didDeletes = true;
            } else {
                ids.add(id);
                names.add(name);
            }
        }

        return didDeletes;
    }

    /**
     * Only does web & email links. Most likely all we'll ever need.
     *
     * @param html Partial HTML
     *
     * @return Spannable with all links
     *
     * @see #linkifyHtml(String, int)
     */
    @NonNull
    public static Spannable linkifyHtml(@NonNull final String html) {
        return linkifyHtml(html, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
    }

    /**
     * Linkify partial HTML. Linkify methods remove all spans before building links, this
     * method preserves them.
     * <p>
     * See:
     * http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml
     *
     * @param html        Partial HTML
     * @param linkifyMask Linkify mask to use in Linkify.addLinks
     *
     * @return Spannable with all links
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static Spannable linkifyHtml(@NonNull final String html,
                                        final int linkifyMask) {
        // Get the spannable HTML
        Spanned text = Html.fromHtml(html);
        // Save the span details for later restoration
        URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

        // Build an empty spannable then add the links
        SpannableString buffer = new SpannableString(text);
        Linkify.addLinks(buffer, linkifyMask);

        // Add back the HTML spannable's
        for (URLSpan span : currentSpans) {
            int end = text.getSpanEnd(span);
            int start = text.getSpanStart(span);
            buffer.setSpan(span, start, end, 0);
        }
        return buffer;
    }

    /**
     * Format a number of bytes in a human readable form.
     */
    @NonNull
    public static String formatFileSize(final float space) {
        if (space < 3072) {
            // Show 'bytes' if < 3k
            return String.format(BookCatalogueApp.getResString(R.string.bytes),
                                 space);
        } else if (space < 250 * 1024) {
            // Show Kb if less than 250kB
            return String.format(BookCatalogueApp.getResString(R.string.kilobytes),
                                 space / 1024);
        } else {
            // Show MB otherwise...
            return String.format(BookCatalogueApp.getResString(R.string.megabytes),
                                 space / (1024 * 1024));
        }
    }

    public interface ItemWithIdFixup {

        long fixupId(@NonNull final DBA db);

        boolean isUniqueById();
    }

}

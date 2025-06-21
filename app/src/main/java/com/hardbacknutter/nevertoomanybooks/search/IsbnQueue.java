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

package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Most methods will need external synchronization.
 */
class IsbnQueue {

    private static final String TAG = "IsbnQueue";

    /** Storage key into preferences for the current queue. */
    private static final String PK_SCAN_QUEUE = "scan.queue";

    /** File reader buffer. */
    private static final int BUFFER_SIZE = 65535;
    private static final String CSV = ",";

    @SuppressWarnings("TypeMayBeWeakened")
    private final Queue<Item> q = new ConcurrentLinkedQueue<>();

    /**
     * Read the previously stored list of items from the preferences.
     *
     * @param context current context
     *
     * @return list
     */
    @NonNull
    static List<Item> readFromPreferences(@NonNull final Context context) {
        final String[] isbnList = PreferenceManager.getDefaultSharedPreferences(context)
                                                   .getString(PK_SCAN_QUEUE, "")
                                                   .split(CSV);
        if (isbnList.length > 0) {
            return readFromStream(context, Arrays.stream(isbnList));
        }
        return List.of();
    }

    static void clearPreferences(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().remove(PK_SCAN_QUEUE).apply();
    }

    /**
     * Import a list of ISBN numbers from a text file.
     * <p>
     * Format supported:  one or more (CSV) ISBN number on each line of the text file.
     * Whitespace and '-' are taken care of as usual, any other text will either
     * cause the line to be skipped, or the import to fail completely.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @return list
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    static List<Item> readFromFile(@NonNull final Context context,
                                   @NonNull final Uri uri)
            throws IOException {
        //TODO: should be run as background task, and use LiveData to update the view...
        // ... but it's so fast for any reasonable length list....
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                try (Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE)) {
                    return readFromStream(context, reader.lines());

                } catch (@NonNull final UncheckedIOException ue) {
                    //noinspection DataFlowIssue
                    throw ue.getCause();
                }
            }
        }
        return List.of();
    }

    @NonNull
    private static List<Item> readFromStream(@NonNull final Context context,
                                             @NonNull final Stream<String> stream) {
        final boolean strictIsbn = BookSearchCriteria.isStrictIsbn(context);
        return stream
                // allow multiple csv
                .map(line -> line.split(CSV))
                .flatMap(Arrays::stream)
                // no duplicates
                .distinct()
                // must not be blank
                .filter(s -> !s.isBlank())
                // valid codes only
                .map(s -> new ISBN(s, strictIsbn))
                .filter(isbn -> isbn.isValid(strictIsbn))
                .map(Item::new)
                .collect(Collectors.toList());
    }

    @NonNull
    Iterator<Item> iterator() {
        return q.iterator();
    }

    /**
     * Check/get the item for the given search-id.
     *
     * @param searchId to check/get
     *
     * @return item
     */
    @NonNull
    Optional<Item> bySearchId(final int searchId) {
        return q.stream()
                .filter(item -> item.getSearchId() == searchId)
                .findAny();
    }

    /**
     * Check if the <strong>ISBN</strong> of the given item is already present.
     *
     * @param item to check
     *
     * @return {@code true} if already present
     */
    boolean containsIsbn(@NonNull final Item item) {
        return q.stream().anyMatch(qi -> qi.isbn.equals(item.isbn));
    }

    /**
     * Unconditionally add the given item.
     * <p>
     * Use {@link #containsIsbn(Item)} <strong>before</strong> calling this method as needed.
     *
     * @param context Current context
     * @param item    to add
     */
    void add(@NonNull final Context context,
             @NonNull final Item item) {
        q.add(item);
        writeToPreferences(context);
    }

    /**
     * Remove the given item.
     *
     * @param context Current context
     * @param item    to remove
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean remove(@NonNull final Context context,
                   @NonNull final Item item) {
        final boolean removed = q.remove(item);
        if (removed) {
            writeToPreferences(context);
        }
        return removed;
    }

    /**
     * Clear the queue.
     *
     * @param context Current context
     */
    void clear(@NonNull final Context context) {
        q.clear();
        clearPreferences(context);
    }

    /**
     * Check if there is any active search ongoing.
     *
     * @return flag
     */
    boolean isSearching() {
        return q.stream().anyMatch(Item::isSearching);
    }

    /**
     * Write the current queue as a csv list of ISBN numbers to preferences.
     *
     * @param context Current context
     */
    private void writeToPreferences(@NonNull final Context context) {
        final String list = q.stream()
                             .map(Item::getIsbn)
                             .map(ISBN::asText)
                             .collect(Collectors.joining(CSV));
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putString(PK_SCAN_QUEUE, list).apply();
    }

    static class Item {
        @NonNull
        private final ISBN isbn;
        /** Set when the search is started. */
        private int searchId;
        /** Set whn the result is in. */
        @Nullable
        private BookSearchResult result;

        Item(@NonNull final ISBN isbn) {
            this.isbn = isbn;
        }

        @NonNull
        ISBN getIsbn() {
            return isbn;
        }

        /**
         * Is there an active search for this item.
         *
         * @return flag
         */
        boolean isSearching() {
            return searchId > 0 && result == null;
        }

        int getSearchId() {
            return searchId;
        }

        void setSearchId(final int searchId) {
            this.searchId = searchId;
        }

        @Nullable
        BookSearchResult getResult() {
            return result;
        }

        void setResult(@NonNull final BookSearchResult result) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "result=" + result);
            }
            this.result = result;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Item item = (Item) o;
            return searchId == item.searchId
                   && Objects.equals(isbn, item.isbn)
                   && Objects.equals(result, item.result);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isbn, searchId, result);
        }

        @Override
        @NonNull
        public String toString() {
            return "Entry{"
                   + "isbn=" + isbn
                   + ", searchId=" + searchId
                   + ", result=" + result
                   + '}';
        }
    }
}

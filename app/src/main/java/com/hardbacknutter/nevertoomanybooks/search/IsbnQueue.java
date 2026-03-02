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

package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

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
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;

/**
 * Most methods will need external synchronization.
 */
class IsbnQueue {

    /** Storage key into preferences for the current queue. */
    private static final String PK_SCAN_QUEUE = "scan.queue";

    /** File reader buffer. */
    private static final int BUFFER_SIZE = 65535;
    private static final String CSV = ",";

    @SuppressWarnings("TypeMayBeWeakened")
    private final Queue<QueuedItem<ISBN>> q = new ConcurrentLinkedQueue<>();

    /**
     * Read the previously stored list of items from the preferences.
     *
     * @return list
     */
    @NonNull
    static List<QueuedItem<ISBN>> readFromPreferences() {
        final String[] isbnList = ServiceLocator.getInstance().getSharedPreferences()
                                                .getString(PK_SCAN_QUEUE, "")
                                                .split(CSV);
        if (isbnList.length > 0) {
            return readFromStream(Arrays.stream(isbnList));
        }
        return List.of();
    }

    static void clearPreferences() {
        ServiceLocator.getInstance().getSharedPreferences().edit().remove(PK_SCAN_QUEUE).apply();
    }

    /**
     * Import a list of ISBNs from a text file.
     * <p>
     * Format supported:  one or more (CSV) ISBN on each line of the text file.
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
    static List<QueuedItem<ISBN>> readFromFile(@NonNull final Context context,
                                         @NonNull final Uri uri)
            throws IOException {
        //TODO: should be run as background task, and use LiveData to update the view...
        // ... but it's so fast for any reasonable length list....
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                try (Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE)) {
                    return readFromStream(reader.lines());

                } catch (@NonNull final UncheckedIOException ue) {
                    //noinspection DataFlowIssue
                    throw ue.getCause();
                }
            }
        }
        return List.of();
    }

    @NonNull
    private static List<QueuedItem<ISBN>> readFromStream(@NonNull final Stream<String> stream) {
        final boolean strictIsbn = BookSearchCriteria.isStrictIsbnGlobal();
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
                .filter(ISBN::isValid)
                .map(QueuedItem::new)
                .collect(Collectors.toList());
    }

    @NonNull
    Iterator<QueuedItem<ISBN>> iterator() {
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
    Optional<QueuedItem<ISBN>> bySearchId(final int searchId) {
        return q.stream()
                .filter(item -> item.getSearchId() == searchId)
                .findAny();
    }

    /**
     * Check if the given ISBN is already in the queue.
     *
     * @param isbn to find
     *
     * @return {@code true} if already present
     */
    boolean contains(@NonNull final ISBN isbn) {
        return q.stream().anyMatch(qi -> qi.getCode().equals(isbn));
    }

    /**
     * Unconditionally add the given item.
     * <p>
     * Use {@link #contains(ISBN)} <strong>before</strong> calling this method as needed.
     *
     * @param item to add
     */
    void add(@NonNull final QueuedItem<ISBN> item) {
        q.add(item);
        writeToPreferences();
    }

    /**
     * Remove the given item.
     *
     * @param item to remove
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean remove(@NonNull final QueuedItem<ISBN> item) {
        final boolean removed = q.remove(item);
        if (removed) {
            writeToPreferences();
        }
        return removed;
    }

    /**
     * Clear the queue.
     *
     */
    void clear() {
        q.clear();
        clearPreferences();
    }

    /**
     * Check if there is any active search ongoing.
     *
     * @return flag
     */
    boolean isSearching() {
        return q.stream().anyMatch(QueuedItem::isSearching);
    }

    /**
     * Write the current queue as a csv list of ISBNs to preferences.
     *
     */
    private void writeToPreferences() {
        final String list = q.stream()
                             .map(QueuedItem::getCode)
                             .map(ISBN::asText)
                             .collect(Collectors.joining(CSV));
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit().putString(PK_SCAN_QUEUE, list).apply();
    }

}

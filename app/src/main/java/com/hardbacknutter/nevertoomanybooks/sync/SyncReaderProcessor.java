/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Handles importing data with each field controlled by a {@link SyncAction}.
 */
public final class SyncReaderProcessor {

    private static final String TAG = "SyncProcessor";

    @NonNull
    private final Map<String, SyncField> fields;

    private SyncReaderProcessor(@NonNull final Map<String, SyncField> fields) {
        this.fields = fields;
    }

    /**
     * Filter the fields we want versus the fields we actually need for the given book data.
     * <p>
     * This method is normally called <strong>before</strong> a website is contacted,
     * as (theoretically) it allows the code to download more or less data depending on
     * the fields wanted. Prime example is of course the cover images.
     *
     * @param localBook to filter
     *
     * @return the filtered SyncField unmodifiableMap
     */
    @NonNull
    public Map<String, SyncField> filter(@NonNull final Book localBook) {

        final Map<String, SyncField> filteredMap = new LinkedHashMap<>();

        for (final SyncField field : fields.values()) {
            switch (field.getAction()) {
                case Skip:
                    // duh...
                    break;

                case Append:
                case Overwrite: {
                    // Append + Overwrite: we always need to get the data
                    filteredMap.put(field.getKey(), field);
                    break;
                }
                case CopyIfBlank: {
                    switch (field.getKey()) {
                        // We should never have a book without authors, but be paranoid
                        case Book.BKEY_AUTHOR_LIST:
                        case Book.BKEY_SERIES_LIST:
                        case Book.BKEY_PUBLISHER_LIST:
                        case Book.BKEY_TOC_LIST:
                        case Book.BKEY_BOOKSHELF_LIST:
                            if (localBook.contains(field.getKey())) {
                                final List<Parcelable> list =
                                        localBook.getParcelableArrayList(field.getKey());
                                if (list.isEmpty()) {
                                    filteredMap.put(field.getKey(), field);
                                }
                            }
                            break;

                        default:
                            // If it's a cover...
                            if (Book.BKEY_TMP_FILE_SPEC[0].equals(field.getKey())) {
                                final String uuid = localBook.getString(DBKey.BOOK_UUID);
                                // check if it's missing or empty.
                                final Optional<File> file = ServiceLocator
                                        .getInstance().getCoverStorage()
                                        .getPersistedFile(uuid, 0);
                                if (file.isEmpty()) {
                                    filteredMap.put(field.getKey(), field);
                                }

                            } else if (Book.BKEY_TMP_FILE_SPEC[1].equals(field.getKey())) {
                                final String uuid = localBook.getString(DBKey.BOOK_UUID);
                                // check if it's missing or empty.
                                final Optional<File> file = ServiceLocator
                                        .getInstance().getCoverStorage()
                                        .getPersistedFile(uuid, 1);
                                if (file.isEmpty()) {
                                    filteredMap.put(field.getKey(), field);
                                }

                            } else {
                                // If the original was blank/zero, add to list
                                final String value = localBook.getString(field.getKey(), null);
                                if (value == null || value.isEmpty() || "0".equals(value)) {
                                    filteredMap.put(field.getKey(), field);
                                }
                            }
                            break;
                    }
                }
            }
        }

        return Collections.unmodifiableMap(filteredMap);
    }

    /**
     * Process the search-result data for one book.
     * <p>
     * This method should be called <strong>after</strong> we got the info/covers from the website.
     * <p>
     * Exceptions related to storing cover files are ignored.
     *
     * @param context          Current context
     * @param bookId           to use for updating the database.
     *                         Must be passed separately, as 'book' can be all-new data.
     * @param localBook        the local book
     * @param fieldsWanted     The (subset) of fields relevant to the current book.
     * @param remoteBook       the data to merge with the local-book
     * @param realNumberParser to use for number parsing
     *
     * @return a {@link Book} object with the <strong>DELTA</strong> fields that we need.
     *         The book id will always be set.
     *         It can be passed to {@link BookDao#update}
     *
     * @throws IOException on <strong>very serious</strong> io issues.
     *                     Less serious io issues are swallowed/ignored
     */
    @Nullable
    public Book process(@NonNull final Context context,
                        final long bookId,
                        @NonNull final Book localBook,
                        @NonNull final Map<String, SyncField> fieldsWanted,
                        @NonNull final Book remoteBook,
                        @NonNull final RealNumberParser realNumberParser)
            throws IOException {

        // Filter the data to remove keys we don't care about
        final Collection<String> toRemove = new ArrayList<>();
        for (final String key : remoteBook.keySet()) {
            final SyncField field = fieldsWanted.get(key);
            if (field == null || field.getAction() == SyncAction.Skip) {
                toRemove.add(key);
            }
        }
        for (final String key : toRemove) {
            remoteBook.remove(key);
        }

        try {
            // For each field, process it according the SyncAction set.
            fieldsWanted
                    .values()
                    .stream()
                    .filter(field -> remoteBook.contains(field.getKey()))
                    .forEach(field -> {
                        try {
                            // Handle thumbnail specially
                            if (Book.BKEY_TMP_FILE_SPEC[0].equals(field.getKey())) {
                                processCover(localBook, remoteBook, 0);
                            } else if (Book.BKEY_TMP_FILE_SPEC[1].equals(field.getKey())) {
                                processCover(localBook, remoteBook, 1);
                            } else {
                                switch (field.getAction()) {
                                    case CopyIfBlank:
                                        // remove unneeded fields from the new data
                                        if (hasField(localBook, field.getKey(), realNumberParser)) {
                                            remoteBook.remove(field.getKey());
                                        }
                                        break;

                                    case Append:
                                        processList(context, localBook, remoteBook, field.getKey());
                                        break;

                                    case Overwrite:
                                    case Skip:
                                        break;
                                }
                            }
                        } catch (@NonNull final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (@NonNull final UncheckedIOException e) {
            //noinspection DataFlowIssue
            throw e.getCause();
        }

        // Commit the new data
        if (!remoteBook.isEmpty()) {
            // Get the language, if there was one requested for updating.
            String bookLang = remoteBook.getString(DBKey.LANGUAGE, null);
            if (bookLang == null || bookLang.isEmpty()) {
                // Otherwise add the original one.
                bookLang = localBook.getString(DBKey.LANGUAGE, null);
                if (bookLang != null && !bookLang.isEmpty()) {
                    remoteBook.putString(DBKey.LANGUAGE, bookLang);
                }
            }

            //IMPORTANT: note how we construct a NEW BOOK, with the DELTA-data which
            // we want to commit to the existing book.
            final Book delta = Book.from(context, remoteBook);
            delta.putLong(DBKey.PK_ID, bookId);
            return delta;
        }

        return null;
    }

    /**
     * Check if we already have this field (with content) in the original data.
     *
     * @param localBook        to check
     * @param key              to test for
     * @param realNumberParser to use for number parsing
     *
     * @return {@code true} if already present
     */
    private boolean hasField(@NonNull final Book localBook,
                             @NonNull final String key,
                             @NonNull final RealNumberParser realNumberParser) {
        switch (key) {
            case Book.BKEY_AUTHOR_LIST:
            case Book.BKEY_SERIES_LIST:
            case Book.BKEY_PUBLISHER_LIST:
            case Book.BKEY_TOC_LIST:
            case Book.BKEY_BOOKSHELF_LIST:
                if (localBook.contains(key)) {
                    return !localBook.getParcelableArrayList(key).isEmpty();
                }
                break;

            default:
                final Object o = localBook.get(key, realNumberParser);
                if (o != null) {
                    final String value = o.toString().trim();
                    return !value.isEmpty() && !"0".equals(value);
                }
                break;
        }

        return false;
    }

    private void processCover(@NonNull final Book localBook,
                              @NonNull final Book remoteBook,
                              @IntRange(from = 0, to = 1) final int cIdx)
            throws IOException {

        final String fileSpec = remoteBook.getString(Book.BKEY_TMP_FILE_SPEC[cIdx], null);
        if (fileSpec != null && !fileSpec.isEmpty()) {
            //noinspection OverlyBroadCatchBlock
            try {
                final String uuid = localBook.getString(DBKey.BOOK_UUID);
                ServiceLocator.getInstance().getCoverStorage()
                              .persist(new File(fileSpec), uuid, cIdx);

            } catch (@NonNull final StorageException | IOException e) {
                // We're called in a loop, and the chance of an exception here is very low
                // so let's log it, and quietly continue.
                LoggerFactory.getLogger()
                             .e(TAG, e, "processCoverImage|uuid="
                                        + localBook.getString(DBKey.BOOK_UUID, null)
                                        + "|cIdx=" + cIdx);
                // except disk-full!
                if (FileUtils.isDiskFull(e)) {
                    //noinspection DataFlowIssue
                    throw (IOException) e;
                }
            }
        }
        remoteBook.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
    }

    /**
     * Combines two ParcelableArrayList's. The result in 'dataToMerge' MAY contain duplicates.
     * These will be
     *
     * @param context    Current context
     * @param localeBook to check; will NOT be modified.
     * @param remoteBook the data to merge with the book;
     *                   after returning, this will contain the new data AND the data we merged
     *                   from the #book
     * @param key        into the incoming data
     *
     * @throws IllegalArgumentException if the key is not an appendable type
     */
    private void processList(@NonNull final Context context,
                             @NonNull final Book localeBook,
                             @NonNull final Book remoteBook,
                             @NonNull final String key) {
        switch (key) {
            case Book.BKEY_AUTHOR_LIST: {
                final List<Author> list = remoteBook.getAuthors();
                if (!list.isEmpty()) {
                    // add the book data to the remoteBook list!
                    // (and not the other way around! We want to collect a delta)
                    list.addAll(localeBook.getAuthors());
                }
                break;
            }
            case Book.BKEY_SERIES_LIST: {
                final List<Series> list = remoteBook.getSeries();
                if (!list.isEmpty()) {
                    list.addAll(localeBook.getSeries());
                }
                break;
            }
            case Book.BKEY_PUBLISHER_LIST: {
                final List<Publisher> list = remoteBook.getPublishers();
                if (!list.isEmpty()) {
                    list.addAll(localeBook.getPublishers());
                }
                break;
            }
            case Book.BKEY_TOC_LIST: {
                final List<TocEntry> list = remoteBook.getToc();
                if (!list.isEmpty()) {
                    list.addAll(localeBook.getToc());
                }
                break;
            }
            case Book.BKEY_BOOKSHELF_LIST: {
                final List<Bookshelf> list = remoteBook.getBookshelves();
                if (!list.isEmpty()) {
                    list.addAll(localeBook.getBookshelves());
                    ServiceLocator.getInstance().getBookshelfDao().pruneList(context, list);
                }
                break;
            }
            default:
                // We currently don't 'append' String fields
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncProcessor{"
               + "fields=" + fields
               + '}';
    }

    public static class Builder {

        @NonNull
        private final String preferencePrefix;
        @NonNull
        private final SharedPreferences prefs;

        private final Map<String, SyncField> fields = new LinkedHashMap<>();
        private final Map<String, String> relatedFields = new LinkedHashMap<>();

        /**
         * Constructor.
         *
         * @param context          Current context
         * @param preferencePrefix for the site/fields
         */
        public Builder(@NonNull final Context context,
                       @NonNull final String preferencePrefix) {
            this.preferencePrefix = preferencePrefix;
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        /**
         * Write current settings to the user preferences.
         */
        public void writePreferences() {

            final SharedPreferences.Editor ed = prefs.edit();
            for (final SyncField syncField : fields.values()) {
                syncField.getAction().write(ed, preferencePrefix + syncField.getKey());
            }
            ed.apply();
        }

        /**
         * Reset current action back to defaults, and write to preferences.
         */
        public void resetPreferences() {

            for (final SyncField syncField : fields.values()) {
                syncField.setDefaultAction();
            }
            writePreferences();
        }

        @NonNull
        public Collection<SyncField> getSyncFields() {
            return fields.values();
        }

        /**
         * Get the {@link SyncAction} for the given key.
         *
         * @param key field to get
         *
         * @return syncAction, or {@code null} if not found
         */
        @Nullable
        public SyncAction getSyncAction(@NonNull final String key) {
            final SyncField syncField = fields.get(key);
            if (syncField != null) {
                return syncField.getAction();
            }
            return null;
        }

        /**
         * Update the {@link SyncAction} for the given key.
         * Does nothing if the field was not actually added before.
         *
         * @param key        field to update
         * @param syncAction to set
         */
        public void setSyncAction(@NonNull final String key,
                                  @NonNull final SyncAction syncAction) {
            final SyncField syncField = fields.get(key);
            if (syncField != null) {
                syncField.setAction(syncAction);
            }
        }

        /**
         * Update the {@link SyncAction} for all keys.
         *
         * @param syncAction to set
         */
        public void setSyncAction(@NonNull final SyncAction syncAction) {
            fields.forEach((key, value) -> value.setAction(syncAction));
        }

        /**
         * Convenience method wrapper for {@link #add(Context, String, String, SyncAction)}.
         * The default SyncAction is always {@link SyncAction#CopyIfBlank}.
         *
         * @param context Current context
         * @param label   Field label
         * @param keys    {Field key} OR {Preference key, Field key}
         *
         * @throws IllegalArgumentException if there are more then 2 keys
         */
        public void add(@NonNull final Context context,
                        @NonNull final String label,
                        @NonNull final String[] keys) {
            switch (keys.length) {
                case 1:
                    add(context, label, keys[0], SyncAction.CopyIfBlank);
                    return;

                case 2:
                    addList(context, label, keys[0], keys[1]);
                    return;
                default:
                    throw new IllegalArgumentException("To many keys: " + Arrays.toString(keys));
            }
        }

        /**
         * Add a {@link SyncField} for a <strong>simple</strong> field
         * if it has not been hidden by the user.
         *
         * @param context       Current context
         * @param label         Field label
         * @param key           Field key
         * @param defaultAction default Usage for this field
         */
        public void add(@NonNull final Context context,
                        @NonNull final String label,
                        @NonNull final String key,
                        @NonNull final SyncAction defaultAction) {

            if (ServiceLocator.getInstance().isFieldEnabled(key)) {
                final SyncAction action = SyncAction
                        .read(prefs, preferencePrefix + key, defaultAction);
                fields.put(key, new SyncField(key, label, false,
                                              defaultAction, action));
            }
        }

        /**
         * Add a {@link SyncField} for a <strong>list</strong> field
         * if it has not been hidden by the user.
         * <p>
         * The default SyncAction is always {@link SyncAction#Append}.
         *
         * @param context Current context
         * @param label   Field label
         * @param prefKey Field name to use for preferences.
         * @param key     Field key
         */
        private void addList(@NonNull final Context context,
                             @NonNull final String label,
                             @NonNull final String prefKey,
                             @NonNull final String key) {

            if (ServiceLocator.getInstance().isFieldEnabled(prefKey)) {
                final SyncAction action = SyncAction
                        .read(prefs, preferencePrefix + key, SyncAction.Append);
                fields.put(key, new SyncField(key, label, true,
                                              SyncAction.Append, action));
            }
        }

        /**
         * Add any related fields with the same setting.
         *
         * @param key        the field to check
         * @param relatedKey to add if the primary field is present
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder addRelatedField(@NonNull final String key,
                                       @NonNull final String relatedKey) {
            // Don't check on key being present in the fields list. We'll do that at usage time.
            //This allows out-of-order adding.
            relatedFields.put(key, relatedKey);
            return this;
        }

        /**
         * Add the supported external-id fields.
         * The label is the search engine name, the value is the external id field name
         *
         * @param context Current context
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder addSidFields(@NonNull final Context context) {
            final SortedMap<String, String> sidMap = new TreeMap<>();
            SearchEngineConfig.getAll().forEach(seConfig -> {
                final Domain domain = seConfig.getExternalIdDomain();
                if (domain != null) {
                    sidMap.put(seConfig.getEngineId().getName(context), domain.getName());
                }
            });
            sidMap.forEach((label, key) -> add(context, label, key, SyncAction.Overwrite));

            return this;
        }

        /**
         * Build the processor.
         *
         * @return new instance
         */
        @NonNull
        public SyncReaderProcessor build() {
            relatedFields.forEach((key, relatedKey) -> {
                final SyncField syncField = fields.get(key);
                if (syncField != null && syncField.getAction() != SyncAction.Skip) {
                    fields.put(relatedKey, syncField.createRelatedField(relatedKey));
                }
            });
            return new SyncReaderProcessor(fields);
        }
    }
}

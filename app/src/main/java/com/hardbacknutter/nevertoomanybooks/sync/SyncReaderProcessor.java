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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.Mapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.MapperFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Handles importing data with each field controlled by a {@link SyncAction}.
 */
public class SyncReaderProcessor {

    private static final String TAG = "SyncProcessor";

    @NonNull
    private final Map<String, SyncField> fields;

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final RealNumberParser realNumberParser;
    /** Mappers to apply. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Collection<Mapper> mappers;

    @AnyThread
    protected SyncReaderProcessor(@NonNull final Context context,
                                  @NonNull final Builder builder) {
        this.fields = builder.fields;
        this.realNumberParser = builder.realNumberParser;

        mappers = MapperFactory.create(context);
    }

    @SuppressWarnings("TypeMayBeWeakened")
    private static boolean isEmptyOrZero(@NonNull final String value) {
        return value.isEmpty() || "0".equals(value) || "0.0".equals(value);
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
    public final Map<String, SyncField> filter(@NonNull final Book localBook) {

        final Map<String, SyncField> filteredMap = new LinkedHashMap<>();

        for (final SyncField field : fields.values()) {
            final FilterResult result = filter(field, localBook);
            if (result == FilterResult.Add) {
                filteredMap.put(field.getKey(), field);

            } else if (result == FilterResult.ApplyDefault) {
                if (doDefaultFiltering(localBook, field)) {
                    filteredMap.put(field.getKey(), field);
                }
            }
        }

        return Collections.unmodifiableMap(filteredMap);
    }

    /**
     * Overridable for custom filtering.
     *
     * @param field     to handle
     * @param localBook to handle
     *
     * @return {@link FilterResult}
     */
    @NonNull
    protected FilterResult filter(@NonNull final SyncField field,
                                  @NonNull final Book localBook) {
        return FilterResult.ApplyDefault;
    }

    /**
     * Apply the default filtering specific to the given field.
     *
     * @param localBook to filter
     * @param field     to process
     *
     * @return {@code true} if the field needs to be added, otherwise skip it.
     */
    private boolean doDefaultFiltering(@NonNull final Book localBook,
                                       @NonNull final SyncField field) {
        switch (field.getAction()) {
            case Append:
            case Overwrite: {
                // Append + Overwrite: we always need to get the data
                return true;
            }
            case CopyIfBlank: {
                if (field.getType() == SyncField.Type.LIST) {
                    // If the local data is absent or empty, add the field
                    return !localBook.contains(field.getKey())
                           || localBook.getParcelableArrayList(field.getKey()).isEmpty();
                }

                for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
                    if (Book.BKEY_TMP_FILE_SPEC[cIdx].equals(field.getKey())) {
                        // check if we have a valid image
                        return ServiceLocator.getInstance().getCoverStorage()
                                             .getPersistedFile(localBook.getUuid(), cIdx)
                                             .isEmpty();
                    }
                }

                // If the local data is blank or numerical zero, add the field
                final String value = localBook.getString(field.getKey(), null);
                return value == null || isEmptyOrZero(value);

            }
            case Skip:
            default:
                // duh...
                return false;
        }
    }

    /**
     * Process the search-result data for one book.
     * <p>
     * This method should be called <strong>after</strong> we got the info/covers from the website.
     * <p>
     * Exceptions related to storing cover files are ignored.
     *
     * @param context      Current context
     * @param bookId       to use for updating the database.
     *                     Must be passed separately, as 'book' can be all-new data.
     * @param localBook    the local book
     * @param remoteBook   the data to merge with the local-book
     * @param fieldsWanted The (subset) of fields relevant to the current book.
     *
     * @return a {@link Book} object with the <strong>DELTA</strong> fields that we need.
     *         The book id will always be set.
     *         It can be passed to {@link BookRepository#update}
     *
     * @throws IOException on <strong>very serious</strong> io issues.
     *                     Less serious io issues are swallowed/ignored
     */
    @WorkerThread
    @Nullable
    public final Book process(@NonNull final Context context,
                              final long bookId,
                              @NonNull final Book localBook,
                              @NonNull final Book remoteBook,
                              @NonNull final Map<String, SyncField> fieldsWanted)
            throws IOException {

        // Filter the data to remove keys we don't care about
        final Collection<String> toRemove = new ArrayList<>();
        remoteBook.keySet().forEach(key -> {
            final SyncField field = fieldsWanted.get(key);
            if (field == null || field.getAction() == SyncAction.Skip) {
                toRemove.add(key);
            }
        });

        toRemove.forEach(remoteBook::remove);

        try {
            // For each field, process it according the SyncAction set.
            fieldsWanted
                    .values()
                    .stream()
                    .filter(field -> remoteBook.contains(field.getKey()))
                    .forEach(field -> {
                        try {
                            if (!process(context, localBook, remoteBook, field)) {
                                doDefaultProcessing(context, localBook, remoteBook, field);
                            }
                        } catch (@NonNull final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (@NonNull final UncheckedIOException e) {
            //noinspection DataFlowIssue
            throw e.getCause();
        }

        // run the mappers
        mappers.forEach(mapper -> mapper.map(context, remoteBook));

        // Commit the new data
        if (!remoteBook.isEmpty()) {
            // Get the language, if there was one requested for updating.
            String bookLang = remoteBook.getLanguage();
            if (bookLang.isEmpty()) {
                // Otherwise add the original one.
                bookLang = localBook.getLanguage();
                if (!bookLang.isEmpty()) {
                    remoteBook.setLanguage(bookLang);
                }
            }

            //IMPORTANT: note how we construct a NEW BOOK, with the DELTA-data which
            // we want to commit to the existing book.
            final Book delta = Book.from(remoteBook, realNumberParser);
            delta.putLong(DBKey.PK_ID, bookId);
            return delta;
        }

        return null;
    }

    /**
     * Overridable for custom processing.
     *
     * @param context    Current context
     * @param localBook  the local book
     * @param remoteBook the data to merge with the local-book
     * @param field      to process
     *
     * @return {@code true} when handled, {@code false} to apply default processing
     *
     * @throws IOException on <strong>very serious</strong> io issues.
     *                     Less serious io issues are swallowed/ignored
     */
    @WorkerThread
    protected boolean process(@NonNull final Context context,
                              @NonNull final Book localBook,
                              @NonNull final Book remoteBook,
                              @NonNull final SyncField field)
            throws IOException {
        return false;
    }

    @WorkerThread
    private void doDefaultProcessing(@NonNull final Context context,
                                     @NonNull final Book localBook,
                                     @NonNull final Book remoteBook,
                                     @NonNull final SyncField field)
            throws IOException {

        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            if (Book.BKEY_TMP_FILE_SPEC[cIdx].equals(field.getKey())) {
                processCover(localBook, remoteBook, cIdx);
                return;
            }
        }

        switch (field.getAction()) {
            case CopyIfBlank:
                // If our local book already has this data,
                // remove the unneeded field from the delta (remote book)
                if (hasField(localBook, field, realNumberParser)) {
                    remoteBook.remove(field.getKey());
                }
                break;

            case Append:
                processAppend(context, localBook, remoteBook, field.getKey());
                break;

            case Overwrite:
                // no action needed, the data in 'remoteBook' will overwrite
                // our local data
                break;

            case Skip:
            default:
                // Skip is N/A as fields to skip will have removed during filtering
                break;
        }
    }

    /**
     * Check if we already have this field (with content) in the original data.
     *
     * @param localBook        to check
     * @param field            to test
     * @param realNumberParser to use for number parsing
     *
     * @return {@code true} if already present
     */
    @WorkerThread
    private boolean hasField(@NonNull final Book localBook,
                             @NonNull final SyncField field,
                             @NonNull final RealNumberParser realNumberParser) {
        if (field.getType() == SyncField.Type.LIST) {
            if (localBook.contains(field.getKey())) {
                return !localBook.getParcelableArrayList(field.getKey()).isEmpty();
            }
        } else {
            // Non-list fields: we want a delta.
            // If our local book already has this data,
            // remove the unneeded field from the delta (remote book)
            // paranoia: check for keys present but considered 'empty'
            // we could probably just do
            //    return localBook.contains(key);
            final Object o = localBook.get(field.getKey(), realNumberParser);
            if (o != null) {
                return !isEmptyOrZero(o.toString().strip());
            }
        }

        return false;
    }

    @WorkerThread
    private void processCover(@NonNull final Book localBook,
                              @NonNull final Book remoteBook,
                              @IntRange(from = 0, to = 3) final int cIdx)
            throws IOException {

        final String fileSpec = remoteBook.getString(Book.BKEY_TMP_FILE_SPEC[cIdx], null);
        if (fileSpec != null && !fileSpec.isEmpty()) {
            //noinspection OverlyBroadCatchBlock
            try {
                ServiceLocator.getInstance().getCoverStorage()
                              .persist(new File(fileSpec), localBook.getUuid(), cIdx);

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
     * Handle the {@link SyncAction#Append}.
     * <p>
     * {@link SyncField.Type#LIST}: Combines two {@code ParcelableArrayList}s.
     * The result in 'remoteBook' MAY contain duplicates.
     * These will be pruned during the save to the database.
     * <p>
     * {@link SyncField.Type#STRING}: concatenates two {@code String}s.
     *
     * @param context    Current context
     * @param localBook  to check; will NOT be modified.
     * @param remoteBook the data to merge with the book;
     *                   after returning, this will contain the new data AND the data we merged
     *                   from the #localBook
     * @param key        into the incoming data
     *
     * @throws IllegalArgumentException if the key is not an appendable type
     */
    @WorkerThread
    private void processAppend(@NonNull final Context context,
                               @NonNull final Book localBook,
                               @NonNull final Book remoteBook,
                               @NonNull final String key) {
        // Add the localBook data to the remoteBook list!
        // and not the other way around! We want to collect a delta in remoteBook.
        // Note the local data/list must be inserted BEFORE the remote data/list,
        // so we properly 'append' the new data.
        switch (key) {
            case Book.BKEY_AUTHOR_LIST: {
                final List<Author> list = remoteBook.getAuthors();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getAuthors());
                    remoteBook.pruneAuthors(context);
                }
                break;
            }
            case Book.BKEY_BOOKSHELF_LIST: {
                final List<Bookshelf> list = remoteBook.getBookshelves();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getBookshelves());
                    remoteBook.pruneBookshelves(context);
                }
                break;
            }
            case Identifier.Value.BKEY_LIST: {
                final List<Identifier.Value> list = remoteBook.getIdentifiers();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getIdentifiers());
                    remoteBook.pruneIdentifiers(context);
                }
                break;
            }
            case Book.BKEY_PUBLISHER_LIST: {
                final List<Publisher> list = remoteBook.getPublishers();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getPublishers());
                    remoteBook.prunePublishers(context);
                }
                break;
            }
            case Book.BKEY_SERIES_LIST: {
                final List<Series> list = remoteBook.getSeries();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getSeries());
                    remoteBook.pruneSeries(context);
                }
                break;
            }
            case Book.BKEY_TAG_LIST: {
                final List<Tag> list = remoteBook.getTags();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getTags());
                    remoteBook.pruneTags(context);
                }
                break;
            }
            case Book.BKEY_TOC_LIST: {
                final List<TocEntry> list = remoteBook.getToc();
                if (!list.isEmpty()) {
                    list.addAll(0, localBook.getToc());
                    remoteBook.pruneToc(context);
                }
                break;
            }
            case DBKey.DESCRIPTION: {
                final String remoteDesc = remoteBook.getDescription();
                if (!remoteDesc.isEmpty()) {
                    final String localDesc = localBook.getDescription();
                    if (!localDesc.isEmpty() && !localDesc.contains(remoteDesc)) {
                        remoteBook.setDescription(localDesc + "<br/><br/>" + remoteDesc);
                    }
                    // else nothing to do, just use the remote description
                }
                break;
            }
            default: {
                throw new IllegalArgumentException(key);
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncProcessor{"
               + "fields=" + fields
               + '}';
    }

    /**
     * The return value from {@link #filter(SyncField, Book)}.
     * This will determine how {@link #filter(Book)} will process the given {@link SyncField}.
     */
    public enum FilterResult {
        /**
         * Add the {@link SyncField} as-is.
         * {@link #filter(SyncField, Book)} has decided!
         */
        Add,
        /**
         * Skip it, do not add it.
         * {@link #filter(SyncField, Book)} has decided!
         */
        Skip,
        /**
         * {@link #filter(SyncField, Book)} does not care, the field needs
         * be handled with the default rules.
         */
        ApplyDefault
    }

    public static class Builder {

        @NonNull
        private final String preferencePrefix;
        @NonNull
        private final SharedPreferences prefs;
        @SuppressWarnings("FieldNotUsedInToString")
        @NonNull
        private final RealNumberParser realNumberParser;

        private final Map<String, SyncField> fields = new LinkedHashMap<>();
        private final Map<String, String> relatedFields = new LinkedHashMap<>();

        /**
         * Constructor.
         *
         * @param preferencePrefix for the site/fields
         * @param locales          to use
         */
        public Builder(@NonNull final String preferencePrefix,
                       @NonNull final List<Locale> locales) {
            this.preferencePrefix = preferencePrefix;
            prefs = ServiceLocator.getInstance().getSharedPreferences();
            this.realNumberParser = new RealNumberParser(locales);
        }

        /**
         * Write current settings to the user preferences.
         */
        private void writePreferences() {
            final SharedPreferences.Editor ed = prefs.edit();
            for (final SyncField syncField : fields.values()) {
                ed.putInt(preferencePrefix + syncField.getKey(),
                          syncField.getAction().getId());
            }
            ed.apply();
        }

        /**
         * Reset current action back to defaults, and write to preferences.
         * <p>
         * This is normally a user initiated action.
         */
        public void resetPreferences() {
            fields.values().forEach(SyncField::setDefaultAction);
            writePreferences();
        }

        /**
         * Get the full list of {@link SyncField}s.
         *
         * @return all fields
         */
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
         * Add a {@link SyncField}.
         *
         * @param label    Field label
         * @param type     of field
         * @param fieldKey to add
         *                 also used as preference key to check user-enabled state
         */
        public void add(@NonNull final String label,
                        @NonNull final SyncField.Type type,
                        @NonNull final String fieldKey) {
            add(label, type, fieldKey, fieldKey);
        }

        /**
         * Add a {@link SyncField}.
         *
         * @param label      Field label
         * @param type       of field
         * @param fieldKey   to add
         * @param enabledKey preference key to check user-enabled state
         */
        public void add(@NonNull final String label,
                        @NonNull final SyncField.Type type,
                        @NonNull final String fieldKey,
                        @NonNull final String enabledKey) {
            if (!ServiceLocator.getInstance().isFieldEnabled(enabledKey)) {
                return;
            }
            final SyncAction action = SyncAction.byId(
                    prefs.getInt(preferencePrefix + fieldKey,
                                 type.getDefaultAction().getId()));
            fields.put(fieldKey, new SyncField(fieldKey, label, type, action));
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
            // This allows out-of-order adding.
            relatedFields.put(key, relatedKey);
            return this;
        }

        /**
         * Build the default processor.
         *
         * @param context Current context
         *
         * @return new instance
         */
        @NonNull
        public SyncReaderProcessor build(@NonNull final Context context) {
            return build(builder -> new SyncReaderProcessor(context, builder));
        }

        /**
         * Build the processor using a custom instance.
         *
         * @param supplier custom SyncReaderProcessor instance
         *
         * @return new instance
         */
        @NonNull
        public SyncReaderProcessor build(
                @NonNull final Function<Builder, SyncReaderProcessor> supplier) {

            writePreferences();

            relatedFields.forEach((key, relatedKey) -> {
                final SyncField syncField = fields.get(key);
                if (syncField != null && syncField.getAction() != SyncAction.Skip) {
                    fields.put(relatedKey, syncField.createRelatedField(relatedKey));
                }
            });
            return supplier.apply(this);
        }

        @Override
        @NonNull
        public String toString() {
            return "Builder{"
                   + "preferencePrefix='" + preferencePrefix + '\''
                   + ", prefs=" + prefs
                   + ", fields=" + fields
                   + ", relatedFields=" + relatedFields
                   + '}';
        }
    }
}

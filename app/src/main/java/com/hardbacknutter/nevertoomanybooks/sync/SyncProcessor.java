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
import androidx.annotation.EmptySuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoHelper;
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
 * Handles synchronizing each field controlled by a {@link SyncAction}.
 * <p>
 * {@code currentData} represents the the baseline data and will never be modified.
 * {@code incomingData} represents the data we need to merge/add.
 * It can/will be modified as needed.
 * <p>
 * The final delta to apply to {@code currentData} will be returned by
 * {@link #process(Context, long, Book, Book, Map)}.
 */
public class SyncProcessor {

    private static final String TAG = "SyncProcessor";
    /** See {@link #ensureLanguage(Book, Book)} and {@link BookDaoHelper}#process. */
    public static final String LANGUAGE = TAG + ":language";

    @NonNull
    private final Map<String, SyncField> fields;

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final RealNumberParser realNumberParser;
    /** Mappers to apply. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Collection<Mapper> mappers;

    @AnyThread
    protected SyncProcessor(@NonNull final Context context,
                            @NonNull final Builder builder) {
        this.fields = builder.fields;
        this.realNumberParser = builder.realNumberParser;

        mappers = MapperFactory.create(context);
    }

    private static boolean isEmptyOrZero(@Nullable final Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Money) {
            return ((Money) value).isZero();
        }
        if (value instanceof Boolean) {
            // false is considered 'empty'
            return !((boolean) value);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0d;
        }
        // catch-all
        final String s = value.toString();
        return s.isEmpty() || "0".equals(s) || "0.0".equals(s);
    }

    /**
     * Filter the fields we want versus the fields we actually need for the given book data.
     * <p>
     * This method is normally called <strong>before</strong> a website/syncserver is contacted,
     * as (theoretically) it allows the code to get more or less data depending on
     * the fields wanted. Prime example is of course the cover images.
     * <p>
     * Override {@link #filter(SyncField, Book)} for custom filtering.
     *
     * @param currentData to check for wanted fields
     *
     * @return the filtered SyncField's as a map
     */
    @NonNull
    public final Map<String, SyncField> filter(@NonNull final Book currentData) {

        final Map<String, SyncField> filteredMap = new LinkedHashMap<>();

        for (final SyncField field : fields.values()) {
            final FilterResult result = filter(field, currentData);
            switch (result) {
                case Add: {
                    filteredMap.put(field.getKey(), field);
                    break;
                }
                case ApplyDefault: {
                    if (doDefaultFiltering(field, currentData)) {
                        filteredMap.put(field.getKey(), field);
                    }
                    break;
                }
                case Skip:
                default:
                    break;
            }
        }

        return filteredMap;
    }

    /**
     * Overridable for custom filtering.
     *
     * @param field       to handle
     * @param currentData to check for wanted fields
     *
     * @return {@link FilterResult}
     */
    @EmptySuper
    @NonNull
    protected FilterResult filter(@NonNull final SyncField field,
                                  @NonNull final Book currentData) {
        return FilterResult.ApplyDefault;
    }

    /**
     * Apply the default filtering specific to the given field.
     *
     * @param field       to process
     * @param currentData to check for wanted fields
     *
     * @return {@code true} if the field needs to be added, otherwise skip it.
     */
    private boolean doDefaultFiltering(@NonNull final SyncField field,
                                       @NonNull final Book currentData) {
        switch (field.getAction()) {
            case CopyIfBlank: {
                // If our currentData does not have this field, add it as 'wanted'
                if (field.getType() == SyncField.Type.LIST) {
                    return currentData.getParcelableArrayList(field.getKey()).isEmpty();
                }

                for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
                    if (Book.BKEY_TMP_FILE_SPEC[cIdx].equals(field.getKey())) {
                        // check if we have a valid image
                        return ServiceLocator.getInstance().getCoverStorage()
                                             .getPersistedFile(currentData.getUuid(), cIdx)
                                             .isEmpty();
                    }
                }

                return isEmptyOrZero(currentData.get(field.getKey(), realNumberParser));

            }
            case Append:
            case Overwrite: {
                // Append + Overwrite: we always need to get the field
                return true;
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
     * <p>
     * Override {@link #process(Context, Book, Book, SyncField)} for custom  processing.
     *
     * @param context      Current context
     * @param bookId       to use for updating the database.
     *                     Must be passed separately, as 'book' can be all-new data.
     * @param currentData  the book with the base data for the sync; will NOT be modified.
     * @param incomingData the data to merge with #currentData
     * @param fieldsWanted The (subset) of fields relevant to the current book.
     *
     * @return a {@link Book} object with the <strong>DELTA</strong> fields that we need.
     *         The book id will always be set.
     *         It can be passed to {@link BookRepository#update}
     *
     * @throws IOException on <strong>very serious</strong> io issues.
     *                     Less serious io issues are swallowed/ignored
     * @see #process(Context, Book, Book, SyncField)
     */
    @WorkerThread
    @Nullable
    public final Book process(@NonNull final Context context,
                              final long bookId,
                              @NonNull final Book currentData,
                              @NonNull final Book incomingData,
                              @NonNull final Map<String, SyncField> fieldsWanted)
            throws IOException {

        // Filter the data to remove keys we don't care about
        final Collection<String> toRemove = new ArrayList<>();
        for (final String key : incomingData.keySet()) {
            final SyncField field = fieldsWanted.get(key);
            if (field == null || field.getAction() == SyncAction.Skip) {
                toRemove.add(key);
            }
        }

        toRemove.forEach(incomingData::remove);

        // For each field, process it according the SyncAction set.
        for (final SyncField field : fieldsWanted.values()) {
            if (incomingData.contains(field.getKey())) {
                if (!process(context, currentData, incomingData, field)) {
                    doDefaultProcessing(context, currentData, incomingData, field);
                }
            }
        }

        // run the mappers
        mappers.forEach(mapper -> mapper.map(context, incomingData));

        // Commit the new data
        if (!incomingData.isEmpty()) {
            ensureLanguage(currentData, incomingData);

            //IMPORTANT: note how we construct a NEW BOOK, with the DELTA-data which
            // we want to commit to the existing book.
            final Book delta = Book.from(incomingData, realNumberParser);
            delta.setId(bookId);
            return delta;
        }

        return null;
    }

    private void ensureLanguage(@NonNull final Book currentData,
                                @NonNull final Book incomingData) {
        // Get the language, if there was one requested for updating.
        String bookLang = incomingData.getLanguage();
        if (bookLang.isEmpty()) {
            // Otherwise add the original one but use a special key.
            // We do this because the BookDaoHelper#process needs it to handle the OB's.
            bookLang = currentData.getLanguage();
            if (!bookLang.isEmpty()) {
                incomingData.putString(LANGUAGE, bookLang);
            }
        }
    }

    /**
     * Overridable for custom processing.
     *
     * @param context      Current context
     * @param currentData  the book with the base data for the sync; will NOT be modified.
     * @param incomingData the data to merge with #currentData
     * @param field        to process
     *
     * @return {@code true} when handled, {@code false} to apply default processing
     *
     * @throws IOException on <strong>very serious</strong> io issues.
     *                     Less serious io issues are swallowed/ignored
     */
    @WorkerThread
    @EmptySuper
    protected boolean process(@NonNull final Context context,
                              @NonNull final Book currentData,
                              @NonNull final Book incomingData,
                              @NonNull final SyncField field)
            throws IOException {
        return false;
    }

    /**
     * Process the given book image.
     *
     * @param context      Current context
     * @param currentData  the book with the base data for the sync; will NOT be modified.
     * @param incomingData the data to merge with #currentData
     * @param field        to process
     *
     * @throws IOException on disk-full; other exceptions are logged but ignored
     */
    @WorkerThread
    private void doDefaultProcessing(@NonNull final Context context,
                                     @NonNull final Book currentData,
                                     @NonNull final Book incomingData,
                                     @NonNull final SyncField field)
            throws IOException {

        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            if (Book.BKEY_TMP_FILE_SPEC[cIdx].equals(field.getKey())) {
                processCover(currentData, incomingData, cIdx);
                return;
            }
        }

        switch (field.getAction()) {
            case CopyIfBlank:
                // If currentData already has this field, remove it from the incomingData
                final boolean hasData;
                if (field.getType() == SyncField.Type.LIST) {
                    hasData = !currentData.getParcelableArrayList(field.getKey()).isEmpty();
                } else {
                    hasData = !isEmptyOrZero(currentData.get(field.getKey(), realNumberParser));
                }

                if (hasData) {
                    incomingData.remove(field.getKey());
                }
                break;

            case Append:
                processAppend(context, currentData, incomingData, field.getKey());
                break;

            case Overwrite:
                // no action needed, the incomingData will overwrite our currentData.
                // At this moment, empty/zero fields in the incomingData are kept.
                // They will be removed once we process it in the BookDaoHelper
                break;

            case Skip:
            default:
                // Skip is N/A as fields to skip will have removed during filtering
                break;
        }
    }

    /**
     * Process the given book image.
     *
     * @param currentData  the book with the base data for the sync; will NOT be modified.
     * @param incomingData the data to merge with #currentData
     * @param cIdx         0..n image index
     *
     * @throws IOException on disk-full; other exceptions are logged but ignored
     */
    @WorkerThread
    private void processCover(@NonNull final Book currentData,
                              @NonNull final Book incomingData,
                              @IntRange(from = 0, to = 3) final int cIdx)
            throws IOException {

        final String fileSpec = incomingData.getString(Book.BKEY_TMP_FILE_SPEC[cIdx], null);
        if (fileSpec != null && !fileSpec.isEmpty()) {
            //noinspection OverlyBroadCatchBlock
            try {
                ServiceLocator.getInstance().getCoverStorage()
                              .persist(new File(fileSpec), currentData.getUuid(), cIdx);

            } catch (@NonNull final StorageException | IOException e) {
                // We're called in a loop, and the chance of an exception here is very low
                // so let's log it, and quietly continue.
                LoggerFactory.getLogger()
                             .e(TAG, e, "processCover|uuid="
                                        + currentData.getString(DBKey.BOOK_UUID, null)
                                        + "|cIdx=" + cIdx);
                // except disk-full!
                if (FileUtils.isDiskFull(e)) {
                    //noinspection DataFlowIssue
                    throw (IOException) e;
                }
            }
        }
        incomingData.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
    }

    /**
     * Handle the {@link SyncAction#Append}.
     * <p>
     * {@link SyncField.Type#LIST}: Combines two {@code ParcelableArrayList}s.
     * The result in 'incomingData' MAY contain duplicates.
     * These will be pruned during the save to the database.
     * <p>
     * {@link SyncField.Type#APPENDABLE_STRING}: concatenates two {@code String}s.
     *
     * @param context      Current context
     * @param currentData  the book with the base data for the sync; will NOT be modified.
     * @param incomingData the data to merge with #currentData
     *                     after returning, this will contain the new data AND the data we merged
     *                     from #currentData
     * @param key          into the #incomingData
     *
     * @throws IllegalArgumentException if the key is not an appendable type
     */
    @WorkerThread
    private void processAppend(@NonNull final Context context,
                               @NonNull final Book currentData,
                               @NonNull final Book incomingData,
                               @NonNull final String key) {
        // NEWTHINGS: adding SyncField.Type#APPENDABLE_STRING or SyncField.Type#LIST}
        //  to a SyncProcessor.

        // Add the currentData data to the incomingData lists!
        // and not the other way around!
        // We want to collect a delta in incomingData.
        // Note the current data/list must be inserted BEFORE the incomingData fields/lists,
        // so we properly 'append' the new data.
        switch (key) {
            case Author.BKEY_LIST: {
                final List<Author> list = incomingData.getAuthors();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getAuthors());
                    incomingData.pruneAuthors(context);
                }
                break;
            }
            case Bookshelf.BKEY_LIST: {
                final List<Bookshelf> list = incomingData.getBookshelves();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getBookshelves());
                    incomingData.pruneBookshelves(context);
                }
                break;
            }
            case Identifier.Value.BKEY_LIST: {
                final List<Identifier.Value> list = incomingData.getIdentifiers();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getIdentifiers());
                    incomingData.pruneIdentifiers(context);
                }
                break;
            }
            case Publisher.BKEY_LIST: {
                final List<Publisher> list = incomingData.getPublishers();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getPublishers());
                    incomingData.prunePublishers(context);
                }
                break;
            }
            case Series.BKEY_LIST: {
                final List<Series> list = incomingData.getSeries();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getSeries());
                    incomingData.pruneSeries(context);
                }
                break;
            }
            case Tag.BKEY_LIST: {
                final List<Tag> list = incomingData.getTags();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getTags());
                    incomingData.pruneTags(context);
                }
                break;
            }
            case TocEntry.BKEY_LIST: {
                final List<TocEntry> list = incomingData.getToc();
                if (!list.isEmpty()) {
                    list.addAll(0, currentData.getToc());
                    incomingData.pruneToc(context);
                }
                break;
            }
            case DBKey.DESCRIPTION: {
                final String remoteDesc = incomingData.getDescription();
                if (!remoteDesc.isEmpty()) {
                    final String localDesc = currentData.getDescription();
                    if (!localDesc.isEmpty() && !localDesc.contains(remoteDesc)) {
                        incomingData.setDescription(localDesc + "<br/><br/>" + remoteDesc);
                    }
                    // else nothing to do, the remote description will be copied as normal
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
         * Add a {@link SyncField} using the default {@link SyncAction} for the type.
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
         * Add a {@link SyncField} using the default {@link SyncAction} for the type.
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
            final SyncAction action = SyncAction.byId(
                    prefs.getInt(preferencePrefix + fieldKey,
                                 type.getDefaultAction().getId()));
            final SyncField syncField = new SyncField(fieldKey, label, type, action);
            add(syncField, enabledKey);
        }

        /**
         * Add a {@link SyncField}.
         *
         * @param syncField  to add
         * @param enabledKey preference key to check user-enabled state
         */
        public void add(@NonNull final SyncField syncField,
                        @NonNull final String enabledKey) {
            if (ServiceLocator.getInstance().isFieldEnabled(enabledKey)) {
                fields.put(syncField.getKey(), syncField);
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
        public SyncProcessor build(@NonNull final Context context) {
            return build(builder -> new SyncProcessor(context, builder));
        }

        /**
         * Build the processor using a custom instance.
         *
         * @param supplier custom SyncProcessor instance
         *
         * @return new instance
         */
        @NonNull
        public SyncProcessor build(
                @NonNull final Function<Builder, SyncProcessor> supplier) {

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

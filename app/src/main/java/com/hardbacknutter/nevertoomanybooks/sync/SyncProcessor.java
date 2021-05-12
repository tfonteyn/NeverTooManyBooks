/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

public final class SyncProcessor
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SyncProcessor> CREATOR = new Creator<SyncProcessor>() {
        @Override
        public SyncProcessor createFromParcel(@NonNull final Parcel in) {
            return new SyncProcessor(in);
        }

        @Override
        public SyncProcessor[] newArray(final int size) {
            return new SyncProcessor[size];
        }
    };

    private static final String TAG = "SyncProcessor";

    @NonNull
    private final Map<String, SyncField> mFields;

    private SyncProcessor(@NonNull final Map<String, SyncField> fields) {
        mFields = fields;
    }

    protected SyncProcessor(@NonNull final Parcel in) {
        final List<SyncField> list = new ArrayList<>();
        ParcelUtils.readParcelableList(in, list, SyncField.class.getClassLoader());

        mFields = new LinkedHashMap<>();
        list.forEach(syncField -> mFields.put(syncField.key, syncField));
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        ParcelUtils.writeParcelableList(dest, new ArrayList<>(mFields.values()), flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Filter the fields we want versus the fields we actually need for the given book data.
     * <p>
     * This method is normally called <strong>before</strong> a website is contacted,
     * as (theoretically) it allows the code to download more or less data depending on
     * the fields wanted. Prime example is of course the cover images.
     *
     * @param book to filter
     *
     * @return the filtered SyncField unmodifiableMap
     */
    @NonNull
    public Map<String, SyncField> filter(@NonNull final Book book) {

        final Map<String, SyncField> filteredMap = new LinkedHashMap<>();

        for (final SyncField field : mFields.values()) {
            switch (field.getAction()) {
                case Skip:
                    // duh...
                    break;

                case Append:
                case Overwrite: {
                    // Append + Overwrite: we always need to get the data
                    filteredMap.put(field.key, field);
                    break;
                }
                case CopyIfBlank: {
                    switch (field.key) {
                        // We should never have a book without authors, but be paranoid
                        case Book.BKEY_AUTHOR_LIST:
                        case Book.BKEY_SERIES_LIST:
                        case Book.BKEY_PUBLISHER_LIST:
                        case Book.BKEY_TOC_LIST:
                        case Book.BKEY_BOOKSHELF_LIST:
                            if (book.contains(field.key)) {
                                final ArrayList<Parcelable> list =
                                        book.getParcelableArrayList(field.key);
                                if (list.isEmpty()) {
                                    filteredMap.put(field.key, field);
                                }
                            }
                            break;

                        default:
                            // If it's a cover...
                            if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[0])) {
                                // check if it's missing or empty.
                                final File file = book.getPersistedCoverFile(0);
                                if (file == null || file.length() == 0) {
                                    filteredMap.put(field.key, field);
                                }

                            } else if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[1])) {
                                // check if it's missing or empty.
                                final File file = book.getPersistedCoverFile(1);
                                if (file == null || file.length() == 0) {
                                    filteredMap.put(field.key, field);
                                }

                            } else {
                                // all other fields can be 'empty string' tested
                                // If the original was blank, add to list
                                final String value = book.getString(field.key);
                                if (value.isEmpty()) {
                                    filteredMap.put(field.key, field);
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
     * @param context      Current context
     * @param bookId       to use for updating the database.
     *                     Must be passed separately, as 'book' can be all-new data.
     * @param book         the local book
     * @param fieldsWanted The (subset) of fields relevant to the current book.
     * @param incoming     the data to merge with the book
     *
     * @return a {@link Book} object with the <strong>DELTA</strong> fields that we need.
     * The book id will always be set.
     * It can be passed to {@link BookDao#update(Context, Book, int)}
     */
    @Nullable
    public Book process(@NonNull final Context context,
                        final long bookId,
                        @NonNull final Book book,
                        @NonNull final Map<String, SyncField> fieldsWanted,
                        @NonNull final Bundle incoming) {

        // Filter the data to remove keys we don't care about
        final Collection<String> toRemove = new ArrayList<>();
        for (final String key : incoming.keySet()) {
            final SyncField field = fieldsWanted.get(key);
            if (field == null || field.getAction() == SyncAction.Skip) {
                toRemove.add(key);
            }
        }
        for (final String key : toRemove) {
            incoming.remove(key);
        }

        final Locale bookLocale = book.getLocale(context);

        // For each field, process it according the SyncAction set.
        fieldsWanted
                .values()
                .stream()
                .filter(field -> incoming.containsKey(field.key))
                .forEach(field -> {
                    // Handle thumbnail specially
                    if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[0])) {
                        processCover(book, incoming, 0);
                    } else if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[1])) {
                        processCover(book, incoming, 1);
                    } else {
                        switch (field.getAction()) {
                            case CopyIfBlank:
                                // remove unneeded fields from the new data
                                if (hasField(book, field.key)) {
                                    incoming.remove(field.key);
                                }
                                break;

                            case Append:
                                processList(context, book, bookLocale, incoming, field.key);
                                break;

                            case Overwrite:
                            case Skip:
                                break;
                        }
                    }
                });

        // Commit the new data
        if (!incoming.isEmpty()) {
            // Get the language, if there was one requested for updating.
            String bookLang = incoming.getString(DBKey.KEY_LANGUAGE);
            if (bookLang == null || bookLang.isEmpty()) {
                // Otherwise add the original one.
                bookLang = book.getString(DBKey.KEY_LANGUAGE);
                if (!bookLang.isEmpty()) {
                    incoming.putString(DBKey.KEY_LANGUAGE, bookLang);
                }
            }

            //IMPORTANT: note how we construct a NEW BOOK, with the DELTA-data which
            // we want to commit to the existing book.
            final Book delta = Book.from(incoming);
            delta.putLong(DBKey.PK_ID, bookId);
            return delta;
        }

        return null;
    }

    /**
     * Check if we already have this field (with content) in the original data.
     *
     * @param key to test for
     *
     * @return {@code true} if already present
     */
    private boolean hasField(@NonNull final Book book,
                             @NonNull final String key) {
        switch (key) {
            case Book.BKEY_AUTHOR_LIST:
            case Book.BKEY_SERIES_LIST:
            case Book.BKEY_PUBLISHER_LIST:
            case Book.BKEY_TOC_LIST:
            case Book.BKEY_BOOKSHELF_LIST:
                if (book.contains(key)) {
                    if (!book.getParcelableArrayList(key).isEmpty()) {
                        return true;
                    }
                }
                break;

            default:
                final Object o = book.get(key);
                if (o != null) {
                    final String value = o.toString().trim();
                    if (!value.isEmpty() && !"0".equals(value)) {
                        return true;
                    }
                }
                break;
        }

        return false;
    }

    private void processCover(@NonNull final Book book,
                              @NonNull final Bundle incoming,
                              @IntRange(from = 0, to = 1) final int cIdx) {

        final String fileSpec = incoming.getString(Book.BKEY_TMP_FILE_SPEC[cIdx]);
        if (fileSpec != null) {
            try {
                book.persistCover(new File(fileSpec), cIdx);

            } catch (@NonNull final CoverStorageException | IOException e) {
                // We're called in a loop, and the chance of an exception here is very low
                // so let's log it, and quietly continue.
                Logger.error(TAG, e, "processCoverImage|uuid="
                                     + book.getString(DBKey.KEY_BOOK_UUID)
                                     + "|cIdx=" + cIdx);
            }
        }
        incoming.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
    }

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param context    Current context
     * @param bookLocale to use
     * @param incoming   Bundle to update
     * @param key        into the incoming data
     */
    private void processList(@NonNull final Context context,
                             @NonNull final Book book,
                             @NonNull final Locale bookLocale,
                             @NonNull final Bundle incoming,
                             @NonNull final String key) {
        switch (key) {
            case Book.BKEY_AUTHOR_LIST: {
                final ArrayList<Author> list = incoming.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(book.getParcelableArrayList(key));
                    Author.pruneList(list, context, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_SERIES_LIST: {
                final ArrayList<Series> list = incoming.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(book.getParcelableArrayList(key));
                    Series.pruneList(list, context, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_PUBLISHER_LIST: {
                final ArrayList<Publisher> list = incoming.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(book.getParcelableArrayList(key));
                    Publisher.pruneList(list, context, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_TOC_LIST: {
                final ArrayList<TocEntry> list = incoming.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(book.getParcelableArrayList(key));
                    TocEntry.pruneList(list, context, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_BOOKSHELF_LIST: {
                final ArrayList<Bookshelf> list = incoming.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(book.getParcelableArrayList(key));
                    Bookshelf.pruneList(list);
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
               + "mFields=" + mFields
               + '}';
    }

    public static class Builder {

        @NonNull
        private final String mPreferencePrefix;
        @NonNull
        private final SharedPreferences mGlobalPref;

        private final Map<String, SyncField> mFields = new LinkedHashMap<>();
        private final Map<String, String> mRelatedFields = new LinkedHashMap<>();

        public Builder(@NonNull final String preferencePrefix) {
            mPreferencePrefix = preferencePrefix;
            mGlobalPref = ServiceLocator.getGlobalPreferences();
        }

        /**
         * Write current settings to the user preferences.
         */
        public void writePreferences() {

            final SharedPreferences.Editor ed = mGlobalPref.edit();
            for (final SyncField syncField : mFields.values()) {
                syncField.getAction().write(ed, mPreferencePrefix + syncField.key);
            }
            ed.apply();
        }

        /**
         * Reset current action back to defaults, and write to preferences.
         */
        public void resetPreferences() {

            for (final SyncField syncField : mFields.values()) {
                syncField.setDefaultAction();
            }
            writePreferences();
        }

        @NonNull
        public Collection<SyncField> getFieldSyncList() {
            return mFields.values();
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
            final SyncField syncField = mFields.get(key);
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
            final SyncField syncField = mFields.get(key);
            if (syncField != null) {
                syncField.setAction(syncAction);
            }
        }


        /**
         * Convenience method wrapper for {@link #add(int, String, SyncAction)}.
         * The default SyncAction is always {@link SyncAction#CopyIfBlank}.
         *
         * @param labelId Field label resource id
         * @param key     Field key
         *
         * @return {@code this} (for chaining)
         */
        public Builder add(@StringRes final int labelId,
                           @NonNull final String key) {
            return add(labelId, key, SyncAction.CopyIfBlank);
        }

        /**
         * Add a {@link SyncField} for a <strong>simple</strong> field
         * if it has not been hidden by the user.
         *
         * @param labelId       Field label resource id
         * @param key           Field key
         * @param defaultAction default Usage for this field
         *
         * @return {@code this} (for chaining)
         */
        public Builder add(@StringRes final int labelId,
                           @NonNull final String key,
                           @NonNull final SyncAction defaultAction) {

            if (DBKey.isUsed(mGlobalPref, key)) {
                final SyncAction action = SyncAction
                        .read(mGlobalPref, mPreferencePrefix + key, defaultAction);
                mFields.put(key, new SyncField(key, labelId, false,
                                               defaultAction, action));
            }
            return this;
        }

        /**
         * Add a {@link SyncField} for a <strong>list</strong> field
         * if it has not been hidden by the user.
         * <p>
         * The default SyncAction is always {@link SyncAction#Append}.
         *
         * @param labelId Field label resource id
         * @param prefKey Field name to use for preferences.
         * @param key     Field key
         *
         * @return {@code this} (for chaining)
         */
        public Builder addList(@StringRes final int labelId,
                               @NonNull final String prefKey,
                               @NonNull final String key) {

            if (DBKey.isUsed(mGlobalPref, prefKey)) {
                final SyncAction action = SyncAction
                        .read(mGlobalPref, mPreferencePrefix + key, SyncAction.Append);
                mFields.put(key, new SyncField(key, labelId, true,
                                               SyncAction.Append, action));
            }
            return this;
        }

        /**
         * Add any related fields with the same setting.
         *
         * @param key        the field to check
         * @param relatedKey to add if the primary field is present
         *
         * @return {@code this} (for chaining)
         */
        public Builder addRelatedField(@NonNull final String key,
                                       @NonNull final String relatedKey) {
            // Don't check on key being present in mFields here. We'll do that at usage time.
            //This allows out-of-order adding.
            mRelatedFields.put(key, relatedKey);
            return this;
        }

        @NonNull
        public SyncProcessor build() {
            for (final Map.Entry<String, String> entry : mRelatedFields.entrySet()) {
                final SyncField syncField = mFields.get(entry.getKey());
                if (syncField != null && (syncField.getAction() != SyncAction.Skip)) {
                    final String relatedKey = entry.getValue();
                    mFields.put(relatedKey, syncField.createRelatedField(relatedKey));
                }
            }
            return new SyncProcessor(mFields);
        }
    }
}

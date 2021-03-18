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
package com.hardbacknutter.nevertoomanybooks.fields.syncing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public final class SyncProcessor {

    private static final String TAG = "SyncProcessor";

    @NonNull
    private final String mPreferencePrefix;

    @NonNull
    private final SharedPreferences mGlobalPreferences;

    /** Database Access. */
    private final BookDao mBookDao;

    @NonNull
    private final Map<String, FieldSync> mFields;

    private SyncProcessor(@NonNull final String preferencePrefix,
                          @NonNull final SharedPreferences globalPreferences,
                          @NonNull final BookDao bookDao,
                          @NonNull final Map<String, FieldSync> fields) {
        mPreferencePrefix = preferencePrefix;
        mGlobalPreferences = globalPreferences;
        mBookDao = bookDao;
        mFields = fields;
    }

    /**
     * Filter the fields we want versus the fields we actually need for the given book data.
     *
     * @param context Current context
     * @param book    to filter
     *
     * @return the filtered FieldSync map
     */
    @NonNull
    public Map<String, FieldSync> filter(@NonNull final Context context,
                                         @NonNull final Book book) {

        final Map<String, FieldSync> filteredMap = new LinkedHashMap<>();

        for (final FieldSync field : mFields.values()) {
            switch (field.getAction()) {
                case Skip:
                    // duh...
                    break;

                case Append:
                case Overwrite:
                    // Append + Overwrite: we always need to get the data
                    filteredMap.put(field.key, field);
                    break;

                case CopyIfBlank:
                    // Handle special cases first, 'default:' for the rest
                    if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[0])) {
                        // - If it's a thumbnail, then see if it's missing or empty.
                        final File file = book.getUuidCoverFile(context, 0);
                        if (file == null || file.length() == 0) {
                            filteredMap.put(field.key, field);
                        }

                    } else if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[1])) {
                        // - If it's a thumbnail, then see if it's missing or empty.
                        final File file = book.getUuidCoverFile(context, 1);
                        if (file == null || file.length() == 0) {
                            filteredMap.put(field.key, field);
                        }

                    } else {
                        switch (field.key) {
                            // We should never have a book without authors, but be paranoid
                            case Book.BKEY_AUTHOR_LIST:
                            case Book.BKEY_SERIES_LIST:
                            case Book.BKEY_PUBLISHER_LIST:
                            case Book.BKEY_TOC_LIST:
                                if (book.contains(field.key)) {
                                    final ArrayList<Parcelable> list =
                                            book.getParcelableArrayList(field.key);
                                    if (list.isEmpty()) {
                                        filteredMap.put(field.key, field);
                                    }
                                }
                                break;

                            default:
                                // If the original was blank, add to list
                                final String value = book.getString(field.key);
                                if (value.isEmpty()) {
                                    filteredMap.put(field.key, field);
                                }
                                break;
                        }
                    }
                    break;
            }
        }

        return filteredMap;
    }

    /**
     * Process the search-result data for one book.
     *
     * @param context      Current context
     * @param bookId       to use for updating the database.
     *                     Must be passed separately, as 'book' can be all-new data.
     * @param book         the local book
     * @param fieldsWanted The (subset) of fields relevant to the current book.
     * @param incoming     the data to merge with the book
     */
    public void processOne(@NonNull final Context context,
                           final long bookId,
                           @NonNull final Book book,
                           @NonNull final Map<String, FieldSync> fieldsWanted,
                           @NonNull final Bundle incoming) {
        // Filter the data to remove keys we don't care about
        final Collection<String> toRemove = new ArrayList<>();
        for (final String key : incoming.keySet()) {
            final FieldSync fieldSync = fieldsWanted.get(key);
            if (fieldSync == null || !fieldSync.isWanted()) {
                toRemove.add(key);
            }
        }
        for (final String key : toRemove) {
            incoming.remove(key);
        }

        final Locale bookLocale = book.getLocale(context);

        // For each field, process it according the usage.
        fieldsWanted
                .values()
                .stream()
                .filter(field -> incoming.containsKey(field.key))
                .forEach(field -> {
                    // Handle thumbnail specially
                    if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[0])) {
                        processCoverField(context, book, incoming, field.getAction(), 0);
                    } else if (field.key.equals(Book.BKEY_TMP_FILE_SPEC[1])) {
                        processCoverField(context, book, incoming, field.getAction(), 1);
                    } else {
                        switch (field.getAction()) {
                            case CopyIfBlank:
                                // remove unneeded fields from the new data
                                if (hasField(book, field.key)) {
                                    incoming.remove(field.key);
                                }
                                break;

                            case Append:
                                processListField(context, book, bookLocale, field.key, incoming);
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
            String bookLang = incoming.getString(DBKeys.KEY_LANGUAGE);
            if (bookLang == null || bookLang.isEmpty()) {
                // Otherwise add the original one.
                bookLang = book.getString(DBKeys.KEY_LANGUAGE);
                if (!bookLang.isEmpty()) {
                    incoming.putString(DBKeys.KEY_LANGUAGE, bookLang);
                }
            }

            //IMPORTANT: note how we construct a NEW BOOK, with the DELTA-data which
            // we want to commit to the existing book.
            final Book delta = Book.from(incoming);
            delta.putLong(DBKeys.KEY_PK_ID, bookId);
            try {
                mBookDao.update(context, delta, 0);
            } catch (@NonNull final DaoWriteException e) {
                // ignore, but log it.
                Logger.error(context, TAG, e);
            }
        }
    }

    /**
     * Check if we already have this field in the original data.
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
                if (book.contains(key)) {
                    if (!book.getParcelableArrayList(key).isEmpty()) {
                        return true;
                    }
                }
                break;

            default:
                // If the original was non-blank, erase from list
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

    private void processCoverField(@NonNull final Context context,
                                   @NonNull final Book book,
                                   @NonNull final Bundle incoming,
                                   @NonNull final SyncAction syncAction,
                                   @IntRange(from = 0, to = 1) final int cIdx) {
        boolean copyThumb = false;
        // check if we already have an image, and what we should do with the new image
        switch (syncAction) {
            case CopyIfBlank:
                final File file = book.getUuidCoverFile(context, cIdx);
                copyThumb = file == null || file.length() == 0;
                break;

            case Overwrite:
                copyThumb = true;
                break;

            case Skip:
            case Append:
                break;
        }

        if (copyThumb) {
            final String fileSpec = incoming.getString(Book.BKEY_TMP_FILE_SPEC[cIdx]);
            if (fileSpec != null) {
                final File downloadedFile = new File(fileSpec);
                try {
                    final File destination = book.getUuidCoverFileOrNew(context, cIdx);
                    FileUtils.rename(downloadedFile, destination);

                } catch (@NonNull final IOException e) {
                    final String uuid = book.getString(DBKeys.KEY_BOOK_UUID);
                    Logger.error(context, TAG, e,
                                 "processCoverImage|uuid=" + uuid + "|cIdx=" + cIdx);
                }
            }
            incoming.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
        }
    }

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param context    Current context
     * @param bookLocale to use
     * @param key        for data
     * @param incoming   Bundle to update
     */
    private void processListField(@NonNull final Context context,
                                  @NonNull final Book book,
                                  @NonNull final Locale bookLocale,
                                  @NonNull final String key,
                                  @NonNull final Bundle incoming) {
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
            default:
                throw new IllegalArgumentException(key);
        }
    }

    @NonNull
    public Collection<FieldSync> getFieldSyncList() {
        return mFields.values();
    }

    @Nullable
    public SyncAction getSyncAction(@NonNull final String key) {
        final FieldSync fieldSync = mFields.get(key);
        if (fieldSync != null) {
            return fieldSync.getAction();
        }
        return null;
    }

    /**
     * Update the {@link FieldSync} for the given key.
     * Does nothing if the field was not actually added before.
     *
     * @param key        field to update
     * @param syncAction to set
     */
    public void setSyncAction(@NonNull final String key,
                              @NonNull final SyncAction syncAction) {
        final FieldSync fieldSync = mFields.get(key);
        if (fieldSync != null) {
            fieldSync.setAction(syncAction);
        }
    }


    /**
     * Add any related fields with the same setting.
     * <p>
     * We enforce a name (string id), although it's never displayed, for sanity/debug sake.
     *
     * @param labelId    Field label resource id (not used)
     * @param key        the field to check
     * @param relatedKey to add if the primary field is present
     */
    public void addRelatedField(@StringRes final int labelId,
                                @NonNull final String key,
                                @NonNull final String relatedKey) {
        final FieldSync fieldSync = mFields.get(key);
        if (fieldSync != null && fieldSync.isWanted()) {
            mFields.put(relatedKey, fieldSync.createRelatedField(relatedKey, labelId));
        }
    }

    public boolean isOverwrite(@NonNull final String key) {
        final FieldSync fieldSync = mFields.get(key);
        return fieldSync != null && fieldSync.getAction() == SyncAction.Overwrite;
    }

    /**
     * Write current settings to the user preferences.
     */
    public void writePreferences() {
        final SharedPreferences.Editor ed = mGlobalPreferences.edit();
        for (final FieldSync fieldSync : mFields.values()) {
            fieldSync.getAction().write(ed, mPreferencePrefix + fieldSync.key);
        }
        ed.apply();
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     */
    public void resetPreferences() {
        for (final FieldSync fieldSync : mFields.values()) {
            fieldSync.setDefaultAction();
        }
        writePreferences();
    }

    public static class Builder {

        @NonNull
        private final String mPrefPrefix;

        private final SharedPreferences mGlobalPref;
        @NonNull
        private final Map<String, FieldSync> mFields = new LinkedHashMap<>();

        public Builder(@NonNull final String preferencePrefix) {
            mPrefPrefix = preferencePrefix;
            mGlobalPref = ServiceLocator.getGlobalPreferences();
        }

        /**
         * Add a {@link FieldSync} for a <strong>simple</strong> field
         * if it has not been hidden by the user.
         *
         * @param labelId       Field label resource id
         * @param key           Field key
         * @param defaultAction default Usage for this field
         */
        public Builder add(@StringRes final int labelId,
                           @NonNull final String key,
                           @NonNull final SyncAction defaultAction) {

            if (DBKeys.isUsed(mGlobalPref, key)) {
                final SyncAction action = SyncAction
                        .read(mGlobalPref, mPrefPrefix + key, defaultAction);
                mFields.put(key, new FieldSync(key, labelId, false,
                                               defaultAction, action));
            }
            return this;
        }

        /**
         * Convenience method wrapper for {@link #add(int, String, SyncAction)}.
         * The default SyncAction is always {@link SyncAction#CopyIfBlank}.
         *
         * @param labelId Field label resource id
         * @param key     Field key
         */
        public Builder add(@StringRes final int labelId,
                           @NonNull final String key) {
            return add(labelId, key, SyncAction.CopyIfBlank);
        }

        /**
         * Add a {@link FieldSync} for a <strong>cover</strong> field
         * if it has not been hidden by the user.
         * <p>
         * The default SyncAction is always {@link SyncAction#CopyIfBlank}.
         *
         * @param labelId Field label resource id
         * @param cIdx    0..n image index
         */
        public Builder addCover(@StringRes final int labelId,
                                @IntRange(from = 0, to = 1) final int cIdx) {

            if (DBKeys.isCoverUsed(mGlobalPref, cIdx)) {
                final String key = DBKeys.PREFS_IS_USED_COVER + "." + cIdx;
                final SyncAction action = SyncAction
                        .read(mGlobalPref, mPrefPrefix + key, SyncAction.CopyIfBlank);
                mFields.put(key, new FieldSync(key, labelId, false,
                                               SyncAction.CopyIfBlank, action));
            }
            return this;
        }

        /**
         * Add a {@link FieldSync} for a <strong>list</strong> field
         * if it has not been hidden by the user.
         * <p>
         * The default SyncAction is always {@link SyncAction#Append}.
         *
         * @param labelId Field label resource id
         * @param prefKey Field name to use for preferences.
         * @param key     Field key
         */
        public Builder addList(@StringRes final int labelId,
                               @NonNull final String prefKey,
                               @NonNull final String key) {

            if (DBKeys.isUsed(mGlobalPref, prefKey)) {
                final SyncAction action =
                        SyncAction.read(mGlobalPref, mPrefPrefix + key, SyncAction.Append);
                mFields.put(key, new FieldSync(key, labelId, true, SyncAction.Append, action));
            }
            return this;
        }

        @NonNull
        public SyncProcessor build(@NonNull final BookDao bookDao) {
            return new SyncProcessor(mPrefPrefix, mGlobalPref, bookDao, mFields);
        }
    }
}

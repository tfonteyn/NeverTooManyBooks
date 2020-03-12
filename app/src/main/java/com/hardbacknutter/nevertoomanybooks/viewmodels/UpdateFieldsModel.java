/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

/**
 * Work flow for the {@link TaskListener.FinishMessage}.
 */
public class UpdateFieldsModel
        extends SearchCoordinator {

    /** Log tag. */
    private static final String TAG = "UpdateFieldsModel";
    private static final String BKEY_LAST_BOOK_ID = TAG + ":lastId";
    /** which fields to update and how. */
    @NonNull
    private final Map<String, FieldUsage> mFieldUsages = new LinkedHashMap<>();

    /** Using SingleLiveEvent to prevent multiple delivery after for example a device rotation. */
    private final MutableLiveData<TaskListener.FinishMessage<Bundle>>
            mTaskFinishedMessage = new SingleLiveEvent<>();

    /** Database Access. */
    private DAO mDb;
    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private ArrayList<Long> mBookIds;
    /** Allows restarting an update task from the given book id onwards. 0 for all. */
    private long mFromBookIdOnwards;

    /** Indicates the user has requested a cancel. Up to the subclass to decide what to do. */
    private boolean mIsCancelled;

    /**
     * Current and original book data.
     * Tracks between {@link #startSearch(Context)}
     * and {@link #processSearchResults(Context, Bundle)}.
     */
    private Bundle mCurrentBookData = new Bundle();
    /**
     * Current book ID.
     * Tracks between {@link #startSearch(Context)}
     * and {@link #processSearchResults(Context, Bundle)}.
     */
    private long mCurrentBookId;
    /**
     * The (subset) of fields relevant to the current book.
     * Tracks between {@link #startSearch(Context)}
     * and {@link #processSearchResults(Context, Bundle)}.
     */
    private Map<String, FieldUsage> mCurrentFieldsWanted;

    private int mCurrentProgressCounter;
    private int mCurrentCursorCount;
    private Cursor mCurrentCursor;

    /** Observable. */
    @NonNull
    public MutableLiveData<TaskListener.FinishMessage<Bundle>> getAllUpdatesFinishedMessage() {
        return mTaskFinishedMessage;
    }

    @Override
    protected void onCleared() {
        // sanity check, should already have been closed.
        if (mCurrentCursor != null) {
            mCurrentCursor.close();
        }

        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        // init the SearchCoordinator.
        super.init(context, args);

        if (mDb == null) {

            mDb = new DAO(TAG);
            // use global preference.
            final Locale locale = LocaleUtils.getUserLocale(context);
            setSiteList(SiteList.getList(context, locale, SiteList.Type.Data));

            if (args != null) {
                //noinspection unchecked
                mBookIds = (ArrayList<Long>) args.getSerializable(UniqueId.BKEY_ID_LIST);
            }

            initFields(context);
        }
    }

    /**
     * Entries are displayed in the order they are added here.
     *
     * @param context Current context
     */
    private void initFields(@NonNull final Context context) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        addListField(prefs, UniqueId.BKEY_AUTHOR_ARRAY, R.string.lbl_author,
                     DBDefinitions.KEY_FK_AUTHOR);

        addField(prefs, DBDefinitions.KEY_TITLE, R.string.lbl_title, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_ISBN, R.string.lbl_isbn, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_THUMBNAIL, R.string.lbl_cover, CopyIfBlank);

        addListField(prefs, UniqueId.BKEY_SERIES_ARRAY, R.string.lbl_series,
                     DBDefinitions.KEY_SERIES_TITLE);

        addListField(prefs, UniqueId.BKEY_TOC_ENTRY_ARRAY, R.string.lbl_table_of_content,
                     DBDefinitions.KEY_TOC_BITMASK);

        addListField(prefs, UniqueId.BKEY_PUBLISHER_ARRAY, R.string.lbl_publisher,
                     DBDefinitions.KEY_PUBLISHER);

        addField(prefs, DBDefinitions.KEY_PRINT_RUN, R.string.lbl_print_run, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_DATE_PUBLISHED, R.string.lbl_date_published, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_DATE_FIRST_PUBLICATION, R.string.lbl_first_publication,
                 CopyIfBlank);

        // list price has related DBDefinitions.KEY_PRICE_LISTED
        addField(prefs, DBDefinitions.KEY_PRICE_LISTED, R.string.lbl_price_listed, CopyIfBlank);

        addField(prefs, DBDefinitions.KEY_DESCRIPTION, R.string.lbl_description, CopyIfBlank);

        addField(prefs, DBDefinitions.KEY_PAGES, R.string.lbl_pages, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_FORMAT, R.string.lbl_format, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_COLOR, R.string.lbl_color, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_LANGUAGE, R.string.lbl_language, CopyIfBlank);
        addField(prefs, DBDefinitions.KEY_GENRE, R.string.lbl_genre, CopyIfBlank);

        //NEWTHINGS: add new site specific ID: add a field
        addField(prefs, DBDefinitions.KEY_EID_ISFDB, R.string.site_isfdb, Overwrite);
        addField(prefs, DBDefinitions.KEY_EID_GOODREADS_BOOK, R.string.site_goodreads, Overwrite);
        addField(prefs, DBDefinitions.KEY_EID_LIBRARY_THING, R.string.site_library_thing,
                 Overwrite);
        addField(prefs, DBDefinitions.KEY_EID_OPEN_LIBRARY, R.string.site_open_library, Overwrite);
        addField(prefs, DBDefinitions.KEY_EID_STRIP_INFO_BE, R.string.site_stripinfo, Overwrite);
    }

    @NonNull
    public Collection<FieldUsage> getFieldUsages() {
        return mFieldUsages.values();
    }

    @Nullable
    public FieldUsage getFieldUsage(@NonNull final String key) {
        return mFieldUsages.get(key);
    }

    /**
     * Allows to set the 'lowest' Book id to start from. See {@link DAO#fetchBooks(List, long)}
     *
     * @param fromBookIdOnwards the lowest book id to start from.
     *                          This allows to fetch a subset of the requested set.
     *                          Defaults to 0, i.e. the full set.
     */
    public void setFromBookIdOnwards(final long fromBookIdOnwards) {
        mFromBookIdOnwards = fromBookIdOnwards;
    }

    /**
     * Add a FieldUsage for a <strong>list</strong> field if it has not been hidden by the user.
     * <p>
     *
     * @param prefs        SharedPreferences
     * @param fieldId      List-field name to use in FieldUsages
     * @param nameStringId Field label string resource ID
     * @param key          Field name to use for preferences.
     */
    private void addListField(@NonNull final SharedPreferences prefs,
                              @NonNull final String fieldId,
                              @StringRes final int nameStringId,
                              @NonNull final String key) {

        if (DBDefinitions.isUsed(prefs, key)) {
            final FieldUsage fieldUsage = FieldUsage.createListField(fieldId, nameStringId, prefs);
            mFieldUsages.put(fieldId, fieldUsage);
        }
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param prefs        SharedPreferences
     * @param fieldId      Field name to use in FieldUsages, and as key for preferences.
     * @param nameStringId Field label string resource ID
     * @param defValue     default Usage for this field
     */
    private void addField(@NonNull final SharedPreferences prefs,
                          @NonNull final String fieldId,
                          @StringRes final int nameStringId,
                          @NonNull final FieldUsage.Usage defValue) {

        if (DBDefinitions.isUsed(prefs, fieldId)) {
            final FieldUsage fieldUsage = FieldUsage.create(fieldId, nameStringId, prefs, defValue);
            mFieldUsages.put(fieldId, fieldUsage);
        }
    }

    /**
     * Add any related fields with the same setting.
     * <p>
     * We enforce a name (string id), although it's never displayed, for sanity/debug sake.
     *
     * @param primaryFieldId the field to check
     * @param relatedFieldId to add if the primary field is present
     * @param nameStringId   Field label string resource ID
     */
    private void addRelatedField(@NonNull final String primaryFieldId,
                                 @NonNull final String relatedFieldId,
                                 @StringRes final int nameStringId) {
        final FieldUsage primaryField = mFieldUsages.get(primaryFieldId);

        if (primaryField != null && primaryField.isWanted()) {
            final FieldUsage fu = primaryField.createRelatedField(relatedFieldId, nameStringId);
            mFieldUsages.put(relatedFieldId, fu);
        }
    }

    /**
     * Write current settings to the user preferences.
     *
     * @param context Current context
     */
    public void writePreferences(@NonNull final Context context) {
        final SharedPreferences.Editor ed =
                PreferenceManager.getDefaultSharedPreferences(context).edit();

        for (FieldUsage fieldUsage : mFieldUsages.values()) {
            fieldUsage.getUsage().write(ed, fieldUsage.fieldId);
        }
        ed.apply();
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     *
     * @param context Current context
     */
    public void resetPreferences(final Context context) {
        for (FieldUsage fieldUsage : mFieldUsages.values()) {
            fieldUsage.reset();
        }
        writePreferences(context);
    }

    /**
     * Start a search.
     *
     * @param context Current context
     *
     * @return {@code true} if a search was started.
     */
    public boolean startSearch(@NonNull final Context context) {
        // add related fields.
        // i.e. if we do the 'list-price' field, we'll also want its currency.
        addRelatedField(DBDefinitions.KEY_PRICE_LISTED,
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY, R.string.lbl_currency);

        for (int cIdx = 0; cIdx < 2; cIdx++) {
            addRelatedField(DBDefinitions.KEY_THUMBNAIL,
                            UniqueId.BKEY_FILE_SPEC[cIdx], R.string.lbl_cover);
        }

        if (mBookIds == null) {
            //update the complete library starting from the given id
            mCurrentBookId = mFromBookIdOnwards;
        }

        mCurrentProgressCounter = 0;

        try {
            mCurrentCursor = mDb.fetchBooks(mBookIds, mCurrentBookId);
            mCurrentCursorCount = mCurrentCursor.getCount();

        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }

        // kick off the first book
        return nextBook(context);
    }

    /**
     * @param context Current context
     *
     * @return {@code true} if a search was started.
     */
    private boolean nextBook(@NonNull final Context context) {

        try {
            final int idCol = mCurrentCursor.getColumnIndex(DBDefinitions.KEY_PK_ID);
            final int langCol = mCurrentCursor.getColumnIndex(DBDefinitions.KEY_LANGUAGE);

            // loop/skip until we start a search for a book.
            while (mCurrentCursor.moveToNext() && !mIsCancelled) {

                mCurrentProgressCounter++;

                // Copy the fields from the cursor and build a complete set of data
                // for this book.
                // This only needs to include data that we can fetch (so, for example,
                // bookshelves are ignored).
                mCurrentBookData = new Bundle();
                for (int i = 0; i < mCurrentCursor.getColumnCount(); i++) {
                    mCurrentBookData.putString(mCurrentCursor.getColumnName(i),
                                               mCurrentCursor.getString(i));
                }

                // always add the language to the ORIGINAL data if we have one,
                // so we can use it for the Locale details when processing the results.
                if (langCol > 0) {
                    String lang = mCurrentCursor.getString(langCol);
                    if (lang != null && !lang.isEmpty()) {
                        mCurrentBookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
                    }
                }

                // Get the book ID
                mCurrentBookId = mCurrentCursor.getLong(idCol);

                // Get the array data about the book
                mCurrentBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                        mDb.getAuthorsByBookId(mCurrentBookId));
                mCurrentBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                        mDb.getSeriesByBookId(mCurrentBookId));
                mCurrentBookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                                        mDb.getTocEntryByBook(mCurrentBookId));

                // Check which fields this book needs.
                mCurrentFieldsWanted = filter(context, mFieldUsages, mCurrentBookData);

                // Grab the searchable fields. Ideally we will have an ISBN but we may not.
                final String isbn = mCurrentBookData.getString(DBDefinitions.KEY_ISBN);
                final String title = mCurrentBookData.getString(DBDefinitions.KEY_TITLE);
                final String author = mCurrentBookData.getString(
                        DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

                if (!mCurrentFieldsWanted.isEmpty()) {
                    // remove all other criteria (this is CRUCIAL)
                    clearSearchText();
                    boolean canSearch = false;

                    if (isbn != null && !isbn.isEmpty()) {
                        setIsbnSearchText(isbn, true);
                        canSearch = true;
                    }

                    if (author != null && !author.isEmpty()
                        && title != null && !title.isEmpty()) {
                        setAuthorSearchText(author);
                        setTitleSearchText(title);
                        canSearch = true;
                    }

                    // Collect native ids we can use
                    final SparseArray<String> nativeIds = new SparseArray<>();
                    for (String key : DBDefinitions.NATIVE_ID_KEYS) {
                        // values can be Long and String, and it's a Bundle, need to get as Object
                        Object o = mCurrentBookData.get(key);
                        if (o != null) {
                            final String value = o.toString().trim();
                            if (!value.isEmpty() && !"0".equals(value)) {
                                nativeIds.put(SearchSites.getSiteIdFromDBDefinitions(key), value);
                            }
                        }
                    }
                    if (nativeIds.size() > 0) {
                        setNativeIdSearchText(nativeIds);
                        canSearch = true;
                    }

                    if (canSearch) {
                        // optional
                        final String publisher = mCurrentBookData
                                .getString(DBDefinitions.KEY_PUBLISHER);
                        if (publisher != null && !publisher.isEmpty()) {
                            setPublisherSearchText(publisher);
                        }

                        final boolean[] thumbs = new boolean[2];
                        for (int cIdx = 0; cIdx < 2; cIdx++) {
                            thumbs[cIdx] = mCurrentFieldsWanted
                                    .containsKey(UniqueId.BKEY_FILE_SPEC[cIdx]);
                        }
                        setFetchThumbnail(thumbs);

                        // Start searching
                        if (search(context)) {
                            // Update the progress base message.
                            if (title != null && !title.isEmpty()) {
                                setBaseMessage(title);
                            } else {
                                setBaseMessage(isbn);
                            }
                            return true;
                        }
                        // else if no search was started, fall through and loop to the next book.
                    }
                }

                // no data needed, or no search-data available.
                setBaseMessage(context.getString(R.string.progress_msg_skip_s, title));
            }
        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }

        postSearch(null);
        return false;
    }

    /**
     * @return {@code true} if a search was started.
     */
    public boolean processSearchResults(@NonNull final Context context,
                                        @NonNull final Bundle bookData) {
        if (!mIsCancelled && !bookData.isEmpty()) {
            // Filter the data to remove keys we don't care about
            final Collection<String> toRemove = new ArrayList<>();
            for (String key : bookData.keySet()) {
                FieldUsage fieldUsage = mCurrentFieldsWanted.get(key);
                if (fieldUsage == null || !fieldUsage.isWanted()) {
                    toRemove.add(key);
                }
            }

            for (String key : toRemove) {
                bookData.remove(key);
            }

            // For each field, process it according the usage.
            for (FieldUsage usage : mCurrentFieldsWanted.values()) {
                if (bookData.containsKey(usage.fieldId)) {
                    // Handle thumbnail specially
                    if (usage.fieldId.equals(UniqueId.BKEY_FILE_SPEC[0])) {
                        processSearchResultsCoverImage(context, bookData, usage, 0);
                    } else if (usage.fieldId.equals(UniqueId.BKEY_FILE_SPEC[1])) {
                        processSearchResultsCoverImage(context, bookData, usage, 1);
                    } else {
                        switch (usage.getUsage()) {
                            case CopyIfBlank:
                                if (hasField(mCurrentBookData, usage.fieldId)) {
                                    bookData.remove(usage.fieldId);
                                }
                                break;

                            case Append:
                                appendLists(usage.fieldId, mCurrentBookData, bookData);
                                break;

                            case Overwrite:
                            case Skip:
                                break;
                        }
                    }
                }
            }

            // Commit the new data
            if (!bookData.isEmpty()) {
                // Get the language, if there was one requested for updating.
                String bookLang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
                if (bookLang == null || bookLang.isEmpty()) {
                    // Otherwise add the original one.
                    bookLang = mCurrentBookData.getString(DBDefinitions.KEY_LANGUAGE);
                    if (bookLang != null && !bookLang.isEmpty()) {
                        bookData.putString(DBDefinitions.KEY_LANGUAGE, bookLang);
                    }
                }

                final Book book = new Book();
                book.putAll(bookData);
                // failures to update are ignored.
                mDb.updateBook(context, mCurrentBookId, book, 0);
            }
        }

        //update the counter, another one done.
        mSearchCoordinatorProgressMessage.setValue(new TaskListener.ProgressMessage(
                R.id.TASK_ID_UPDATE_FIELDS, null,
                mCurrentCursorCount, mCurrentProgressCounter, null));

        // On to the next book in the list.
        return nextBook(context);
    }

    private void processSearchResultsCoverImage(@NonNull final Context context,
                                                @NonNull final Bundle bookData,
                                                final FieldUsage usage,
                                                final int cIdx) {
        final String uuid = mCurrentBookData.getString(DBDefinitions.KEY_BOOK_UUID);
        Objects.requireNonNull(uuid, ErrorMsg.NULL_UUID);
        boolean copyThumb = false;
        switch (usage.getUsage()) {
            case CopyIfBlank:
                File file = AppDir.getCoverFile(context, uuid, cIdx);
                copyThumb = !file.exists() || file.length() == 0;
                break;

            case Overwrite:
                copyThumb = true;
                break;

            case Skip:
            case Append:
                break;
        }

        if (copyThumb) {
            final String fileSpec = bookData.getString(UniqueId.BKEY_FILE_SPEC[cIdx]);
            if (fileSpec != null) {
                final File downloadedFile = new File(fileSpec);
                final File destination = AppDir.getCoverFile(context, uuid, cIdx);
                FileUtils.rename(downloadedFile, destination);
            }
        }
    }

    /**
     * Cleanup up and report the final outcome.
     *
     * <ul>Callers:
     * <li>when we've not started a search (for whatever reason, including we're all done)</li>
     * <li>when an exception is thrown</li>
     * <li>when we're cancelled</li>
     * </ul>
     *
     * @param e (optional) exception
     */
    private void postSearch(@Nullable final Exception e) {
        if (mCurrentCursor != null) {
            mCurrentCursor.close();
        }

        // Tell the SearchCoordinator we're done and it should clean up.
        setBaseMessage(null);
        super.cancel(false);

        // Prepare the task result.
        final TaskListener.TaskStatus taskStatus;
        if (mIsCancelled) {
            taskStatus = TaskListener.TaskStatus.Cancelled;
        } else if (e != null) {
            taskStatus = TaskListener.TaskStatus.Failed;
        } else {
            taskStatus = TaskListener.TaskStatus.Success;
        }

        final Bundle data = new Bundle();
        data.putLong(BKEY_LAST_BOOK_ID, mCurrentBookId);

        // all books || a list of books || (single book && ) not cancelled
        if (mBookIds == null || mBookIds.size() > 1 || !mIsCancelled) {
            // One or more books were changed.
            // Technically speaking when doing a list of books, the task might have been
            // cancelled before the first book was done. We disregard this fringe case.
            data.putBoolean(UniqueId.BKEY_BOOK_MODIFIED, true);

            // if applicable, pass the first book for reposition the list on screen
            if (mBookIds != null && !mBookIds.isEmpty()) {
                data.putLong(DBDefinitions.KEY_PK_ID, mBookIds.get(0));
            }
        }

        final TaskListener.FinishMessage<Bundle> message = new TaskListener.FinishMessage<>(
                R.id.TASK_ID_UPDATE_FIELDS, taskStatus, data, e);
        // the last book id which was handled; can be used to restart the update.
        mFromBookIdOnwards = message.result.getLong(UpdateFieldsModel.BKEY_LAST_BOOK_ID);

        mTaskFinishedMessage.setValue(message);
    }


    /**
     * Filter the fields we want versus the fields we actually need for the given book data.
     *
     * @param context         Current context
     * @param requestedFields the FieldUsage map to clean up
     * @param bookData        Bundle with keys to filter on
     *
     * @return the filtered FieldUsage map
     */
    private Map<String, FieldUsage> filter(@NonNull final Context context,
                                           @NonNull final Map<String, FieldUsage> requestedFields,
                                           @NonNull final Bundle bookData) {

        final Map<String, FieldUsage> fieldUsages = new LinkedHashMap<>();
        for (FieldUsage usage : requestedFields.values()) {
            switch (usage.getUsage()) {
                case Skip:
                    // duh...
                    break;

                case Append:
                case Overwrite:
                    // Append + Overwrite: we always need to get the data
                    fieldUsages.put(usage.fieldId, usage);
                    break;

                case CopyIfBlank:
                    // Handle special cases first, 'default:' for the rest
                    if (usage.fieldId.equals(UniqueId.BKEY_FILE_SPEC[0])) {
                        filterCoverImage(context, bookData, fieldUsages, usage, 0);
                    } else if (usage.fieldId.equals(UniqueId.BKEY_FILE_SPEC[1])) {
                        filterCoverImage(context, bookData, fieldUsages, usage, 1);
                    } else {
                        switch (usage.fieldId) {
                            // We should never have a book without authors, but be paranoid
                            case UniqueId.BKEY_AUTHOR_ARRAY:
                            case UniqueId.BKEY_SERIES_ARRAY:
                            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                                if (bookData.containsKey(usage.fieldId)) {
                                    ArrayList list = bookData.getParcelableArrayList(usage.fieldId);
                                    if (list == null || list.isEmpty()) {
                                        fieldUsages.put(usage.fieldId, usage);
                                    }
                                }
                                break;

                            default:
                                // If the original was blank, add to list
                                String value = bookData.getString(usage.fieldId);
                                if (value == null || value.isEmpty()) {
                                    fieldUsages.put(usage.fieldId, usage);
                                }
                                break;
                        }
                    }
                    break;
            }
        }

        return fieldUsages;
    }

    private void filterCoverImage(@NonNull final Context context,
                                  @NonNull final Bundle bookData,
                                  final Map<String, FieldUsage> fieldUsages,
                                  final FieldUsage usage,
                                  final int cIdx) {
        // - If it's a thumbnail, then see if it's missing or empty.
        final String uuid = bookData.getString(DBDefinitions.KEY_BOOK_UUID);
        Objects.requireNonNull(uuid, ErrorMsg.NULL_UUID);
        final File file = AppDir.getCoverFile(context, uuid, cIdx);
        if (!file.exists() || file.length() == 0) {
            fieldUsages.put(usage.fieldId, usage);
        }
    }

    /**
     * Check if we already have this field in the original data.
     *
     * @param currentBookData to check
     * @param fieldId         to test for
     *
     * @return {@code true} if already present
     */
    private boolean hasField(@NonNull final Bundle currentBookData,
                             @NonNull final String fieldId) {
        switch (fieldId) {
            case UniqueId.BKEY_AUTHOR_ARRAY:
            case UniqueId.BKEY_SERIES_ARRAY:
            case UniqueId.BKEY_TOC_ENTRY_ARRAY:
                if (currentBookData.containsKey(fieldId)) {
                    ArrayList<Parcelable> list = currentBookData.getParcelableArrayList(fieldId);
                    if (list != null && !list.isEmpty()) {
                        return true;
                    }
                }
                break;

            default:
                // If the original was non-blank, erase from list
                String value = currentBookData.getString(fieldId);
                if (value != null && !value.isEmpty()) {
                    return true;
                }
                break;
        }

        return false;
    }

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param <T>         type of the ArrayList elements
     * @param key         for data
     * @param source      Bundle to read from
     * @param destination Bundle to read from, and to write the combined list to
     */
    private <T extends Parcelable> void appendLists(@NonNull final String key,
                                                    @NonNull final Bundle source,
                                                    @NonNull final Bundle destination) {
        // Get the list from the original, if it's present.
        ArrayList<T> destinationList = source.getParcelableArrayList(key);

        // Otherwise use an empty list
        if (destinationList == null) {
            destinationList = new ArrayList<>();
        }

        // Get the list from the new data, if it's present.
        final ArrayList<T> newDataList = destination.getParcelableArrayList(key);
        if (newDataList != null && !newDataList.isEmpty()) {
            // do the actual append by copying new data to the source list
            // if the latter does not already have the object.
            for (T item : newDataList) {
                if (!destinationList.contains(item)) {
                    destinationList.add(item);
                }
            }
        }

        // Save the combined list to the new data bundle.
        destination.putParcelableArrayList(key, destinationList);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        mIsCancelled = true;
        postSearch(null);
        return true;
    }
}

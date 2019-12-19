/*
 * @Copyright 2019 HardBackNutter
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
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

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
    private Map<String, FieldUsage> mCurrentBookFieldUsages;

    private int mCurrentProgressCounter;
    private int mCurrentCursorCount;
    private BookCursor mCurrentCursor;

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

            mDb = new DAO();
            // use global preference.
            setSiteList(SiteList.getList(context, SiteList.Type.Data));

            if (args != null) {
                //noinspection unchecked
                mBookIds = (ArrayList<Long>) args.getSerializable(UniqueId.BKEY_ID_LIST);
            }

            initFields();
        }
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private void initFields() {

        addListField(UniqueId.BKEY_AUTHOR_ARRAY, R.string.lbl_author, DBDefinitions.KEY_FK_AUTHOR);

        addField(DBDefinitions.KEY_TITLE, R.string.lbl_title, CopyIfBlank);
        addField(DBDefinitions.KEY_ISBN, R.string.lbl_isbn, CopyIfBlank);
        addField(UniqueId.BKEY_THUMBNAIL, R.string.lbl_cover, CopyIfBlank);

        addListField(UniqueId.BKEY_SERIES_ARRAY, R.string.lbl_series,
                     DBDefinitions.KEY_SERIES_TITLE);

        addListField(UniqueId.BKEY_TOC_ENTRY_ARRAY, R.string.lbl_table_of_content,
                     DBDefinitions.KEY_TOC_BITMASK);

        addListField(UniqueId.BKEY_PUBLISHER_ARRAY, R.string.lbl_publisher,
                     DBDefinitions.KEY_PUBLISHER);

        addField(DBDefinitions.KEY_PRINT_RUN, R.string.lbl_print_run, CopyIfBlank);
        addField(DBDefinitions.KEY_DATE_PUBLISHED, R.string.lbl_date_published, CopyIfBlank);
        addField(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, R.string.lbl_first_publication,
                 CopyIfBlank);

        // list price has related DBDefinitions.KEY_PRICE_LISTED
        addField(DBDefinitions.KEY_PRICE_LISTED, R.string.lbl_price_listed, CopyIfBlank);

        addField(DBDefinitions.KEY_DESCRIPTION, R.string.lbl_description, CopyIfBlank);

        addField(DBDefinitions.KEY_PAGES, R.string.lbl_pages, CopyIfBlank);
        addField(DBDefinitions.KEY_FORMAT, R.string.lbl_format, CopyIfBlank);
        addField(DBDefinitions.KEY_COLOR, R.string.lbl_color, CopyIfBlank);
        addField(DBDefinitions.KEY_LANGUAGE, R.string.lbl_language, CopyIfBlank);
        addField(DBDefinitions.KEY_GENRE, R.string.lbl_genre, CopyIfBlank);

        //NEWTHINGS: add new site specific ID: add a field
        addField(DBDefinitions.KEY_EID_ISFDB, R.string.isfdb, Overwrite);
        addField(DBDefinitions.KEY_EID_GOODREADS_BOOK, R.string.goodreads, Overwrite);
        addField(DBDefinitions.KEY_EID_LIBRARY_THING, R.string.library_thing, Overwrite);
        addField(DBDefinitions.KEY_EID_OPEN_LIBRARY, R.string.open_library, Overwrite);
        addField(DBDefinitions.KEY_EID_STRIP_INFO_BE, R.string.stripinfo, Overwrite);
    }

    @NonNull
    public Collection<FieldUsage> getFieldUsages() {
        return mFieldUsages.values();
    }

    @Nullable
    public FieldUsage getFieldUsage(@NonNull final String key) {
        return mFieldUsages.get(key);
    }

    public void putFieldUsage(@NonNull final String key,
                              @NonNull final FieldUsage fieldUsage) {
        mFieldUsages.put(key, fieldUsage);
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
     * Add any related fields with the same setting.
     * <p>
     * We enforce a name (string id), although it's never displayed, for sanity/debug sake.
     *
     * @param primaryFieldId the field to check
     * @param relatedFieldId to add if the primary field is present
     * @param nameStringId   Field label string resource ID
     */
    @SuppressWarnings("SameParameterValue")
    private void addRelatedField(@NonNull final String primaryFieldId,
                                 @NonNull final String relatedFieldId,
                                 @StringRes final int nameStringId) {
        FieldUsage primaryField = mFieldUsages.get(primaryFieldId);
        if (primaryField != null && primaryField.isWanted()) {
            FieldUsage fu = new FieldUsage(relatedFieldId, nameStringId, primaryField);
            mFieldUsages.put(relatedFieldId, fu);
        }
    }

    /**
     * Add a FieldUsage for a <strong>list</strong> field if it has not been hidden by the user.
     * <p>
     * The default usage for a list field is {@link FieldUsage.Usage#Append}.
     *
     * @param fieldId      List-field name to use in FieldUsages
     * @param nameStringId Field label string resource ID
     * @param visField     Field name to check for visibility.
     */
    private void addListField(@NonNull final String fieldId,
                              @StringRes final int nameStringId,
                              @NonNull final String visField) {

        if (App.isUsed(visField)) {
            FieldUsage fu = new FieldUsage(fieldId, nameStringId, FieldUsage.Usage.Append, true);
            mFieldUsages.put(fieldId, fu);
        }
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param fieldId      Field name to use in FieldUsages + check for visibility
     * @param nameStringId Field label string resource ID
     * @param defaultUsage default Usage for this field
     */
    private void addField(@NonNull final String fieldId,
                          @StringRes final int nameStringId,
                          @NonNull final FieldUsage.Usage defaultUsage) {

        if (App.isUsed(fieldId)) {
            FieldUsage fu = new FieldUsage(fieldId, nameStringId, defaultUsage, false);
            putFieldUsage(fieldId, fu);
        }
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
            addRelatedField(UniqueId.BKEY_THUMBNAIL,
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
            String isbn;
            String title;
            String author;

            boolean skip;
            do {
                skip = false;

                if (!mCurrentCursor.moveToNext() || mIsCancelled) {
                    postSearch(null);
                    return false;
                }

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
                int langCol = mCurrentCursor.getColumnIndex(DBDefinitions.KEY_LANGUAGE);
                if (langCol > 0) {
                    String lang = mCurrentCursor.getString(langCol);
                    if (lang != null && !lang.isEmpty()) {
                        mCurrentBookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
                    }
                }

                // Get the book ID
                mCurrentBookId = mCurrentCursor.getId();

                // Get the array data about the book
                mCurrentBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                        mDb.getAuthorsByBookId(mCurrentBookId));
                mCurrentBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                        mDb.getSeriesByBookId(mCurrentBookId));
                mCurrentBookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                                        mDb.getTocEntryByBook(mCurrentBookId));

                // Grab the searchable fields. Ideally we will have an ISBN but we may not.
                // Make sure the searchable fields are not NULL
                isbn = mCurrentBookData.getString(DBDefinitions.KEY_ISBN, "");
                title = mCurrentBookData.getString(DBDefinitions.KEY_TITLE, "");
                author = mCurrentBookData
                        .getString(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST, "");

                // Check which fields this book needs.
                mCurrentBookFieldUsages = filter(context, mFieldUsages, mCurrentBookData);
                // if no data required, skip to next book
                if (mCurrentBookFieldUsages.isEmpty()
                    || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    setBaseMessage(context.getString(R.string.progress_msg_skip_title, title));
                    skip = true;
                }

            } while (skip);


            // at this point we know we want a search,update the progress base message.
            if (!title.isEmpty()) {
                setBaseMessage(title);
            } else {
                setBaseMessage(isbn);
            }

            boolean wantCoverImage =
                    mCurrentBookFieldUsages.containsKey(UniqueId.BKEY_FILE_SPEC[0])
                    || mCurrentBookFieldUsages.containsKey(UniqueId.BKEY_FILE_SPEC[1]);
            setFetchThumbnail(wantCoverImage);

            // theoretically generic codes could be allowed, but this process is
            // not under strict user-control, so let's be paranoid.
            setIsbnSearchText(isbn, true);
            setAuthorSearchText(author);
            setTitleSearchText(title);
            // optional
            String publisher = mCurrentBookData.getString(DBDefinitions.KEY_PUBLISHER, "");
            setPublisherSearchText(publisher);

            // Start searching
            return searchByText(context);

        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }
    }

    /**
     * @return {@code true} if a search was started.
     */
    public boolean processSearchResults(@NonNull final Context context,
                                        @NonNull final Bundle bookData) {
        if (!mIsCancelled && !bookData.isEmpty()) {
            // Filter the data to remove keys we don't care about
            Collection<String> toRemove = new ArrayList<>();
            for (String key : bookData.keySet()) {
                //noinspection ConstantConditions
                if (!mCurrentBookFieldUsages.containsKey(key)
                    || !mCurrentBookFieldUsages.get(key).isWanted()) {
                    toRemove.add(key);
                }
            }

            for (String key : toRemove) {
                bookData.remove(key);
            }

            // For each field, process it according the usage.
            for (FieldUsage usage : mCurrentBookFieldUsages.values()) {
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

                mDb.updateBook(context, mCurrentBookId, new Book(bookData), 0);
            }
        }

        //update the counter, another one done.
        mSearchCoordinatorProgressMessage.setValue(new TaskListener.ProgressMessage(
                R.id.TASK_ID_UPDATE_FIELDS, mCurrentCursorCount, mCurrentProgressCounter, null));

        // On to the next book in the list.
        return nextBook(context);
    }

    private void processSearchResultsCoverImage(@NonNull final Context context,
                                                @NonNull final Bundle bookData,
                                                final FieldUsage usage,
                                                final int cIdx) {
        String uuid = mCurrentBookData.getString(DBDefinitions.KEY_BOOK_UUID);
        boolean copyThumb = false;
        switch (usage.getUsage()) {
            case CopyIfBlank:
                //noinspection ConstantConditions
                File file = StorageUtils.getCoverFileForUuid(context, uuid, cIdx);
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
            String fileSpec = bookData.getString(UniqueId.BKEY_FILE_SPEC[cIdx]);
            if (fileSpec != null) {
                File downloadedFile = new File(fileSpec);
                //noinspection ConstantConditions
                File destination = StorageUtils.getCoverFileForUuid(context, uuid, cIdx);
                StorageUtils.renameFile(downloadedFile, destination);
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
        TaskListener.TaskStatus taskStatus;
        if (mIsCancelled) {
            taskStatus = TaskListener.TaskStatus.Cancelled;
        } else if (e != null) {
            taskStatus = TaskListener.TaskStatus.Failed;
        } else {
            taskStatus = TaskListener.TaskStatus.Success;
        }

        Bundle data = new Bundle();
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

        TaskListener.FinishMessage<Bundle> message = new TaskListener.FinishMessage<>(
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
     * @param bookData        to filter on
     *
     * @return the filtered FieldUsage map
     */
    private Map<String, FieldUsage> filter(@NonNull final Context context,
                                           @NonNull final Map<String, FieldUsage> requestedFields,
                                           @NonNull final Bundle bookData) {

        Map<String, FieldUsage> fieldUsages = new LinkedHashMap<>();
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
        String uuid = bookData.getString(DBDefinitions.KEY_BOOK_UUID);
        //noinspection ConstantConditions
        File file = StorageUtils.getCoverFileForUuid(context, uuid, cIdx);
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
        ArrayList<T> newDataList = destination.getParcelableArrayList(key);
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

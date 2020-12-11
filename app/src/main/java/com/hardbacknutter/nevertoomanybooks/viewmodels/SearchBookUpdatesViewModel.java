/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

public class SearchBookUpdatesViewModel
        extends SearchCoordinator {

    /** Log tag. */
    private static final String TAG = "SearchBookUpdatesViewModel";
    private static final String BKEY_LAST_BOOK_ID = TAG + ":lastId";

    /** which fields to update and how. */
    @NonNull
    private final Map<String, FieldUsage> mFields = new LinkedHashMap<>();

    private final MutableLiveData<FinishedMessage<Bundle>> mListFinished = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Exception>> mListFailed = new MutableLiveData<>();

    /**
     * Current and original book data.
     * The object gets cleared and reused for each iteration of the loop.
     */
    private final Book mCurrentBook = new Book();
    /** Database Access. */
    private DAO mDb;
    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private ArrayList<Long> mBookIdList;
    /** Allows restarting an update task from the given book id onwards. 0 for all. */
    private long mFromBookIdOnwards;
    /** Indicates the user has requested a cancel. Up to the subclass to decide what to do. */
    private boolean mIsCancelled;
    /** Tracks the current book ID. */
    private long mCurrentBookId;
    /** The (subset) of fields relevant to the current book. */
    private Map<String, FieldUsage> mCurrentFieldsWanted;

    private int mCurrentProgressCounter;
    private int mCurrentCursorCount;
    private Cursor mCurrentCursor;

    /** Observable. */
    @NonNull
    public MutableLiveData<FinishedMessage<Bundle>> onAllDone() {
        return mListFinished;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<FinishedMessage<Exception>> onCatastrophe() {
        return mListFailed;
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

            if (args != null) {
                //noinspection unchecked
                mBookIdList = (ArrayList<Long>) args.getSerializable(Book.BKEY_BOOK_ID_LIST);
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

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);

        addCoverField(global, R.string.lbl_cover_front, 0);
        addCoverField(global, R.string.lbl_cover_back, 1);

        addField(global, DBDefinitions.KEY_TITLE, R.string.lbl_title, CopyIfBlank);
        addField(global, DBDefinitions.KEY_ISBN, R.string.lbl_isbn, CopyIfBlank);
        addListField(global, Book.BKEY_AUTHOR_LIST, R.string.lbl_authors,
                     DBDefinitions.KEY_FK_AUTHOR);
        addListField(global, Book.BKEY_SERIES_LIST, R.string.lbl_series_multiple,
                     DBDefinitions.KEY_SERIES_TITLE);
        addField(global, DBDefinitions.KEY_DESCRIPTION, R.string.lbl_description,
                 CopyIfBlank);

        addListField(global, Book.BKEY_TOC_LIST, R.string.lbl_table_of_content,
                     DBDefinitions.KEY_TOC_BITMASK);

        addListField(global, Book.BKEY_PUBLISHER_LIST, R.string.lbl_publishers,
                     DBDefinitions.KEY_PUBLISHER_NAME);
        addField(global, DBDefinitions.KEY_PRINT_RUN, R.string.lbl_print_run,
                 CopyIfBlank);
        addField(global, DBDefinitions.KEY_DATE_PUBLISHED, R.string.lbl_date_published,
                 CopyIfBlank);
        addField(global, DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                 R.string.lbl_first_publication,
                 CopyIfBlank);

        // list price has related DBDefinitions.KEY_PRICE_LISTED
        addField(global, DBDefinitions.KEY_PRICE_LISTED, R.string.lbl_price_listed,
                 CopyIfBlank);

        addField(global, DBDefinitions.KEY_PAGES, R.string.lbl_pages, CopyIfBlank);
        addField(global, DBDefinitions.KEY_FORMAT, R.string.lbl_format, CopyIfBlank);
        addField(global, DBDefinitions.KEY_COLOR, R.string.lbl_color, CopyIfBlank);
        addField(global, DBDefinitions.KEY_LANGUAGE, R.string.lbl_language, CopyIfBlank);
        addField(global, DBDefinitions.KEY_GENRE, R.string.lbl_genre, CopyIfBlank);

        for (final SearchEngineRegistry.Config config : SearchEngineRegistry
                .getInstance().getAll()) {
            final Domain domain = config.getExternalIdDomain();
            if (domain != null) {
                addField(global, domain.getName(), config.getNameResId(), Overwrite);
            }
        }
    }

    @NonNull
    public Collection<FieldUsage> getFieldUsages() {
        return mFields.values();
    }

    /**
     * Whether the user needs to be warned about lengthy download of covers.
     *
     * @return {@code true} if a dialog should be shown
     */
    public boolean isShowWarningAboutCovers() {

        // Less than (arbitrary) 10 books, don't check/warn needed.
        if (mBookIdList != null && mBookIdList.size() < 10) {
            return false;
        }

        // More than 10 books, check if the user actually wants covers
        final FieldUsage covers = mFields.get(DBDefinitions.PREFS_IS_USED_COVER + ".0");
        return covers != null && covers.getUsage() == FieldUsage.Usage.Overwrite;
    }

    /**
     * Update the fields usage flag.
     * Does nothing if the field ws not actually added before.
     *
     * @param key   field to update
     * @param usage to set
     */
    public void updateFieldUsage(@NonNull final String key,
                                 @NonNull final FieldUsage.Usage usage) {
        final FieldUsage fieldUsage = mFields.get(key);
        if (fieldUsage != null) {
            fieldUsage.setUsage(usage);
        }
    }

    /**
     * Allows to set the 'lowest' Book id to start from. See {@link DAO#fetchBooks(long)}
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
     * @param global    Global preferences
     * @param fieldId   List-field name to use in FieldUsages
     * @param nameResId Field label resource id
     * @param key       Field name to use for preferences.
     */
    private void addListField(@NonNull final SharedPreferences global,
                              @NonNull final String fieldId,
                              @StringRes final int nameResId,
                              @NonNull final String key) {

        if (DBDefinitions.isUsed(global, key)) {
            mFields.put(fieldId, FieldUsage.createListField(fieldId, nameResId, global));
        }
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param global    Global preferences
     * @param nameResId Field label resource id
     * @param cIdx      0..n image index
     */
    private void addCoverField(@NonNull final SharedPreferences global,
                               @StringRes final int nameResId,
                               @IntRange(from = 0, to = 1) final int cIdx) {

        if (DBDefinitions.isCoverUsed(global, cIdx)) {
            final String fieldId = DBDefinitions.PREFS_IS_USED_COVER + "." + cIdx;
            mFields.put(fieldId, FieldUsage.create(fieldId, nameResId, global, CopyIfBlank));
        }
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param global    Global preferences
     * @param fieldId   Field name to use in FieldUsages, and as key for preferences.
     * @param nameResId Field label resource id
     * @param defValue  default Usage for this field
     */
    private void addField(@NonNull final SharedPreferences global,
                          @NonNull final String fieldId,
                          @StringRes final int nameResId,
                          @NonNull final FieldUsage.Usage defValue) {

        if (DBDefinitions.isUsed(global, fieldId)) {
            mFields.put(fieldId, FieldUsage.create(fieldId, nameResId, global, defValue));
        }
    }

    /**
     * Add any related fields with the same setting.
     * <p>
     * We enforce a name (string id), although it's never displayed, for sanity/debug sake.
     *
     * @param primaryFieldId the field to check
     * @param relatedFieldId to add if the primary field is present
     * @param nameResId      Field label resource id (not used)
     */
    private void addRelatedField(@NonNull final String primaryFieldId,
                                 @NonNull final String relatedFieldId,
                                 @StringRes final int nameResId) {
        final FieldUsage primaryField = mFields.get(primaryFieldId);

        if (primaryField != null && primaryField.isWanted()) {
            final FieldUsage fu = primaryField.createRelatedField(relatedFieldId, nameResId);
            mFields.put(relatedFieldId, fu);
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

        for (final FieldUsage fieldUsage : mFields.values()) {
            fieldUsage.getUsage().write(ed, fieldUsage.fieldId);
        }
        ed.apply();
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     *
     * @param context Current context
     */
    public void resetPreferences(@NonNull final Context context) {
        for (final FieldUsage fieldUsage : mFields.values()) {
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
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                        R.string.lbl_currency);

        addRelatedField(DBDefinitions.PREFS_IS_USED_COVER + ".0",
                        Book.BKEY_TMP_FILE_SPEC[0],
                        R.string.lbl_cover_front);
        addRelatedField(DBDefinitions.PREFS_IS_USED_COVER + ".1",
                        Book.BKEY_TMP_FILE_SPEC[1],
                        R.string.lbl_cover_back);

        mCurrentProgressCounter = 0;

        try {
            if (mBookIdList == null || mBookIdList.isEmpty()) {
                mCurrentCursor = mDb.fetchBooks(mFromBookIdOnwards);
            } else {
                mCurrentCursor = mDb.fetchBooks(mBookIdList);
            }
            mCurrentCursorCount = mCurrentCursor.getCount();

        } catch (@NonNull final Exception e) {
            postSearch(e);
            return false;
        }

        // kick off the first book
        return nextBook(context);
    }

    /**
     * Move the cursor forward and update the next book.
     *
     * @param context Current context
     *
     * @return {@code true} if a search was started.
     */
    private boolean nextBook(@NonNull final Context context) {

        try {
            final int idCol = mCurrentCursor.getColumnIndex(DBDefinitions.KEY_PK_ID);

            // loop/skip until we start a search for a book.
            while (mCurrentCursor.moveToNext() && !mIsCancelled) {

                mCurrentProgressCounter++;

                //read the book ID
                mCurrentBookId = mCurrentCursor.getLong(idCol);

                // and populate the actual book based on the cursor data
                mCurrentBook.load(mCurrentBookId, mCurrentCursor, mDb);

                // Check which fields this book needs.
                mCurrentFieldsWanted = filter(context, mFields);

                final String title = mCurrentBook.getString(DBDefinitions.KEY_TITLE);

                if (!mCurrentFieldsWanted.isEmpty()) {
                    // remove all other criteria (this is CRUCIAL)
                    clearSearchText();
                    boolean canSearch = false;

                    final String isbnStr = mCurrentBook.getString(DBDefinitions.KEY_ISBN);
                    if (!isbnStr.isEmpty()) {
                        setIsbnSearchText(isbnStr, true);
                        canSearch = true;
                    }

                    final Author author = mCurrentBook.getPrimaryAuthor();
                    if (author != null) {
                        final String authorName = author.getFormattedName(true);
                        if (!authorName.isEmpty() && !title.isEmpty()) {
                            setAuthorSearchText(authorName);
                            setTitleSearchText(title);
                            canSearch = true;
                        }
                    }

                    // Collect external ID's we can use
                    final SparseArray<String> externalIds = new SparseArray<>();
                    for (final SearchEngineRegistry.Config config : SearchEngineRegistry
                            .getInstance().getAll()) {
                        final Domain domain = config.getExternalIdDomain();
                        if (domain != null) {
                            final String value = mCurrentBook.getString(domain.getName());
                            if (!value.isEmpty() && !"0".equals(value)) {
                                externalIds.put(config.getEngineId(), value);
                            }
                        }
                    }

                    if (externalIds.size() > 0) {
                        setExternalIds(externalIds);
                        canSearch = true;
                    }

                    if (canSearch) {
                        // optional: whether this is used will depend on SearchEngine/Preferences
                        final Publisher publisher = mCurrentBook.getPrimaryPublisher();
                        if (publisher != null) {
                            final String publisherName = publisher.getName();
                            if (!publisherName.isEmpty()) {
                                setPublisherSearchText(publisherName);
                            }
                        }

                        // optional: whether this is used will depend on SearchEngine/Preferences
                        final boolean[] thumbs = new boolean[2];
                        for (int cIdx = 0; cIdx < 2; cIdx++) {
                            thumbs[cIdx] = mCurrentFieldsWanted
                                    .containsKey(Book.BKEY_TMP_FILE_SPEC[cIdx]);
                        }
                        setFetchThumbnail(thumbs);

                        // Start searching
                        if (search(context)) {
                            // Update the progress base message.
                            if (!title.isEmpty()) {
                                setBaseMessage(title);
                            } else {
                                setBaseMessage(isbnStr);
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
     * Process the search-result data.
     *
     * @param context  Current context
     * @param bookData result-data to process
     *
     * @return {@code true} if a search was started.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean processSearchResults(@NonNull final Context context,
                                        @Nullable final Bundle bookData) {

        if (!mIsCancelled && bookData != null && !bookData.isEmpty()) {

            // Filter the data to remove keys we don't care about
            final Collection<String> toRemove = new ArrayList<>();
            for (final String key : bookData.keySet()) {
                final FieldUsage fieldUsage = mCurrentFieldsWanted.get(key);
                if (fieldUsage == null || !fieldUsage.isWanted()) {
                    toRemove.add(key);
                }
            }
            for (final String key : toRemove) {
                bookData.remove(key);
            }

            final Locale bookLocale = mCurrentBook.getLocale(context);

            // For each field, process it according the usage.
            mCurrentFieldsWanted
                    .values()
                    .stream()
                    .filter(usage -> bookData.containsKey(usage.fieldId))
                    .forEach(usage -> {
                        // Handle thumbnail specially
                        if (usage.fieldId.equals(Book.BKEY_TMP_FILE_SPEC[0])) {
                            processSearchResultsCoverImage(context, bookData, usage, 0);
                        } else if (usage.fieldId.equals(Book.BKEY_TMP_FILE_SPEC[1])) {
                            processSearchResultsCoverImage(context, bookData, usage, 1);
                        } else {
                            switch (usage.getUsage()) {
                                case CopyIfBlank:
                                    // remove unneeded fields from the new data
                                    if (hasField(usage.fieldId)) {
                                        bookData.remove(usage.fieldId);
                                    }
                                    break;

                                case Append:
                                    appendLists(context, usage.fieldId, bookLocale, bookData);
                                    break;

                                case Overwrite:
                                case Skip:
                                    break;
                            }
                        }
                    });

            // Commit the new data
            if (!bookData.isEmpty()) {
                // Get the language, if there was one requested for updating.
                String bookLang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
                if (bookLang == null || bookLang.isEmpty()) {
                    // Otherwise add the original one.
                    bookLang = mCurrentBook.getString(DBDefinitions.KEY_LANGUAGE);
                    if (!bookLang.isEmpty()) {
                        bookData.putString(DBDefinitions.KEY_LANGUAGE, bookLang);
                    }
                }

                //IMPORTANT: note how we construct a NEW BOOK, with the DELTA-data which
                // we want to commit to the existing book.
                final Book delta = Book.from(bookData);
                delta.putLong(DBDefinitions.KEY_PK_ID, mCurrentBookId);
                try {
                    mDb.update(context, delta, 0);
                } catch (@NonNull final DAO.DaoWriteException e) {
                    // ignore, but log it.
                    Logger.error(context, TAG, e);
                }
            }
        }

        //update the counter, another one done.
        mSearchCoordinatorProgress.setValue(new ProgressMessage(
                R.id.TASK_ID_UPDATE_FIELDS, null,
                mCurrentProgressCounter, mCurrentCursorCount, null
        ));

        // On to the next book in the list.
        return nextBook(context);
    }

    private void processSearchResultsCoverImage(@NonNull final Context context,
                                                @NonNull final Bundle bookData,
                                                @NonNull final FieldUsage usage,
                                                @IntRange(from = 0, to = 1) final int cIdx) {
        boolean copyThumb = false;
        // check if we already have an image, and what we should do with the new image
        switch (usage.getUsage()) {
            case CopyIfBlank:
                final File file = mCurrentBook.getUuidCoverFile(context, cIdx);
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
            final String fileSpec = bookData.getString(Book.BKEY_TMP_FILE_SPEC[cIdx]);
            if (fileSpec != null) {
                final File downloadedFile = new File(fileSpec);
                final File destination = mCurrentBook.getUuidCoverFileOrNew(context, cIdx);
                try {
                    FileUtils.rename(downloadedFile, destination);
                } catch (@NonNull final IOException e) {
                    final String uuid = mCurrentBook.getString(DBDefinitions.KEY_BOOK_UUID);
                    Logger.error(context, TAG, e,
                                 "processSearchResultsCoverImage|uuid=" + uuid + "|cIdx=" + cIdx);
                }
            }
            bookData.remove(Book.BKEY_TMP_FILE_SPEC[cIdx]);
        }
    }

    /**
     * Cleanup up and report the final outcome.
     *
     * <ul>Callers:
     *      <li>when we've not started a search (for whatever reason, including we're all done)</li>
     *      <li>when an exception is thrown</li>
     *      <li>when we're cancelled</li>
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

        // the last book id which was handled; can be used to restart the update.
        mFromBookIdOnwards = mCurrentBookId;

        final Bundle results = new Bundle();
        results.putLong(BKEY_LAST_BOOK_ID, mFromBookIdOnwards);

        // all books || a list of books || (single book && ) not cancelled
        if (mBookIdList == null || mBookIdList.size() > 1 || !mIsCancelled) {
            // One or more books were changed.
            // Technically speaking when doing a list of books, the task might have been
            // cancelled before the first book was done. We disregard this fringe case.
            results.putBoolean(Entity.BKEY_DATA_MODIFIED, true);

            // if applicable, pass the first book for repositioning the list on screen
            if (mBookIdList != null && !mBookIdList.isEmpty()) {
                results.putLong(DBDefinitions.KEY_PK_ID, mBookIdList.get(0));
            }
        }

        if (e != null) {
            Logger.error(App.getAppContext(), TAG, e);
            final FinishedMessage<Exception> message =
                    new FinishedMessage<>(R.id.TASK_ID_UPDATE_FIELDS, e);
            mListFailed.setValue(message);

        } else {
            final FinishedMessage<Bundle> message =
                    new FinishedMessage<>(R.id.TASK_ID_UPDATE_FIELDS, results);
            if (mIsCancelled) {
                mSearchCoordinatorCancelled.setValue(message);
            } else {
                mListFinished.setValue(message);
            }
        }
    }

    /**
     * Filter the fields we want versus the fields we actually need for the given book data.
     *
     * @param context         Current context
     * @param requestedFields the FieldUsage map to clean up
     *
     * @return the filtered FieldUsage map
     */
    private Map<String, FieldUsage> filter(@NonNull final Context context,
                                           @NonNull final Map<String, FieldUsage> requestedFields) {

        final Map<String, FieldUsage> fieldUsages = new LinkedHashMap<>();
        for (final FieldUsage usage : requestedFields.values()) {
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
                    if (usage.fieldId.equals(Book.BKEY_TMP_FILE_SPEC[0])) {
                        filterCoverImage(context, fieldUsages, usage, 0);
                    } else if (usage.fieldId.equals(Book.BKEY_TMP_FILE_SPEC[1])) {
                        filterCoverImage(context, fieldUsages, usage, 1);
                    } else {
                        switch (usage.fieldId) {
                            // We should never have a book without authors, but be paranoid
                            case Book.BKEY_AUTHOR_LIST:
                            case Book.BKEY_SERIES_LIST:
                            case Book.BKEY_PUBLISHER_LIST:
                            case Book.BKEY_TOC_LIST:
                                if (mCurrentBook.contains(usage.fieldId)) {
                                    final ArrayList<Parcelable> list =
                                            mCurrentBook.getParcelableArrayList(usage.fieldId);
                                    if (list.isEmpty()) {
                                        fieldUsages.put(usage.fieldId, usage);
                                    }
                                }
                                break;

                            default:
                                // If the original was blank, add to list
                                final String value = mCurrentBook.getString(usage.fieldId);
                                if (value.isEmpty()) {
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
                                  @NonNull final Map<String, FieldUsage> fieldUsages,
                                  @NonNull final FieldUsage usage,
                                  @IntRange(from = 0, to = 1) final int cIdx) {
        // - If it's a thumbnail, then see if it's missing or empty.
        final File file = mCurrentBook.getUuidCoverFile(context, cIdx);
        if (file == null || file.length() == 0) {
            fieldUsages.put(usage.fieldId, usage);
        }
    }

    /**
     * Check if we already have this field in the original data.
     *
     * @param fieldId to test for
     *
     * @return {@code true} if already present
     */
    private boolean hasField(@NonNull final String fieldId) {
        switch (fieldId) {
            case Book.BKEY_AUTHOR_LIST:
            case Book.BKEY_SERIES_LIST:
            case Book.BKEY_PUBLISHER_LIST:
            case Book.BKEY_TOC_LIST:
                if (mCurrentBook.contains(fieldId)) {
                    if (!mCurrentBook.getParcelableArrayList(fieldId).isEmpty()) {
                        return true;
                    }
                }
                break;

            default:
                // If the original was non-blank, erase from list
                final Object o = mCurrentBook.get(fieldId);
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

    /**
     * Combines two ParcelableArrayList's, weeding out duplicates.
     *
     * @param context    Current context
     * @param key        for data
     * @param bookLocale to use
     * @param bookData   Bundle to update
     */
    private void appendLists(@NonNull final Context context,
                             @NonNull final String key,
                             @NonNull final Locale bookLocale,
                             @NonNull final Bundle bookData) {
        switch (key) {
            case Book.BKEY_AUTHOR_LIST: {
                final ArrayList<Author> list = bookData.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(mCurrentBook.getParcelableArrayList(key));
                    Author.pruneList(list, context, mDb, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_SERIES_LIST: {
                final ArrayList<Series> list = bookData.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(mCurrentBook.getParcelableArrayList(key));
                    Series.pruneList(list, context, mDb, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_PUBLISHER_LIST: {
                final ArrayList<Publisher> list = bookData.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(mCurrentBook.getParcelableArrayList(key));
                    Publisher.pruneList(list, context, mDb, false, bookLocale);
                }
                break;
            }
            case Book.BKEY_TOC_LIST: {
                final ArrayList<TocEntry> list = bookData.getParcelableArrayList(key);
                if (list != null && !list.isEmpty()) {
                    list.addAll(mCurrentBook.getParcelableArrayList(key));
                    TocEntry.pruneList(list, context, mDb, false, bookLocale);
                }
                break;
            }
            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        mIsCancelled = true;
        postSearch(null);
        return true;
    }
}

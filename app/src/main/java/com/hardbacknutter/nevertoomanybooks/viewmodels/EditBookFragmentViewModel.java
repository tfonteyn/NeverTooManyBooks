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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;

public class EditBookFragmentViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Log tag. */
    private static final String TAG = "EditBookFragmentVM";

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    /** The fields collection handled in this model. The key is the fragment tag. */
    private final Map<String, Fields> mFieldsMap = new HashMap<>();
    /** The key is the fragment tag. */
    private final Collection<String> mFragmentsWithUnfinishedEdits = new HashSet<>();
    private final MutableLiveData<ArrayList<Author>> mAuthorList = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Series>> mSeriesList = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Publisher>> mPublisherList = new MutableLiveData<>();

    /** <strong>Optionally</strong> passed in via the arguments. */
    @Nullable
    private ListStyle mStyle;
    /**
     * The Book this model represents. The only time this can be {@code null}
     * is when this model is just initialized, or when the Book was deleted.
     */
    private Book mBook;

    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     */
    @Nullable
    private List<String> mGenres;
    /** Field drop down list. */
    @Nullable
    private List<String> mLocations;
    /** Field drop down list. */
    @Nullable
    private List<String> mFormats;
    /** Field drop down list. */
    @Nullable
    private List<String> mColors;
    /** Field drop down list. */
    @Nullable
    private List<String> mLanguagesCodes;
    /** Field drop down list. */
    @Nullable
    private List<String> mPricePaidCurrencies;
    /** Field drop down list. */
    @Nullable
    private List<String> mListPriceCurrencies;
    /** Field drop down list. */
    @Nullable
    private List<String> mAuthorNamesFormatted;
    /** Field drop down list. */
    @Nullable
    private List<String> mAuthorFamilyNames;
    /** Field drop down list. */
    @Nullable
    private List<String> mAuthorGivenNames;
    /** Field drop down list. */
    @Nullable
    private List<String> mPublisherNames;
    /** Field drop down list. */
    @Nullable
    private List<String> mSeriesTitles;

    /** The currently displayed tab. */
    private int mCurrentTab;

    private DateParser mDateParser;

    /**
     * <ul>
     * <li>{@link DBKey#PK_ID}  book id</li>
     * <li>{@link Entity#BKEY_DATA_MODIFIED}      boolean</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        // always set the *current* book, so the BoB list can reposition correctly.
        if (mBook != null) {
            mResultIntent.putExtra(DBKey.PK_ID, mBook.getId());
        }
        return mResultIntent;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {

        if (mDateParser == null) {
            mDateParser = new FullDateParser(context);

            if (args != null) {
                final String styleUuid = args.getString(ListStyle.BKEY_STYLE_UUID);
                if (styleUuid != null) {
                    mStyle = ServiceLocator.getInstance().getStyles()
                                           .getStyleOrDefault(context, styleUuid);
                }

                // 1. Do we have a bundle? e.g. after an internet search
                final Bundle bookData = args.getBundle(Book.BKEY_DATA_BUNDLE);
                if (bookData != null) {
                    mBook = Book.from(bookData);
                    // has unsaved data, hence 'Dirty'
                    mBook.setStage(EntityStage.Stage.Dirty);

                } else {
                    // 2. Do we have an id?, e.g. user clicked on a book in a list.
                    final long bookId = args.getLong(DBKey.PK_ID, 0);
                    if (bookId > 0) {
                        mBook = Book.from(bookId, ServiceLocator.getInstance().getBookDao());
                    } else {
                        mBook = new Book();
                    }
                    // has unchanged data, hence 'WriteAble'
                    mBook.setStage(EntityStage.Stage.WriteAble);
                }
            } else {
                // 3. No args, we want an empty new book (e.g. user wants to add one manually).
                mBook = new Book();
                // has no data, hence 'WriteAble'
                mBook.setStage(EntityStage.Stage.WriteAble);
            }

            mBook.addValidators();
            mBook.ensureBookshelf(context);
            mBook.ensureLanguage(context);
        }
    }

    public int getCurrentTab() {
        return mCurrentTab;
    }

    public void setCurrentTab(final int currentTab) {
        mCurrentTab = currentTab;
    }

    @NonNull
    public Fields getFields(@Nullable final String key) {
        Fields fields;
        synchronized (mFieldsMap) {
            fields = mFieldsMap.get(key);
            if (fields == null) {
                fields = new Fields();
                mFieldsMap.put(key, fields);
            }
        }
        return fields;
    }

    /**
     * Convert a Field value (String) to an Instant in time.
     *
     * @param field       to extract from
     * @param todayIfNone if set, and the incoming date is null, use 'today' for the date
     *
     * @return instant
     */
    @Nullable
    public Instant getInstant(@NonNull final Field<String, TextView> field,
                              final boolean todayIfNone) {

        final LocalDateTime date = mDateParser.parse(field.getAccessor().getValue());
        if (date == null && !todayIfNone) {
            return null;
        }

        if (date != null) {
            return date.toInstant(ZoneOffset.UTC);
        }
        return Instant.now();
    }

    /**
     * Get the list of fragments (their tags) which have unfinished edits.
     *
     * @return list
     */
    @NonNull
    public Collection<String> getUnfinishedEdits() {
        return mFragmentsWithUnfinishedEdits;
    }

    /**
     * Add or remove the given fragment tag from the list of unfinished edits.
     *
     * @param tag                of fragment
     * @param hasUnfinishedEdits flag
     */
    public void setUnfinishedEdits(@NonNull final String tag,
                                   final boolean hasUnfinishedEdits) {
        if (hasUnfinishedEdits) {
            // Flag up this fragment as having unfinished edits.
            mFragmentsWithUnfinishedEdits.add(tag);
        } else {
            mFragmentsWithUnfinishedEdits.remove(tag);
        }
    }

    @NonNull
    public Book getBook() {
        return mBook;
    }

    /**
     * Insert/update the book into the database, store cover files, and prepare activity results.
     *
     * @param context Current context
     *
     * @throws DaoWriteException on failure
     */
    public void saveBook(@NonNull final Context context)
            throws DaoWriteException {

        if (mBook.isNew()) {
            ServiceLocator.getInstance().getBookDao().insert(context, mBook, 0);
        } else {
            ServiceLocator.getInstance().getBookDao().update(context, mBook, 0);
        }
        mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
        mBook.setStage(EntityStage.Stage.Clean);
    }

    /**
     * Delete an individual TocEntry.
     *
     * @param context  Current context
     * @param tocEntry to delete.
     *
     * @return {@code true} if a row was deleted
     */
    public boolean deleteTocEntry(@NonNull final Context context,
                                  @NonNull final TocEntry tocEntry) {
        return ServiceLocator.getInstance().getTocEntryDao().delete(context, tocEntry);
    }

    /**
     * Get the primary book Author.
     *
     * @param context Current context
     *
     * @return primary book author (or 'unknown' if none)
     */
    @NonNull
    public Author getPrimaryAuthor(@NonNull final Context context) {
        final Author author = mBook.getPrimaryAuthor();
        if (author != null) {
            return author;
        } else {
            return Author.createUnknownAuthor(context);
        }
    }

    /**
     * Check if the book already exists in the database.
     *
     * @return {@code true} if it does
     */
    public boolean bookExists() {
        if (mBook.isNew()) {
            final String isbnStr = mBook.getString(DBKey.KEY_ISBN);
            if (!isbnStr.isEmpty()) {
                return ServiceLocator.getInstance().getBookDao().bookExistsByIsbn(isbnStr);
            }
        }

        return false;
    }

    /**
     * Add any fields the book does not have yet (does not overwrite existing ones).
     *
     * @param args to check
     */
    public void addFieldsFromBundle(@Nullable final Bundle args) {
        if (args != null) {
            final Bundle bookData = args.getBundle(Book.BKEY_DATA_BUNDLE);
            if (bookData != null) {
                bookData.keySet()
                        .stream()
                        .filter(key -> !mBook.contains(key))
                        .forEach(key -> mBook.put(key, bookData.get(key)));
            }
        }
    }

    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>The fragment has no access to the style -> return the global visibility</li>
     *     <li>The global style is set to HIDE -> {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param global Global preferences
     * @param cIdx   0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isCoverUsed(@NonNull final SharedPreferences global,
                               @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx])) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields().isShowCover(global, cIdx);
        }
    }

    @NonNull
    public List<Bookshelf> getAllBookshelves() {
        // not cached.
        // This allows the user to edit the global list of shelves while editing a book.
        return ServiceLocator.getInstance().getBookshelfDao().getAll();
    }

    /**
     * Load an Author names list.
     *
     * @return list of Author names
     */
    @NonNull
    public List<String> getAllAuthorNames() {
        if (mAuthorNamesFormatted == null) {
            mAuthorNamesFormatted = ServiceLocator.getInstance().getAuthorDao()
                                                  .getNames(DBKey.KEY_AUTHOR_FORMATTED);
        }
        return mAuthorNamesFormatted;
    }

    /**
     * Load an Author Family names list.
     *
     * @return list of Author Family names
     */
    @NonNull
    public List<String> getAllAuthorFamilyNames() {
        if (mAuthorFamilyNames == null) {
            mAuthorFamilyNames = ServiceLocator.getInstance().getAuthorDao()
                                               .getNames(DBKey.KEY_AUTHOR_FAMILY_NAME);
        }
        return mAuthorFamilyNames;
    }

    /**
     * Load an Author Given names list.
     *
     * @return list of Author Given names
     */
    @NonNull
    public List<String> getAllAuthorGivenNames() {
        if (mAuthorGivenNames == null) {
            mAuthorGivenNames = ServiceLocator.getInstance().getAuthorDao()
                                              .getNames(DBKey.KEY_AUTHOR_GIVEN_NAMES);
        }
        return mAuthorGivenNames;
    }

    /**
     * Load a Publisher names list.
     *
     * @return list of Publisher names
     */
    @NonNull
    public List<String> getAllPublisherNames() {
        if (mPublisherNames == null) {
            mPublisherNames = ServiceLocator.getInstance().getPublisherDao().getNames();
        }
        return mPublisherNames;
    }

    /**
     * Load a Series titles list.
     *
     * @return list of Series titles
     */
    @NonNull
    public List<String> getAllSeriesTitles() {
        if (mSeriesTitles == null) {
            mSeriesTitles = ServiceLocator.getInstance().getSeriesDao().getNames();
        }
        return mSeriesTitles;
    }

    /**
     * Load a language list.
     * <p>
     * Returns a unique list of all languages in the database.
     * The list is ordered by {@link DBKey#UTC_DATE_LAST_UPDATED}.
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    public List<String> getAllLanguagesCodes() {
        if (mLanguagesCodes == null) {
            mLanguagesCodes = ServiceLocator.getInstance().getLanguageDao().getList();
        }
        return mLanguagesCodes;
    }

    /**
     * Load a format list.
     *
     * @return List of formats
     */
    @NonNull
    public List<String> getAllFormats() {
        if (mFormats == null) {
            mFormats = ServiceLocator.getInstance().getFormatDao().getList();
        }
        return mFormats;
    }

    /**
     * Load a color list.
     *
     * @return List of colors
     */
    @NonNull
    public List<String> getAllColors() {
        if (mColors == null) {
            mColors = ServiceLocator.getInstance().getColorDao().getList();
        }
        return mColors;
    }

    /**
     * Load a genre list.
     *
     * @return List of genres
     */
    @NonNull
    public List<String> getAllGenres() {
        if (mGenres == null) {
            mGenres = ServiceLocator.getInstance().getGenreDao().getList();
        }
        return mGenres;
    }

    /**
     * Load a location list.
     *
     * @return List of locations
     */
    @NonNull
    public List<String> getAllLocations() {
        if (mLocations == null) {
            mLocations = ServiceLocator.getInstance().getLocationDao().getList();
        }
        return mLocations;
    }

    /**
     * Load a currency list.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getAllListPriceCurrencyCodes() {
        if (mListPriceCurrencies == null) {
            mListPriceCurrencies = ServiceLocator
                    .getInstance().getBookDao().getCurrencyCodes(DBKey.PRICE_LISTED_CURRENCY);
        }
        return mListPriceCurrencies;
    }

    /**
     * Load a currency list.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getAllPricePaidCurrencyCodes() {
        if (mPricePaidCurrencies == null) {
            mPricePaidCurrencies = ServiceLocator
                    .getInstance().getBookDao().getCurrencyCodes(DBKey.PRICE_PAID_CURRENCY);
        }
        return mPricePaidCurrencies;
    }


    /**
     * Check if the passed Author is only used by this book.
     *
     * @param context Current context
     * @param author  to check
     *
     * @return {@code true} if the Author is only used by this book
     */
    public boolean isSingleUsage(@NonNull final Context context,
                                 @NonNull final Author author) {
        final Locale bookLocale = mBook.getLocale(context);

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        final long nrOfReferences = authorDao.countBooks(context, author, bookLocale)
                                    + authorDao.countTocEntries(context, author, bookLocale);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    /**
     * Check if the passed Series is only used by this book.
     *
     * @param context Current context
     * @param series  to check
     *
     * @return {@code true} if the Series is only used by this book
     */
    public boolean isSingleUsage(@NonNull final Context context,
                                 @NonNull final Series series) {
        final Locale bookLocale = mBook.getLocale(context);
        final long nrOfReferences = ServiceLocator.getInstance().getSeriesDao()
                                                  .countBooks(context, series, bookLocale);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    /**
     * Check if the passed Publisher is only used by this book.
     *
     * @param context   Current context
     * @param publisher to check
     *
     * @return {@code true} if the Publisher is only used by this book
     */
    public boolean isSingleUsage(@NonNull final Context context,
                                 @NonNull final Publisher publisher) {
        final Locale bookLocale = mBook.getLocale(context);
        final long nrOfReferences = ServiceLocator.getInstance().getPublisherDao()
                                                  .countBooks(context, publisher, bookLocale);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    @NonNull
    public LiveData<ArrayList<Author>> onAuthorList() {
        return mAuthorList;
    }

    @NonNull
    public LiveData<ArrayList<Series>> onSeriesList() {
        return mSeriesList;
    }

    @NonNull
    public LiveData<ArrayList<Publisher>> onPublisherList() {
        return mPublisherList;
    }


    public void updateAuthors(@NonNull final ArrayList<Author> list) {
        mBook.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, list);
        mAuthorList.setValue(list);
    }

    public void updateSeries(@NonNull final ArrayList<Series> list) {
        mBook.putParcelableArrayList(Book.BKEY_SERIES_LIST, list);
        mSeriesList.setValue(list);
    }

    public void updatePublishers(@NonNull final ArrayList<Publisher> list) {
        mBook.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, list);
        mPublisherList.setValue(list);
    }


    public boolean changeForThisBook(@NonNull final Context context,
                                     @NonNull final Author original,
                                     @NonNull final Author modified) {

        if (ServiceLocator.getInstance().getAuthorDao().insert(context, modified) > 0) {
            final ArrayList<Author> list = mBook.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            // unlink the original, and link with the new one
            // Note that the original *might* be orphaned at this time.
            // That's ok, it will get garbage collected from the database sooner or later.
            list.remove(original);
            list.add(modified);
            mBook.pruneAuthors(context, true);
            return true;
        }
        Logger.error(TAG, new Throwable(), "Could not update", "original=" + original,
                     "modified=" + modified);
        return false;
    }

    public boolean changeForAllBooks(@NonNull final Context context,
                                     @NonNull final Author original,
                                     @NonNull final Author modified) {
        // copy all new data
        original.copyFrom(modified, true);

        if (ServiceLocator.getInstance().getAuthorDao().update(context, original)) {
            mBook.pruneAuthors(context, true);
            mBook.refreshAuthorList(context);
            return true;
        }

        Logger.error(TAG, new Throwable(), "Could not update", "original=" + original,
                     "modified=" + modified);
        return false;
    }

    public boolean changeForThisBook(@NonNull final Context context,
                                     @NonNull final Series original,
                                     @NonNull final Series modified) {
        if (ServiceLocator.getInstance().getSeriesDao()
                          .insert(context, modified, mBook.getLocale(context)) > 0) {
            final ArrayList<Series> list = mBook.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            // unlink the original, and link with the new one
            // Note that the original *might* be orphaned at this time.
            // That's ok, it will get garbage collected from the database sooner or later.
            list.remove(original);
            list.add(modified);
            mBook.pruneSeries(context, true);
            return true;
        }

        Logger.error(TAG, new Throwable(), "Could not update", "original=" + original,
                     "modified=" + modified);
        return false;
    }

    public boolean changeForAllBooks(@NonNull final Context context,
                                     @NonNull final Series original,
                                     @NonNull final Series modified) {
        // copy all new data
        original.copyFrom(modified, true);

        if (ServiceLocator.getInstance().getSeriesDao()
                          .update(context, original, mBook.getLocale(context))) {
            mBook.pruneSeries(context, true);
            mBook.refreshSeriesList(context);
            return true;
        }
        Logger.error(TAG, new Throwable(), "Could not update", "original=" + original,
                     "modified=" + modified);
        return false;
    }

    public boolean changeForThisBook(@NonNull final Context context,
                                     @NonNull final Publisher original,
                                     @NonNull final Publisher modified) {

        if (ServiceLocator.getInstance().getPublisherDao()
                          .insert(context, modified, mBook.getLocale(context)) > 0) {
            final ArrayList<Publisher> list = mBook
                    .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            // unlink the original, and link with the new one
            // Note that the original *might* be orphaned at this time.
            // That's ok, it will get garbage collected from the database sooner or later.
            list.remove(original);
            list.add(modified);
            mBook.prunePublishers(context, true);
            return true;
        }
        Logger.error(TAG, new Throwable(), "Could not update", "original=" + original,
                     "modified=" + modified);
        return false;
    }

    public boolean changeForAllBooks(@NonNull final Context context,
                                     @NonNull final Publisher original,
                                     @NonNull final Publisher modified) {
        // copy all new data
        original.copyFrom(modified);

        if (ServiceLocator.getInstance().getPublisherDao()
                          .update(context, original, mBook.getLocale(context))) {
            mBook.prunePublishers(context, true);
            mBook.refreshPublishersList(context);
            return true;
        }
        Logger.error(TAG, new Throwable(), "Could not update", "original=" + original,
                     "modified=" + modified);
        return false;
    }


    public void fixId(@NonNull final Context context,
                      @NonNull final Author author) {
        ServiceLocator.getInstance().getAuthorDao()
                      .fixId(context, author, true, mBook.getLocale(context));
    }

    public void fixId(@NonNull final Context context,
                      @NonNull final Series series) {
        ServiceLocator.getInstance().getSeriesDao()
                      .fixId(context, series, true, mBook.getLocale(context));
    }

    public void fixId(@NonNull final Context context,
                      @NonNull final Publisher publisher) {
        ServiceLocator.getInstance().getPublisherDao()
                      .fixId(context, publisher, true, mBook.getLocale(context));
    }

    public void fixId(@NonNull final Context context,
                      @NonNull final TocEntry tocEntry) {
        ServiceLocator.getInstance().getTocEntryDao()
                      .fixId(context, tocEntry, true, mBook.getLocale(context));
    }
}

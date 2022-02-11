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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.bookdetails.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

public class BooksOnBookshelfViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfViewModel";

    /** collapsed/expanded. */
    static final String BKEY_LIST_STATE = TAG + ":list.state";

    /** Allows to set an explicit shelf. */
    static final String BKEY_BOOKSHELF = TAG + ":bs";

    private static final String ERROR_NULL_BOOKLIST = "mBooklist";

    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();

    /** Cache for all bookshelves. */
    private final List<Bookshelf> mBookshelfList = new ArrayList<>();

    private final BoBTask mBoBTask = new BoBTask();

    /** Database Access. */
    private BookDao mBookDao;
    /**
     * Flag (potentially) set when coming back from another Activity.
     * Indicates if list rebuild is needed in {@link BooksOnBookshelf}#onResume.
     */
    private boolean mForceRebuildInOnResume;
    /** Flag to indicate that a list has been successfully loaded. */
    private boolean mListHasBeenLoaded;
    /** Currently selected bookshelf. */
    @Nullable
    private Bookshelf mBookshelf;
    /** The row id we want the new list to display more-or-less in the center. */
    private long mCurrentCenteredBookId;
    /** Preferred booklist state in next rebuild. */
    private RebuildBooklist mRebuildMode;
    /** Current displayed list. */
    @Nullable
    private Booklist mBooklist;

    // Not using a list here as we need separate access to the amazon handler
    @Nullable
    private ViewBookOnWebsiteHandler mViewBookHandler;
    @Nullable
    private AmazonHandler mAmazonHandler;

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mBoBTask.onProgressUpdate();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<BoBTask.Outcome>>> onCancelled() {
        return mBoBTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onFailure() {
        return mBoBTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<BoBTask.Outcome>>> onFinished() {
        return mBoBTask.onFinished();
    }

    @Override
    protected void onCleared() {
        if (mBooklist != null) {
            mBooklist.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @Nullable final Bundle args) {

        if (mBookDao == null) {
            mBookDao = ServiceLocator.getInstance().getBookDao();

            // first start of the activity, read from user preference
            mRebuildMode = RebuildBooklist.getPreferredMode(context);

            if (args != null) {
                // extract search criteria if any are present
                mSearchCriteria.from(args, true);

                // allow the caller to override the user preference
                if (args.containsKey(BKEY_LIST_STATE)) {
                    mRebuildMode = Objects.requireNonNull(args.getParcelable(BKEY_LIST_STATE));
                }

                // check for an explicit bookshelf set
                if (args.containsKey(BKEY_BOOKSHELF)) {
                    // might be null, that's ok.
                    mBookshelf = Bookshelf.getBookshelf(context, args.getInt(BKEY_BOOKSHELF));
                }
            }
        } else {
            // always preserve the state when the hosting fragment was revived
            mRebuildMode = RebuildBooklist.FromSaved;
        }

        // Set the last/preferred bookshelf if not explicitly set above
        // or use the default == first start of the app
        if (mBookshelf == null) {
            mBookshelf = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT);
        }
    }

    void resetPreferredListRebuildMode(@NonNull final Context context) {
        mRebuildMode = RebuildBooklist.getPreferredMode(context);
    }

    @NonNull
    MenuHandler getViewBookHandler() {
        if (mViewBookHandler == null) {
            mViewBookHandler = new ViewBookOnWebsiteHandler();
        }
        return mViewBookHandler;
    }

    @NonNull
    MenuHandler getAmazonHandler() {
        if (mAmazonHandler == null) {
            mAmazonHandler = new AmazonHandler();
        }
        return mAmazonHandler;
    }

    /**
     * Get the Bookshelf list to show in the Spinner.
     * Will be empty until a call to {@link #reloadBookshelfList(Context)} is made.
     *
     * @return list
     */
    @NonNull
    List<Bookshelf> getBookshelfList() {
        return mBookshelfList;
    }

    /**
     * Construct the Bookshelf list to show in the Spinner.
     *
     * @param context Current context.
     */
    void reloadBookshelfList(@NonNull final Context context) {
        mBookshelfList.clear();
        mBookshelfList.add(Bookshelf.getBookshelf(context, Bookshelf.ALL_BOOKS));
        mBookshelfList.addAll(ServiceLocator.getInstance().getBookshelfDao().getAll());
    }

    /**
     * Find the position of the currently set Bookshelf in the Spinner.
     * (with fallback to the default, or to 0 if needed)
     *
     * @param context Current context.
     *
     * @return the position that reflects the current bookshelf.
     */
    int getSelectedBookshelfSpinnerPosition(@NonNull final Context context) {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);

        final List<Bookshelf> bookshelfList = getBookshelfList();
        // Not strictly needed, but guard against future changes
        if (bookshelfList.isEmpty()) {
            reloadBookshelfList(context);
        }

        // position we want to find
        Integer selectedPosition = null;
        // fallback if no selection found
        Integer defaultPosition = null;

        for (int i = 0; i < bookshelfList.size(); i++) {
            final Bookshelf bookshelf = bookshelfList.get(i);
            // find the position of the default shelf.
            if (bookshelf.getId() == Bookshelf.DEFAULT) {
                defaultPosition = i;
            }
            // find the position of the selected shelf
            if (bookshelf.getId() == mBookshelf.getId()) {
                selectedPosition = i;
            }
        }

        if (selectedPosition != null) {
            return selectedPosition;

        } else {
            return Objects.requireNonNullElse(defaultPosition, 0);
        }
    }

    @NonNull
    Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
        return mBookshelf;
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context Current context
     * @param id      of desired Bookshelf
     */
    void setCurrentBookshelf(@NonNull final Context context,
                             final long id) {
        mBookshelf = ServiceLocator.getInstance().getBookshelfDao().getById(id);
        if (mBookshelf == null) {
            mBookshelf = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        }
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        mBookshelf.setAsPreferred(global);
    }

    @SuppressWarnings("UnusedReturnValue")
    boolean reloadSelectedBookshelf(@NonNull final Context context) {
        final Bookshelf newBookshelf =
                Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        if (!newBookshelf.equals(mBookshelf)) {
            // if it was.. switch to it.
            mBookshelf = newBookshelf;
            return true;
        }
        return false;
    }


    /**
     * Get the style of the current bookshelf.
     *
     * @param context Current context
     *
     * @return style
     */
    @NonNull
    ListStyle getCurrentStyle(@NonNull final Context context) {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
        return mBookshelf.getStyle(context);
    }

    /**
     * Should be called after <strong>a style was edited</strong>.
     *
     * @param context Current context
     * @param uuid    the style which was edited
     */
    void onStyleEdited(@NonNull final Context context,
                       @NonNull final String uuid) {
        // The uuid/style SHOULD be valid as we just edited finished editing it.
        final ListStyle style = ServiceLocator.getInstance().getStyles()
                                              .getStyle(context, uuid);
        //noinspection ConstantConditions
        changeStyle(context, style);
    }

    /**
     * Should be called after <strong>a style was changed/selected</strong>.
     * The style should exist (id != 0), or if it doesn't, the default style will be used instead.
     *
     * @param context   Current context
     * @param styleUuid the style to apply
     */
    void onStyleChanged(@NonNull final Context context,
                        @NonNull final String styleUuid) {
        // Always validate first
        final ListStyle style = ServiceLocator.getInstance().getStyles()
                                              .getStyleOrDefault(context, styleUuid);
        changeStyle(context, style);
    }

    /**
     * Called after <strong>a style was changed/edited</strong>.
     * The style <strong>MUST</strong> be valid. No checks are done!
     *
     * @param context Current context
     * @param style   the style to apply
     */
    private void changeStyle(@NonNull final Context context,
                             @NonNull final ListStyle style) {
        Objects.requireNonNull(mBookshelf, Bookshelf.TAG);

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        // set as the global default.
        ServiceLocator.getInstance().getStyles().setDefault(global, style.getUuid());
        // save the new bookshelf/style combination
        mBookshelf.setAsPreferred(global);
        mBookshelf.setStyle(context, style);
    }

    /**
     * Save current position information in the preferences.
     * We do this to preserve this data across application shutdown/startup.
     *
     * @param context    Current context
     * @param position   adapter list position; i.e. first visible position in the list
     * @param viewOffset offset in pixels for the first visible position in the list
     */
    void saveListPosition(@NonNull final Context context,
                          final int position,
                          final int viewOffset) {
        if (mListHasBeenLoaded) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            mBookshelf.setFirstVisibleItemPosition(context, position, viewOffset);
        }
    }

    /**
     * Check if a rebuild is needed in {@code Activity#onResume()}.
     *
     * @return {@code true} if a rebuild is needed
     */
    boolean isForceRebuildInOnResume() {
        return mForceRebuildInOnResume;
    }

    /**
     * Request a rebuild at the next {@code Activity#onResume()}.
     *
     * @param forceRebuild Flag
     */
    void setForceRebuildInOnResume(final boolean forceRebuild) {
        mForceRebuildInOnResume = forceRebuild;
    }

    /**
     * Check if the list has (ever) loaded successfully.
     *
     * @return {@code true} if loaded at least once.
     */
    boolean isListLoaded() {
        return mListHasBeenLoaded;
    }

    @IntRange(from = 0)
    public long getCurrentCenteredBookId() {
        return mCurrentCenteredBookId;
    }

    /**
     * Set the <strong>desired</strong> book id to position the list.
     * Pass {@code 0} to disable.
     *
     * @param bookId to use
     */
    void setCurrentCenteredBookId(@IntRange(from = 0) final long bookId) {
        mCurrentCenteredBookId = bookId;
    }

    /**
     * Check if this book is lend out, or not.
     *
     * @param rowData with data
     *
     * @return {@code true} if this book is available for lending.
     */
    boolean isAvailable(@NonNull final DataHolder rowData) {
        final String loanee;
        if (rowData.contains(DBKey.KEY_LOANEE)) {
            loanee = rowData.getString(DBKey.KEY_LOANEE);
        } else {
            loanee = ServiceLocator.getInstance().getLoaneeDao().getLoaneeByBookId(
                    rowData.getLong(DBKey.FK_BOOK));
        }
        return (loanee == null) || loanee.isEmpty();
    }

    @NonNull
    SearchCriteria getSearchCriteria() {
        return mSearchCriteria;
    }

    boolean setSearchCriteria(@Nullable final Bundle bundle,
                              @SuppressWarnings("SameParameterValue") final boolean clearFirst) {
        if (bundle != null) {
            return mSearchCriteria.from(bundle, clearFirst);
        } else {
            return false;
        }
    }

    /**
     * This is used to re-display the list in onResume.
     * i.e. {@link #mCurrentCenteredBookId} was set, but a rebuild was not needed.
     *
     * @return the node(s), can be empty, but never {@code null}
     */
    @NonNull
    List<BooklistNode> getTargetNodes() {
        if (mCurrentCenteredBookId != 0) {
            Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
            return mBooklist.getVisibleBookNodes(mCurrentCenteredBookId);
        }

        return new ArrayList<>();
    }

    /**
     * Set the desired state on the given node.
     *
     * @param nodeRowId          list-view row id of the node in the list
     * @param nextState          the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     *
     * @return the node
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    BooklistNode setNode(final long nodeRowId,
                         @NonNull final BooklistNode.NextState nextState,
                         final int relativeChildLevel) {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.setNode(nodeRowId, nextState, relativeChildLevel);
    }

    void expandAllNodes(@IntRange(from = 1) final int topLevel,
                        final boolean expand) {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        mBooklist.setAllNodes(topLevel, expand);
    }

    @NonNull
    ArrayList<Long> getCurrentBookIdList() {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.getCurrentBookIdList();
    }

    @Nullable
    BooklistNode getNextBookWithoutCover(final long rowId) {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.getNextBookWithoutCover(rowId);
    }

    @Nullable
    String getHeaderFilterText(@NonNull final Context context) {
        final ListStyle style = getCurrentStyle(context);
        if (style.isShowHeader(ListStyle.HEADER_SHOW_FILTER)) {

            final Collection<String> filterText = style
                    .getFilters()
                    .getAll()
                    .stream()
                    .filter(f -> f.isActive(context))
                    .map(filter -> filter.getLabel(context))
                    .collect(Collectors.toList());

            final String ftsSearchText = mSearchCriteria.getFtsSearchText();
            if (!ftsSearchText.isEmpty()) {
                filterText.add('"' + ftsSearchText + '"');
            }

            if (!filterText.isEmpty()) {
                return context.getString(R.string.lbl_search_filtered_on_x,
                                         TextUtils.join(", ", filterText));
            }
        }
        return null;
    }

    @Nullable
    String getHeaderStyleName(@NonNull final Context context) {
        final ListStyle style = getCurrentStyle(context);
        if (style.isShowHeader(ListStyle.HEADER_SHOW_STYLE_NAME)) {
            return style.getLabel(context);
        }
        return null;
    }

    @Nullable
    String getHeaderBookCount(@NonNull final Context context) {
        final ListStyle style = getCurrentStyle(context);
        if (style.isShowHeader(ListStyle.HEADER_SHOW_BOOK_COUNT) && mBooklist != null) {
            final int totalBooks = mBooklist.countBooks();
            final int distinctBooks = mBooklist.countDistinctBooks();
            if (distinctBooks == totalBooks) {
                return context.getResources().getQuantityString(R.plurals.displaying_n_books,
                                                                distinctBooks, totalBooks);
            } else {
                return context.getString(R.string.txt_displaying_n_books_in_m_entries,
                                         distinctBooks, totalBooks);
            }
        }
        return null;
    }

    @NonNull
    String getBookNavigationTableName() {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.getNavigationTableName();
    }

    /**
     * Wrapper to get the list cursor.
     * Note this is a {@link BooklistCursor}
     *
     * @return cursor
     */
    @NonNull
    BooklistCursor getNewListCursor() {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.getNewListCursor();
    }

    @NonNull
    List<Author> getAuthorsByBookId(@IntRange(from = 1) final long bookId) {
        return ServiceLocator.getInstance().getAuthorDao().getAuthorsByBookId(bookId);
    }

    @NonNull
    ArrayList<Long> getBookIdsByAuthor(@IntRange(from = 1) final long authorId,
                                       final boolean justThisShelf) {
        if (justThisShelf) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            return ServiceLocator.getInstance().getAuthorDao()
                                 .getBookIds(authorId, mBookshelf.getId());
        } else {
            return ServiceLocator.getInstance().getAuthorDao().getBookIds(authorId);
        }
    }

    @NonNull
    ArrayList<Long> getBookIdsBySeries(@IntRange(from = 1) final long seriesId,
                                       final boolean justThisShelf) {
        if (justThisShelf) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            return ServiceLocator.getInstance().getSeriesDao()
                                 .getBookIds(seriesId, mBookshelf.getId());
        } else {
            return ServiceLocator.getInstance().getSeriesDao().getBookIds(seriesId);
        }
    }

    @NonNull
    ArrayList<Long> getBookIdsByPublisher(@IntRange(from = 1) final long publisherId,
                                          final boolean justThisShelf) {
        if (justThisShelf) {
            Objects.requireNonNull(mBookshelf, Bookshelf.TAG);
            return ServiceLocator.getInstance().getPublisherDao()
                                 .getBookIds(publisherId, mBookshelf.getId());
        } else {
            return ServiceLocator.getInstance().getPublisherDao().getBookIds(publisherId);
        }
    }

    boolean setAuthorComplete(@IntRange(from = 1) final long authorId,
                              final boolean isComplete) {
        return ServiceLocator.getInstance().getAuthorDao().setComplete(authorId, isComplete);
    }

    boolean setSeriesComplete(@IntRange(from = 1) final long seriesId,
                              final boolean isComplete) {
        return ServiceLocator.getInstance().getSeriesDao().setComplete(seriesId, isComplete);
    }

    boolean setBookRead(@IntRange(from = 1) final long bookId,
                        final boolean isRead) {
        return mBookDao.setRead(bookId, isRead);
    }

    Book getBook(@IntRange(from = 1) final long bookId) {
        return Book.from(bookId);
    }

    /**
     * Delete the given Series.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} on a successful delete
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Context context,
                   @NonNull final Series series) {
        return ServiceLocator.getInstance().getSeriesDao().delete(context, series);
    }

    /**
     * Delete the given Publisher.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} on a successful delete
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Context context,
                   @NonNull final Publisher publisher) {
        return ServiceLocator.getInstance().getPublisherDao().delete(context, publisher);
    }

    /**
     * Delete the given Bookshelf.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} on a successful delete
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Bookshelf bookshelf) {
        return ServiceLocator.getInstance().getBookshelfDao().delete(bookshelf);
    }

    /**
     * Delete the given Book.
     *
     * @param bookId to delete
     *
     * @return {@code true} on a successful delete
     */
    boolean deleteBook(@IntRange(from = 1) final long bookId) {
        return mBookDao.delete(bookId);
    }

    @SuppressWarnings("UnusedReturnValue")
    boolean lendBook(@IntRange(from = 1) final long bookId,
                     @SuppressWarnings("SameParameterValue") @Nullable final String loanee) {
        return ServiceLocator.getInstance().getLoaneeDao().setLoanee(bookId, loanee);
    }

    @NonNull
    int[] onBookRead(@IntRange(from = 1) final long bookId,
                     final boolean read) {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.updateBookRead(bookId, read)
                        .stream()
                        .mapToInt(BooklistNode::getAdapterPosition)
                        .toArray();
    }

    @NonNull
    int[] onBookLend(@IntRange(from = 1) final long bookId,
                     @Nullable final String loanee) {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.updateBookLoanee(bookId, loanee).stream()
                        .mapToInt(BooklistNode::getAdapterPosition)
                        .toArray();
    }


    /**
     * Queue a rebuild of the underlying cursor and data.
     */
    void buildBookList() {
        Objects.requireNonNull(mBookshelf, ERROR_NULL_BOOKLIST);

        mBoBTask.build(mBookshelf, mRebuildMode, mSearchCriteria, mCurrentCenteredBookId);
    }

    boolean isBuilding() {
        return mBoBTask.isRunning();
    }

    void onBuildFinished(@NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        //we already checked, don't check if (message.isNewEvent())

        // the new build is completely done. We can safely discard the previous one.
        if (mBooklist != null) {
            mBooklist.close();
        }

        mBooklist = message.getData().requireResult().getList();

        // Save a flag to say list was loaded at least once successfully
        mListHasBeenLoaded = true;

        // preserve the new state by default
        mRebuildMode = RebuildBooklist.FromSaved;
    }

    void onBuildCancelled() {
        // reset the central book id.
        mCurrentCenteredBookId = 0;
    }

    void onBuildFailed() {
        // reset the central book id.
        mCurrentCenteredBookId = 0;
    }

    @NonNull
    public List<BooklistNode> getVisibleBookNodes(final long bookId) {
        Objects.requireNonNull(mBooklist, ERROR_NULL_BOOKLIST);
        return mBooklist.getVisibleBookNodes(bookId);
    }
}

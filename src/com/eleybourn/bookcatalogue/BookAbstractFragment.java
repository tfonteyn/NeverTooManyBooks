/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Based class for all fragments that appear in {@link EditBookActivity}
 *
 * {@link BookDetailsFragment} extends {@link BookAbstractFragmentWithCoverImage}
 *
 * {@link EditBookFieldsFragment} extends {@link BookAbstractFragmentWithCoverImage}
 * {@link EditBookNotesFragment}
 * {@link EditBookLoanedFragment}
 * {@link EditBookAnthologyFragment}
 *
 * @author pjw
 */
public abstract class BookAbstractFragment extends Fragment implements DataEditor {

    private final int MENU_GROUP_BOOK = 1;
    private final int MENU_GROUP_BOOK_AMAZON_AUTHOR = 2;
    private final int MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES = 3;
    private final int MENU_GROUP_BOOK_AMAZON_SERIES = 4;

    /** */
    protected Fields mFields;
    /** Database instance */
    protected CatalogueDBAdapter mDb;

    /** A link to the Activity, cached to avoid requireActivity() all over the place */
    private Activity mActivity;

    /*  the casting is a kludge... ever since pulling edit/show book apart. Needs redoing */
    protected boolean isDirty() {
        return ((BaseActivity.CanBeDirty) mActivity).isDirty();
    }

    protected void setDirty(final boolean isDirty) {
        ((BaseActivity.CanBeDirty) mActivity).setDirty(isDirty);
    }

    protected Book getBook() {
        return ((HasBook) mActivity).getBook();
    }

    protected void setBookId(final long bookId) {
        ((HasBook) mActivity).setBookId(bookId);
    }

    /**
     * Called to do initial creation of a fragment.  This is called after
     * {@link #onAttach(Activity)} and before
     * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * <p>Note that this can be called while the fragment's activity is
     * still in the process of being created.  As such, you can not rely
     * on things like the activity's content view hierarchy being initialized
     * at this point.  If you want to do work once the activity itself is
     * created, see {@link #onActivityCreated(Bundle)}.
     *
     * <p>Any restored child fragments will be created before the base
     * <code>Fragment.onCreate</code> method returns.</p>
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
        this.setHasOptionsMenu(true);
    }

    /**
     * Ensure activity supports interface
     *
     * Called when a fragment is first attached to its context.
     * {@link #onCreate(Bundle)} will be called after this.
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof HasBook)) {
            throw new RTE.MustImplementException(context, HasBook.class);
        }

        if (!(context instanceof BaseActivity.CanBeDirty)) {
            throw new RTE.MustImplementException(context, BaseActivity.CanBeDirty.class);
        }
    }

    /**
     * Called when the fragment's activity has been created and this
     * fragment's view hierarchy instantiated.  It can be used to do final
     * initialization once these pieces are in place, such as retrieving
     * views or restoring state.  It is also useful for fragments that use
     * {@link #setRetainInstance(boolean)} to retain their instance,
     * as this callback tells the fragment when it is fully associated with
     * the new activity instance.  This is called after {@link #onCreateView}
     * and before {@link #onViewStateRestored(Bundle)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = requireActivity();

        mDb = new CatalogueDBAdapter(mActivity)
                .open();

        mFields = new Fields(this);
    }

    /**
     * visibility false by default, see {@link #onPrepareOptionsMenu}
     *
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {

        menu.add(MENU_GROUP_BOOK, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete)
                .setVisible(false)
                .setIcon(R.drawable.ic_delete);

        menu.add(MENU_GROUP_BOOK, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate)
                .setVisible(false)
                .setIcon(R.drawable.ic_content_copy);

        menu.add(MENU_GROUP_BOOK, R.id.MENU_BOOK_UPDATE_FROM_INTERNET, 0, R.string.internet_update_fields)
                .setVisible(false)
                .setIcon(R.drawable.ic_search);

        /* TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books. */
        menu.add(MENU_GROUP_BOOK, R.id.MENU_SHARE, 0, R.string.menu_share_this)
                .setVisible(false)
                .setIcon(R.drawable.ic_share);


        menu.add(MENU_GROUP_BOOK_AMAZON_AUTHOR, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0, R.string.menu_amazon_books_by_author)
                .setVisible(false)
                .setIcon(R.drawable.ic_search);

        menu.add(MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0, R.string.menu_amazon_books_by_author_in_series)
                .setIcon(R.drawable.ic_search);

        menu.add(MENU_GROUP_BOOK_AMAZON_SERIES, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0, R.string.menu_amazon_books_in_series)
                .setVisible(false)
                .setIcon(R.drawable.ic_search);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Set visibility of menu items as appropriate
     *
     * @see #setHasOptionsMenu
     * @see #onCreateOptionsMenu
     */
    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        boolean bookExists = getBook().getBookId() != 0;

        menu.setGroupVisible(MENU_GROUP_BOOK, bookExists);

        boolean hasAuthor = getBook().getAuthorList().size() > 0;
        boolean hasSeries = getBook().getSeriesList().size() > 0;

        menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_AUTHOR, hasAuthor);
        menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES, hasAuthor && hasSeries);
        menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_SERIES, hasSeries);
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final long bookId = getBook().getBookId();

        switch (item.getItemId()) {

            case R.id.MENU_BOOK_DELETE:
                BookUtils.deleteBook(mActivity, mDb, bookId,
                        new Runnable() {
                            @Override
                            public void run() {
                                mActivity.finish();
                            }
                        });
                return true;

            case R.id.MENU_BOOK_DUPLICATE:
                Bundle bookData = BookUtils.duplicateBook(mDb, bookId);
                Intent intent = new Intent(mActivity, EditBookActivity.class);
                intent.putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                mActivity.startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* result handled by hosting Activity */
                return true;

            case R.id.MENU_BOOK_UPDATE_FROM_INTERNET:
                /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                updateFromInternet(getBook());
                return true;


            case R.id.MENU_SHARE:
                BookUtils.shareBook(mActivity, mDb, bookId);
                return true;


            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonUtils.openSearchPage(mActivity, getAuthorFromBook(), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonUtils.openSearchPage(mActivity, null, getSeriesFromBook());
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonUtils.openSearchPage(mActivity, getAuthorFromBook(), getSeriesFromBook());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        // This is now done in onPause() since the view may have been deleted when this is called
        onSaveBookDetails(getBook());
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        // Load the data while preserving the isDirty() status
        mFields.setAfterFieldChangeListener(null);
        final boolean wasDirty = isDirty();
        onLoadBookDetails(getBook(), false);
        setDirty(wasDirty);

        // Set the listener to monitor edits
        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            @Override
            public void afterFieldChange(@NonNull Field field, @Nullable final String newValue) {
                setDirty(true);
            }
        });
    }

    @Override
    @CallSuper
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    private void updateFromInternet(@NonNull final Book book) {
        Intent intent = new Intent(requireActivity(), UpdateFromInternetActivity.class);
        // bookId to update
        intent.putExtra(UniqueId.KEY_ID, book.getBookId());
        // just as info, to display on the screen
        intent.putExtra(UniqueId.KEY_TITLE, book.getString(UniqueId.KEY_TITLE));
        intent.putExtra(UniqueId.KEY_AUTHOR_FORMATTED, book.getString(UniqueId.KEY_AUTHOR_FORMATTED));
        startActivityForResult(intent, UpdateFromInternetActivity.REQUEST_CODE); /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case UpdateFromInternetActivity.REQUEST_CODE: /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    long bookId = data.getLongExtra(UniqueId.KEY_ID, 0);
                    if (bookId != 0) {
                        setBookId(bookId);
                    } else {
                        boolean wasCancelled = data.getBooleanExtra(UniqueId.BKEY_CANCELED, false);
                        Logger.info(this, "UpdateFromInternet wasCancelled= " + wasCancelled);
                    }
                }
                break;

            default:
                Logger.error("onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
                break;
        }
    }

    /**
     * @return the first author in the list of authors for this book
     */
    @Nullable
    private String getAuthorFromBook() {
        ArrayList<Author> list = getBook().getAuthorList();
        return list.size() > 0 ? list.get(0).getDisplayName() : null;
    }

    /**
     * @return the first series in the list of series for this book
     */
    @Nullable
    private String getSeriesFromBook() {
        ArrayList<Series> list = getBook().getSeriesList();
        return list.size() > 0 ? list.get(0).name : null;
    }

    /**
     * Default implementation of code to load the Book object
     * Override as needed, calling super as the first step
     *
     * @param book       to load from
     * @param setAllFrom Options indicating setAllFrom() has already been called on the mFields object
     */
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllFrom) {
        if (!setAllFrom) {
            mFields.setAllFrom(book);
        }
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onLoadBookDetails}
     */
    @Override
    public final <T extends DataManager> void transferDataFrom(@NonNull final T dataManager) {
        final boolean wasDirty = isDirty();
        onLoadBookDetails((Book) dataManager, false);
        setDirty(wasDirty);
    }

    /**
     * Default implementation of code to save existing data to the Book object
     * Override as needed, calling super as the first step
     */
    @CallSuper
    protected void onSaveBookDetails(@NonNull final Book book) {
        mFields.putAllInto(book);
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onSaveBookDetails}
     */
    @Override
    public final <T extends DataManager> void transferDataTo(@NonNull final T dataManager) {
        onSaveBookDetails((Book) dataManager);
    }

    /**
     * Hides unused fields if they have not any useful data.
     * Checks all text fields except of author, series, loaned.
     *
     * The latter and non-text fields are handled in
     * {@link EditBookFieldsFragment} or {@link BookDetailsFragment}
     * - bookshelf
     * - anthology/toc
     * - read status
     * - rating
     *
     * @see FieldVisibilityActivity
     */
    protected void showHideFields(final boolean hideIfEmpty) {
        mFields.resetVisibility();

        showHideField(hideIfEmpty, R.id.coverImage, R.id.row_image);

        showHideField(hideIfEmpty, R.id.isbn, R.id.row_isbn);
        showHideField(hideIfEmpty, R.id.series, R.id.row_series, R.id.lbl_series);
        showHideField(hideIfEmpty, R.id.description, R.id.lbl_description, R.id.description_divider);

        showHideField(hideIfEmpty, R.id.publisher, R.id.lbl_publishing, R.id.row_publisher);
        showHideField(hideIfEmpty, R.id.date_published, R.id.row_date_published);
        showHideField(hideIfEmpty, R.id.first_publication, R.id.row_first_publication);

        showHideField(hideIfEmpty, R.id.pages, R.id.row_pages);
        showHideField(hideIfEmpty, R.id.list_price, R.id.row_list_price);
        showHideField(hideIfEmpty, R.id.format, R.id.row_format);
        showHideField(hideIfEmpty, R.id.genre, R.id.lbl_genre, R.id.row_genre);
        showHideField(hideIfEmpty, R.id.language, R.id.lbl_language, R.id.row_language);

        // **** MY COMMENTS SECTION ****

        showHideField(hideIfEmpty, R.id.notes, R.id.lbl_notes, R.id.row_notes);
        showHideField(hideIfEmpty, R.id.location, R.id.row_location, R.id.row_location);
        showHideField(hideIfEmpty, R.id.read_start, R.id.row_read_start);
        showHideField(hideIfEmpty, R.id.read_end, R.id.row_read_end);
        showHideField(hideIfEmpty, R.id.signed, R.id.row_signed);

        //NEWKIND: when adding fields that can be invisible, add them here
    }

    /**
     * Text fields:
     * Hide text field if it has not any useful data.
     * Don't show a field if it is already hidden (assumed by user preference)
     *
     * ImageView:
     * use the visibility status of the ImageView to show/hide the relatedFields
     *
     * @param hideIfEmpty   hide if empty
     * @param fieldId       layout resource id of the field
     * @param relatedFields list of fields whose visibility will also be set based on the first field
     */
    protected void showHideField(final boolean hideIfEmpty,
                                 @IdRes final int fieldId,
                                 @NonNull @IdRes final int... relatedFields) {
        //noinspection ConstantConditions
        final View view = getView().findViewById(fieldId);
        if (view != null) {
            int visibility = view.getVisibility();
            if (hideIfEmpty) {
                if (visibility != View.GONE) {
                    // Determine if we should hide it
                    if (view instanceof ImageView) {
                        visibility = view.getVisibility(); //TOMF ??
                    } else {
                        final String value = mFields.getField(fieldId).getValue().toString();
                        final boolean isExist = value != null && !value.isEmpty();
                        visibility = isExist ? View.VISIBLE : View.GONE;
                        view.setVisibility(visibility);
                    }
                }
            }
            // Set the related views
            for (int i : relatedFields) {
                View rv = getView().findViewById(i);
                if (rv != null) {
                    rv.setVisibility(visibility);
                }
            }
        }
    }

    public interface HasBook {
        @NonNull
        Book getBook();

        void setBookId(final long bookId);
    }

    static class ViewUtils {
        private ViewUtils() {
        }

        /**
         * Gets the total number of rows from the adapter, then use that to set the ListView to the
         * full height so all rows are visible (no scrolling)
         *
         * Does nothing if the adapter is null, or if the view is not visible
         */
        static void justifyListViewHeightBasedOnChildren(@NonNull final ListView listView) {
            ListAdapter adapter = listView.getAdapter();
            if (adapter == null || listView.getVisibility() != View.VISIBLE) {
                return;
            }

            int totalHeight = 0;
            for (int i = 0; i < adapter.getCount(); i++) {
                View listItem = adapter.getView(i, null, listView);
                listItem.measure(0, 0);
                totalHeight += listItem.getMeasuredHeight();
            }

            ViewGroup.LayoutParams layoutParams = listView.getLayoutParams();
            layoutParams.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount()));
            listView.setLayoutParams(layoutParams);
            listView.requestLayout();
        }

        /**
         * Ensure that next up/down/left/right View is visible for all sub-views of the passed view.
         */
        static void fixFocusSettings(@NonNull final View root) {
            final INextView getDown = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusDownId();
                }

                @Override
                public void setNext(@NonNull final View v, @IdRes final int id) {
                    v.setNextFocusDownId(id);
                }
            };
            final INextView getUp = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusUpId();
                }

                @Override
                public void setNext(@NonNull final View v, @IdRes final int id) {
                    v.setNextFocusUpId(id);
                }
            };
            final INextView getLeft = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusLeftId();
                }

                @Override
                public void setNext(@NonNull final View v, @IdRes final int id) {
                    v.setNextFocusLeftId(id);
                }
            };
            final INextView getRight = new INextView() {
                @Override
                public int getNext(@NonNull final View v) {
                    return v.getNextFocusRightId();
                }

                @Override
                public void setNext(@NonNull final View v, @IdRes final int id) {
                    v.setNextFocusRightId(id);
                }
            };

            @SuppressLint("UseSparseArrays")
            Map<Integer, View> vh = new HashMap<>();
            getViews(root, vh);

            for (Map.Entry<Integer, View> ve : vh.entrySet()) {
                final View v = ve.getValue();
                if (v.getVisibility() == View.VISIBLE) {
                    fixNextView(vh, v, getDown);
                    fixNextView(vh, v, getUp);
                    fixNextView(vh, v, getLeft);
                    fixNextView(vh, v, getRight);
                }
            }
        }

        /**
         * Passed a collection of views, a specific View and an INextView, ensure that the
         * currently set 'next' view is actually a visible view, updating it if necessary.
         *
         * @param list   Collection of all views
         * @param view   View to check
         * @param getter Methods to get/set 'next' view
         */
        private static void fixNextView(@NonNull final Map<Integer, View> list,
                                        @NonNull final View view,
                                        @NonNull final INextView getter) {
            int nextId = getter.getNext(view);
            if (nextId != View.NO_ID) {
                int actualNextId = getNextView(list, nextId, getter);
                if (actualNextId != nextId) {
                    getter.setNext(view, actualNextId);
                }
            }
        }

        /**
         * Passed a collection of views, a specific view and an INextView object find the
         * first VISIBLE object returned by INextView when called recursively.
         *
         * @param list   Collection of all views
         * @param nextId ID of 'next' view to get
         * @param getter Interface to lookup 'next' ID given a view
         *
         * @return ID if first visible 'next' view
         */
        private static int getNextView(@NonNull final Map<Integer, View> list,
                                       final int nextId,
                                       @NonNull final INextView getter) {
            final View v = list.get(nextId);
            if (v == null) {
                return View.NO_ID;
            }

            if (v.getVisibility() == View.VISIBLE) {
                return nextId;
            }

            return getNextView(list, getter.getNext(v), getter);
        }

        /**
         * Passed a parent view, add it and all children view (if any) to the passed collection
         *
         * @param parent Parent View
         * @param list   Collection
         */
        private static void getViews(@NonNull final View parent,
                                     @NonNull final Map<Integer, View> list) {
            // Get the view ID and add it to collection if not already present.
            @IdRes final int id = parent.getId();
            if (id != View.NO_ID && !list.containsKey(id)) {
                list.put(id, parent);
            }
            // If it's a ViewGroup, then process children recursively.
            if (parent instanceof ViewGroup) {
                final ViewGroup g = (ViewGroup) parent;
                final int nChildren = g.getChildCount();
                for (int i = 0; i < nChildren; i++) {
                    getViews(g.getChildAt(i), list);
                }
            }
        }

        /**
         * Debug utility to dump an entire view hierarchy to the output.
         */
        @SuppressWarnings("unused")
        static void debugDumpViewTree(final int depth, @NonNull final View view) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth * 4; i++) {
                sb.append(" ");
            }
            sb.append(view.getClass().getCanonicalName())
                    .append(" (").append(view.getId()).append(")")
                    .append(view.getId() == R.id.lbl_row_description ? "DESC! ->" : " ->");

            if (view instanceof TextView) {
                String s = ((TextView) view).getText().toString().trim();
                s = s.substring(0, Math.min(s.length(), 20));
                sb.append(s);
            } else {
                Logger.info(BookAbstractFragment.class, sb.toString());
            }
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    debugDumpViewTree(depth + 1, g.getChildAt(i));
                }
            }
        }

        private interface INextView {
            int getNext(@NonNull final View v);

            void setNext(@NonNull final View v, @IdRes final int id);
        }
    }

}

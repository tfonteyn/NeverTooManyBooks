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
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.utils.BookUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Based class for all fragments that appear in the {@link BookDetailsActivity} activity
 *
 * @author pjw
 */
public abstract class EditBookAbstractFragment extends Fragment implements DataEditor {
    /** */
    protected Fields mFields;

    /** A link to the {@link BookEditManager} for this fragment (the activity) */
    protected BookEditManager mEditManager;
    /** Database instance */
    protected CatalogueDBAdapter mDb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (!(context instanceof BookEditManager))
            throw new IllegalStateException("Activity " + context.getClass().getSimpleName() + " must implement BookEditManager");

        mEditManager = (BookEditManager) context;
        mDb = new CatalogueDBAdapter(context);
        mDb.open();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFields = new Fields(this);
    }

    /**
     * Define the common menu options; each subclass can add more as necessary
     */
    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        //menu.clear();
        final long currRow = mEditManager.getBook().getBookId();
        if (currRow != 0) {
            menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete)
                    .setIcon(R.drawable.ic_mode_edit);

            menu.add(Menu.NONE, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate)
                    .setIcon(R.drawable.ic_content_copy);
//TODO: enable when done
//            menu.add(Menu.NONE, R.id.MENU_BOOK_UPDATE_FROM_INTERNET, 0, R.string.internet_update_fields)
//                    .setIcon(R.drawable.ic_search);

            // TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books.
            menu.add(Menu.NONE, R.id.MENU_SHARE, 0, R.string.menu_share_this)
                    .setIcon(R.drawable.ic_share);
            // Very rarely used, and easy to miss-click, so do not add as action_if_room!
            //.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        if (this instanceof BookDetailsFragment) {
            MenuItem item = menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.edit_book)
                    .setIcon(R.drawable.ic_mode_edit);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        boolean hasAuthor = mEditManager.getBook().getAuthorsList().size() > 0;
        if (hasAuthor) {
            menu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0, R.string.amazon_books_by_author)
                    .setIcon(R.drawable.ic_search);
        }

        if (mEditManager.getBook().getSeriesList().size() > 0) {
            if (hasAuthor) {
                menu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0, R.string.amazon_books_by_author_in_series)
                        .setIcon(R.drawable.ic_search);
            }
            menu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0, R.string.amazon_books_in_series)
                    .setIcon(R.drawable.ic_search);
        }
    }

    /**
     * This will be called when a menu item is selected. A large switch
     * statement to call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final long currRow = mEditManager.getBook().getBookId();
        switch (item.getItemId()) {
            case R.id.SUBMENU_REPLACE_THUMB:
                if (this instanceof EditBookFieldsFragment) {
                    ((EditBookFieldsFragment) this).showCoverContextMenu();
                    return true;
                }
                return false;

            case R.id.MENU_SHARE:
                BookUtils.shareBook(getActivity(), mDb, currRow);
                return true;

            case R.id.MENU_BOOK_DELETE:
                BookUtils.deleteBook(getActivity(), mDb, currRow,
                        new Runnable() {
                            @Override
                            public void run() {
                                getActivity().finish();
                            }
                        });
                return true;

            case R.id.MENU_BOOK_DUPLICATE:
                BookUtils.duplicateBook(getActivity(), mDb, currRow);
                return true;

            //TODO: enable when done
//                case R.id.MENU_BOOK_UPDATE_FROM_INTERNET:
//                    updateFromInternet();
//                    return true;

            case R.id.MENU_BOOK_EDIT:
                BookDetailsActivity.startEditMode(getActivity(), currRow, BookDetailsActivity.TAB_EDIT);
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                String author = getAuthorFromBook();
                AmazonUtils.openSearchPage(getActivity(), author, null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                String series = getSeriesFromBook();
                AmazonUtils.openSearchPage(getActivity(), null, series);
                return true;
            }

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                String author = getAuthorFromBook();
                String series = getSeriesFromBook();
                AmazonUtils.openSearchPage(getActivity(), author, series);
                return true;
            }
        }
        return false;
    }

    //TODO: enable when done
    private void updateFromInternet() {
        Intent intent = new Intent(getActivity(), UpdateFromInternet.class);
        intent.putExtra(UniqueId.KEY_ID, mEditManager.getBook().getBookId());
        intent.putExtra(UniqueId.KEY_TITLE, mEditManager.getBook().get(UniqueId.KEY_TITLE).toString());
        intent.putExtra(UniqueId.KEY_AUTHOR_FORMATTED, mEditManager.getBook().get(UniqueId.KEY_AUTHOR_FORMATTED).toString());
        startActivityForResult(intent, 1);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                String result = intent.getStringExtra("result");
                //TODO: implement this, then enable menu handling
            }
        }
    }

    @Nullable
    private String getAuthorFromBook() {
        ArrayList<Author> list = mEditManager.getBook().getAuthorsList();
        return list.size() > 0 ? list.get(0).getDisplayName() : null;
    }

    @Nullable
    private String getSeriesFromBook() {
        ArrayList<Series> list = mEditManager.getBook().getSeriesList();
        return list.size() > 0 ? list.get(0).name : null;
    }

    @Override
    public void onPause() {
        super.onPause();
        // This is now done in onPause() since the view may have been deleted when this is called
        onSaveBookDetails(mEditManager.getBook());
    }

    /**
     * Called to load data from the Book object when needed.
     *
     * @param book   to load from
     * @param setAllDone Options indicating setAll() has already been called on the mFields object
     */
    abstract protected void onLoadBookDetails(@NonNull final Book book,
                                              @SuppressWarnings("SameParameterValue") final boolean setAllDone);

    /**
     * Default implementation of code to save existing data to the Book object
     */
    protected void onSaveBookDetails(@NonNull final Book book) {
        mFields.getAll(book);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Load the data and preserve the isDirty() setting
        mFields.setAfterFieldChangeListener(null);
        final boolean wasDirty = mEditManager.isDirty();
        Book book = mEditManager.getBook();
        onLoadBookDetails(book, false);
        mEditManager.setDirty(wasDirty);

        // Set the listener to monitor edits
        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            @Override
            public void afterFieldChange(@NonNull Field field, @Nullable final String newValue) {
                mEditManager.setDirty(true);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDb.close();
    }

    @Override
    public void saveAllEdits(@NonNull final DataManager dataManager) {
        mFields.getAll(mEditManager.getBook());
    }

    /**
     * This is 'final' because we want inheritors to implement onLoadBookDetails()
     */
    @Override
    public final void reloadData(@NonNull final DataManager dataManager) {
        final boolean wasDirty = mEditManager.isDirty();
        onLoadBookDetails(mEditManager.getBook(), false);
        mEditManager.setDirty(wasDirty);
    }

    /**
     * Show or Hide text field if it has not any useful data.
     * Don't show a field if it is already hidden (assumed by user preference)
     *
     * @param hideIfEmpty   hide if empty
     * @param fieldId       layout resource id of the field
     * @param relatedFields list of fields whose visibility will also be set based on the first field
     */
    void showHideField(final boolean hideIfEmpty, @IdRes final int fieldId, @IdRes final int... relatedFields) {
        // Get the base view
        final View view = getView().findViewById(fieldId);
        int visibility;
        if (view != null) {
            visibility = view.getVisibility();
            if (hideIfEmpty) {
                if (view.getVisibility() != View.GONE) {
                    // Determine if we should hide it
                    if (view instanceof ImageView) {
                        visibility = view.getVisibility();
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
                if (rv != null)
                    rv.setVisibility(visibility);
            }
        }
    }

    /**
     * Hides unused fields if they have not any useful data. Checks all text fields
     * except of author, series and loaned.
     */
    protected void showHideFields(final boolean hideIfEmpty) {
        mFields.resetVisibility();

        showHideField(hideIfEmpty, R.id.publisher, R.id.lbl_publishing, R.id.row_publisher);
        showHideField(hideIfEmpty, R.id.date_published, R.id.row_date_published);
        showHideField(hideIfEmpty, R.id.first_publication, R.id.row_first_publication);
        showHideField(hideIfEmpty, R.id.image, R.id.row_image);
        showHideField(hideIfEmpty, R.id.pages, R.id.row_pages);
        showHideField(hideIfEmpty, R.id.format, R.id.row_format);
        showHideField(hideIfEmpty, R.id.genre, R.id.lbl_genre, R.id.row_genre);
        showHideField(hideIfEmpty, R.id.language, R.id.lbl_language, R.id.row_language);
        showHideField(hideIfEmpty, R.id.isbn, R.id.row_isbn);
        showHideField(hideIfEmpty, R.id.series, R.id.row_series, R.id.lbl_series);
        showHideField(hideIfEmpty, R.id.list_price, R.id.row_list_price);
        showHideField(hideIfEmpty, R.id.description, R.id.lbl_description, R.id.description_divider);

        // **** MY COMMENTS SECTION ****

        showHideField(hideIfEmpty, R.id.notes, R.id.lbl_notes, R.id.row_notes);
        showHideField(hideIfEmpty, R.id.read_start, R.id.row_read_start);
        showHideField(hideIfEmpty, R.id.read_end, R.id.row_read_end);
        showHideField(hideIfEmpty, R.id.location, R.id.row_location, R.id.row_location);
        showHideField(hideIfEmpty, R.id.signed, R.id.row_signed);
    }

    /**
     * Interface that any containing activity must implement.
     *
     * @author pjw
     */
    public interface BookEditManager {
        void addAnthologyTab(final boolean showAnthology);

        boolean isDirty();

        void setDirty(final boolean isDirty);

        Book getBook();

        void setRowId(final long id);

        List<String> getFormats();

        List<String> getGenres();

        List<String> getLanguages();

        List<String> getLocations();

        List<String> getPublishers();
    }

    public static class ViewUtils {
        private ViewUtils() {
        }

        /**
         * Ensure that next up/down/left/right View is visible for all sub-views of the passed view.
         */
        public static void fixFocusSettings(@NonNull final View root) {
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
            HashMap<Integer, View> vh = new HashMap<>();
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
        private static void fixNextView(@NonNull final HashMap<Integer, View> list,
                                        @NonNull final View view,
                                        @NonNull final INextView getter) {
            int nextId = getter.getNext(view);
            if (nextId != View.NO_ID) {
                int actualNextId = getNextView(list, nextId, getter);
                if (actualNextId != nextId)
                    getter.setNext(view, actualNextId);
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
        private static int getNextView(@NonNull final HashMap<Integer, View> list,
                                       final int nextId,
                                       @NonNull final INextView getter) {
            final View v = list.get(nextId);
            if (v == null)
                return View.NO_ID;

            if (v.getVisibility() == View.VISIBLE)
                return nextId;

            return getNextView(list, getter.getNext(v), getter);
        }

        /**
         * Passed a parent view, add it and all children view (if any) to the passed collection
         *
         * @param parent Parent View
         * @param list   Collection
         */
        private static void getViews(@NonNull final View parent,
                                     @NonNull final HashMap<Integer, View> list) {
            // Get the view ID and add it to collection if not already present.
            @IdRes
            final int id = parent.getId();
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

        private interface INextView {
            int getNext(@NonNull final View v);

            void setNext(@NonNull final View v, @IdRes final int id);
        }

        /*
         * Debug utility to dump an entire view hierarchy to the output.
         *
         * @param depth
         * @param v
         */
        //public static void dumpViewTree(int depth, View v) {
        //	for(int i = 0; i < depth*4; i++)
        //		System.out.print(" ");
        //	System.out.print(v.getClass().getName() + " (" + v.getId() + ")" + (v.getId() == R.id.descriptionLabel? "DESC! ->" : " ->"));
        //	if (v instanceof TextView) {
        //		String s = ((TextView)v).getText().toString().trim();
        //		System.out.println(s.substring(0, Math.min(s.length(), 20)));
        //	} else {
        //		System.out.println();
        //	}
        //	if (v instanceof ViewGroup) {
        //		ViewGroup g = (ViewGroup)v;
        //		for(int i = 0; i < g.getChildCount(); i++) {
        //			dumpViewTree(depth+1, g.getChildAt(i));
        //		}
        //	}
        //}
    }
}

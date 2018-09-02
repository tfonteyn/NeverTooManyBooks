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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.utils.BookUtils;

import java.util.ArrayList;

/**
 * Based class for all fragments that appear in the BookEdit activity
 *
 * @author pjw
 */
public abstract class BookEditFragmentAbstract extends Fragment implements DataEditor {
    protected static final int THUMBNAIL_OPTIONS_ID = 5;
    private static final int DELETE_ID = 1;
    private static final int DUPLICATE_ID = 3; //2 is taken by populate in anthology
    private static final int SHARE_ID = 4;
    private static final int EDIT_OPTIONS_ID = 6;
    protected Fields mFields;
    /**
     * A link to the {@link BookEditManager} for this fragment (the activity)
     */
    protected BookEditManager mEditManager;
    /**
     * Database instance
     */
    protected CatalogueDBAdapter mDbHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof BookEditManager))
            throw new RuntimeException("Activity " + context.getClass().getSimpleName() + " must implement BookEditManager");

        mEditManager = (BookEditManager) context;
        mDbHelper = new CatalogueDBAdapter(context);
        mDbHelper.open();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFields = new Fields(this);
    }

    /**
     * Define the common menu options; each subclass can add more as necessary
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        //menu.clear();
        final Long currRow = mEditManager.getBookData().getRowId();
        if (currRow != 0) {
            MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
            delete.setIcon(android.R.drawable.ic_menu_delete);

            MenuItem duplicate = menu.add(0, DUPLICATE_ID, 0, R.string.menu_duplicate);
            duplicate.setIcon(android.R.drawable.ic_menu_add);
        }

        // TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books.
        MenuItem tweet = menu.add(0, SHARE_ID, 0, R.string.menu_share_this);
        tweet.setIcon(R.drawable.ic_menu_twitter);
        // Very rarely used, and easy to miss-click.
        //tweet.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (this instanceof BookDetailsReadOnly) {
            MenuItem item = menu.add(0, EDIT_OPTIONS_ID, 0, R.string.edit_book);
            item.setIcon(android.R.drawable.ic_menu_edit);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        boolean hasAuthor = mEditManager.getBookData().getAuthors().size() > 0;
        if (hasAuthor) {
            MenuItem item = menu.add(0, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0, R.string.amazon_books_by_author);
            item.setIcon(R.drawable.ic_www_search_2_holo_dark);
        }

        if (mEditManager.getBookData().getSeries().size() > 0) {
            if (hasAuthor) {
                MenuItem item = menu.add(0, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0, R.string.amazon_books_by_author_in_series);
                item.setIcon(R.drawable.ic_www_search_2_holo_dark);
            }
            {
                MenuItem item = menu.add(0, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0, R.string.amazon_books_in_series);
                item.setIcon(R.drawable.ic_www_search_2_holo_dark);
            }
        }
    }

    /**
     * This will be called when a menu item is selected. A large switch
     * statement to call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Long currRow = mEditManager.getBookData().getRowId();

        try {
            switch (item.getItemId()) {
                case THUMBNAIL_OPTIONS_ID:
                    if (this instanceof BookEditFields) {
                        ((BookEditFields) this).showCoverContextMenu();
                        return true;
                    }
                    break;
                case SHARE_ID:
                    BookUtils.shareBook(getActivity(), mDbHelper, currRow);
                    return true;
                case DELETE_ID:
                    BookUtils.deleteBook(getActivity(), mDbHelper, currRow,
                            new Runnable() {
                                @Override
                                public void run() {
                                    getActivity().finish();
                                }
                            });
                    return true;
                case DUPLICATE_ID:
                    BookUtils.duplicateBook(getActivity(), mDbHelper, currRow);
                    return true;
                case EDIT_OPTIONS_ID:
                    BookEdit.editBook(getActivity(), currRow, BookEdit.TAB_EDIT);
                    return true;

                case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                    String author = getAuthorFromBook();
                    AmazonUtils.openAmazonSearchPage(getActivity(), author, null);
                    return true;
                }
                case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                    String series = getSeriesFromBook();
                    AmazonUtils.openAmazonSearchPage(getActivity(), null, series);
                    return true;
                }

                case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                    String author = getAuthorFromBook();
                    String series = getSeriesFromBook();
                    AmazonUtils.openAmazonSearchPage(getActivity(), author, series);
                    return true;
                }
            }

        } catch (NullPointerException e) {
            Logger.logError(e);
        }
        return false;
    }

    private String getAuthorFromBook() {
        ArrayList<Author> authors = mEditManager.getBookData().getAuthors();
        if (authors.size() > 0)
            return authors.get(0).getDisplayName();
        else
            return null;
    }

    private String getSeriesFromBook() {
        ArrayList<Series> list = mEditManager.getBookData().getSeries();
        if (list.size() > 0)
            return list.get(0).name;
        else
            return null;
    }

    @Override
    public void onPause() {
        super.onPause();
        // This is now done in onPause() since the view may have been deleted when this is called
        onSaveBookDetails(mEditManager.getBookData());
    }

    /**
     * Called to load data from the BookData object when needed.
     *
     * @param book       BookData to load from
     * @param setAllDone Flag indicating setAll() has already been called on the mFields object
     */
    abstract protected void onLoadBookDetails(BookData book, boolean setAllDone);

    /**
     * Default implementation of code to save existing data to the BookData object
     */
    protected void onSaveBookDetails(BookData book) {
        mFields.getAll(book);
    }

    @Override
    public void onResume() {
        //double t0 = System.currentTimeMillis();

        super.onResume();

        // Load the data and preserve the isDirty() setting
        mFields.setAfterFieldChangeListener(null);
        final boolean wasDirty = mEditManager.isDirty();
        BookData book = mEditManager.getBookData();
        onLoadBookDetails(book, false);
        mEditManager.setDirty(wasDirty);

        // Set the listener to monitor edits
        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            @Override
            public void afterFieldChange(Field field, String newValue) {
                mEditManager.setDirty(true);
            }
        });
        //System.out.println("BEFA resume: " + (System.currentTimeMillis() - t0));
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mDbHelper.close();
    }

    @Override
    public void saveAllEdits(DataManager data) {
        mFields.getAll(mEditManager.getBookData());
    }

    /**
     * This is 'final' because we want inheritors to implement onLoadBookDetails()
     */
    @Override
    public final void reloadData(DataManager data) {
        final boolean wasDirty = mEditManager.isDirty();
        onLoadBookDetails(mEditManager.getBookData(), false);
        mEditManager.setDirty(wasDirty);
    }

    /**
     * Show or Hide text field if it has not any useful data.
     * Don't show a field if it is already hidden (assumed by user preference)
     *
     * @param hideIfEmpty   hide if empty
     * @param resId         layout resource id of the field
     * @param relatedFields list of fields whose visibility will also be set based on the first field
     *
     * @return The resulting visibility setting value (VISIBLE or GONE)
     */
    @SuppressWarnings("UnusedReturnValue")
    private int showHideField(boolean hideIfEmpty, int resId, int... relatedFields) {
        // Get the base view
        final View v = getView().findViewById(resId);
        int visibility;
        if (v == null) {
            visibility = View.GONE;
        } else {
            visibility = v.getVisibility();
            if (hideIfEmpty) {
                if (v.getVisibility() != View.GONE) {
                    // Determine if we should hide it
                    if (v instanceof ImageView) {
                        visibility = v.getVisibility();
                    } else {
                        final String value = mFields.getField(resId).getValue().toString();
                        final boolean isExist = value != null && !value.isEmpty();
                        visibility = isExist ? View.VISIBLE : View.GONE;
                        v.setVisibility(visibility);
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
        return visibility;
    }

    /**
     * Hides unused fields if they have not any useful data. Checks all text fields
     * except of author, series and loaned.
     */
    protected void showHideFields(boolean hideIfEmpty) {
        mFields.resetVisibility();

        // Check publishing information; in reality only one of these fields will exist
        showHideField(hideIfEmpty, R.id.publishing_details, R.id.lbl_publishing, R.id.row_publisher);
        showHideField(hideIfEmpty, R.id.publisher, R.id.lbl_publishing, R.id.row_publisher);

        showHideField(hideIfEmpty, R.id.date_published, R.id.row_date_published);

        showHideField(hideIfEmpty, R.id.row_img, R.id.image_wrapper);

        showHideField(hideIfEmpty, R.id.pages, R.id.row_pages);

        showHideField(hideIfEmpty, R.id.format, R.id.row_format);

        showHideField(hideIfEmpty, R.id.genre, R.id.lbl_genre, R.id.row_genre);

        showHideField(hideIfEmpty, R.id.language, R.id.lbl_language, R.id.row_language);

        showHideField(hideIfEmpty, R.id.isbn, R.id.row_isbn);

        showHideField(hideIfEmpty, R.id.series, R.id.row_series, R.id.lbl_series);

        showHideField(hideIfEmpty, R.id.list_price, R.id.row_list_price);

        showHideField(hideIfEmpty, R.id.description, R.id.descriptionLabel, R.id.description_divider);

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
        //public Fields getFields();
        void setShowAnthology(boolean showAnthology);

        boolean isDirty();

        void setDirty(boolean isDirty);

        BookData getBookData();

        void setRowId(Long id);

        ArrayList<String> getFormats();

        ArrayList<String> getGenres();

        ArrayList<String> getLanguages();

        ArrayList<String> getPublishers();
    }
}

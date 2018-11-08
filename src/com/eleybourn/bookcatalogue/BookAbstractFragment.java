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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.baseactivity.CanBeDirty;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.picklist.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.picklist.SelectOneDialog;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
 * {@link EditBookTOCFragment}
 *
 * @author pjw
 */
public abstract class BookAbstractFragment extends Fragment implements DataEditor {

    private final int MENU_GROUP_BOOK = 1;

    private final int MENU_GROUP_SEARCH_AMAZON = 10;
    private final int MENU_GROUP_BOOK_AMAZON_AUTHOR = 11;
    private final int MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES = 12;
    private final int MENU_GROUP_BOOK_AMAZON_SERIES = 13;

    /** */
    protected Fields mFields;
    /** Database instance */
    protected CatalogueDBAdapter mDb;

    /** A link to the Activity, cached to avoid requireActivity() all over the place */
    private Activity mActivity;

    /*  the casting is a kludge... ever since pulling edit/show book apart. Needs redoing */
    protected boolean isDirty() {
        return ((CanBeDirty) mActivity).isDirty();
    }

    protected void setDirty(final boolean isDirty) {
        ((CanBeDirty) mActivity).setDirty(isDirty);
    }

    protected Book getBook() {
        return ((HasBook) mActivity).getBook();
    }

    protected void reload(final long bookId) {
        ((HasBook) mActivity).reload(bookId);
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
    public void onCreate(final @Nullable Bundle savedInstanceState) {
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
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof HasBook)) {
            throw new RTE.MustImplementException(context, HasBook.class);
        }

        if (!(context instanceof CanBeDirty)) {
            throw new RTE.MustImplementException(context, CanBeDirty.class);
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
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // cache to avoid multiple calls to requireActivity()
        mActivity = requireActivity();

        mDb = new CatalogueDBAdapter(mActivity)
                .open();

        initFields();
    }

    /**
     * Add any {@link Field} we need to {@link Fields}
     *
     * Set corresponding validators/formatters (if any)
     * Set onClickListener etc...
     *
     * Note this is NOT where we set values.
     *
     * Override as needed, but call super first
     */
    @CallSuper
    protected void initFields() {
        mFields = new Fields(this);
    }

    //<editor-fold desc="Field editors">
    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a {@link SelectOneDialog#selectObjectDialog} with a list of strings
     * to choose from.
     *
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the OnClickListener to (can be same as fieldId)
     * @param list          list of strings to choose from.
     */
    protected void initValuePicker(final @NonNull Field field,
                                   final @StringRes int dialogTitleId,
                                   final @IdRes int fieldButtonId,
                                   final @NonNull List<String> list) {
        // only bother when visible
        if (!field.visible) {
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, list);
        mFields.setAdapter(field.id, adapter);

        // Get the list to use in the AutoComplete stuff
        AutoCompleteTextView textView = field.getView();
        textView.setAdapter(new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, list));

        // Get the drop-down button for the list and setup dialog
        //noinspection ConstantConditions
        getView().findViewById(fieldButtonId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectOneDialog.selectObjectDialog(requireActivity().getLayoutInflater(),
                        getString(dialogTitleId),
                        list,
                        field.getValue().toString(),
                        new SelectOneDialog.SimpleDialogOnClickListener() {
                            @Override
                            public void onClick(final @NonNull SelectOneDialog.SimpleDialogItem item) {
                                field.setValue(item.toString());
                            }
                        });
            }
        });
    }

    /**
     * bind a field (button) to bring up a text editor in an overlapping dialog.
     *
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the OnClickListener to (can be same as field.id)
     * @param multiLine     true if the dialog box should offer a multi-line input.
     */
    protected void initTextFieldEditor(final @NonNull Field field,
                                       final @StringRes int dialogTitleId,
                                       final @IdRes int fieldButtonId,
                                       final boolean multiLine) {
        // only bother when visible
        if (!field.visible) {
            return;
        }

        //noinspection ConstantConditions
        getView().findViewById(fieldButtonId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                TextFieldEditorDialogFragment frag = new TextFieldEditorDialogFragment();
                Bundle args = new Bundle();
                args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
                args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
                args.putString(TextFieldEditorDialogFragment.BKEY_TEXT, field.getValue().toString());
                args.putBoolean(TextFieldEditorDialogFragment.BKEY_MULTI_LINE, multiLine);
                frag.setArguments(args);
                frag.show(requireFragmentManager(), null);
            }
        });
    }

    protected void initPartialDatePicker(final @NonNull Field field,
                                         final @StringRes int dialogTitleId,
                                         final boolean todayIfNone) {
        // only bother when visible
        if (!field.visible) {
            return;
        }

        field.getView().setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Object value = field.getValue();
                String date;
                if (todayIfNone && value.toString().isEmpty()) {
                    date = DateUtils.toSqlDateTime(new Date());
                } else {
                    date = value.toString();
                }

                PartialDatePickerDialogFragment frag = new PartialDatePickerDialogFragment();
                Bundle args = new Bundle();
                args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
                args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
                args.putString(PartialDatePickerDialogFragment.BKEY_DATE, date);
                frag.setArguments(args);
                frag.show(requireFragmentManager(), null);
            }
        });
    }

    protected <T> void initCheckListEditor(final @NonNull Field field,
                                           final @StringRes int dialogTitleId,
                                           final @NonNull ArrayList<CheckListItem<T>> list) {
        // only bother when visible
        if (!field.visible) {
            return;
        }

        field.getView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                CheckListEditorDialogFragment<T> frag = new CheckListEditorDialogFragment<>();
                Bundle args = new Bundle();
                args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
                args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
                args.putSerializable(CheckListEditorDialogFragment.BKEY_CHECK_LIST, list);
                frag.setArguments(args);
                frag.show(requireFragmentManager(), null);
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="standard menu handling">
    /**
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(final @NonNull Menu menu,
                                    final @NonNull MenuInflater inflater) {

        menu.add(MENU_GROUP_BOOK, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete_book)
                .setIcon(R.drawable.ic_delete);
        menu.add(MENU_GROUP_BOOK, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate_book)
                .setIcon(R.drawable.ic_content_copy);
        menu.add(MENU_GROUP_BOOK, R.id.MENU_BOOK_UPDATE_FROM_INTERNET, 0, R.string.internet_update_fields)
                .setIcon(R.drawable.ic_search);
        /* TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books. */
        menu.add(MENU_GROUP_BOOK, R.id.MENU_SHARE, 0, R.string.menu_share_this)
                .setIcon(R.drawable.ic_share);

        SubMenu searchMenu = menu.addSubMenu(MENU_GROUP_SEARCH_AMAZON,R.id.SUBMENU_AMAZON_SEARCH, 0,R.string.amazon);
        searchMenu.add(MENU_GROUP_BOOK_AMAZON_AUTHOR, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0, R.string.menu_amazon_books_by_author)
                .setIcon(R.drawable.ic_search);
        searchMenu.add(MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0, R.string.menu_amazon_books_by_author_in_series)
                .setIcon(R.drawable.ic_search);
        searchMenu.add(MENU_GROUP_BOOK_AMAZON_SERIES, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0, R.string.menu_amazon_books_in_series)
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

        menu.setGroupVisible(MENU_GROUP_SEARCH_AMAZON, hasAuthor || hasSeries);
        // now done as submenu, see onOptionsItemSelected
//        menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_AUTHOR, hasAuthor);
//        menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES, hasAuthor && hasSeries);
//        menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_SERIES, hasSeries);
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
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        final long bookId = getBook().getBookId();

        switch (item.getItemId()) {

            case R.id.SUBMENU_AMAZON_SEARCH: {
                Menu menu = item.getSubMenu();
                boolean hasAuthor = getBook().getAuthorList().size() > 0;
                boolean hasSeries = getBook().getSeriesList().size() > 0;

                menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_AUTHOR, hasAuthor);
                menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_AUTHOR_IN_SERIES, hasAuthor && hasSeries);
                menu.setGroupVisible(MENU_GROUP_BOOK_AMAZON_SERIES, hasSeries);
                // let the normal call flow go on
                break;
            }
            case R.id.MENU_BOOK_DELETE: {
                BookUtils.deleteBook(mActivity, mDb, bookId,
                        // runs if user confirmed deletion.
                        new Runnable() {
                            @Override
                            public void run() {
                                mActivity.setResult(Activity.RESULT_OK);
                                mActivity.finish();
                            }
                        });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                Bundle bookData = BookUtils.duplicateBook(mDb, bookId);
                Intent intent = new Intent(mActivity, EditBookActivity.class);
                intent.putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                mActivity.startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* result handled by hosting Activity */
                return true;
            }
            case R.id.MENU_BOOK_UPDATE_FROM_INTERNET: {
                /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                updateFromInternet(getBook());
                return true;
            }
            case R.id.MENU_SHARE: {
                BookUtils.shareBook(mActivity, mDb, bookId);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                AmazonUtils.openSearchPage(mActivity, getBook().getPrimaryAuthor(), null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                AmazonUtils.openSearchPage(mActivity, null, getBook().getPrimarySeries());
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                AmazonUtils.openSearchPage(mActivity, getBook().getPrimaryAuthor(), getBook().getPrimarySeries());
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateFromInternet(final @NonNull Book book) {
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
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case UpdateFromInternetActivity.REQUEST_CODE: /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    long bookId = data.getLongExtra(UniqueId.KEY_ID, 0);
                    if (bookId > 0) {
                        reload(bookId);
                    } else {
                        boolean wasCancelled = data.getBooleanExtra(UniqueId.BKEY_CANCELED, false);
                        Logger.info(this, "UpdateFromInternet wasCancelled= " + wasCancelled);
                    }
                }
                return;

            default:
                if (BuildConfig.DEBUG) {
                    // lowest level of our Fragments, see if we missed anything
                    Logger.info(this, "onActivityResult: BookAbstractFragment requestCode=" + requestCode + ", resultCode=" + resultCode);
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    //</editor-fold>

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        // load the book, while disabling the AfterFieldChangeListener
        mFields.setAfterFieldChangeListener(null);
        loadFieldsFrom(getBook());
        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            @Override
            public void afterFieldChange(@NonNull Field field, final @Nullable String newValue) {
                setDirty(true);
            }
        });
    }

    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        // This is now done in onPause() since the view may have been deleted when this is called
        saveFieldsTo(getBook());
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }


    //<editor-fold desc="DataEditor interface">

    /**
     * This is 'final' because we want inheritors to implement {@link #onLoadFieldsFromBook}
     *
     * Load the data while preserving the isDirty() status
     */
    @Override
    public final <T extends DataManager> void loadFieldsFrom(final @NonNull T dataManager) {
        final boolean wasDirty = isDirty();
        onLoadFieldsFromBook((Book) dataManager, false);
        setDirty(wasDirty);
    }

    /**
     * Default implementation of code to load the Book object
     * Override as needed, calling super as the first step
     *
     * This is where you should populate all the fields with the values coming from the book.
     * This base class manages all the actual fields, but 'special' fields can/should be handled
     * in overrides.
     *
     * @param book       to load from
     * @param setAllFrom flag indicating {@link Fields#setAllFrom} has already been called or not
     */
    @CallSuper
    protected void onLoadFieldsFromBook(final @NonNull Book book, final boolean setAllFrom) {
        if (!setAllFrom) {
            mFields.setAllFrom(book);
        }
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onLoadFieldsFromBook done");
        }
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}
     */
    @Override
    public final <T extends DataManager> void saveFieldsTo(final @NonNull T dataManager) {
        onSaveFieldsToBook((Book) dataManager);
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Field} into the {@link DataManager} e.g. the {@link Book}
     *
     * Override as needed, calling super if needed
     */
    protected void onSaveFieldsToBook(final @NonNull Book book) {
        mFields.putAllInto(book);

        if (BuildConfig.DEBUG) {
            Logger.info(this, "onSaveFieldsToBook done");
        }
    }
    //</editor-fold>


    /**
     * Hides unused fields if they have no useful data.
     *
     * Authors & Title are always visible as they are required fields.
     *
     * Series is done in:
     * {@link EditBookFieldsFragment#populateSeriesListField}
     * {@link BookDetailsFragment#populateSeriesListField}
     *
     * Special fields not checked here:
     * - toc
     * - read status
     *
     * @see FieldVisibilityActivity
     */
    protected void showHideFields(final boolean hideIfEmpty) {
        mFields.resetVisibility();

        // actual book
        showHideField(hideIfEmpty, R.id.coverImage, R.id.row_image);
        showHideField(hideIfEmpty, R.id.isbn, R.id.row_isbn, R.id.lbl_isbn);
        showHideField(hideIfEmpty, R.id.description, R.id.lbl_description);
        showHideField(hideIfEmpty, R.id.publisher, R.id.row_publisher, R.id.lbl_publishing);
        showHideField(hideIfEmpty, R.id.date_published, R.id.row_date_published, R.id.lbl_publishing);
        showHideField(hideIfEmpty, R.id.first_publication, R.id.row_first_publication);
        showHideField(hideIfEmpty, R.id.pages, R.id.row_pages, R.id.lbl_pages);
        showHideField(hideIfEmpty, R.id.price_listed, R.id.row_price_listed, R.id.lbl_price_listed);
        showHideField(hideIfEmpty, R.id.format, R.id.row_format, R.id.lbl_format);
        showHideField(hideIfEmpty, R.id.genre, R.id.row_genre, R.id.lbl_genre);
        showHideField(hideIfEmpty, R.id.language, R.id.row_language, R.id.lbl_language);
//        showHideField(hideIfEmpty, R.id.toc, R.id.row_toc);

        // personal fields
        showHideField(hideIfEmpty, R.id.bookshelves, R.id.row_bookshelves, R.id.lbl_bookshelves);
//        showHideField(hideIfEmpty, R.id.read, R.id.row_read, R.id.lbl_read);

        showHideField(hideIfEmpty, R.id.edition, R.id.row_edition, R.id.lbl_edition);
        showHideField(hideIfEmpty, R.id.notes, R.id.row_notes);
        showHideField(hideIfEmpty, R.id.location, R.id.row_location, R.id.lbl_location);
        showHideField(hideIfEmpty, R.id.date_purchased, R.id.row_date_purchased);
        showHideField(hideIfEmpty, R.id.price_paid, R.id.row_price_paid, R.id.lbl_price_paid);
        showHideField(hideIfEmpty, R.id.read_start, R.id.row_read_start, R.id.lbl_read_start);
        showHideField(hideIfEmpty, R.id.read_end, R.id.row_read_end, R.id.lbl_read_end);
        showHideField(hideIfEmpty, R.id.signed, R.id.row_signed);
        showHideField(hideIfEmpty, R.id.rating, R.id.row_rating, R.id.lbl_rating);

        // other
        showHideField(hideIfEmpty, R.id.loaned_to);

        //NEWKIND: new fields
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
    private void showHideField(final boolean hideIfEmpty,
                               final @IdRes int fieldId,
                               final @NonNull @IdRes int... relatedFields) {
        //noinspection ConstantConditions
        final View view = getView().findViewById(fieldId);
        if (view != null) {
            int visibility = view.getVisibility();
            if (hideIfEmpty) {
                if (visibility != View.GONE) {
                    // Determine if we should hide it
                    if (!(view instanceof ImageView)) {
                        final String value = mFields.getField(fieldId).getValue().toString();
                        final boolean hasValue = value != null && !value.isEmpty();
                        visibility = hasValue ? View.VISIBLE : View.GONE;
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

    /** saving on some typing */
    protected SharedPreferences getPrefs() {
        return requireActivity().getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
    }

    public interface HasBook {
        @NonNull
        Book getBook();

        void reload(final long bookId);
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
        static void justifyListViewHeightBasedOnChildren(final @NonNull ListView listView) {
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
        static void fixFocusSettings(final @NonNull View root) {
            final INextView getDown = new INextView() {
                @Override
                public int getNext(final @NonNull View v) {
                    return v.getNextFocusDownId();
                }

                @Override
                public void setNext(final @NonNull View v, final @IdRes int id) {
                    v.setNextFocusDownId(id);
                }
            };
            final INextView getUp = new INextView() {
                @Override
                public int getNext(final @NonNull View v) {
                    return v.getNextFocusUpId();
                }

                @Override
                public void setNext(final @NonNull View v, final @IdRes int id) {
                    v.setNextFocusUpId(id);
                }
            };
            final INextView getLeft = new INextView() {
                @Override
                public int getNext(final @NonNull View v) {
                    return v.getNextFocusLeftId();
                }

                @Override
                public void setNext(final @NonNull View v, final @IdRes int id) {
                    v.setNextFocusLeftId(id);
                }
            };
            final INextView getRight = new INextView() {
                @Override
                public int getNext(final @NonNull View v) {
                    return v.getNextFocusRightId();
                }

                @Override
                public void setNext(final @NonNull View v, final @IdRes int id) {
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
        private static void fixNextView(final @NonNull Map<Integer, View> list,
                                        final @NonNull View view,
                                        final @NonNull INextView getter) {
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
        private static int getNextView(final @NonNull Map<Integer, View> list,
                                       final int nextId,
                                       final @NonNull INextView getter) {
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
        private static void getViews(final @NonNull View parent,
                                     final @NonNull Map<Integer, View> list) {
            // Get the view ID and add it to collection if not already present.
            final @IdRes int id = parent.getId();
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
        static void debugDumpViewTree(final int depth, final @NonNull View view) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth * 4; i++) {
                sb.append(" ");
            }
            sb.append(view.getClass().getCanonicalName())
                    .append(" (").append(view.getId()).append(")")
                    .append(view.getId() == R.id.row_lbl_description ? "DESC! ->" : " ->");

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
            int getNext(final @NonNull View v);

            void setNext(final @NonNull View v, final @IdRes int id);
        }
    }

}

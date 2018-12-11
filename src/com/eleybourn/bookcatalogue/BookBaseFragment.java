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
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Based class for {@link BookFragment} and all fragments that appear in {@link EditBookActivity}
 *
 * Full list:
 * {@link BookFragment}
 * {@link EditBookFieldsFragment}
 * {@link EditBookNotesFragment}
 * {@link EditBookLoanedFragment}
 * {@link EditBookTOCFragment}
 *
 * @author pjw
 */
public abstract class BookBaseFragment extends Fragment implements DataEditor {

    /** */
    protected Fields mFields;
    /** A link to the Activity, cached to avoid requireActivity() all over the place */
    private BaseActivity mActivity;

    /** Database instance */
    protected CatalogueDBAdapter mDb;

    protected void setActivityTitle(final @NonNull Book book) {
        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            if (book.getBookId() > 0) {
                // editing an existing book
                actionBar.setTitle(book.getString(UniqueId.KEY_TITLE));
                actionBar.setSubtitle(book.getAuthorTextShort());
            } else {
                // new book
                actionBar.setTitle(R.string.title_add_book);
                actionBar.setSubtitle(null);
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    abstract protected BookManager getBookManager();
    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

//    /**
//     * Ensure activity supports interface
//     */
//    @Override
//    @CallSuper
//    public void onAttach(final @NonNull Context context) {
//        super.onAttach(context);
//    }

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
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
        this.setHasOptionsMenu(true);
        Tracker.exitOnCreate(this);
    }

//    @Nullable
//    @Override
//    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
//        return super.onCreateView(inflater, container, savedInstanceState);
//    }

    /**
     * If we (the Fragment) is a {@link BookManager} then load the {@link Book}
     */
    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        // cache to avoid multiple calls to requireActivity()
        mActivity = (BaseActivity) requireActivity();

        super.onActivityCreated(savedInstanceState);

        mDb = new CatalogueDBAdapter(mActivity);

        Bundle args = getArguments();

        if (this instanceof BookManager) {
            // load the Book from (in order) savedInstanceState; arg; database; or a new book
            long bookId = BundleUtils.getLongFromBundles(UniqueId.KEY_ID, savedInstanceState, args);
            Bundle bookData = BundleUtils.getBundleFromBundles(UniqueId.BKEY_BOOK_DATA, savedInstanceState, args);
            Book book = Book.getBook(mDb, bookId, bookData);
            getBookManager().setBook(book);
        }

        initFields();
        Tracker.exitOnActivityCreated(this);
    }

    /**
     * Add any {@link Field} we need to {@link Fields}
     *
     * Set corresponding validators/formatters (if any)
     * Set onClickListener etc...
     *
     * Note this is NOT where we set values.
     *
     * Override as needed, but call super FIRST
     */
    @CallSuper
    protected void initFields() {
        mFields = new Fields(this);
    }

    /**
     * Here we trigger the Fragment to load it's Fields from the Book
     */
    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        populateFrame();
        Tracker.exitOnResume(this);
    }

    /**
     * Populate all Fields with the data from the Book
     */
    protected void populateFrame() {
        // load the book, while disabling the AfterFieldChangeListener
        mFields.setAfterFieldChangeListener(null);
        Book book = getBookManager().getBook();
        loadFieldsFrom(book);
        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            @Override
            public void afterFieldChange(@NonNull Field field, final @Nullable String newValue) {
                getBookManager().setDirty(true);
            }
        });
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onLoadFieldsFromBook}
     *
     * Load the data while preserving the isDirty() status
     */
    @Override
    public final <T extends DataManager> void loadFieldsFrom(final @NonNull T dataManager) {
        final boolean wasDirty = getBookManager().isDirty();
        onLoadFieldsFromBook((Book) dataManager, false);
        getBookManager().setDirty(wasDirty);

        setActivityTitle((Book) dataManager);
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
     * @param setAllFrom flag indicating {@link Fields#setAllFrom(DataManager)} has already been called or not
     */
    @CallSuper
    protected void onLoadFieldsFromBook(final @NonNull Book book, final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getBookId());
        if (!setAllFrom) {
            mFields.setAllFrom(book);
        }
        Tracker.exitOnLoadFieldsFromBook(this, book.getBookId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    /**
     * Here we trigger the Fragment to save it's Fields to the Book
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        // This is now done in onPause() since the view may have been deleted when this is called
        saveFieldsTo(getBookManager().getBook());
        super.onPause();
        Tracker.exitOnPause(this);
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}
     * (just for consistency with the load process)
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
        Tracker.enterOnSaveFieldsToBook(this, book.getBookId());
        mFields.putAllInto(book);
        Tracker.exitOnSaveFieldsToBook(this, book.getBookId());
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

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu handlers">

    /**
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    public void onCreateOptionsMenu(final @NonNull Menu menu,
                                    final @NonNull MenuInflater inflater) {

        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete_book)
                .setIcon(R.drawable.ic_delete);
        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate_book)
                .setIcon(R.drawable.ic_content_copy);
        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_BOOK_UPDATE_FROM_INTERNET, 0, R.string.menu_internet_update_fields)
                .setIcon(R.drawable.ic_search);
        /* TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books. */
        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_SHARE, 0, R.string.menu_share_this)
                .setIcon(R.drawable.ic_share);

        MenuHandler.addAmazonSearchSubMenu(menu);

        // same menu, but different visibility/group
        menu.add(R.id.MENU_BOOK_READ, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read);
        menu.add(R.id.MENU_BOOK_UNREAD, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread);

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
        Book book = getBookManager().getBook();

        boolean bookExists = book.getBookId() != 0;
        menu.setGroupVisible(R.id.MENU_GROUP_BOOK, bookExists);

        boolean isRead = (0 != book.getInt(UniqueId.KEY_BOOK_READ));
        menu.setGroupVisible(R.id.MENU_BOOK_READ, bookExists && !isRead);
        menu.setGroupVisible(R.id.MENU_BOOK_UNREAD, bookExists && isRead);

        boolean hasAuthor = book.getAuthorList().size() > 0;
        boolean hasSeries = book.getSeriesList().size() > 0;
        menu.setGroupVisible(R.id.SUBMENU_AMAZON_SEARCH, hasAuthor || hasSeries);
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param menuItem The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem menuItem) {
        final Book book = getBookManager().getBook();

        switch (menuItem.getItemId()) {

            case R.id.MENU_BOOK_DELETE: {
                @StringRes
                int errorMsgId = StandardDialogs.deleteBookAlert(mActivity, mDb, book.getBookId(), new Runnable() {
                    @Override
                    public void run() {
                        mActivity.setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
                        mActivity.finish();
                    }
                });
                if (errorMsgId != 0) {
                    StandardDialogs.showUserMessage(mActivity, errorMsgId);
                }
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                Intent intent = new Intent(mActivity, EditBookActivity.class);
                intent.putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* result handled by hosting Activity */
                return true;
            }
            case R.id.MENU_BOOK_UPDATE_FROM_INTERNET: {
                /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                Intent intent = new Intent(requireActivity(), UpdateFieldsFromInternetActivity.class);
                // bookId to update
                intent.putExtra(UniqueId.KEY_ID, book.getBookId());
                // just as info, to display on the screen
                intent.putExtra(UniqueId.KEY_TITLE, book.getString(UniqueId.KEY_TITLE));
                intent.putExtra(UniqueId.KEY_AUTHOR_FORMATTED, book.getString(UniqueId.KEY_AUTHOR_FORMATTED));

                startActivityForResult(intent, UpdateFieldsFromInternetActivity.REQUEST_CODE); /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                return true;
            }
            case R.id.MENU_SHARE: {
                startActivity(Intent.createChooser(book.getShareBookIntent(mActivity), getString(R.string.share)));
                return true;
            }
            case R.id.MENU_BOOK_READ: {
                // toggle 'read' status
                boolean isRead = (0 != book.getInt(UniqueId.KEY_BOOK_READ));
                if (book.setRead(mDb, !isRead)) {
                    // reverse value obv.
                    mFields.getField(R.id.read).setValue(isRead ? "0" : "1");
                }
                return true;
            }
        }

        if (MenuHandler.handleAmazonSearchSubMenu(mActivity, menuItem, book)) {
            return true;
        }

        return super.onOptionsItemSelected(menuItem);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

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

        // Get the list to use in the AutoCompleteTextView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_dropdown_item_1line, list);
        mFields.setAdapter(field.id, adapter);

        // Get the drop-down button for the list and setup dialog
        //noinspection ConstantConditions
        getView().findViewById(fieldButtonId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectOneDialog.selectObjectDialog(requireActivity().getLayoutInflater(),
                        getString(dialogTitleId), field, list,
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
     * @param callerTag     the fragment class that is calling the editor
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the OnClickListener to (can be same as field.id)
     * @param multiLine     true if the dialog box should offer a multi-line input.
     */
    @SuppressWarnings("SameParameterValue")
    protected void initTextFieldEditor(final @NonNull String callerTag,
                                       final @NonNull Field field,
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
                args.putString(UniqueId.BKEY_CALLER_ID, callerTag);
                args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
                args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
                args.putString(TextFieldEditorDialogFragment.BKEY_TEXT, field.getValue().toString());
                args.putBoolean(TextFieldEditorDialogFragment.BKEY_MULTI_LINE, multiLine);
                frag.setArguments(args);
                frag.show(requireFragmentManager(), null);
            }
        });
    }

    /**
     * @param callerTag     the fragment class that is calling the editor
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, pre-populate with today's date
     */
    protected void initPartialDatePicker(final @NonNull String callerTag,
                                         final @NonNull Field field,
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
                    date = DateUtils.localSqlDateForToday();
                } else {
                    date = value.toString();
                }

                if (DEBUG_SWITCHES.DATETIME && BuildConfig.DEBUG) {
                    Logger.info(this, "initPartialDatePicker date.toString(): " + date);
                }

                PartialDatePickerDialogFragment frag = new PartialDatePickerDialogFragment();
                Bundle args = new Bundle();
                args.putString(UniqueId.BKEY_CALLER_ID, callerTag);
                args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
                args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
                args.putString(PartialDatePickerDialogFragment.BKEY_DATE, date);
                frag.setArguments(args);
                frag.show(requireFragmentManager(), null);
            }
        });
    }

    /**
     * @param callerTag     the fragment class that is calling the editor
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param listGetter    {@link CheckListEditorListGetter<T>} interface to get the *current* list
     * @param <T>           type of the {@link CheckListItem}
     */
    protected <T> void initCheckListEditor(final @NonNull String callerTag,
                                           final @NonNull Field field,
                                           final @StringRes int dialogTitleId,
                                           final @NonNull CheckListEditorListGetter<T> listGetter) {
        // only bother when visible
        if (!field.visible) {
            return;
        }

        field.getView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                CheckListEditorDialogFragment<T> frag = new CheckListEditorDialogFragment<>();
                Bundle args = new Bundle();
                args.putString(UniqueId.BKEY_CALLER_ID, callerTag);
                args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
                args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
                args.putParcelableArrayList(CheckListEditorDialogFragment.BKEY_CHECK_LIST, listGetter.getList());
                frag.setArguments(args);
                frag.show(requireFragmentManager(), null);
            }
        });
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this,requestCode,resultCode, data);

        switch (requestCode) {
            case UpdateFieldsFromInternetActivity.REQUEST_CODE: /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    long bookId = data.getLongExtra(UniqueId.KEY_ID, 0);
                    if (bookId > 0) {
                        // replace current book with the updated one, no attempt is made to merge if in edit mode.
                        Book book = Book.getBook(mDb, bookId);
                        getBookManager().setBook(book);
                        loadFieldsFrom(book);
                    } else {
                        boolean wasCancelled = data.getBooleanExtra(UniqueId.BKEY_CANCELED, false);
                        Logger.info(this, "wasCancelled= " + wasCancelled);
                    }
                }
                break;

            default:
                // lowest level of our Fragment, see if we missed anything
                Logger.info(this, "BookBaseFragment|onActivityResult|NOT HANDLED: requestCode=" + requestCode + ", resultCode=" + resultCode);
                super.onActivityResult(requestCode,resultCode,data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Hides unused fields if they have no useful data.
     * Should normally be called at the *end* of {@link #onLoadFieldsFromBook}
     *
     * Authors & Title are always visible as they are required fields.
     *
     * Series is done in:
     * {@link EditBookFieldsFragment#populateSeriesListField}
     * {@link BookFragment#populateSeriesListField}
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
        showHideField(hideIfEmpty, R.id.date_acquired, R.id.row_date_acquired);
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
     * Hide text field if it does not have any useful data.
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

    /**
     * used to load the {@link CheckListEditorDialogFragment} with the *current* list
     * e.g. not the state of the list at init time
     */
    public interface CheckListEditorListGetter<T> {
        ArrayList<CheckListItem<T>> getList();
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
                Logger.info(BookBaseFragment.class, sb.toString());
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

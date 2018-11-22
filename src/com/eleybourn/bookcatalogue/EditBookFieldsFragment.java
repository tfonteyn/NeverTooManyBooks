/*
 * @copyright 2010 Evan Leybourn
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItemBase;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.CoverHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab
 */
public class EditBookFieldsFragment extends BookBaseFragment implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener<Bookshelf>,
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener,
        TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener {

    public static final String TAG = "EditBookFieldsFragment";
    /**
     * Field drop down lists
     * Lists in database so far, we cache them for performance but only load them when really needed
     */
    private List<String> mFormats;
    private List<String> mGenres;
    private List<String> mLanguages;
    private List<String> mPublishers;
    private List<String> mListPriceCurrencies;

    private CoverHandler mCoverHandler;

    /* ------------------------------------------------------------------------------------------ */
    @NonNull
    protected BookManager getBookManager() {
        //noinspection ConstantConditions
        return ((EditBookFragment) this.getParentFragment()).getBookManager();
    }

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

//    @Override
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_fields, container, false);
    }

    /**
     * has no specific Arguments or savedInstanceState as all is done via
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @CallSuper
    @Override
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);

        try {
            //noinspection ConstantConditions
            ViewUtils.fixFocusSettings(getView());
        } catch (Exception e) {
            // Log, but ignore. This is a non-critical feature that prevents crashes when the
            // 'next' key is pressed and some views have been hidden.
            Logger.error(e);
        }
        Tracker.exitOnActivityCreated(this);
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();
        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        Field field;

        // book fields
        // ENHANCE: simplify the SQL and use a formatter instead.
        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR_FORMATTED)
                .getView().setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(requireActivity(), EditAuthorListActivity.class);
                        intent.putExtra(UniqueId.BKEY_AUTHOR_ARRAY, getBookManager().getBook().getAuthorList());
                        intent.putExtra(UniqueId.KEY_ID, getBookManager().getBook().getBookId());
                        intent.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                        requireActivity().startActivityForResult(intent, EditAuthorListActivity.REQUEST_CODE); /* dd74343a-50ff-4ce9-a2e4-a75f7bcf9e36 */
                    }
                });

        mFields.add(R.id.name, UniqueId.KEY_SERIES_NAME)
                .getView().setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(requireActivity(), EditSeriesListActivity.class);
                        intent.putExtra(UniqueId.BKEY_SERIES_ARRAY, getBookManager().getBook().getSeriesList());
                        intent.putExtra(UniqueId.KEY_ID, getBookManager().getBook().getBookId());
                        intent.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                        requireActivity().startActivityForResult(intent, EditSeriesListActivity.REQUEST_CODE); /* bca659b6-dfb9-4a97-b651-5b05ad102400 */
                    }
                });

        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES);
        mFields.add(R.id.title, UniqueId.KEY_TITLE);
        mFields.add(R.id.isbn, UniqueId.KEY_BOOK_ISBN);

        field = mFields.add(R.id.description, UniqueId.KEY_DESCRIPTION)
                .setShowHtml(true);
        initTextFieldEditor(TAG, field, R.string.lbl_description, R.id.btn_description, true);

        field = mFields.add(R.id.genre, UniqueId.KEY_BOOK_GENRE);
        initValuePicker(field, R.string.lbl_genre, R.id.btn_genre, getGenres());

        field = mFields.add(R.id.language, UniqueId.KEY_BOOK_LANGUAGE)
                .setFormatter(new Fields.LanguageFormatter());
        initValuePicker(field, R.string.lbl_language, R.id.btn_language, getLanguages());

        field = mFields.add(R.id.format, UniqueId.KEY_BOOK_FORMAT);
        initValuePicker(field, R.string.lbl_format, R.id.btn_format, getFormats());

        field = mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER);
        initValuePicker(field, R.string.lbl_publisher, R.id.btn_publisher, getPublishers());

        field = mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED)
                .setFormatter(dateFormatter);
        initPartialDatePicker(TAG, field, R.string.lbl_date_published, false);

        field = mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION)
                .setFormatter(dateFormatter);
        initPartialDatePicker(TAG, field, R.string.lbl_first_publication, false);

        mFields.add(R.id.price_listed, UniqueId.KEY_BOOK_PRICE_LISTED);
        field = mFields.add(R.id.price_listed_currency, UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY);
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_listed_currency, getListPriceCurrencyCodes());

        /* Anthology is provided as a boolean, see {@link Book#initValidators()}*/
        mFields.add(R.id.is_anthology, Book.IS_ANTHOLOGY)
                .getView().setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View view) {
                        Checkable cb = (Checkable) view;
                        EditBookFragment frag = (EditBookFragment) getParentFragment();
                        //noinspection ConstantConditions
                        frag.addTOCTab(cb.isChecked());
                    }
                });

        // add the cover image
        Field coverField = mFields.add(R.id.coverImage, "", UniqueId.BKEY_HAVE_THUMBNAIL)
                .setDoNotFetch(true);

        mCoverHandler = new CoverHandler(requireActivity(), mDb, getBookManager(),
                coverField, mFields.getField(R.id.isbn));

        // Personal fields
        field = mFields.add(R.id.bookshelves, UniqueId.KEY_BOOKSHELF_NAME)
                .setDoNotFetch(true);
        initCheckListEditor(TAG, field, R.string.lbl_bookshelves_long, new CheckListEditorListGetter<Bookshelf>() {
            @Override
            public ArrayList<CheckListItem<Bookshelf>> getList() {
                return getBookManager().getBook().getEditableBookshelvesList(mDb);
            }
        });
    }

//    @CallSuper
//    @Override
//    public void onResume() {
//        super.onResume();
//    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(final @NonNull Book book, final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getBookId());
        super.onLoadFieldsFromBook(book, setAllFrom);

        // new book ? load data fields from Extras
        if (book.getBookId() <= 0) {
            Bundle extras = requireActivity().getIntent().getExtras();
            populateFieldsFromBundle(book, extras);
        }

        populateAuthorListField(book);
        populateSeriesListField(book);

        mCoverHandler.populateCoverView();
        mFields.getField(R.id.is_anthology).setValue(book.getString(Book.IS_ANTHOLOGY));

        mFields.getField(R.id.bookshelves).setValue(book.getBookshelfListAsText());

        // Restore default visibility
        showHideFields(false);

        Tracker.exitOnLoadFieldsFromBook(this, book.getBookId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">
    private void populateFieldsFromBundle(final @NonNull Book book, final @Nullable Bundle bundle) {
        if (bundle != null) {
            /*
             * From the ISBN Search (add if not there yet)
             */
            Bundle values = bundle.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (values != null) {
                for (Field field : mFields) {
                    if (!field.column.isEmpty() && values.containsKey(field.column)) {
                        String val = values.getString(field.column);
                        if (val != null) {
                            field.setValue(val);
                        }
                    }
                }
            }
        }

        /*
         * Use the currently selected bookshelf as default for a new book
         */
        final List<Bookshelf> list = book.getBookshelfList();
        if (list.isEmpty()) {
            Bookshelf bookshelf = null;
            String name = BookCatalogueApp.getStringPreference(BooksOnBookshelf.PREF_BOOKSHELF, null);
            if (name != null && !name.isEmpty()) {
                bookshelf = mDb.getBookshelfByName(name);
            }
            if (bookshelf == null) /* || name.isEmpty() */ {
                // unlikely to be true, but use default just in case
                bookshelf = new Bookshelf(Bookshelf.DEFAULT_ID, mDb.getBookshelfName(Bookshelf.DEFAULT_ID));
            }

            mFields.getField(R.id.bookshelves).setValue(bookshelf.name);

            ArrayList<Bookshelf> bsList = new ArrayList<>();
            bsList.add(bookshelf);
            book.putBookshelfList(bsList);
        }
    }

    private void populateAuthorListField(final @NonNull Book book) {
        ArrayList<Author> list = book.getAuthorList();
        if (list.size() != 0 && Utils.pruneList(mDb, list)) {
            getBookManager().setDirty(true);
            book.putAuthorList(list);
        }

        String newText = book.getAuthorTextShort();
        if (newText.isEmpty()) {
            newText = getString(R.string.btn_set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    private void populateSeriesListField(final @NonNull Book book) {
        boolean visible = mFields.getField(R.id.name).visible;
        if (visible) {
            ArrayList<Series> list = book.getSeriesList();
            int seriesCount = list.size();

            if (seriesCount != 0 && Utils.pruneList(mDb, list)) {
                getBookManager().setDirty(true);
                book.putSeriesList(list);
            }

            String newText = book.getSeriesTextShort();
            if (newText.isEmpty()) {
                newText = getString(R.string.btn_set_series);
            }
            mFields.getField(R.id.name).setValue(newText);
        }
        //noinspection ConstantConditions
        getView().findViewById(R.id.lbl_series).setVisibility(visible ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.name).setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        mCoverHandler.dismissCoverBrowser();

        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getBookId());
        super.onSaveFieldsToBook(book);
        Tracker.exitOnSaveFieldsToBook(this, book.getBookId());
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu handlers">

    /**
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    public void onCreateOptionsMenu(final @NonNull Menu menu, final @NonNull MenuInflater inflater) {
        if (mFields.getField(R.id.coverImage).visible) {
            menu.add(Menu.NONE, R.id.SUBMENU_THUMB_REPLACE, 0, R.string.menu_cover_replace)
                    .setIcon(R.drawable.ic_add_a_photo)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        super.onCreateOptionsMenu(menu, inflater);
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
        switch (menuItem.getItemId()) {
            case R.id.SUBMENU_THUMB_REPLACE:
                // Show the context menu for the cover thumbnail
                mCoverHandler.prepareCoverImageViewContextMenu();
                return true;
        }

        return super.onOptionsItemSelected(menuItem);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Field editors callbacks">
    @Override
    public void onCheckListEditorSave(final @NonNull CheckListEditorDialogFragment dialog,
                                          final int destinationFieldId,
                                          final @NonNull List<CheckListItem<Bookshelf>> list) {
        dialog.dismiss();

        if (destinationFieldId == R.id.bookshelves) {
            ArrayList<Bookshelf> result = new Book.BookshelfCheckListItem().extractList(list);
            getBookManager().getBook().putBookshelfList(result);
            mFields.getField(destinationFieldId).setValue(getBookManager().getBook().getBookshelfListAsText());
        }
    }

    @Override
    public void onCheckListEditorCancel(final @NonNull CheckListEditorDialogFragment dialog,
                                        final int destinationFieldId) {
        dialog.dismiss();
    }

    @Override
    public void onTextFieldEditorSave(@NonNull final TextFieldEditorDialogFragment dialog,
                                      final int destinationFieldId,
                                      @NonNull final String newText) {
        dialog.dismiss();
        mFields.getField(destinationFieldId).setValue(newText);
    }

    @Override
    public void onTextFieldEditorCancel(@NonNull final TextFieldEditorDialogFragment dialog,
                                        final int destinationFieldId) {
        dialog.dismiss();
    }

    @Override
    public void onPartialDatePickerSave(@NonNull final PartialDatePickerDialogFragment dialog,
                                        final int destinationFieldId,
                                        @Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
        dialog.dismiss();
        mFields.getField(destinationFieldId).setValue(DateUtils.buildPartialDate(year, month, day));
    }

    @Override
    public void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                          final int destinationFieldId) {
        dialog.dismiss();
    }
    //</editor-fold>

    //<editor-fold desc="Field drop down lists">

    /**
     * Load a publisher list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    @NonNull
    private List<String> getPublishers() {
        if (mPublishers == null) {
            mPublishers = mDb.getPublishers();
        }
        return mPublishers;
    }

    /**
     * Load a genre list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of genres
     */
    @NonNull
    private List<String> getGenres() {
        if (mGenres == null) {
            mGenres = mDb.getGenres();
        }
        return mGenres;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of languages; full displayName
     */
    @NonNull
    private List<String> getLanguages() {
        if (mLanguages == null) {
            mLanguages = mDb.getLanguages();
        }
        return mLanguages;
    }

    /**
     * Load a format list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of formats
     */
    @NonNull
    private List<String> getFormats() {
        if (mFormats == null) {
            mFormats = mDb.getFormats();
        }
        return mFormats;
    }

    /**
     * Load a currency list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    private List<String> getListPriceCurrencyCodes() {
        if (mListPriceCurrencies == null) {
            mListPriceCurrencies = mDb.getCurrencyCodes(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY);
        }
        return mListPriceCurrencies;
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        Book book = getBookManager().getBook();
        switch (requestCode) {
            case EditAuthorListActivity.REQUEST_CODE: { /* dd74343a-50ff-4ce9-a2e4-a75f7bcf9e36 */
                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                    ArrayList<Author> list = BundleUtils.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, data.getExtras());
                    book.putAuthorList(list != null ? list : new ArrayList<Author>());

                    getBookManager().setDirty(true);
                } else {
                    // Even though the dialog was terminated, some authors MAY have been updated/added.
                    book.refreshAuthorList(mDb);
                }
                // The user may have edited or merged authors.
                // This will have already been applied to the database so no update is  necessary,
                // but we do need to update the data we display.
                boolean wasDirty = getBookManager().isDirty();
                populateAuthorListField(book);
                getBookManager().setDirty(wasDirty);
                break;
            }
            case EditSeriesListActivity.REQUEST_CODE: { /* bca659b6-dfb9-4a97-b651-5b05ad102400 */
                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                    ArrayList<Series> list = BundleUtils.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, data.getExtras());
                    book.putSeriesList(list != null ? list : new ArrayList<Series>());

                    populateSeriesListField(book);
                    getBookManager().setDirty(true);
                }
                break;
            }
            default:
                // handle any cover image result codes
                if (!mCoverHandler.onActivityResult(requestCode, resultCode, data)) {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
        }

        Tracker.exitOnActivityResult(this);
    }
}

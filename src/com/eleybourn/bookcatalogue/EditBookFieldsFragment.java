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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.CoverHandler;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab.
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener<Bookshelf>,
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener,
        TextFieldEditorDialogFragment.OnTextFieldEditorResultsListener {

    /** Fragment manager tag. */
    public static final String TAG = EditBookFieldsFragment.class.getSimpleName();

    private static final int REQ_EDIT_AUTHORS = 0;
    private static final int REQ_EDIT_SERIES = 1;

    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     */
    private List<String> mFormats;
    /** Field drop down list. */
    private List<String> mGenres;
    /** Field drop down list. */
    private List<String> mLanguages;
    /** Field drop down list. */
    private List<String> mPublishers;
    /** Field drop down list. */
    private List<String> mListPriceCurrencies;

    private CoverHandler mCoverHandler;

    /* ------------------------------------------------------------------------------------------ */
    @Override
    @NonNull
    protected BookManager getBookManager() {
        return ((EditBookFragment) requireParentFragment()).getBookManager();
    }

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_fields, container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ViewUtils.fixFocusSettings(requireView());
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();
        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        Field field;

        // book fields

        // defined, but handled manually
        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR)
               .getView().setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(@NonNull final View v) {
                        Intent intent = new Intent(requireActivity(), EditAuthorListActivity.class);
                        intent.putExtra(UniqueId.BKEY_AUTHOR_ARRAY,
                                        getBookManager().getBook()
                                                        .getList(UniqueId.BKEY_AUTHOR_ARRAY));

                        intent.putExtra(UniqueId.KEY_ID, getBookManager().getBook().getId());
                        intent.putExtra(UniqueId.KEY_TITLE,
                                        mFields.getField(R.id.title).getValue().toString());
                        startActivityForResult(intent, REQ_EDIT_AUTHORS);
                    }
                });

        // defined, but handled manually
        mFields.add(R.id.name, "", UniqueId.KEY_SERIES)
               .getView().setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(@NonNull final View v) {
                        Intent intent = new Intent(requireActivity(), EditSeriesListActivity.class);
                        intent.putExtra(UniqueId.BKEY_SERIES_ARRAY,
                                        getBookManager().getBook()
                                                        .getList(UniqueId.BKEY_SERIES_ARRAY));

                        intent.putExtra(UniqueId.KEY_ID, getBookManager().getBook().getId());
                        intent.putExtra(UniqueId.KEY_TITLE,
                                        mFields.getField(R.id.title).getValue().toString());
                        startActivityForResult(intent, REQ_EDIT_SERIES);
                    }
                });

        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES);
        mFields.add(R.id.title, UniqueId.KEY_TITLE);
        mFields.add(R.id.isbn, UniqueId.KEY_BOOK_ISBN);

        field = mFields.add(R.id.description, UniqueId.KEY_BOOK_DESCRIPTION)
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
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_listed_currency,
                        getListPriceCurrencyCodes());

        /* Anthology is provided as a boolean, see {@link Book#initValidators()}*/
        mFields.add(R.id.is_anthology, Book.HAS_MULTIPLE_WORKS)
               .getView().setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(@NonNull final View v) {
                        Checkable cb = (Checkable) v;
                        EditBookFragment frag = (EditBookFragment) requireParentFragment();
                        frag.addTOCTab(cb.isChecked());
                    }
                });

        // defined, but handled manually
        Field coverField = mFields.add(R.id.coverImage, "", UniqueId.BKEY_THUMBNAIL);
        mCoverHandler = new CoverHandler(this, mDb, getBookManager(),
                                         coverField, mFields.getField(R.id.isbn));


        // Personal fields

        // defined, but handled manually (reminder: storing the list back into the book
        // is handled by onCheckListEditorSave)
        field = mFields.add(R.id.bookshelves, "", UniqueId.KEY_BOOKSHELF_NAME);
        initCheckListEditor(TAG, field, R.string.lbl_bookshelves_long,
                            new CheckListEditorListGetter<Bookshelf>() {
                                @Override
                                public ArrayList<CheckListItem<Bookshelf>> getList() {
                                    return getBookManager().getBook()
                                                           .getEditableBookshelvesList(mDb);
                                }
                            });
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getId());
        super.onLoadFieldsFromBook(book, setAllFrom);

        // new book ? load data fields from Extras
        if (book.getId() <= 0) {
            Bundle extras = requireActivity().getIntent().getExtras();
            populateNewBookFieldsFromBundle(book, extras);
        }

        populateAuthorListField(book);
        populateSeriesListField(book);
        ArrayList<Bookshelf> bsList = book.getList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        mFields.getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));

        boolean isAnt = book.isBitSet(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                                      TocEntry.Type.MULTIPLE_WORKS);
        mFields.getField(R.id.is_anthology).setValue(isAnt ? "1" : "0");

        mCoverHandler.populateCoverView();

        // Restore default visibility
        showHideFields(false);

        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    private void populateNewBookFieldsFromBundle(@NonNull final Book book,
                                                 @Nullable final Bundle bundle) {
        // Check if we have any data, for example from a Search
        if (bundle != null) {
            Bundle values = bundle.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (values != null) {
                // if we do, add if not there yet
                mFields.setAllFrom(values, false);
            }
        }

        // If the new book is not on any Bookshelf, use the current bookshelf as default
        final ArrayList<Bookshelf> list = book.getList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        if (list.isEmpty()) {

            Bookshelf bookshelf = null;
            String name = Prefs.getPrefs()
                               .getString(Bookshelf.PREF_BOOKSHELF_CURRENT, null);
            if (name != null && !name.isEmpty()) {
                bookshelf = mDb.getBookshelfByName(name);
            }
            if (bookshelf == null) /* || name.isEmpty() */ {
                // unlikely to be true, but use default just in case
                bookshelf = Bookshelf.getDefaultBookshelf(mDb);
            }

            mFields.getField(R.id.bookshelves).setValue(bookshelf.getName());
            // add to set, and store in book.
            list.add(bookshelf);
            book.putList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> list = book.getList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty() && Utils.pruneList(mDb, list)) {
            getBookManager().setDirty(true);
            book.putList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }

        String newText = book.getAuthorTextShort();
        if (newText.isEmpty()) {
            newText = getString(R.string.btn_set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    private void populateSeriesListField(@NonNull final Book book) {
        boolean visible = mFields.getField(R.id.name).isVisible();
        if (visible) {
            ArrayList<Series> list = book.getList(UniqueId.BKEY_SERIES_ARRAY);
            if (!list.isEmpty() && Utils.pruneList(mDb, list)) {
                getBookManager().setDirty(true);
                book.putList(UniqueId.BKEY_SERIES_ARRAY, list);
            }

            String newText = book.getSeriesTextShort();
            if (newText.isEmpty()) {
                newText = getString(R.string.btn_set_series);
            }
            mFields.getField(R.id.name).setValue(newText);
        }
        requireView().findViewById(R.id.lbl_series).setVisibility(
                visible ? View.VISIBLE : View.GONE);
        requireView().findViewById(R.id.name).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    @Override
    @CallSuper
    public void onPause() {
        mCoverHandler.dismissCoverBrowser();
        super.onPause();
    }

    /**
     * Overriding to get extra debug.
     */
    @Override
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getId());
        super.onSaveFieldsToBook(book);
        Tracker.exitOnSaveFieldsToBook(this, book.getId());
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
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (mFields.getField(R.id.coverImage).isVisible()) {
            menu.add(Menu.NONE, R.id.SUBMENU_THUMB_REPLACE, 0, R.string.menu_cover_replace)
                .setIcon(R.drawable.ic_add_a_photo)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.SUBMENU_THUMB_REPLACE:
                // Show the context menu for the cover thumbnail
                mCoverHandler.prepareCoverImageViewContextMenu();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Field editors callbacks">
    @Override
    public void onCheckListEditorSave(final int destinationFieldId,
                                      @NonNull final List<CheckListItem<Bookshelf>> list) {

        if (destinationFieldId == R.id.bookshelves) {
            ArrayList<Bookshelf> bsList = new Book.BookshelfCheckListItem().extractList(list);
            getBookManager().getBook().putList(UniqueId.BKEY_BOOKSHELF_ARRAY, bsList);
            mFields.getField(destinationFieldId).setValue(Bookshelf.toDisplayString(bsList));
        }
    }

    @Override
    public void onTextFieldEditorSave(final int destinationFieldId,
                                      @NonNull final String newText) {
        mFields.getField(destinationFieldId).setValue(newText);
    }

    @Override
    public void onPartialDatePickerSave(final int destinationFieldId,
                                        @Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
        mFields.getField(destinationFieldId).setValue(DateUtils.buildPartialDate(year, month, day));
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
            mPublishers = mDb.getPublisherNames();
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
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        Book book = getBookManager().getBook();
        switch (requestCode) {
            case REQ_EDIT_AUTHORS:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                        //noinspection ConstantConditions
                        ArrayList<Author> list =
                                data.getExtras().getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                        book.putList(UniqueId.BKEY_AUTHOR_ARRAY,
                                     list != null ? list : new ArrayList<Author>());

                        getBookManager().setDirty(true);
                    } else {
                        // Even though the dialog was terminated,
                        // some authors MAY have been modified.
                        book.refreshAuthorList(mDb);
                    }

                    boolean wasDirty = getBookManager().isDirty();
                    populateAuthorListField(book);
                    getBookManager().setDirty(wasDirty);

                }
                break;

            case REQ_EDIT_SERIES:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                        //noinspection ConstantConditions
                        ArrayList<Series> list =
                                data.getExtras().getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                        book.putList(UniqueId.BKEY_SERIES_ARRAY,
                                     list != null ? list : new ArrayList<Series>());

                        populateSeriesListField(book);
                        getBookManager().setDirty(true);
                    } else {
                        // Even though the dialog was terminated,
                        // some series MAY have been modified.
                        book.refreshSeriesList(mDb);
                    }

                    boolean wasDirty = getBookManager().isDirty();
                    populateSeriesListField(book);
                    getBookManager().setDirty(wasDirty);

                }
                break;

            default:
                // handle any cover image request codes
                if (!mCoverHandler.onActivityResult(requestCode, resultCode, data)) {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
        }

        Tracker.exitOnActivityResult(this);
    }
}

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
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab.
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener<Bookshelf>,
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
    private List<String> mGenres;

    /** Handles cover replacement, rotation, etc. */
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

        Field field;

        // book fields

        mFields.add(R.id.title, DatabaseDefinitions.KEY_TITLE);
        mFields.add(R.id.isbn, DatabaseDefinitions.KEY_ISBN);
        mFields.add(R.id.description, DatabaseDefinitions.KEY_DESCRIPTION);

        // ENHANCE: {@link Fields.ImageViewAccessor}
//        field = mFields.add(R.id.coverImage, UniqueId.KEY_BOOK_UUID, UniqueId.BKEY_COVER_IMAGE);
        field = mFields.add(R.id.coverImage, "", UniqueId.BKEY_COVER_IMAGE);
        ImageUtils.ImageSize imageSize = ImageUtils.getImageSizes(requireActivity());
//        Fields.ImageViewAccessor iva = field.getFieldDataAccessor();
//        iva.setMaxSize( imageSize.small, imageSize.small);
        mCoverHandler = new CoverHandler(this, mDb, getBookManager(),
                                         mFields.getField(R.id.isbn), field,
                                         imageSize.small, imageSize.small);

        // defined, but handled manually
        mFields.add(R.id.author, "", DatabaseDefinitions.KEY_AUTHOR)
               .getView().setOnClickListener(
                v -> {
                    String title = mFields.getField(R.id.title).getValue().toString().trim();
                    ArrayList<Author> list =
                            getBookManager().getBook().getParcelableArrayList(
                                    UniqueId.BKEY_AUTHOR_ARRAY);

                    Intent intent = new Intent(requireActivity(), EditAuthorListActivity.class)
                            .putExtra(DatabaseDefinitions.KEY_ID, getBookManager().getBook().getId())
                            .putExtra(DatabaseDefinitions.KEY_TITLE, title)
                            .putExtra(UniqueId.BKEY_AUTHOR_ARRAY, list);
                    startActivityForResult(intent, REQ_EDIT_AUTHORS);
                });

        // defined, but handled manually
        mFields.add(R.id.series, "", DatabaseDefinitions.KEY_SERIES)
               .getView().setOnClickListener(
                v -> {
                    String title = mFields.getField(R.id.title).getValue().toString().trim();
                    ArrayList<Series> list =
                            getBookManager().getBook().getParcelableArrayList(
                                    UniqueId.BKEY_SERIES_ARRAY);

                    Intent intent = new Intent(requireActivity(), EditSeriesListActivity.class)
                            .putExtra(DatabaseDefinitions.KEY_ID, getBookManager().getBook().getId())
                            .putExtra(DatabaseDefinitions.KEY_TITLE, title)
                            .putExtra(UniqueId.BKEY_SERIES_ARRAY, list);
                    startActivityForResult(intent, REQ_EDIT_SERIES);
                });

        field = mFields.add(R.id.genre, DatabaseDefinitions.KEY_GENRE);
        initValuePicker(field, R.string.lbl_genre, R.id.btn_genre, getGenres());

        // Personal fields

        // defined, but handled manually (reminder: storing the list back into the book
        // is handled by onCheckListEditorSave)
        field = mFields.add(R.id.bookshelves, "", DatabaseDefinitions.KEY_BOOKSHELF);
        //noinspection ConstantConditions
        initCheckListEditor(getTag(), field, R.string.lbl_bookshelves_long,
                            () -> getBookManager().getBook().getEditableBookshelvesList(mDb));
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getId());
        super.onLoadFieldsFromBook(book, setAllFrom);

        populateAuthorListField(book);
        populateSeriesListField(book);

        ArrayList<Bookshelf> bsList = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        mFields.getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));

        // ENHANCE: {@link Fields.ImageViewAccessor}
        // allow the field to known the uuid of the book, so it can load 'itself'
        mFields.getField(R.id.coverImage)
               .getView()
               .setTag(R.id.TAG_UUID, book.get(DatabaseDefinitions.KEY_BOOK_UUID));
        mCoverHandler.updateCoverView();

        // Restore default visibility
        showHideFields(false);

        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    /**
     * Handle the Bookshelf default.
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected void populateNewBookFieldsFromBundle(@NonNull final Book book,
                                                   @Nullable final Bundle bundle) {
        super.populateNewBookFieldsFromBundle(book, bundle);

        // If the new book is not on any Bookshelf, use the current bookshelf as default
        final ArrayList<Bookshelf> list = book.getParcelableArrayList(
                UniqueId.BKEY_BOOKSHELF_ARRAY);
        if (list.isEmpty()) {

            Bookshelf bookshelf = null;
            String name = App.getPrefs()
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
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty() && Utils.pruneList(mDb, list)) {
            getBookManager().setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }

        //noinspection ConstantConditions
        String newText = book.getAuthorTextShort(getContext());
        if (newText.isEmpty()) {
            newText = getString(R.string.btn_set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    private void populateSeriesListField(@NonNull final Book book) {
        boolean visible = mFields.getField(R.id.series).isVisible();
        if (visible) {
            ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
            if (!list.isEmpty() && Utils.pruneList(mDb, list)) {
                getBookManager().setDirty(true);
                book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
            }

            //noinspection ConstantConditions
            String newText = book.getSeriesTextShort(getContext());
            if (newText.isEmpty()) {
                newText = getString(R.string.btn_set_series);
            }
            mFields.getField(R.id.series).setValue(newText);
        }
        requireView().findViewById(R.id.lbl_series).setVisibility(
                visible ? View.VISIBLE : View.GONE);
        requireView().findViewById(R.id.series).setVisibility(visible ? View.VISIBLE : View.GONE);
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
            getBookManager().getBook().putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY,
                                                              bsList);
            mFields.getField(destinationFieldId).setValue(Bookshelf.toDisplayString(bsList));
        }
    }

    @Override
    public void onTextFieldEditorSave(final int destinationFieldId,
                                      @NonNull final String newText) {
        mFields.getField(destinationFieldId).setValue(newText);
    }

    //</editor-fold>

    //<editor-fold desc="Field drop down lists">

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
                        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                    list != null ? list : new ArrayList<>());

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
                        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                    list != null ? list : new ArrayList<>());

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

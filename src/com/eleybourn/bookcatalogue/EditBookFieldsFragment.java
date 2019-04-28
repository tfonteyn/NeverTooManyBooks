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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
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
     * <li>{@link #onLoadFieldsFromBook} from base class onResume
     * <li>{@link #onSaveFieldsToBook} from base class onPause
     * <p>
     * <p>{@inheritDoc}
     */
    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ViewUtils.fixFocusSettings(getView());
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();

        // book fields

        mFields.add(R.id.title, DBDefinitions.KEY_TITLE);
        mFields.add(R.id.isbn, DBDefinitions.KEY_ISBN);
        mFields.add(R.id.description, DBDefinitions.KEY_DESCRIPTION);

        // ENHANCE: {@link Fields.ImageViewAccessor}
//        field = mFields.add(R.id.coverImage, UniqueId.KEY_BOOK_UUID, UniqueId.BKEY_COVER_IMAGE);
        Field coverImageField = mFields.add(R.id.coverImage, "", UniqueId.BKEY_COVER_IMAGE);
        ImageUtils.DisplaySizes displaySizes = ImageUtils.getDisplaySizes(requireActivity());
//        Fields.ImageViewAccessor iva = field.getFieldDataAccessor();
//        iva.setMaxSize( imageSize.small, imageSize.small);
        //noinspection ConstantConditions
        mCoverHandler = new CoverHandler(getFragmentManager(),this, mDb,
                                         mBookModel.getBook(),
                                         mFields.getField(R.id.isbn).getView(),
                                         coverImageField.getView(),
                                         displaySizes.small, displaySizes.small);

        // defined, but handled manually
        mFields.add(R.id.author, "", DBDefinitions.KEY_AUTHOR)
               .getView().setOnClickListener(
                v -> {
                    String title = mFields.getField(R.id.title).getValue().toString().trim();
                    ArrayList<Author> list =
                            mBookModel.getBook().getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

                    Intent intent = new Intent(requireActivity(), EditAuthorListActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, mBookModel.getBook().getId())
                            .putExtra(DBDefinitions.KEY_TITLE, title)
                            .putExtra(UniqueId.BKEY_AUTHOR_ARRAY, list);
                    startActivityForResult(intent, REQ_EDIT_AUTHORS);
                });

        // defined, but handled manually
        mFields.add(R.id.series, "", DBDefinitions.KEY_SERIES)
               .getView().setOnClickListener(
                v -> {
                    String title = mFields.getField(R.id.title).getValue().toString().trim();
                    ArrayList<Series> list =
                            mBookModel.getBook().getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

                    Intent intent = new Intent(requireActivity(), EditSeriesListActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, mBookModel.getBook().getId())
                            .putExtra(DBDefinitions.KEY_TITLE, title)
                            .putExtra(UniqueId.BKEY_SERIES_ARRAY, list);
                    startActivityForResult(intent, REQ_EDIT_SERIES);
                });

        Field field;

        field = mFields.add(R.id.genre, DBDefinitions.KEY_GENRE);
        initValuePicker(field, R.string.lbl_genre, R.id.btn_genre, getGenres());


        // Personal fields

        // defined, but handled manually (reminder: storing the list back into the book
        // is handled by onCheckListEditorSave)
        field = mFields.add(R.id.bookshelves, "", DBDefinitions.KEY_BOOKSHELF);
        //noinspection ConstantConditions
        initCheckListEditor(getTag(), field, R.string.lbl_bookshelves_long,
                            () -> mBookModel.getBook().getEditableBookshelvesList(mDb));
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(final boolean setAllFrom) {
        super.onLoadFieldsFromBook(setAllFrom);

        populateAuthorListField();
        populateSeriesListField();

        ArrayList<Bookshelf> bsList = mBookModel.getBook().getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        mFields.getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));

        // ENHANCE: {@link Fields.ImageViewAccessor}
        // allow the field to known the uuid of the book, so it can load 'itself'
        mFields.getField(R.id.coverImage).getView()
               .setTag(R.id.TAG_UUID, mBookModel.getBook().get(DBDefinitions.KEY_BOOK_UUID));
        mCoverHandler.updateCoverView();

        // Restore default visibility
        showHideFields(false);
    }

    //</editor-fold>

    //<editor-fold desc="Populate">

    /**
     * Handle the Bookshelf default.
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    protected void populateNewBookFieldsFromBundle(@Nullable final Bundle bundle) {
        super.populateNewBookFieldsFromBundle(bundle);

        // If the new book is not on any Bookshelf, use the current bookshelf as default
        final ArrayList<Bookshelf> list =
                mBookModel.getBook().getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

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
            mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField() {
        ArrayList<Author> list = mBookModel.getBook()
                                           .getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty() && Utils.pruneList(mDb, list)) {
            mBookModel.setDirty(true);
            mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }

        //noinspection ConstantConditions
        String newText = mBookModel.getBook().getAuthorTextShort(getContext());
        if (newText.isEmpty()) {
            newText = getString(R.string.btn_set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    private void populateSeriesListField() {

        if (mFields.getField(R.id.series).isUsed()) {

            ArrayList<Series> list = mBookModel.getBook()
                                               .getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
            if (!list.isEmpty() && Utils.pruneList(mDb, list)) {
                mBookModel.setDirty(true);
                mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
            }

            //noinspection ConstantConditions
            String newText = mBookModel.getBook().getSeriesTextShort(getContext());
            if (newText.isEmpty()) {
                newText = getString(R.string.btn_set_series);
            }
            mFields.getField(R.id.series).setValue(newText);

            setVisibility(View.VISIBLE, R.id.series, R.id.lbl_series);

        } else {
            setVisibility(View.GONE, R.id.series, R.id.lbl_series);
        }
    }

    //</editor-fold>

    //<editor-fold desc="Fragment shutdown">

    @Override
    @CallSuper
    public void onPause() {
        mCoverHandler.dismissCoverBrowser();
        super.onPause();
    }

    //</editor-fold>

    //<editor-fold desc="Field editors callbacks">

    @Override
    public void onCheckListEditorSave(final int destinationFieldId,
                                      @NonNull final List<CheckListItem<Bookshelf>> list) {

        if (destinationFieldId == R.id.bookshelves) {
            ArrayList<Bookshelf> bsList = new Book.BookshelfCheckListItem().extractList(list);
            mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, bsList);
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

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_EDIT_AUTHORS:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                        //noinspection ConstantConditions
                        ArrayList<Author> list =
                                data.getExtras().getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                        mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                    list != null ? list : new ArrayList<>());

                        mBookModel.setDirty(true);
                    } else {
                        // Even though the dialog was terminated,
                        // some authors MAY have been modified.
                        mBookModel.getBook().refreshAuthorList(mDb);
                    }

                    boolean wasDirty = mBookModel.isDirty();
                    populateAuthorListField();
                    mBookModel.setDirty(wasDirty);

                }
                break;

            case REQ_EDIT_SERIES:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                        //noinspection ConstantConditions
                        ArrayList<Series> list =
                                data.getExtras().getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                        mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                    list != null ? list : new ArrayList<>());

                        populateSeriesListField();
                        mBookModel.setDirty(true);
                    } else {
                        // Even though the dialog was terminated,
                        // some series MAY have been modified.
                        mBookModel.getBook().refreshSeriesList(mDb);
                    }

                    boolean wasDirty = mBookModel.isDirty();
                    populateSeriesListField();
                    mBookModel.setDirty(wasDirty);

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

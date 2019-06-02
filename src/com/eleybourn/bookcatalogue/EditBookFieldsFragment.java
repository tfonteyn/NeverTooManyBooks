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

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
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
        extends EditBookBaseFragment<Bookshelf> {

    /** Fragment manager tag. */
    public static final String TAG = EditBookFieldsFragment.class.getSimpleName();

    private static final int REQ_EDIT_AUTHORS = 0;
    private static final int REQ_EDIT_SERIES = 1;

    /** Handles cover replacement, rotation, etc. */
    private CoverHandler mCoverHandler;

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
     * <ul>
     * <li>{@link #onLoadFieldsFromBook} from base class onResume</li>
     * <li>{@link #onSaveFieldsToBook} from base class onPause</li>
     * </ul>
     * {@inheritDoc}
     */
    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do other stuff here that might affect the view.

        // Fix up the views
        ViewUtils.fixFocusSettings(requireView());
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        Book book = mBookBaseFragmentModel.getBook();

        // book fields

        fields.add(R.id.title, DBDefinitions.KEY_TITLE);
        fields.add(R.id.isbn, DBDefinitions.KEY_ISBN);
        fields.add(R.id.description, DBDefinitions.KEY_DESCRIPTION);

        // ENHANCE: {@link Fields.ImageViewAccessor}
//        field = fields.add(R.id.coverImage, UniqueId.KEY_BOOK_UUID, UniqueId.BKEY_COVER_IMAGE);
        Field coverImageField = fields.add(R.id.coverImage, "", UniqueId.BKEY_COVER_IMAGE);
        //noinspection ConstantConditions
        ImageUtils.DisplaySizes displaySizes = ImageUtils.getDisplaySizes(getActivity());
//        Fields.ImageViewAccessor iva = field.getFieldDataAccessor();
//        iva.setMaxSize( imageSize.small, imageSize.small);
        mCoverHandler = new CoverHandler(this, mBookBaseFragmentModel.getDb(),
                                         book,
                                         fields.getField(R.id.isbn).getView(),
                                         coverImageField.getView(),
                                         displaySizes.small, displaySizes.small);

        // defined, but handled manually
        fields.add(R.id.author, "", DBDefinitions.KEY_AUTHOR)
              .getView().setOnClickListener(v -> {
            String title = fields.getField(R.id.title).getValue().toString().trim();
            ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

            Intent intent = new Intent(getActivity(), EditAuthorListActivity.class)
                    .putExtra(DBDefinitions.KEY_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(UniqueId.BKEY_AUTHOR_ARRAY, list);
            startActivityForResult(intent, REQ_EDIT_AUTHORS);
        });

        // defined, but handled manually
        fields.add(R.id.series, "", DBDefinitions.KEY_SERIES)
              .getView().setOnClickListener(v -> {
            // use the current title.
            String title = fields.getField(R.id.title).getValue().toString().trim();
            ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

            Intent intent = new Intent(getActivity(), EditSeriesListActivity.class)
                    .putExtra(DBDefinitions.KEY_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(UniqueId.BKEY_SERIES_ARRAY, list);
            startActivityForResult(intent, REQ_EDIT_SERIES);
        });

        Field field;

        field = fields.add(R.id.genre, DBDefinitions.KEY_GENRE);
        initValuePicker(field, R.string.lbl_genre, R.id.btn_genre,
                        mBookBaseFragmentModel.getGenres());

        // Personal fields

        // defined, but handled manually (reminder: storing the list back into the book
        // is handled by onCheckListEditorSave)
        field = fields.add(R.id.bookshelves, "", DBDefinitions.KEY_BOOKSHELF);
        initCheckListEditor(field, R.string.lbl_bookshelves_long, () ->
                book.getEditableBookshelvesList(mBookBaseFragmentModel.getDb()));
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        Book book = mBookBaseFragmentModel.getBook();

        populateAuthorListField();
        populateSeriesListField();

        ArrayList<Bookshelf> bsList = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        getField(R.id.bookshelves).setValue(Bookshelf.toDisplayString(bsList));

        // ENHANCE: {@link Fields.ImageViewAccessor}
        // allow the field to known the uuid of the book, so it can load 'itself'
        getField(R.id.coverImage).getView()
                                 .setTag(R.id.TAG_UUID, book.get(DBDefinitions.KEY_BOOK_UUID));
        mCoverHandler.updateCoverView();

        // Restore default visibility
        showHideFields(false);
    }

    /**
     * Handle the Bookshelf default.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void populateNewBookFieldsFromBundle(@Nullable final Bundle bundle) {
        super.populateNewBookFieldsFromBundle(bundle);

        Book book = mBookBaseFragmentModel.getBook();

        // If the new book is not on any Bookshelf, use the current bookshelf as default
        final ArrayList<Bookshelf> list =
                book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

        if (list.isEmpty()) {
            //noinspection ConstantConditions
            Bookshelf bookshelf = mBookBaseFragmentModel.getBookshelf(getContext());

            getField(R.id.bookshelves).setValue(bookshelf.getName());
            // add to set, and store in book.
            list.add(bookshelf);
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField() {
        Book book = mBookBaseFragmentModel.getBook();

        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty() && Utils.pruneList(mBookBaseFragmentModel.getDb(), list)) {
            mBookBaseFragmentModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }

        //noinspection ConstantConditions
        String newText = book.getAuthorTextShort(getContext());
        if (newText.isEmpty()) {
            newText = getString(R.string.btn_set_authors);
        }
        getField(R.id.author).setValue(newText);
    }

    private void populateSeriesListField() {
        Book book = mBookBaseFragmentModel.getBook();

        if (getField(R.id.series).isUsed()) {

            ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
            if (!list.isEmpty() && Utils.pruneList(mBookBaseFragmentModel.getDb(), list)) {
                mBookBaseFragmentModel.setDirty(true);
                book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
            }

            //noinspection ConstantConditions
            String newText = book.getSeriesTextShort(getContext());
            if (newText.isEmpty()) {
                newText = getString(R.string.btn_set_series);
            }
            getField(R.id.series).setValue(newText);
            setVisibility(View.VISIBLE, R.id.series, R.id.lbl_series);

        } else {
            setVisibility(View.GONE, R.id.series, R.id.lbl_series);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        Book book = mBookBaseFragmentModel.getBook();

        switch (requestCode) {
            case REQ_EDIT_AUTHORS:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                        ArrayList<Author> list =
                                data.getParcelableArrayListExtra(UniqueId.BKEY_AUTHOR_ARRAY);
                        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                                    list != null ? list : new ArrayList<>(0));

                        mBookBaseFragmentModel.setDirty(true);
                    } else {
                        // Even though the dialog was terminated,
                        // some authors MAY have been modified.
                        mBookBaseFragmentModel.refreshAuthorList();
                    }

                    boolean wasDirty = mBookBaseFragmentModel.isDirty();
                    populateAuthorListField();
                    mBookBaseFragmentModel.setDirty(wasDirty);

                }
                break;

            case REQ_EDIT_SERIES:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                        ArrayList<Series> list =
                                data.getParcelableArrayListExtra(UniqueId.BKEY_SERIES_ARRAY);
                        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                                    list != null ? list : new ArrayList<>(0));

                        populateSeriesListField();
                        mBookBaseFragmentModel.setDirty(true);
                    } else {
                        // Even though the dialog was terminated,
                        // some series MAY have been modified.
                        mBookBaseFragmentModel.refreshSeriesList();
                    }

                    boolean wasDirty = mBookBaseFragmentModel.isDirty();
                    populateSeriesListField();
                    mBookBaseFragmentModel.setDirty(wasDirty);

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

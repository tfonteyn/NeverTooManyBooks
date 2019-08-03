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

package com.hardbacknutter.nevertomanybooks;

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

import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertomanybooks.debug.Tracker;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.Book;
import com.hardbacknutter.nevertomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertomanybooks.entities.Series;
import com.hardbacknutter.nevertomanybooks.utils.Csv;
import com.hardbacknutter.nevertomanybooks.utils.ImageUtils;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab.
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment<Bookshelf> {

    /** Fragment manager tag. */
    public static final String TAG = "EditBookFieldsFragment";

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

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusSettings.fix(getView());
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

        Field coverImageField = fields.add(R.id.coverImage,
                                           DBDefinitions.KEY_BOOK_UUID, UniqueId.BKEY_IMAGE)
                                      .setScale(ImageUtils.SCALE_MEDIUM);

        mCoverHandler = new CoverHandler(this, mBookBaseFragmentModel.getDb(),
                                         book,
                                         fields.getField(R.id.isbn).getView(),
                                         coverImageField.getView(),
                                         ImageUtils.SCALE_MEDIUM);

        // defined, but handled manually
        fields.add(R.id.author, "", DBDefinitions.KEY_FK_AUTHOR)
              .getView().setOnClickListener(v -> {
            String title = fields.getField(R.id.title).getValue().toString();
            ArrayList<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

            Intent intent = new Intent(getContext(), EditAuthorListActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(UniqueId.BKEY_AUTHOR_ARRAY, authors);
            startActivityForResult(intent, REQ_EDIT_AUTHORS);
        });

        // defined, but handled manually
        fields.add(R.id.series, "", DBDefinitions.KEY_SERIES_TITLE)
              .getView().setOnClickListener(v -> {
            // use the current title.
            String title = fields.getField(R.id.title).getValue().toString();
            ArrayList<Series> series = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

            Intent intent = new Intent(getContext(), EditSeriesListActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(UniqueId.BKEY_SERIES_ARRAY, series);
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
                mBookBaseFragmentModel.getEditableBookshelvesList());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        Book book = mBookBaseFragmentModel.getBook();

        populateAuthorListField(book);
        populateSeriesListField(book);
        populateBookshelvesField(book);

        // hide unwanted fields
        showOrHideFields(false);
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

    private void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        //noinspection ConstantConditions
        if (!list.isEmpty()
                && ItemWithFixableId.pruneList(getContext(), mBookBaseFragmentModel.getDb(), list)) {
            mBookBaseFragmentModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }
        //noinspection ConstantConditions
        String name = book.getAuthorTextShort(getContext());
        if (name.isEmpty() && book.containsKey(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
            // allow this fallback. It's used after a search that did not return results,
            // in which case it contains whatever the user typed.
            name = book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
        }
        getField(R.id.author).setValue(name);
    }

    private void populateSeriesListField(@NonNull final Book book) {
        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        //noinspection ConstantConditions
        if (!list.isEmpty()
                && ItemWithFixableId.pruneList(getContext(), mBookBaseFragmentModel.getDb(), list)) {
            mBookBaseFragmentModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
        }

        //noinspection ConstantConditions
        getField(R.id.series).setValue(book.getSeriesTextShort(getContext()));
    }

    /**
     * The bookshelves field is a single csv String.
     */
    private void populateBookshelvesField(@NonNull final Book book) {
        ArrayList<Bookshelf> list = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        getField(R.id.bookshelves).setValue(Csv.join(", ", list, Bookshelf::getName));
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
                        if (list == null) {
                            list = new ArrayList<>(0);
                        }
                        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
                        mBookBaseFragmentModel.setDirty(true);

                    } else {
                        // Even though the dialog was terminated,
                        // some authors MAY have been modified.
                        mBookBaseFragmentModel.refreshAuthorList();
                    }

                    boolean wasDirty = mBookBaseFragmentModel.isDirty();
                    populateAuthorListField(book);
                    mBookBaseFragmentModel.setDirty(wasDirty);

                }
                break;

            case REQ_EDIT_SERIES:
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                            && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                        ArrayList<Series> list =
                                data.getParcelableArrayListExtra(UniqueId.BKEY_SERIES_ARRAY);
                        if (list == null) {
                            list = new ArrayList<>(0);
                        }
                        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
//                        populateSeriesListField();
                        mBookBaseFragmentModel.setDirty(true);

                    } else {
                        // Even though the dialog was terminated,
                        // some series MAY have been modified.
                        //noinspection ConstantConditions
                        mBookBaseFragmentModel.refreshSeriesList(getContext());
                    }

                    boolean wasDirty = mBookBaseFragmentModel.isDirty();
                    populateSeriesListField(book);
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

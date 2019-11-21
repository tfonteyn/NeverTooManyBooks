/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.baseactivity.EditObjectListModel;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.FocusFixer;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.EditIsbn;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab.
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment<Bookshelf> {

    public static final String TAG = "EditBookFieldsFragment";

    private static final int REQ_EDIT_AUTHORS = 0;
    private static final int REQ_EDIT_SERIES = 1;

    /** Handles cover replacement, rotation, etc. */
    private CoverHandler mCoverHandler;

    private View mTitleView;
    private View mAuthorView;
    private View mSeriesView;
    private View mDescriptionView;
    private View mBookshelvesView;

    private EditText mIsbnView;
    private ImageView mCoverImageView;
    private AutoCompleteTextView mGenreView;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_book_fields, container, false);
        mAuthorView = view.findViewById(R.id.author);
        mTitleView = view.findViewById(R.id.title);
        mSeriesView = view.findViewById(R.id.series);
        mDescriptionView = view.findViewById(R.id.description);
        mIsbnView = view.findViewById(R.id.isbn);
        mCoverImageView = view.findViewById(R.id.coverImage);
        mGenreView = view.findViewById(R.id.genre);
        mBookshelvesView = view.findViewById(R.id.bookshelves);

        return view;
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookDetailsFragment}.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        Book book = mBookModel.getBook();

        // book fields

        fields.addString(R.id.title, mTitleView, DBDefinitions.KEY_TITLE);

        // defined, but fetched/stored manually
        // Storing the list back into the book is handled by onCheckListEditorSave
        fields.addString(R.id.author, mAuthorView, "", DBDefinitions.KEY_FK_AUTHOR);
        mAuthorView.setOnClickListener(v -> {
            String title = fields.getField(R.id.title).getValue().toString();
            ArrayList<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

            Intent intent = new Intent(getContext(), EditBookAuthorsActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(DBDefinitions.KEY_LANGUAGE,
                              book.getString(DBDefinitions.KEY_LANGUAGE))
                    .putExtra(UniqueId.BKEY_AUTHOR_ARRAY, authors);
            startActivityForResult(intent, REQ_EDIT_AUTHORS);
        });

        // defined, but fetched/stored manually
        // Storing the list back into the book is handled by onCheckListEditorSave
        fields.addString(R.id.series, mSeriesView, "", DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);
        mSeriesView.setOnClickListener(v -> {
            // use the current title.
            String title = fields.getField(R.id.title).getValue().toString();
            ArrayList<Series> series = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

            Intent intent = new Intent(getContext(), EditBookSeriesActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(DBDefinitions.KEY_LANGUAGE,
                              book.getString(DBDefinitions.KEY_LANGUAGE))
                    .putExtra(UniqueId.BKEY_SERIES_ARRAY, series);
            startActivityForResult(intent, REQ_EDIT_SERIES);
        });

        fields.addString(R.id.description, mDescriptionView, DBDefinitions.KEY_DESCRIPTION)
              .setRelatedFields(R.id.lbl_description);

        fields.addString(R.id.isbn, mIsbnView, DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);
        mIsbnView.addTextChangedListener(new EditIsbn.ValidationTextWatcher(mIsbnView));

        fields.addString(R.id.coverImage, mCoverImageView, DBDefinitions.KEY_BOOK_UUID,
                         UniqueId.BKEY_IMAGE)
              .setScale(ImageUtils.SCALE_MEDIUM);
        mCoverHandler = new CoverHandler(this, mBookModel.getDb(), book,
                                         mIsbnView,
                                         mCoverImageView,
                                         ImageUtils.SCALE_MEDIUM);

        Field<String> field;

        field = fields.addString(R.id.genre, mGenreView, DBDefinitions.KEY_GENRE)
                      .setRelatedFields(R.id.lbl_genre);
        initValuePicker(field, mGenreView, R.string.lbl_genre, R.id.btn_genre,
                        mBookModel.getGenres());

        // Personal fields

        // defined, but fetched/stored manually
        // Storing the list back into the book is handled by onCheckListEditorSave
        field = fields
                .addString(R.id.bookshelves, mBookshelvesView, "", DBDefinitions.KEY_BOOKSHELF)
                .setRelatedFields(R.id.lbl_bookshelves);
        initCheckListEditor(field, mBookshelvesView, R.string.lbl_bookshelves_long,
                            () -> mBookModel.getEditableBookshelvesList());
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        Book book = mBookModel.getBook();

        switch (requestCode) {
            case REQ_EDIT_AUTHORS: {
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK) {
                        if (data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                            ArrayList<Author> list =
                                    data.getParcelableArrayListExtra(UniqueId.BKEY_AUTHOR_ARRAY);
                            if (list == null) {
                                list = new ArrayList<>(0);
                            }
                            book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
                        }

                        if (data.getBooleanExtra(EditObjectListModel.BKEY_LIST_MODIFIED, false)) {
                            mBookModel.setDirty(true);
                        }

                        // Some Authors MAY have been modified.
                        if (data.getBooleanExtra(EditObjectListModel.BKEY_GLOBAL_CHANGES_MADE,
                                                 false)) {
                            //noinspection ConstantConditions
                            mBookModel.refreshAuthorList(getContext());
                        }
                    }
                }
                break;
            }
            case REQ_EDIT_SERIES: {
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK) {
                        if (data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                            ArrayList<Series> list =
                                    data.getParcelableArrayListExtra(UniqueId.BKEY_SERIES_ARRAY);
                            if (list == null) {
                                list = new ArrayList<>(0);
                            }
                            book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
                        }

                        if (data.getBooleanExtra(EditObjectListModel.BKEY_LIST_MODIFIED, false)) {
                            mBookModel.setDirty(true);
                        }

                        // Some Series MAY have been modified.
                        if (data.getBooleanExtra(EditObjectListModel.BKEY_GLOBAL_CHANGES_MADE,
                                                 false)) {
                            //noinspection ConstantConditions
                            mBookModel.refreshSeriesList(getContext());
                        }
                    }
                }
                break;
            }
            default: {
                // handle any cover image request codes
                if (!mCoverHandler.onActivityResult(requestCode, resultCode, data)) {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
            }
        }
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do other stuff here that might affect the view.

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusFixer.fix(getView());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        // if the book has no cover, make sure the temp cover is deleted.
        if (!mBookModel.getBook().getBoolean(UniqueId.BKEY_IMAGE)) {
            StorageUtils.deleteFile(StorageUtils.getTempCoverFile());
        }
        super.onLoadFieldsFromBook();

        populateAuthorListField();
        populateSeriesListField();
        populateBookshelvesField();

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

        Book book = mBookModel.getBook();

        // If the new book is not on any Bookshelf, use the current bookshelf as default
        ArrayList<Bookshelf> list = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

        if (list.isEmpty()) {
            //noinspection ConstantConditions
            Bookshelf bookshelf = mBookModel.getBookshelf(getContext());

            getFields().getField(R.id.bookshelves).setValue(bookshelf.getName());
            // add to set, and store in book.
            list.add(bookshelf);
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField() {
        Book book = mBookModel.getBook();

        //noinspection ConstantConditions
        @NonNull
        Context context = getContext();

        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty() && ItemWithFixableId.pruneList(list, context, mBookModel.getDb(),
                                                           Locale.getDefault(), false)) {
            mBookModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }

        String value = book.getAuthorTextShort(context);
        if (value.isEmpty() && book.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
            // allow this fallback. It's used after a search that did not return results,
            // in which case it contains whatever the user typed.
            value = book.getString(UniqueId.BKEY_SEARCH_AUTHOR);
        }
        getFields().getField(R.id.author).setValue(value);
    }

    private void populateSeriesListField() {
        Book book = mBookModel.getBook();

        //noinspection ConstantConditions
        @NonNull
        Context context = getContext();

        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (!list.isEmpty() && ItemWithFixableId.pruneList(list, context, mBookModel.getDb(),
                                                           book.getLocale(context), false)) {
            mBookModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
        }

        String value;
        if (list.isEmpty()) {
            value = "";
        } else {
            value = list.get(0).getLabel(context);
            if (list.size() > 1) {
                value += ' ' + getString(R.string.and_others);
            }
        }
        getFields().getField(R.id.series).setValue(value);
    }

    private void populateBookshelvesField() {
        Book book = mBookModel.getBook();

        ArrayList<Bookshelf> list = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

        //noinspection ConstantConditions
        String value = Csv.join(", ", list, bookshelf -> bookshelf.getLabel(getContext()));
        getFields().getField(R.id.bookshelves).setValue(value);

        // String value;
        // if (list.isEmpty()) {
        //     value = "";
        // } else {
        //     //noinspection ConstantConditions
        //     value = list.get(0).getLabel(getContext());
        //     if (list.size() > 1) {
        //         value += ' ' + getString(R.string.and_others);
        //     }
        // }
        // getFields().getField(R.id.bookshelves).setValue(value);
    }
}

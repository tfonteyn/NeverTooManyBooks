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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

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
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        Book book = mBookModel.getBook();

        // book fields

        fields.add(R.id.title, DBDefinitions.KEY_TITLE);

        // defined, but fetched/stored manually
        // Storing the list back into the book is handled by onCheckListEditorSave
        fields.add(R.id.author, "", DBDefinitions.KEY_FK_AUTHOR)
              .getView().setOnClickListener(v -> {
            String title = fields.getField(R.id.title).getValue().toString();
            ArrayList<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

            Intent intent = new Intent(getContext(), EditAuthorListActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(DBDefinitions.KEY_LANGUAGE,
                              book.getString(DBDefinitions.KEY_LANGUAGE))
                    .putExtra(UniqueId.BKEY_AUTHOR_ARRAY, authors);
            startActivityForResult(intent, REQ_EDIT_AUTHORS);
        });

        // defined, but fetched/stored manually
        // Storing the list back into the book is handled by onCheckListEditorSave
        fields.add(R.id.series, "", DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFieldIds(R.id.lbl_series)
              .getView().setOnClickListener(v -> {
            // use the current title.
            String title = fields.getField(R.id.title).getValue().toString();
            ArrayList<Series> series = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

            Intent intent = new Intent(getContext(), EditSeriesListActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, book.getId())
                    .putExtra(DBDefinitions.KEY_TITLE, title)
                    .putExtra(DBDefinitions.KEY_LANGUAGE,
                              book.getString(DBDefinitions.KEY_LANGUAGE))
                    .putExtra(UniqueId.BKEY_SERIES_ARRAY, series);
            startActivityForResult(intent, REQ_EDIT_SERIES);
        });

        fields.add(R.id.description, DBDefinitions.KEY_DESCRIPTION)
              .setRelatedFieldIds(R.id.lbl_description);

        Field<String> isbnField = fields.add(R.id.isbn, DBDefinitions.KEY_ISBN)
                                        .setRelatedFieldIds(R.id.lbl_isbn);

        Field<String> coverField =
                fields.add(R.id.coverImage, DBDefinitions.KEY_BOOK_UUID, UniqueId.BKEY_IMAGE)
                      .setScale(ImageUtils.SCALE_MEDIUM);

        mCoverHandler = new CoverHandler(this, mBookModel.getDb(), book,
                                         isbnField.getView(), coverField.getView(),
                                         ImageUtils.SCALE_MEDIUM);

        Field<String> field;

        field = fields.add(R.id.genre, DBDefinitions.KEY_GENRE)
                      .setRelatedFieldIds(R.id.lbl_genre);
        initValuePicker(field, R.string.lbl_genre, R.id.btn_genre, mBookModel.getGenres());

        // Personal fields

        // defined, but fetched/stored manually
        // Storing the list back into the book is handled by onCheckListEditorSave
        field = fields.add(R.id.bookshelves, "", DBDefinitions.KEY_BOOKSHELF)
                      .setRelatedFieldIds(R.id.lbl_bookshelves);
        initCheckListEditor(field, R.string.lbl_bookshelves_long,
                            () -> mBookModel.getEditableBookshelvesList());
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        Book book = mBookModel.getBook();

        //noinspection ConstantConditions
        Locale userLocale = LocaleUtils.getLocale(getContext());

        switch (requestCode) {
            case REQ_EDIT_AUTHORS: {
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                        && data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                        ArrayList<Author> list =
                                data.getParcelableArrayListExtra(UniqueId.BKEY_AUTHOR_ARRAY);
                        if (list == null) {
                            list = new ArrayList<>(0);
                        }
                        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
                        mBookModel.setDirty(true);

                    } else {
                        // Even though the dialog was terminated,
                        // some authors MAY have been modified.
                        mBookModel.refreshAuthorList(getContext(), userLocale);
                    }

                    boolean wasDirty = mBookModel.isDirty();
                    populateAuthorListField(userLocale);
                    mBookModel.setDirty(wasDirty);

                }
                break;
            }
            case REQ_EDIT_SERIES: {
                if (data != null) {
                    if (resultCode == Activity.RESULT_OK
                        && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                        ArrayList<Series> list =
                                data.getParcelableArrayListExtra(UniqueId.BKEY_SERIES_ARRAY);
                        if (list == null) {
                            list = new ArrayList<>(0);
                        }
                        book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
                        mBookModel.setDirty(true);

                    } else {
                        // Even though the dialog was terminated,
                        // some series MAY have been modified.
                        mBookModel.refreshSeriesList(getContext());
                    }

                    boolean wasDirty = mBookModel.isDirty();
                    populateSeriesListField();
                    mBookModel.setDirty(wasDirty);

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

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * <ul>All storage interaction is done via:
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

    @Override
    protected void onLoadFieldsFromBook() {
        // if the book has no cover, then make sure the temp cover is deleted.
        if (!mBookModel.getBook().getBoolean(UniqueId.BKEY_IMAGE)) {
            StorageUtils.deleteFile(StorageUtils.getTempCoverFile());
        }
        super.onLoadFieldsFromBook();

        //noinspection ConstantConditions
        Locale locale = LocaleUtils.getLocale(getContext());

        populateAuthorListField(locale);
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
        final ArrayList<Bookshelf> list =
                book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

        if (list.isEmpty()) {
            //noinspection ConstantConditions
            Bookshelf bookshelf = mBookModel.getBookshelf(getContext());

            getField(R.id.bookshelves).setValue(bookshelf.getName());
            // add to set, and store in book.
            list.add(bookshelf);
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField(@NonNull final Locale userLocale) {
        Book book = mBookModel.getBook();

        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty()
            && ItemWithFixableId.pruneList(list, getContext(), mBookModel.getDb(), userLocale)) {
            mBookModel.setDirty(true);
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

    private void populateSeriesListField() {
        Book book = mBookModel.getBook();

        Context context = getContext();
        //noinspection ConstantConditions
        Locale locale = LocaleUtils.getLocale(context);

        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (!list.isEmpty()
            && ItemWithFixableId
                    .pruneList(list, context, mBookModel.getDb(), book.getLocale(locale))) {
            mBookModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
        }

        String result;
        if (list.isEmpty()) {
            result = "";
        } else {
            result = list.get(0).getLabel();
            if (list.size() > 1) {
                result += ' ' + getString(R.string.and_others);
            }
        }

        getField(R.id.series).setValue(result);
    }

    /**
     * The bookshelves field is a single csv String.
     */
    private void populateBookshelvesField() {
        Book book = mBookModel.getBook();

        ArrayList<Bookshelf> list = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        getField(R.id.bookshelves).setValue(Csv.join(", ", list, Bookshelf::getLabel));
    }
}

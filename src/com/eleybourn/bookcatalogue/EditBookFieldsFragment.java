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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment.OnPartialDatePickerResultListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorDialogFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is called by {@link EditBookActivity} and displays the main Books fields Tab
 *
 * Note it does not extends directly from {@link BookAbstractFragment}
 * but uses the {@link BookAbstractFragmentWithCoverImage} intermediate instead.
 * It shares the latter with {@link BookDetailsFragment}
 */
public class EditBookFieldsFragment extends BookAbstractFragmentWithCoverImage
        implements
        OnPartialDatePickerResultListener,
        OnTextFieldEditorListener,
        CheckListEditorDialogFragment.OnCheckListChangedListener {

    /** Lists in database so far, we cache them for performance */
    private List<String> mFormats;
    private List<String> mGenres;
    private List<String> mLanguages;
    private List<String> mPublishers;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_fields, container, false);
    }

    /**
     * Check the activity supports the interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof EditBookActivity)) {
            throw new IllegalStateException(EditBookFieldsFragment.class.getCanonicalName() +
                    " can only be hosted inside " + EditBookActivity.class.getCanonicalName());
        }
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            setDirty(false);
        }

        try {
            //noinspection ConstantConditions
            ViewUtils.fixFocusSettings(getView());
        } catch (Exception e) {
            // Log, but ignore. This is a non-critical feature that prevents crashes when the
            // 'next' key is pressed and some views have been hidden.
            Logger.error(e);
        }

        setDirty(false);
    }




    /**
     * Add all book fields with corresponding validators/formatters.
     * Note this is NOT where we set values.
     *
     * Some fields are only present (or need specific handling) on the 'edit' activity.
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();

        /* Anthology is provided as a boolean, see {@link Book#initValidators()}*/
        mFields.add(R.id.anthology, Book.IS_ANTHOLOGY);

        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER);
        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED)
                .setFormatter(new Fields.DateFieldFormatter());
        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION)
                .setFormatter(new Fields.DateFieldFormatter());

        mFields.add(R.id.bookshelves, "", UniqueId.KEY_BOOKSHELF_NAME)
                .setOutputOnly(true);
        mFields.getField(R.id.bookshelves).getView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                CheckListEditorDialogFragment<Bookshelf> c = new CheckListEditorDialogFragment<>();
                c.setTitle(R.string.bookshelves);
                c.setDestinationFieldId(R.id.bookshelves);
                // if we chain these calls, then lint complains as the param type will be wrong
                c.setList(getBook().getEditableBookshelvesList(mDb));
                c.show(requireFragmentManager(), null);
            }
        });

        mFields.setOnClickListener(R.id.date_published, new View.OnClickListener() {
            public void onClick(View view) {
                PartialDatePickerDialogFragment.newInstance()
                        .setTitle(R.string.date_published)
                        .setDate(mFields.getField(R.id.date_published).putValueInto())
                        .setDestinationFieldId(R.id.date_published)
                        .show(requireFragmentManager(), null);
            }
        });

        mFields.setOnClickListener(R.id.first_publication, new View.OnClickListener() {
            public void onClick(View view) {
                PartialDatePickerDialogFragment.newInstance()
                        .setTitle(R.string.first_publication)
                        .setDate(mFields.getField(R.id.first_publication).putValueInto())
                        .setDestinationFieldId(R.id.first_publication)
                        .show(requireFragmentManager(), null);
            }
        });

        mFields.setOnClickListener(R.id.anthology, new View.OnClickListener() {
            public void onClick(View view) {
                Checkable cb = (Checkable) view;
                ((EditBookActivity) requireActivity()).addAnthologyTab(cb.isChecked());
            }
        });

        mFields.setOnClickListener(R.id.author, new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireActivity(), EditAuthorListActivity.class);
                intent.putExtra(UniqueId.BKEY_AUTHOR_ARRAY, getBook().getAuthorList());
                intent.putExtra(UniqueId.KEY_ID, getBook().getBookId());
                intent.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).putValueInto().toString());
                startActivityForResult(intent, EditAuthorListActivity.REQUEST_CODE); /* dd74343a-50ff-4ce9-a2e4-a75f7bcf9e36 */
            }
        });

        mFields.setOnClickListener(R.id.series, new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireActivity(), EditSeriesListActivity.class);
                intent.putExtra(UniqueId.BKEY_SERIES_ARRAY, getBook().getSeriesList());
                intent.putExtra(UniqueId.KEY_ID, getBook().getBookId());
                intent.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).putValueInto().toString());
                startActivityForResult(intent, EditSeriesListActivity.REQUEST_CODE); /* bca659b6-dfb9-4a97-b651-5b05ad102400 */
            }
        });

        initMenuMoreButton(R.id.publisher, R.id.btn_publisher, getPublishers(), R.string.publisher);
        initMenuMoreButton(R.id.format, R.id.btn_format, getFormats(), R.string.format);
        initMenuMoreButton(R.id.genre, R.id.btn_genre, getGenres(), R.string.genre);
        initMenuMoreButton(R.id.language, R.id.btn_language, getLanguages(), R.string.lbl_language);


        // The Edit icon for bringing up the dialog to edit the description
        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_description).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                TextFieldEditorDialogFragment.newInstance()
                        .setTitle(R.string.description)
                        .setDestinationFieldId(R.id.description)
                        .setText(mFields.getField(R.id.description).putValueInto().toString())
                        .show(requireFragmentManager(), null);
            }
        });
    }

    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a dialog box with a list of strings to choose from.
     */
    private void initMenuMoreButton(@IdRes final int resId,
                                    @IdRes final int buttonResId,
                                    @NonNull final List<String> list,
                                    @StringRes final int dialogTitleResId) {

        final Field field = mFields.getField(resId);
        // only bother when visible
        if (field.visible) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                    android.R.layout.simple_dropdown_item_1line, list);
            mFields.setAdapter(resId, adapter);

            // Get the list to use in the AutoComplete stuff
            AutoCompleteTextView textView = field.getView();
            textView.setAdapter(new ArrayAdapter<>(requireActivity(),
                    android.R.layout.simple_dropdown_item_1line, list));

            // Get the drop-down button for the list and setup dialog
            //noinspection ConstantConditions
            getView().findViewById(buttonResId).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StandardDialogs.selectStringDialog(requireActivity().getLayoutInflater(),
                            getString(dialogTitleResId),
                            list, field.putValueInto().toString(),
                            new SimpleDialogOnClickListener() {
                                @Override
                                public void onClick(@NonNull final SimpleDialogItem item) {
                                    field.setValue(item.toString());
                                }
                            });
                }
            });
        }
    }

    @Override
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllFrom) {
        super.onLoadBookDetails(book, setAllFrom);

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);
    }

    @Override
    protected void populateFields(@NonNull final Book book) {
        // new book ? load data fields from Extras
        if (book.getBookId() <= 0) {
            Bundle extras = requireActivity().getIntent().getExtras();
            if (extras != null) {
                // From the ISBN Search (add)
                Bundle values = extras.getParcelable(UniqueId.BKEY_BOOK_DATA);
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
            initDefaultBookshelf();
        }

        mFields.getField(R.id.anthology).setValue(book.getString(Book.IS_ANTHOLOGY));
        setCoverImage(book.getBookId());

        populateBookshelves(mFields.getField(R.id.bookshelves), book);
    }

    @Override
    protected void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> list = book.getAuthorList();
        if (list.size() != 0 && Utils.pruneList(mDb, list)) {
            setDirty(true);
            book.putAuthorList(list);
        }

        String newText = book.getAuthorTextShort();
        if (newText.isEmpty()) {
            newText = getString(R.string.set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    @Override
    protected void populateSeriesListField(@NonNull final Book book) {
        ArrayList<Series> list = book.getSeriesList();
        if (list.size() != 0 && Utils.pruneList(mDb, list)) {
            setDirty(true);
            book.putSeriesList(list);
        }

        String newText = book.getSeriesTextShort();
        if (newText.isEmpty()) {
            newText = getString(R.string.set_series);
        }
        mFields.getField(R.id.series).setValue(newText);
    }

    /**
     * Use the currently selected bookshelf as default
     */
    private void initDefaultBookshelf() {
        final Book book = getBook();
        final List<Bookshelf> list = book.getBookshelfList();
        if (list.isEmpty()) {
            Bookshelf bookshelf = null;
            String name = BookCatalogueApp.Prefs.getStringOrEmpty(BooksOnBookshelf.PREF_BOOKSHELF);
            if (!name.isEmpty()) {
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

    /**
     * Load a publisher list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    @NonNull
    public List<String> getPublishers() {
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
    public List<String> getGenres() {
        if (mGenres == null) {
            mGenres = mDb.getGenres();
        }
        return mGenres;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of languages
     */
    @NonNull
    public List<String> getLanguages() {
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
    public List<String> getFormats() {
        if (mFormats == null) {
            mFormats = mDb.getFormats();
        }
        return mFormats;
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        Book book = getBook();
        switch (requestCode) {
            case EditAuthorListActivity.REQUEST_CODE: /* dd74343a-50ff-4ce9-a2e4-a75f7bcf9e36 */
                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                    book.putAuthorList(ArrayUtils.getAuthorListFromIntentExtras(data));
                    setDirty(true);
                } else {
                    // Even though the dialog was terminated, some authors MAY have been updated/added.
                    book.refreshAuthorList(mDb);
                }
                // We do the fix here because the user may have edited or merged authors;
                // this will have already been applied to the database so no update is
                // necessary, but we do need to update the data we display.
                boolean wasDirty = isDirty();
                populateAuthorListField(book);
                setDirty(wasDirty);
                return;

            case EditSeriesListActivity.REQUEST_CODE: /* bca659b6-dfb9-4a97-b651-5b05ad102400 */
                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                    book.putSeriesList(ArrayUtils.getSeriesListFromIntentExtras(data));
                    populateSeriesListField(book);
                    setDirty(true);
                }
                return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * The callback received when the user "sets" the date in the dialog.
     *
     * Build a full or partial date in SQL format
     */
    @Override
    public void onPartialDatePickerSet(@NonNull final PartialDatePickerDialogFragment dialog,
                                       @IdRes final int destinationFieldId,
                                       @Nullable final Integer year,
                                       @Nullable final Integer month,
                                       @Nullable final Integer day) {
        String value = DateUtils.buildPartialDate(year, month, day);
        mFields.getField(destinationFieldId).setValue(value);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                          @IdRes final int destinationFieldId) {
        dialog.dismiss();
    }

    /**
     * The callback received when the user "sets" the text editor in the text editor dialog.
     *
     * Set the appropriate field
     */
    @Override
    public void onTextFieldEditorSave(@NonNull final TextFieldEditorDialogFragment dialog,
                                      @IdRes final int callerId,
                                      @NonNull final String newText) {
        mFields.getField(callerId).setValue(newText);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the text editor dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onTextFieldEditorCancel(@NonNull final TextFieldEditorDialogFragment dialog,
                                        @IdRes final int callerId) {
        dialog.dismiss();
    }

    @Override
    public <T> void onCheckListSave(@NonNull final CheckListEditorDialogFragment dialog,
                                    final int destinationFieldId,
                                    @NonNull final List<CheckListItem<T>> list) {
        dialog.dismiss();

        ArrayList<Bookshelf> result = new Book.BookshelfCheckListItem().extractList(list);
        getBook().putBookshelfList(result);
        mFields.getField(R.id.bookshelves).setValue(getBook().getBookshelfListAsText());
    }

    @Override
    public void onCheckListCancel(@NonNull final CheckListEditorDialogFragment dialog,
                                  final int destinationFieldId) {
        dialog.dismiss();

    }

}

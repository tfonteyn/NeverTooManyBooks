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
import android.database.SQLException;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfCheckChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
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
        implements OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    private static final String TAG_BOOKSHELVES_DIALOG = "bookshelves_dialog";

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

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this);
        try {
            // init fields etc....
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                getEditBookManager().setDirty(false);
            }

            final CompoundButton cb = getView().findViewById(R.id.anthology);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    getEditBookManager().addAnthologyTab(cb.isChecked());
                }
            });

            getView().findViewById(R.id.description_edit_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    Object object = mFields.getField(R.id.description).getValue();
                    TextFieldEditorFragment.newInstance(R.id.description, R.string.description, object.toString())
                            .show(getFragmentManager(), null);
                }
            });

            mFields.setListener(R.id.date_published, new View.OnClickListener() {
                public void onClick(View view) {
                    PartialDatePickerFragment.newInstance()
                            .setTitle(R.string.date_published)
                            .setDate(mFields.getField(R.id.date_published).getValue())
                            .setDialogId(R.id.date_published) /* Set to the destination field ID */
                            .show(getFragmentManager(), null);
                }
            });

            mFields.setListener(R.id.first_publication, new View.OnClickListener() {
                public void onClick(View view) {
                    PartialDatePickerFragment.newInstance()
                            .setTitle(R.string.first_publication)
                            .setDate(mFields.getField(R.id.first_publication).getValue())
                            .setDialogId(R.id.first_publication) /* Set to the destination field ID */
                            .show(getFragmentManager(), null);
                }
            });

            mFields.setListener(R.id.bookshelf, new View.OnClickListener() {
                public void onClick(View v) {
                    BookshelfDialogFragment.newInstance(
                            getBook().getBookId(),
                            getBook().getString(Book.BOOKSHELF_TEXT),
                            getBook().getBookshelfList())
                            .show(getFragmentManager(), TAG_BOOKSHELVES_DIALOG);
                }
            });

            mFields.setListener(R.id.author, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), EditAuthorListActivity.class);
                    intent.putExtra(UniqueId.BKEY_AUTHOR_ARRAY, getBook().getAuthorList());
                    intent.putExtra(UniqueId.KEY_ID, getBook().getBookId());
                    intent.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                    startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_EDIT_AUTHORS);
                }
            });

            mFields.setListener(R.id.series, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), EditSeriesListActivity.class);
                    intent.putExtra(UniqueId.BKEY_SERIES_ARRAY, getBook().getSeriesList());
                    intent.putExtra(UniqueId.KEY_ID, getBook().getBookId());
                    intent.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                    startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_EDIT_SERIES);
                }
            });


            initMenuMoreButton(R.id.publisher, R.id.publisher_button, getPublishers(), R.string.publisher);
            initMenuMoreButton(R.id.format, R.id.format_button, getFormats(), R.string.format);
            initMenuMoreButton(R.id.genre, R.id.genre_button, getGenres(), R.string.genre);
            initMenuMoreButton(R.id.language, R.id.language_button, getLanguages(), R.string.language);


            try {
                ViewUtils.fixFocusSettings(getView());
            } catch (Exception e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.error(e);
            }

            getEditBookManager().setDirty(false);

        } catch (@NonNull IndexOutOfBoundsException | SQLException e) {
            Logger.error(e);
        } finally {
            Tracker.exitOnActivityCreated(this);
        }
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
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_dropdown_item_1line, list);
            mFields.setAdapter(resId, adapter);

            // Get the list to use in the AutoComplete stuff
            AutoCompleteTextView textView = field.getView();
            textView.setAdapter(new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_dropdown_item_1line, list));

            // Get the drop-down button for the list and setup dialog
            View button = getView().findViewById(buttonResId);
            button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(),
                            getString(dialogTitleResId),
                            list, field.getValue().toString(),
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
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        super.onLoadBookDetails(book, setAllDone);

        // new book ? populate from Extras
        if (book.getBookId() <= 0) {
            Bundle extras = getActivity().getIntent().getExtras();
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
            // Update the ImageView with the new image
            setCoverImage(book.getBookId());
        }

        populateAuthorListField(book);
        populateSeriesListField(book);

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);
    }

    /**
     * Add all book fields with corresponding validators. Note this is NOT where we set values.
     *
     * Some fields are only present on the 'edit' activity.
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();

        /* Anthology needs an accessor, see {@link Book#initValidators()}*/
        mFields.add(R.id.anthology, Book.IS_ANTHOLOGY, null);

        mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER, null);

        mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED, null,
                new Fields.DateFieldFormatter());

        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION, null,
                new Fields.DateFieldFormatter());
    }

    @Override
    protected void populateFields(@NonNull final Book book) {
        setCoverImage(book.getBookId());
        mFields.getField(R.id.anthology).setValue(book.getString(Book.IS_ANTHOLOGY));
        populateBookshelves(mFields.getField(R.id.bookshelf), book);
    }

    @Override
    protected void populateAuthorListField(@NonNull final Book book) {
        ArrayList<Author> list = book.getAuthorList();
        if (list.size() != 0 && Utils.pruneList(mDb, list)) {
            getEditBookManager().setDirty(true);
            book.setAuthorList(list);
        }

        String newText = book.getAuthorTextShort();
        if (newText == null || newText.isEmpty()) {
            newText = getString(R.string.set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    @Override
    protected void populateSeriesListField(@NonNull final Book book) {
        String newText;
        ArrayList<Series> list = book.getSeriesList();
        if (list.size() == 0) {
            newText = getString(R.string.set_series);
        } else {
            boolean trimmed = Series.pruneSeriesList(list);
            trimmed |= Utils.pruneList(mDb, list);
            if (trimmed) {
                book.setSeriesList(list);
            }
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += " " + getString(R.string.and_others);
            }
        }
        mFields.getField(R.id.series).setValue(newText);
    }

    /**
     * Use the currently selected bookshelf as default
     */
    private void initDefaultBookshelf() {
        final Book book = getBook();
        final String list = book.getBookshelfList();
        if (list.isEmpty()) {
            String currentShelf = BookCatalogueApp.Prefs.getStringOrEmpty(BooksOnBookshelf.PREF_BOOKSHELF);
            if (currentShelf.isEmpty()) {
                currentShelf = mDb.getBookshelfName(Bookshelf.DEFAULT_ID);
            }
            mFields.getField(R.id.bookshelf).setValue(currentShelf);
            book.setBookshelfList(ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, currentShelf));
        }
    }

    /**
     * Load a publisher list; reloading this list every time a tab changes is
     * slow. So we cache it.
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
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent intent) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode);
        super.onActivityResult(requestCode,resultCode,intent);

        try {
            Book book = getBook();
            // reminder: no, AnthologyTitle is not handled here, has its own Tab
            switch (requestCode) {
                case UniqueId.ACTIVITY_REQUEST_CODE_EDIT_AUTHORS: {
                    if (resultCode == Activity.RESULT_OK && intent != null && intent.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
                        book.setAuthorList(ArrayUtils.getAuthorFromIntentExtras(intent));
                        getEditBookManager().setDirty(true);
                    } else {
                        // Even though the dialog was terminated, some authors MAY have been updated/added.
                        book.refreshAuthorList(mDb);
                    }
                    // We do the fix here because the user may have edited or merged authors; this will
                    // have already been applied to the database so no update is necessary, but we do need
                    // to update the data we display.
                    boolean wasDirty = getEditBookManager().isDirty();
                    populateAuthorListField(book);
                    getEditBookManager().setDirty(wasDirty);
                    break;
                }
                case UniqueId.ACTIVITY_REQUEST_CODE_EDIT_SERIES: {
                    if (resultCode == Activity.RESULT_OK && intent != null && intent.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
                        book.setSeriesList(ArrayUtils.getSeriesFromIntentExtras(intent));
                        populateSeriesListField(book);
                        getEditBookManager().setDirty(true);
                    }
                    break;
                }
            }
        } finally {
            Tracker.exitOnActivityResult(this, requestCode, resultCode);
        }
    }

    /**
     * Show the context menu for the cover thumbnail
     */
    public void showCoverContextMenu() {
        View view = getView().findViewById(R.id.image);
        view.showContextMenu();
    }


    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        if (Fields.isVisible(UniqueId.KEY_BOOK_THUMBNAIL)) {
            menu.add(Menu.NONE, R.id.SUBMENU_REPLACE_THUMB, 0, R.string.cover_options_cc_ellipsis)
                    .setIcon(R.drawable.ic_add_a_photo)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * The callback received when the user "sets" the date in the dialog.
     *
     * Build a full or partial date in SQL format
     */
    @Override
    public void onPartialDatePickerSet(@IdRes final int dialogId,
                                       @NonNull final PartialDatePickerFragment dialog,
                                       @Nullable final Integer year,
                                       @Nullable final Integer month,
                                       @Nullable final Integer day) {
        String value = DateUtils.buildPartialDate(year, month, day);
        mFields.getField(dialogId).setValue(value);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(@IdRes final int dialogId,
                                          @NonNull final PartialDatePickerFragment dialog) {
        dialog.dismiss();
    }

    /**
     * The callback received when the user "sets" the text editor in the text editor dialog.
     *
     * Set the appropriate field
     */
    @Override
    public void onTextFieldEditorSave(@IdRes final int dialogId,
                                      @NonNull final TextFieldEditorFragment dialog,
                                      @NonNull final String newText) {
        mFields.getField(dialogId).setValue(newText);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the text editor dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onTextFieldEditorCancel(@IdRes final int dialogId,
                                        @NonNull final TextFieldEditorFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onBookshelfCheckChanged(@NonNull final String textList,
                                        @NonNull final String encodedList) {

        mFields.getField(R.id.bookshelf).setValue(textList);
        getBook().setBookshelfList(encodedList);
    }
}

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
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.database.ColumnInfo;
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
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewUtils;

import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_THUMBNAIL;


public class BookEditFields extends BookDetailsFragmentAbstract
        implements OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    public static final String BOOKSHELVES_DIALOG = "bookshelves_dialog";
    public static final String BKEY_BOOK_DATA = "bookData";
    /**
     * Used as: if (DEBUG && BuildConfig.DEBUG) { ... }
     */
    private static final boolean DEBUG = false;
    private static final int ACTIVITY_EDIT_AUTHORS = 1000;
    private static final int ACTIVITY_EDIT_SERIES = 1001;

    /**
     * Display the edit fields page
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_fields, container, false);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this);
        double t0;
        double t1;

        try {
            if (DEBUG && BuildConfig.DEBUG) {
                t0 = System.currentTimeMillis();
            }
            super.onActivityCreated(savedInstanceState);
            if (DEBUG && BuildConfig.DEBUG) {
                t1 = System.currentTimeMillis();
            }

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
            }

            getView().findViewById(R.id.author)
                    .setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(getActivity(), EditAuthorListActivity.class);
                            i.putExtra(ColumnInfo.KEY_AUTHOR_ARRAY, mEditManager.getBookData().getAuthors());
                            i.putExtra(ColumnInfo.KEY_ROWID, mEditManager.getBookData().getRowId());
                            i.putExtra(ColumnInfo.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                            startActivityForResult(i, ACTIVITY_EDIT_AUTHORS);
                        }
                    });

            getView().findViewById(R.id.series)
                    .setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(getActivity(), EditSeriesListActivity.class);
                            i.putExtra(ColumnInfo.KEY_SERIES_ARRAY, mEditManager.getBookData().getSeries());
                            i.putExtra(ColumnInfo.KEY_ROWID, mEditManager.getBookData().getRowId());
                            i.putExtra(ColumnInfo.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                            startActivityForResult(i, ACTIVITY_EDIT_SERIES);
                        }
                    });

            ArrayAdapter<String> publisher_adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getPublishers());
            mFields.setAdapter(R.id.publisher, publisher_adapter);

            ArrayAdapter<String> genre_adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getGenres());
            mFields.setAdapter(R.id.genre, genre_adapter);

            ArrayAdapter<String> language_adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getLanguages());
            mFields.setAdapter(R.id.language, language_adapter);
            mFields.setListener(R.id.date_published, new View.OnClickListener() {
                public void onClick(View view) {
                    showDatePublishedDialog();
                }
            });

            final Field formatField = mFields.getField(R.id.format);
            // Get the formats to use in the AutoComplete stuff
            AutoCompleteTextView formatText = (AutoCompleteTextView) formatField.getView();
            formatText.setAdapter(new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_dropdown_item_1line,
                    mEditManager.getFormats()));
            // Get the drop-down button for the formats list and setup dialog
            ImageView formatButton = getView().findViewById(R.id.format_button);
            formatButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(),
                            getString(R.string.format),
                            mDb.getFormats(), formatField.getValue().toString(),
                            new SimpleDialogOnClickListener() {
                                @Override
                                public void onClick(SimpleDialogItem item) {
                                    formatField.setValue(item.toString());
                                }
                            });
                }
            });

            final Field languageField = mFields.getField(R.id.language);
            // Get the languages to use in the AutoComplete stuff
            AutoCompleteTextView languageText = (AutoCompleteTextView) languageField.getView();
            languageText.setAdapter(new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_dropdown_item_1line,
                    mEditManager.getLanguages()));
            // Get the drop-down button for the languages list and setup dialog
            ImageView languageButton = getView().findViewById(R.id.language_button);
            languageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(),
                            getString(R.string.language),
                            mDb.getLanguages(), languageField.getValue().toString(),
                            new SimpleDialogOnClickListener() {
                                @Override
                                public void onClick(SimpleDialogItem item) {
                                    languageField.setValue(item.toString());
                                }
                            });
                }
            });

            Field bookshelfButtonFe = mFields.getField(R.id.bookshelf);
            bookshelfButtonFe.getView().setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    BookshelfDialogFragment frag = BookshelfDialogFragment.newInstance(
                            R.id.bookshelf,
                            mEditManager.getBookData().getRowId(),
                            mEditManager.getBookData().getBookshelfText(),
                            mEditManager.getBookData().getBookshelfList()
                    );
                    frag.show(getFragmentManager(), BOOKSHELVES_DIALOG);
                }
            });

            final CheckBox cb = getView().findViewById(R.id.anthology);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    mEditManager.setShowAnthology(cb.isChecked());
                }
            });

            final ImageView editDescBtn = getView().findViewById(R.id.description_edit_button);
            editDescBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    showDescriptionDialog();
                }
            });

            try {
                ViewUtils.fixFocusSettings(getView());
            } catch (Exception ignore) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.logError(ignore);
            }

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
            } else {
                mEditManager.setDirty(false);
            }

        } catch (IndexOutOfBoundsException | SQLException ignore) {
            Logger.logError(ignore);
        } finally {
            Tracker.exitOnActivityCreated(this);
        }

        if (DEBUG && BuildConfig.DEBUG) {
            System.out.println("BEF oAC(super): " + (t1 - t0) + ", BEF oAC: " + (System.currentTimeMillis() - t0));
        }

    }

    private void showDescriptionDialog() {
        Object o = mFields.getField(R.id.description).getValue();
        String description = (o == null ? null : o.toString());
        TextFieldEditorFragment.newInstance(R.id.description, R.string.description, description)
                .show(getFragmentManager(), null);
    }

    private void showDatePublishedDialog() {
        PartialDatePickerFragment.newInstance()
                .setTitle(R.string.date_published)
                .setDate(mFields.getField(R.id.date_published).getValue())
                .setDialogId(R.id.date_published) /* Set to the destination field ID */
                .show(getFragmentManager(), null);
    }

    /**
     * This function will populate the forms elements in three different ways
     * 1. If a valid rowId exists it will populate the fields from the database
     * 2. If fields have been passed from another activity (e.g. ISBNSearch) it will populate the fields from the bundle
     * 3. It will leave the fields blank for new books.
     */
    private void populateFields() {
        double t0;
        if (DEBUG && BuildConfig.DEBUG) {
            t0 = System.currentTimeMillis();
        }
        final BookData book = mEditManager.getBookData();
        populateFieldsFromBook(book);
        if (book.getRowId() <= 0) {
            Bundle extras = getActivity().getIntent().getExtras();
            if (extras != null) {
                // From the ISBN Search (add)
                if (extras.containsKey(ColumnInfo.KEY_BOOK)) {
                    throw new RuntimeException("[book] array passed in Intent");
                } else {
                    Bundle values = extras.getParcelable(BKEY_BOOK_DATA);
                    if (values != null) {
                        for (Field f : mFields) {
                            if (!f.column.isEmpty() && values.containsKey(f.column)) {
                                try {
                                    f.setValue(values.getString(f.column));
                                } catch (Exception ignore) {
                                    String msg = "Populate field " + f.column + " failed: " + ignore.getMessage();
                                    Logger.logError(ignore, msg);
                                }
                            }
                        }
                    }
                }
            }
            initDefaultShelf();
            setCoverImage();
        } //else { //Populating from database
        //populateFieldsFromBook(book);
        //getActivity().setTitle(this.getResources().getString(R.string.menu));
        //}

        populateAuthorListField();
        populateSeriesListField();

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);

        if (DEBUG && BuildConfig.DEBUG) {
            System.out.println("BEF popF: " + (System.currentTimeMillis() - t0));
        }
    }

    /**
     * Use the currently selected bookshelf as default
     */
    private void initDefaultShelf() {
        final BookData book = mEditManager.getBookData();
        final String list = book.getBookshelfList();
        if (list == null || list.isEmpty()) {
            String currShelf = BookCataloguePreferences.getString(BooksOnBookshelf.PREF_BOOKSHELF, "");
            if (currShelf.isEmpty()) {
                currShelf = mDb.getBookshelfName(1);
            }
            String encoded_shelf = ArrayUtils.encodeListItem(currShelf, BOOKSHELF_SEPARATOR);
            Field fe = mFields.getField(R.id.bookshelf);
            fe.setValue(currShelf);
            book.setBookshelfList(encoded_shelf);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);

        super.onSaveInstanceState(outState);

        Tracker.exitOnSaveInstanceState(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode);
        try {
            super.onActivityResult(requestCode, resultCode, intent);

            switch (requestCode) {
                case ACTIVITY_EDIT_AUTHORS:
                    if (resultCode == Activity.RESULT_OK && intent.hasExtra(ColumnInfo.KEY_AUTHOR_ARRAY)) {
                        mEditManager.getBookData().setAuthorList(ArrayUtils.getAuthorFromIntentExtras(intent));
                        mEditManager.setDirty(true);
                    } else {
                        // Even though the dialog was terminated, some authors MAY have been updated/added.
                        mEditManager.getBookData().refreshAuthorList(mDb);
                    }
                    // We do the fix here because the user may have edited or merged authors; this will
                    // have already been applied to the database so no update is necessary, but we do need
                    // to update the data we display.
                    boolean oldDirty = mEditManager.isDirty();
                    populateAuthorListField();
                    mEditManager.setDirty(oldDirty);
                case ACTIVITY_EDIT_SERIES:
                    if (resultCode == Activity.RESULT_OK && intent.hasExtra(ColumnInfo.KEY_SERIES_ARRAY)) {
                        mEditManager.getBookData().setSeriesList(ArrayUtils.getSeriesFromIntentExtras(intent));
                        populateSeriesListField();
                        mEditManager.setDirty(true);
                    }
            }
        } finally {
            Tracker.exitOnActivityResult(this, requestCode, resultCode);
        }
    }

    @Override
    protected void populateAuthorListField() {
        ArrayList<Author> list = mEditManager.getBookData().getAuthors();
        if (list.size() != 0 && Utils.pruneList(mDb, list)) {
            mEditManager.setDirty(true);
            mEditManager.getBookData().setAuthorList(list);
        }
        super.populateAuthorListField();
    }

    /**
     * Show the context menu for the cover thumbnail
     */
    public void showCoverContextMenu() {
        View v = getView().findViewById(R.id.row_img);
        v.showContextMenu();
    }

    @Override
    protected void onLoadBookDetails(BookData book, boolean setAllDone) {
        if (!setAllDone) {
            mFields.setAll(book);
        }
        populateFields();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (FieldVisibilityActivity.isVisible(KEY_THUMBNAIL)) {
            menu.add(0, BookEditFragmentAbstract.THUMBNAIL_OPTIONS_ID, 0, R.string.cover_options_cc_ellipsis)
                    .setIcon(android.R.drawable.ic_menu_camera)
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
    public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
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
    public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
        dialog.dismiss();
    }

    /**
     * The callback received when the user "sets" the text editor in the text editor dialog.
     *
     * Set the appropriate field
     */
    @Override
    public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText) {
        mFields.getField(dialogId).setValue(newText);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the text editor dialog.
     *
     * Dismiss it.
     */
    @Override
    public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onBookshelfCheckChanged(int dialogId, BookshelfDialogFragment dialog,
                                        boolean checked, String shelf, String textList, String encodedList) {

        mFields.getField(R.id.bookshelf).setValue(textList);
        mEditManager.getBookData().setBookshelfList(encodedList);
    }
}

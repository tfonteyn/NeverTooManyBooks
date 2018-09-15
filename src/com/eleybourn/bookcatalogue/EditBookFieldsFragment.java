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

import java.util.ArrayList;

public class EditBookFieldsFragment extends BookDetailsAbstractFragment
        implements OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    public static final String TAG_BOOKSHELVES_DIALOG = "bookshelves_dialog";
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
                mEditManager.setDirty(savedInstanceState.getBoolean(UniqueId.BKEY_DIRTY));
            }

            final CheckBox cb = getView().findViewById(R.id.anthology);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    mEditManager.setShowAnthology(cb.isChecked());
                }
            });

            mFields.setListener(R.id.description_edit_button, new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    Object o = mFields.getField(R.id.description).getValue();
                    String description = (o == null ? null : o.toString());
                    TextFieldEditorFragment.newInstance(R.id.description, R.string.description, description)
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

            mFields.setListener(R.id.bookshelf, new View.OnClickListener() {
                public void onClick(View v) {
                    BookshelfDialogFragment frag = BookshelfDialogFragment.newInstance(
                            R.id.bookshelf,
                            mEditManager.getBookData().getRowId(),
                            mEditManager.getBookData().getBookshelfText(),
                            mEditManager.getBookData().getBookshelfList()
                    );
                    frag.show(getFragmentManager(), TAG_BOOKSHELVES_DIALOG);
                }
            });

            mFields.setListener(R.id.author, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(getActivity(), EditAuthorListActivity.class);
                    i.putExtra(UniqueId.BKEY_AUTHOR_ARRAY, mEditManager.getBookData().getAuthors());
                    i.putExtra(UniqueId.KEY_ID, mEditManager.getBookData().getRowId());
                    i.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                    startActivityForResult(i, ACTIVITY_EDIT_AUTHORS);
                }
            });

            mFields.setListener(R.id.series, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(getActivity(), EditSeriesListActivity.class);
                    i.putExtra(UniqueId.BKEY_SERIES_ARRAY, mEditManager.getBookData().getSeries());
                    i.putExtra(UniqueId.KEY_ID, mEditManager.getBookData().getRowId());
                    i.putExtra(UniqueId.KEY_TITLE, mFields.getField(R.id.title).getValue().toString());
                    startActivityForResult(i, ACTIVITY_EDIT_SERIES);
                }
            });


            setupMenuMoreButton(R.id.publisher, R.id.publisher_button, mEditManager.getPublishers(), R.string.format);
            setupMenuMoreButton(R.id.format, R.id.format_button, mEditManager.getFormats(), R.string.format);
            setupMenuMoreButton(R.id.genre, R.id.genre_button, mEditManager.getGenres(), R.string.genre);
            setupMenuMoreButton(R.id.language, R.id.language_button, mEditManager.getLanguages(), R.string.language);


            try {
                ViewUtils.fixFocusSettings(getView());
            } catch (Exception ignore) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.logError(ignore);
            }

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean(UniqueId.BKEY_DIRTY));
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

    public void setupMenuMoreButton(final int resId, final int buttonResId, final ArrayList<String> list, final int dialogTitleResId) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_dropdown_item_1line, list);
        mFields.setAdapter(resId, adapter);

        final Field field = mFields.getField(resId);
        // Get the list to use in the AutoComplete stuff
        AutoCompleteTextView textView = (AutoCompleteTextView) field.getView();
        textView.setAdapter(new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_dropdown_item_1line,
                list));
        // Get the drop-down button for the list and setup dialog
        ImageView button = getView().findViewById(buttonResId);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(),
                        getString(dialogTitleResId),
                        list, field.getValue().toString(),
                        new SimpleDialogOnClickListener() {
                            @Override
                            public void onClick(SimpleDialogItem item) {
                                field.setValue(item.toString());
                            }
                        });
            }
        });
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
                if (extras.containsKey(UniqueId.KEY_BOOK)) {
                    throw new RuntimeException("[book] array passed in Intent");
                } else {
                    Bundle values = extras.getParcelable(UniqueId.BKEY_BOOK_DATA);
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
            String currShelf = BCPreferences.getString(BooksOnBookshelf.PREF_BOOKSHELF, "");
            if (currShelf.isEmpty()) {
                currShelf = mDb.getBookshelfName(1);
            }
            String encoded_shelf = ArrayUtils.encodeListItem(BOOKSHELF_SEPARATOR, currShelf);
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
                    if (resultCode == Activity.RESULT_OK && intent.hasExtra(UniqueId.BKEY_AUTHOR_ARRAY)) {
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
                    if (resultCode == Activity.RESULT_OK && intent.hasExtra(UniqueId.BKEY_SERIES_ARRAY)) {
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
        if (FieldVisibilityActivity.isVisible(UniqueId.BKEY_THUMBNAIL)) {
            menu.add(0, EditBookAbstractFragment.THUMBNAIL_OPTIONS_ID, 0, R.string.cover_options_cc_ellipsis)
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

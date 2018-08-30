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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
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
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
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
import com.eleybourn.bookcatalogue.utils.Convert;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewUtils;

import java.util.ArrayList;


public class BookEditFields extends BookDetailsAbstract
        implements OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    private static final int ACTIVITY_EDIT_AUTHORS = 1000;
    private static final int ACTIVITY_EDIT_SERIES = 1001;

    /**
     * Display the edit fields page
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.edit_book_fields, container, false);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this);
        double t0 = 0;
        double t1 = 0;

        try {
            if (BuildConfig.DEBUG) {
                t0 = System.currentTimeMillis();
            }
            super.onActivityCreated(savedInstanceState);
            if (BuildConfig.DEBUG) {
                t1 = System.currentTimeMillis();
            }

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
            }

            //Set click listener on Author field
            getView().findViewById(R.id.author)
                    .setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(getActivity(), EditAuthorList.class);
                            i.putExtra(ColumnNames.KEY_AUTHOR_ARRAY, mEditManager.getBookData().getAuthorList());
                            i.putExtra(ColumnNames.KEY_ROWID, mEditManager.getBookData().getRowId());
                            i.putExtra("title_label", ColumnNames.KEY_TITLE);
                            i.putExtra("title", mFields.getField(R.id.title).getValue().toString());
                            startActivityForResult(i, ACTIVITY_EDIT_AUTHORS);
                        }
                    });

            //Set click listener on Series field
            getView().findViewById(R.id.series)
                    .setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(getActivity(), EditSeriesList.class);
                            i.putExtra(ColumnNames.KEY_SERIES_ARRAY, mEditManager.getBookData().getSeriesList());
                            i.putExtra(ColumnNames.KEY_ROWID, mEditManager.getBookData().getRowId());
                            i.putExtra("title_label", ColumnNames.KEY_TITLE);
                            i.putExtra("title", mFields.getField(R.id.title).getValue().toString());
                            startActivityForResult(i, ACTIVITY_EDIT_SERIES);
                        }
                    });

            ArrayAdapter<String> publisher_adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getPublishers());
            mFields.setAdapter(R.id.publisher, publisher_adapter);
            ArrayAdapter<String> genre_adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getGenres());
            mFields.setAdapter(R.id.genre, genre_adapter);
            ArrayAdapter<String> language_adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getLanguages());
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
                            mDbHelper.getFormats(), formatField.getValue().toString(),
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
                            mDbHelper.getLanguages(), languageField.getValue().toString(),
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
                    frag.show(getFragmentManager(), "bookshelves_dialog");
                }
            });

            // Build the label for the book description if this is first time, otherwise will be built later
            if (savedInstanceState == null)
                buildDescription();

            // Setup the Save/Add/Anthology UI elements
            setupUi();

            try {
                ViewUtils.fixFocusSettings(getView());
                getView().findViewById(R.id.author).requestFocus();
            } catch (Exception e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.logError(e);
            }

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
            } else {
                mEditManager.setDirty(false);
            }

        } catch (IndexOutOfBoundsException | SQLException e) {
            Logger.logError(e);
        } finally {
            Tracker.exitOnActivityCreated(this);
        }

        if (BuildConfig.DEBUG) {
            double tn = System.currentTimeMillis();
            System.out.println("BEF oAC(super): " + (t1 - t0));
            System.out.println("BEF oAC: " + (tn - t0));
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
                .setDate(mFields.getField(R.id.date_published).getValue())
                .setTitle(R.string.date_published)
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
        double t0 = System.currentTimeMillis();
        Bundle extras = getActivity().getIntent().getExtras();
        final BookData book = mEditManager.getBookData();

        populateFieldsFromBook(book);
        if (book.getRowId() > 0) { //Populating from database
//			populateFieldsFromBook(book);
            //getActivity().setTitle(this.getResources().getString(R.string.menu));
        } else {
            if (extras != null) {
                // From the ISBN Search (add)
                try {
                    if (extras.containsKey("book")) {
                        throw new RuntimeException("[book] array passed in Intent");
                    } else {
                        Bundle values = extras.getParcelable("bookData");
                        for (Field f : mFields) {
                            if (!f.column.isEmpty() && values.containsKey(f.column)) {
                                try {
                                    f.setValue(values.getString(f.column));
                                } catch (Exception e) {
                                    String msg = "Populate field " + f.column + " failed: " + e.getMessage();
                                    Logger.logError(e, msg);
                                }
                            }
                        }

                    }
                } catch (NullPointerException e) {
                    Logger.logError(e);
                }
            }
            initDefaultShelf();
            setCoverImage();
        }

        populateAuthorListField();
        populateSeriesListField();

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);

        if (BuildConfig.DEBUG) {
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
                currShelf = mDbHelper.getBookshelfName(1);
            }
            String encoded_shelf = ArrayUtils.encodeListItem(currShelf, BOOKSHELF_SEPARATOR);
            Field fe = mFields.getField(R.id.bookshelf);
            fe.setValue(currShelf);
            book.setBookshelfList(encoded_shelf);
        }
    }

    private void setupUi() {
        final CheckBox cb = getView().findViewById(R.id.anthology);

        cb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mEditManager.setShowAnthology(cb.isChecked());
            }
        });
    }

    /**
     * Setup the 'description' header field to have a clickable link.
     */
    private void buildDescription() {
        double t0 = System.currentTimeMillis();
        // get the view
        final TextView tv = getView().findViewById(R.id.descriptionLabel);
        // Build the prefix text ('Description ')
        String baseText = getString(R.string.description) + " ";
        // Create the span ('Description (edit...)').
        SpannableString f = new SpannableString(baseText + "(" + getString(R.string.edit_lc_ellipsis) + ")");
        f.setSpan(new InternalSpan(new OnClickListener() {
            public void onClick(View v) {
                BookEditFields.this.showDescriptionDialog();
            }
        }), baseText.length(), f.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Quirks in Android mean old spans may be preserved; delete them
        clearOldInternalSpans(tv);
        // Set the text
        tv.setText(f);

        // Set the MovementMethod to allow clicks
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        // Prevent focus...not precisely sure why, but sample code does this
        tv.setFocusable(false);
        // Set the colour to prevent flicker on click
        tv.setTextColor(this.getResources().getColor(android.R.color.primary_text_dark_nodisable));
        if (BuildConfig.DEBUG) {
            System.out.println("BEF bDesc: " + (System.currentTimeMillis() - t0));
        }
    }

    private void clearOldInternalSpans(TextView tv) {
        CharSequence cs = tv.getText();
        if (cs instanceof Spannable) {
            final Spannable s = (Spannable) cs;
            InternalSpan[] spans = s.getSpans(0, tv.getText().length(), InternalSpan.class);
            for (InternalSpan span : spans) {
                s.removeSpan(span);
            }
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
                    if (resultCode == Activity.RESULT_OK && intent.hasExtra(ColumnNames.KEY_AUTHOR_ARRAY)) {
                        mEditManager.getBookData().setAuthorList(ArrayUtils.getAuthorFromIntentExtras(intent));
                        mEditManager.setDirty(true);
                    } else {
                        // Even though the dialog was terminated, some authors MAY have been updated/added.
                        mEditManager.getBookData().refreshAuthorList(mDbHelper);
                    }
                    // We do the fixup here because the user may have edited or merged authors; this will
                    // have already been applied to the database so no update is necessary, but we do need
                    // to update the data we display.
                    boolean oldDirty = mEditManager.isDirty();
                    populateAuthorListField();
                    mEditManager.setDirty(oldDirty);
                case ACTIVITY_EDIT_SERIES:
                    if (resultCode == Activity.RESULT_OK && intent.hasExtra(ColumnNames.KEY_SERIES_ARRAY)) {
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
        ArrayList<Author> list = mEditManager.getBookData().getAuthorList();
        if (list.size() != 0 && Utils.pruneList(mDbHelper, list)) {
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
        if (!setAllDone)
            mFields.setAll(book);
        populateFields();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        boolean thumbVisible = BookCataloguePreferences.getBoolean(FieldVisibility.prefix + "thumbnail", true);
        if (thumbVisible) {
            MenuItem thumbOptions = menu.add(0, BookEditFragmentAbstract.THUMBNAIL_OPTIONS_ID, 0, R.string.cover_options_cc_ellipsis);
            thumbOptions.setIcon(android.R.drawable.ic_menu_camera);
            thumbOptions.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * The callback received when the user "sets" the date in the dialog.
     * <p>
     * Build a full or partial date in SQL format
     */
    @Override
    public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
        String value = Convert.buildPartialDate(year, month, day);
        mFields.getField(dialogId).setValue(value);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     * <p>
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
        dialog.dismiss();
    }

    /**
     * The callback received when the user "sets" the text editor in the text editor dialog.
     * <p>
     * Set the appropriate field
     */
    @Override
    public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText) {
        mFields.getField(dialogId).setValue(newText);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the text editor dialog.
     * <p>
     * Dismiss it.
     */
    @Override
    public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onBookshelfCheckChanged(int dialogId,
                                        BookshelfDialogFragment dialog, boolean checked, String shelf, String textList, String encodedList) {

        Field fe = mFields.getField(R.id.bookshelf);
        fe.setValue(textList);
        mEditManager.getBookData().setBookshelfList(encodedList);
    }

    /**
     * Class to implement a clickable span of text and call a listener when text is clicked.
     */
    static class InternalSpan extends ClickableSpan {
        final OnClickListener mListener;

        InternalSpan(OnClickListener listener) {
            mListener = listener;
        }

        @Override
        public void onClick(View widget) {
            mListener.onClick(widget);
        }
    }

}

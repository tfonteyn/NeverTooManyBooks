/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;

import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.BookDetailsAbstractFragment.BOOKSHELF_SEPARATOR;

/**
 * Fragment wrapper for the Bookshelf list
 *
 * @author pjw
 */
public class BookshelfDialogFragment extends DialogFragment {
    private static final String BKEY_ROW_ID = "rowId";
    private static final String BKEY_TEXT = "text";
    private static final String BKEY_LIST = "list";

    /** ID passed by caller. Can be 0, will be passed back in event */
    private int mDialogId;
    /** Current display text for bookshelf list */
    private String mCurrText;
    /** Current encoded list of bookshelves */
    private String mCurrList;

    /**
     * Constructor
     *
     * @param dialogId    ID passed by caller. Can be 0, will be passed back in event
     * @param bookId      Book ID
     * @param initialText Initial display text for bookshelf list
     * @param initialList Initial encoded list of bookshelves
     *
     * @return Instance of dialog fragment
     */
    public static BookshelfDialogFragment newInstance(final int dialogId,
                                                      final long bookId,
                                                      @NonNull final String initialText,
                                                      @NonNull final String initialList) {
        BookshelfDialogFragment frag = new BookshelfDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        args.putLong(BKEY_ROW_ID, bookId);
        args.putString(BKEY_TEXT, initialText);
        args.putString(BKEY_LIST, initialList);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports event
     */
    @Override
    public void onAttach(Context a) {
        super.onAttach(a);

        if (!(a instanceof OnBookshelfCheckChangeListener))
            throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnBookshelfCheckChangeListener");

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bookshelves, null);
    }

    /**
     * Save instance variables that we need
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(BKEY_LIST, mCurrList);
        outState.putString(BKEY_TEXT, mCurrText);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Grab the args
        final Bundle ags = getArguments();
        mDialogId = ags.getInt(UniqueId.BKEY_DIALOG_ID);
        /* Book ID */
        long rowId = ags.getLong(BKEY_ROW_ID);
        // Retrieve dynamic values
        if (savedInstanceState != null && savedInstanceState.containsKey(BKEY_TEXT)) {
            mCurrText = savedInstanceState.getString(BKEY_TEXT);
        } else {
            mCurrText = ags.getString(BKEY_TEXT);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(BKEY_LIST)) {
            mCurrList = savedInstanceState.getString(BKEY_LIST);
        } else {
            mCurrList = ags.getString(BKEY_LIST);
        }

        // Setp the dialog
        getDialog().setTitle(R.string.select_bookshelves);

        // Build a list of shelves
        CatalogueDBAdapter db = new CatalogueDBAdapter(getActivity());
        db.open();
        try (Cursor bookshelves_for_book = db.fetchAllBookshelves(rowId)) {
            final View rootView = getView();

            // Handle the OK button
            Button button = rootView.findViewById(R.id.bookshelf_dialog_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BookshelfDialogFragment.this.dismiss();
                }
            });

            // Get the root view for the list of checkboxes
            LinearLayout cbRoot = rootView.findViewById(R.id.bookshelf_dialog_root);

            // Loop through all bookshelves and build the checkbox list
            if (bookshelves_for_book.moveToFirst()) {
                final String shelves = BOOKSHELF_SEPARATOR + mCurrList + BOOKSHELF_SEPARATOR;
                do {
                    final CheckBox cb = new CheckBox(getActivity());
                    boolean checked = false;
                    String db_bookshelf = bookshelves_for_book.getString(bookshelves_for_book.getColumnIndex(DatabaseDefinitions.DOM_BOOKSHELF_ID.name)).trim();
                    String db_encoded_bookshelf = ArrayUtils.encodeListItem(BOOKSHELF_SEPARATOR, db_bookshelf);
                    if (shelves.contains(BOOKSHELF_SEPARATOR + db_encoded_bookshelf + BOOKSHELF_SEPARATOR)) {
                        checked = true;
                    }
                    cb.setChecked(checked);
                    cb.setHintTextColor(Color.WHITE);
                    cb.setHint(db_bookshelf);
                    // Setup a click listener that sends all clicks back to the calling activity and maintains the two lists
                    cb.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String hint = cb.getHint() + "";
                            String name = hint.trim();
                            String encoded_name = ArrayUtils.encodeListItem(BOOKSHELF_SEPARATOR, name);
                            // If box is checked, then we just append to list
                            if (cb.isChecked()) {
                                if (mCurrText == null || mCurrText.isEmpty()) {
                                    mCurrText = name;
                                    mCurrList = encoded_name;
                                } else {
                                    mCurrText += ", " + name;
                                    mCurrList += BOOKSHELF_SEPARATOR + encoded_name;
                                }
                            } else {
                                // Get the underlying list
                                ArrayList<String> shelves = ArrayUtils.decodeList(BOOKSHELF_SEPARATOR, mCurrList);
                                // Start a new list
                                StringBuilder newList = new StringBuilder();
                                StringBuilder newText = new StringBuilder();
                                for (String s : shelves) {
                                    // If item in underlying list is non-blank...
                                    if (s != null && !s.isEmpty()) {
                                        // If item in underlying list does not match...
                                        if (!s.equalsIgnoreCase(name)) {
                                            // Convert item
                                            String item = ArrayUtils.encodeListItem(BOOKSHELF_SEPARATOR, s);
                                            // Append to list (or set to only element if list empty)
                                            if (newList.length() == 0) {
                                                newList.append(ArrayUtils.encodeListItem(BOOKSHELF_SEPARATOR, s));
                                                newText.append(s);
                                            } else {
                                                newList.append(BOOKSHELF_SEPARATOR).append(item);
                                                newText.append(", ").append(s);
                                            }
                                        }
                                    }
                                }
                                mCurrList = newList.toString();
                                mCurrText = newText.toString();
                            }
                            ((OnBookshelfCheckChangeListener) getActivity()).onBookshelfCheckChanged(
                                    mDialogId,
                                    BookshelfDialogFragment.this,
                                    cb.isChecked(), name, mCurrText, mCurrList);
                        }
                    });
                    cbRoot.addView(cb, cbRoot.getChildCount() - 1);
                }
                while (bookshelves_for_book.moveToNext());
            }

        } finally {
            db.close();
        }
    }

    /**
     * Interface for message sending
     *
     * @author pjw
     */
    public interface OnBookshelfCheckChangeListener {
        void onBookshelfCheckChanged(int dialogId, BookshelfDialogFragment dialog, boolean checked, String shelf, String textList, String encodedList);
    }
}

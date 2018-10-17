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
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.List;

/**
 * Fragment wrapper for the Bookshelf list
 *
 * @author pjw
 */
public class BookshelfDialogFragment extends DialogFragment {
    private static final String BKEY_ROW_ID = "rowId";
    private static final String BKEY_TEXT = "text";
    private static final String BKEY_LIST = "list";

    /** Current display text for bookshelf list */
    @Nullable
    private String mCurrText;
    /** Current encoded list (,|) of bookshelves */
    @Nullable
    private String mCurrList;

    /**
     * Constructor
     *
     * @param bookId      Book ID
     * @param initialText Initial display text for bookshelf list
     * @param initialList Initial encoded list of bookshelves
     *
     * @return Instance of dialog fragment
     */
    @NonNull
    public static BookshelfDialogFragment newInstance(final long bookId,
                                                      @NonNull final String initialText,
                                                      @NonNull final String initialList) {
        BookshelfDialogFragment frag = new BookshelfDialogFragment();
        Bundle args = new Bundle();
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
    @CallSuper
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof OnBookshelfCheckChangeListener))
            throw new RTE.MustImplementException(context, OnBookshelfCheckChangeListener.class);

    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bookshelves, null);
    }

    /**
     * Save instance variables that we need
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putString(BKEY_LIST, mCurrList);
        outState.putString(BKEY_TEXT, mCurrText);
        super.onSaveInstanceState(outState);
    }

    /**
     * Note for developer: the logic goes like this:
     *
     * 1. the incoming data has a two lists attached:
     * - BKEY_TEXT with human readable bookshelf names comma separated
     * - BKEY_LIST same, but with ,| etc encoded
     *
     * 2. a full list of bookshelves is fetched from the db
     * 3. walk that list, each one that occurs in BOTH 1+2 -> set the checkbox
     * 4. user makes checkbox changes
     * 5. each change will add or extract the chosen one from the lists of 1.
     * 6. upon "ok", the two lists from 1. are rebuild and send back.
     *
     * TODO: make strong coffee and see if the caller can present structured data using bookshelf ID's instead of two strings.
     *
     * Temporary removed from the db adapter, here is the sql that fetches a suitable data set.
     * Re-insert it in the db adapter, modify it to return an ArrayList<Bookshelf> and redo the below AFTER coffee is brewed.
     *
     * //     /**
     * //     * Return a Cursor over the list of all bookshelves in the database
     * //     *
     * //     * DOM_ID
     * //     * DOM_BOOKSHELF
     * //     * 0 or 1               boolean if book was on shelf or not.
     * //     *
     * //     * @param bookId the book, which in turn adds a new field on each row as to the active state of that bookshelf for the book
     * //     * @return Cursor over all bookshelves
     * //
     */
//    @NonNull
//    public Cursor fetchBookshelvesByBookId(final long bookId) {
//        String sql = "SELECT DISTINCT bs." + DOM_ID + " AS " + DOM_ID + "," +
//                " bs." + DOM_BOOKSHELF + " AS " + DOM_BOOKSHELF + "," +
//                " CASE WHEN w." + DOM_BOOK_ID + " IS NULL THEN 0 ELSE 1 END as " + DOM_BOOK_ID +
//                " FROM " + TBL_BOOKSHELF + " bs LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w" +
//
//                " ON (w." + DOM_BOOKSHELF + "=bs." + DOM_ID + " AND w." + DOM_BOOK_ID + "=" + bookId + ") " +
//                " ORDER BY Upper(bs." + DOM_BOOKSHELF + ") " + COLLATION;
//        return mSyncedDb.rawQuery(sql, new String[]{});
//    }
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //long bookId = getArguments().getLong(BKEY_ROW_ID);

        // Retrieve dynamic values
        if (savedInstanceState != null && savedInstanceState.containsKey(BKEY_TEXT)) {
            mCurrText = savedInstanceState.getString(BKEY_TEXT);
        } else {
            //noinspection ConstantConditions
            mCurrText = getArguments().getString(BKEY_TEXT);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(BKEY_LIST)) {
            mCurrList = savedInstanceState.getString(BKEY_LIST);
        } else {
            mCurrList = getArguments().getString(BKEY_LIST);
        }

        // Setup the dialog
        getDialog().setTitle(R.string.select_bookshelves);

        // Handle the OK button
        //noinspection ConstantConditions
        Button button = getView().findViewById(R.id.bookshelf_dialog_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BookshelfDialogFragment.this.dismiss();
            }
        });

        // Get the root view for the list of checkboxes
        LinearLayout cbRoot = getView().findViewById(R.id.bookshelf_dialog_root);

        final String shelves = Bookshelf.SEPARATOR + mCurrList + Bookshelf.SEPARATOR;

        // Loop through all bookshelves and build the checkbox list
        CatalogueDBAdapter db = new CatalogueDBAdapter(requireContext());
        db.open();
        List<Bookshelf> allBookshelves = db.getBookshelves();
        db.close();

        //noinspection ConstantConditions
        final List<String> currentShelves = ArrayUtils.decodeList(Bookshelf.SEPARATOR, mCurrList);

        for (Bookshelf bookshelf : allBookshelves) {
            String db_encoded_bookshelf = ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, bookshelf.name);

            final CompoundButton cb = new CheckBox(getActivity());
            cb.setChecked((shelves.contains(Bookshelf.SEPARATOR + db_encoded_bookshelf + Bookshelf.SEPARATOR)));
            //TODO: not sure... just delete, or use an attr so light-theme works.
            //cb.setHintTextColor(Color.WHITE);
            cb.setHint(bookshelf.name);
            // Setup a click listener that sends all clicks back to the calling activity and maintains the two lists
            cb.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String hint = cb.getHint() + "";
                    String name = hint.trim();
                    String encoded_name = ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, name);
                    // If box is checked, then we just append to list
                    if (cb.isChecked()) {
                        if (mCurrText == null || mCurrText.isEmpty()) {
                            mCurrText = name;
                            mCurrList = encoded_name;
                        } else {
                            mCurrText += Bookshelf.SEPARATOR + " " + name;
                            mCurrList += Bookshelf.SEPARATOR + encoded_name;
                        }
                    } else {
                        StringBuilder newList = new StringBuilder();
                        StringBuilder newText = new StringBuilder();

                        for (String shelf : currentShelves) {
                            // If item in underlying list is non-blank, and does not match
                            if (shelf != null && !shelf.isEmpty() && !shelf.equalsIgnoreCase(name)) {
                                // Append to list (or set to only element if list empty)
                                String encoded_shelf = ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, shelf);
                                if (newList.length() == 0) {
                                    newList.append(encoded_shelf);
                                    newText.append(shelf);
                                } else {
                                    newList.append(Bookshelf.SEPARATOR).append(encoded_shelf);
                                    newText.append(Bookshelf.SEPARATOR).append(" ").append(shelf);
                                }

                            }
                        }
                        mCurrList = newList.toString();
                        mCurrText = newText.toString();
                    }
                    ((OnBookshelfCheckChangeListener) requireActivity()).onBookshelfCheckChanged(mCurrText, mCurrList);
                }
            });

            cbRoot.addView(cb, cbRoot.getChildCount() - 1);
        }
    }

    /**
     * Interface for message sending
     *
     * @author pjw
     */
    public interface OnBookshelfCheckChangeListener {
        void onBookshelfCheckChanged(@NonNull final String textList,
                                     @NonNull final String encodedList);
    }
}

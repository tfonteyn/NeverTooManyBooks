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
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment wrapper for the Bookshelf list
 *
 * TOMF TODO: redo this code style as all other DialogFragment extenders in this project!
 *
 * @author pjw
 */
public class BookshelfDialogFragment extends DialogFragment {

    private long mBookId;

    /** all bookshelves in the database, with a 'selected' flag added reflecting the book being on this shelf or not */
    private List<SelectedBookshelf> mAllBookshelves;

    /**
     * Constructor
     *
     * @return Instance of dialog fragment
     */
    @NonNull
    public static BookshelfDialogFragment newInstance(final long bookId) {
        BookshelfDialogFragment frag = new BookshelfDialogFragment();
        Bundle args = new Bundle();
        args.putLong(UniqueId.KEY_ID, bookId);
        frag.setArguments(args);
        return frag;
    }


    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnBookshelfSelectionDialogResultListener))
            throw new RTE.MustImplementException(context, OnBookshelfSelectionDialogResultListener.class);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bookshelves, null);
    }

    /**
     * Save instance variables that we need
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putLong(UniqueId.KEY_ID, mBookId);
        super.onSaveInstanceState(outState);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey(UniqueId.KEY_ID)) {
            mBookId = savedInstanceState.getLong(UniqueId.KEY_ID);
        } else {
            //noinspection ConstantConditions
            mBookId = getArguments().getLong(UniqueId.KEY_ID);
        }

        mAllBookshelves = new ArrayList<>();

        // get the list of all bookshelves in the database
        CatalogueDBAdapter db = new CatalogueDBAdapter(requireContext())
                .open();
        Book book = db.getBookById(mBookId);
        // and the list of all shelves the book is currently on.
        //noinspection ConstantConditions
        List<Bookshelf> currentShelves = book.getBookshelfList();

        // Setup the dialog
        getDialog().setTitle(R.string.select_bookshelves);

        // Handle the OK button
        //noinspection ConstantConditions
        getView().findViewById(R.id.bookshelf_dialog_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // rebuild the books bookshelf list and send back to the Activity
                        ArrayList<Bookshelf> list = new ArrayList<>();
                        for (SelectedBookshelf sbs : mAllBookshelves) {
                            if (sbs.selected) {
                                list.add(sbs.bookshelf);
                            }
                        }
                        // let the activity know, so it can update its display
                        ((OnBookshelfSelectionDialogResultListener) requireActivity())
                                .OnBookshelfSelectionDialogResult(list);

                        // and that's all folks, close
                        BookshelfDialogFragment.this.dismiss();
                    }
                });

        // Get the root view for the list of checkboxes
        LinearLayout cbRoot = getView().findViewById(R.id.bookshelf_dialog_root);

        // Loop through all bookshelves in the database and build the shelves/checkbox list for this book
        for (Bookshelf bookshelf : db.getBookshelves()) {

            boolean selected = currentShelves.contains(bookshelf);

            mAllBookshelves.add(new SelectedBookshelf(bookshelf, selected));

            final CompoundButton cb = new CheckBox(getActivity());
            cb.setText(bookshelf.name);
            cb.setChecked(selected);
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = cb.getText().toString().trim();
                    // find the clicked list, and set its add/remove status
                    for (SelectedBookshelf sbs : mAllBookshelves) {
                        if (sbs.bookshelf.name.equals(name)) {
                            sbs.selected = cb.isChecked();
                        }
                    }
                }
            });

            cbRoot.addView(cb, cbRoot.getChildCount() - 1);
        }

        db.close();
    }

    /**
     * Interface for message sending
     *
     * @author pjw
     */
    public interface OnBookshelfSelectionDialogResultListener {
        void OnBookshelfSelectionDialogResult(@NonNull final ArrayList<Bookshelf> list);
    }

    private class SelectedBookshelf {
        Bookshelf bookshelf;
        boolean selected;

        SelectedBookshelf(@NonNull final Bookshelf bookshelf, final boolean selected) {
            this.bookshelf = bookshelf;
            this.selected = selected;
        }
    }
}

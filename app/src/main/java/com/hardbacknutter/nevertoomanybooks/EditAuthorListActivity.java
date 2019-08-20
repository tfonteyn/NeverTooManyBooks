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

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.baseactivity.EditObjectListActivity;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit a list of authors provided in an {@code ArrayList<Author>}
 * and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditBookAuthorDialogFragment} for list
 */
public class EditAuthorListActivity
        extends EditObjectListActivity<Author> {

    /**
     * Constructor.
     */
    public EditAuthorListActivity() {
        super(UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_author;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.title_edit_book_authors);

        mAutoCompleteAdapter =
                new ArrayAdapter<>(this,
                                   android.R.layout.simple_dropdown_item_1line,
                                   mModel.getDb()
                                         .getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED));

        mAutoCompleteTextView = findViewById(R.id.author);
        mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);
    }

    @Override
    protected RecyclerViewAdapterBase
    createListAdapter(@NonNull final ArrayList<Author> list,
                      @NonNull final StartDragListener dragStartListener) {
        return new AuthorListAdapter(getLayoutInflater(), list, dragStartListener);
    }

    @Override
    protected void onAdd(@NonNull final View target) {
        String name = mAutoCompleteTextView.getText().toString().trim();
        if (name.isEmpty()) {
            UserMessage.show(mAutoCompleteTextView, R.string.warning_missing_name);
            return;
        }

        Author newAuthor = Author.fromString(name);
        // see if it already exists
        newAuthor.fixId(this, mModel.getDb());
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            UserMessage.show(mAutoCompleteTextView, R.string.warning_author_already_in_list);
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAutoCompleteTextView.setText("");
    }

    /**
     * Handle the edits.
     *
     * @param author        the original data.
     * @param newAuthorData a holder for the edited data.
     */
    private void processChanges(@NonNull final Author author,
                                @NonNull final Author newAuthorData) {

        // See if the old one is used by any other books.
        long nrOfReferences = mModel.getDb().countBooksByAuthor(author)
                              + mModel.getDb().countTocEntryByAuthor(author);

        // if it's not, then we can simply re-use the old object.
        if (mModel.isSingleUsage(nrOfReferences)) {
            /*
             * Use the original author, but update its fields
             *
             * see below and {@link DAO#insertBookDependents} where an *insert* will be done
             * The 'old' author will be orphaned.
             * TODO: simplify / don't orphan?
             */
            author.copyFrom(newAuthorData);
            ItemWithFixableId.pruneList(this, mModel.getDb(), mList);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author
        // is used in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);

        AlertDialog dialog = new AlertDialog.Builder(this)
                                     .setTitle(R.string.title_scope_of_change)
                                     .setIconAttribute(android.R.attr.alertDialogIcon)
                                     .setMessage(getString(R.string.confirm_apply_author_changed,
                                                           author.getSortName(),
                                                           newAuthorData.getSortName(), allBooks))
                                     .create();

        /*
         * choosing 'this book':
         * Copy the data fields (name,..) from the holder to the 'old' author.
         * and remove any duplicates.
         *
         * When the actual book is saved, {@link DAO#updateBook} will call
         * {@link DAO#insertBookDependents} which when updating TBL_BOOK_AUTHORS
         * will first try and find the author based on name.
         * If its names differ -> new Author -> inserts the new author.
         * Result: *this* book now uses the modified/new author,
         * while all others keep using the original one.
         *
         * TODO: speculate if it would not be easier to:
         * - fixup(newAuthor) and  if id == 0 insert newAuthor
         * - remove old author from book
         * - add new author to book
         */
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book),
                         (d, which) -> {
                             author.copyFrom(newAuthorData);
                             ItemWithFixableId.pruneList(this, mModel.getDb(), mList);
                             mListAdapter.notifyDataSetChanged();
                         });

        /*
         * Choosing 'all books':
         * globalReplace:
         * - Find/update or insert the new Author.
         * - update the TOC of all books so they use the new author id.
         * - update TBL_BOOK_AUTHORS for all books to use the new author id
         * - re-order the 'position' if needed.
         * Result:
         * - all books previously using the olf author, now point to the new author.
         * - the old author will still exist, but won't be in use.
         *
         * Copy the data fields (name,..) from the holder to the 'old' author.
         * and remove any duplicates.
         *
         * When the actual book is saved, {@link DAO#updateBook} will call
         * {@link DAO#insertBookDependents} which when updating TBL_BOOK_AUTHORS
         * will first try and find the author (with the old id) based on name.
         * => it will find the NEW author, and update the id in memory (old becomes new)
         * Result:
         * - this book uses the new author (by recycling the old object with all new id/data)
         *
         * TODO: speculate if this can be simplified.
         */
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, (d, which) -> {
            Locale userLocale = LocaleUtils.getPreferredLocale();
            mModel.setGlobalReplacementsMade(
                    mModel.getDb().globalReplace(author, newAuthorData, userLocale));
            author.copyFrom(newAuthorData);
            ItemWithFixableId.pruneList(this, mModel.getDb(), mList);
            mListAdapter.notifyDataSetChanged();
        });

        dialog.show();
    }

    /**
     * Edit an Author from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     */
    public static class EditBookAuthorDialogFragment
            extends EditAuthorBaseDialogFragment {

        /** Fragment manager tag. */
        static final String TAG = "EditBookAuthorDialogFragment";

        private EditAuthorListActivity mActivity;

        /**
         * Constructor.
         *
         * @param author to edit.
         *
         * @return the instance
         */
        static EditBookAuthorDialogFragment newInstance(@NonNull final Author author) {
            EditBookAuthorDialogFragment frag = new EditBookAuthorDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onAttach(@NonNull final Context context) {
            super.onAttach(context);
            mActivity = (EditAuthorListActivity) context;
        }

        /**
         * Handle the edits.
         *
         * @param author        the original data.
         * @param newAuthorData a holder for the edited data.
         */
        protected void confirmChanges(@NonNull final Author author,
                                      @NonNull final Author newAuthorData) {
            mActivity.processChanges(author, newAuthorData);
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView authorView;
        @NonNull
        final TextView authorSortView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            authorView = itemView.findViewById(R.id.row_author);
            authorSortView = itemView.findViewById(R.id.row_author_sort);
        }
    }

    protected class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        /**
         * Constructor.
         *
         * @param inflater          LayoutInflater to use
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final LayoutInflater inflater,
                          @NonNull final ArrayList<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(inflater, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                                .inflate(R.layout.row_edit_author_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            Author author = getItem(position);

            holder.authorView.setText(author.getLabel());

            if (author.getLabel().equals(author.getSortName())) {
                holder.authorSortView.setVisibility(View.GONE);
            } else {
                holder.authorSortView.setVisibility(View.VISIBLE);
                holder.authorSortView.setText(author.getSortName());
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.findFragmentByTag(EditBookAuthorDialogFragment.TAG) == null) {
                    EditBookAuthorDialogFragment.newInstance(author)
                                                .show(fm, EditBookAuthorDialogFragment.TAG);
                }
            });
        }
    }
}

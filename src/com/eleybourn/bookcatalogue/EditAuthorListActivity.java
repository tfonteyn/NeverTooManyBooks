/*
 * @copyright 2011 Philip Warner
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.entities.EditAuthorBaseDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit a list of authors provided in an ArrayList and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditBookAuthorDialogFragment} for list
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity
        extends EditObjectListActivity<Author> {

    /** Main screen Author name field. */
    private AutoCompleteTextView mAuthorNameView;

    /** Adapter for mAuthorNameView. */
    @SuppressWarnings("FieldCanBeLocal")
    private ArrayAdapter<String> mAuthorAdapter;

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
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        mAuthorAdapter = new ArrayAdapter<>(this,
                                            android.R.layout.simple_dropdown_item_1line,
                                            mDb.getAuthorsFormattedName());

        mAuthorNameView = findViewById(R.id.author);
        mAuthorNameView.setAdapter(mAuthorAdapter);
    }

    @Override
    protected void onAdd(@NonNull final View target) {
        String authorName = mAuthorNameView.getText().toString().trim();
        if (authorName.isEmpty()) {
            UserMessage.showUserMessage(mAuthorNameView, R.string.warning_required_name);
            return;
        }

        Author newAuthor = Author.fromString(authorName);
        // see if it already exists
        newAuthor.fixupId(mDb);
        // and check it's not already in the list.
        for (Author author : mList) {
            if (author.equals(newAuthor)) {
                UserMessage.showUserMessage(mAuthorNameView,
                                            R.string.warning_author_already_in_list);
                return;
            }
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAuthorNameView.setText("");
    }

    @Override
    protected boolean onSave(@NonNull final Intent data) {
        final AutoCompleteTextView view = findViewById(R.id.author);
        String str = view.getText().toString().trim();
        if (str.isEmpty()) {
            // no current edit, so we're good to go.
            return true;
        }

        // if the user had enter a (partial) new name, check if it's ok to leave
        StandardDialogs.showConfirmUnsavedEditsDialog(this, () -> {
            // runs when user clicks 'exit anyway'
            view.setText("");
            doSave();
        });
        return false;
    }

    @Override
    protected RecyclerViewAdapterBase
    createListAdapter(@NonNull final ArrayList<Author> list,
                      @NonNull final StartDragListener dragStartListener) {
        return new AuthorListAdapter(this, list, dragStartListener);
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
        long nrOfReferences = mDb.countBooksByAuthor(author)
                + mDb.countTocEntryByAuthor(author);
        boolean usedByOthers = nrOfReferences > (mRowId == 0 ? 0 : 1);

        // if it's not, then we can simply re-use the old object.
        if (!usedByOthers) {
            /*
             * Use the original author, but update its fields
             *
             * see below and {@link DAO#insertBookDependents} where an *insert* will be done
             * The 'old' author will be orphaned.
             * TODO: simplify / don't orphan?
             */
            author.copyFrom(newAuthorData);
            Utils.pruneList(mDb, mList);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author
        // is used in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(getString(R.string.confirm_apply_author_changed,
                                      author.getSortName(), newAuthorData.getSortName(), allBooks))
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
                             d.dismiss();

                             author.copyFrom(newAuthorData);
                             Utils.pruneList(mDb, mList);
                             mListAdapter.notifyDataSetChanged();
                         });

        /*
         * Choosing 'all books':
         * globalReplaceAuthor:
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
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks,
                         (d, which) -> {
                             d.dismiss();

                             mGlobalReplacementsMade = mDb.globalReplaceAuthor(author,
                                                                               newAuthorData,
                                                                               LocaleUtils.getPreferredLocal());

                             author.copyFrom(newAuthorData);
                             Utils.pruneList(mDb, mList);
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
        public static final String TAG = EditBookAuthorDialogFragment.class.getSimpleName();

        /**
         * Constructor.
         *
         * @param author to edit.
         *
         * @return the instance
         */
        public static EditBookAuthorDialogFragment newInstance(@NonNull final Author author) {
            EditBookAuthorDialogFragment frag = new EditBookAuthorDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(DBDefinitions.KEY_AUTHOR, author);
            frag.setArguments(args);
            return frag;
        }

        /**
         * Handle the edits.
         *
         * @param author        the original data.
         * @param newAuthorData a holder for the edited data.
         */
        protected void confirmChanges(@NonNull final Author author,
                                      @NonNull final Author newAuthorData) {
            //noinspection ConstantConditions
            ((EditAuthorListActivity) getActivity()).processChanges(author, newAuthorData);
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

        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final ArrayList<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
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

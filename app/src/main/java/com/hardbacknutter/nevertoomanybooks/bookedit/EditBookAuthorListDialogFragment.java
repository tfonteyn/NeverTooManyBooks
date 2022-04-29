/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EntityFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Authors of a Book.
 * <p>
 * Maybe TODO: cannot set author type when creating but only when editing existing author.
 */
public class EditBookAuthorListDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookAuthorListDlg";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_AUTHOR = TAG + ":rk:" + EditBookAuthorDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookViewModel mVm;
    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mVm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /** View Binding. */
    private DialogEditBookAuthorListBinding mVb;
    /** the rows. */
    private ArrayList<Author> mList;
    /** The adapter for the list itself. */
    private AuthorListAdapter mListAdapter;

    private final EditBookAuthorDialogFragment.Launcher mOnEditAuthorLauncher =
            new EditBookAuthorDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final Author original,
                                     @NonNull final Author modified) {
                    processChanges(original, modified);
                }
            };

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookAuthorListDialogFragment() {
        super(R.layout.dialog_edit_book_author_list);
        setForceFullscreen();
    }

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookAuthorListDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        mOnEditAuthorLauncher.registerForFragmentResult(getChildFragmentManager(), RK_EDIT_AUTHOR,
                                                        this);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookAuthorListBinding.bind(view);

        mVb.toolbar.setSubtitle(mVm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getAllAuthorNames());
        mVb.author.setAdapter(nameAdapter);
        mVb.author.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mVb.lblAuthor.setError(null);
            }
        });

        // soft-keyboards 'done' button act as a shortcut to add the author
        mVb.author.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd();
                return true;
            }
            return false;
        });

        mVb.authorList.setHasFixedSize(true);

        mList = mVm.getBook().getAuthors();
        mListAdapter = new AuthorListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.authorList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.authorList);
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            onAdd();
            return true;
        }
        return false;
    }

    /**
     * Create a new entry.
     */
    private void onAdd() {
        // clear any previous error
        mVb.lblAuthor.setError(null);

        final String name = mVb.author.getText().toString().trim();
        if (name.isEmpty()) {
            mVb.lblAuthor.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        // Editing the Author type is not provided on the main screen.
        // The user must open the detail dialog after creation of the entry.
        final Author newAuthor = Author.from(name);

        // see if it already exists
        //noinspection ConstantConditions
        mVm.fixId(getContext(), newAuthor);
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            mVb.lblAuthor.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            mList.add(newAuthor);
            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.authorList.scrollToPosition(mListAdapter.getItemCount() - 1);

            // clear the form for next entry
            mVb.author.setText("");
            mVb.author.requestFocus();
        }
    }

    protected boolean saveChanges() {
        if (!mVb.author.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                mVb.author.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        mVm.updateAuthors(mList);
        return true;
    }

    /**
     * Process the modified (if any) data.
     *
     * @param original the original data the user was editing
     * @param modified the modifications the user made in a placeholder object.
     *                 Non-modified data was copied here as well.
     */
    @SuppressLint("NotifyDataSetChanged")
    private void processChanges(@NonNull final Author original,
                                @NonNull final Author modified) {

        final Context context = getContext();

        // name not changed ?
        if (original.getFamilyName().equals(modified.getFamilyName())
            && original.getGivenNames().equals(modified.getGivenNames())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            original.setComplete(modified.isComplete());

            // Type is not part of the Author table, but of the book_author table.
            if (original.getType() != modified.getType()) {
                // so if the type is different, just update it
                original.setType(modified.getType());
                //noinspection ConstantConditions
                mVm.getBook().pruneAuthors(context, true);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name was modified. Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mVm.isSingleUsage(context, original)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            original.copyFrom(modified, true);
            mVm.getBook().pruneAuthors(context, true);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the object was modified and it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        StandardDialogs.confirmScopeForChange(
                context, original.getLabel(context), modified.getLabel(context),
                () -> changeForAllBooks(original, modified),
                () -> changeForThisBook(original, modified));
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForAllBooks(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (mVm.changeForAllBooks(getContext(), original, modified)) {
            mListAdapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForThisBook(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // treat the new data as a new Author; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Author. That's ok, it will get
        // garbage collected from the database sooner or later.
        //noinspection ConstantConditions
        if (mVm.changeForThisBook(getContext(), original, modified)) {
            mListAdapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }

        //URGENT: updated author(s): Book gets them, but TocEntries remain using old set
        //
        // A TocEntry is unique based on author and title_od.
        // Updating the in-memory TOC list and/or the TocEntries stored in the database
        // with the new author:
        // .
        // The problem is two-fold:
        // If we simply create a new TocEntry?
        // - old one not used anywhere else ? ok, just delete it
        // - old one present in other books ? replace ? leave as-is ?
        // but it's the SAME story (text), now existing with two different authors.
        // - update the TocEntry as-is... i.e. in the database?
        // .. more headaches....
        // .
        // SOLUTION one of:
        // - just ASK the user with a "mod toc" or "no"
        // - don't bother, assume this won't be needed very often and
        //   have the user will do it manually
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView authorView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            authorView = itemView.findViewById(R.id.row_author);
        }
    }

    private class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        @NonNull
        private final FieldFormatter<Author> mFormatter;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final List<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);

            mFormatter = new EntityFormatter<>(Details.Full);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            final Holder holder = new Holder(view);
            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> mOnEditAuthorLauncher.launch(
                    mVm.getBook().getTitle(),
                    getItem(holder.getBindingAdapterPosition())));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Author author = getItem(position);
            mFormatter.apply(author, holder.authorView);
        }
    }
}

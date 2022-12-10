/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EntityFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Authors of a Book.
 */
public class EditBookAuthorListDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookAuthorListDlg";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_AUTHOR =
            TAG + ":rk:" + EditBookAuthorDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookAuthorListBinding vb;
    /** the rows. */
    private List<Author> authorList;
    /** React to list changes. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                    vm.updateAuthors(authorList);
                }
            };
    /** The adapter for the list itself. */
    private AuthorListAdapter adapter;

    private final EditBookAuthorDialogFragment.Launcher editLauncher =
            new EditBookAuthorDialogFragment.Launcher() {
                @Override
                public void onAdd(@NonNull final Author author) {
                    add(author);
                }

                @Override
                public void onModified(@NonNull final Author original,
                                       @NonNull final Author modified) {
                    processChanges(original, modified);
                }
            };

    private final AdapterRowHandler adapterRowHandler = new AdapterRowHandler() {

        @Override
        public void edit(final int position) {
            final Book book = vm.getBook();
            editLauncher.launch(book.getTitle(),
                                book.getString(DBKey.LANGUAGE),
                                EditAction.Edit,
                                authorList.get(position));
        }
    };

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

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
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        editLauncher.registerForFragmentResult(getChildFragmentManager(),
                                               EditAuthorViewModel.BKEY_REQUEST_KEY,
                                               RK_EDIT_AUTHOR,
                                               this);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditBookAuthorListBinding.bind(view);

        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllAuthorNames());
        vb.author.setAdapter(nameAdapter);
        vb.author.addTextChangedListener((ExtTextWatcher) s -> vb.lblAuthor.setError(null));
        vb.author.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                vb.lblAuthor.setError(null);
            }
        });

        // soft-keyboards 'done' button act as a shortcut to add the author
        vb.author.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd(false);
                return true;
            }
            return false;
        });

        vb.authorList.setHasFixedSize(true);

        authorList = vm.getBook().getAuthors();
        adapter = new AuthorListAdapter(getContext(), authorList, adapterRowHandler,
                                        vh -> itemTouchHelper.startDrag(vh));
        vb.authorList.setAdapter(adapter);
        adapter.registerAdapterDataObserver(adapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.authorList);
    }

    @Override
    public void onDestroyView() {
        adapter.unregisterAdapterDataObserver(adapterDataObserver);
        super.onDestroyView();
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            // R.id.btn_add
            // R.id.btn_add_details
            onAdd(button.getId() == R.id.btn_add_details);
            return true;
        }
        return false;
    }

    /**
     * Create a new entry.
     *
     * @param withDetails {@code true} to use the detailed dialog to add an Author
     *                    {@code false} to just add the Author name as-is
     */
    private void onAdd(final boolean withDetails) {
        // clear any previous error
        vb.lblAuthor.setError(null);

        final String name = vb.author.getText().toString().trim();
        if (name.isBlank()) {
            vb.lblAuthor.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Author author = Author.from(name);
        if (withDetails) {
            final Book book = vm.getBook();
            editLauncher.launch(book.getTitle(),
                                book.getString(DBKey.LANGUAGE),
                                EditAction.Add,
                                author);
        } else {
            add(author);
        }
    }

    /**
     * Add the given Author to the list, providing it's not already there.
     *
     * @param author to add
     */
    private void add(@NonNull final Author author) {
        // see if it already exists
        //noinspection ConstantConditions
        vm.fixId(getContext(), author);

        // and check it's not already in the list.
        if (authorList.contains(author)) {
            vb.lblAuthor.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            authorList.add(author);
            adapter.notifyItemInserted(authorList.size() - 1);
            vb.authorList.scrollToPosition(adapter.getItemCount() - 1);

            // clear the form for next entry
            vb.author.setText("");
            vb.author.requestFocus();
        }
    }

    protected boolean saveChanges() {
        if (!vb.author.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                vb.author.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        // The list itself is already saved by the adapterDataObserver
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

        // The name was not changed OR
        // the name was modified but not used by any other books.
        //noinspection ConstantConditions
        if ((original.getFamilyName().equals(modified.getFamilyName())
             && original.getGivenNames().equals(modified.getGivenNames()))
            || vm.isSingleUsage(context, original)) {

            original.copyFrom(modified, true);
            adapter.notifyDataSetChanged();

        } else {
            // Object was modified and it's used in more than one place.
            // We need to ask the user if they want to make the changes globally.
            StandardDialogs.confirmScopeForChange(
                    context, context.getString(R.string.lbl_author),
                    //TODO: if the names are the same, we should probably state
                    // that some other attribute was changed
                    original.getLabel(context), modified.getLabel(context),
                    () -> changeForAllBooks(original, modified),
                    () -> changeForThisBook(original, modified));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForAllBooks(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (vm.changeForAllBooks(getContext(), original, modified)) {
            adapter.notifyDataSetChanged();
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
        if (vm.changeForThisBook(getContext(), original, modified)) {
            adapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }

        //FIXME: updated author(s): Book gets them, but TocEntries remain using old set
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

    private static class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        @NonNull
        private final FieldFormatter<Author> formatter;
        @NonNull
        private final AdapterRowHandler adapterRowHandler;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final List<Author> items,
                          @NonNull final AdapterRowHandler adapterRowHandler,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            this.adapterRowHandler = adapterRowHandler;

            formatter = new EntityFormatter<>(Details.Full);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            final Holder holder = new Holder(view);
            holder.rowDetailsView.setOnClickListener(
                    v -> adapterRowHandler.edit(holder.getBindingAdapterPosition()));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Author author = getItem(position);
            formatter.apply(author, holder.authorView);
        }
    }
}

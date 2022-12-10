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
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookPublisherListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Publishers of a Book.
 */
public class EditBookPublisherListDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookPubListDlg";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_PUBLISHER =
            TAG + ":rk:" + EditBookPublisherDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookPublisherListBinding vb;
    /** the rows. */
    private List<Publisher> publisherList;
    /** React to list changes. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                    vm.updatePublishers(publisherList);
                }
            };
    /** The adapter for the list itself. */
    private PublisherListAdapter adapter;

    private final EditBookPublisherDialogFragment.Launcher editLauncher =
            new EditBookPublisherDialogFragment.Launcher() {
                @Override
                public void onAdd(@NonNull final Publisher publisher) {
                    add(publisher);
                }

                @Override
                public void onModified(@NonNull final Publisher original,
                                       @NonNull final Publisher modified) {
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
                                publisherList.get(position));
        }
    };
    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookPublisherListDialogFragment() {
        super(R.layout.dialog_edit_book_publisher_list);
        setForceFullscreen();
    }

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookPublisherListDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        editLauncher.registerForFragmentResult(getChildFragmentManager(),
                                               EditBookPublisherDialogFragment.BKEY_REQUEST_KEY,
                                               RK_EDIT_PUBLISHER,
                                               this);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditBookPublisherListBinding.bind(view);

        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllPublisherNames());
        vb.publisherName.setAdapter(nameAdapter);
        vb.publisherName.addTextChangedListener((ExtTextWatcher) s ->
                vb.lblPublisherName.setError(null));
        vb.publisherName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                vb.lblPublisherName.setError(null);
            }
        });

        // soft-keyboards 'done' button act as a shortcut to add the publisher
        vb.publisherName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd(false);
                return true;
            }
            return false;
        });

        vb.publisherList.setHasFixedSize(true);

        publisherList = vm.getBook().getPublishers();
        adapter = new PublisherListAdapter(getContext(), publisherList, adapterRowHandler,
                                           vh -> itemTouchHelper.startDrag(vh));
        vb.publisherList.setAdapter(adapter);
        adapter.registerAdapterDataObserver(adapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.publisherList);
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
     * @param withDetails {@code true} to use the detailed dialog to add a Publisher
     *                    {@code false} to just add the Publisher name as-is
     */
    private void onAdd(final boolean withDetails) {
        // clear any previous error
        vb.lblPublisherName.setError(null);

        final String name = vb.publisherName.getText().toString().trim();
        if (name.isEmpty()) {
            vb.lblPublisherName.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Publisher publisher = Publisher.from(name);
        if (withDetails) {
            final Book book = vm.getBook();
            editLauncher.launch(book.getTitle(),
                                book.getString(DBKey.LANGUAGE),
                                EditAction.Add,
                                publisher);
        } else {
            add(publisher);
        }
    }

    /**
     * Add the given Publisher to the list, providing it's not already there.
     *
     * @param publisher to add
     */
    private void add(@NonNull final Publisher publisher) {
        // see if it already exists
        //noinspection ConstantConditions
        vm.fixId(getContext(), publisher);
        // and check it's not already in the list.
        if (publisherList.contains(publisher)) {
            vb.lblPublisherName.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            publisherList.add(publisher);
            adapter.notifyItemInserted(publisherList.size() - 1);
            vb.publisherList.scrollToPosition(adapter.getItemCount() - 1);

            // clear the form for next entry
            vb.publisherName.setText("");
            vb.publisherName.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!vb.publisherName.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                vb.publisherName.setText("");
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
    private void processChanges(@NonNull final Publisher original,
                                @NonNull final Publisher modified) {

        final Context context = getContext();

        // The name was not changed OR
        // the name was modified but not used by any other books.
        //noinspection ConstantConditions
        if (original.getName().equals(modified.getName())
            || vm.isSingleUsage(context, original)) {

            original.copyFrom(modified);
            adapter.notifyDataSetChanged();

        } else {
            // Object was modified and it's used in more than one place.
            // We need to ask the user if they want to make the changes globally.
            StandardDialogs.confirmScopeForChange(
                    context, context.getString(R.string.lbl_publisher),
                    //TODO: if the names are the same, we should probably state
                    // that some other attribute was changed
                    original.getLabel(context), modified.getLabel(context),
                    () -> changeForAllBooks(original, modified),
                    () -> changeForThisBook(original, modified));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForAllBooks(@NonNull final Publisher original,
                                   @NonNull final Publisher modified) {

        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (vm.changeForAllBooks(getContext(), original, modified)) {
            adapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForThisBook(@NonNull final Publisher original,
                                   @NonNull final Publisher modified) {
        // treat the new data as a new Publisher; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Publisher. That's ok, it will get
        // garbage collected from the database sooner or later.
        //noinspection ConstantConditions
        if (vm.changeForThisBook(getContext(), original, modified)) {
            adapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView publisherView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            publisherView = itemView.findViewById(R.id.row_publisher);
        }
    }

    private static class PublisherListAdapter
            extends RecyclerViewAdapterBase<Publisher, Holder> {

        @NonNull
        private final AdapterRowHandler adapterRowHandler;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Publishers
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        PublisherListAdapter(@NonNull final Context context,
                             @NonNull final List<Publisher> items,
                             @NonNull final AdapterRowHandler adapterRowHandler,
                             @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            this.adapterRowHandler = adapterRowHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_publisher_list, parent, false);
            final Holder holder = new Holder(view);
            holder.rowDetailsView.setOnClickListener(
                    v -> adapterRowHandler.edit(holder.getBindingAdapterPosition()));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Publisher publisher = getItem(position);
            holder.publisherView.setText(publisher.getLabel(getContext()));
        }
    }
}

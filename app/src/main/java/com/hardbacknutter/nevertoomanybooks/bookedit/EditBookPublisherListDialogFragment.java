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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookPublisherListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
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
    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /** View Binding. */
    private DialogEditBookPublisherListBinding vb;
    /** the rows. */
    private List<Publisher> publisherList;
    /** The adapter for the list itself. */
    private PublisherListAdapter adapter;
    private final EditBookPublisherDialogFragment.Launcher editPublisherLauncher =
            new EditBookPublisherDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final Publisher original,
                                     @NonNull final Publisher modified) {
                    processChanges(original, modified);
                }
            };

    private final AdapterRowHandler adapterRowHandler = new AdapterRowHandler() {

        @Override
        public void edit(final int position) {
            editPublisherLauncher.launch(vm.getBook().getTitle(),
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

        editPublisherLauncher.registerForFragmentResult(getChildFragmentManager(),
                                                        RK_EDIT_PUBLISHER, this);
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
        vb.publisher.setAdapter(nameAdapter);
        vb.publisher.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                vb.lblPublisher.setError(null);
            }
        });

        // soft-keyboards 'done' button act as a shortcut to add the publisher
        vb.publisher.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd();
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
        vb.lblPublisher.setError(null);

        final String name = vb.publisher.getText().toString().trim();
        if (name.isEmpty()) {
            vb.lblPublisher.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Publisher newPublisher = Publisher.from(name);

        // see if it already exists
        //noinspection ConstantConditions
        vm.fixId(getContext(), newPublisher);
        // and check it's not already in the list.
        if (publisherList.contains(newPublisher)) {
            vb.lblPublisher.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            publisherList.add(newPublisher);
            adapter.notifyItemInserted(publisherList.size() - 1);
            vb.publisherList.scrollToPosition(adapter.getItemCount() - 1);

            // clear the form for next entry
            vb.publisher.setText("");
            vb.publisher.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!vb.publisher.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                vb.publisher.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        vm.updatePublishers(publisherList);
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

        // name not changed ?
        if (original.getName().equals(modified.getName())) {
            return;
        }

        final Context context = getContext();

        // The name was modified. Check if it's used by any other books.
        //noinspection ConstantConditions
        if (vm.isSingleUsage(context, original)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            original.copyFrom(modified);
            vm.getBook().prunePublishers(context, true);
            adapter.notifyDataSetChanged();
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

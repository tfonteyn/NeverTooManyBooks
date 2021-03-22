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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookPublisherBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookPublisherListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;
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
            TAG + ":rk:" + EditPublisherForBookDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookFragmentViewModel mVm;
    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mVm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /** View Binding. */
    private DialogEditBookPublisherListBinding mVb;
    /** the rows. */
    private ArrayList<Publisher> mList;
    /** The adapter for the list itself. */
    private PublisherListAdapter mListAdapter;
    private final EditBookBaseFragment.EditItemLauncher<Publisher> mOnEditPublisherLauncher =
            new EditBookBaseFragment.EditItemLauncher<Publisher>(RK_EDIT_PUBLISHER) {
                @Override
                public void onResult(@NonNull final Publisher original,
                                     @NonNull final Publisher modified) {
                    processChanges(original, modified);
                }
            };

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

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

        mOnEditPublisherLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookPublisherListBinding.bind(view);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

        mVb.toolbar.setSubtitle(mVm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getAllPublisherNames());
        mVb.publisher.setAdapter(nameAdapter);
        mVb.publisher.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mVb.lblPublisher.setError(null);
            }
        });

        // soft-keyboards 'done' button act as a shortcut to add the publisher
        mVb.publisher.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd();
                return true;
            }
            return false;
        });

        mVb.publisherList.setHasFixedSize(true);

        mList = mVm.getBook().getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        mListAdapter = new PublisherListAdapter(getContext(), mList,
                                                vh -> mItemTouchHelper.startDrag(vh));
        mVb.publisherList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.publisherList);
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
        mVb.lblPublisher.setError(null);

        final String name = mVb.publisher.getText().toString().trim();
        if (name.isEmpty()) {
            mVb.lblPublisher.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Publisher newPublisher = Publisher.from(name);

        // see if it already exists
        //noinspection ConstantConditions
        mVm.fixId(getContext(), newPublisher);
        // and check it's not already in the list.
        if (mList.contains(newPublisher)) {
            mVb.lblPublisher.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            mList.add(newPublisher);
            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.publisherList.scrollToPosition(mListAdapter.getItemCount() - 1);

            // clear the form for next entry
            mVb.publisher.setText("");
            mVb.publisher.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!mVb.publisher.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                mVb.publisher.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        mVm.updatePublishers(mList);
        return true;
    }

    /**
     * Process the modified (if any) data.
     *
     * @param original the original data the user was editing
     * @param modified the modifications the user made in a placeholder object.
     *                 Non-modified data was copied here as well.
     */
    private void processChanges(@NonNull final Publisher original,
                                @NonNull final Publisher modified) {

        // name not changed ?
        if (original.getName().equals(modified.getName())) {
            return;
        }

        // The name was modified. Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mVm.isSingleUsage(getContext(), original)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            original.copyFrom(modified);
            //noinspection ConstantConditions
            mVm.prunePublishers(getContext());
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the object was modified and it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        StandardDialogs.confirmScopeForChange(
                getContext(), original, modified,
                () -> changeForAllBooks(original, modified),
                () -> changeForThisBook(original, modified));
    }

    private void changeForAllBooks(@NonNull final Publisher original,
                                   @NonNull final Publisher modified) {

        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (mVm.changeForAllBooks(getContext(), original, modified)) {
            mListAdapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    private void changeForThisBook(@NonNull final Publisher original,
                                   @NonNull final Publisher modified) {
        // treat the new data as a new Publisher; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Publisher. That's ok, it will get
        // garbage collected from the database sooner or later.
        //noinspection ConstantConditions
        if (mVm.changeForThisBook(getContext(), original, modified)) {
            mListAdapter.notifyDataSetChanged();
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

    /**
     * Edit a single Publisher from the book's publisher list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditPublisherForBookDialogFragment
            extends FFBaseDialogFragment {

        /** Fragment/Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "EditPublisherForBookDlg";
        private static final String BKEY_REQUEST_KEY = TAG + ":rk";

        /** FragmentResultListener request key to use for our response. */
        private String mRequestKey;

        @SuppressWarnings("FieldCanBeLocal")
        private EditBookFragmentViewModel mVm;

        /** Displayed for info only. */
        @Nullable
        private String mBookTitle;
        /** View Binding. */
        private DialogEditBookPublisherBinding mVb;

        /** The Publisher we're editing. */
        private Publisher mPublisher;

        /** Current edit. */
        private String mName;

        /**
         * No-arg constructor for OS use.
         */
        public EditPublisherForBookDialogFragment() {
            super(R.layout.dialog_edit_book_publisher);
        }

        /**
         * Launch the dialog.
         *
         * @param fm         The FragmentManager this fragment will be added to.
         * @param requestKey for use with the FragmentResultListener
         * @param bookTitle  displayed for info only
         * @param publisher  to edit
         */
        static void launch(@NonNull final FragmentManager fm,
                           @SuppressWarnings("SameParameterValue")
                           @NonNull final String requestKey,
                           @NonNull final String bookTitle,
                           @NonNull final Publisher publisher) {
            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(DBKeys.KEY_TITLE, bookTitle);
            args.putParcelable(DBKeys.KEY_FK_PUBLISHER, publisher);

            final DialogFragment frag = new EditPublisherForBookDialogFragment();
            frag.setArguments(args);
            frag.show(fm, TAG);
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                 "BKEY_REQUEST_KEY");
            mPublisher = Objects.requireNonNull(args.getParcelable(DBKeys.KEY_FK_PUBLISHER),
                                                "KEY_FK_PUBLISHER");

            mBookTitle = args.getString(DBKeys.KEY_TITLE);

            if (savedInstanceState == null) {
                mName = mPublisher.getName();
            } else {
                //noinspection ConstantConditions
                mName = savedInstanceState.getString(DBKeys.KEY_PUBLISHER_NAME);
            }
        }

        @Override
        public void onViewCreated(@NonNull final View view,
                                  @Nullable final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mVb = DialogEditBookPublisherBinding.bind(view);
            mVb.toolbar.setSubtitle(mBookTitle);

            //noinspection ConstantConditions
            mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

            //noinspection ConstantConditions
            final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    ExtArrayAdapter.FilterType.Diacritic, mVm.getAllPublisherNames());
            mVb.name.setText(mName);
            mVb.name.setAdapter(nameAdapter);
        }

        @Override
        protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
            if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        }

        private boolean saveChanges() {
            viewToModel();

            // basic check only, we're doing more extensive checks later on.
            if (mName.isEmpty()) {
                showError(mVb.lblName, R.string.vldt_non_blank_required);
                return false;
            }

            // Create a new Publisher as a holder for all changes.
            final Publisher tmpPublisher = Publisher.from(mName);

            EditBookBaseFragment.EditItemLauncher
                    .setResult(this, mRequestKey, mPublisher, tmpPublisher);
            return true;
        }

        private void viewToModel() {
            mName = mVb.name.getText().toString().trim();
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBKeys.KEY_PUBLISHER_NAME, mName);
        }

        @Override
        public void onPause() {
            viewToModel();
            super.onPause();
        }
    }

    private class PublisherListAdapter
            extends RecyclerViewAdapterBase<Publisher, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Publishers
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        PublisherListAdapter(@NonNull final Context context,
                             @NonNull final List<Publisher> items,
                             @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_publisher_list, parent, false);
            final Holder holder = new Holder(view);
            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> EditPublisherForBookDialogFragment
                    .launch(getChildFragmentManager(), RK_EDIT_PUBLISHER,
                            mVm.getBook().getTitle(),
                            getItem(holder.getBindingAdapterPosition())));
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

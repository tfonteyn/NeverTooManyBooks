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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogBookshelfFiltersBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

public class BookshelfFiltersDialogFragment
        extends FFBaseDialogFragment {

    public static final String TAG = "BookshelfFiltersDlg";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** The Bookshelf we're editing. */
    private Bookshelf mBookshelf;

    @SuppressWarnings("FieldCanBeLocal")
    private FilterListAdapter mListAdapter;
    private List<PFilter<?>> mList;

    /** View Binding. */
    @SuppressWarnings("FieldCanBeLocal")
    private DialogBookshelfFiltersBinding mVb;

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    private boolean mModified;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * No-arg constructor for OS use.
     */
    public BookshelfFiltersDialogFragment() {
        super(R.layout.dialog_bookshelf_filters);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             BKEY_REQUEST_KEY);
        mBookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                            DBKey.FK_BOOKSHELF);
        // ALL filters, active or not.
        mList = mBookshelf.getFilters();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogBookshelfFiltersBinding.bind(view);

        mVb.toolbar.setSubtitle(mBookshelf.getName());

        //noinspection ConstantConditions
        mListAdapter = new FilterListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.filterList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.filterList);
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.MENU_ACTION_CONFIRM) {
            final CharSequence[] items = Arrays.stream(FilterFactory.SUPPORTED_LABELS)
                                               .mapToObj(this::getString)
                                               .toArray(CharSequence[]::new);

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setSingleChoiceItems(items, -1, (dialog, which) -> {
                        final String name = FilterFactory.SUPPORTED_NAMES[which];
                        if (mList.stream().noneMatch(f -> f.getPrefName().equals(name))) {
                            final PFilter<?> filter = FilterFactory.create(name);
                            mList.add(filter);
                            mListAdapter.notifyItemInserted(mList.size());
                        }
                        dialog.dismiss();
                    })
                    .create()
                    .show();
            return true;
        }
        return false;
    }

    protected boolean saveChanges() {
        if (mModified) {
            //noinspection ConstantConditions
            ServiceLocator.getInstance().getBookshelfDao().update(getContext(), mBookshelf);
        }
        Launcher.setResult(this, mRequestKey, mModified);
        return true;
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private static final String BKEY_MODIFIED = TAG + ":m";

        private String mRequestKey;
        private FragmentManager mFragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              final boolean modified) {
            final Bundle result = new Bundle(1);
            result.putBoolean(BKEY_MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            mFragmentManager = fragmentManager;
            mRequestKey = requestKey;
            mFragmentManager.setFragmentResultListener(mRequestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         */
        public void launch(@NonNull final Bookshelf bookshelf) {

            final Bundle args = new Bundle(2);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

            final DialogFragment frag = new BookshelfFiltersDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getBoolean(BKEY_MODIFIED));
        }

        /**
         * Callback handler with the user's selection.
         */
        public abstract void onResult(final boolean modified);
    }

    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        final TextView mNameView;
        final TextView mValueView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            mNameView = itemView.findViewById(R.id.lbl_filter);
            mValueView = itemView.findViewById(R.id.filter);
        }
    }

    private static class FilterListAdapter
            extends RecyclerViewAdapterBase<PFilter<?>, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of items
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        FilterListAdapter(@NonNull final Context context,
                          @NonNull final List<PFilter<?>> items,
                          @Nullable final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_bookshelf_filter, parent, false);
            final Holder holder = new Holder(view);
            holder.mValueView.setOnClickListener(v -> {
                //URGENT: edit the filter value
                final PFilter<?> filter = getItem(holder.getBindingAdapterPosition());
            });
            return holder;

        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Context context = getContext();
            final PFilter<?> filter = getItem(position);

            holder.mNameView.setText(filter.getLabel(context));
            holder.mValueView.setText(filter.getValueText(context));
        }
    }
}

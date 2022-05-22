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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBooleanFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PEntityListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PStringEqualityFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogBookshelfFiltersBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBitmaskBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBooleanBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterEntityListBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterStringEqualityBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

public class BookshelfFiltersDialogFragment
        extends FFBaseDialogFragment {

    public static final String TAG = "BookshelfFiltersDlg";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private static final String[] Z_ARRAY_STRING = new String[0];

    /** The Bookshelf we're editing. */
    private Bookshelf bookshelf;

    private FilterListAdapter listAdapter;
    /** View Binding. */
    @SuppressWarnings("FieldCanBeLocal")
    private DialogBookshelfFiltersBinding vb;
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    private boolean modified;
    private final ModificationListener modificationListener = isModified -> modified = isModified;
    /** The list we're editing. */
    private List<PFilter<?>> filterList;

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
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                            BKEY_REQUEST_KEY);
        bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                           DBKey.FK_BOOKSHELF);
        filterList = bookshelf.getFilters();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogBookshelfFiltersBinding.bind(view);

        vb.toolbar.setSubtitle(bookshelf.getName());

        //noinspection ConstantConditions
        listAdapter = new FilterListAdapter(getContext(), filterList, modificationListener);
        vb.filterList.setAdapter(listAdapter);
        vb.filterList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (filterList.isEmpty()) {
            onAdd();
        }
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
            onAdd();
            return true;
        }
        return false;
    }

    // We don't set the modified flag on adding a filter - the filter is NOT activated yet here.
    private void onAdd() {

        // key: the label, sorted locale-alphabetically; value: the DBKey
        final SortedMap<String, String> map = new TreeMap<>();

        final Context context = getContext();

        //noinspection ConstantConditions
        FilterFactory.SUPPORTED
                .stream()
                .filter(GlobalFieldVisibility::isUsed)
                .forEach(key -> map.put(FieldVisibility.getLabel(context, key), key));


        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lbl_add_filter)
                .setSingleChoiceItems(map.keySet().toArray(Z_ARRAY_STRING), -1, (dialog, which) -> {
                    final String dbKey = map.values().toArray(Z_ARRAY_STRING)[which];
                    if (filterList.stream().noneMatch(f -> f.getDBKey().equals(dbKey))) {
                        FilterFactory.createFilter(dbKey).ifPresent(filter -> {
                            filterList.add(filter);
                            listAdapter.notifyItemInserted(filterList.size());
                        });
                    }

                    dialog.dismiss();
                })
                .create()
                .show();
    }

    protected boolean saveChanges() {
        if (modified) {
            bookshelf.setFilters(filterList);
            //noinspection ConstantConditions
            ServiceLocator.getInstance().getBookshelfDao().update(getContext(), bookshelf);
        }
        Launcher.setResult(this, requestKey, modified);
        return true;
    }

    @FunctionalInterface
    private interface ModificationListener {

        void setModified(boolean modified);
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

    private static class FilterListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<PFilter<?>> mItems;
        @NonNull
        private final ModificationListener mModificationListener;
        private final LayoutInflater mLayoutInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param items   List of items
         */
        FilterListAdapter(@NonNull final Context context,
                          @NonNull final List<PFilter<?>> items,
                          @NonNull final ModificationListener modificationListener) {
            mLayoutInflater = LayoutInflater.from(context);
            mItems = items;
            mModificationListener = modificationListener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = mLayoutInflater.inflate(viewType, parent, false);

            final Holder holder;
            if (viewType == PBooleanFilter.LAYOUT_ID) {
                holder = new BooleanHolder(view, mModificationListener);
            } else if (viewType == PStringEqualityFilter.LAYOUT_ID) {
                holder = new StringEqualityHolder(view, mModificationListener);
            } else if (viewType == PEntityListFilter.LAYOUT_ID) {
                holder = new EntityListHolder<>(view, mModificationListener);
            } else if (viewType == PBitmaskFilter.LAYOUT_ID) {
                holder = new BitmaskHolder(view, mModificationListener);
            } else {
                throw new IllegalArgumentException("Unknown viewType");
            }

            if (holder.mDelBtn != null) {
                holder.mDelBtn.setOnClickListener(v -> {
                    final int pos = holder.getBindingAdapterPosition();
                    mItems.remove(pos);
                    notifyItemRemoved(pos);
                    mModificationListener.setModified(true);
                });
            }
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            holder.onBind(holder.itemView.getContext(), mItems.get(position));
        }

        @Override
        public int getItemViewType(final int position) {
            return mItems.get(position).getPrefLayoutId();
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    private abstract static class Holder
            extends RecyclerView.ViewHolder {

        @Nullable
        final ImageButton mDelBtn;
        @NonNull
        final ModificationListener mModificationListener;

        Holder(@NonNull final View itemView,
               @NonNull final ModificationListener modificationListener) {
            super(itemView);
            mModificationListener = modificationListener;
            mDelBtn = itemView.findViewById(R.id.btn_del);
        }

        public abstract void onBind(@NonNull Context context,
                                    @NonNull PFilter<?> pFilter);
    }

    private static class BooleanHolder
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterBooleanBinding mVb;

        BooleanHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            mVb = RowEditBookshelfFilterBooleanBinding.bind(itemView);
        }

        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            final PBooleanFilter filter = (PBooleanFilter) pFilter;
            mVb.lblFilter.setText(filter.getLabel(context));

            mVb.valueTrue.setText(filter.getValueText(context, true));
            mVb.valueFalse.setText(filter.getValueText(context, false));

            mVb.filter.setOnCheckedChangeListener(null);
            final Boolean value = filter.getValue();
            if (value == null) {
                mVb.filter.clearCheck();
            } else {
                mVb.valueTrue.setChecked(value);
            }

            mVb.filter.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == -1) {
                    filter.setValue(null);
                } else {
                    filter.setValue(checkedId == mVb.valueTrue.getId());
                }
                mModificationListener.setModified(true);
            });
        }
    }

    private static class StringEqualityHolder
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterStringEqualityBinding mVb;

        StringEqualityHolder(@NonNull final View itemView,
                             @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            mVb = RowEditBookshelfFilterStringEqualityBinding.bind(itemView);
        }

        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            //noinspection TypeMayBeWeakened
            final PStringEqualityFilter filter = (PStringEqualityFilter) pFilter;
            mVb.lblFilter.setText(filter.getLabel(context));

            // We cannot share this adapter/formatter between multiple Holder instances
            // as they depends on the DBKey of the filter.
            @Nullable
            final FieldArrayAdapter fieldAdapter =
                    FilterFactory.createAdapter(context, filter);
            // likewise, always set the adapter even when null
            mVb.filter.setAdapter(fieldAdapter);

            @Nullable
            final FieldFormatter<String> fieldFormatter =
                    fieldAdapter != null ? fieldAdapter.getFormatter() : null;

            final String initialValue;
            if (fieldFormatter != null) {
                initialValue = fieldFormatter.format(context, filter.getValueText(context));
            } else {
                initialValue = filter.getValueText(context);
            }
            mVb.filter.setText(initialValue);

            mVb.filter.addTextChangedListener((ExtTextWatcher) s -> {
                if (fieldFormatter instanceof EditFieldFormatter) {
                    filter.setValue(((EditFieldFormatter<String>) fieldFormatter)
                                            .extract(context, s.toString()));
                } else {
                    filter.setValue(s.toString());
                }
                mModificationListener.setModified(true);
            });
        }
    }

    private static class EntityListHolder<T extends Entity>
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterEntityListBinding mVb;

        EntityListHolder(@NonNull final View itemView,
                         @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            mVb = RowEditBookshelfFilterEntityListBinding.bind(itemView);
        }

        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            //noinspection unchecked
            final PEntityListFilter<T> filter = (PEntityListFilter<T>) pFilter;
            mVb.lblFilter.setText(filter.getLabel(context));

            mVb.filter.setText(filter.getValueText(context));

            mVb.ROWONCLICKTARGET.setOnClickListener(v -> {
                final List<T> entities = filter.getEntities();
                final List<Long> ids = entities.stream()
                                               .map(Entity::getId)
                                               .collect(Collectors.toList());
                final List<String> labels = entities.stream()
                                                    .map(entity -> entity.getLabel(context))
                                                    .collect(Collectors.toList());

                new MultiChoiceAlertDialogBuilder<Long>(context)
                        .setTitle(context.getString(R.string.lbl_bookshelves))
                        .setItems(ids, labels)
                        .setSelectedItems(filter.getValue())
                        .setPositiveButton(android.R.string.ok, value -> {
                            filter.setValue(value);
                            mVb.filter.setText(filter.getValueText(context));
                            mModificationListener.setModified(true);
                        })
                        .create()
                        .show();
            });
        }
    }

    private static class BitmaskHolder
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterBitmaskBinding mVb;

        BitmaskHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            mVb = RowEditBookshelfFilterBitmaskBinding.bind(itemView);
        }

        @Override
        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            final PBitmaskFilter filter = (PBitmaskFilter) pFilter;
            mVb.lblFilter.setText(filter.getLabel(context));

            mVb.filter.setText(filter.getValueText(context));

            mVb.ROWONCLICKTARGET.setOnClickListener(v -> {
                final Map<Integer, Integer> bitsAndLabels = filter.getBitsAndLabels();
                final List<Integer> ids = new ArrayList<>(bitsAndLabels.keySet());
                final List<String> labels = bitsAndLabels.values()
                                                         .stream()
                                                         .map(context::getString)
                                                         .collect(Collectors.toList());

                new MultiChoiceAlertDialogBuilder<Integer>(context)
                        .setTitle(context.getString(R.string.lbl_edition))
                        .setItems(ids, labels)
                        .setSelectedItems(filter.getValue())
                        .setPositiveButton(android.R.string.ok, value -> {
                            filter.setValue(value);
                            mVb.filter.setText(filter.getValueText(context));
                            mModificationListener.setModified(true);
                        })
                        .create()
                        .show();
            });
        }
    }
}

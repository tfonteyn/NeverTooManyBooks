/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBooleanFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PEntityListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PStringEqualityFilter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfFiltersContentBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBitmaskBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBooleanBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterEntityListBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterStringEqualityBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class BookshelfFiltersDialogFragment
        extends FFBaseDialogFragment {

    public static final String TAG = "BookshelfFiltersDlg";

    private FilterListAdapter listAdapter;
    /** View Binding. */
    @SuppressWarnings("FieldCanBeLocal")
    private DialogEditBookshelfFiltersContentBinding vb;

    private BookshelfFiltersViewModel vm;


    private final ModificationListener modificationListener =
            isModified -> vm.setModified(isModified);


    /**
     * No-arg constructor for OS use.
     */
    public BookshelfFiltersDialogFragment() {
        super(R.layout.dialog_edit_bookshelf_filters,
              R.layout.dialog_edit_bookshelf_filters_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(BookshelfFiltersViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditBookshelfFiltersContentBinding.bind(view.findViewById(R.id.dialog_content));
        setSubtitle(vm.getBookshelf().getName());

        //noinspection ConstantConditions
        listAdapter = new FilterListAdapter(getContext(), vm.getFilterList(), modificationListener);
        vb.filterList.setAdapter(listAdapter);
        vb.filterList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (vm.getFilterList().isEmpty()) {
            onAdd();
        }
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_clear || id == R.id.btn_neutral) {
                vm.setModified(true);
                vm.getFilterList().clear();
                saveChanges();
                dismiss();
                return true;

            } else if (id == R.id.btn_add) {
                onAdd();
                return true;

            } else if (id == R.id.btn_select || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    // We don't set the modified flag on adding a filter - the filter is NOT activated yet here.
    private void onAdd() {
        final Context context = getContext();
        //noinspection ConstantConditions
        final Pair<String[], String[]> items = vm.getFilterChoiceItems(context);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lbl_add_filter)
                .setSingleChoiceItems(items.first, -1, (dialog, which) -> {
                    final String dbKey = items.second[which];
                    if (vm.getFilterList().stream().noneMatch(f -> f.getDBKey().equals(dbKey))) {
                        final PFilter<?> filter = FilterFactory.createFilter(dbKey);
                        if (filter != null) {
                            vm.getFilterList().add(filter);
                            listAdapter.notifyItemInserted(vm.getFilterList().size());
                        }
                    }

                    dialog.dismiss();
                })
                .create()
                .show();
    }

    protected boolean saveChanges() {
        //noinspection ConstantConditions
        final boolean success = vm.saveChanges(getContext());
        if (success) {
            Launcher.setResult(this, vm.getRequestKey(), vm.isModified());
        }
        return success;
    }

    @FunctionalInterface
    private interface ModificationListener {

        void setModified(boolean modified);
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private static final String BKEY_MODIFIED = TAG + ":m";

        @NonNull
        private final String requestKey;
        private FragmentManager fragmentManager;

        public Launcher(@NonNull final String requestKey) {
            this.requestKey = requestKey;
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              final boolean modified) {
            final Bundle result = new Bundle(1);
            result.putBoolean(BKEY_MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param bookshelf to edit
         */
        public void launch(@NonNull final Bookshelf bookshelf) {

            final Bundle args = new Bundle(2);
            args.putString(BookshelfFiltersViewModel.BKEY_REQUEST_KEY, requestKey);
            args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

            final DialogFragment frag = new BookshelfFiltersDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getBoolean(BKEY_MODIFIED));
        }

        /**
         * Callback handler.
         */
        public abstract void onResult(boolean modified);
    }

    private static class FilterListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<PFilter<?>> filters;
        @NonNull
        private final ModificationListener modificationListener;
        private final LayoutInflater layoutInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param filters List of items
         */
        FilterListAdapter(@NonNull final Context context,
                          @NonNull final List<PFilter<?>> filters,
                          @NonNull final ModificationListener modificationListener) {
            layoutInflater = LayoutInflater.from(context);
            this.filters = filters;
            this.modificationListener = modificationListener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = layoutInflater.inflate(viewType, parent, false);

            final Holder holder;
            if (viewType == PBooleanFilter.LAYOUT_ID) {
                holder = new BooleanHolder(view, modificationListener);
            } else if (viewType == PStringEqualityFilter.LAYOUT_ID) {
                holder = new StringEqualityHolder(view, modificationListener);
            } else if (viewType == PEntityListFilter.LAYOUT_ID) {
                holder = new EntityListHolder<>(view, modificationListener);
            } else if (viewType == PBitmaskFilter.LAYOUT_ID) {
                holder = new BitmaskHolder(view, modificationListener);
            } else {
                throw new IllegalArgumentException("Unknown viewType");
            }

            if (holder.delBtn != null) {
                holder.delBtn.setOnClickListener(v -> {
                    final int pos = holder.getBindingAdapterPosition();
                    filters.remove(pos);
                    notifyItemRemoved(pos);
                    modificationListener.setModified(true);
                });
            }
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final PFilter<?> pFilter = filters.get(position);
            //noinspection unchecked
            ((BindableViewHolder<PFilter<?>>) holder).onBind(pFilter);
        }

        @Override
        public int getItemViewType(final int position) {
            return filters.get(position).getPrefLayoutId();
        }

        @Override
        public int getItemCount() {
            return filters.size();
        }
    }

    private abstract static class Holder
            extends RecyclerView.ViewHolder {

        @Nullable
        final Button delBtn;
        @NonNull
        final ModificationListener modificationListener;

        Holder(@NonNull final View itemView,
               @NonNull final ModificationListener modificationListener) {
            super(itemView);
            this.modificationListener = modificationListener;
            delBtn = itemView.findViewById(R.id.btn_del);
        }
    }

    private static class BooleanHolder
            extends Holder
            implements BindableViewHolder<PBooleanFilter> {

        @NonNull
        private final RowEditBookshelfFilterBooleanBinding vb;

        BooleanHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterBooleanBinding.bind(itemView);
        }

        public void onBind(@NonNull final PBooleanFilter filter) {
            final Context context = itemView.getContext();
            vb.lblFilter.setText(filter.getLabel(context));
            vb.valueTrue.setText(filter.getValueText(context, true));
            vb.valueFalse.setText(filter.getValueText(context, false));

            vb.filter.setOnCheckedChangeListener(null);
            final Boolean value = filter.getValue();
            if (value == null) {
                vb.filter.clearCheck();
            } else {
                vb.valueTrue.setChecked(value);
                vb.valueFalse.setChecked(!value);
            }

            vb.filter.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == -1) {
                    filter.setValue(null);
                } else {
                    filter.setValue(checkedId == vb.valueTrue.getId());
                }
                modificationListener.setModified(true);
            });
        }
    }

    private static class StringEqualityHolder
            extends Holder
            implements BindableViewHolder<PStringEqualityFilter> {

        @NonNull
        private final RowEditBookshelfFilterStringEqualityBinding vb;

        StringEqualityHolder(@NonNull final View itemView,
                             @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterStringEqualityBinding.bind(itemView);
        }

        public void onBind(@NonNull final PStringEqualityFilter filter) {
            final Context context = itemView.getContext();
            vb.lblFilter.setText(filter.getLabel(context));
            vb.filter.setText(filter.getValueText(context));

            // We cannot share this adapter/formatter between multiple Holder instances
            // as they depends on the DBKey of the filter.
            @Nullable
            final ExtArrayAdapter<String> adapter = FilterFactory
                    .createAdapter(context, filter.getDBKey());
            // likewise, always set the adapter even when null
            vb.filter.setAdapter(adapter);
            vb.filter.addTextChangedListener((ExtTextWatcher) s -> {
                filter.setValueText(context, s.toString());
                modificationListener.setModified(true);
            });
        }
    }

    private static class EntityListHolder<T extends Entity>
            extends Holder
            implements BindableViewHolder<PEntityListFilter<T>> {

        @NonNull
        private final RowEditBookshelfFilterEntityListBinding vb;

        EntityListHolder(@NonNull final View itemView,
                         @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterEntityListBinding.bind(itemView);
        }

        public void onBind(@NonNull final PEntityListFilter<T> filter) {
            final Context context = itemView.getContext();
            vb.lblFilter.setText(filter.getLabel(context));
            vb.filter.setText(filter.getValueText(context));

            vb.ROWONCLICKTARGET.setOnClickListener(v -> {
                final List<T> entities = filter.getEntities();
                final List<Long> ids = entities.stream()
                                               .map(Entity::getId)
                                               .collect(Collectors.toList());
                final List<String> labels = entities.stream()
                                                    .map(entity -> entity.getLabel(context))
                                                    .collect(Collectors.toList());

                new MultiChoiceAlertDialogBuilder<Long>(context)
                        .setTitle(filter.getLabel(context))
                        .setItems(ids, labels)
                        .setSelectedItems(filter.getValue())
                        .setPositiveButton(android.R.string.ok, value -> {
                            filter.setValue(value);
                            vb.filter.setText(filter.getValueText(context));
                            modificationListener.setModified(true);
                        })
                        .create()
                        .show();
            });
        }
    }

    private static class BitmaskHolder
            extends Holder
            implements BindableViewHolder<PBitmaskFilter> {

        @NonNull
        private final RowEditBookshelfFilterBitmaskBinding vb;

        BitmaskHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterBitmaskBinding.bind(itemView);
        }

        @Override
        public void onBind(@NonNull final PBitmaskFilter filter) {
            final Context context = itemView.getContext();
            vb.lblFilter.setText(filter.getLabel(context));
            vb.filter.setText(filter.getValueText(context));

            vb.ROWONCLICKTARGET.setOnClickListener(v -> {
                final Map<Integer, String> bitsAndLabels = filter.getBitsAndLabels(context);
                final List<Integer> ids = new ArrayList<>(bitsAndLabels.keySet());
                final List<String> labels = new ArrayList<>(bitsAndLabels.values());

                new MultiChoiceAlertDialogBuilder<Integer>(context)
                        .setTitle(context.getString(R.string.lbl_edition))
                        .setItems(ids, labels)
                        .setSelectedItems(filter.getValue())
                        .setPositiveButton(android.R.string.ok, value -> {
                            filter.setValue(value);
                            vb.filter.setText(filter.getValueText(context));
                            modificationListener.setModified(true);
                        })
                        .create()
                        .show();
            });
        }
    }
}

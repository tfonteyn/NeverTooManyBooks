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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfFiltersBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBitmaskBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBooleanBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterEntityListBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterStringEqualityBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
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
    private DialogEditBookshelfFiltersBinding vb;
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
        super(R.layout.dialog_edit_bookshelf_filters);
        setFloatingDialogMarginBottom(0);
        setFloatingDialogHeight(R.dimen.floating_dialog_generic_height);
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

        vb = DialogEditBookshelfFiltersBinding.bind(view);

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

    @Nullable
    @Override
    protected Button mapButton(@NonNull final Button actionButton,
                               @NonNull final View buttonPanel) {
        if (actionButton.getId() == R.id.btn_select) {
            return buttonPanel.findViewById(R.id.btn_positive);

        } else if (actionButton.getId() == R.id.btn_clear) {
            return buttonPanel.findViewById(R.id.btn_neutral);
        }
        return null;
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        final int itemId = menuItem.getItemId();
        if (itemId == R.id.MENU_ACTION_CONFIRM && button != null) {
            if (button.getId() == R.id.btn_clear) {
                modified = true;
                filterList.clear();
                saveChanges();
                dismiss();
                return true;

            } else if (button.getId() == R.id.btn_add) {
                onAdd();
                return true;

            } else if (button.getId() == R.id.btn_select) {
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

        // key: the label, sorted locale-alphabetically; value: the DBKey
        final SortedMap<String, String> map = new TreeMap<>();

        final Context context = getContext();

        //noinspection ConstantConditions
        FilterFactory.SUPPORTED
                .entrySet()
                .stream()
                .filter(entry -> GlobalFieldVisibility.isUsed(entry.getKey()))
                .forEach(entry -> map.put(context.getString(entry.getValue()), entry.getKey()));

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lbl_add_filter)
                .setSingleChoiceItems(map.keySet().toArray(Z_ARRAY_STRING), -1, (dialog, which) -> {
                    final String dbKey = map.values().toArray(Z_ARRAY_STRING)[which];
                    if (filterList.stream().noneMatch(f -> f.getDBKey().equals(dbKey))) {
                        final PFilter<?> filter = FilterFactory.createFilter(dbKey);
                        if (filter != null) {
                            filterList.add(filter);
                            listAdapter.notifyItemInserted(filterList.size());
                        }
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

        private String requestKey;
        private FragmentManager fragmentManager;

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
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         */
        public void launch(@NonNull final Bookshelf bookshelf) {

            final Bundle args = new Bundle(2);
            args.putString(BKEY_REQUEST_KEY, requestKey);
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
         * Callback handler with the user's selection.
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
            holder.onBind(holder.itemView.getContext(), filters.get(position));
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
        final ImageButton delBtn;
        @NonNull
        final ModificationListener modificationListener;

        Holder(@NonNull final View itemView,
               @NonNull final ModificationListener modificationListener) {
            super(itemView);
            this.modificationListener = modificationListener;
            delBtn = itemView.findViewById(R.id.btn_del);
        }

        public abstract void onBind(@NonNull Context context,
                                    @NonNull PFilter<?> pFilter);
    }

    private static class BooleanHolder
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterBooleanBinding vb;

        BooleanHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterBooleanBinding.bind(itemView);
        }

        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            final PBooleanFilter filter = (PBooleanFilter) pFilter;
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
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterStringEqualityBinding vb;

        StringEqualityHolder(@NonNull final View itemView,
                             @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterStringEqualityBinding.bind(itemView);
        }

        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            final PStringEqualityFilter filter = (PStringEqualityFilter) pFilter;
            vb.lblFilter.setText(filter.getLabel(context));

            // We cannot share this adapter/formatter between multiple Holder instances
            // as they depends on the DBKey of the filter.
            @Nullable
            final ExtArrayAdapter<String> adapter = FilterFactory
                    .createAdapter(context, filter.getDBKey());
            // likewise, always set the adapter even when null
            vb.filter.setAdapter(adapter);

            vb.filter.setText(filter.getValueText(context));
            vb.filter.addTextChangedListener((ExtTextWatcher) s -> {
                filter.setValueText(context, s.toString());
                modificationListener.setModified(true);
            });
        }
    }

    private static class EntityListHolder<T extends Entity>
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterEntityListBinding vb;

        EntityListHolder(@NonNull final View itemView,
                         @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterEntityListBinding.bind(itemView);
        }

        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            //noinspection unchecked
            final PEntityListFilter<T> filter = (PEntityListFilter<T>) pFilter;
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
            extends Holder {

        @NonNull
        private final RowEditBookshelfFilterBitmaskBinding vb;

        BitmaskHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener modificationListener) {
            super(itemView, modificationListener);
            vb = RowEditBookshelfFilterBitmaskBinding.bind(itemView);
        }

        @Override
        public void onBind(@NonNull final Context context,
                           @NonNull final PFilter<?> pFilter) {
            final PBitmaskFilter filter = (PBitmaskFilter) pFilter;
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

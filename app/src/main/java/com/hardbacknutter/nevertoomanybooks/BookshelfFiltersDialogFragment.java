/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.ui.ModificationListener;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.ui.PFilterListAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfFiltersContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

/**
 * TODO: unify with {@link BookshelfFiltersBottomSheet}.
 */
public class BookshelfFiltersDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "BookshelfFiltersDlg";

    private PFilterListAdapter adapter;
    /** View Binding. */
    @SuppressWarnings("FieldCanBeLocal")
    private DialogEditBookshelfFiltersContentBinding vb;

    private BookshelfFiltersViewModel vm;

    private final ModificationListener modificationListener =
            new ModificationListener() {
                @Override
                public void onModified(final int pos) {
                    adapter.notifyItemChanged(pos);
                    vm.setModified(true);
                }

                @Override
                public void onDelete(final int pos) {
                    vm.getFilterList().remove(pos);
                    adapter.notifyItemRemoved(pos);
                    vm.setModified(true);
                }
            };

    /**
     * No-arg constructor for OS use.
     */
    public BookshelfFiltersDialogFragment() {
        super(R.layout.dialog_edit_bookshelf_filters,
              R.layout.dialog_edit_bookshelf_filters_content,
              // Fullscreen on Medium screens
              // to avoid 3 buttons overlapping text on a UI in e.g. german
              EnumSet.of(WindowSizeClass.Medium),
              EnumSet.of(WindowSizeClass.Medium));
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

        //noinspection DataFlowIssue
        adapter = new PFilterListAdapter(getContext(), vm.getFilterList(), modificationListener);
        vb.filterList.setAdapter(adapter);
        vb.filterList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));

        adjustWindowSize(vb.filterList, 0.33f);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (vm.getFilterList().isEmpty()) {
            onAdd();
        }
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_clear || id == R.id.btn_neutral) {
                vm.setModified(true);
                vm.getFilterList().clear();
                if (saveChanges()) {
                    dismiss();
                }
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

    private void onAdd() {
        final Context context = getContext();
        //noinspection DataFlowIssue
        final Pair<String[], String[]> items = vm.getFilterChoiceItems(context);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lbl_add_filter)
                .setSingleChoiceItems(items.first, -1, (dialog, which) -> {
                    final String dbKey = items.second[which];
                    if (vm.getFilterList().stream().noneMatch(f -> f.getDBKey().equals(dbKey))) {
                        final PFilter<?> filter = FilterFactory.createFilter(dbKey);
                        if (filter != null) {
                            vm.getFilterList().add(filter);
                            // We don't set the modified flag on adding a filter.
                            // The filter is NOT yet activated.
                            adapter.notifyItemInserted(vm.getFilterList().size());
                        }
                    }

                    dialog.dismiss();
                })
                .create()
                .show();
    }

    private boolean saveChanges() {
        //noinspection DataFlowIssue
        if (vm.saveChanges(getContext())) {
            BookshelfFiltersLauncher.setResult(this, vm.getRequestKey(), vm.isModified());
            return true;
        }
        return false;
    }
}

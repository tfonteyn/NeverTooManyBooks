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
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.ui.ModificationListener;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.ui.PFilterListAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfFiltersContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.ToolbarWithActionButtons;

class BookshelfFiltersDelegate
        implements FlexDialogDelegate<DialogEditBookshelfFiltersContentBinding> {

    @NonNull
    private final DialogFragment owner;

    private final BookshelfFiltersViewModel vm;

    private PFilterListAdapter adapter;

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

    /** View Binding. */
    private DialogEditBookshelfFiltersContentBinding vb;

    BookshelfFiltersDelegate(@NonNull final DialogFragment owner,
                             @NonNull final Bundle args) {
        this.owner = owner;
        vm = new ViewModelProvider(owner).get(BookshelfFiltersViewModel.class);
        vm.init(args);
    }

    @Override
    public void initToolbarActionButtons(@NonNull final Toolbar dialogToolbar,
                                         final int menuResId,
                                         @NonNull final ToolbarWithActionButtons listener) {
        FlexDialogDelegate.super.initToolbarActionButtons(dialogToolbar, menuResId, listener);
        dialogToolbar.setSubtitle(vm.getBookshelf().getName());
    }

    @Override
    public void onViewCreated(@NonNull final DialogEditBookshelfFiltersContentBinding vb) {
        this.vb = vb;
        final Context context = vb.getRoot().getContext();

        adapter = new PFilterListAdapter(context, vm.getFilterList(), modificationListener);
        vb.filterList.setAdapter(adapter);
        vb.filterList.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));
    }

    @Override
    public void onStart() {
        if (vm.getFilterList().isEmpty()) {
            onAdd();
        }
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_clear || id == R.id.btn_neutral) {
                vm.setModified(true);
                vm.getFilterList().clear();
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;

            } else if (id == R.id.btn_add) {
                onAdd();
                return true;

            } else if (id == R.id.btn_select || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private void onAdd() {
        final Context context = vb.getRoot().getContext();
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
        if (vm.saveChanges(vb.getRoot().getContext())) {
            BookshelfFiltersLauncher.setResult(owner, vm.getRequestKey(), vm.isModified());
            return true;
        }
        return false;
    }
}

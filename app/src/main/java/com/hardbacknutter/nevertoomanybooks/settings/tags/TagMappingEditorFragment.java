/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditTagMappingsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagMappingBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTagMappingBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTagMappingDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

public class TagMappingEditorFragment
        extends BaseFragment {

    private static final String TAG = "TagMappingEditorFrag";
    private static final String RK_MENU = TAG + ":rk:menu";
    private static final String RK_MAPPING = TAG + ":rk:mapping";

    private FragmentEditTagMappingsBinding vb;
    private List<TagMapping> mappings;
    private TagAdapter adapter;
    private EditParcelableLauncher<TagMapping> editLauncher;
    private ExtMenuLauncher menuLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditParcelableLauncher<>(
                RK_MAPPING,
                EditTagMappingDialogFragment::new,
                EditTagMappingBottomSheet::new);
        editLauncher.setOnEditInPlaceListener(this::onEntryUpdated);
        editLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditTagMappingsBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.apply(vb.tagList);

        final Context context = getContext();

        mappings = ServiceLocator.getInstance().getTagMappingDao().getAll();
        //noinspection DataFlowIssue
        adapter = new TagAdapter(context, mappings);

        adapter.setOnRowClickListener((v, position) -> editEntry(mappings.get(position)));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (v, position) -> {
                    final Menu menu = MenuUtils.createEditDeleteContextMenu(v.getContext());
                    //noinspection DataFlowIssue
                    final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(v.getContext())
                                .setListener(this::onMenuItemSelected)
                                .setMenuOwner(position)
                                .setMenu(menu, true)
                                .show(v, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), null, null, position, menu, true);
                    }
                });

        vb.tagList.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        initFab();
    }

    private void initFab() {
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.add_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editEntry(new TagMapping("", Set.of())));
    }

    /**
     * Menu selection listener.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(final int position,
                                       @IdRes final int menuItemId) {

        if (menuItemId == R.id.MENU_EDIT) {
            editEntry(mappings.get(position));
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            deleteEntry(position);
            return true;
        }
        return false;
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param mapping to edit
     */
    private void editEntry(@NonNull final TagMapping mapping) {
        //noinspection DataFlowIssue
        editLauncher.editInPlace(getActivity(), mapping);
    }

    private void onEntryUpdated(@NonNull final TagMapping tagMapping) {
        try {
            if (tagMapping.getId() == 0) {
                // check by NAME it's not already in the list.
                int position = mappings.stream().map(TagMapping::getName)
                                       .collect(Collectors.toList())
                                       .indexOf(tagMapping.getName());
                if (position >= 0) {
                    Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                                  Snackbar.LENGTH_LONG).show();
                    vb.tagList.scrollToPosition(position);
                } else {
                    // It's a new entry, add it
                    ServiceLocator.getInstance().getTagMappingDao().insert(tagMapping);

                    // find insertion point using a brute-force sequential search...
                    position = 0;
                    while (position < mappings.size()
                           && mappings.get(position).compareTo(tagMapping) < 0) {
                        position++;
                    }
                    mappings.add(position, tagMapping);
                    adapter.notifyItemInserted(position);
                    vb.tagList.scrollToPosition(position);
                }
            } else {
                // It's an existing entry in the list, find it and update with the new data
                final int position = mappings.stream().map(TagMapping::getId).collect(
                        Collectors.toList()).indexOf(tagMapping.getId());

                final TagMapping existing = mappings.get(position);
                existing.copyFrom(tagMapping);
                ServiceLocator.getInstance().getTagMappingDao().update(existing);

                adapter.notifyItemChanged(position);
                vb.tagList.scrollToPosition(position);
            }

        } catch (@NonNull final DaoInsertException | DaoUpdateException e) {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final TagMapping tagMapping = mappings.get(position);
        // paranoia
        if (tagMapping.getId() == 0) {
            // We should never get here.. all tags should have an id
            mappings.remove(position);
            adapter.notifyItemRemoved(position);
            return;
        }
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.action_delete)
                .setMessage(getString(R.string.confirm_delete_substitutions, tagMapping.getName()))
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.ok, (d, w) -> {
                    mappings.remove(position);
                    adapter.notifyItemRemoved(position);
                })
                .create()
                .show();
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends RowViewHolder {

        @NonNull
        private final RowEditTagMappingBinding vb;

        Holder(@NonNull final RowEditTagMappingBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@NonNull final TagMapping mapping) {
            vb.name.setText(mapping.getName());
            vb.mapping.setText(mapping.getMappings().stream().sorted().collect(
                    Collectors.joining("; ")));
        }
    }

    private static class TagAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<TagMapping> items;
        @NonNull
        private final LayoutInflater inflater;

        @Nullable
        private OnRowClickListener rowClickListener;
        @Nullable
        private OnRowClickListener rowShowMenuListener;
        @Nullable
        private ExtMenuButton contextMenuMode;

        TagAdapter(@NonNull final Context context,
                   @NonNull final List<TagMapping> items) {
            inflater = LayoutInflater.from(context);
            this.items = items;
        }


        /**
         * Set the {@link OnRowClickListener} for a click on a row.
         * This listener will be propagated to all row ViewHolders.
         *
         * @param listener to set
         */
        void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
            this.rowClickListener = listener;
        }

        /**
         * Set the {@link OnRowClickListener} for showing the context menu on a row.
         * This listener will be propagated to all row ViewHolders.
         *
         * @param contextMenuMode how to show context menus
         * @param listener        to receive clicks
         */
        void setOnRowShowMenuListener(@NonNull final ExtMenuButton contextMenuMode,
                                      @Nullable final OnRowClickListener listener) {
            this.rowShowMenuListener = listener;
            this.contextMenuMode = contextMenuMode;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditTagMappingBinding vb =
                    RowEditTagMappingBinding.inflate(inflater, parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            holder.onBind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}

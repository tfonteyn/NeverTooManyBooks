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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Objects;
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
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping.EditTagMappingLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * This editor allows CRUD actions on {@link TagMapping}s.
 * Editing/creating uses an {@link EditParcelableLauncher}.
 * Dao interaction is handled in
 * {@code com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTagMappingDelegate}.
 * <p>
 * This fragment is hosted in the ViewPager2 of {@link TagAdminFragment}.
 */
public class TagMappingEditorFragment
        extends BaseFragment {

    private static final String TAG = "TagMappingEditorFrag";
    private static final String RK_MENU = TAG + ":rk:menu";
    private static final String RK_TAG = TAG + ":rk:tag";
    private static final String BKEY_POSITION = TAG + ":pos";
    private static final int POS_NEW_ENTRY = -1;

    private FragmentEditTagMappingsBinding vb;
    private List<TagMapping> mappings;
    private TagAdapter adapter;
    private ExtMenuLauncher menuLauncher;
    private EditTagMappingLauncher editLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditTagMappingLauncher(RK_TAG);
        editLauncher.setResultListener(this::onEditEntryDone);
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

        adapter.setOnRowClickListener((v, position) -> editEntry(mappings.get(position), position));
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
        fab.setOnClickListener(v -> editEntry(new TagMapping("", Set.of()),
                                              POS_NEW_ENTRY));
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
            editEntry(mappings.get(position), position);
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
     * @param mapping  to edit
     * @param position the position of the item; use {@link #POS_NEW_ENTRY} for a new entry.
     */
    private void editEntry(@NonNull final TagMapping mapping,
                           final int position) {
        final Bundle extras = new Bundle();
        extras.putInt(BKEY_POSITION, position);

        //noinspection DataFlowIssue
        editLauncher.launch(getActivity(), mapping, extras);
    }

    private void onEditEntryDone(@NonNull final TagMapping original,
                                 @NonNull final TagMapping edit,
                                 @Nullable final Bundle extras) {

        // anything actually changed ? If not, we're done.
        if (edit.equals(original)) {
            return;
        }

        try {
            Objects.requireNonNull(extras);
            final int originalPos = extras.getInt(BKEY_POSITION);

            if (originalPos == POS_NEW_ENTRY) {
                // User was adding a new mapping
                addEntry(edit);
            } else {
                // User was editing an existing mapping
                updateEntry(original, originalPos, edit);
            }
        } catch (@NonNull final DaoInsertException | DaoUpdateException e) {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    private int findByName(@NonNull final String name) {
        return mappings.stream().map(TagMapping::getName)
                       .collect(Collectors.toList())
                       .indexOf(name);
    }

    private void addEntry(@NonNull final TagMapping edit)
            throws DaoInsertException {

        // check by NAME it's not already in the list.
        final int existingPos = findByName(edit.getName());

        if (existingPos >= 0) {
            // Trying to add a NEW one already there.
            // TODO: propose merge/overwrite
            // For now, reject it!
            Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            vb.tagList.scrollToPosition(existingPos);
        } else {
            // It's a new entry, add it
            ServiceLocator.getInstance().getTagMappingDao().insert(edit);

            // find insertion point using a brute-force sequential search...
            int position = 0;
            while (position < mappings.size()
                   && mappings.get(position).compareTo(edit) < 0) {
                position++;
            }
            mappings.add(position, edit);
            adapter.notifyItemInserted(position);
            vb.tagList.scrollToPosition(position);
        }
    }

    private void updateEntry(@NonNull final TagMapping original,
                             final int originalPos,
                             @NonNull final TagMapping edit)
            throws DaoUpdateException {

        // check by NAME it's not already in the list.
        final int existingPos = findByName(edit.getName());

        // == when the name was NOT modified and we found ourselves.
        // -1 when the name WAS modified and there is no other match
        if (existingPos == originalPos || existingPos == -1) {
            //  Update with the new data.
            original.copyFrom(edit);
            ServiceLocator.getInstance().getTagMappingDao().update(original);
            adapter.notifyItemChanged(originalPos);
            vb.tagList.scrollToPosition(originalPos);

        } else {
            // We found another entry with the same external tag-name.
            // TODO: propose overwrite/merge
            // For now, reject the edit!
            Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            vb.tagList.scrollToPosition(existingPos);
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
        StandardDialogs.deleteTagMapping(getContext(), tagMapping, () -> {
            mappings.remove(position);
            adapter.notifyItemRemoved(position);
        });
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

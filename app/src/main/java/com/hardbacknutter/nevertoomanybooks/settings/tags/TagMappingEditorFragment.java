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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditTagMappingsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagMappingBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping.EditTagMappingLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
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

    private TagAdminViewModel vm;
    @Nullable
    private ProgressDelegate progressDelegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(TagAdminViewModel.class);

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

        getToolbar().addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner(),
                                     Lifecycle.State.RESUMED);

        vm.onTagMapperFinished().observe(getViewLifecycleOwner(), this::onMappingFinished);
        vm.onTagMapperCancelled().observe(getViewLifecycleOwner(), this::onMappingCancelled);
        vm.onTagMapperFailure().observe(getViewLifecycleOwner(), this::onMappingFailure);

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
     * Called from {@link TagAdminFragment}.
     *
     * @param tagName for we want to create a new mapping (or edit existing).
     */
    void editOrCreateMapping(@NonNull final String tagName) {
        for (int i = 0; i < mappings.size(); i++) {
            if (mappings.get(i).getName().equalsIgnoreCase(tagName)) {
                editEntry(mappings.get(i), i);
                return;
            }
        }
        editEntry(new TagMapping(tagName, Set.of()), POS_NEW_ENTRY);
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
            final int position = extras.getInt(BKEY_POSITION);

            if (position == POS_NEW_ENTRY) {
                // User was adding a new mapping
                addEntry(edit);
            } else {
                // User was editing an existing mapping
                updateEntry(original, position, edit);
            }
        } catch (@NonNull final DaoWriteException e) {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    /**
     * Case-sensitive.
     *
     * @param mappingName to find
     *
     * @return position, or {@code -1} if not found
     */
    @IntRange(from = -1)
    private int findByName(@NonNull final String mappingName) {
        return mappings.stream().map(TagMapping::getName)
                       .collect(Collectors.toList())
                       .indexOf(mappingName);
    }

    private void addEntry(@NonNull final TagMapping tagMapping)
            throws DaoWriteException {

        // check by NAME it's not already in the list.
        final int existingPos = findByName(tagMapping.getName());

        if (existingPos >= 0) {
            // Trying to add a NEW one already there.
            // TODO: propose merge/overwrite
            // For now, reject it!
            Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            vb.tagList.scrollToPosition(existingPos);
        } else {
            // It's a new entry, add it
            ServiceLocator.getInstance().getTagMappingDao().insert(tagMapping);

            // find insertion point using a brute-force sequential search...
            int position = 0;
            while (position < mappings.size()
                   && mappings.get(position).compareTo(tagMapping) < 0) {
                position++;
            }
            mappings.add(position, tagMapping);
            adapter.notifyItemInserted(position);
            vb.tagList.scrollToPosition(position);
        }
    }

    private void updateEntry(@NonNull final TagMapping original,
                             final int position,
                             @NonNull final TagMapping tagMapping)
            throws DaoWriteException {

        // check by NAME it's not already in the list.
        final int existingPos = findByName(tagMapping.getName());

        // == when the name was NOT modified and we found ourselves.
        // -1 when the name WAS modified and there is no other match
        if (existingPos == position || existingPos == -1) {
            //  Update with the new data.
            original.copyFrom(tagMapping);

            ServiceLocator.getInstance().getTagMappingDao().update(original);
            adapter.notifyItemChanged(position);
            vb.tagList.scrollToPosition(position);

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

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void startTagMapper(@NonNull final Set<TagMapperTask.Options> options) {
        vm.startTagMapper(options);
        // The task is typically very fast, and will not report progress
        // This indeterminate-progress dialog might just flash up/away...
        //noinspection DataFlowIssue
        progressDelegate = new ProgressDelegate(getProgressFrame())
                .setTitle(R.string.lbl_substitutions)
                .setPreventSleep(true)
                .setIndeterminate(true)
                .setOnCancelListener(v -> vm.cancelTagMapper())
                .show(() -> getActivity().getWindow());
    }

    private void onMappingFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();
        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e, getString(R.string.lbl_substitutions),
                             (d, w) -> getActivity().finish());
        });
    }

    private void onMappingCancelled(
            @NonNull final LiveDataEvent<Map<TagMapperTask.Options, Integer>> message) {
        closeProgressDialog();
        message.process(optionsCount -> reportMappingsDone(R.string.cancelled, optionsCount));
    }

    private void onMappingFinished(
            @NonNull final LiveDataEvent<Map<TagMapperTask.Options, Integer>> message) {
        closeProgressDialog();
        message.process(optionsCount -> reportMappingsDone(R.string.action_done, optionsCount));
    }

    // as so often.. the reporting code is longer than the action code... is this really needed?
    private void reportMappingsDone(@StringRes final int titleId,
                                    @NonNull final Map<TagMapperTask.Options, Integer>
                                            optionsCount) {

        final int changes = optionsCount.values().stream().reduce(Integer::sum).orElse(0);
        if (changes > 0) {
            // - Flag up that returning to BoB will require a list rebuild
            // - lets the sibling fragment (tag-list) know it needs to rebuild as well
            vm.setModified();

            final StringJoiner sj = new StringJoiner("\n");

            if (optionsCount.containsKey(TagMapperTask.Options.ApplyMappings)) {
                //noinspection DataFlowIssue
                final int count = optionsCount.get(TagMapperTask.Options.ApplyMappings);
                if (count > 0) {
                    final String msg = getString(R.string.list_element, getString(
                            R.string.name_colon_value,
                            getString(R.string.lbl_tag_mapper_apply_mappings_result),
                            getResources().getQuantityString(R.plurals.n_books, count, count)));
                    sj.add(msg);
                }
            }
            if (optionsCount.containsKey(TagMapperTask.Options.MergeCaseDifferences)) {
                //noinspection DataFlowIssue
                final int count = optionsCount.get(TagMapperTask.Options.MergeCaseDifferences);
                if (count > 0) {
                    final String msg = getString(R.string.list_element, getString(
                            R.string.name_colon_value,
                            getString(R.string.lbl_tag_mapper_apply_merge_case_result),
                            getResources().getQuantityString(R.plurals.n_books, count, count)));
                    sj.add(msg);
                }
            }
            if (optionsCount.containsKey(TagMapperTask.Options.PurgeUnusedTags)) {
                //noinspection DataFlowIssue
                final int count = optionsCount.get(TagMapperTask.Options.PurgeUnusedTags);
                if (count > 0) {
                    final String msg = getString(R.string.list_element, getString(
                            R.string.name_colon_value,
                            getString(R.string.lbl_tag_mapper_apply_purge_unused),
                            getResources().getQuantityString(R.plurals.n_tags, count, count)));
                    sj.add(msg);
                }
            }

            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.info_24px)
                    .setTitle(titleId)
                    .setMessage(sj.toString())
                    .setPositiveButton(R.string.action_done, (d, w) -> d.dismiss())
                    .create()
                    .show();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.info_nothing_to_do, Snackbar.LENGTH_LONG).show();
        }
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
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

    private final class ToolbarMenuProvider
            implements MenuProvider {
        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_APPLY, Menu.NONE, R.string.action_apply)
                .setIcon(R.drawable.find_replace_24px)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_APPLY) {
                final Context context = getContext();

                //noinspection DataFlowIssue
                new MultiChoiceAlertDialogBuilder<TagMapperTask.Options>(getContext())
                        .setTitle(R.string.lbl_substitutions)
                        .setMessage(R.string.confirm_apply_substitutions)
                        .setSelectedItems(Set.of(TagMapperTask.Options.ApplyMappings,
                                                 TagMapperTask.Options.MergeCaseDifferences))
                        .setItems(List.of(TagMapperTask.Options.ApplyMappings,
                                          TagMapperTask.Options.MergeCaseDifferences,
                                          TagMapperTask.Options.PurgeUnusedTags),
                                  List.of(context.getString(
                                                  R.string.lbl_tag_mapper_apply_mappings),
                                          context.getString(
                                                  R.string.lbl_tag_mapper_apply_merge_case),
                                          context.getString(
                                                  R.string.lbl_tag_mapper_apply_purge_unused)))
                        .setPositiveButton(R.string.ok,
                                           TagMappingEditorFragment.this::startTagMapper)
                        .build()
                        .show();
                return true;
            }
            return false;
        }
    }
}

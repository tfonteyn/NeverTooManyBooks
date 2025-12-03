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
import androidx.recyclerview.widget.GridLayoutManager;
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
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditTagMappingsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagMappingBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping.EditTagMappingLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * This editor allows CRUD actions on {@link TagMapping}s.
 * Editing/creating uses an {@link EditParcelableLauncher}.
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
    private TagAdapter adapter;
    private ExtMenuLauncher menuLauncher;
    private EditTagMappingLauncher editLauncher;
    private TagAdminViewModel vm;
    private final PositionHandler positionHandler = new PositionHandler() {

        @Override
        public void onEdit(final int position) {
            editEntry(vm.getTagMappings().get(position), position);
        }

        @Override
        public void onShowContextMenu(@NonNull final View v,
                                      final int position) {
            showContextMenu(v, position);
        }
    };
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

        final GridLayoutManager layoutManager = (GridLayoutManager) vb.tagList.getLayoutManager();
        //noinspection DataFlowIssue
        adapter = new TagAdapter(context, layoutManager.getSpanCount(), vm.getTagMappings(),
                                 positionHandler);

        final GridDividerItemDecoration decoration =
                new GridDividerItemDecoration(context, false, true);
        vb.tagList.addItemDecoration(decoration);

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
     * @param tag for we want to create a new mapping (or edit existing).
     */
    void editOrCreateMapping(@NonNull final Tag tag) {
        final String tagName = tag.getName();

        final int position = vm.findTagMappingPosition(tagName);
        final TagMapping tagMapping;
        if (position >= 0) {
            tagMapping = vm.getTagMappings().get(position);
        } else {
            tagMapping = new TagMapping(tagName, Set.of());
        }

        editEntry(tagMapping, position);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void showContextMenu(@NonNull final View anchor,
                                 final int position) {
        final Context context = anchor.getContext();
        final Menu menu = MenuUtils.createEditDeleteContextMenu(context);
        //noinspection DataFlowIssue
        final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
        if (menuMode.isPopup()) {
            new ExtMenuPopupWindow(context)
                    .setListener(this::onMenuItemSelected)
                    .setMenuOwner(position)
                    .setMenu(menu, true)
                    .show(anchor, menuMode);
        } else {
            menuLauncher.launch(getActivity(), null, null, position, menu, true);
        }
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
            editEntry(vm.getTagMappings().get(position), position);
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

    private void addEntry(@NonNull final TagMapping tagMapping)
            throws DaoWriteException {

        // check by NAME it's not already in the list.
        final int existingPos = vm.findTagMappingPosition(tagMapping.getTagName());

        if (existingPos >= 0) {
            // Trying to add a NEW one already there.
            // TODO: propose merge/overwrite
            // For now, reject it!
            Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            vb.tagList.scrollToPosition(existingPos);
        } else {
            // It's a new entry, add it
            final int position = vm.insert(tagMapping);
            adapter.notifyItemInserted(position);
            vb.tagList.scrollToPosition(position);
        }
    }

    private void updateEntry(@NonNull final TagMapping original,
                             final int position,
                             @NonNull final TagMapping tagMapping)
            throws DaoWriteException {

        // check by NAME it's not already in the list.
        final int existingPos = vm.findTagMappingPosition(tagMapping.getTagName());

        // == when the name was NOT modified and we found ourselves.
        // -1 when the name WAS modified and there is no other match
        if (existingPos == position || existingPos == -1) {
            //  Update with the new data.
            original.copyFrom(tagMapping);

            vm.update(original);
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
        final TagMapping tagMapping = vm.getTagMappings().get(position);
        //noinspection DataFlowIssue
        StandardDialogs.deleteTagMapping(getContext(), tagMapping, () -> {
            vm.deleteTagMapping(position);
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
     * Proxy between adapter and Fragment/ViewModel.
     */
    private interface PositionHandler {
        /**
         * Edit the given position.
         *
         * @param position the position (index) in the list of items.
         */
        void onEdit(int position);

        /**
         * Show the menu.
         *
         * @param anchor   view
         * @param position the position (index) in the list of items.
         */
        void onShowContextMenu(@NonNull View anchor,
                               int position);
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

        void onBind(@Nullable final TagMapping mapping) {
            if (mapping == null) {
                vb.getRoot().setVisibility(View.INVISIBLE);
            } else {
                vb.getRoot().setVisibility(View.VISIBLE);

                vb.name.setText(mapping.getTagName());
                final Set<String> r = mapping.getMappings();
                final String text;
                if (r.isEmpty()) {
                    final Context context = itemView.getContext();
                    text = context.getString(R.string.brackets,
                                             context.getString(R.string.action_delete));
                } else {
                    text = r.stream().sorted().collect(Collectors.joining("; "));
                }
                vb.mapping.setText(text);
            }
        }
    }

    private static class TagAdapter
            extends MultiColumnRecyclerViewAdapter<Holder> {

        @NonNull
        private final List<TagMapping> items;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context         Current context
         * @param columnCount     from the grid layout
         * @param items           to display
         * @param positionHandler Proxy between adapter and ViewModel.
         */
        TagAdapter(@NonNull final Context context,
                   @IntRange(from = 1) final int columnCount,
                   @NonNull final List<TagMapping> items,
                   @NonNull final PositionHandler positionHandler) {
            super(context, columnCount);
            this.items = items;
            this.positionHandler = positionHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditTagMappingBinding vb =
                    RowEditTagMappingBinding.inflate(getInflater(), parent, false);
            adjustColumns(vb.getRoot());
            final Holder holder = new Holder(vb);

            // click -> edit
            holder.setOnRowClickListener((v, gridPosition) -> {
                final int listIndex = gridToListPosition(gridPosition);
                requireValidOrThrow(listIndex, gridPosition);
                positionHandler.onEdit(listIndex);
            });

            // long-click -> context menu
            holder.setOnRowLongClickListener(
                    ExtMenuButton.getPreferredMode(parent.getContext()), (v, gridPosition) -> {
                        final int listIndex = gridToListPosition(gridPosition);
                        requireValidOrThrow(listIndex, gridPosition);
                        positionHandler.onShowContextMenu(v, listIndex);
                    });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int gridPosition) {
            final int listIndex = gridToListPosition(gridPosition);
            if (listIndex == RecyclerView.NO_POSITION) {
                holder.onBind(null);
            } else {
                holder.onBind(items.get(listIndex));
            }
        }

        @Override
        protected int getListSize() {
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

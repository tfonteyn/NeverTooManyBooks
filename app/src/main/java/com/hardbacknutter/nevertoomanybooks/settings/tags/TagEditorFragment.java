/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditTagNamesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagNameBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.editstring.EditStringLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * This editor allows CRUD actions on {@link Tag}s.
 * Editing/creating uses the in-memory {@link EditStringLauncher}.
 * {@link TagDao} interaction is local in this class.
 * <p>
 * This fragment is hosted in the ViewPager2 of {@link TagAdminFragment}.
 * <p>
 * IMPORTANT: EditTagDelegate and TagEditorFragment have partially overlapping
 * functionality which should kept in sync.
 */
public class TagEditorFragment
        extends BaseFragment {

    private static final String TAG = "TagEditorFragment";
    private static final String RK_MENU = TAG + ":rk:menu";
    private static final String RK_TAG = TAG + ":rk:tag";
    private static final String BKEY_POSITION = TAG + ":pos";
    private static final int POS_NEW_ENTRY = -1;

    private FragmentEditTagNamesBinding vb;
    private TagAdapter adapter;
    private ExtMenuLauncher menuLauncher;
    private EditStringLauncher editLauncher;

    private TagAdminViewModel vm;

    private final PositionHandler positionHandler = new PositionHandler() {

        @Override
        public void onEdit(final int position) {
            editEntry(vm.getTags().get(position), position);
        }

        @Override
        public void onShowContextMenu(@NonNull final View v,
                                      final int position) {
            showContextMenu(v, position);
        }
    };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(TagAdminViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditStringLauncher(RK_TAG, this::onEditEntryDone);
        editLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditTagNamesBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.apply(vb.tagList);

        final GridLayoutManager layoutManager = (GridLayoutManager) vb.tagList.getLayoutManager();
        //noinspection DataFlowIssue
        adapter = new TagAdapter(layoutManager.getSpanCount(), vm.getTags(), positionHandler);

        //noinspection DataFlowIssue
        final GridDividerItemDecoration decoration =
                new GridDividerItemDecoration(getContext(), false, true);
        vb.tagList.addItemDecoration(decoration);

        vb.tagList.setAdapter(adapter);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        initFab();
        // the tag list could have been modified from a sibling
        // fragment in our host fragment (ViewPager)
        if (vm.isModified()) {
            vm.reloadTags();
            adapter.notifyDataSetChanged();
        }
    }

    private void initFab() {
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.add_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editEntry(new Tag(""), POS_NEW_ENTRY));
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void showContextMenu(@NonNull final View anchor,
                                 final int position) {
        final Context context = anchor.getContext();
        final Menu menu = MenuUtils.createEditDeleteContextMenu(context);
        menu.add(Menu.NONE, R.id.MENU_ACTION_ADD, 999,
                 R.string.lbl_add_or_edit_substitution)
            .setIcon(R.drawable.add_24px);

        menuLauncher.launch(anchor, null, null, position, menu);
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
            editEntry(vm.getTags().get(position), position);
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            deleteEntry(position);
            return true;

        } else if (menuItemId == R.id.MENU_ACTION_ADD) {
            getParentFragmentManager()
                    .getFragments()
                    .stream()
                    .filter(f -> f instanceof TagAdminFragment)
                    .findFirst()
                    .ifPresent(f -> ((TagAdminFragment) f)
                            .editOrCreateMapping(vm.getTags().get(position)));
            return true;
        }
        return false;
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param tag      to edit
     * @param position the position of the item; use {@link #POS_NEW_ENTRY} for a new entry.
     */
    private void editEntry(@NonNull final Tag tag,
                           final int position) {
        final Bundle extras = new Bundle();
        extras.putInt(BKEY_POSITION, position);
        //noinspection DataFlowIssue
        editLauncher.launch(getActivity(),
                            getString(R.string.lbl_tag), null,
                            InputType.TYPE_CLASS_TEXT,
                            tag.getName(),
                            extras);
    }

    private void onEditEntryDone(@Nullable final String previousName,
                                 @NonNull final String tagName,
                                 @Nullable final Bundle extras) {

        // anything actually changed ? If not, we're done.
        if (tagName.equals(previousName)) {
            return;
        }

        // brute force... the user modified something
        vm.setModified();

        try {
            Objects.requireNonNull(extras);
            final int position = extras.getInt(BKEY_POSITION);

            if (position == POS_NEW_ENTRY) {
                // User was adding a new tag
                addEntry(tagName);
            } else {
                // User was editing an existing tag
                updateEntry(tagName, position);
            }
        } catch (@NonNull final DaoWriteException e) {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e);
        }
    }


    private void addEntry(@NonNull final String tagName)
            throws DaoWriteException {
        // check by NAME it's not already in the list.
        final int existingPos = vm.findTagPosition(tagName);

        if (existingPos >= 0) {
            // Trying to add a NEW one already there. Just reject it...
            Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            vb.tagList.scrollToPosition(existingPos);
        } else {
            // It's a new entry, add it
            final int position = vm.insert(new Tag(tagName));
            adapter.notifyItemInserted(position);
            vb.tagList.scrollToPosition(position);
        }
    }

    private void updateEntry(@NonNull final String tagName,
                             final int position)
            throws DaoWriteException {

        // check by NAME it's not already in the list.
        final int existingPos = vm.findTagPosition(tagName);

        // we only get here if the new name IS different from the previous name
        // ... no need to compare positions
        if (existingPos == -1) {
            // update with the new data.
            final Tag tag = vm.getTags().get(position);
            tag.setName(tagName);

            vm.update(tag);
            adapter.notifyItemChanged(position);
            vb.tagList.scrollToPosition(position);

        } else {
            // Renaming a tag to have the same name as another/existing tag, propose to merge
            final Context context = getContext();
            //noinspection DataFlowIssue
            StandardDialogs.askToMerge(context, R.string.confirm_merge_tags, tagName, () -> {
                if (vm.moveBooks(context, position, existingPos)) {
                    adapter.notifyItemRemoved(position);
                    vb.tagList.scrollToPosition(existingPos);
                }
            });
        }
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final Tag tag = vm.getTags().get(position);
        //noinspection DataFlowIssue
        StandardDialogs.deleteTag(getContext(), tag, vm.countBooks(tag), () -> {
            vm.deleteTag(position);
            adapter.notifyItemRemoved(position);
        });
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
        private final RowEditTagNameBinding vb;

        Holder(@NonNull final RowEditTagNameBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@Nullable final Tag tag) {
            if (tag == null) {
                vb.getRoot().setVisibility(View.INVISIBLE);
            } else {
                vb.getRoot().setVisibility(View.VISIBLE);

                vb.name.setText(tag.getName());
            }
        }
    }

    private static class TagAdapter
            extends MultiColumnRecyclerViewAdapter<Holder> {

        @NonNull
        private final List<Tag> items;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param columnCount     from the grid layout
         * @param items           to display
         * @param positionHandler Proxy between adapter and ViewModel.
         */
        TagAdapter(@IntRange(from = 1) final int columnCount,
                   @NonNull final List<Tag> items,
                   @NonNull final PositionHandler positionHandler) {
            super(columnCount);
            this.items = items;
            this.positionHandler = positionHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditTagNameBinding vb = RowEditTagNameBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
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
                    ExtMenuButton.getPreferredMode(), (v, gridPosition) -> {
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
}

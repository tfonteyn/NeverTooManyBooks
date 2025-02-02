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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditTagNamesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagNameBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.editstring.EditStringLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.logger.LoggerFactory;

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
    private List<Tag> tags;
    private TagAdapter adapter;
    private ExtMenuLauncher menuLauncher;
    private EditStringLauncher editLauncher;

    private TagAdminViewModel vm;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(TagAdminViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditStringLauncher(RK_TAG);
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
        vb = FragmentEditTagNamesBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.apply(vb.tagList);

        final Context context = getContext();

        tags = ServiceLocator.getInstance().getTagDao().getAll();
        //noinspection DataFlowIssue
        adapter = new TagAdapter(context, tags);

        adapter.setOnRowClickListener((v, position) -> editEntry(tags.get(position), position));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (v, position) -> {
                    final Menu menu = MenuUtils.createEditDeleteContextMenu(v.getContext());
                    menu.add(Menu.NONE, R.id.MENU_ACTION_ADD, 999,
                             R.string.lbl_add_or_edit_substitution)
                        .setIcon(R.drawable.add_24px);

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

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        initFab();
        // the tag list could have been modified from a sibling
        // fragment in out host fragment (ViewPager)
        if (vm.isModified()) {
            tags.clear();
            tags.addAll(ServiceLocator.getInstance().getTagDao().getAll());
            adapter.notifyDataSetChanged();
        }
    }

    private void initFab() {
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.add_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editEntry(new Tag(""), POS_NEW_ENTRY));
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
            editEntry(tags.get(position), position);
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
                            .editOrCreateMapping(tags.get(position).getName()));
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
        final int existingPos = tags.stream().map(Tag::getName)
                                    .collect(Collectors.toList())
                                    .indexOf(tagName);
        if (existingPos >= 0) {
            // Trying to add a NEW one already there. Just reject it...
            Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            vb.tagList.scrollToPosition(existingPos);
        } else {
            // It's a new entry, add it
            final Tag tag = new Tag(tagName);
            ServiceLocator.getInstance().getTagDao().insert(tag);

            // find insertion point using a brute-force sequential search...
            int position = 0;
            while (position < tags.size() && tags.get(position).compareTo(tag) < 0) {
                position++;
            }
            tags.add(position, tag);
            adapter.notifyItemInserted(position);
            vb.tagList.scrollToPosition(position);
        }
    }

    private void updateEntry(@NonNull final String tagName,
                             final int position)
            throws DaoWriteException {

        final Tag tag = tags.get(position);
        tag.setName(tagName);

        // check by NAME it's not already in the list.
        final int existingPos = tags.stream().map(Tag::getName)
                                    .collect(Collectors.toList())
                                    .indexOf(tagName);

        if (existingPos >= 0) {
            // Renaming a tag to have the same name as another, propose to merge
            final Context context = getContext();
            //noinspection DataFlowIssue
            StandardDialogs.askToMerge(context, R.string.confirm_merge_tags,
                                       tag.getName(), () -> {
                        try {
                            ServiceLocator.getInstance().getTagDao()
                                          .moveBooks(context, tag, tags.get(existingPos));
                            adapter.notifyItemRemoved(position);
                            vb.tagList.scrollToPosition(existingPos);
                        } catch (@NonNull final DaoWriteException e) {
                            // log, but ignore - should never happen unless disk full
                            LoggerFactory.getLogger().e(TAG, e, tag);
                        }
                    });
        } else {
            // update with the new data.
            ServiceLocator.getInstance().getTagDao().update(tag);
            adapter.notifyItemChanged(position);
            vb.tagList.scrollToPosition(position);
        }
    }


    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final Tag tag = tags.get(position);
        // paranoia
        if (tag.getId() == 0) {
            // We should never get here.. all tags should have an id
            tags.remove(position);
            adapter.notifyItemRemoved(position);
            return;
        }

        //noinspection DataFlowIssue
        StandardDialogs.deleteTag(getContext(), tag, () -> {
            ServiceLocator.getInstance().getTagDao().delete(tag);
            tags.remove(position);
            adapter.notifyItemRemoved(position);
            // brute force... the user modified something
            vm.setModified();
        });
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

        void onBind(@NonNull final Tag tag) {
            vb.name.setText(tag.getName());
        }
    }

    private static class TagAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<Tag> items;
        @NonNull
        private final LayoutInflater inflater;

        @Nullable
        private OnRowClickListener rowClickListener;
        @Nullable
        private OnRowClickListener rowShowMenuListener;
        @Nullable
        private ExtMenuButton contextMenuMode;

        TagAdapter(@NonNull final Context context,
                   @NonNull final List<Tag> items) {
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
            final RowEditTagNameBinding vb =
                    RowEditTagNameBinding.inflate(inflater, parent, false);
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

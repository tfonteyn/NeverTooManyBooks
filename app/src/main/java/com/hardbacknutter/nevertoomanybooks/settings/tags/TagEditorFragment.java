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
import android.text.InputType;
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
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditTagNamesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagNameBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
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

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditStringLauncher(RK_TAG);
        editLauncher.setResultListener(this::onEntryUpdated);
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

    private void onEntryUpdated(@NonNull final String previous,
                                @NonNull final String current,
                                @Nullable final Bundle extras) {
        try {
            Objects.requireNonNull(extras);
            int position = extras.getInt(BKEY_POSITION);
            if (position == POS_NEW_ENTRY) {
                // check it's not already in the list.
                position = tags.stream().map(Tag::getName).collect(Collectors.toList())
                               .indexOf(current);
                if (position >= 0) {
                    Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                                  Snackbar.LENGTH_LONG).show();
                    vb.tagList.scrollToPosition(position);
                } else {
                    // It's a new entry, add it
                    final Tag tag = new Tag(current);
                    ServiceLocator.getInstance().getTagDao().insert(tag);

                    // find insertion point using a brute-force sequential search...
                    position = 0;
                    while (position < tags.size() && tags.get(position).compareTo(tag) < 0) {
                        position++;
                    }
                    tags.add(position, tag);
                    adapter.notifyItemInserted(position);
                    vb.tagList.scrollToPosition(position);

                }
            } else {
                // It's an existing entry in the list, find it and update with the new data
                final Tag existing = tags.get(position);
                existing.setName(current);
                ServiceLocator.getInstance().getTagDao().update(existing);

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
        final Tag tag = tags.get(position);
        // paranoia
        if (tag.getId() == 0) {
            // We should never get here.. all tags should have an id
            tags.remove(position);
            adapter.notifyItemRemoved(position);
            return;
        }
        final int books = ServiceLocator.getInstance().getTagDao().getBookIds(tag.getId()).size();
        final String nrOfBook = getResources().getQuantityString(R.plurals.n_books, books);
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.action_delete)
                .setMessage(getString(R.string.confirm_delete_tag_from_x_books,
                                      tag.getName(),
                                      nrOfBook))
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.ok, (d, w) -> {
                    tags.remove(position);
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

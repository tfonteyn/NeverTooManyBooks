/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.viewmodels.PreferredStylesViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit the list of styles.
 * <ul>
 * <li>Enable/disable their presence in the styles menu</li>
 * <li>Individual context menus allow cloning/editing/deleting of styles</li>
 * </ul>
 * All changes are saved immediately.
 */
public class PreferredStylesActivity
        extends BaseActivity {

    /** The adapter for the list. */
    private RecyclerViewAdapterBase mListAdapter;
    /** The View for the list. */
    @SuppressWarnings("FieldCanBeLocal")
    private RecyclerView mListView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager mLayoutManager;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    /** The ViewModel. */
    private PreferredStylesViewModel mModel;
    private final SimpleAdapterDataObserver mAdapterDataObserver = new SimpleAdapterDataObserver() {
        @Override
        public void onChanged() {
            // we save the order after each change.
            mModel.saveMenuOrder();
            // and make sure the results flags up we changed something.
            setResult(UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_PREFERRED_STYLES);
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_styles_edit_list;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        mModel.init();

        mListView = findViewById(android.R.id.list);
        mLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(mLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, mLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = new BooklistStylesAdapter(getLayoutInflater(), mModel.getList(),
                                                 vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        setTitle(R.string.lbl_preferred_styles);

        if (savedInstanceState == null) {
            TipManager.display(getLayoutInflater(), R.string.tip_booklist_styles_editor, null);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_EDIT_STYLE: {

                if (resultCode == UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE) {
                    Objects.requireNonNull(data);
                    BooklistStyle style = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                    if (style != null) {
                        mModel.handleStyleChange(style);
                    }
                    mListAdapter.notifyDataSetChanged();

                    // need to send up the chain
                    setResult(resultCode, data);
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    private void onCreateContextMenu(final int position) {
        BooklistStyle style = mModel.getList().get(position);

        Menu menu = MenuPicker.createMenu(this);

        if (style.isUserDefined()) {
            menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
                .setIcon(R.drawable.ic_edit);
            menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
                .setIcon(R.drawable.ic_delete);
        }

        menu.add(Menu.NONE, R.id.MENU_CLONE, 0, R.string.menu_duplicate)
            .setIcon(R.drawable.ic_content_copy);

        String title = style.getLabel(this);
        new MenuPicker<>(getLayoutInflater(), title, menu, style, this::onContextItemSelected)
                .show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem that was selected
     * @param style    to act on
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final BooklistStyle style) {

        switch (menuItem.getItemId()) {
            case R.id.MENU_CLONE:
                editStyle(style.clone(this));
                return true;

            case R.id.MENU_EDIT:
                if (style.isUserDefined()) {
                    editStyle(style);
                } else {
                    // editing a system style -> clone it first.
                    editStyle(style.clone(this));
                }
                return true;

            case R.id.MENU_DELETE:
                mModel.deleteStyle(style);
                mListAdapter.notifyDataSetChanged();

                setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
                return true;

            default:
                return false;
        }
    }

    /**
     * @param style to edit
     */
    private void editStyle(@NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debugEnter(this, "editStyle", style);
        }

        Intent intent = new Intent(this, SettingsActivity.class)
                                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, StyleSettingsFragment.TAG)
                                .putExtra(UniqueId.BKEY_STYLE, style);
        startActivityForResult(intent, UniqueId.REQ_EDIT_STYLE);
    }

    /**
     * Holder pattern object for list items.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView groupsView;
        @NonNull
        final TextView kindView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
            groupsView = itemView.findViewById(R.id.groups);
            kindView = itemView.findViewById(R.id.kind);
        }
    }

    private class BooklistStylesAdapter
            extends RecyclerViewAdapterBase<BooklistStyle, Holder> {

        /**
         * Constructor.
         *
         * @param inflater          LayoutInflater to use
         * @param items             List of styles
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        BooklistStylesAdapter(@NonNull final LayoutInflater inflater,
                              @NonNull final ArrayList<BooklistStyle> items,
                              @NonNull final StartDragListener dragStartListener) {
            super(inflater, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                                .inflate(R.layout.row_edit_booklist_style_groups, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            BooklistStyle style = getItem(position);

            holder.nameView.setText(style.getLabel(getContext()));

            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(style.isPreferred());

            holder.mCheckableButton.setOnClickListener(v -> {
                boolean newStatus = !style.isPreferred();
                style.setPreferred(newStatus);
                holder.mCheckableButton.setChecked(newStatus);
                notifyItemChanged(holder.getAdapterPosition());
            });

            holder.groupsView.setText(style.getGroupLabels(getContext()));
            if (style.isUserDefined()) {
                holder.kindView.setText(R.string.style_is_user_defined);
            } else {
                holder.kindView.setText(R.string.style_is_builtin);
            }

            // long-click -> menu
            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }
    }
}

/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertomanybooks.settings;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.debug.Tracker;
import com.hardbacknutter.nevertomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertomanybooks.viewmodels.PreferredStylesViewModel;
import com.hardbacknutter.nevertomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertomanybooks.widgets.ddsupport.StartDragListener;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Activity to edit the list of styles.
 * - enable/disable their presence in the styles menu.
 * - Individual context menus allow cloning/editing/deleting of styles.
 * <p>
 * All changes are saved immediately.
 */
public class PreferredStylesActivity
        extends BaseActivity {

    /** The adapter for the list. */
    protected RecyclerViewAdapterBase mListAdapter;
    /** The View for the list. */
    protected RecyclerView mListView;
    protected LinearLayoutManager mLayoutManager;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    /** The ViewModel. */
    private PreferredStylesViewModel mModel;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_styles_edit_list;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = ViewModelProviders.of(this).get(PreferredStylesViewModel.class);
        mModel.init(this);

        mListView = findViewById(android.R.id.list);
        mLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(mLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, mLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = new BooklistStylesAdapter(this, mModel.getList(),
                viewHolder -> mItemTouchHelper.startDrag(viewHolder));
        mListAdapter.registerAdapterDataObserver(new SimpleAdapterDataObserver() {
            @Override
            public void onChanged() {
                // we save the order after each change.
                mModel.saveMenuOrder(PreferredStylesActivity.this);
                // and make sure the results flags up we changed something.
                setResult(UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_PREFERRED_STYLES);
            }
        });
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        setTitle(R.string.lbl_preferred_styles);

        if (savedInstanceState == null) {
            TipManager.display(getLayoutInflater(),
                    R.string.tip_booklist_styles_editor, null);
        }
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

        String menuTitle = style.getLabel(this);
        final MenuPicker<BooklistStyle> picker = new MenuPicker<>(this, menuTitle, menu, style,
                this::onContextItemSelected);
        picker.show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem that was selected
     * @param style    to act on
     *
     * @return {@code true} if handled.
     */
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem,
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
                .putExtra(UniqueId.BKEY_STYLE, (Parcelable) style);
        startActivityForResult(intent, UniqueId.REQ_EDIT_STYLE);
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

    private static class BooklistStylesAdapter
            extends RecyclerViewAdapterBase<BooklistStyle, Holder> {

        @NonNull
        private final PreferredStylesActivity mActivity;

        BooklistStylesAdapter(@NonNull final PreferredStylesActivity activity,
                              @NonNull final ArrayList<BooklistStyle> items,
                              @NonNull final StartDragListener dragStartListener) {
            super(activity, items, dragStartListener);
            mActivity = activity;
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
                mActivity.onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }
    }
}

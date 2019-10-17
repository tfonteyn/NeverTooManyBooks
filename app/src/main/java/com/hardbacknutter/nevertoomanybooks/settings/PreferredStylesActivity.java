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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
    private BooklistStylesAdapter mListAdapter;
    /** The View for the list. */
    @SuppressWarnings("FieldCanBeLocal")
    private RecyclerView mListView;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    /** The ViewModel. */
    private PreferredStylesViewModel mModel;

    private final SimpleAdapterDataObserver mAdapterDataObserver = new SimpleAdapterDataObserver() {
        @Override
        public void onChanged() {
            // we save the order after each change.
            mModel.saveMenuOrder();
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_preferred_styles;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        mModel.init(Objects.requireNonNull(getIntent().getExtras()));

        mListView = findViewById(android.R.id.list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(layoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, layoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = new BooklistStylesAdapter(this, mModel.getList(),
                                                 mModel.getInitialStyleId(),
                                                 vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        setTitle(R.string.lbl_preferred_styles);

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_booklist_styles_editor, null);
        }
    }

    @Override
    public void onBackPressed() {
        Intent data = new Intent();

        // return the currently selected style, so the caller can apply it.
        BooklistStyle selectedStyle = mListAdapter.getSelectedStyle();
        if (selectedStyle != null) {
            data.putExtra(UniqueId.BKEY_STYLE, selectedStyle);
        }

        data.putExtra(UniqueId.BKEY_STYLE_MODIFIED, mModel.isDirty());

        setResult(Activity.RESULT_OK, data);
        super.onBackPressed();
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
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    if (data.getBooleanExtra(UniqueId.BKEY_STYLE_MODIFIED, false)) {
                        BooklistStyle style = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                        if (style != null) {
                            int position = mModel.handleStyleChange(style);
                            mListAdapter.setSelectedPosition(position);
                        }

                        // check if the style was cloned from a builtin style.
                        long templateId =
                                data.getLongExtra(StyleSettingsFragment.BKEY_TEMPLATE_ID, 0);
                        if (templateId < 0) {
                            // We're assuming the user wanted to 'replace' the builtin style,
                            // so remove the builtin style from the preferred styles.
                            BooklistStyle templateStyle = mModel.getBooklistStyle(templateId);
                            if (templateStyle != null) {
                                templateStyle.setPreferred(false);
                            }
                        }

                        // always update all rows
                        mListAdapter.notifyDataSetChanged();
                    }
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
        new MenuPicker<>(this, title, null, menu, style, this::onContextItemSelected)
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
                // pass the style id of the template style
                editStyle(style.clone(this), style.getId());
                return true;

            case R.id.MENU_EDIT:
                // dev sanity check
                if (BuildConfig.DEBUG /* always */) {
                    if (!style.isUserDefined()) {
                        throw new IllegalStateException("can't edit a builtin style");
                    }
                }
                editStyle(style, style.getId());
                return true;

            case R.id.MENU_DELETE:
                mModel.deleteStyle(style);
                mListAdapter.notifyDataSetChanged();
                return true;

            default:
                return false;
        }
    }

    /**
     * Start the edit process.
     *
     * @param style           to edit
     * @param templateStyleId the id of the style we're cloning from, or the style itself
     */
    private void editStyle(@NonNull final BooklistStyle style,
                           final long templateStyleId) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debugEnter(this, "editStyle", style);
        }

        Intent intent = new Intent(this, SettingsActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, StyleSettingsFragment.TAG)
                .putExtra(UniqueId.BKEY_STYLE, style)
                .putExtra(StyleSettingsFragment.BKEY_TEMPLATE_ID, templateStyleId);

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

        /** The id of the style which should be / is selected at creation time. */
        private final long mInitialStyleId;

        /** Currently selected row. */
        private int mSelectedPosition = RecyclerView.NO_POSITION;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of styles
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        BooklistStylesAdapter(@NonNull final Context context,
                              @NonNull final ArrayList<BooklistStyle> items,
                              final long initialStyleId,
                              @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            mInitialStyleId = initialStyleId;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_preferred_styles, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            BooklistStyle style = getItem(position);

            holder.nameView.setText(style.getLabel(getContext()));

            holder.groupsView.setText(style.getGroupLabels(getContext()));
            if (style.isUserDefined()) {
                holder.kindView.setText(R.string.style_is_user_defined);
            } else {
                holder.kindView.setText(R.string.style_is_builtin);
            }

            // handle the 'preferred style' checkable
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(style.isPreferred());
            holder.mCheckableButton.setOnClickListener(v -> setPreferred(holder));

            // select the original style if there was nothing selected (yet).
            if (mSelectedPosition == RecyclerView.NO_POSITION
                && style.getId() == mInitialStyleId) {
                mSelectedPosition = position;
            }

            // update the current row
            holder.itemView.setSelected(mSelectedPosition == position);

            // click -> set the row/style as 'selected'.
            // Do not modify the 'preferred' state of the row.
            holder.rowDetailsView.setOnClickListener(v -> {
                // update the previous, now unselected, row.
                notifyItemChanged(mSelectedPosition);
                // get/update the newly selected row.
                mSelectedPosition = holder.getAdapterPosition();
                notifyItemChanged(mSelectedPosition);
            });

            // long-click -> context menu
            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }

        /**
         * The user clicked the checkable button of the row.
         * <p>
         * User checked the row:
         * - set the row/style 'preferred'
         * - set the row 'selected'
         * User unchecked the row:
         * - set the row to 'not preferred'
         * - look up and down in the list to find a 'preferred' row, and set it 'selected'
         *
         * @param holder for the checked row.
         */
        private void setPreferred(@NonNull final Holder holder) {
            // current row/style
            int position = holder.getAdapterPosition();
            BooklistStyle style = getItem(position);

            // handle the 'preferred' state of the current row/style
            boolean checked = !style.isPreferred();
            style.setPreferred(checked);
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(checked);

            // handle the 'selected' state of the current row/style.
            if (!checked && mSelectedPosition == position) {
                //look up and down in the list to find a 'preferred' row, and set it 'selected'
                if (!findPreferred(-1) && !findPreferred(+1)) {
                    // if no such row found, force the current row regardless
                    mSelectedPosition = position;
                }
                // update the newly selected row. This might be another, or the current row.
                notifyItemChanged(mSelectedPosition);
            }

            // update the current row unless we've already done that above.
            if (mSelectedPosition != position) {
                // update the current row.
                notifyItemChanged(position);
            }
        }

        /**
         * Look up and down in the list to find a 'preferred' row.
         * If found, set it as the selected position.
         *
         * @param direction must be either '-1' or '+1'
         *
         * @return {@code true} if a suitable position was found (and set)
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean findPreferred(final int direction) {
            int newPosition = mSelectedPosition;
            while (true) {
                // move one up or down.
                newPosition = newPosition + direction;

                // breached the upper or lower limit ?
                if (newPosition < 0 || newPosition >= this.getItemCount()) {
                    return false;
                }

                if (getItem(newPosition).isPreferred()) {
                    mSelectedPosition = newPosition;
                    return true;
                }
            }
        }

        /**
         * Update the selection.
         *
         * @param position the newly selected row
         */
        void setSelectedPosition(final int position) {
            mSelectedPosition = position;
        }

        /**
         * Get the currently selected style.
         *
         * @return style, or {@code null} if none selected (which should never happen... flw)
         */
        @Nullable
        BooklistStyle getSelectedStyle() {
            return mSelectedPosition != RecyclerView.NO_POSITION ? getItem(mSelectedPosition)
                                                                 : null;
        }

        @Override
        public boolean onItemMove(final int fromPosition,
                                  final int toPosition) {

            if (fromPosition == mSelectedPosition) {
                // moving the selected row.
                mSelectedPosition = toPosition;
            } else if (toPosition == mSelectedPosition) {
                if (fromPosition > mSelectedPosition) {
                    // push down
                    mSelectedPosition++;
                } else {
                    // push up
                    mSelectedPosition--;
                }
            }
            return super.onItemMove(fromPosition, toPosition);
        }
    }
}

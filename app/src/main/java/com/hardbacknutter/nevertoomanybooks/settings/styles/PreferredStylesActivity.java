/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.viewmodels.PreferredStylesViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit the list of styles.
 * <ul>
 *      <li>Enable/disable their presence in the styles menu</li>
 *      <li>Individual context menus allow cloning/editing/deleting of styles</li>
 * </ul>
 * All changes are saved immediately.
 */
public class PreferredStylesActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "PreferredStylesActivity";

    /** The adapter for the list. */
    private BooklistStylesAdapter mListAdapter;
    /** The View for the list. */
    @SuppressWarnings("FieldCanBeLocal")
    private RecyclerView mListView;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    /** The ViewModel. */
    private PreferredStylesViewModel mModel;

    /** Saves the order after each change. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mModel.saveMenuOrder(PreferredStylesActivity.this);
                }
            };

    private final MenuPickerDialogFragment.OnResultListener mOnMenuPickerListener =
            this::onContextItemSelected;

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_edit_preferred_styles);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager().setFragmentResultListener(
                MenuPickerDialogFragment.REQUEST_KEY, this, mOnMenuPickerListener);

        mModel = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        mModel.init(this, Objects.requireNonNull(getIntent().getExtras(), ErrorMsg.NULL_EXTRAS));

        mListView = findViewById(R.id.stylesList);
        mListView.addItemDecoration(new DividerItemDecoration(this, RecyclerView.VERTICAL));
        mListView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = new BooklistStylesAdapter(this, mModel.getList(),
                                                 mModel.getInitialStyleUuid(),
                                                 vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mListView.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_booklist_styles_editor, null);
        }
    }

    @Override
    public void onBackPressed() {
        final Intent resultData = new Intent();

        // return the currently selected style UUID, so the caller can apply it.
        // This is independent from the fact of this style (or any other) been modified or not.
        final BooklistStyle selectedStyle = mListAdapter.getSelected();
        if (selectedStyle != null) {
            resultData.putExtra(BooklistStyle.BKEY_STYLE_UUID, selectedStyle.getUuid());
        }

        // again: independent from the returned style!
        resultData.putExtra(BooklistStyle.BKEY_STYLE_MODIFIED, mModel.isDirty());

        setResult(Activity.RESULT_OK, resultData);
        super.onBackPressed();
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.EDIT_STYLE: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    // We get the ACTUAL style back.
                    // This style might be new (id==0) or already existing (id!=0).
                    @Nullable
                    final BooklistStyle style = data.getParcelableExtra(BooklistStyle.BKEY_STYLE);

                    if (data.getBooleanExtra(BooklistStyle.BKEY_STYLE_MODIFIED, false)) {
                        if (style != null) {
                            // id of the original style we cloned (different from current)
                            // or edited (same as current).
                            final long templateId = data.getLongExtra(
                                    StyleBaseFragment.BKEY_TEMPLATE_ID, style.getId());

                            final int position = mModel.onStyleChange(this, style, templateId);
                            mListAdapter.setSelectedPosition(position);
                        }

                        // always update all rows as the order might have changed
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        // the style was not modified, discard it if this was a cloned (new) style
                        if (style != null && style.getId() == 0) {
                            BooklistStyle.StyleDAO.discard(this, style);
                        }
                    }
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_PURGE_BLNS, 0, R.string.lbl_purge_blns)
            .setIcon(R.drawable.ic_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        // only enable if a style is selected
        menu.findItem(R.id.MENU_PURGE_BLNS)
            .setEnabled(mListAdapter.getSelected() != null);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PURGE_BLNS: {
                final BooklistStyle selected = mListAdapter.getSelected();
                if (selected != null) {
                    StandardDialogs.purgeBLNS(this, R.string.lbl_style, selected, () ->
                            mModel.purgeBLNS(selected.getId()));
                }
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        final Resources res = getResources();
        final BooklistStyle style = mModel.getList().get(position);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            final ArrayList<MenuPickerDialogFragment.Pick> menu = new ArrayList<>();

            if (style.isUserDefined()) {
                menu.add(new MenuPickerDialogFragment.Pick(
                        R.id.MENU_EDIT, res.getInteger(R.integer.MENU_ORDER_EDIT),
                        getString(R.string.action_edit_ellipsis),
                        R.drawable.ic_edit));
                menu.add(new MenuPickerDialogFragment.Pick(
                        R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                        getString(R.string.action_delete),
                        R.drawable.ic_delete));
            }

            menu.add(new MenuPickerDialogFragment.Pick(
                    R.id.MENU_DUPLICATE, res.getInteger(R.integer.MENU_ORDER_DUPLICATE),
                    getString(R.string.action_duplicate),
                    R.drawable.ic_content_copy));

            final String title = style.getLabel(this);
            MenuPickerDialogFragment
                    .newInstance(title, menu, position)
                    .show(getSupportFragmentManager(), MenuPickerDialogFragment.TAG);
        } else {
            final Menu menu = MenuPicker.createMenu(this);
            if (style.isUserDefined()) {
                menu.add(Menu.NONE, R.id.MENU_EDIT,
                         res.getInteger(R.integer.MENU_ORDER_EDIT),
                         R.string.action_edit_ellipsis)
                    .setIcon(R.drawable.ic_edit);
                menu.add(Menu.NONE, R.id.MENU_DELETE,
                         res.getInteger(R.integer.MENU_ORDER_DELETE),
                         R.string.action_delete)
                    .setIcon(R.drawable.ic_delete);
            }

            menu.add(Menu.NONE, R.id.MENU_DUPLICATE,
                     res.getInteger(R.integer.MENU_ORDER_DUPLICATE),
                     R.string.action_duplicate)
                .setIcon(R.drawable.ic_content_copy);

            final String title = style.getLabel(this);
            new MenuPicker(this, title, menu, position, this::onContextItemSelected)
                    .show();
        }
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {

        final BooklistStyle style = mModel.getList().get(position);

        switch (menuItem) {
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
                mModel.deleteStyle(this, style);
                mListAdapter.notifyItemRemoved(position);
                return true;

            case R.id.MENU_DUPLICATE:
                // pass the style id of the template style
                editStyle(style.clone(this), style.getId());
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

        final Intent intent = new Intent(this, SettingsActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, StyleFragment.TAG)
                .putExtra(BooklistStyle.BKEY_STYLE, style)
                .putExtra(StyleBaseFragment.BKEY_TEMPLATE_ID, templateStyleId);

        startActivityForResult(intent, RequestCode.EDIT_STYLE);
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView groupsView;
        @NonNull
        final TextView typeView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
            groupsView = itemView.findViewById(R.id.groups);
            typeView = itemView.findViewById(R.id.type);
        }
    }

    private class BooklistStylesAdapter
            extends RecyclerViewAdapterBase<BooklistStyle, Holder> {

        /** The UUID of the item which should be / is selected at creation time. */
        @NonNull
        private final String mInitialSelectedItemUuid;

        /** Currently selected row. */
        private int mSelectedPosition = RecyclerView.NO_POSITION;

        /**
         * Constructor.
         *
         * @param context                 Current context
         * @param items                   List of styles
         * @param initialSelectedItemUuid initially selected style UUID
         * @param dragStartListener       Listener to handle the user moving rows up and down
         */
        BooklistStylesAdapter(@NonNull final Context context,
                              @NonNull final List<BooklistStyle> items,
                              @NonNull final String initialSelectedItemUuid,
                              @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            mInitialSelectedItemUuid = initialSelectedItemUuid;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_preferred_styles, parent, false);
            final Holder holder = new Holder(view);

            // click -> set the row as 'selected'.
            // Do not modify the 'preferred' state of the row.
            holder.rowDetailsView.setOnClickListener(v -> {
                // first update the previous, now unselected, row.
                notifyItemChanged(mSelectedPosition);
                // get and update the newly selected row.
                mSelectedPosition = holder.getBindingAdapterPosition();
                notifyItemChanged(mSelectedPosition);
            });

            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getBindingAdapterPosition());
                return true;
            });

            // click the checkbox -> set as 'preferred'
            //noinspection ConstantConditions
            holder.mCheckableButton.setOnClickListener(v -> onItemCheckChanged(holder));

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final BooklistStyle style = getItem(position);

            holder.nameView.setText(style.getLabel(getContext()));

            holder.groupsView.setText(style.getGroupLabels(getContext()));
            if (style.isUserDefined()) {
                holder.typeView.setText(R.string.style_is_user_defined);
            } else {
                holder.typeView.setText(R.string.style_is_builtin);
            }

            // set the 'preferred style' checkable
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(style.isPreferred(getContext()));

            // select the original row if there was nothing selected (yet).
            if (mSelectedPosition == RecyclerView.NO_POSITION
                && mInitialSelectedItemUuid.equals(style.getUuid())) {
                mSelectedPosition = position;
            }

            // finally update the new current row
            holder.itemView.setSelected(mSelectedPosition == position);
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
        private void onItemCheckChanged(@NonNull final Holder holder) {
            // current row/style
            final int position = holder.getBindingAdapterPosition();
            final BooklistStyle style = getItem(position);

            // handle the 'preferred' state of the current row/style
            final boolean checked = !style.isPreferred(getContext());
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

                if (getItem(newPosition).isPreferred(getContext())) {
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
         * Get the currently selected item.
         *
         * @return style, or {@code null} if none selected (which should never happen... flw)
         */
        @Nullable
        BooklistStyle getSelected() {
            if (mSelectedPosition != RecyclerView.NO_POSITION) {
                return getItem(mSelectedPosition);
            }
            return null;
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

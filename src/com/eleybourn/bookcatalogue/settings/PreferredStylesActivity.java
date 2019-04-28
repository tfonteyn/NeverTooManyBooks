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

package com.eleybourn.bookcatalogue.settings;

import android.content.Context;
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

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.MenuPicker;
import com.eleybourn.bookcatalogue.dialogs.ValuePicker;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.SimpleAdapterDataObserver;
import com.eleybourn.bookcatalogue.widgets.ddsupport.OnStartDragListener;

/**
 * Activity to edit the list of styles.
 * - enable/disable their presence in the styles menu.
 * - Individual context menus allow cloning/editing/deleting of styles.
 *
 * @author Philip Warner
 */
public class PreferredStylesActivity
        extends EditObjectListActivity<BooklistStyle> {

    private static final int REQ_EDIT_STYLE = 0;

    /** The row being edited. Set when an individual style is edited. */
    private int mEditedRow;

    /**
     * Constructor.
     */
    public PreferredStylesActivity() {
        super(null);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_styles_edit_list;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.lbl_preferred_styles);

        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(),
                                    R.string.hint_booklist_styles_editor, null);
        }
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Override
    @NonNull
    protected ArrayList<BooklistStyle> getList() {
        return new ArrayList<>(BooklistStyles.getStyles(mDb, true).values());
    }

    /**
     * Bring up the context menu for a row (style).
     * <p>
     * Reminder: the item row itself has to have:  android:longClickable="true".
     * Otherwise the click will only work on the 'blank' bits of the row.
     */
    private void onCreateContextMenu(final int position) {

        BooklistStyle style = mList.get(position);

        Menu menu = MenuPicker.createMenu(this);
        menu.add(Menu.NONE, R.id.MENU_CLONE, 0, R.string.menu_duplicate)
            .setIcon(R.drawable.ic_content_copy);

        if (style.isUserDefined()) {
            menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
                .setIcon(R.drawable.ic_edit);
            menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
                .setIcon(R.drawable.ic_delete);
        }

        // display the menu
        String menuTitle = style.getLabel(this);
        final MenuPicker<Integer> picker = new MenuPicker<>(this, menuTitle, menu, position,
                                                            this::onContextItemSelected);
        picker.show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     */
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                         @NonNull final Integer position) {

        // Save the current row
        mEditedRow = position;

        BooklistStyle style = mList.get(position);
        switch (menuItem.getItemId()) {
            case R.id.MENU_CLONE:
                // clone any style
                style = style.getClone(this, mDb);
                editStyle(style);
                return true;

            case R.id.MENU_EDIT:
                // editing a system style -> clone it first.
                if (!style.isUserDefined()) {
                    style = style.getClone(this, mDb);
                }
                editStyle(style);
                return true;

            case R.id.MENU_DELETE:
                // the delete menu is only brought up for user defined styles.
                style.delete(mDb);
                handleStyleChange(null);
                setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
                return true;

            default:
                return false;
        }
    }

    /**
     * Edit the style.
     *
     * @param style to edit
     */
    private void editStyle(@NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debugEnter(this, "editStyle", style.toString());
        }
        Intent intent = new Intent(this, SettingsActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BooklistStyleSettingsFragment.TAG)
                .putExtra(BooklistStyleSettingsFragment.REQUEST_BKEY_STYLE, (Parcelable) style);
        startActivityForResult(intent, REQ_EDIT_STYLE);
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case REQ_EDIT_STYLE: {
                // need to send up the chain as-is
                if (resultCode == UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE) {
                    //noinspection ConstantConditions
                    BooklistStyle style = data.getParcelableExtra(
                            BooklistStyleSettingsFragment.REQUEST_BKEY_STYLE);
                    handleStyleChange(style);
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
     * Called after a style has been edited.
     */
    private void handleStyleChange(@Nullable final BooklistStyle style) {
        try {
            if (style == null) {
                // Style was deleted. Refresh.
                mList = getList();

            } else if (mEditedRow < 0) {
                // New Style added. So put at top and set as preferred
                mList.add(0, style);
                style.setPreferred(true);

            } else {
                // Existing Style edited.
                BooklistStyle origStyle = mList.get(mEditedRow);
                if (origStyle.getId() != style.getId()) {
                    if (!origStyle.isUserDefined()) {
                        // Working on a clone of a builtin style
                        if (origStyle.isPreferred()) {
                            // Replace the original row with the new one
                            mList.set(mEditedRow, style);
                            // Make the new one preferred
                            style.setPreferred(true);
                            // And demote the original
                            origStyle.setPreferred(false);
                            mList.add(origStyle);
                        } else {
                            // Try to put it directly after original
                            mList.add(mEditedRow, style);
                        }
                    } else {
                        // A clone of an user-defined. Put it directly after the user-defined
                        mList.add(mEditedRow, style);
                    }
                } else {
                    mList.set(mEditedRow, style);
                }
            }
            if (style != null) {
                // add to the db if new.
                if (style.getId() == 0) {
                    mDb.insertBooklistStyle(style);
                }
            }

            setList(mList);
            mListAdapter.notifyDataSetChanged();

        } catch (RuntimeException e) {
            Logger.error(this, e);
            // Do our best to recover
            setList(getList());
        }
    }

    protected RecyclerViewAdapterBase createListAdapter(@NonNull final ArrayList<BooklistStyle> list,
                                                        @NonNull final OnStartDragListener dragStartListener) {
        BooklistStyleListAdapter adapter =
                new BooklistStyleListAdapter(this, list, dragStartListener);
        adapter.registerAdapterDataObserver(new SimpleAdapterDataObserver() {
            @Override
            public void onChanged() {
                // we save the order after each change.
                BooklistStyles.savePreferredStyleMenuOrder(mList);
                // and make sure the results flags up we changed something.
                setResult(UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_PREFERRED_STYLES);
            }
        });
        return adapter;
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

    private class BooklistStyleListAdapter
            extends RecyclerViewAdapterBase<BooklistStyle, Holder> {

        BooklistStyleListAdapter(@NonNull final Context context,
                                 @NonNull final ArrayList<BooklistStyle> items,
                                 @NonNull final OnStartDragListener dragStartListener) {
            super(context, items, dragStartListener);
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
                notifyItemChanged(position);
            });

            holder.groupsView.setText(style.getGroupListDisplayNames(getContext()));
            if (style.isUserDefined()) {
                holder.kindView.setText(R.string.style_is_user_defined);
            } else {
                holder.kindView.setText(R.string.style_is_builtin);
            }

            // long-click -> menu
            holder.rowDetailsView.setOnLongClickListener((v) -> {
                onCreateContextMenu(position);
                return true;
            });
        }
    }
}

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
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Objects;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;

/**
 * Activity to edit the list of styles.
 * - enable/disable their presence in the styles menu.
 * - Individual context menus allow cloning/editing/deleting of styles.
 *
 * @author Philip Warner
 */
public class PreferredStylesActivity
        extends EditObjectListActivity<BooklistStyle>
        implements SimpleDialog.ListViewContextMenu,
                   AdapterView.OnItemLongClickListener {

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

        // We want context menus on the ListView
        //getListView().setOnCreateContextMenuListener(this);
        // no, we don't, as we'll use long click to bring up custom context menus WITH icons
        getListView().setOnItemLongClickListener(this);

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
     * Reminder: the item row itself has to have:  android:longClickable="true".
     * Otherwise the click will only work on the 'blank' bits of the row.
     */
    @Override
    public boolean onItemLongClick(@NonNull final AdapterView<?> parent,
                                   @NonNull final View view,
                                   final int position,
                                   final long id) {
        BooklistStyle style = mListAdapter.getItem(position);
        //noinspection ConstantConditions
        String menuTitle = style.getDisplayName();

        // legal trick to get an instance of Menu.
        mListViewContextMenu = new PopupMenu(view.getContext(), null).getMenu();
        // custom menuInfo
        SimpleDialog.ContextMenuInfo menuInfo =
                new SimpleDialog.ContextMenuInfo(menuTitle, position);
        // populate the menu
        if (style.isUserDefined()) {
            mListViewContextMenu.add(Menu.NONE, R.id.MENU_STYLE_DELETE, 0,
                                     R.string.menu_delete_style)
                                .setIcon(R.drawable.ic_delete);
            mListViewContextMenu.add(Menu.NONE, R.id.MENU_STYLE_EDIT, 0,
                                     R.string.menu_edit_booklist_style)
                                .setIcon(R.drawable.ic_edit);
        }
        mListViewContextMenu.add(Menu.NONE, R.id.MENU_STYLE_CLONE, 0,
                                 R.string.menu_clone_style)
                            .setIcon(R.drawable.ic_content_copy);

        // display the menu
        onCreateListViewContextMenu(view, mListViewContextMenu, menuInfo);
        return true;
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    @Override
    public boolean onListViewContextItemSelected(@NonNull final MenuItem menuItem,
                                                 final int position) {

        // Save the current row
        mEditedRow = position;

        BooklistStyle style = mList.get(position);
        switch (menuItem.getItemId()) {
            case R.id.MENU_STYLE_DELETE:
                style.delete(mDb);
                handleStyleChange(null);
                setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
                return true;

            case R.id.MENU_STYLE_EDIT:
                if (!style.isUserDefined()) {
                    style = style.getClone(mDb);
                }
                editStyle(style);
                return true;

            case R.id.MENU_STYLE_CLONE:
                editStyle(style.getClone(mDb));
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
        if (DEBUG_SWITCHES.DUMP_STYLE && BuildConfig.DEBUG) {
            Logger.info(this, "editStyle", style.toString());
        }
        Intent intent = new Intent(this, SettingsActivity.class)
                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BooklistStyleSettingsFragment.TAG)
                .putExtra(BooklistStyleSettingsFragment.REQUEST_BKEY_STYLE, (Parcelable) style);
        startActivityForResult(intent, REQ_EDIT_STYLE);
    }

    @Override
    protected void onListChanged() {
        super.onListChanged();
        // we save the order after each change.
        BooklistStyles.savePreferredStyleMenuOrder(mList);
        // and make sure the results flags up we changed something.
        setResult(UniqueId.ACTIVITY_RESULT_OK_BooklistPreferredStyles);
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_EDIT_STYLE: {
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_OK_BooklistStyleProperties: {
                        Objects.requireNonNull(data);
                        BooklistStyle style = data.getParcelableExtra(
                                BooklistStyleSettingsFragment.REQUEST_BKEY_STYLE);
                        handleStyleChange(style);
                        // need to send up the chain as-is
                        setResult(UniqueId.ACTIVITY_RESULT_OK_BooklistStyleProperties,
                                  data);
                        break;
                    }
                    default:
                        break;
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
                // Was added. So put at top and set as preferred
                mList.add(0, style);
                style.setPreferred(true);
            } else {
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
            onListChanged();

        } catch (RuntimeException e) {
            Logger.error(e);
            // Do our best to recover
            setList(getList());
        }
    }

    protected ArrayAdapter<BooklistStyle> createListAdapter(@NonNull final ArrayList<BooklistStyle> list) {
        return new BooklistStyleListAdapter(this, list);
    }

    /**
     * Holder pattern object for list items.
     */
    private static class Holder {

        @NonNull
        final CheckedTextView checkableView;
        @NonNull
        final TextView nameView;
        @NonNull
        final TextView groupsView;
        @NonNull
        final TextView kindView;
        BooklistStyle style;

        public Holder(@NonNull final View rowView) {
            nameView = rowView.findViewById(R.id.name);
            checkableView = rowView.findViewById(R.id.TLV_ROW_CHECKABLE);
            groupsView = rowView.findViewById(R.id.groups);
            kindView = rowView.findViewById(R.id.kind);

            rowView.setTag(this);
        }
    }

    private class BooklistStyleListAdapter
            extends SimpleListAdapter<BooklistStyle> {

        BooklistStyleListAdapter(@NonNull final Context context,
                                 @NonNull final ArrayList<BooklistStyle> items) {
            super(context, R.layout.row_edit_booklist_style_groups, items);
        }

        @Override
        public void onGetView(@NonNull final View convertView,
                              @NonNull final BooklistStyle item) {
            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                holder = new Holder(convertView);
                holder.checkableView.setTag(holder);

                // Handle clicks on the CheckedTextView
                holder.checkableView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(@NonNull final View v) {
                        Holder h = (Holder) v.getTag();
                        boolean newStatus = !h.style.isPreferred();
                        h.style.setPreferred(newStatus);
                        h.checkableView.setChecked(newStatus);
                        onListChanged();
                    }
                });
            }

            // Setup the variant fields in the holder
            holder.style = item;
            holder.nameView.setText(item.getDisplayName());
            holder.checkableView.setChecked(item.isPreferred());

            holder.groupsView.setText(item.getGroupListDisplayNames());
            if (item.isUserDefined()) {
                holder.kindView.setText(R.string.style_is_user_defined);
            } else {
                holder.kindView.setText(R.string.style_is_builtin);
            }
        }

        /**
         * Delegate to ListView host.
         */
        @Override
        protected void onListChanged() {
            // will save the menu order + call setResult
            PreferredStylesActivity.this.onListChanged();
        }
    }
}

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

package com.eleybourn.bookcatalogue.booklist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckedTextView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.picklist.SelectOneDialog;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Activity to edit the list of styles and enable/disable their presence in the styles menu.
 *
 * @author Philip Warner
 */
public class BooklistStylesActivity extends EditObjectListActivity<BooklistStyle> implements SelectOneDialog.hasListViewContextMenu {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLES;

    /** The row being edited. Set when an individual style is edited */
    private int mEditedRow;

    /**
     * Constructor
     */
    public BooklistStylesActivity() {
        super(R.layout.activity_booklist_styles_edit_list, R.layout.row_edit_booklist_style_groups, null);
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            this.setTitle(R.string.preferred_styles);

            // We want context menus to be available
            registerForContextMenu(getListView());

            if (savedInstanceState == null) {
                HintManager.displayHint(this.getLayoutInflater(), R.string.hint_booklist_styles_editor, null);
            }
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Override
    @NonNull
    protected ArrayList<BooklistStyle> getList() {
        return new ArrayList<>(BooklistStyles.getAllStyles(this.mDb));
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public boolean onListViewContextItemSelected(@NonNull final MenuItem menuItem,
                                                 @NonNull final SelectOneDialog.SimpleDialogMenuInfo menuInfo) {

        switch (menuItem.getItemId()) {
            case R.id.MENU_STYLE_DELETE: {
                mList.get(menuInfo.position).delete(mDb);
                setList(getList()); // Refresh the list
                return true;
            }
            case R.id.MENU_STYLE_EDIT: {
                editStyle(menuInfo.position, false);
                return true;
            }
            case R.id.MENU_STYLE_CLONE: {
                editStyle(menuInfo.position, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Edit the style at 'position' in the list, saving its details locally. Optionally clone.
     *
     * @param position    Position in list
     * @param alwaysClone Force a clone, even if its already user-defined
     */
    private void editStyle(final int position, final boolean alwaysClone) {
        // Save the current row
        mEditedRow = position;

        BooklistStyle style = mList.get(position);

        if (!style.isUserDefined() || alwaysClone) {
            try {
                style = style.getClone();
                style.setName(style.getDisplayName());
            } catch (RTE.DeserializationException e) {
                Logger.error(e);
                StandardDialogs.showUserMessage(this, R.string.error_unexpected_error);
                return;
            }
        }

        Intent intent = new Intent(this, BooklistStylePropertiesActivity.class);
        intent.putExtra(BooklistStylePropertiesActivity.REQUEST_BKEY_STYLE, style);
        startActivityForResult(intent, BooklistStylePropertiesActivity.REQUEST_CODE); /* fadd7b9a-7eaf-4af9-90ce-6ffb7b93afe6 */
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case BooklistStylePropertiesActivity.REQUEST_CODE:  /* fadd7b9a-7eaf-4af9-90ce-6ffb7b93afe6 */
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    BooklistStyle style = (BooklistStyle) data.getSerializableExtra(BooklistStylePropertiesActivity.REQUEST_BKEY_STYLE);
                    // style can be null (when it was deleted)
                    handleStyleResult(style);
                }
                // pass the result code up
                setResult(resultCode);
                return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Called after a style has been edited.
     *
     * @param style as received from onActivityResult
     */
    private void handleStyleResult(final @Nullable BooklistStyle style) {
        try {
            if (style == null) {
                // Style was deleted. Refresh.
                setList(getList());

            } else if (mEditedRow < 0) {
                // Was added. So put at top and set as preferred
                style.setPreferred(true);
                mList.add(0, style);
                BooklistStyles.saveMenuOrder(mList);

            } else {
                BooklistStyle origStyle = mList.get(mEditedRow);
                if (origStyle.id != style.id) {
                    if (!origStyle.isUserDefined()) {
                        // Working on a clone of a builtin style
                        if (origStyle.isPreferred()) {
                            // Replace the original row with the new one
                            mList.set(mEditedRow, style);
                            // And demote the original
                            origStyle.setPreferred(false);
                            mList.add(origStyle);
                        } else {
                            // Try to put it directly after original
                            mList.add(mEditedRow, style);
                        }
                    } else {
                        // A clone of an original. Put it directly after the original
                        mList.add(mEditedRow, style);
                    }
                    if (style.isPreferred()) {
                        BooklistStyles.saveMenuOrder(mList);
                    }
                } else {
                    mList.set(mEditedRow, style);
                }
            }
            setList(mList);
        } catch (Exception e) {
            Logger.error(e);
            // Do our best to recover
            setList(getList());
        }
    }

    protected SimpleListAdapter<BooklistStyle> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<BooklistStyle> list) {
        return new BooklistStyleListAdapter(this, rowViewId, list);
    }

    protected class BooklistStyleListAdapter extends SimpleListAdapter<BooklistStyle> implements
            SimpleListAdapterRowActionListener<BooklistStyle> {

        @NonNull
        private final Context mContext;

        BooklistStyleListAdapter(final @NonNull Context context, final @LayoutRes int rowViewId, final @NonNull ArrayList<BooklistStyle> items) {
            super(context, rowViewId, items);
            mContext = context;
        }

        @Override
        public void onGetView(final @NonNull View target, final @NonNull BooklistStyle style) {
            Holder holder = ViewTagger.getTag(target, R.id.TAG_HOLDER);// value: BooklistStylesActivity.Holder
            if (holder == null) {
                holder = new Holder();
                holder.preferred = target.findViewById(R.id.preferred);
                holder.name = target.findViewById(R.id.name);
                holder.groups = target.findViewById(R.id.groups);
                holder.kind = target.findViewById(R.id.kind);
                // Tag relevant views
                ViewTagger.setTag(holder.preferred, R.id.TAG_HOLDER, holder);// value: BooklistStylesActivity.Holder
                ViewTagger.setTag(target, R.id.TAG_HOLDER, holder);// value: BooklistStylesActivity.Holder

                // Handle clicks on the CheckedTextView
                holder.preferred.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(@NonNull View v) {
                        Holder h = ViewTagger.getTagOrThrow(v, R.id.TAG_HOLDER);// value: BooklistStylesActivity.Holder
                        boolean newStatus = !h.style.isPreferred();
                        h.style.setPreferred(newStatus);
                        h.preferred.setChecked(newStatus);
                        onListChanged();
                    }
                });
            }

            // Set the volatile fields in the holder
            holder.style = style;
            holder.name.setText(style.getDisplayName());
            holder.groups.setText(style.getGroupListDisplayNames());
            holder.preferred.setChecked(style.isPreferred());

            if (style.isUserDefined()) {
                holder.kind.setText(R.string.user_defined);
            } else {
                holder.kind.setText(R.string.builtin);
            }
        }

        /**
         * TODO: use {@link com.eleybourn.bookcatalogue.dialogs.picklist.SelectOneDialog.SimpleDialogMenuItem}
         * Use the onRowLongClick to present a context menu.
         */
        @Override
        public boolean onRowLongClick(final @NonNull View target, final @NonNull BooklistStyle style, final int position) {
            String menuTitle = style.getDisplayName();

            // legal trick to get an instance of Menu.
            mListViewContextMenu = new PopupMenu(mContext, null).getMenu();
            // custom menuInfo
            SelectOneDialog.SimpleDialogMenuInfo menuInfo = new SelectOneDialog.SimpleDialogMenuInfo(menuTitle, target, position);
            // populate the menu
            if (style.isUserDefined()) {
                mListViewContextMenu.add(Menu.NONE, R.id.MENU_STYLE_DELETE, 0, R.string.menu_delete_style)
                        .setIcon(R.drawable.ic_delete);
                mListViewContextMenu.add(Menu.NONE, R.id.MENU_STYLE_EDIT, 0, R.string.menu_edit_book_list_style)
                        .setIcon(R.drawable.ic_mode_edit);
            }
            mListViewContextMenu.add(Menu.NONE, R.id.MENU_STYLE_CLONE, 0, R.string.menu_clone_style)
                    .setIcon(R.drawable.ic_content_copy);

            // display
            BooklistStylesActivity.this.onCreateListViewContextMenu(mListViewContextMenu, target, menuInfo);
            return true;

        }

        @Override
        public void onListChanged() {
            BooklistStyles.saveMenuOrder(mList);
        }
    }

    /**
     * Holder pattern object for list items
     *
     * @author Philip Warner
     */
    private class Holder {
        BooklistStyle style;
        CheckedTextView preferred;
        TextView name;
        TextView groups;
        TextView kind;
    }
}

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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckedTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * Activity to edit the list of styles and enable/disable their presence in the styles menu.
 *
 * @author Philip Warner
 */
public class BooklistStylesListActivity extends EditObjectListActivity<BooklistStyle> {
    /**
     * The row being edited. Set when an individual style is edited
     */
    private int mEditedRow;

    /**
     * Constructor
     */
    public BooklistStylesListActivity() {
        super(null, R.layout.booklist_styles_edit_list, R.layout.booklist_styles_edit_row);
    }

    public static void startActivity(Activity from) {
        Intent i = new Intent(from, BooklistStylesListActivity.class);
        from.startActivityForResult(i, UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLES);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            this.setTitle(R.string.preferred_styles);

            // We want context menus to be available
            registerForContextMenu(getListView());

            if (savedInstanceState == null) {
                HintManager.displayHint(this, R.string.hint_booklist_styles_editor, null);
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Override
    @NonNull
    protected ArrayList<BooklistStyle> getList() {
        ArrayList<BooklistStyle> styles = new ArrayList<>();
        // get the preferred styles first
        for (BooklistStyle s : BooklistStyles.getAllStyles(this.mDb)) {
            styles.add(s);
        }
        return styles;
    }

    /**
     * Required, not used
     */
    @Override
    protected void onAdd(View v) {
    }

    @Override
    protected void onSetupView(@NonNull final View target, @NonNull final BooklistStyle style) {
        Holder holder;
        holder = ViewTagger.getTag(target, R.id.TAG_HOLDER);
        if (holder == null) {
            holder = new Holder();
            holder.preferred = target.findViewById(R.id.preferred);
            holder.name = target.findViewById(R.id.name);
            holder.groups = target.findViewById(R.id.groups);
            holder.kind = target.findViewById(R.id.kind);
            // Tag relevant views
            ViewTagger.setTag(holder.preferred, R.id.TAG_HOLDER, holder);
            ViewTagger.setTag(target, R.id.TAG_HOLDER, holder);

            // Handle clicks on the CheckedTextView
            holder.preferred.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Holder h = ViewTagger.getTag(v, R.id.TAG_HOLDER);
                    if (h != null) {
                        boolean newStatus = !h.style.isPreferred();
                        h.style.setPreferred(newStatus);
                        h.preferred.setChecked(newStatus);
                        onListChanged();
                    }
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

//    @Override
//    protected void onListItemClick(ListView l, View v, int position, long id) {
//        super.onListItemClick(l, v, position, id);
//    }

    /**
     * TODO: is this a good idea ? wouldn't we expect a 'real' context menu ?
     * Use the RowClick to present a pseudo context menu.
     */
    @Override
    protected void onRowClick(@NonNull final View target, @NonNull final BooklistStyle style, final int position) {
        // Build the array of menu items based on the style we are editing
        final ArrayList<ContextItem> items = new ArrayList<>();
        if (style.isUserDefined()) {
            items.add(new ContextItem(R.string.delete_style, R.id.MENU_STYLE_DELETE));
            items.add(new ContextItem(R.string.edit_style, R.id.MENU_STYLE_EDIT));
        }
        items.add(new ContextItem(R.string.clone_style, R.id.MENU_STYLE_CLONE));

        // Turn the list into an array
        CharSequence[] csa = items.toArray(new CharSequence[0]);

        // Show the dialog
        final AlertDialog dialog = new AlertDialog.Builder(getLayoutInflater().getContext())
                .setItems(csa, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        switch (items.get(which).getId()) {
                            case R.id.MENU_STYLE_DELETE:
                                style.deleteFromDb(mDb);
                                setList(getList()); // Refresh the list
                                dialog.dismiss();
                                return;
                            case R.id.MENU_STYLE_EDIT:
                                editStyle(position, style, false);
                                dialog.dismiss();
                                return;
                            case R.id.MENU_STYLE_CLONE:
                                editStyle(position, style, true);
                                dialog.dismiss();
                                return;
                            default:
                                break;
                        }
                    }
                }).create();

        dialog.show();
    }

    /**
     * Edit the passed style, saving its details locally. Optionally for a clone.
     *
     * @param position    Position in list
     * @param style       Actual style
     * @param alwaysClone Force a clone, even if its already user-defined
     */
    private void editStyle(final int position, @NonNull BooklistStyle style, final boolean alwaysClone) {
        // Save the current row
        mEditedRow = position;

        if (!style.isUserDefined() || alwaysClone) {
            try {
                style = style.getClone();
                style.setRowId(0);
                style.setName(style.getDisplayName());
            } catch (Exception e) {
                Logger.logError(e);
                Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent intent = new Intent(this, BooklistStylePropertiesActivity.class);
        intent.putExtra(BooklistStylePropertiesActivity.BKEY_STYLE, style);
        startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE);
    }

    @Override
    protected void onListChanged() {
        BooklistStyles.saveMenuOrder(mList);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE:
                handleStyleResult(data);
                break;
        }
    }

    /**
     * Called after a style has been edited.
     *
     * @param data Data passed to onActivityResult
     */
    private void handleStyleResult(@Nullable final Intent data) {
        // Make sure we have a style. If not, the user must have cancelled.
        if (data == null || !data.hasExtra(BooklistStylePropertiesActivity.BKEY_STYLE)) {
            return;
        }

        try {
            BooklistStyle result = (BooklistStyle) data.getSerializableExtra(BooklistStylePropertiesActivity.BKEY_STYLE);
            if (result == null) {
                // Style was deleted. Refresh.
                setList(getList());
            } else if (mEditedRow < 0) {
                // Was added. So put at top and mark as preferred
                result.setPreferred(true);
                mList.add(0, result);
                BooklistStyles.saveMenuOrder(mList);
            } else {
                BooklistStyle origStyle = mList.get(mEditedRow);
                if (origStyle.getRowId() != result.getRowId()) {
                    if (!origStyle.isUserDefined()) {
                        // Working on a clone of a builtin style
                        if (origStyle.isPreferred()) {
                            // Replace the original row with the new one
                            mList.set(mEditedRow, result);
                            // And demote the original
                            origStyle.setPreferred(false);
                            mList.add(origStyle);
                        } else {
                            // Try to put it directly after original
                            mList.add(mEditedRow, result);
                        }
                    } else {
                        // A clone of an original. Put it directly after the original
                        mList.add(mEditedRow, result);
                    }
                    if (result.isPreferred()) {
                        BooklistStyles.saveMenuOrder(mList);
                    }
                } else {
                    mList.set(mEditedRow, result);
                }
            }
            setList(mList);
        } catch (Exception e) {
            Logger.logError(e);
            // Do our best to recover
            setList(getList());
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

    /**
     * Class used for an item in the pseudo-context menu.
     * <p>
     * Context menus don't seem to work for EditObject subclasses, perhaps because we consume click events.
     *
     * @author Philip Warner
     */
    private class ContextItem implements CharSequence {
        /**
         * String for this item
         */
        private final String mString;
        /**
         * ID of this item
         */
        private final int mId;

        /**
         * Constructor
         *
         * @param stringId ID of String for this item
         * @param id       ID of this item
         */
        ContextItem(@StringRes final int stringId, final int id) {
            mString = getString(stringId);
            mId = id;
        }

        /**
         * Return the associated string
         */
        @NonNull
        public String toString() {
            return mString;
        }

        /**
         * Get the ID
         */
        public int getId() {
            return mId;
        }

        /**
         * Use the string object to provide the CharSequence implementation
         */
        @Override
        public char charAt(final int index) {
            return mString.charAt(index);
        }

        /**
         * Use the string object to provide the CharSequence implementation
         */
        @Override
        public int length() {
            return mString.length();
        }

        /**
         * Use the string object to provide the CharSequence implementation
         */
        @Override
        public CharSequence subSequence(final int start, final int end) {
            return mString.subSequence(start, end);
        }
    }
}

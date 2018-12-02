package com.eleybourn.bookcatalogue.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SelectOneDialog {
    /**
     * Select a custom item from a list, and call handler when/if item is selected.
     */
    private static void selectItemDialog(final @NonNull LayoutInflater inflater,
                                         final @Nullable String title,
                                         final @Nullable String message,
                                         final @NonNull List<SimpleDialogItem> items,
                                         final @Nullable SimpleDialogItem selectedItem,
                                         final @NonNull SimpleDialogOnClickListener handler) {

        // Build the base dialog
        final View root = inflater.inflate(R.layout.dialog_select_one_from_list, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(inflater.getContext())
                .setView(root);

        if (title != null && !title.isEmpty()) {
            builder.setTitle(title);
        }
        // and the top message (if any)
        TextView messageView = root.findViewById(R.id.message);
        if (message != null && !message.isEmpty()) {
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
            root.findViewById(R.id.messageBottomDivider).setVisibility(View.GONE);
        }

        final AlertDialog dialog = builder.create();

        // Create the listener for each item
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(final @NonNull View view) {
                SimpleDialogItem item = ViewTagger.getTag(view, R.id.TAG_DIALOG_ITEM);
                // For a consistent UI, make sure the selector is checked as well.
                // NOT mandatory from a functional point of view, just consistent
                if (item != null && !(view instanceof Checkable)) {
                    CompoundButton btn = item.getSelector(view);
                    if (btn != null) {
                        btn.setChecked(true);
                        btn.invalidate();
                    }
                }
                dialog.dismiss();
                if (item != null) {
                    handler.onClick(item);
                }
            }
        };

        // Add the items to the dialog
        ViewGroup list = root.findViewById(android.R.id.list);
        for (SimpleDialogItem item : items) {
            View view = item.getView(inflater);
            view.setOnClickListener(listener);
            view.setBackgroundResource(android.R.drawable.list_selector_background);

            ViewTagger.setTag(view, R.id.TAG_DIALOG_ITEM, item);

            CompoundButton btn = item.getSelector(view);
            if (btn != null) {
                btn.setVisibility(View.VISIBLE);
                ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
                btn.setChecked(item == selectedItem);
                btn.setOnClickListener(listener);
            }
            list.addView(view);
        }
        dialog.show();
    }

    /**
     * Wrapper class to present a list of files for selection
     *
     * @see #selectItemDialog
     */
    public static void selectFileDialog(final @NonNull LayoutInflater inflater,
                                        final @Nullable String title,
                                        final @NonNull List<File> files,
                                        final @NonNull SimpleDialogOnClickListener handler) {
        List<SimpleDialogItem> items = new ArrayList<>();
        for (File file : files) {
            items.add(new SimpleDialogFileItem(file));
        }
        selectItemDialog(inflater, title, null, items, null, handler);
    }

    /**
     * Wrapper class to present a list of objects for selection.
     *
     * The objects get wrapped in {@link SimpleDialogObjectItem} which provides a RadioButton selector
     * for each row.
     *
     * @param <T> type of object
     *
     * @see #selectItemDialog
     */
    public static <T> void selectObjectDialog(final @NonNull LayoutInflater inflater,
                                              final @Nullable String title,
                                              final @NonNull Fields.Field field,
                                              final @NonNull List<T> list,
                                              final @NonNull SimpleDialogOnClickListener handler) {
        List<SimpleDialogItem> items = new ArrayList<>();
        SimpleDialogItem selectedItem = null;
        for (T listEntry : list) {
            SimpleDialogItem item = new SimpleDialogObjectItem(field, listEntry);
            if (listEntry.equals(field.getValue()))
                selectedItem = item;
            items.add(item);
        }
        selectItemDialog(inflater, title, null, items, selectedItem, handler);
    }

    /**
     * Wrapper class to present a {@link Menu} *with* icons
     *
     * @see #selectItemDialog
     */
    public static void showContextMenuDialog(final @NonNull LayoutInflater inflater,
                                             final @NonNull SimpleDialogMenuInfo menuInfo,
                                             final @NonNull Menu menu,
                                             final @NonNull SimpleDialogOnClickListener handler) {
        List<SimpleDialogItem> items = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            items.add(new SimpleDialogMenuItem(item));
        }
        selectItemDialog(inflater, menuInfo.title, null, items, null, handler);
    }

    /**
     * Interface for item that displays in a custom dialog list
     */
    public interface SimpleDialogItem {
        @NonNull
        View getView(final @NonNull LayoutInflater inflater);

        /** optional, for visual effects only */
        @Nullable
        CompoundButton getSelector(View v);
    }

    /**
     * Interface to listen for item selection in a custom dialog list
     */
    public interface SimpleDialogOnClickListener {
        void onClick(final @NonNull SimpleDialogItem item);
    }

    /** Marker interface to indicate the {@link BaseListActivity} has a {@link ListView} using this type of context menu */
    public interface hasListViewContextMenu {
        void initListViewContextMenuListener(final @NonNull Context context);

        void onCreateListViewContextMenu(final @NonNull Menu menu,
                                         final @NonNull View view,
                                         final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo);

        @SuppressWarnings("UnusedReturnValue")
        boolean onListViewContextItemSelected(final @NonNull MenuItem menuItem,
                                              final @NonNull SimpleDialogMenuInfo menuInfo);
    }

    /** Marker interface to indicate the {@link BaseActivity} has a {@link View} using this type of context menu */
    public interface hasViewContextMenu {
        void initViewContextMenuListener(final @NonNull Context context, final @NonNull View view);

        void onCreateViewContextMenu(final @NonNull Menu menu,
                                     final @NonNull View view,
                                     final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo);

        @SuppressWarnings("UnusedReturnValue")
        boolean onViewContextItemSelected(final @NonNull MenuItem menuItem,
                                          final @NonNull View view);
    }

    /**
     * Item to manage a "String+Icon" / Menu item in a list of items.
     */
    public static class SimpleDialogMenuItem implements SimpleDialogItem {
        private final MenuItem mMenuItem;

        /**
         * @param menuItem a standard ContextMenu MenuItem.
         */
        SimpleDialogMenuItem(final @NonNull MenuItem menuItem) {
            mMenuItem = menuItem;
        }

        @NonNull
        public MenuItem getMenuItem() {
            return mMenuItem;
        }

        @Override
        @NonNull
        public View getView(final @NonNull LayoutInflater inflater) {
            @SuppressLint("InflateParams") View root = inflater.inflate(R.layout.row_simple_dialog_list_item, null);
            TextView line = root.findViewById(R.id.name);
            line.setText(mMenuItem.getTitle());
            Drawable icon = mMenuItem.getIcon();
            Drawable subMenuPointer = null;
            if (mMenuItem.hasSubMenu()) {
                subMenuPointer = inflater.getContext().getDrawable(R.drawable.submenu_arrow_nofocus);
            }
            line.setCompoundDrawablesWithIntrinsicBounds(icon, null, subMenuPointer, null);
            return root;
        }

        @Nullable
        public CompoundButton getSelector(final @NonNull View view) {
            return null;
        }
    }

    /**
     * Simple item to manage a File object in a list of items.
     */
    public static class SimpleDialogFileItem implements SimpleDialogItem {
        @NonNull
        private final File mFile;

        SimpleDialogFileItem(final @NonNull File file) {
            mFile = file;
        }

        @NonNull
        public File getFile() {
            return mFile;
        }

        /**
         * Get a View to display the file
         */
        @Override
        @NonNull
        public View getView(final @NonNull LayoutInflater inflater) {
            @SuppressLint("InflateParams") View root = inflater.inflate(R.layout.dialog_file_list_item, null);
            TextView name = root.findViewById(R.id.name);
            name.setText(mFile.getName());

            // Set the path
            TextView location = root.findViewById(R.id.path);
            location.setText(mFile.getParent());
            // Set the size
            TextView size = root.findViewById(R.id.size);
            size.setText(Utils.formatFileSize(mFile.length()));
            // Set the last modified date
            TextView update = root.findViewById(R.id.updated);
            update.setText(DateUtils.toPrettyDateTime(new Date(mFile.lastModified())));
            // Return it
            return root;
        }

        @Override
        @Nullable
        public CompoundButton getSelector(final View v) {
            return null;
        }
    }

    /**
     * Item to manage a Field value in a list of items.
     *
     * Uses the {@link Fields.FieldFormatter}, if the Field has one.
     */
    private static class SimpleDialogObjectItem implements SimpleDialogItem {

        @NonNull
        final Fields.Field mField;

        @NonNull
        private final Object mRawValue;

        SimpleDialogObjectItem( final @NonNull Fields.Field field, final @NonNull Object value) {
            mField = field;
            mRawValue = value;
        }

        /**
         * Get a View to display the object
         */
        @Override
        @NonNull
        public View getView(final @NonNull LayoutInflater inflater) {
            @SuppressLint("InflateParams") View root = inflater.inflate(R.layout.row_simple_dialog_list_item, null);
            TextView name = root.findViewById(R.id.name);
            name.setText(mField.format(mRawValue.toString()));
            return root;
        }

        @NonNull
        public CompoundButton getSelector(final @NonNull View view) {
            return (CompoundButton) view.findViewById(R.id.selector);
        }

        /**
         * Get the underlying object as a string
         */
        @Override
        public String toString() {
            return mRawValue.toString();
        }
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    public static class SimpleDialogMenuInfo implements ContextMenu.ContextMenuInfo {
        /**
         * The child view for which the context menu is being displayed. This
         * will be one of the children of this MenuItem's list.
         */
        public final View targetView;

        /**
         * The position in MenuItem's list for which the context menu is being
         * displayed.
         */
        public final int position;

        /**
         * The title that can be used as the menu header
         */
        public String title;

        public SimpleDialogMenuInfo(final @NonNull String title,
                                    final @NonNull View targetView,
                                    final int position) {
            this.targetView = targetView;
            this.position = position;
            this.title = title;
        }
    }
}

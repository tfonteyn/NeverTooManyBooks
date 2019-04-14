package com.eleybourn.bookcatalogue.dialogs;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

//TOMF: DialogFragment?
public final class SimpleDialog {

    private SimpleDialog() {
    }

    /**
     * Select a custom item from a list, and call handler when/if item is selected.
     *
     * @param title          for the dialog
     * @param message        optional message to display (or null for none)
     * @param items          list to choose from
     * @param selectedItem   pre-selected item (or null for none)
     * @param resultListener which will receive the selected row item
     * @param <T>            type of the actual Object that is represented by a row in the
     *                       selection list.
     */
    private static <T> void selectItemDialog(@NonNull final LayoutInflater inflater,
                                             @Nullable final String title,
                                             @Nullable final String message,
                                             @NonNull final List<SimpleDialogItem<T>> items,
                                             @Nullable final SimpleDialogItem<T> selectedItem,
                                             @NonNull final OnClickListener<T> resultListener) {

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
        View.OnClickListener viewListener = v -> {
            //noinspection unchecked
            SimpleDialogItem<T> item = (SimpleDialogItem<T>) v.getTag(R.id.TAG_DIALOG_ITEM);
            // For a consistent UI, make sure the selector is checked as well.
            // NOT mandatory from a functional point of view, just consistent
            if (item != null && !(v instanceof Checkable)) {
                CompoundButton btn = item.getSelector(v);
                if (btn != null) {
                    btn.setChecked(true);
                    btn.invalidate();
                }
            }
            dialog.dismiss();
            if (item != null) {
                resultListener.onClick(item);
            }
        };

        // Add the items to the dialog
        ViewGroup list = root.findViewById(R.id.item_list);
        for (SimpleDialogItem<T> item : items) {
            View view = item.getView(inflater);
            view.setOnClickListener(viewListener);

            view.setTag(R.id.TAG_DIALOG_ITEM, item);

            CompoundButton buttonView = item.getSelector(view);
            if (buttonView != null) {
                buttonView.setVisibility(View.VISIBLE);
                buttonView.setTag(R.id.TAG_DIALOG_ITEM, item);
                buttonView.setChecked(item.equals(selectedItem));
                buttonView.setOnClickListener(viewListener);
            }
            list.addView(view);
        }
        dialog.show();
    }

    /**
     * Present a list of files for selection.
     *
     * @param title          for the dialog
     * @param files          list to choose from
     * @param resultListener which will receive the selected row item
     *
     * @see #selectItemDialog
     */
    public static void selectFileDialog(@NonNull final LayoutInflater inflater,
                                        @Nullable final String title,
                                        @NonNull final List<File> files,
                                        @NonNull final OnClickListener<File> resultListener) {
        List<SimpleDialogItem<File>> items = new ArrayList<>();
        for (File file : files) {
            items.add(new SimpleDialogFileItem(file));
        }
        selectItemDialog(inflater, title, null, items, null, resultListener);
    }

    /**
     * Present a list of objects for selection.
     *
     * @param field to get/set
     * @param list  list to choose from
     * @param <T>   type of the actual Object that is represented by a row in the selection list.
     *
     * @see #selectItemDialog
     */
    public static <T> void selectFieldDialog(@NonNull final LayoutInflater inflater,
                                             @Nullable final String title,
                                             @NonNull final Fields.Field field,
                                             @NonNull final List<T> list) {
        List<SimpleDialogItem<T>> items = new ArrayList<>();
        SimpleDialogItem<T> selectedItem = null;
        for (T listEntry : list) {
            SimpleDialogItem<T> item = new SimpleDialogFieldFormattedItem<>(field, listEntry);
            if (listEntry.equals(field.getValue())) {
                selectedItem = item;
            }
            items.add(item);
        }

        OnClickListener<T> resultListener = item -> field.setValue(item.getItem().toString());
        selectItemDialog(inflater, title, null, items, selectedItem, resultListener);
    }

    /**
     * Present a context {@link Menu} *with* icons.
     *
     * @param title          for the menu header
     * @param menu           the menu
     * @param resultListener which will receive the selected row item
     *
     * @see #selectItemDialog
     */
    private static void showContextMenu(@NonNull final LayoutInflater inflater,
                                        @NonNull final String title,
                                        @NonNull final Menu menu,
                                        @NonNull final OnClickListener<MenuItem> resultListener) {
        List<SimpleDialogItem<MenuItem>> items = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            items.add(new SimpleDialogMenuItem(menu.getItem(i)));
        }
        selectItemDialog(inflater, title, null, items, null, resultListener);
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    public static void onCreateListViewContextMenu(@NonNull final View view,
                                                   @NonNull final Menu menu,
                                                   @NonNull final String title,
                                                   final int position,
                                                   @NonNull final ListViewContextMenu handler) {
        if (menu.size() > 0) {
            showContextMenu(LayoutInflater.from(view.getContext()), title, menu, item -> {
                MenuItem menuItem = item.getItem();
                if (menuItem.hasSubMenu()) {
                    // recursive call for sub-menu
                    onCreateListViewContextMenu(view,
                                                menuItem.getSubMenu(),
                                                menuItem.getTitle().toString(), position, handler);
                } else {
                    handler.onContextItemSelected(menuItem, position);
                }
            });
        }
    }

    /**
     * Using {@link SimpleDialog#showContextMenu} for context menus.
     */
    public static void onCreateViewContextMenu(@NonNull final View view,
                                               @NonNull final Menu menu,
                                               @NonNull final String title,
                                               @NonNull final ViewContextMenu handler) {

        if (menu.size() > 0) {
            showContextMenu(LayoutInflater.from(view.getContext()), title, menu, item -> {
                MenuItem menuItem = item.getItem();
                if (menuItem.hasSubMenu()) {
                    // recursive call for sub-menu
                    onCreateViewContextMenu(view,
                                            menuItem.getSubMenu(),
                                            menuItem.getTitle().toString(), handler);
                } else {
                    handler.onContextItemSelected(menuItem, view);
                }
            });
        }
    }

    /**
     * Interface for item that displays in a custom dialog list.
     */
    public interface SimpleDialogItem<T> {

        @NonNull
        View getView(@NonNull LayoutInflater inflater);

        /** optional, mostly for visual effects only. */
        @Nullable
        CompoundButton getSelector(@NonNull View view);

        /** @return the encapsulated item. */
        @NonNull
        T getItem();
    }

    /**
     * Interface to listen for item selection in a custom dialog list.
     */
    public interface OnClickListener<T> {

        void onClick(@NonNull SimpleDialogItem<T> item);
    }

    /**
     * Adding a context menu to a {@link ListView} items. Only a single ListView is supported.
     * <p>
     * Example code for setting up the menu:
     * <pre>
     * {@code
     * <p>
     *     //getListView().setLongClickable(true);
     *    getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
     *      @Override public boolean onItemLongClick(@NonNull final AdapterView<?> parent,
     *                                               @NonNull final View view,
     *                                               final int position, final long id) {
     *
     *          // legal trick to get an instance of Menu.
     *          mListViewContextMenu = new PopupMenu(view.getContext(), null).getMenu();
     *
     *          // POPULATE THE MENU
     *          mListViewContextMenu.add(Menu.NONE, R.id.MENU_DELETE, Menu.NONE,
     *                                   R.string.menu_delete_something)
     *                              .setIcon(R.drawable.ic_delete);
     *
     *          // display
     *          String menuTitle = mList.get(position).getTitle();
     *          SimpleDialog.onCreateListViewContextMenu(mListViewContextMenu, view, menuTitle, position,
     *                      new ListViewContextMenu() {
     *                          public boolean onContextItemSelected(@NonNull final MenuItem menuItem,
     *                                                                final int position) {
     *                          ...
     *                          }
     *                      });
     *          return true;
     *      }
     *   });
     * }
     * </pre>
     */
    public interface ListViewContextMenu {

        /**
         * @param menuItem that was selected
         * @param position of the item in the list/cursor
         *
         * @return <tt>true</tt> if the selection was handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onContextItemSelected(@NonNull MenuItem menuItem,
                                      int position);
    }

    /**
     * Adding a context menu to a {@link ListView} items.
     * Multiple views are supported; the view must be passed to every method.
     * <p>
     * Usage: see {@link ListViewContextMenu}.
     */
    public interface ViewContextMenu {

        /**
         * @param menuItem that was selected
         * @param view     on which the context menu is triggered. Passed here so we can
         *                 have multiple views with different context menus.
         *
         * @return <tt>true</tt> if the selection was handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onContextItemSelected(@NonNull MenuItem menuItem,
                                      @NonNull View view);
    }

    /**
     * Present a "String+Icon" / Menu item in a list of items.
     */
    public static class SimpleDialogMenuItem
            implements SimpleDialogItem<MenuItem> {

        private final MenuItem mMenuItem;

        /**
         * @param menuItem a standard ContextMenu MenuItem.
         */
        SimpleDialogMenuItem(@NonNull final MenuItem menuItem) {
            mMenuItem = menuItem;
        }

        @NonNull
        public MenuItem getItem() {
            return mMenuItem;
        }

        @Override
        @NonNull
        public View getView(@NonNull final LayoutInflater inflater) {
            @SuppressLint("InflateParams")
            View root = inflater.inflate(R.layout.row_simple_dialog_list_item, null);

            TextView name = root.findViewById(R.id.name);
            name.setText(mMenuItem.getTitle());

            // add a little arrow to indicate sub-menus.
            Drawable subMenuPointer = null;
            if (mMenuItem.hasSubMenu()) {
                subMenuPointer = inflater.getContext().getDrawable(R.drawable.ic_arrow_right);
            }
            name.setCompoundDrawablesWithIntrinsicBounds(mMenuItem.getIcon(), null, subMenuPointer,
                                                         null);
            return root;
        }

        @Nullable
        public CompoundButton getSelector(@NonNull final View view) {
            return null;
        }
    }

    /**
     * Present a File object in a list of items.
     */
    public static class SimpleDialogFileItem
            implements SimpleDialogItem<File> {

        @NonNull
        private final File mFile;

        SimpleDialogFileItem(@NonNull final File file) {
            mFile = file;
        }

        @NonNull
        public File getItem() {
            return mFile;
        }

        /**
         * @return a View to display the File information.
         */
        @Override
        @NonNull
        public View getView(@NonNull final LayoutInflater inflater) {
            @SuppressLint("InflateParams")
            View root = inflater.inflate(R.layout.row_file_list_item, null);

            TextView name = root.findViewById(R.id.name);
            name.setText(mFile.getName());

            TextView path = root.findViewById(R.id.path);
            path.setText(mFile.getParent());

            TextView size = root.findViewById(R.id.size);
            size.setText(Utils.formatFileSize(inflater.getContext(), mFile.length()));

            Locale locale = LocaleUtils.from(inflater.getContext());
            TextView lastModDate = root.findViewById(R.id.date);
            lastModDate.setText(DateUtils.toPrettyDateTime(locale, new Date(mFile.lastModified())));

            return root;
        }

        @Override
        @Nullable
        public CompoundButton getSelector(@NonNull final View view) {
            return null;
        }
    }

    /**
     * Present a Field value in a list of items.
     * <p>
     * Uses the {@link Fields.FieldFormatter}, if the Field has one.
     *
     * @param <FVT> Field Value Type
     */
    private static class SimpleDialogFieldFormattedItem<FVT>
            implements SimpleDialogItem<FVT> {

        @NonNull
        private final Fields.Field mField;

        @NonNull
        private final FVT mValue;

        /**
         * Constructor.
         *
         * @param field to use
         * @param value to get/set
         */
        SimpleDialogFieldFormattedItem(@NonNull final Fields.Field field,
                                       @NonNull final FVT value) {
            mField = field;
            mValue = value;
        }

        @NonNull
        public FVT getItem() {
            return mValue;
        }

        /**
         * @return a View to display the object.
         */
        @Override
        @NonNull
        public View getView(@NonNull final LayoutInflater inflater) {
            @SuppressLint("InflateParams")
            View root = inflater.inflate(R.layout.row_simple_dialog_list_item, null);

            TextView name = root.findViewById(R.id.name);
            name.setText(mField.format(mValue.toString()));

            return root;
        }

        @NonNull
        public CompoundButton getSelector(@NonNull final View view) {
            return (CompoundButton) view.findViewById(R.id.selector);
        }
    }
}

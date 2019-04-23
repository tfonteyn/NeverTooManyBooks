package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

/**
 * Provides an AlertDialog with an optional title and message.
 * The content is a list of options, behaving like a menu.
 * <p>
 * So you basically get a 'deluxe' {@link PopupMenu}.
 * <p>
 * TODO: this is deliberately not extending AlertDialog, nor integrating the constructor in the
 * static methods. Postponed to a rethink of this approach for now.
 * One of the issues is the passing around of an untyped user Object.
 */
public class PopupMenuDialog {

    private final RecyclerView mListView;
    private final AlertDialog dialog;

    private PopupMenuDialog(@NonNull final Context context,
                            @Nullable final String title,
                            @Nullable final String message) {
        // Build the base dialog
        final View root = LayoutInflater.from(context).inflate(R.layout.dialog_popupmenu, null);
        dialog = new AlertDialog.Builder(context)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .create();

        if (title != null && !title.isEmpty()) {
            dialog.setTitle(title);
        }

        TextView messageView = root.findViewById(R.id.message);
        if (message != null && !message.isEmpty()) {
            messageView.setText(message);
            messageView.setVisibility(View.VISIBLE);
        } else {
            messageView.setVisibility(View.GONE);
        }

        mListView = root.findViewById(android.R.id.list);
        mListView.setLayoutManager(new LinearLayoutManager(context));
    }

    /**
     * A context menu on a view.
     * <p>
     * The caller can create a menu with:
     * <pre>
     * {@code
     *      Menu menu = new PopupMenu(context, null).getMenu();
     * }
     * </pre>
     * and then populate it and pass it into this method.
     *
     * @param context    caller context
     * @param title      for the dialog/menu
     * @param menu       the menu options to show
     * @param userObject a reference free to set/use by the caller
     * @param handler    callback handler with the MenuItem the user chooses + the position
     */
    public static <T> void showContextMenu(@NonNull final Context context,
                                           @Nullable final String title,
                                           @NonNull final Menu menu,
                                           @NonNull final T userObject,
                                           @NonNull final OnContextItemSelected<T> handler) {
        // sanity check
        if (menu.size() > 0) {
            final PopupMenuDialog dialog = new PopupMenuDialog(context, title, null);

            final MenuItemListAdapter adapter =
                    new MenuItemListAdapter(context, menu,
                                            (menuItem) -> {
                                                dialog.dismiss();
                                                if (menuItem.hasSubMenu()) {
                                                    // recursive call for sub-menu
                                                    showContextMenu(
                                                            context,
                                                            menuItem.getTitle().toString(),
                                                            menuItem.getSubMenu(),
                                                            userObject,
                                                            handler);
                                                } else {
                                                    handler.onContextItemSelected(menuItem,
                                                                                  userObject);
                                                }
                                            });

            dialog.setAdapter(adapter, 0);
            dialog.show();
        }
    }

    /**
     * Present a list of files for selection.
     *
     * @param context caller context
     * @param title   for the dialog
     * @param files   list to choose from
     * @param handler which will receive the selected row item
     */
    public static void selectFileDialog(@NonNull final Context context,
                                        @Nullable final String title,
                                        @Nullable final String message,
                                        @NonNull final List<File> files,
                                        @NonNull final OnClickListener<File> handler) {

        final PopupMenuDialog dialog = new PopupMenuDialog(context, title, message);

        final FileItemListAdapter adapter = new FileItemListAdapter(context, files, (item) -> {
            dialog.dismiss();
            handler.onClick(item);
        });
        dialog.setAdapter(adapter, 0);
        dialog.show();
    }

    /**
     * Present a list of objects for selection.
     *
     * @param context caller context
     * @param field   to get/set
     * @param list    list to choose from
     * @param <T>     type of the actual Object that is represented by a row in the selection list.
     */
    public static <T> void selectFieldDialog(@NonNull final Context context,
                                             @Nullable final String title,
                                             @NonNull final Fields.Field field,
                                             @NonNull final List<T> list) {

        final PopupMenuDialog dialog = new PopupMenuDialog(context, title, null);

        final FieldListAdapter<T> adapter = new FieldListAdapter<>(context, field, list, (item) -> {
            dialog.dismiss();
            field.setValue(item.toString());
        });
        dialog.setAdapter(adapter, adapter.getPreSelectedPosition());
        dialog.show();
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    /**
     * @param adapter          to use
     * @param scrollToPosition position to scroll initially to. Set to 0 for no scroll.
     */
    private void setAdapter(@NonNull final RecyclerView.Adapter adapter,
                            final int scrollToPosition) {
        mListView.setAdapter(adapter);
        mListView.scrollToPosition(scrollToPosition);
    }

    /**
     * Interface to listen for item selection in a custom dialog list.
     */
    public interface OnClickListener<T> {

        void onClick(@NonNull T item);
    }


    public interface OnContextItemSelected<CT> {

        /**
         * @param menuItem   that was selected
         * @param userObject that the caller passed in when creating the context menu
         *
         * @return {@code true} if the selection was handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onContextItemSelected(@NonNull MenuItem menuItem,
                                      @NonNull CT userObject);
    }

    private static class FieldListAdapter<T>
            extends RecyclerView.Adapter<SimpleItemHolder<T>> {

        @NonNull
        private final List<T> mList;

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final Fields.Field mField;
        @NonNull
        private final OnClickListener<T> mListener;
        private int mPreSelectedPosition = -1;

        FieldListAdapter(@NonNull final Context context,
                         @NonNull final Fields.Field field,
                         @NonNull final List<T> objects,
                         @NonNull final OnClickListener<T> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            mList = objects;

            mField = field;

            int position = 0;

            for (T listEntry : objects) {
                if (listEntry.equals(field.getValue())) {
                    mPreSelectedPosition = position;
                    break;
                }
                position++;
            }
        }

        /**
         * @return the position of the original value, or -1 if none.
         */
        int getPreSelectedPosition() {
            return mPreSelectedPosition;
        }

        @NonNull
        @Override
        public SimpleItemHolder<T> onCreateViewHolder(@NonNull final ViewGroup parent,
                                                      final int viewType) {
            View root = mInflater.inflate(R.layout.row_simple_dialog_list_item, parent, false);
            return new SimpleItemHolder<>(root, mListener);
        }

        @Override
        public void onBindViewHolder(@NonNull final SimpleItemHolder<T> holder,
                                     final int position) {
            holder.item = mList.get(position);
            holder.textView.setText(mField.format(holder.item.toString()));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class MenuItemListAdapter
            extends RecyclerView.Adapter<SimpleItemHolder<MenuItem>> {

        @NonNull
        final Drawable mSubMenuPointer;
        @NonNull
        private final List<MenuItem> mList = new ArrayList<>();
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final OnClickListener<MenuItem> mListener;

        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Menu menu,
                            @NonNull final OnClickListener<MenuItem> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            for (int i = 0; i < menu.size(); i++) {
                mList.add(menu.getItem(i));
            }

            //noinspection ConstantConditions
            mSubMenuPointer = context.getDrawable(R.drawable.ic_submenu);
        }

        @NonNull
        @Override
        public SimpleItemHolder<MenuItem> onCreateViewHolder(@NonNull final ViewGroup parent,
                                                             final int viewType) {
            View root = mInflater.inflate(R.layout.row_simple_dialog_list_item, parent, false);
            return new SimpleItemHolder<>(root, mListener);
        }

        @Override
        public void onBindViewHolder(@NonNull final SimpleItemHolder<MenuItem> holder,
                                     final int position) {
            holder.item = mList.get(position);
            holder.textView.setText(holder.item.getTitle());

            // add a little arrow to indicate sub-menus.
            if (holder.item.hasSubMenu()) {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(
                        holder.item.getIcon(), null, mSubMenuPointer, null);
            } else {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(
                        holder.item.getIcon(), null, null, null);
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class SimpleItemHolder<T>
            extends ItemHolderBase<T> {

        @NonNull
        final TextView textView;

        SimpleItemHolder(@NonNull final View root,
                         @NonNull final OnClickListener<T> listener) {
            super(root, listener);
            textView = root.findViewById(R.id.menu_item);
        }
    }

    private static class FileItemListAdapter
            extends RecyclerView.Adapter<FileDetailHolder> {

        @NonNull
        final Locale mLocale;
        @NonNull
        private final List<File> mList;
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final OnClickListener<File> mListener;

        FileItemListAdapter(@NonNull final Context context,
                            @NonNull final List<File> objects,
                            @NonNull final OnClickListener<File> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            mLocale = LocaleUtils.from(context);
            mList = objects;
        }

        @NonNull
        @Override
        public FileDetailHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
            View root = mInflater.inflate(R.layout.row_file_list_item, parent, false);
            return new FileDetailHolder(root, mListener);
        }

        @Override
        public void onBindViewHolder(@NonNull final FileDetailHolder holder,
                                     final int position) {

            Context context = mInflater.getContext();

            holder.item = mList.get(position);
            holder.name.setText(holder.item.getName());
            holder.path.setText(holder.item.getParent());
            holder.size.setText(Utils.formatFileSize(context, holder.item.length()));
            holder.lastModDate.setText(DateUtils.toPrettyDateTime(mLocale,
                                                                  new Date(
                                                                          holder.item.lastModified())));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class FileDetailHolder
            extends ItemHolderBase<File>
            implements View.OnClickListener {

        @NonNull
        final TextView name;
        @NonNull
        final TextView path;
        @NonNull
        final TextView size;
        @NonNull
        final TextView lastModDate;


        FileDetailHolder(@NonNull final View root,
                         @NonNull final OnClickListener<File> listener) {
            super(root, listener);

            name = root.findViewById(R.id.name);
            path = root.findViewById(R.id.path);
            size = root.findViewById(R.id.size);
            lastModDate = root.findViewById(R.id.date);
        }
    }

    private static class ItemHolderBase<T>
            extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        @NonNull
        private final OnClickListener<T> mListener;

        /** currently bound item. */
        T item;

        ItemHolderBase(@NonNull final View itemView,
                       @NonNull final OnClickListener<T> listener) {
            super(itemView);
            itemView.setOnClickListener(this);
            mListener = listener;
        }

        @Override
        public void onClick(final View v) {
            if (item == null) {
                throw new IllegalStateException("item must be set in onBindViewHolder");
            }
            mListener.onClick(item);
        }
    }
}

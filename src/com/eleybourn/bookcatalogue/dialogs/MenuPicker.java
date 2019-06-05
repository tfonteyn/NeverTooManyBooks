package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.R;

/**
 * Show context menu on a view.
 *
 * @param <T> type of the actual Object that is represented by a row in the selection list.
 */
public class MenuPicker<T>
        extends ValuePicker {

    private MenuItemListAdapter mAdapter;

    /**
     * Constructor.
     * <p>
     * The caller can create a menu calling {@link #createMenu(Context)},
     * populate it and pass it here.
     *
     * @param context    Current context
     * @param title      for the dialog/menu
     * @param menu       the menu options to show
     * @param userObject a reference free to set/use by the caller
     * @param listener   callback handler with the MenuItem the user chooses + the position
     */
    public MenuPicker(@NonNull final Context context,
                      @Nullable final String title,
                      @NonNull final Menu menu,
                      @NonNull final T userObject,
                      @NonNull final ContextItemSelected<T> listener) {
        super(context, title, null);

        mAdapter = new MenuItemListAdapter(context, menu, (menuItem) -> {
            if (menuItem.hasSubMenu()) {
                setTitle(menuItem.getTitle());
                mAdapter.setMenu(menuItem.getSubMenu());
            } else {
                dismiss();
                listener.onContextItemSelected(menuItem, userObject);
            }
        });

        setAdapter(mAdapter, 0);
    }

    public static Menu createMenu(@NonNull final Context context) {
        // legal trick to get an instance of Menu.
        return new PopupMenu(context, null).getMenu();
    }

    public interface ContextItemSelected<T> {

        /**
         * @param menuItem   that was selected
         * @param userObject that the caller passed in when creating the context menu
         *
         * @return {@code true} if the selection was handled.
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onContextItemSelected(@NonNull MenuItem menuItem,
                                      @NonNull T userObject);
    }

    private static class MenuItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        final Drawable mSubMenuPointer;
        @NonNull
        private final List<MenuItem> mList = new ArrayList<>();
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final PickListener<MenuItem> mListener;

        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Menu menu,
                            @NonNull final PickListener<MenuItem> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            setMenu(menu);

            //noinspection ConstantConditions
            mSubMenuPointer = context.getDrawable(R.drawable.ic_submenu);
        }

        public void setMenu(@NonNull final Menu menu) {
            mList.clear();
            for (int i = 0; i < menu.size(); i++) {
                mList.add(menu.getItem(i));
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View root = mInflater.inflate(R.layout.row_simple_dialog_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            MenuItem item = mList.get(position);
            holder.textView.setText(item.getTitle());

            // add a little arrow to indicate sub-menus.
            if (item.hasSubMenu()) {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(
                        item.getIcon(), null, mSubMenuPointer, null);
            } else {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(
                        item.getIcon(), null, null, null);
            }

            // onClick on the whole view.
            holder.itemView.setOnClickListener(v -> mListener.onPicked(item));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView textView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.menu_item);
        }
    }
}

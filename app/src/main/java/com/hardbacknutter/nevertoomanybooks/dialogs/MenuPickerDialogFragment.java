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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * Show context menu on a view.
 * <p>
 * Experimental: manual menu construction fully supported,
 * using a menu inflater works but will ignore icons.
 * <p>
 * See build.gradle for app module; android/defaultConfig
 * buildConfigField("boolean", "MENU_PICKER_USES_FRAGMENT", "false")
 */
public class MenuPickerDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "MenuPickerDialogFrag";
    public static final String REQUEST_KEY = TAG + ":rk";

    private static final String BKEY_MENU = TAG + ":menu";
    private static final String BKEY_POSITION = TAG + ":pos";

    /** Cached position of the item in the list this menu was invoked on. */
    private int mPosition;

    /**
     * Constructor.
     *
     * @param title         (optional) for the dialog/menu
     * @param headerMessage (optional) message to display above the menu
     * @param pickList      the menu options to show
     * @param position      of the item in a list where the context menu was initiated
     */
    public static DialogFragment newInstance(@Nullable final String title,
                                             @Nullable final String headerMessage,
                                             @NonNull final ArrayList<Pick> pickList,
                                             final int position) {
        final DialogFragment frag = new MenuPickerDialogFragment();
        final Bundle args = new Bundle(4);
        args.putString(StandardDialogs.BKEY_DIALOG_TITLE, title);
        args.putString(StandardDialogs.BKEY_DIALOG_MESSAGE, headerMessage);
        args.putParcelableArrayList(BKEY_MENU, pickList);
        args.putInt(BKEY_POSITION, position);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Constructor - <strong>No support for icons</strong>.
     *
     * @param title         (optional) for the dialog/menu
     * @param headerMessage (optional) message to display above the menu
     * @param menu          the menu options to show
     * @param position      of the item in a list where the context menu was initiated
     *
     * @return instance
     */
    public static DialogFragment newInstance(@Nullable final String title,
                                             @Nullable final String headerMessage,
                                             @NonNull final Menu menu,
                                             final int position) {
        return newInstance(title, headerMessage, convert(menu), position);
    }

    private static ArrayList<Pick> convert(@NonNull final Menu menu) {
        final ArrayList<Pick> pickList = new ArrayList<>();
        ArrayList<Pick> subPickList;

        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            final SubMenu itemSubMenu = item.getSubMenu();
            if (itemSubMenu != null) {
                subPickList = convert(itemSubMenu);
            } else {
                subPickList = null;
            }
            final int id = item.getItemId();
            final Pick pick = new Pick(id,
                                       item.getOrder(),
                                       item.getTitle().toString(),
                                       0,
                                       subPickList);
            pickList.add(pick);
        }

        return pickList;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View root = getLayoutInflater().inflate(R.layout.dialog_popupmenu, null);

        // list of options
        final RecyclerView listView = root.findViewById(R.id.item_list);
        final TextView mMessageView = root.findViewById(R.id.message);

        //noinspection ConstantConditions
        AlertDialog mDialog = new MaterialAlertDialogBuilder(getContext())
                .setView(root)
                .create();

        final Bundle args = requireArguments();
        mPosition = args.getInt(BKEY_POSITION);

        // optional title
        final String title = args.getString(StandardDialogs.BKEY_DIALOG_TITLE);
        if (title != null && !title.isEmpty()) {
            mDialog.setTitle(title);
        }
        // Optional message
        if (mMessageView != null) {
            final String message = args.getString(StandardDialogs.BKEY_DIALOG_MESSAGE);
            if (message != null && !message.isEmpty()) {
                mMessageView.setText(message);
                mMessageView.setVisibility(View.VISIBLE);
            } else {
                mMessageView.setVisibility(View.GONE);
            }
        }

        //noinspection ConstantConditions
        final MenuItemListAdapter adapter =
                new MenuItemListAdapter(getContext(), args.getParcelableArrayList(BKEY_MENU));

        listView.setAdapter(adapter);

        return mDialog;
    }

    public interface OnResultListener
            extends FragmentResultListener {

        /* private. */ String MENU_ITEM = "menuItem";
        /* private. */ String POSITION = "position";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @IdRes final int menuItemId,
                               final int position) {
            final Bundle result = new Bundle();
            result.putInt(MENU_ITEM, menuItemId);
            result.putInt(POSITION, position);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(result.getInt(MENU_ITEM), result.getInt(POSITION));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param menuItemId that was selected
         * @param position   of the item in a list where the context menu was initiated
         *
         * @return {@code true} if handled (not used here, but needed for compatibility)
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean onResult(@IdRes int menuItemId,
                         int position);
    }

    /** Equivalent of a {@code MenuItem}. */
    public static class Pick
            implements Parcelable, Comparable<Pick> {

        /** {@link Parcelable}. */
        public static final Creator<Pick> CREATOR = new Creator<Pick>() {
            @Override
            @NonNull
            public Pick createFromParcel(@NonNull final Parcel in) {
                return new Pick(in);
            }

            @Override
            @NonNull
            public Pick[] newArray(final int size) {
                return new Pick[size];
            }
        };
        @IdRes
        private final int mItemId;
        private final int mOrder;

        private boolean mIsEnabled = true;
        private boolean mIsVisible = true;
        @NonNull
        private String mTitle;
        @Nullable
        private List<Pick> mSubMenu;
        @DrawableRes
        private int mIconId;
        @Nullable
        private Drawable mIcon;

        public Pick(@IdRes final int itemId,
                    final int order,
                    @NonNull final String title,
                    @DrawableRes final int iconId) {
            mItemId = itemId;
            mOrder = order;
            mTitle = title;
            mIconId = iconId;
        }

        public Pick(@IdRes final int itemId,
                    final int order,
                    @NonNull final String title,
                    @DrawableRes final int iconId,
                    @Nullable final List<Pick> subMenu) {
            mItemId = itemId;
            mOrder = order;
            mTitle = title;
            mIconId = iconId;
            mSubMenu = subMenu;
        }

        Pick(@NonNull final Parcel in) {
            mItemId = in.readInt();
            mOrder = in.readInt();
            mIconId = in.readInt();
            //noinspection ConstantConditions
            mTitle = in.readString();
            mSubMenu = in.createTypedArrayList(Pick.CREATOR);
            mIsEnabled = in.readByte() != 0;
            mIsVisible = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(mItemId);
            dest.writeInt(mOrder);
            dest.writeInt(mIconId);
            dest.writeString(mTitle);
            dest.writeTypedList(mSubMenu);
            dest.writeByte((byte) (mIsEnabled ? 1 : 0));
            dest.writeByte((byte) (mIsVisible ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public boolean isEnabled() {
            return mIsEnabled;
        }

        public Pick setEnabled(final boolean enabled) {
            mIsEnabled = enabled;
            return this;
        }

        public boolean isVisible() {
            return mIsVisible;
        }

        public Pick setVisible(final boolean visible) {
            mIsVisible = visible;
            return this;
        }

        public int getItemId() {
            return mItemId;
        }

        @NonNull
        public String getTitle() {
            return mTitle;
        }

        public Pick setTitle(@NonNull final String title) {
            mTitle = title;
            return this;
        }

        @NonNull
        Drawable getIcon(@NonNull final Context context) {
            if (mIcon == null) {
                mIcon = context.getDrawable(mIconId);
                Objects.requireNonNull(mIcon, ErrorMsg.NULL_DRAWABLE);
            }
            return mIcon;
        }

        public Pick setIcon(@DrawableRes final int iconId) {
            mIconId = iconId;
            return this;
        }

        boolean hasSubMenu() {
            return mSubMenu != null;
        }

        @NonNull
        List<Pick> getSubMenu() {
            Objects.requireNonNull(mSubMenu, ErrorMsg.NULL_MENU);
            return mSubMenu;
        }

        @Override
        public int compareTo(final Pick o) {
            return Integer.compare(mOrder, o.mOrder);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pick)) {
                return false;
            }
            return ((Pick) o).mItemId == mItemId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mItemId);
        }
    }

    /**
     * Row ViewHolder for {@link MenuItemListAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @Nullable
        final TextView textView;

        Holder(@NonNull final View itemView,
               final int viewType) {
            super(itemView);

            if (viewType == MenuItemListAdapter.MENU_ITEM) {
                textView = itemView.findViewById(R.id.menu_item);
            } else {
                textView = null;
            }
        }
    }

    private class MenuItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        /** ViewType. */
        static final int MENU_DIVIDER = 0;
        /** ViewType. */
        static final int MENU_ITEM = 1;

        @NonNull
        private final Drawable mSubMenuPointer;
        @NonNull
        private final List<Pick> mList = new ArrayList<>();
        @NonNull
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context    Current context
         * @param choiceList Menu (list of items) to display
         */
        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Iterable<Pick> choiceList) {

            mInflater = LayoutInflater.from(context);
            setMenu(choiceList);

            //noinspection ConstantConditions
            mSubMenuPointer = context.getDrawable(R.drawable.ic_submenu);
        }

        /**
         * Add all choices items to the adapter list.
         * Invisible items are <strong>not added</strong>,
         * disabled items are added and will be shown disabled.
         *
         * @param choiceList to add.
         */
        void setMenu(@NonNull final Iterable<Pick> choiceList) {
            mList.clear();
            for (Pick item : choiceList) {
                if (item.isVisible()) {
                    mList.add(item);
                }
            }
            // apply the Pick order
            Collections.sort(mList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View root;
            if (viewType == MENU_ITEM) {
                root = mInflater.inflate(R.layout.row_simple_list_item, parent, false);
            } else {
                root = mInflater.inflate(R.layout.row_simple_list_divider, parent, false);
            }
            return new Holder(root, viewType);
        }

        @Override
        public int getItemViewType(final int position) {
            if (mList.get(position).getItemId() != R.id.MENU_DIVIDER) {
                return MENU_ITEM;
            } else {
                return MENU_DIVIDER;
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            if (holder.textView != null) {
                final Pick item = mList.get(position);
                holder.textView.setText(item.getTitle());

                // add a little arrow to indicate sub-menus.
                if (item.hasSubMenu()) {
                    //noinspection ConstantConditions
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(getContext()), null, mSubMenuPointer, null);
                } else {
                    //noinspection ConstantConditions
                    holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            item.getIcon(getContext()), null, null, null);
                }

                holder.textView.setEnabled(item.isEnabled());
                if (item.isEnabled()) {
                    holder.textView.setOnClickListener(v -> {
                        if (item.hasSubMenu()) {
                            //noinspection ConstantConditions
                            getDialog().setTitle(item.getTitle());
                            setMenu(item.getSubMenu());
                        } else {

                            OnResultListener.sendResult(MenuPickerDialogFragment.this, REQUEST_KEY,
                                                        item.getItemId(), mPosition);

                            dismiss();
                        }
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}

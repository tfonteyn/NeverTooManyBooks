/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.annotation.SuppressLint;
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Show context menu on a view.
 * <p>
 * Experimental: manual menu construction fully supported.
 * When using a menu inflater we use reflection to read the icon id... this is a BAD idea...
 * <p>
 * See build.gradle for app module; android/defaultConfig
 * buildConfigField("boolean", "MENU_PICKER_USES_FRAGMENT", "false")
 */
public class MenuPickerDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "MenuPickerDialogFrag";

    private static final String BKEY_MENU = TAG + ":menu";
    private static final String BKEY_TITLE = TAG + ":title";
    private static final String BKEY_MESSAGE = TAG + ":message";
    private static final String BKEY_POSITION = TAG + ":pos";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** Cached position of the item in the list this menu was invoked on. */
    private int mPosition;

    /**
     * Constructor.
     */
    public MenuPickerDialogFragment() {
        super(R.layout.dialog_popupmenu);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mPosition = args.getInt(BKEY_POSITION);

        final Iterable<Pick> menu = args.getParcelableArrayList(BKEY_MENU);

        // optional title
        final String title = args.getString(BKEY_TITLE);
        final TextView titleView = view.findViewById(R.id.alertTitle);
        if (title != null && !title.isEmpty()) {
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(title);
        } else {
            titleView.setVisibility(View.GONE);
        }

        // optional message
        final String message = args.getString(BKEY_MESSAGE);
        final TextView messageView = view.findViewById(R.id.alertMessage);
        if (message != null && !message.isEmpty()) {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
        }

        //noinspection ConstantConditions
        final MenuItemListAdapter adapter = new MenuItemListAdapter(getContext(), menu);
        final RecyclerView listView = view.findViewById(R.id.item_list);
        listView.setAdapter(adapter);
    }

    public abstract static class Launcher
            extends DialogFragmentLauncherBase {

        private static final String MENU_ITEM = "menuItem";
        private static final String POSITION = "position";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @IdRes final int menuItemId,
                               final int position) {
            final Bundle result = new Bundle(2);
            result.putInt(MENU_ITEM, menuItemId);
            result.putInt(POSITION, position);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
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

                pickList.add(new Pick(item, subPickList));
            }

            return pickList;
        }

        /**
         * Launch the dialog.
         *
         * @param title    (optional) for the dialog/menu
         * @param message  (optional) for the dialog/menu
         * @param menu     the menu options to show
         * @param position of the item in a list where the context menu was initiated
         */
        public void launch(@Nullable final String title,
                           @Nullable final String message,
                           @NonNull final ArrayList<Pick> menu,
                           final int position) {

            final Bundle args = new Bundle(4);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(BKEY_TITLE, title);
            args.putString(BKEY_MESSAGE, message);
            args.putParcelableArrayList(BKEY_MENU, menu);
            args.putInt(BKEY_POSITION, position);

            final DialogFragment frag = new MenuPickerDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, MenuPickerDialogFragment.TAG);
        }

        /**
         * Launch the dialog.
         *
         * @param title    (optional) for the dialog/menu
         * @param message  (optional) for the dialog/menu
         * @param menu     the menu options to show
         * @param position of the item in a list where the context menu was initiated
         */
        public void launch(@Nullable final String title,
                           @Nullable final String message,
                           @NonNull final Menu menu,
                           final int position) {
            launch(title, message, convert(menu), position);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getInt(MENU_ITEM),
                     result.getInt(POSITION));
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
        public abstract boolean onResult(@IdRes int menuItemId,
                                         int position);
    }

    /** An incomplete, but "just enough", implementation of a {@code MenuItem}. */
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

        private boolean mIsEnabled;
        private boolean mIsVisible;
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
            mIsEnabled = true;
            mIsVisible = true;
        }

        public Pick(@NonNull final MenuItem item,
                    @Nullable final List<Pick> subMenu) {
            mItemId = item.getItemId();
            mOrder = item.getOrder();
            mTitle = item.getTitle().toString();

            // Using reflection to read the icon id... this is a BAD idea...
            try {
                final Field iconIdField = item.getClass().getDeclaredField("mIconResId");
                iconIdField.setAccessible(true);
                mIconId = iconIdField.getInt(item);
            } catch (@NonNull final NoSuchFieldException | IllegalAccessException ignore) {
                // ignore
            }

            mSubMenu = subMenu;

            mIsEnabled = item.isEnabled();
            mIsVisible = item.isVisible();
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

        /**
         * NOT supported after (the list of) the Pick item has been passed to the adapter.
         */
        public Pick setVisible(final boolean visible) {
            mIsVisible = visible;
            return this;
        }

        @IdRes
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

        @SuppressLint("UseCompatLoadingForDrawables")
        @Nullable
        Drawable getIcon(@NonNull final Context context) {
            if (mIcon == null && mIconId != 0) {
                mIcon = Objects.requireNonNull(context.getDrawable(mIconId),
                                               String.valueOf(mIconId));
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
            return Objects.requireNonNull(mSubMenu, "mSubMenu");
        }

        @Override
        public int compareTo(final Pick o) {
            return Integer.compare(mOrder, o.mOrder);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
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
        /** Cached inflater. */
        @NonNull
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param menu    Menu (list of items) to display
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        MenuItemListAdapter(@NonNull final Context context,
                            @NonNull final Iterable<Pick> menu) {

            mInflater = LayoutInflater.from(context);
            setMenu(menu);

            //noinspection ConstantConditions
            mSubMenuPointer = context.getDrawable(R.drawable.ic_submenu);
        }

        /**
         * Add all menu items to the adapter list.
         * Invisible items are <strong>not added</strong>,
         * disabled items are added and will be shown disabled.
         *
         * @param menu to add.
         */
        void setMenu(@NonNull final Iterable<Pick> menu) {
            mList.clear();
            for (final Pick item : menu) {
                if (item.isVisible()) {
                    mList.add(item);
                }
            }
            // apply the Pick order
            Collections.sort(mList);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(final int position) {
            if (mList.get(position).getItemId() != R.id.MENU_DIVIDER) {
                return MENU_ITEM;
            } else {
                return MENU_DIVIDER;
            }
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
            final Holder holder = new Holder(root, viewType);
            if (holder.textView != null) {
                holder.textView.setOnClickListener(v -> onItemClicked(holder));
            }
            return holder;
        }

        void onItemClicked(@NonNull final Holder holder) {
            final Pick item = mList.get(holder.getBindingAdapterPosition());
            if (item.isEnabled()) {
                if (item.hasSubMenu()) {
                    //noinspection ConstantConditions
                    getDialog().setTitle(item.getTitle());
                    setMenu(item.getSubMenu());
                } else {
                    dismiss();
                    Launcher.sendResult(MenuPickerDialogFragment.this,
                                        mRequestKey, item.getItemId(), mPosition);
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            if (holder.textView != null) {
                final Pick item = mList.get(position);
                holder.textView.setEnabled(item.isEnabled());

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
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}

/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.databinding.PopupMenuBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class BottomSheetMenu
        extends BottomSheetDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "BottomSheetMenu";
    private static final String BKEY_TITLE = TAG + ":t";
    private static final String BKEY_MESSAGE = TAG + ":msg";
    private static final String BKEY_MENU = TAG + ":menu";
    private static final String BKEY_MENU_OWNER = TAG + ":owner";

    private PopupMenuBinding vb;
    private List<ExtMenuItem> menuList;
    private String title;
    private String message;
    private String requestKey;
    private int menuOwner;
    private final MenuItemListAdapter.MenuCallback menuCallback =
            new MenuItemListAdapter.MenuCallback() {
                @Override
                public void onNewMenuTitle(@NonNull final CharSequence title) {
                    vb.title.setText(title);
                    vb.title.setVisibility(View.VISIBLE);
                }

                @Override
                public void onMenuItemClick(@IdRes final int menuItemId) {
                    BottomSheetMenu.this.dismiss();
                    Launcher.setResult(BottomSheetMenu.this, requestKey, menuOwner, menuItemId);
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);

        title = args.getString(BKEY_TITLE);
        message = args.getString(BKEY_MESSAGE);
        menuList = args.getParcelableArrayList(BKEY_MENU);
        menuOwner = args.getInt(BKEY_MENU_OWNER);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = PopupMenuBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        if (title != null) {
            vb.title.setText(title);
            vb.title.setVisibility(View.VISIBLE);
        }
        if (message != null) {
            vb.message.setText(message);
            vb.message.setVisibility(View.VISIBLE);
        }

        //noinspection DataFlowIssue
        final MenuItemListAdapter adapter = new MenuItemListAdapter(getContext(), menuCallback);
        adapter.setMenu(menuList);
        vb.itemList.setAdapter(adapter);
    }

    public static class Launcher
            extends DialogLauncher {

        private static final String MENU_ITEM = TAG + ":mi";

        @NonNull
        private final ResultListener resultListener;

        /**
         * Constructor.
         *
         * @param requestKey     FragmentResultListener request key to use for our response.
         * @param resultListener listener
         */
        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            super(requestKey, BottomSheetMenu::new);
            this.resultListener = resultListener;
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment   the calling DialogFragment
         * @param requestKey to use
         * @param menuItemId The menu item that was invoked.
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              final int menuOwner,
                              @IdRes final int menuItemId) {
            final Bundle result = new Bundle(2);
            result.putInt(BKEY_MENU_OWNER, menuOwner);
            result.putInt(MENU_ITEM, menuItemId);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param adapterPosition     the position where the menu originates from
         * @param menuTitle           optional menu title
         * @param message             optional message
         * @param menu                to display
         * @param groupDividerEnabled flag
         */
        public void launch(final int adapterPosition,
                           @Nullable final CharSequence menuTitle,
                           @Nullable final CharSequence message,
                           @NonNull final Menu menu,
                           final boolean groupDividerEnabled) {

            final ArrayList<ExtMenuItem> items = ExtMenu.convert(menu, groupDividerEnabled);

            final Bundle args = new Bundle(5);
            args.putInt(BottomSheetMenu.BKEY_MENU_OWNER, adapterPosition);

            if (menuTitle != null) {
                args.putString(BottomSheetMenu.BKEY_TITLE, menuTitle.toString());
            }
            if (message != null) {
                args.putString(BottomSheetMenu.BKEY_MESSAGE, message.toString());
            }
            args.putParcelableArrayList(BottomSheetMenu.BKEY_MENU, items);

            createDialog(args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(result.getInt(BKEY_MENU_OWNER),
                                    result.getInt(MENU_ITEM));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler.
             *
             * @param menuOwner  id or adapter-row for the View/item which owns the menu
             * @param menuItemId The menu item that was invoked.
             */
            void onResult(int menuOwner,
                          @IdRes int menuItemId);
        }
    }
}

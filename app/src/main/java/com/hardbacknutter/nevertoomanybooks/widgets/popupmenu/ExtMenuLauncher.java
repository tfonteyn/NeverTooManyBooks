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

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class ExtMenuLauncher
        extends DialogLauncher {

    private static final String TAG = "ExtMenuLauncher";
    static final String BKEY_TITLE = TAG + ":t";
    static final String BKEY_MESSAGE = TAG + ":msg";
    static final String BKEY_MENU = TAG + ":menu";
    /**
     * Typically the adapter-position (includes {@code 0}) for the View/item which
     * owns the menu. But can also be a generic id.
     */
    static final String BKEY_MENU_OWNER = TAG + ":owner";
    private static final String RESULT_MENU_ITEM = TAG + ":mi";

    @NonNull
    private final ExtMenuResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    public ExtMenuLauncher(@NonNull final String requestKey,
                           @NonNull final ExtMenuResultListener resultListener) {
        super(requestKey,
              // We ONLY use a BottomSheet here as the dialog is done by using a PopupWindow
              ExtMenuBottomSheet::new,
              ExtMenuBottomSheet::new);
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
        result.putInt(RESULT_MENU_ITEM, menuItemId);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param context             preferably the {@code Activity}
     *                            but another UI {@code Context} will also do.
     * @param menuTitle           optional menu title
     * @param message             optional message
     * @param menuOwner           Typically the adapter-position (includes {@code 0}) for
     *                            the View/item which owns the menu.
     *                            But can also be a generic id.
     *                            It will be passed back as the first argument of
     *                            {@link ExtMenuResultListener#onMenuItemClick(int, int)}.
     * @param menu                to display
     * @param groupDividerEnabled flag
     */
    public void launch(@NonNull final Context context,
                       @Nullable final CharSequence menuTitle,
                       @Nullable final CharSequence message,
                       final int menuOwner,
                       @NonNull final Menu menu,
                       final boolean groupDividerEnabled) {

        final ArrayList<ExtMenuItem> items = ExtMenu.convert(menu, groupDividerEnabled);

        final Bundle args = new Bundle(5);
        args.putInt(BKEY_MENU_OWNER, menuOwner);

        if (menuTitle != null) {
            args.putString(BKEY_TITLE, menuTitle.toString());
        }
        if (message != null) {
            args.putString(BKEY_MESSAGE, message.toString());
        }
        args.putParcelableArrayList(BKEY_MENU, items);

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onMenuItemClick(result.getInt(BKEY_MENU_OWNER),
                                       result.getInt(RESULT_MENU_ITEM));
    }
}

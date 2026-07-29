/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;

public class ExtMenuLauncher
        extends DialogLauncher {

    private static final String TAG = "ExtMenuLauncher";
    private static final String RESULT_MENU_ITEM = TAG + ":mi";
    private static final String RESULT_MENU_OWNER = TAG + ":owner";

    @NonNull
    private final ExtMenuResultListener resultListener;
    private boolean groupDividerEnabled = true;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    public ExtMenuLauncher(@NonNull final String requestKey,
                           @NonNull final ExtMenuResultListener resultListener) {
        super(requestKey,
              // We ONLY use a BottomSheet as the dialog is done by using a PopupWindow
              ExtMenuBottomSheet::new,
              ExtMenuBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Change the flag to enable the group dividers.
     * <p>
     * The default is {@code true}.
     *
     * @param groupDividerEnabled flag
     */
    public void setGroupDividerEnabled(final boolean groupDividerEnabled) {
        this.groupDividerEnabled = groupDividerEnabled;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param menuOwner  as was passed into {@link #launch}
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
        result.putInt(RESULT_MENU_OWNER, menuOwner);
        result.putInt(RESULT_MENU_ITEM, menuItemId);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog/menu.
     *
     * @param anchor              the anchor for {@link MenuMode#Anchored},
     *                            or a view from which the window token can be used
     *                            for the other modes
     * @param menuTitle           optional menu title
     * @param message             optional message
     * @param menuOwner           Typically the adapter-position (includes {@code 0}) for
     *                            the View/item which owns the menu.
     *                            But can also be a generic id.
     *                            It will be passed back as the first argument of
     *                            {@link ExtMenuResultListener#onMenuItemClick(int, int)}.
     * @param menu                to display
     */
    public void launch(@NonNull final View anchor,
                       @Nullable final CharSequence menuTitle,
                       @Nullable final CharSequence message,
                       final int menuOwner,
                       @NonNull final Menu menu) {
        final Context context = anchor.getContext();

        final MenuMode menuMode = MenuMode.getMode(context, menu);
        if (menuMode.isPopup()) {
            new ExtMenuPopupWindow(context)
                    .setTitle(menuTitle)
                    .setMessage(message)
                    .setMenuOwner(menuOwner)
                    .setMenu(menu, groupDividerEnabled)
                    .setListener(resultListener)
                    .show(anchor, menuMode);
        } else {
            final ArrayList<ExtMenuItem> items = ExtMenuItem.convert(menu, groupDividerEnabled);

            final ExtMenuInput input = new ExtMenuInput(
                    getRequestKey(), menuTitle, message, menuOwner, items);
            showDialog(context, input.toBundle());
        }
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onMenuItemClick(result.getInt(RESULT_MENU_OWNER),
                                       result.getInt(RESULT_MENU_ITEM));
    }
}

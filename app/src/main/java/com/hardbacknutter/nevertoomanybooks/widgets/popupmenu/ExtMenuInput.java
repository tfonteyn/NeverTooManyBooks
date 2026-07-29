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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class ExtMenuInput {
    private static final String TAG = "ExtMenuInput";

    private static final String BKEY_TITLE = TAG + ":t";
    private static final String BKEY_MESSAGE = TAG + ":msg";
    private static final String BKEY_MENU = TAG + ":menu";
    /**
     * Typically the adapter-position (includes {@code 0}) for the View/item which
     * owns the menu. But can also be a generic id.
     */
    private static final String BKEY_MENU_OWNER = TAG + ":owner";

    @NonNull
    private final String requestKey;
    @Nullable
    private final CharSequence menuTitle;
    @Nullable
    private final CharSequence message;
    private final int menuOwner;
    @NonNull
    private final ArrayList<ExtMenuItem> items;

    ExtMenuInput(@NonNull final String requestKey,
                 @Nullable final CharSequence menuTitle,
                 @Nullable final CharSequence message,
                 final int menuOwner,
                 @NonNull final ArrayList<ExtMenuItem> items) {
        this.requestKey = requestKey;
        this.menuTitle = menuTitle;
        this.message = message;
        this.menuOwner = menuOwner;
        this.items = items;
    }

    @NonNull
    static ExtMenuInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);

        final String title = args.getString(ExtMenuInput.BKEY_TITLE);
        final String message = args.getString(ExtMenuInput.BKEY_MESSAGE);
        final int menuOwner = args.getInt(ExtMenuInput.BKEY_MENU_OWNER);
        @SuppressWarnings("deprecation")
        final ArrayList<ExtMenuItem> items = Objects.requireNonNull(
                args.getParcelableArrayList(ExtMenuInput.BKEY_MENU));

        return new ExtMenuInput(requestKey, title, message, menuOwner, items);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(5);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);

        if (menuTitle != null) {
            args.putString(ExtMenuInput.BKEY_TITLE, menuTitle.toString());
        }
        if (message != null) {
            args.putString(ExtMenuInput.BKEY_MESSAGE, message.toString());
        }

        args.putInt(ExtMenuInput.BKEY_MENU_OWNER, menuOwner);
        args.putParcelableArrayList(ExtMenuInput.BKEY_MENU, items);

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @Nullable
    CharSequence getMenuTitle() {
        return menuTitle;
    }

    @Nullable
    CharSequence getMessage() {
        return message;
    }

    int getMenuOwner() {
        return menuOwner;
    }

    @NonNull
    List<ExtMenuItem> getItems() {
        return items;
    }
}

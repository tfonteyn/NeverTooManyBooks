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

package com.hardbacknutter.nevertoomanybooks.dialogs.stylepicker;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class StylePickerInput {
    private static final String TAG = "StylePickerInput";

    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":all";

    @NonNull
    private final String requestKey;
    @Nullable
    private final String uuid;
    private final boolean showAllStyles;

    StylePickerInput(@NonNull final String requestKey,
                     @Nullable final String uuid,
                     final boolean showAllStyles) {
        this.requestKey = requestKey;
        this.uuid = uuid;
        this.showAllStyles = showAllStyles;
    }

    @NonNull
    static StylePickerInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);

        final String uuid = args.getString(Style.BKEY_UUID);
        final boolean showAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);

        return new StylePickerInput(requestKey, uuid, showAllStyles);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(Style.BKEY_UUID, uuid);
        args.putBoolean(BKEY_SHOW_ALL_STYLES, showAllStyles);

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @Nullable
    String getUuid() {
        return uuid;
    }

    boolean isShowAllStyles() {
        return showAllStyles;
    }
}

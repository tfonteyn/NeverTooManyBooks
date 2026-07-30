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

package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public final class EditStyleInput {

    private static final String TAG = "EditStyleInput";

    static final int ACTION_EDIT_DEFAULTS = -1;
    static final int ACTION_CLONE = 0;
    static final int ACTION_EDIT = 1;

    private static final String BKEY_ACTION = TAG + ":action";
    private static final String BKEY_SET_AS_PREFERRED = TAG + ":setAsPreferred";

    @EditAction
    private final int action;

    @Nullable
    private final String styleUuid;

    /**
     * If set to {@code true} the edited/cloned style will be set as preferred.
     * If set to {@code false} the preferred state will not be touched.
     */
    private final boolean setAsPreferred;

    private EditStyleInput(@EditAction final int action,
                           @Nullable final String styleUuid,
                           final boolean setAsPreferred) {
        this.action = action;
        this.styleUuid = styleUuid;
        this.setAsPreferred = setAsPreferred;
    }

    @NonNull
    public static EditStyleInput edit(@NonNull final Style style) {
        return new EditStyleInput(ACTION_EDIT, style.getUuid(), style.isPreferred());
    }

    @NonNull
    public static EditStyleInput edit(@NonNull final Style style,
                                      final boolean setAsPreferred) {
        return new EditStyleInput(ACTION_EDIT, style.getUuid(), setAsPreferred);
    }

    @NonNull
    public static EditStyleInput duplicate(@NonNull final Style style) {
        return new EditStyleInput(ACTION_CLONE, style.getUuid(), style.isPreferred());
    }

    @NonNull
    public static EditStyleInput editDefaults() {
        return new EditStyleInput(ACTION_EDIT_DEFAULTS, null, false);
    }

    @NonNull
    static EditStyleInput fromBundle(@NonNull final Bundle args) {
        @EditStyleInput.EditAction
        final int action = args.getInt(EditStyleInput.BKEY_ACTION, EditStyleInput.ACTION_EDIT);
        final String uuid = args.getString(Style.BKEY_UUID);
        final boolean setAsPreferred = args.getBoolean(EditStyleInput.BKEY_SET_AS_PREFERRED, false);

        return new EditStyleInput(action, uuid, setAsPreferred);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putString(Style.BKEY_UUID, styleUuid);
        args.putInt(BKEY_ACTION, action);
        args.putBoolean(BKEY_SET_AS_PREFERRED, setAsPreferred);

        return args;
    }

    @EditAction
    int getAction() {
        return action;
    }

    @Nullable
    String getStyleUuid() {
        return styleUuid;
    }

    boolean isSetAsPreferred() {
        return setAsPreferred;
    }

    @IntDef({ACTION_EDIT_DEFAULTS, ACTION_CLONE, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EditAction {

    }
}

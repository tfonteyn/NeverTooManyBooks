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

package com.hardbacknutter.prefslib.internal;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDialogFactory;

public class DefaultDialogFactory
        implements SettingsDialogFactory {

    static final String BKEY_KEY = "key";
    static final String BKEY_DIALOG_MESSAGE = "msg";

    private static final String ERROR_UNKNOWN_TYPE = "Unsupported Setting type: ";

    @Override
    @NonNull
    public DialogFragment create(@NonNull final Context context,
                                 @NonNull final Setting setting,
                                 @Nullable final String dialogMessage) {
        final DialogFragment fragment;
        final Setting.Type type = setting.getType();
        switch (type) {
            case String:
                fragment = new StringDialogFragment();
                break;
            case SingleChoice:
                fragment = new SingleChoiceDialogFragment();
                break;
            case MultiChoice:
                fragment = new MultiChoiceDialogFragment();
                break;
            default:
                throw new IllegalArgumentException(ERROR_UNKNOWN_TYPE + type);
        }

        final Bundle args = new Bundle(2);
        args.putString(BKEY_KEY, setting.getKey());
        if (dialogMessage != null) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }
        fragment.setArguments(args);
        return fragment;
    }
}

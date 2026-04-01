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

package com.hardbacknutter.prefslib;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

@FunctionalInterface
public interface SettingsDialogFactory {

    /**
     * Callback requesting a DialogFragment for the given setting.
     *
     * @param context       Current context
     * @param setting       for which a dialog should be created
     * @param dialogMessage optional message which should be displayed
     *
     * @return dialog fragment
     */
    @NonNull
    DialogFragment create(@NonNull Context context,
                          @NonNull Setting setting,
                          @Nullable String dialogMessage);
}

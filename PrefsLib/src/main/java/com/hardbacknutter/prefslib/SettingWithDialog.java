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

import androidx.annotation.Nullable;

import java.util.function.Function;

/**
 * A setting which shows a dialog to get the value,
 * can display an additional message.
 */
public interface SettingWithDialog {

    /**
     * Set or remove the provider.
     *
     * @param provider to use
     */
    void setDialogMessageProvider(@Nullable Function<Setting, String> provider);

    /**
     * Called when a dialog is displayed.
     *
     * @return message, or {@code null} for none
     */
    @Nullable
    String getDialogMessage();
}

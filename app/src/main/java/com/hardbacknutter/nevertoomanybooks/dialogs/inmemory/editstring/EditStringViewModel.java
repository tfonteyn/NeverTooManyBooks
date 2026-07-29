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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.editstring;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

@SuppressWarnings("WeakerAccess")
public class EditStringViewModel
        extends ViewModel {

    @Nullable
    private String previousValue;
    @NonNull
    private String currentValue = "";
    @Nullable
    private Bundle extras;

    private boolean initDone;

    /**
     * Pseudo constructor.
     *
     * @param args all arguments
     */
    void init(@NonNull final EditStringInput args) {
        if (!initDone) {
            initDone = true;

            previousValue = args.getEdit();
            currentValue = previousValue != null ? previousValue : "";

            extras = args.getExtras();
        }
    }

    /**
     * Case-sensitive.
     *
     * @return flag
     */
    boolean isModified() {
        return !currentValue.equals(previousValue);
    }

    @Nullable
    String getPreviousValue() {
        return previousValue;
    }

    @NonNull
    String getCurrentValue() {
        return currentValue;
    }

    void setCurrentValue(@NonNull final String currentValue) {
        this.currentValue = currentValue;
    }

    /**
     * Get the optional/additional payload.
     *
     * @return extras Bundle
     */
    @Nullable
    Bundle getExtras() {
        return extras;
    }
}

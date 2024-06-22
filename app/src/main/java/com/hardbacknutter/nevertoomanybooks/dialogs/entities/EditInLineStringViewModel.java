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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

@SuppressWarnings("WeakerAccess")
public class EditInLineStringViewModel
        extends ViewModel {

    /** The text we're editing. */
    private String originalText;
    /** Current edit. */
    private String currentText;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (originalText == null) {
            originalText = args.getString(EditInLineStringLauncher.BKEY_TEXT, "");

            currentText = originalText;
        }
    }

    @NonNull
    String getOriginalText() {
        return originalText;
    }

    @NonNull
    String getCurrentText() {
        return currentText;
    }

    void setCurrentText(@NonNull final String currentText) {
        this.currentText = currentText;
    }

    boolean isModified() {
        return !currentText.equals(originalText);
    }
}

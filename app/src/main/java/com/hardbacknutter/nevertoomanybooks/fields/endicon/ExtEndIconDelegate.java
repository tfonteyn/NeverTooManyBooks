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
package com.hardbacknutter.nevertoomanybooks.fields.endicon;

import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface ExtEndIconDelegate {

    /**
     * MUST be called BEFORE {@link #setTextInputLayout(TextInputLayout)}
     * is called, or the listener will be ignored.
     *
     * @param endIconOnClickConsumer to use
     */
    void setEndIconOnClickListener(@Nullable View.OnClickListener endIconOnClickConsumer);

    void setTextInputLayout(@NonNull TextInputLayout parent);

    void updateEndIcon();

    @IntDef({
            TextInputLayout.END_ICON_CUSTOM,
            TextInputLayout.END_ICON_NONE,
            TextInputLayout.END_ICON_PASSWORD_TOGGLE,
            TextInputLayout.END_ICON_CLEAR_TEXT,
            TextInputLayout.END_ICON_DROPDOWN_MENU
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface EndIconMode {

    }
}

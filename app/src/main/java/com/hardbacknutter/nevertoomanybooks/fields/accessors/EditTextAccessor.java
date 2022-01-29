/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields.accessors;

import android.widget.EditText;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Stores and retrieves data from an EditText.
 *
 * <pre>
 *     {@code
 *             <com.google.android.material.textfield.TextInputLayout
 *             android:id="@+id/lbl_title"
 *             style="@style/TIL.EditText"
 *             android:hint="@string/lbl_title"
 *             app:layout_constraintEnd_toEndOf="parent"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_author"
 *             >
 *
 *             <com.google.android.material.textfield.TextInputEditText
 *                 android:id="@+id/title"
 *                 style="@style/titleTextEntry"
 *                 android:layout_width="match_parent"
 *                 android:layout_height="wrap_content"
 *                 tools:ignore="Autofill"
 *                 tools:text="@sample/data.json/book/title"
 *                 />
 *
 *         </com.google.android.material.textfield.TextInputLayout>}
 * </pre>
 *
 * @param <T> type of Field value.
 */
public class EditTextAccessor<T>
        extends BaseEditTextAccessor<T, EditText> {

    /**
     * Constructor.
     */
    public EditTextAccessor() {
        super(null, false);
    }

    /**
     * Constructor.
     *
     * @param formatter      to use
     * @param enableReformat flag: reformat after every user-change.
     */
    public EditTextAccessor(@NonNull final FieldFormatter<T> formatter,
                            final boolean enableReformat) {
        super(formatter, enableReformat);
    }
}

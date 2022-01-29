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

import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;


/**
 * The value is the text of the AutoCompleteTextView.
 * <p>
 * A {@code null} value is always handled as {@code ""}.
 *
 * <pre>
 *             <com.google.android.material.textfield.TextInputLayout
 *             android:id="@+id/lbl_genre"
 *             style="@style/TIL.AutoCompleteTextView"
 *             android:hint="@string/lbl_genre"
 *             app:layout_constraintEnd_toEndOf="parent"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_bookshelves"
 *             >
 *
 *             <com.google.android.material.textfield.MaterialAutoCompleteTextView
 *                 android:id="@+id/genre"
 *                 style="@style/autoCompleteTextEntry"
 *                 android:completionThreshold="2"
 *                 android:layout_width="match_parent"
 *                 android:layout_height="wrap_content"
 *                 android:imeOptions="actionNext"
 *                 tools:ignore="LabelFor"
 *                 tools:text="Fiction"
 *                 />
 *
 *         </com.google.android.material.textfield.TextInputLayout>
 * </pre>
 */
public class AutoCompleteTextAccessor
        extends BaseEditTextAccessor<String, AutoCompleteTextView> {

    /** The list for the adapter. */
    @NonNull
    private final Supplier<List<String>> mListSupplier;

    /**
     * Constructor.
     *
     * @param listSupplier Supplier with auto complete values
     */
    public AutoCompleteTextAccessor(@NonNull final Supplier<List<String>> listSupplier) {
        super(null, false);
        mListSupplier = listSupplier;
    }

    /**
     * Constructor.
     *
     * @param formatter      to use
     * @param enableReformat flag: reformat after every user-change.
     * @param listSupplier   Supplier with auto complete values
     */
    public AutoCompleteTextAccessor(@NonNull final FieldFormatter<String> formatter,
                                    final boolean enableReformat,
                                    @NonNull final Supplier<List<String>> listSupplier) {
        super(formatter, enableReformat);
        mListSupplier = listSupplier;
    }

    @Override
    public void setView(@NonNull final AutoCompleteTextView view) {
        super.setView(view);
        view.setAdapter(new FieldArrayAdapter(view.getContext(), mListSupplier.get(), mFormatter));
    }
}

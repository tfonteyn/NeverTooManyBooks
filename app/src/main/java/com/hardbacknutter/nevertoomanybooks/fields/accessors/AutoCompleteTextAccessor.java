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

import android.text.Editable;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;


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
        extends TextAccessor<String, AutoCompleteTextView>
        implements ExtTextWatcher {

    /** Log tag. */
    private static final String TAG = "AutoCompleteTextAcc";

    /** Reformat only every 0.5 seconds: this is good enough and easier on the user. */
    private static final int REFORMAT_DELAY_MS = 500;

    /** Enable or disable the formatting text watcher. */
    private final boolean mEnableReformat;

    /** The list for the adapter. */
    @NonNull
    private final Supplier<List<String>> mListSupplier;

    /** Timer for the text watcher. */
    private long mLastChange;

    /**
     * Constructor.
     *
     * @param listSupplier Supplier with auto complete values
     */
    public AutoCompleteTextAccessor(@NonNull final Supplier<List<String>> listSupplier) {
        super(null);
        mListSupplier = listSupplier;
        mEnableReformat = false;
    }

    /**
     * Constructor.
     *
     * @param listSupplier   Supplier with auto complete values
     * @param formatter      to use
     * @param enableReformat flag: reformat after every user-change.
     */
    public AutoCompleteTextAccessor(@NonNull final Supplier<List<String>> listSupplier,
                                    @NonNull final FieldFormatter<String> formatter,
                                    final boolean enableReformat) {
        super(formatter);
        mListSupplier = listSupplier;
        mEnableReformat = enableReformat && formatter instanceof EditFieldFormatter;
    }

    @Override
    public void setView(@NonNull final AutoCompleteTextView view) {
        super.setView(view);
        view.setAdapter(new FieldArrayAdapter(view.getContext(), mListSupplier.get(), mFormatter));
        view.addTextChangedListener(this);
    }

    @Nullable
    @Override
    public String getValue() {
        final AutoCompleteTextView view = getView();
        if (view != null) {
            final String text = view.getText().toString().trim();
            if (mFormatter != null) {
                if (mFormatter instanceof EditFieldFormatter) {
                    return ((EditFieldFormatter<String>) mFormatter)
                            .extract(view.getContext(), text);
                } else {
                    // otherwise use the locale variable
                    return mRawValue;
                }
            } else {
                return text;
            }

        } else {
            return mRawValue;
        }
    }

    @Override
    public void setValue(@Nullable final String value) {
        mRawValue = value;

        final AutoCompleteTextView view = getView();
        if (view != null) {
            // We need to do this in two steps. First format the value as normal.
            String text = null;
            if (mFormatter != null) {
                try {
                    text = mFormatter.format(view.getContext(), mRawValue);

                } catch (@NonNull final ClassCastException e) {
                    // Due to the way a Book loads data from the database,
                    // it's possible that it gets the column type wrong.
                    // See {@link BookCursor} class docs.
                    Logger.error(TAG, e, value);
                }
            }

            // No formatter, or ClassCastException.
            if (text == null) {
                text = mRawValue != null ? mRawValue : "";
            }

            // Second step set the view but ...

            // Disable the ChangedTextWatcher.
            view.removeTextChangedListener(this);

            // prevent auto-completion to kick in / stop the dropdown from opening.
            view.setText(text, false);

            // finally re-enable the watcher
            view.addTextChangedListener(this);
        }
    }

    /**
     * TextWatcher for TextView fields.
     *
     * <ol>
     *      <li>Re-formats if allowed and needed</li>
     *      <li>clears any previous error</li>
     *      <li>propagate the fact that the field changed</li>
     * </ol>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void afterTextChanged(@NonNull final Editable editable) {
        // reformat if allowed and needed
        if (mEnableReformat && System.currentTimeMillis() - mLastChange > REFORMAT_DELAY_MS) {
            // the view will never be null here.
            final TextView view = getView();
            // We have mEnableReformat, hence we can access the EditFieldFormatter directly.
            //noinspection ConstantConditions
            final String value = ((EditFieldFormatter<String>) mFormatter)
                    .extract(view.getContext(), view.getText().toString().trim());
            final String formatted = mFormatter.format(view.getContext(), value);

            // if the new text *can* be formatted and is different
            if (!editable.toString().trim().equalsIgnoreCase(formatted)) {
                view.removeTextChangedListener(this);
                // replace the coded value with the formatted value.
                editable.replace(0, editable.length(), formatted);
                view.addTextChangedListener(this);
            }
        }

        // Clear any previous error. The new content will be re-checked at validation time.
        mField.setError(null);

        broadcastChange();

        mLastChange = System.currentTimeMillis();
    }
}

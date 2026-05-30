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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.view.View;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtClearTextEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Base implementation for {@link TextViewField} and {@link EditTextField}.
 * <p>
 * Supports an optional {@link FieldFormatter}.
 *
 * @param <T> type of Field value. Usually just String, but any type supported by the
 *            {@link DataManager} should work (if not -> bug).
 * @param <V> type of Field View, must extend TextView
 */
abstract class BaseTextField<T, V extends TextView>
        extends BaseField<T, V> {

    @NonNull
    private FieldFormatter<T> formatter = (context, value)
            -> value != null ? String.valueOf(value) : "";

    @ExtEndIconDelegate.EndIconMode
    int endIconMode;

    @IdRes
    int textInputLayoutId;

    @Nullable
    private ExtEndIconDelegate endIconDelegate;

    private final View.OnClickListener endIconOnClickListener = v -> {
        final T previous = getValue();
        setValue(null);
        notifyIfChanged(previous);
    };

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param prefKey     The preference key to check if this Field is used or not
     */
    BaseTextField(@NonNull final FragmentId fragmentId,
                  @IdRes final int fieldViewId,
                  @NonNull final String fieldKey,
                  @NonNull final String prefKey) {
        super(fragmentId, fieldViewId, fieldKey, prefKey);
    }

    void setFormatter(@NonNull final FieldFormatter<T> formatter) {
        this.formatter = formatter;
    }

    @NonNull
    public FieldFormatter<T> getFormatter() {
        return formatter;
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);

        if (textInputLayoutId != 0) {
            final TextInputLayout til = parent.findViewById(textInputLayoutId);

            // On of our own end-icon delegates?
            if (endIconMode == TextInputLayout.END_ICON_CLEAR_TEXT) {
                endIconDelegate = new ExtClearTextEndIconDelegate<>(parent.getContext(), this);
                endIconDelegate.setEndIconOnClickListener(endIconOnClickListener);
                endIconDelegate.setTextInputLayout(til);

                // or use a default delegate?
            } else if (til.getEndIconMode() == TextInputLayout.END_ICON_CLEAR_TEXT) {
                til.setEndIconOnClickListener(endIconOnClickListener);
            }
        }
    }

    @Override
    public void load(@NonNull final Context context,
                     @NonNull final DataManager source,
                     @NonNull final RealNumberParser realNumberParser) {
        // We don't know the type <T>, so just cast it. If that fails -> BUG
        //noinspection unchecked
        internalLoad((T) source.get(getFieldKey(), realNumberParser));
    }

    @Override
    @Nullable
    public T getValue() {
        return rawValue;
    }

    @CallSuper
    @Override
    public void setValue(@Nullable final T value) {
        super.setValue(value);
        if (endIconDelegate != null) {
            endIconDelegate.updateEndIcon();
        }
    }

    @Override
    void internalSave(@NonNull final DataManager target) {
        // We don't know the type <T> so put as Object (DataManager will auto-detect).
        target.put(getFieldKey(), getValue());
    }

    /**
     * Check if the given value is considered to be 'empty'.
     * The {@code T} type decides what 'empty' means.
     * <p>
     * An Object is considered to be empty if:
     * <ul>
     *      <li>{@code null}</li>
     *      <li>{@code String.isEmpty()}</li>
     *      <li>{@code Money.isZero()}</li>
     *      <li>{@code Number.doubleValue() == 0.0d}</li>
     *      <li>{@code Boolean == false}</li>
     *      <li>{@code Collection.isEmpty}</li>
     *      <li>{@code !Checkable.isChecked()}</li>
     *      <li>{@link Object#toString()}#isEmpty()</li>
     * </ul>
     *
     * @return {@code true} if empty.
     */
    @Override
    boolean isEmpty(@Nullable final T o) {
        return o == null
               || o instanceof String && ((String) o).isEmpty()
               || o instanceof Money && ((Money) o).isZero()
               || o instanceof Number && ((Number) o).doubleValue() == 0.0d
               || o instanceof Boolean && !(Boolean) o
               || o instanceof Collection && ((Collection<?>) o).isEmpty()
               || o instanceof Checkable && !((Checkable) o).isChecked()
               // catch-all
               || o.toString().isEmpty();
    }
}

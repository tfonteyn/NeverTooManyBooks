/*
 * @Copyright 2018-2022 HardBackNutter
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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtClearTextEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Base implementation for {@link TextViewField} and {@link EditTextField}.
 * <p>
 * Supports an optional {@link FieldFormatter}.
 *
 * @param <T> type of Field value. Usually just String, but any type supported by the
 *            {@link DataManager} should work (if not -> bug).
 * @param <V> type of Field View, must extend TextView
 */
public abstract class BaseTextField<T, V extends TextView>
        extends BaseField<T, V> {

    @NonNull
    final FieldFormatter<T> formatter;

    @ExtEndIconDelegate.EndIconMode
    int endIconMode;

    @IdRes
    int textInputLayoutId;

    @Nullable
    private ExtEndIconDelegate endIconDelegate;

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param prefKey     The preference key to check if this Field is used or not
     * @param formatter   (optional) formatter to use
     */
    BaseTextField(@NonNull final FragmentId fragmentId,
                  @IdRes final int fieldViewId,
                  @NonNull final String fieldKey,
                  @NonNull final String prefKey,
                  @Nullable final FieldFormatter<T> formatter) {
        super(fragmentId, fieldViewId, fieldKey, prefKey);
        this.formatter = Objects.requireNonNullElseGet(
                formatter,
                () -> (context, value) -> value != null ? String.valueOf(value) : "");
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);

        if (textInputLayoutId != 0) {
            final TextInputLayout til = parent.findViewById(textInputLayoutId);

            // On of our own end-icon delegates?
            if (endIconMode == TextInputLayout.END_ICON_CLEAR_TEXT) {
                endIconDelegate = new ExtClearTextEndIconDelegate<>(this);
                endIconDelegate.setOnClickConsumer(v -> {
                    final T previous = getValue();
                    setValue(null);
                    notifyIfChanged(previous);
                });
                endIconDelegate.setTextInputLayout(til);

                // or use a default delegate?
            } else if (til.getEndIconMode() == TextInputLayout.END_ICON_CLEAR_TEXT) {
                til.setEndIconOnClickListener(v -> {
                    final T previous = getValue();
                    setValue(null);
                    notifyIfChanged(previous);
                });
            }
        }
    }

    @Override
    public void setInitialValue(@NonNull final Context context,
                                @NonNull final DataManager source) {
        final Object obj = source.get(context, fieldKey);
        if (obj != null) {
            //noinspection unchecked
            initialValue = (T) obj;
            setValue(initialValue);
        }
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
    @Nullable
    public T getValue() {
        return rawValue;
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        // We don't know the type <T> so put as Object (DataManager will auto-detect).
        // It will be the original rawValue.
        target.put(fieldKey, getValue());
    }

    /**
     * Check if the given value is considered to be 'empty'.
     * The encapsulated type decides what 'empty' means.
     * <p>
     * An Object is considered to be empty if:
     * <ul>
     *      <li>{@code null}</li>
     *      <li>{@code Money.isZero()}</li>
     *      <li>{@code Number.doubleValue() == 0.0d}</li>
     *      <li>{@code Boolean == false}</li>
     *      <li>{@code Collection.isEmpty}</li>
     *      <li>{@code !Checkable.isChecked()}</li>
     *      <li>{@code String.isEmpty()}</li>
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

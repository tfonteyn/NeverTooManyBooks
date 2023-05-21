/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Base implementation.
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View.
 */
public abstract class BaseField<T, V extends View>
        implements Field<T, V> {

    private static final String TAG = "BaseField";

    /**
     * Key used to access a {@link DataManager} or {@code Bundle}.
     * <ul>
     *      <li>key is set<br>
     *          Data is fetched from the {@link DataManager} (or Bundle),
     *          and populated on the screen.
     *          Extraction depends on the formatter in use.</li>
     *      <li>key is not set (i.e. "")<br>
     *          field is defined, but data handling must be done manually.</li>
     * </ul>
     * <p>
     * See {@link #isAutoPopulated()}.
     */
    @NonNull
    final String fieldKey;

    /**
     * The preference key (field-name) to check if this Field is used or not.
     * i.e. the key to be used for {@link Field#isUsed(Context)}.
     */
    @NonNull
    private final String usedKey;

    @NonNull
    private final FragmentId fragmentId;

    /** Fields that need to follow visibility. */
    private final Collection<Integer> relatedViews = new HashSet<>();

    @SuppressWarnings("FieldNotUsedInToString")
    @IdRes
    private final int fieldViewId;

    /** The value as originally loaded from the database. */
    @Nullable
    T initialValue;

    /**
     * The value which is currently held in memory.
     * If there is no current View, then this value *is* the correct current value.
     * If there is a View, then the View will contain the correct current value.
     * i.e. always try the View first before using this value.
     * <p>
     * Updated by the user and/or {@link #setValue(Object)}.
     */
    @Nullable
    T rawValue;

    @SuppressWarnings("FieldNotUsedInToString")
    @IdRes
    private int errorViewId;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<V> viewReference;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<View> errorViewReference;

    @Nullable
    private String errorText;

    @Nullable
    private Validator<T, V> validator;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<AfterChangedListener> afterFieldChangeListener;

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param prefKey     The preference key to check if this Field is used or not
     */
    BaseField(@NonNull final FragmentId fragmentId,
              @IdRes final int fieldViewId,
              @NonNull final String fieldKey,
              @NonNull final String prefKey) {
        this.fragmentId = fragmentId;
        this.fieldViewId = fieldViewId;
        this.fieldKey = fieldKey;
        usedKey = prefKey;
    }

    /**
     * set the field ID's which should follow visibility with this Field.
     * <p>
     * <strong>Dev. note:</strong> this could be done using
     * {@link androidx.constraintlayout.widget.Group}
     * but that means creating a group for EACH field. That would be overkill.
     *
     * @param viewIds labels etc
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public Field<T, V> addRelatedViews(@NonNull @IdRes final Integer... viewIds) {
        relatedViews.addAll(Arrays.asList(viewIds));
        return this;
    }

    /**
     * Set the optional validator for this field.
     *
     * @param validator to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public Field<T, V> setValidator(@NonNull final Validator<T, V> validator) {
        this.validator = validator;
        return this;
    }

    @Override
    public void setAfterFieldChangeListener(@Nullable final AfterChangedListener listener) {
        afterFieldChangeListener = listener != null ? new WeakReference<>(listener) : null;
    }

    @Override
    @NonNull
    public FragmentId getFragmentId() {
        return fragmentId;
    }

    @Override
    @IdRes
    public int getFieldViewId() {
        return fieldViewId;
    }

    @Override
    public boolean isAutoPopulated() {
        return !fieldKey.isEmpty();
    }

    /**
     * Set the id for the optional error view.
     *
     * @param id view id
     */
    void setErrorViewId(@IdRes final int id) {
        errorViewId = id;
        relatedViews.add(id);
    }

    @NonNull
    public V requireView() {
        return Objects.requireNonNull(getView());
    }

    /**
     * Internal to the Field class.
     *
     * @param target {@link DataManager} to save the Field value into.
     *
     * @see #putValue(DataManager)
     */
    abstract void internalPutValue(@NonNull DataManager target);

    @Override
    public void putValue(@NonNull final DataManager target) {
        internalPutValue(target);
        if (validator != null) {
            validator.validate(this);
        }
    }

    @CallSuper
    @Override
    public void setValue(@Nullable final T value) {
        rawValue = value;
        if (validator != null) {
            validator.validate(this);
        }
    }

    /**
     * Set the visibility for the related views.
     *
     * @param parent parent view for all related fields.
     */
    void setRelatedViewsVisibility(@NonNull final View parent) {
        setRelatedViewsVisibility(parent, requireView().getVisibility());
    }

    /**
     * Set the visibility for the related views.
     *
     * @param parent     parent view for all related fields.
     * @param visibility to use
     */
    private void setRelatedViewsVisibility(@NonNull final View parent,
                                           final int visibility) {
        for (final int viewId : relatedViews) {
            final View view = parent.findViewById(viewId);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    @Override
    public void setVisibility(@NonNull final View parent,
                              final boolean hideEmptyFields,
                              final boolean keepHiddenFieldsHidden) {
        final View view = requireView();
        final int currentVisibility = view.getVisibility();

        if (view instanceof ImageView
            || keepHiddenFieldsHidden && currentVisibility == View.GONE) {
            // An ImageView always keeps its current visibility.
            // When 'keepHiddenFieldsHidden' is set, hidden fields stay hidden.
            // Either way, the related views follow the main view
            setRelatedViewsVisibility(parent, currentVisibility);

        } else {
            if (hideEmptyFields && isEmpty()) {
                // When 'hideEmptyFields' is set, empty fields are hidden.
                view.setVisibility(View.GONE);
                setRelatedViewsVisibility(parent, View.GONE);

            } else if (isUsed(parent.getContext())) {
                // Anything else (in use) should be visible if it's not yet.
                view.setVisibility(View.VISIBLE);
                setRelatedViewsVisibility(parent, View.VISIBLE);
            }
        }
    }

    /**
     * Get the previously set view.
     *
     * @return view, or {@code null} if the reference is dead
     *
     * @throws NoViewException if the View-reference is not set.
     * @see #requireView()
     */
    @Nullable
    V getView()
            throws NoViewException {
        if (viewReference != null) {
            return viewReference.get();
        }
        return null;
    }

    @CallSuper
    @Override
    public void setParentView(@NonNull final View parent) {

        viewReference = new WeakReference<>(parent.findViewById(fieldViewId));
        // Unused fields are hidden here BEFORE they are displayed at all.
        if (!isUsed(parent.getContext())) {
            requireView().setVisibility(View.GONE);
            setRelatedViewsVisibility(parent, View.GONE);
            return;
        }

        if (errorViewId != 0) {
            final View errorView = parent.findViewById(errorViewId);
            if (errorView != null) {
                errorViewReference = new WeakReference<>(errorView);
                // Restore any previous error text
                if (errorText != null) {
                    setError(errorText);
                }
            } else {
                errorViewReference = null;
            }
        }
    }

    @Override
    public void setError(@Nullable final String errorText) {
        this.errorText = errorText;
        // Don't complain if the view is not there. We can get called when
        // the field is not on display.
        if (errorViewReference != null) {
            final View errorView = errorViewReference.get();
            if (errorView != null) {
                if (errorView instanceof TextInputLayout) {
                    final TextInputLayout til = (TextInputLayout) errorView;
                    til.setError(errorText);
                } else if (errorView instanceof TextView) {
                    final TextView textView = (TextView) errorView;
                    textView.setError(errorText);
                } else {
                    throw new IllegalStateException("Wrong view type: " + errorView);
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        try {
            return isEmpty(getValue());
        } catch (@NonNull final ClassCastException e) {
            // added due to github #4.
            LoggerFactory.getLogger().e(TAG, e, "value=" + getValue());
        }
        return true;
    }

    /**
     * Check if the given value is considered to be 'empty'.
     *
     * @param value to check
     *
     * @return {@code true} if empty.
     *
     * @throws ClassCastException if the value (which comes from a Bundle) does not match the type
     */
    abstract boolean isEmpty(@Nullable T value);

    @Override
    public void notifyIfChanged(@Nullable final T previous) {
        final T currentValue = getValue();
        final boolean changed;
        try {
            final boolean allEmpty = isEmpty(initialValue)
                                     && isEmpty(previous)
                                     && isEmpty(currentValue);
            final boolean allEqual = Objects.equals(initialValue, previous)
                                     && Objects.equals(previous, currentValue);

            changed = !allEmpty && !allEqual;

        } catch (@NonNull final ClassCastException e) {
            // added due to github #4.
            LoggerFactory.getLogger().e(TAG, e, "value=" + getValue());
            return;
        }

        if (changed) {
            if (afterFieldChangeListener != null && afterFieldChangeListener.get() != null) {
                afterFieldChangeListener.get().onAfterChanged(this);

            } else {
                if (BuildConfig.DEBUG /* always */) {
                    // the listener being null is perfectly fine.
                    // i.e. it will be null during population of fields.
                    if (afterFieldChangeListener != null) {
                        // The REFERENT being dead is however not fine, so log this in debug.
                        // flw: this message should never be seen!
                        LoggerFactory.getLogger().d(TAG, "notifyIfChanged",
                                                    "onChanged",
                                                    "afterFieldChangeListener was dead");
                    }
                }
            }
        }
    }

    @Override
    public boolean isUsed(@NonNull final Context context) {
        return GlobalFieldVisibility.isUsed(context, usedKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "BaseField{"
               + "fieldKey=" + fieldKey
               + ", usedKey=" + usedKey
               + ": fragmentId=" + fragmentId
               + ", relatedViews=" + relatedViews
               + ", initialValue=`" + initialValue + "`"
               + ", rawValue=`" + rawValue + "`"
               + ", getValue=`" + getValue() + "`"
               + ", errorText=`" + errorText + "`"
               + ", validator=" + validator
               + '}';
    }
}

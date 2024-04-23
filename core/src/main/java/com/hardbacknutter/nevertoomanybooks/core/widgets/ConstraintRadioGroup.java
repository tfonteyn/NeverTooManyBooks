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
package com.hardbacknutter.nevertoomanybooks.core.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintHelper;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Replacement for {@link android.widget.RadioGroup} which allows Radio buttons to be held
 * in a ConstraintLayout. Not all functionality is implemented yet.
 * <p>
 * Usage:
 * <pre>{@code
 *       <com.hardbacknutter.nevertoomanybooks.core.widgets.ConstraintRadioGroup
 *         android:id="@+id/rb_books_group"
 *         android:layout_width="0dp"
 *         android:layout_height="0dp"
 *         app:constraint_referenced_ids="rb_books_all,rb_books_since_last_backup"
 *         tools:ignore="MissingConstraints"
 *         />
 * }</pre>
 */
public final class ConstraintRadioGroup
        extends ConstraintHelper {

    /** All radio buttons in this group. */
    @NonNull
    private final SparseArray<RadioButton> mRadioButtons = new SparseArray<>();

    /** User listener. */
    @Nullable
    private OnCheckedChangeListener mOnCheckedChangeListener;
    /** Stop the individual button listeners from infinite recursion. */
    private boolean mProtectFromCheckedChange;
    /** The currently checked radio button id. */
    @IdRes
    private int mCheckedId = View.NO_ID;
    /** individual button listener. */
    @NonNull
    private final CompoundButton.OnCheckedChangeListener mChildOnCheckedChangeListener =
            (buttonView, isChecked) -> {
                // prevents from infinite recursion
                if (mProtectFromCheckedChange) {
                    return;
                }

                mProtectFromCheckedChange = true;
                // uncheck previously checked button
                if (mCheckedId != View.NO_ID) {
                    setCheckedStateForView(mCheckedId, false);
                }
                mProtectFromCheckedChange = false;

                // set the new checked button
                setCheckedId(buttonView.getId());
            };

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public ConstraintRadioGroup(@NonNull final Context context) {
        super(context);
    }

    public ConstraintRadioGroup(@NonNull final Context context,
                                @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public ConstraintRadioGroup(@NonNull final Context context,
                                @Nullable final AttributeSet attrs,
                                final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void init(@Nullable final AttributeSet attrs) {
        super.init(attrs);
        super.setVisibility(View.GONE);
        mUseViewMeasure = false;
    }

    @Override
    public void updatePreLayout(@NonNull final ConstraintLayout container) {
        super.updatePreLayout(container);

        // Collect the radio buttons in our container, and set a listener on them.
        for (int i = 0; i < mCount; i++) {
            // mIds: constraint_referenced_ids
            final int id = mIds[i];
            final View view = container.getViewById(id);
            // sanity check
            if (view instanceof RadioButton) {
                final RadioButton radioButton = (RadioButton) view;
                mRadioButtons.put(id, radioButton);
                radioButton.setOnCheckedChangeListener(mChildOnCheckedChangeListener);
                if (radioButton.isChecked()) {
                    mCheckedId = id;
                }
            }
        }
    }

    /**
     * <p>Sets the selection to the radio button whose identifier is passed in
     * parameter. Using {@code View.NO_ID} as the selection identifier clears the selection;
     * such an operation is equivalent to invoking {@link #clearCheck()}.</p>
     *
     * @param viewId the unique id of the radio button to select in this group
     *
     * @see #getCheckedRadioButtonId()
     * @see #clearCheck()
     */
    public void check(@IdRes final int viewId) {
        // don't even bother
        if (viewId != View.NO_ID && viewId == mCheckedId) {
            return;
        }

        // uncheck current
        if (mCheckedId != View.NO_ID) {
            setCheckedStateForView(mCheckedId, false);
        }

        // check new one
        if (viewId != View.NO_ID) {
            setCheckedStateForView(viewId, true);
        }

        setCheckedId(viewId);
    }

    private void setCheckedId(@IdRes final int id) {
        mCheckedId = id;

        if (mOnCheckedChangeListener != null) {
            mOnCheckedChangeListener.onCheckedChanged(this, id);
        }
    }

    private void setCheckedStateForView(@IdRes final int viewId,
                                        final boolean checked) {
        final RadioButton radioButton = mRadioButtons.get(viewId);
        if (radioButton != null) {
            radioButton.setChecked(checked);
        }
    }

    /**
     * <p>Returns the identifier of the selected radio button in this group.
     * Upon empty selection, the returned value is {@code View.NO_ID}</p>
     *
     * @return the unique id of the selected radio button in this group
     *
     * @see #check(int)
     * @see #clearCheck()
     */
    @IdRes
    public int getCheckedRadioButtonId() {
        return mCheckedId;
    }

    /**
     * <p>Clears the selection. When the selection is cleared, no radio button
     * in this group is selected and {@link #getCheckedRadioButtonId()} returns
     * {@code View.NO_ID}.</p>
     *
     * @see #check(int)
     * @see #getCheckedRadioButtonId()
     */
    public void clearCheck() {
        check(View.NO_ID);
    }

    /**
     * <p>Register a callback to be invoked when the checked radio button
     * changes in this group.</p>
     *
     * @param listener the callback to call on checked state change
     */
    public void setOnCheckedChangeListener(@Nullable final OnCheckedChangeListener listener) {
        mOnCheckedChangeListener = listener;
    }

    /**
     * <p>Interface definition for a callback to be invoked when the checked
     * radio button changed in this group.</p>
     */
    @FunctionalInterface
    public interface OnCheckedChangeListener {

        /**
         * <p>Called when the checked radio button has changed. When the
         * selection is cleared, id is View.NO_ID.</p>
         *
         * @param group     the group in which the checked radio button has changed
         * @param checkedId the unique identifier of the newly checked radio button
         */
        void onCheckedChanged(@NonNull ConstraintRadioGroup group,
                              @IdRes int checkedId);
    }
}

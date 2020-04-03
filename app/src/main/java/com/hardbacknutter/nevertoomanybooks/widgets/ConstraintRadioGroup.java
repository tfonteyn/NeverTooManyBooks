/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
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
 *   <android.support.constraint.ConstraintLayout
 *     ...
 *     <com.hardbacknutter.nevertoomanybooks.widgets.ConstraintRadioGroup
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         app:constraint_referenced_ids="radioButton1, radioButton2" />
 *   </android.support.constraint.ConstraintLayout>
 * }</pre>
 * <p>
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
    private int mCheckedId = View.NO_ID;
    /** individual button listener. */
    @NonNull
    private final CompoundButton.OnCheckedChangeListener mRadioButtonListener =
            (buttonView, isChecked) -> {
                // prevents from infinite recursion
                if (mProtectFromCheckedChange) {
                    return;
                }

                // uncheck previously checked button
                if (mCheckedId != View.NO_ID) {
                    mProtectFromCheckedChange = true;
                    setCheckedStateForView(mCheckedId, false);
                    mProtectFromCheckedChange = false;
                }

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
        mUseViewMeasure = false;
    }

    @Override
    public void updatePreLayout(@NonNull final ConstraintLayout container) {
        super.updatePreLayout(container);

        // Collect the radio buttons in our container, and set a listener on them.
        for (int i = 0; i < mCount; i++) {
            // mIds: constraint_referenced_ids
            int id = mIds[i];
            View view = container.getViewById(id);
            // sanity check
            if (view instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) view;
                mRadioButtons.put(id, radioButton);
                radioButton.setOnCheckedChangeListener(mRadioButtonListener);
                if (radioButton.isChecked()) {
                    mCheckedId = id;
                }
            }
        }
    }

    @Override
    public void updatePostLayout(@NonNull final ConstraintLayout container) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = 0;
        params.height = 0;
        super.updatePostLayout(container);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        for (int i = 0; i < mRadioButtons.size(); i++) {
            mRadioButtons.valueAt(i).setEnabled(enabled);
        }
    }

    /**
     * <p>Sets the selection to the radio button whose identifier is passed in
     * parameter. Using {@code View.NO_ID} as the selection identifier clears the selection;
     * such an operation is equivalent to invoking {@link #clearCheck()}.</p>
     *
     * @param id the unique id of the radio button to select in this group
     *
     * @see #getCheckedRadioButtonId()
     * @see #clearCheck()
     */
    public void check(@IdRes final int id) {
        // don't even bother
        if (id != -1 && (id == mCheckedId)) {
            return;
        }

        mProtectFromCheckedChange = true;

        // uncheck current
        if (mCheckedId != View.NO_ID) {
            setCheckedStateForView(mCheckedId, false);
        }

        // check new one
        if (id != View.NO_ID) {
            setCheckedStateForView(id, true);
        }

        setCheckedId(id);
    }

    private void setCheckedId(@IdRes final int id) {
        if (mCheckedId != id) {
            mCheckedId = id;

            if (mOnCheckedChangeListener != null) {
                mOnCheckedChangeListener.onCheckedChanged(this, id);
            }
        }
    }

    private void setCheckedStateForView(final int viewId,
                                        final boolean checked) {
        RadioButton radioButton = mRadioButtons.get(viewId);
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public void clearCheck() {
        check(View.NO_ID);
    }

    /**
     * <p>Register a callback to be invoked when the checked radio button
     * changes in this group.</p>
     *
     * @param listener the callback to call on checked state change
     */
    @SuppressWarnings("unused")
    public void setOnCheckedChangeListener(@Nullable final OnCheckedChangeListener listener) {
        mOnCheckedChangeListener = listener;
    }

    /**
     * <p>Interface definition for a callback to be invoked when the checked
     * radio button changed in this group.</p>
     */
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

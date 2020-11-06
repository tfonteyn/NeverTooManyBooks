/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintHelper;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Replacement for {@link androidx.constraintlayout.widget.Group}.
 * <p>
 * The original only provides support for visibility and is broken in different ways
 * depending on the version.
 * <p>
 * This replacement is based/tested on 'androidx.constraintlayout:constraintlayout:2.0.0-beta5'
 *
 * <ul>It allow and propagates:
 * <li>visibility (working)</li>
 * <li>setting tags</li>
 * <li>setting OnClickListener/OnLongClickListener</li>
 * </ul>
 * on all views referenced by this group.
 * <p>
 * /src/main/res/values/attrs.xml:
 * <pre>
 *     {@code
 *     <declare-styleable name="ExtGroup">
 *         <attr name="groupApply">
 *             <flag name="visibility" value="0x1" />
 *             <flag name="onclick" value="0x2" />
 *             <flag name="tags" value="0x4" />
 *         </attr>
 *     </declare-styleable>
 *     }
 * </pre>
 */
public class ExtGroup
        extends ConstraintHelper {

    public static final int APPLY_VISIBILITY = 0x1;
    public static final int APPLY_ON_CLICK = 0x2;
    public static final int APPLY_TAGS = 0x4;

    @Nullable
    private OnClickListener mOnClickListener;
    @Nullable
    private OnLongClickListener mOnLongClickListener;
    @Nullable
    private Object mTag;
    private SparseArray<Object> mKeyedTags;

    /** Initialised in {@link #init(AttributeSet)}. */
    private int mApplyFlags;

    public ExtGroup(@NonNull final Context context,
                    @Nullable final AttributeSet attrs,
                    final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ExtGroup(@NonNull final Context context,
                    @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtGroup(@NonNull final Context context) {
        super(context);
    }

    // reminder: this method is called from the super constructor.
    // and this is BEFORE this class-init phase.
    protected void init(@Nullable final AttributeSet attrs) {
        super.init(attrs);
        mUseViewMeasure = false;

        if (attrs != null) {
            final TypedArray ta = getContext().getTheme().obtainStyledAttributes(
                    attrs, R.styleable.ExtGroup, 0, 0);
            try {
                mApplyFlags = ta.getInteger(R.styleable.ExtGroup_groupApply, 0);
            } finally {
                ta.recycle();
            }
        } else {
            mApplyFlags = 0;
        }
    }

    public void updatePostInvalidate(@NonNull final ConstraintLayout container) {
        applyLayoutFeatures(container);
    }

    public void updatePreLayout(@NonNull final ConstraintLayout container) {
        super.updatePreLayout(container);
        applyLayoutFeatures(container);
    }

    public void updatePostLayout(@NonNull final ConstraintLayout container) {
        final ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) getLayoutParams();
        params.getConstraintWidget().setWidth(0);
        params.getConstraintWidget().setHeight(0);
    }

    /**
     * Overrides the original; does not call super.
     * Fixes the visibility issue + adds the new features.
     */
    protected void applyLayoutFeatures(@NonNull final ConstraintLayout container) {
        // original code in 2.0.0-beta5
        //         int visibility = this.getVisibility();
        //        float elevation = 0.0F;
        //        if (VERSION.SDK_INT >= 21) {
        //            elevation = this.getElevation();
        //        }
        //
        //        for(int i = 0; i < this.mCount; ++i) {
        //            int id = this.mIds[i];
        //            View view = container.getViewById(id);
        //            if (view != null) {
        //                if (visibility != 0) {
        //                    view.setVisibility(visibility);
        //                }
        //
        //                if (elevation > 0.0F && VERSION.SDK_INT >= 21) {
        //                    view.setTranslationZ(view.getTranslationZ() + elevation);
        //                }
        //            }
        //        }

        final int visibility = this.getVisibility();
        final float elevation = this.getElevation();

        for (int i = 0; i < this.mCount; ++i) {
            final int id = this.mIds[i];
            final View view = container.getViewById(id);
            if (view != null) {

                if ((mApplyFlags & APPLY_VISIBILITY) != 0) {
                    view.setVisibility(visibility);
                    if (elevation > 0.0F) {
                        view.setTranslationZ(view.getTranslationZ() + elevation);
                    }
                }

                if ((mApplyFlags & APPLY_ON_CLICK) != 0) {
                    if (mOnClickListener != null) {
                        view.setOnClickListener(mOnClickListener);
                    }
                    if (mOnLongClickListener != null) {
                        view.setOnLongClickListener(mOnLongClickListener);
                    }
                }

                if ((mApplyFlags & APPLY_TAGS) != 0) {
                    if (mTag != null) {
                        view.setTag(mTag);
                    }
                    if (mKeyedTags != null) {
                        for (int keyIdx = 0; keyIdx < mKeyedTags.size(); keyIdx++) {
                            view.setTag(mKeyedTags.keyAt(keyIdx), mKeyedTags.valueAt(keyIdx));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setTag(@Nullable final Object tag) {
        mTag = tag;
        super.setTag(tag);
    }

    @Override
    public void setTag(@IdRes final int key,
                       @Nullable final Object tag) {
        if (mKeyedTags == null) {
            mKeyedTags = new SparseArray<>(2);
        }

        mKeyedTags.put(key, tag);
        super.setTag(key, tag);
    }

    @Override
    public void setOnClickListener(@Nullable final OnClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    @Override
    public void setOnLongClickListener(@Nullable final OnLongClickListener onLongClickListener) {
        mOnLongClickListener = onLongClickListener;
    }

    @Override
    @NonNull
    public String toString() {
        return "ExtGroup{"
               + "id=" + this.getId()
               + ", mApplyFlags=0b" + Integer.toBinaryString(mApplyFlags)
               + ", mTag=" + mTag
               + ", mKeyedTags=" + mKeyedTags
               + ", mOnClickListener=" + mOnClickListener
               + ", mOnLongClickListener=" + mOnLongClickListener
               + '}';
    }
}

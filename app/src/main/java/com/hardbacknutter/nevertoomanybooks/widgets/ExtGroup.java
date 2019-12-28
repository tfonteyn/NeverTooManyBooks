/*
 * @Copyright 2019 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Allow and propagate setting tags, OnClickListener and OnLongClickListener
 * on all views referenced by this group.
 * <p>
 * Tags are also set on the group itself.
 *
 * <strong>Developer note</strong>: we need to extend Group because its
 * init/updatePostLayout methods accesses some protected members.
 * But we need to stop Group from manipulating the referenced views themselves,
 * hence we override (and do not call the super) for {@link #updatePreLayout(ConstraintLayout)}.
 */
public class ExtGroup
        extends androidx.constraintlayout.widget.Group {

    @Nullable
    private OnClickListener mOnClickListener;
    @Nullable
    private OnLongClickListener mOnLongClickListener;
    private Object mTag;
    private SparseArray<Object> mKeyedTags;

    public ExtGroup(@NonNull final Context context) {
        super(context);
    }

    public ExtGroup(@NonNull final Context context,
                    @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtGroup(@NonNull final Context context,
                    @Nullable final AttributeSet attrs,
                    final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setTag(@Nullable final Object tag) {
        mTag = tag;
        super.setTag(tag);
    }

    @Override
    public void setTag(final int key,
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
    public void updatePreLayout(@NonNull final ConstraintLayout container) {
        // Don't call super. We need to modify the code to control visibility of references views.
        // BEGIN - copy of Group#updatePreLayout
//        int visibility = this.getVisibility();
//        float elevation = this.getElevation();

        if (this.mReferenceIds != null) {
            this.setIds(this.mReferenceIds);
        }

//        for(int i = 0; i < this.mCount; ++i) {
//            int id = this.mIds[i];
//            View view = container.getViewById(id);
//            if (view != null) {
//                view.setVisibility(visibility);
//                if (elevation > 0.0F) {
//                    view.setElevation(elevation);
//                }
//            }
//        }
        // END - copy of Group#updatePreLayout

        for (View view : getViews(container)) {
            if (mOnClickListener != null) {
                view.setOnClickListener(mOnClickListener);
            }
            if (mOnLongClickListener != null) {
                view.setOnLongClickListener(mOnLongClickListener);
            }
            if (mTag != null) {
                view.setTag(mTag);
            }
            if (mKeyedTags != null) {
                for (int i = 0; i < mKeyedTags.size(); i++) {
                    view.setTag(mKeyedTags.keyAt(i), mKeyedTags.valueAt(i));
                }
            }
        }
    }
}

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
package com.hardbacknutter.nevertoomanybooks.core.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Allow the user to drag the FAB button.
 * <p>
 * Reminder: the super class is an {@code ImageButton}
 */
public class MovableFloatingActionButton
        extends FloatingActionButton
        implements View.OnTouchListener {

    /**
     * Often, there will be a slight, unintentional, drag when the user taps the FAB,
     * so we need to account for this.
     */
    private static final float CLICK_DRAG_TOLERANCE = 10;

    private float downRawX;
    private float downRawY;
    private float dX;
    private float dY;

    public MovableFloatingActionButton(@NonNull final Context context) {
        super(context);
        init();
    }

    public MovableFloatingActionButton(@NonNull final Context context,
                                       @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MovableFloatingActionButton(@NonNull final Context context,
                                       @Nullable final AttributeSet attrs,
                                       final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(@NonNull final View view,
                           @NonNull final MotionEvent motionEvent) {

        final ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view
                .getLayoutParams();

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                downRawX = motionEvent.getRawX();
                downRawY = motionEvent.getRawY();
                dX = view.getX() - downRawX;
                dY = view.getY() - downRawY;
                return true;
            }
            case MotionEvent.ACTION_UP: {
                final float upRawX = motionEvent.getRawX();
                final float upRawY = motionEvent.getRawY();

                final float upDX = upRawX - downRawX;
                final float upDY = upRawY - downRawY;

                if (Math.abs(upDX) < CLICK_DRAG_TOLERANCE
                    && Math.abs(upDY) < CLICK_DRAG_TOLERANCE) {
                    return performClick();
                } else {
                    // A drag
                    return true;
                }
            }
            case MotionEvent.ACTION_MOVE: {
                final int viewWidth = view.getWidth();
                final int viewHeight = view.getHeight();

                final View viewParent = (View) view.getParent();
                final int parentWidth = viewParent.getWidth();
                final int parentHeight = viewParent.getHeight();

                float newX = motionEvent.getRawX() + dX;
                // Don't allow the FAB past the left/right hand side of the parent
                newX = Math.max(layoutParams.leftMargin, newX);
                newX = Math.min(parentWidth - viewWidth - layoutParams.rightMargin, newX);

                float newY = motionEvent.getRawY() + dY;
                // Don't allow the FAB past the top/bottom of the parent
                newY = Math.max(layoutParams.topMargin, newY);
                newY = Math.min(parentHeight - viewHeight - layoutParams.bottomMargin, newY);

                view.animate().x(newX).y(newY).setDuration(0).start();
                return true;
            }

            default:
                return super.onTouchEvent(motionEvent);
        }
    }
}

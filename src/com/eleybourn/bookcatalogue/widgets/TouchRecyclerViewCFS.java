/*
 * Copyright (c) 2012 Philip Warner
 * Portions Copyright (c) 2010 CommonsWare, LLC
 * Portions Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.eleybourn.bookcatalogue.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Replacement for {@link TouchListView}. Work-in-progress.
 *
 * and then I learned about {@link ItemTouchHelper} ....
 *
 *
 * <p>
 * Note: ONLY supports the {@link LinearLayoutManager}.
 * <p>
 * Adapted from TouchListView from CommonsWare which is based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2. See
 * <a href="https://github.com/timsu/cwac-touchlist">https://github.com/timsu/cwac-touchlist</a>
 * <p>
 * Customizable attributes:
 * <pre>
 *      {@code
 *      <declare-styleable name="TouchListView">
 *          <!-- MANDATORY! -->
 *          <attr name="trv_grabber" format="reference" />
 *          <!-- optional -->
 *          <attr name="trv_normal_height" format="dimension" />
 *          <attr name="trv_dnd_background" format="color" />
 *          <attr name="trv_remove_mode">
 *              <enum name="none" value="-1" />
 *              <enum name="fling" value="0" />
 *              <enum name="slideRight" value="1" />
 *              <enum name="slideLeft" value="2" />
 *          </attr>
 *      </declare-styleable>
 *      }
 * </pre>
 * <li>trv_grabber:
 * The android:id value of an icon in your rows that should be used as the "grab handle"
 * for the drag-and-drop operation (required)
 * <li>trv_normal_height:
 * The height of one of your regular rows.
 * Default: the height of the first row, assuming all rows are equal height.
 * <li>trv_dnd_background:
 * A colour to use as the background of your row when it is being dragged
 * Default: fully transparent.
 * <li>trv_remove_mode:<ul>
 * <li>none:         (default) user cannot remove entries
 * <li>slideRight:   user can remove entries by dragging to the right quarter of the list
 * <li>slideLeft:    user can remove entries by dragging to the left quarter of the list)
 * <li>fling:        ...not quite sure what this does
 * </ul>
 */
public class TouchRecyclerViewCFS
        extends RecyclerViewCFS {

    /** {@link #mRemoveMode}. */
    private static final int MODE_NOT_SET = -1;
    private static final int FLING = 0;
    private static final int SLIDE_RIGHT = 1;
    private static final int SLIDE_LEFT = 2;

    private final int mTouchSlop;

    @IdRes
    private final int mGrabberId;

    /** Color.TRANSPARENT by default. */
    @ColorInt
    private final int mDndBackgroundColor;

    private final int mRemoveMode;

    /** Height of a row. Uses the height of the FIRST item, assuming all others are equal height. */
    private int mItemHeight;

    /** Don't use directly; always use {@link #getWindowManager()} to access. */
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowParams;

    /** The view that is being dragged. */
    @Nullable
    private ImageView mDragView;
    /** The dragged view, as a bitmap for visual feedback. */
    @Nullable
    private Bitmap mDragBitmap;

    /** Which item is being dragged. */
    private int mDragPos;
    /** Where was the dragged item originally. */
    private int mFirstDragPos;
    /** At what offset inside the item did the user grab it. */
    private int mDragPoint;
    /** The difference between screen coordinates and coordinates in this view. */
    private int mCoordinatesOffset;

    /** Optional listener to get notified when a drag starts. */
    @Nullable
    private OnDragListener mOnDragListener;
    /** Optional listener to get notified when a drop happens. */
    @Nullable
    private OnDropListener mOnDropListener;
    /** Optional listener to get notified if a removal is done. */
    @Nullable
    private OnRemoveListener mOnRemoveListener;

    private int mUpperBound;
    private int mLowerBound;

    /**
     * The height of the ListView. It's set in {@link #onInterceptTouchEvent(MotionEvent)}
     * and the {@link #onTouchEvent} will then use the cached copy.
     * This prevents any issues with a changing height.
     */
    private int mTotalHeight;

    @Nullable
    private GestureDetector mGestureDetector;

    /** Set to {@code true} at start of a new drag operation. */
    private boolean mWasFirstExpansion;
    @Nullable
    private Integer mSavedHeight;
    private LinearLayoutManager mLayoutManager;

    /**
     * Constructor used when instantiating Views programmatically.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     */
    public TouchRecyclerViewCFS(@NonNull final Context context) {
        this(context, null);
    }

    /**
     * Constructor used by the LayoutInflater.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public TouchRecyclerViewCFS(@NonNull final Context context,
                                @Nullable final AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }

    /**
     * Constructor used by the LayoutInflater if there was a 'style' attribute.
     *
     * @param context      The Context the view is running in, through which it can
     *                     access the current theme, resources, etc.
     * @param attrs        The attributes of the XML tag that is inflating the view.
     * @param defStyleAttr An attribute in the current theme that contains a
     *                     reference to a style resource that supplies default values for
     *                     the view. Can be 0 to not look for defaults.
     */
    public TouchRecyclerViewCFS(@NonNull final Context context,
                                @Nullable final AttributeSet attrs,
                                final int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TouchRecyclerViewCFS,
                                                      defStyleAttr, 0);

        mGrabberId = a.getResourceId(R.styleable.TouchRecyclerViewCFS_trv_grabber, -1);
        mRemoveMode = a.getInt(R.styleable.TouchRecyclerViewCFS_trv_remove_mode, MODE_NOT_SET);
        mItemHeight = a.getDimensionPixelSize(R.styleable.TouchRecyclerViewCFS_trv_normal_height,
                                              0);
        mDndBackgroundColor = a.getColor(R.styleable.TouchRecyclerViewCFS_trv_background,
                                         Color.TRANSPARENT);
        a.recycle();
    }

    /**
     * Intercept, and take a local reference for the layout manager.
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    public void setLayoutManager(@Nullable final LayoutManager layout) {
        super.setLayoutManager(layout);
        if (!(layout instanceof LinearLayoutManager)) {
            throw new IllegalStateException("The adapter MUST be a LinearLayoutManager");
        }
        mLayoutManager = (LinearLayoutManager) layout;
    }

    @Override
    @CallSuper
    public boolean onInterceptTouchEvent(@NonNull final MotionEvent e) {
        if (mOnRemoveListener != null && mGestureDetector == null) {
            if (mRemoveMode == FLING) {
                mGestureDetector = new GestureDetector(
                        getContext(),
                        new SimpleOnGestureListener() {
                            @Override
                            public boolean onFling(@NonNull final MotionEvent e1,
                                                   @NonNull final MotionEvent e2,
                                                   final float velocityX,
                                                   final float velocityY) {
                                if (mDragView != null) {
                                    if (velocityX > 1000) {
                                        Rect r = new Rect();
                                        mDragView.getDrawingRect(r);
                                        if (e2.getX() > r.right * 2 / 3) {
                                            // fast fling right with release near the right
                                            // edge of the screen
                                            stopDragging();
                                            mOnRemoveListener.onRemove(mFirstDragPos);
                                            unExpandViews(true);
                                        }
                                    }
                                    // flinging while dragging should have no effect
                                    return true;
                                }
                                return false;
                            }
                        });
            }
        }

        if (mOnDragListener != null || mOnDropListener != null) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                View rowView = findChildViewUnder(e.getX(), e.getY());
                if (rowView == null) {
                    return super.onInterceptTouchEvent(e);
                }

                // is it a draggable row ?
                View dragHandle = rowView.findViewById(mGrabberId);
                if (dragHandle != null) {

                    int x = (int) e.getX();
                    int y = (int) e.getY();

                    mDragPoint = y - rowView.getTop();
                    mCoordinatesOffset = ((int) e.getRawY()) - y;

                    if ((dragHandle.getLeft() < x) && (x < dragHandle.getRight())) {
                        rowView.setDrawingCacheEnabled(true);
                        // Create a copy of the drawing cache so that it does not get recycled
                        // by the framework when the list tries to clean up memory
                        Bitmap bitmap = Bitmap.createBitmap(rowView.getDrawingCache(false));
                        rowView.setDrawingCacheEnabled(false);

                        Rect listBounds = new Rect();
                        getGlobalVisibleRect(listBounds, null);

                        startDragging(bitmap, listBounds.left, y);
                        mDragPos = getChildAdapterPosition(rowView);
                        mFirstDragPos = mDragPos;
                        mWasFirstExpansion = true;
                        // init the current height.
                        mTotalHeight = getHeight();
                        int touchSlop = mTouchSlop;
                        mUpperBound = Math.min(y - touchSlop, mTotalHeight / 3);
                        mLowerBound = Math.max(y + touchSlop, mTotalHeight * 2 / 3);
                        return false;
                    }

                    mDragView = null;
                }
            }
        }
        return super.onInterceptTouchEvent(e);
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public boolean onTouchEvent(@NonNull final MotionEvent e) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(e);
        }
        if ((mOnDragListener != null || mOnDropListener != null) && mDragView != null) {
            int action = e.getAction();
            switch (action) {
                // end of a drag; either a drop, or a removal action
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect r = new Rect();
                    mDragView.getDrawingRect(r);
                    stopDragging();

                    // handle removal listeners
                    if (mRemoveMode == SLIDE_RIGHT && e.getX() > r.left + (r.width() * 3 / 4)) {
                        if (mOnRemoveListener != null) {
                            mOnRemoveListener.onRemove(mFirstDragPos);
                        }
                        unExpandViews(true);
                    } else if (mRemoveMode == SLIDE_LEFT && e.getX() < r.left + (r.width() / 4)) {
                        if (mOnRemoveListener != null) {
                            mOnRemoveListener.onRemove(mFirstDragPos);
                        }
                        unExpandViews(true);

                    } else {
                        // handle drop listener
                        //noinspection ConstantConditions
                        if (mOnDropListener != null && mDragPos >= 0
                                && mDragPos < getAdapter().getItemCount()) {
                            mOnDropListener.onDrop(mFirstDragPos, mDragPos);
                        }
                        unExpandViews(false);
                    }
                    break;

                // keep on dragging
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    int x = (int) e.getX();
                    int y = (int) e.getY();
                    dragView(x, y);
                    int itemIndex = pointToItemIndex(x, y);
                    if (itemIndex >= 0) {
                        if (action == MotionEvent.ACTION_DOWN || itemIndex != mDragPos) {
                            if (mOnDragListener != null) {
                                mOnDragListener.onDrag(mDragPos, itemIndex);
                            }
                            mDragPos = itemIndex;
                            expandViews(mWasFirstExpansion);
                            if (mWasFirstExpansion) {
                                mWasFirstExpansion = false;
                            }
                        }

                        // speed == the number of pixels (4 or 16) that a scroll will be done.
                        // i.e., slower (4) or faster (16)
                        int speed = 0;
                        // adjustScrollBounds
                        if (y >= mTotalHeight / 3) {
                            mUpperBound = mTotalHeight / 3;
                        }
                        if (y <= mTotalHeight * 2 / 3) {
                            mLowerBound = mTotalHeight * 2 / 3;
                        }
                        if (y > mLowerBound) {
                            // scroll the list up a bit
                            speed = y > (mTotalHeight + mLowerBound) / 2 ? 16 : 4;
                        } else if (y < mUpperBound) {
                            // scroll the list down a bit
                            speed = y < mUpperBound / 2 ? -16 : -4;
                        }
                        if (speed != 0) {
                            View rowView = findChildViewUnder(x, y);
                            if (rowView != null) {
                                int position = getChildAdapterPosition(rowView);
                                int offset = rowView.getTop() - speed;
                                mLayoutManager.scrollToPositionWithOffset(position, offset);
                            }
                        }
                    }
                    break;
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    /**
     * Wrapper for {@link #findChildViewUnder}.
     * /**
     * Find the position of the topmost view under the given point.
     *
     * @param x Horizontal position in pixels to search
     * @param y Vertical position in pixels to search
     *
     * @return The position of the child view under (x, y) or NO_POSITION if no matching
     * child is found
     */
    private int pointToPosition(final float x,
                                final float y) {
        View child = findChildViewUnder(x, y);
        return child == null ? NO_POSITION : getChildAdapterPosition(child);
    }

    /**
     * Convert a vertical offset to an item index.
     * <p>
     * 2019-03-05: need to pass the x as well so we can have padding/margins.
     * Original code always used x=0 which meant ANY padding made the pos==NO_POSITION
     *
     * @param x X in local coordinate
     * @param y Y in local coordinate
     *
     * @return the index
     */
    private int pointToItemIndex(final int x,
                                 final int y) {
        if (mItemHeight == 0) {
            // uses the height of the FIRST item, assuming all others are equal height.
            mItemHeight = getChildAt(0).getHeight();
        }

        int adjusted_y = y - mDragPoint - (mItemHeight / 2);
        int pos = pointToPosition(x, adjusted_y);
        if (pos >= 0) {
            if (pos <= mFirstDragPos) {
                pos += 1;
            }
        } else if (adjusted_y < 0) {
            pos = 0;
        }

        Logger.debug(this,"pointToItemIndex",
                     "y=" + y, "adjusted_y=" + adjusted_y,
                     "pos(x,y)=" + pointToPosition(x,y),
                     "pos(x,adjusted_y)=" + pointToPosition(x, adjusted_y),
                     "final pos=" + pos
        );
        return pos;
    }

    /**
     * Restore size and visibility for all list items.
     *
     * @param deletion {@code true} if we just deleted a row; {@code false} if it was a drop.
     */
    private void unExpandViews(final boolean deletion) {
        for (int i = 0; ; i++) {
            View rowView = getChildAt(i);
            if (rowView == null) {
                if (deletion) {
                    // HACK force update of mItemCount
                    int position = mLayoutManager.findFirstVisibleItemPosition();
                    int offset = getChildAt(0).getTop();
                    setAdapter(getAdapter());
                    mLayoutManager.scrollToPositionWithOffset(position, offset);
                    // end hack
                }
                // force children to be recreated where needed
                layoutChildren();

                rowView = getChildAt(i);
                if (rowView == null) {
                    break;
                }
            }

            if (isDraggableRow(rowView)) {
                ViewGroup.LayoutParams layoutParams = rowView.getLayoutParams();
                if (mSavedHeight != null) {
                    layoutParams.height = mSavedHeight;
                } else {
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                rowView.setLayoutParams(layoutParams);
                rowView.setVisibility(View.VISIBLE);
                rowView.setPadding(rowView.getPaddingLeft(), 0, rowView.getPaddingRight(), 0);
            }
        }
    }

    /**
     * Adjust visibility and size to make it appear as though an item is being dragged around
     * and other items are making room for it.
     * <p>
     * If dropping the item would result in it still being in the same place, then make the
     * dragged list item's size small, but make the item invisible.
     * <p>
     * Otherwise, if the dragged list item is still on screen, make it as small as possible
     * and expand the item below the insert point.
     * <p>
     * If the dragged item is not on screen, only expand the item below the current insert point.
     */
    private void expandViews(final boolean firstTime) {

        int firstVisPos = mLayoutManager.findFirstVisibleItemPosition();

        // Find the effective child number that we are hovering over
        int child = mDragPos - firstVisPos - 1;
        if (mDragPos > mFirstDragPos) {
            // If the current drag position is past the 'invisible' dragged position, add 1
            child++;
        }

        // Get the view that corresponds to the row being dragged, if present in current set of rows
        View first = getChildAt(mFirstDragPos - firstVisPos);

        // Loop through all visible views, adjusting them
        for (int i = 0; ; i++) {
            // Get next row, break if finished
            View rowView = getChildAt(i);
            if (rowView == null) {
                break;
            }

            // If this is a 'draggable' row, process it
            if (isDraggableRow(rowView)) {
                // Set the default padding at top/bottom (we may have previously changed it)
                rowView.setPadding(rowView.getPaddingLeft(), 0, rowView.getPaddingRight(), 0);

                // Get the height of the current view, and save it if not saved already
                ViewGroup.LayoutParams layoutParams = rowView.getLayoutParams();
                if (mSavedHeight == null) {
                    // Save the height the first time we get it. We make the assumption that
                    // all rows will be the same height, whether that is a fixed value
                    // or 'wrap-contents'/'fill-parent'.
                    mSavedHeight = layoutParams.height;
                }
                // Set the height to the previously saved height.
                layoutParams.height = mSavedHeight;

                int visibility = View.VISIBLE;

                // processing the item that is being dragged
                // If this view is the actual row we are dragging...then shrink it...except
                if (rowView.equals(first)) {
                    // ...if we are here the first time. The first time in, the user is
                    if (firstTime) {
                        // hovering over the original location, so we just make it invisible.
                        visibility = View.INVISIBLE;
                    } else {
                        // hovering over the original location
                        layoutParams.height = 1;
                    }
                }

                // If the drag position is above the top of the list then pad the top item
                if (child < 0) {
                    // If the current view is the first item OR second item and we are
                    // dragging first then pad its top.
                    if (i == 0 || (i == 1 && mFirstDragPos == 0)) {
                        // Position prior to first item; add padding on the top
                        //noinspection ConstantConditions
                        rowView.setPadding(rowView.getPaddingLeft(), mDragView.getHeight(),
                                           rowView.getPaddingRight(), 0);
                    }
                } else if (i == child) {
                    // The user is hovering over the current row, so pad the bottom
                    //noinspection ConstantConditions
                    rowView.setPadding(rowView.getPaddingLeft(), 0,
                                       rowView.getPaddingRight(), mDragView.getHeight());
                }

                // Now apply the height and visibility to the current view, invalidate it, and loop.
                rowView.setLayoutParams(layoutParams);
                rowView.setVisibility(visibility);
                rowView.invalidate();
            }
        }
        // Request re-layout since we changed the items layout and not doing this
        // would cause bogus hit-box calculation in myPointToPosition
        layoutChildren();
    }

    /** mimic ListView method. */
    private void layoutChildren() {
        // nop; TEST: can probably be eliminated
    }

    @NonNull
    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        }
        return mWindowManager;
    }

    /**
     * A row is draggable if it has a drag icon.
     */
    private boolean isDraggableRow(@NonNull final View view) {
        return view.findViewById(mGrabberId) != null;
    }

    private void startDragging(@NonNull final Bitmap bm,
                               final int x,
                               final int y) {
        stopDragging();

        mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP | Gravity.START;
        mWindowParams.x = x;
        mWindowParams.y = y - mDragPoint + mCoordinatesOffset;

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = 0;

        ImageView v = new ImageView(getContext());
        v.setBackgroundColor(mDndBackgroundColor);
        v.setImageBitmap(bm);
        mDragBitmap = bm;

        getWindowManager().addView(v, mWindowParams);
        mDragView = v;
    }

    private void dragView(final int x,
                          final int y) {
        float alpha = 1.0f;
        //noinspection ConstantConditions
        int width = mDragView.getWidth();

        if (mRemoveMode == SLIDE_RIGHT) {
            if (x > width / 2) {
                alpha = ((float) (width - x)) / (width / 2f);
            }
            mWindowParams.alpha = alpha;
        } else if (mRemoveMode == SLIDE_LEFT) {
            if (x < width / 2) {
                alpha = ((float) x) / (width / 2f);
            }
            mWindowParams.alpha = alpha;
        }
        mWindowParams.y = y - mDragPoint + mCoordinatesOffset;
        mWindowManager.updateViewLayout(mDragView, mWindowParams);
    }

    private void stopDragging() {
        if (mDragView != null) {
            getWindowManager().removeView(mDragView);
            mDragView.setImageDrawable(null);
            mDragView = null;
        }
        if (mDragBitmap != null) {
            mDragBitmap.recycle();
            mDragBitmap = null;
        }
    }

    @SuppressWarnings("unused")
    public void setOnDragListener(@NonNull final OnDragListener listener) {
        mOnDragListener = listener;
    }

    public void setOnDropListener(@NonNull final OnDropListener listener) {
        mOnDropListener = listener;
    }

    @SuppressWarnings("unused")
    public void setOnRemoveListener(@NonNull final OnRemoveListener listener) {
        mOnRemoveListener = listener;
    }

    public interface OnDragListener {

        void onDrag(int fromPosition,
                    int toPosition);
    }

    public interface OnDropListener {

        void onDrop(int fromPosition,
                    int toPosition);
    }

    public interface OnRemoveListener {

        void onRemove(int position);
    }

}

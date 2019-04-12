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
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;

/**
 * TouchListView from CommonsWare which is based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2.
 * <p>
 * https://github.com/timsu/cwac-touchlist
 * <p>
 * Customizable attributes:
 * <pre>
 *      {@code
 *      <declare-styleable name="TouchListView">
 *          <attr name="normal_height" format="dimension" />
 *          <attr name="expanded_height" format="dimension" />
 *          <attr name="grabber" format="reference" />
 *          <attr name="dnd_background" format="color" />
 *          <attr name="remove_mode">
 *              <enum name="none" value="-1" />
 *              <enum name="fling" value="0" />
 *              <enum name="slideRight" value="1" />
 *              <enum name="slideLeft" value="2" />
 *          </attr>
 *      </declare-styleable>
 *      }
 * </pre>
 * <p>
 * normal_height:
 * The height of one of your regular rows.
 * Default: calculated assuming all rows are equal height.
 * <p>
 * expanded_height:
 * The largest possible height of one of your rows.
 * Default: the value of normal_height.
 * <p>
 * grabber:
 * The android:id value of an icon in your rows that should be used as the "grab handle"
 * for the drag-and-drop operation (required)
 * <p>
 * dnd_background:
 * A colour to use as the background of your row when it is being dragged
 * Default: fully transparent.
 * <p>
 * remove_mode:
 * ="none"         (default) user cannot remove entries
 * ="slideRight"   user can remove entries by dragging to the right quarter of the list
 * ="slideLeft"    user can remove entries by dragging to the left quarter of the list)
 * ="fling"        ...not quite sure what this does
 * <p>
 */
public class TouchListView
        extends ListView {

    /** {@link #mRemoveMode}. */
    private static final int MODE_NOT_SET = -1;
    private static final int FLING = 0;
    private static final int SLIDE_RIGHT = 1;
    private static final int SLIDE_LEFT = 2;

    private final Rect mTempRect = new Rect();
    private final int mTouchSlop;

    @IdRes
    private final int mGrabberId;

    /** Color.TRANSPARENT by default. */
    @ColorInt
    private final int mDndBackgroundColor;

    private final int mRemoveMode;

    /** Height of a row in pixels. */
    private int mItemHeight;
    //private int mItemHeightExpanded=-1;

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

    /** The height of the ListView. */
    private int mHeight;
    @Nullable
    private GestureDetector mGestureDetector;

    /** Set to <tt>true</tt> at start of a new drag operation. */
    private boolean mWasFirstExpansion;
    @Nullable
    private Integer mSavedHeight;

    /**
     * Constructor used when instantiating Views programmatically.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     */
    public TouchListView(@NonNull final Context context) {
        this(context, null);
    }

    /**
     * Constructor used by the LayoutInflater.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param attrs   The attributes of the XML t that is inflating the view.
     */
    public TouchListView(@NonNull final Context context,
                         @Nullable final AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }

    /**
     * Constructor used by the LayoutInflater if there was a 'style' attribute.
     *
     * @param context      The Context the view is running in, through which it can
     *                     access the current theme, resources, etc.
     * @param attrs        The attributes of the XML t that is inflating the view.
     * @param defStyleAttr An attribute in the current theme that contains a
     *                     reference to a style resource that supplies default values for
     *                     the view. Can be 0 to not look for defaults.
     */
    public TouchListView(@NonNull final Context context,
                         @Nullable final AttributeSet attrs,
                         final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /**
     * Constructor.
     *
     * @param context      The Context the view is running in, through which it can
     *                     access the current theme, resources, etc.
     * @param attrs        The attributes of the XML t that is inflating the view.
     * @param defStyleAttr An attribute in the current theme that contains a
     *                     reference to a style resource that supplies default values for
     *                     the view. Can be 0 to not look for defaults.
     * @param defStyleRes  A resource identifier of a style resource that
     *                     supplies default values for the view, used only if
     *                     defStyleAttr is 0 or can not be found in the theme. Can be 0
     *                     to not look for defaults.
     */
    public TouchListView(@NonNull final Context context,
                         @Nullable final AttributeSet attrs,
                         final int defStyleAttr,
                         final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TouchListView,
                                                      defStyleAttr, defStyleRes);

        mGrabberId = a.getResourceId(R.styleable.TouchListView_grabber, -1);
        mRemoveMode = a.getInt(R.styleable.TouchListView_remove_mode, MODE_NOT_SET);
        mItemHeight = a.getDimensionPixelSize(R.styleable.TouchListView_normal_height, 0);
        //mItemHeightExpanded = typedArray
        //     .getDimensionPixelSize(R.styleable.TouchListView_expanded_height, mItemHeight);
        mDndBackgroundColor = a.getColor(R.styleable.TouchListView_dnd_background,
                                         Color.TRANSPARENT);

        a.recycle();
    }

    /** {@inheritDoc}. */
    @Override
    public final void addHeaderView(@NonNull final View v,
                                    @NonNull final Object data,
                                    final boolean isSelectable) {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc}. */
    @Override
    public final void addHeaderView(@NonNull final View v) {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc}. */
    @Override
    public final void addFooterView(@NonNull final View v,
                                    @NonNull final Object data,
                                    final boolean isSelectable) {
        if (mRemoveMode == SLIDE_LEFT || mRemoveMode == SLIDE_RIGHT) {
            throw new UnsupportedOperationException();
        }
    }

    /** {@inheritDoc}. */
    @Override
    public final void addFooterView(@NonNull final View v) {
        if (mRemoveMode == SLIDE_LEFT || mRemoveMode == SLIDE_RIGHT) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * React to touch events.
     * <p>
     * <p>
     * {@inheritDoc}.
     */
    @Override
    @CallSuper
    public boolean onInterceptTouchEvent(@NonNull final MotionEvent ev) {
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
                                        Rect r = mTempRect;
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
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    int x = (int) ev.getX();
                    int y = (int) ev.getY();
                    int itemNumber = pointToPosition(x, y);
                    if (itemNumber == AdapterView.INVALID_POSITION) {
                        break;
                    }

                    View rowView = getChildAt(itemNumber - getFirstVisiblePosition());

                    if (isDraggableRow(rowView)) {
                        mDragPoint = y - rowView.getTop();
                        mCoordinatesOffset = ((int) ev.getRawY()) - y;
                        View dragHandle = rowView.findViewById(mGrabberId);
                        Rect r = mTempRect;
                        //dragHandle.getDrawingRect(r);

                        r.left = dragHandle.getLeft();
                        r.right = dragHandle.getRight();
                        r.top = dragHandle.getTop();
                        r.bottom = dragHandle.getBottom();

                        if ((r.left < x) && (x < r.right)) {
                            rowView.setDrawingCacheEnabled(true);
                            // Create a copy of the drawing cache so that it does not get recycled
                            // by the framework when the list tries to clean up memory
                            Bitmap bitmap = Bitmap.createBitmap(rowView.getDrawingCache());
                            rowView.setDrawingCacheEnabled(false);

                            Rect listBounds = new Rect();

                            getGlobalVisibleRect(listBounds, null);

                            startDragging(bitmap, listBounds.left, y);
                            mDragPos = itemNumber;
                            mFirstDragPos = mDragPos;
                            mWasFirstExpansion = true;
                            mHeight = getHeight();
                            int touchSlop = mTouchSlop;
                            mUpperBound = Math.min(y - touchSlop, mHeight / 3);
                            mLowerBound = Math.max(y + touchSlop, mHeight * 2 / 3);
                            return false;
                        }

                        mDragView = null;
                    }
                    break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    /** {@inheritDoc}. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public boolean onTouchEvent(@NonNull final MotionEvent ev) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }
        if ((mOnDragListener != null || mOnDropListener != null) && mDragView != null) {
            int action = ev.getAction();
            switch (action) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect r = mTempRect;
                    mDragView.getDrawingRect(r);
                    stopDragging();

                    if (mRemoveMode == SLIDE_RIGHT && ev.getX() > r.left + (r.width() * 3 / 4)) {
                        if (mOnRemoveListener != null) {
                            mOnRemoveListener.onRemove(mFirstDragPos);
                        }
                        unExpandViews(true);
                    } else if (mRemoveMode == SLIDE_LEFT && ev.getX() < r.left + (r.width() / 4)) {
                        if (mOnRemoveListener != null) {
                            mOnRemoveListener.onRemove(mFirstDragPos);
                        }
                        unExpandViews(true);
                    } else {
                        if (mOnDropListener != null && mDragPos >= 0 && mDragPos < getCount()) {
                            mOnDropListener.onDrop(mFirstDragPos, mDragPos);
                        }
                        unExpandViews(false);
                    }
                    break;

                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    int x = (int) ev.getX();
                    int y = (int) ev.getY();
                    dragView(x, y);
                    int item = getItemForPosition(x, y);
                    if (item >= 0) {
                        if (action == MotionEvent.ACTION_DOWN || item != mDragPos) {
                            if (mOnDragListener != null) {
                                mOnDragListener.onDrag(mDragPos, item);
                            }
                            mDragPos = item;
                            doExpansion(mWasFirstExpansion);
                            if (mWasFirstExpansion) {
                                mWasFirstExpansion = false;
                            }
                        }
                        int speed = 0;
                        adjustScrollBounds(y);
                        if (y > mLowerBound) {
                            // scroll the list up a bit
                            speed = y > (mHeight + mLowerBound) / 2 ? 16 : 4;
                        } else if (y < mUpperBound) {
                            // scroll the list down a bit
                            speed = y < mUpperBound / 2 ? -16 : -4;
                        }
                        if (speed != 0) {
                            int ref = pointToPosition(0, mHeight / 2);
                            if (ref == AdapterView.INVALID_POSITION) {
                                //we hit a divider or an invisible view, check somewhere else
                                ref = pointToPosition(0, mHeight / 2 + getDividerHeight() + 64);
                            }
                            View rowView = getChildAt(ref - getFirstVisiblePosition());
                            if (rowView != null) {
                                int pos = rowView.getTop();
                                setSelectionFromTop(ref, pos - speed);
                            }
                        }
                    }
                    break;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * {@link #pointToPosition} doesn't consider invisible views, but we
     * need to, so implement a slightly different version.
     * We still need access to the original method, so we don't override it.
     * <p>
     * Maps a point to a position in the list.
     *
     * @param x X in local coordinate
     * @param y Y in local coordinate
     *
     * @return The position of the item which contains the specified point, or
     * {@link #INVALID_POSITION} if the point does not intersect an item.
     */
    private int myPointToPosition(final int x,
                                  final int y) {
        Rect frame = mTempRect;
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            child.getHitRect(frame);
            if (frame.contains(x, y)) {
                return getFirstVisiblePosition() + i;
            }
        }
        return INVALID_POSITION;
    }

    /**
     * Convert a vertical offset to an item index.
     * <p>
     * 2019-03-05: need to pass the x as well so we can have padding/margins.
     * Original code always used x=0 which meant ANY padding made the pos==INVALID_POSITION
     *
     * @param x X in local coordinate
     * @param y Y in local coordinate
     *
     * @return the index
     */
    private int getItemForPosition(final int x,
                                   final int y) {
        //TODO: do we need mItemHeight at all ? how about mSavedHeight ?
        if (mItemHeight == 0) {
            mItemHeight = getChildAt(0).getHeight();
        }
        int adjusted_y = y - mDragPoint - (mItemHeight / 2);
        int pos = myPointToPosition(x, adjusted_y);
        if (pos >= 0) {
            if (pos <= mFirstDragPos) {
                pos += 1;
            }
        } else if (adjusted_y < 0) {
            pos = 0;
        }

        return pos;
    }

    private void adjustScrollBounds(final int y) {
        if (y >= mHeight / 3) {
            mUpperBound = mHeight / 3;
        }
        if (y <= mHeight * 2 / 3) {
            mLowerBound = mHeight * 2 / 3;
        }
    }

    /**
     * Restore size and visibility for all list items.
     */
    private void unExpandViews(final boolean deletion) {
        //if(true) return;
        for (int i = 0; ; i++) {
            View rowView = getChildAt(i);
            if (rowView == null) {
                if (deletion) {
                    // HACK force update of mItemCount
                    int position = getFirstVisiblePosition();
                    int y = getChildAt(0).getTop();
                    setAdapter(getAdapter());
                    setSelectionFromTop(position, y);
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
                ViewGroup.LayoutParams params = rowView.getLayoutParams();
                if (mSavedHeight != null) {
                    params.height = mSavedHeight;
                } else {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                //params.height = mItemHeight;
                rowView.setLayoutParams(params);
                rowView.setVisibility(View.VISIBLE);
                //v.setBackgroundColor(Color.TRANSPARENT);
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
    private void doExpansion(final boolean firstTime) {

        // Find the effective child number that we are hovering over
        int child = mDragPos - getFirstVisiblePosition() - 1;
        if (mDragPos > mFirstDragPos) {
            // If the current drag position is past the 'invisible' dragged position, add 1
            child++;
        }

        // Get the view that corresponds to the row being dragged, if present in current set of rows
        View first = getChildAt(mFirstDragPos - getFirstVisiblePosition());

        // Loop through all visible views, adjusting them
        for (int i = 0; ; i++) {
            // Get next child, break if finished
            View rowView = getChildAt(i);
            if (rowView == null) {
                break;
            }

            // If this is a 'draggable' row, process it
            if (isDraggableRow(rowView)) {
                // Set the default padding at top/bot (we may have previously changed it)
                rowView.setPadding(rowView.getPaddingLeft(), 0, rowView.getPaddingRight(), 0);

                // Get the height of the current view, and save it if not saved already
                ViewGroup.LayoutParams params = rowView.getLayoutParams();
                if (mSavedHeight == null) {
                    // Save the height the first time we get it. We make the assumption that
                    // all rows will be the same height, whether that is a fixed value
                    // or 'wrap-contents'/'fill-parent'.
                    mSavedHeight = params.height;
                }
                // Set the height to the previously saved height.
                params.height = mSavedHeight;

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
                        params.height = 1;
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
                rowView.setLayoutParams(params);
                rowView.setVisibility(visibility);
                rowView.invalidate();
            }
        }
        // Request re-layout since we changed the items layout and not doing this
        // would cause bogus hit-box calculation in myPointToPosition
        layoutChildren();
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

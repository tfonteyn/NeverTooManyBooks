/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;

/**
 * 2019-04-10: still in use in BoB, mainly for the {@link FastScroller.SectionIndexerV2} use.
 * Not sure if any of the other old reasons to use it are still valid.
 * Review once better understood/tested.
 * <p>
 * <p>
 * Subclass of ListView that uses a local implementation of FastScroller to bypass
 * the deficiencies in the original Android version. See {@link FastScroller} for a discussion.
 * <p>
 * We need to subclass ListView because we need access to events that are only provided
 * by the subclass.
 * Customizable attributes:
 * <pre>
 *      {@code
 *      <declare-styleable name="FastScrollListView">
 *          <attr name="scrollHandle" format="reference" />
 *          <attr name="overlay" format="reference" />
 *          <attr name="textColor" format="color" />
 *      </declare-styleable>
 *      }
 * </pre>
 * All of them have default.
 *
 * @author Philip Warner
 */
public class FastScrollListView
        extends ListView {

    /** the thumb grabber icon for the scroller. */
    @DrawableRes
    private int mScrollHandleDrawableId;
    @DrawableRes
    private int mOverlayDrawableId;
    @ColorInt
    private int mTextColor;
    /** Active scroller, if any. */
    @Nullable
    private FastScroller mFastScroll;
    /** The actual 'user' listener, if any. */
    @Nullable
    private OnScrollListener mUserOnScrollListener;

    /**
     * Constructor used when instantiating Views programmatically.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     */
    public FastScrollListView(@NonNull final Context context) {
        super(context, null);
    }

    /**
     * Constructor used by the LayoutInflater.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param attrs   The attributes of the XML t that is inflating the view.
     */
    public FastScrollListView(@NonNull final Context context,
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
    public FastScrollListView(@NonNull final Context context,
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
    public FastScrollListView(@NonNull final Context context,
                              @Nullable final AttributeSet attrs,
                              final int defStyleAttr,
                              final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        /*
         * Set as the 'super' listener, forwarding events to the 'user' listener set
         * via {@link #setOnScrollListener(OnScrollListener)}.
         */
        super.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScroll(@NonNull final AbsListView view,
                                 final int firstVisibleItem,
                                 final int visibleItemCount,
                                 final int totalItemCount) {
                if (mFastScroll != null) {
                    mFastScroll.onScroll(firstVisibleItem, visibleItemCount, totalItemCount);
                }
                if (mUserOnScrollListener != null) {
                    mUserOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount,
                                                   totalItemCount);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull final AbsListView view,
                                             final int scrollState) {
                if (mUserOnScrollListener != null) {
                    mUserOnScrollListener.onScrollStateChanged(view, scrollState);
                }
            }
        });


        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.FastScrollListView, defStyleAttr, defStyleRes);

        mScrollHandleDrawableId = a.getResourceId(R.styleable.FastScrollListView_scrollHandle, -1);
        mOverlayDrawableId = a.getResourceId(R.styleable.FastScrollListView_overlay, -1);
        mTextColor = a.getColor(R.styleable.FastScrollListView_textColor, -1);

        a.recycle();
    }

    /** {@inheritDoc}. */
    @Override
    public void setFastScrollEnabled(final boolean enabled) {
        if (!enabled) {
            if (mFastScroll != null) {
                mFastScroll.stop();
                mFastScroll = null;
            }
        } else {
            if (mFastScroll == null) {
                mFastScroll = new FastScroller(getContext(), this,
                                               mScrollHandleDrawableId, mOverlayDrawableId,
                                               mTextColor);
            }
        }
    }

    /** {@inheritDoc}. */
    @Override
    public void setOnScrollListener(@NonNull final OnScrollListener l) {
        mUserOnScrollListener = l;
    }

    /**
     * Pass to scroller if defined, otherwise perform default actions.
     * <p>
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    protected void onSizeChanged(final int w,
                                 final int h,
                                 final int oldw,
                                 final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFastScroll != null) {
            mFastScroll.onSizeChanged(w, h);
        }
    }

    /**
     * Pass to scroller if defined, otherwise perform default actions.
     * <p>
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public boolean onTouchEvent(@NonNull final MotionEvent ev) {
        return mFastScroll != null && mFastScroll.onTouchEvent(ev)
                || super.onTouchEvent(ev);

    }

    /**
     * Pass to scroller if defined, otherwise perform default actions.
     * <p>
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public boolean onInterceptTouchEvent(@NonNull final MotionEvent ev) {
        return mFastScroll != null && mFastScroll.onInterceptTouchEvent(ev)
                || super.onInterceptTouchEvent(ev);
    }

    /**
     * Send draw() to the scroller as well.
     * <p>
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void draw(@NonNull final Canvas canvas) {
        super.draw(canvas);
        if (mFastScroll != null) {
            mFastScroll.draw(canvas);
        }
    }

    /**
     * Helper class for AbsListView to draw and control the Fast Scroll thumb.
     * <p>
     * This is a substantially modified version of the Android 2.3 FastScroller.
     * <p>
     * The original did not work correctly with ExpandableListViews; the thumb would work
     * for only a small portion of fully expanded views and exhibited odd behaviour with
     * view with a small number of groups but large children.
     * <p>
     * The underlying approach to scrolling with a summary in the original version was
     * also flawed: it translated a thumb position of 50% to mean that the middle summaryGroup
     * should be visible. While this may seem sensible, it is contrary to reasonable expectations
     * with scrollable lists: a thumb at 50% in any scrollable list should result in the list
     * being at the mid-point. With an expandableListView, this needs to take into account the total
     * number of items (groups and children), NOT just the summary groups. Doing what the original
     * implementation did is not only counter-intuitive, but also makes the thumb unusable in the
     * case of n groups, where one of those n has O(n) children, and is expanded.
     * In this case, the entire set of children will move through the screen based on the same
     * finger movement as moving between two unexpanded groups. In the more general case it can
     * be characterised as uneven scrolling if sections have widely varying sizes.
     * <p>
     * Finally, the original would fail to correctly place the overlay if setFastScrollEnabled was
     * called after the Activity had been fully drawn: this is because the only place that set the
     * overlay position was in the onSizeChanged event.
     * <p>
     * Combine this with the desire to display more than a single letter in the overlay,
     * and a rewrite was more or less essential.
     * <p>
     * The solution is:
     * <p>
     * - modify init() to fake an onSizeChanged event
     * - modify onSizeChanged() to make the overlay 75% of total width;
     * - modify draw() to handle arbitrary text (ellipsize if necessary)
     * - modify scrollTo() to just deal with list contents and not try to do any fancy
     * calculations about group position.
     * <p>
     * Because the original was in the android package, it had access to classes that we do not
     * have access to, so in some cases we now check if mList is an ExpandableListView rather than
     * checking if the adapter is an ExpandableListConnector.
     * <p>
     * *********************************************************
     * <p>
     * NOTE: any class implementing a SectionIndexer for this object MUST return flattened
     * positions in calls to getPositionForSection(), and will be passed flattened positions in
     * calls to getSectionForPosition().
     * <p>
     * *********************************************************
     */
    public static class FastScroller {

        /** Minimum number of pages to justify showing a fast scroll thumb. */
        private static final int MIN_PAGES = 4;
        /** Scroll thumb not showing. */
        private static final int STATE_NONE = 0;
        // ENHANCE: Not implemented yet - fade-in transition
        // private static final int STATE_ENTER = 1;
        /** Scroll thumb visible and moving along with the scrollbar. */
        private static final int STATE_VISIBLE = 2;
        /** Scroll thumb being dragged by user. */
        private static final int STATE_DRAGGING = 3;
        /** Scroll thumb fading out due to inactivity timeout. */
        private static final int STATE_EXIT = 4;

        /** This value is in SP taken from the Android sources. */
        private static final int LARGE_TEXT_SIZE_IN_SP = 22;
        /** Text size after scaling (or same as large, if scaling failed). */
        private static int sLargeTextScaledSizeInSp = 22;

        private final int mOverlaySize;

        private final Handler mHandler = new Handler();
        @NonNull
        private final AbsListView mList;
        /** Color for the text inside the overlay. */
        @ColorInt
        private final int mTextColor;
        private final RectF mOverlayPos;
        private final TextPaint mPaint;
        private final ScrollFade mScrollFade;
        private int mThumbH;
        private int mThumbW;
        private int mThumbY;
        private boolean mScrollCompleted;
        private int mVisibleItem;
        private int mListOffset;
        private int mItemCount = -1;
        private boolean mLongList;
        private Object[] mSections;
        @Nullable
        private String mSectionTextV1;
        @Nullable
        private String[] mSectionTextV2;
        /** If we have text (either v2 or v1 style), will be set to true == draw the overlay. */
        private boolean mDrawOverlay;
        private int mState;
        private boolean mChangedBounds;
        private BaseAdapter mListAdapter;
        /** The grabber for the user to scroll with. */
        private Drawable mThumbDrawable;
        /** The overlay applied to the section line1/2 showing the 'current' row. */
        private Drawable mOverlayDrawable;
        /** android.widget.SectionIndexer. */
        @Nullable
        private SectionIndexer mSectionIndexerV1;
        /** our own. */
        @Nullable
        private SectionIndexerV2 mSectionIndexerV2;

        /**
         * Constructor.
         *
         * @param context           caller context
         * @param listView          the list
         * @param thumbDrawableId   grabber icon, can be 0
         * @param overlayDrawableId overlay, can be 0
         */
        FastScroller(@NonNull final Context context,
                     @NonNull final AbsListView listView,
                     @DrawableRes final int thumbDrawableId,
                     @DrawableRes final int overlayDrawableId,
                     @ColorInt final int textColor) {

            mList = listView;
            // the default passed in should have been valid, or -1
            mTextColor = textColor != -1 ? textColor : android.R.attr.textColorPrimary;

            try {
                mThumbDrawable = context.getDrawable(thumbDrawableId);
            } catch (Resources.NotFoundException e) {
                // compass.... yeah well... why not...
                mThumbDrawable = context.getDrawable(android.R.drawable.ic_menu_compass);
            }

            try {
                mOverlayDrawable = context.getDrawable(overlayDrawableId);
            } catch (Resources.NotFoundException e) {
                // bad default yes, the background is black.
                // alternative: alert_light_frame
                mOverlayDrawable = context.getDrawable(android.R.drawable.alert_dark_frame);
            }

            // Determine the overlay size based on 3 x LargeTextSize;
            // if we get an error, just use a hard-coded guess.
            try {
                float scale = context.getResources().getDisplayMetrics().scaledDensity;
                sLargeTextScaledSizeInSp = (int) (LARGE_TEXT_SIZE_IN_SP * scale);

            } catch (RuntimeException e) {
                // Not a critical value; just try to get it close.
                sLargeTextScaledSizeInSp = LARGE_TEXT_SIZE_IN_SP;
            }

            mOverlaySize = 3 * sLargeTextScaledSizeInSp;

            // Can't use the view width yet, because it has probably not been set up so we just
            // use the native width. It will be set later when we come to actually draw it.
            mThumbW = mThumbDrawable.getIntrinsicWidth();
            mThumbH = mThumbDrawable.getIntrinsicHeight();
            mChangedBounds = true;

            mScrollCompleted = true;

            getSections();

            mOverlayPos = new RectF();
            mScrollFade = new ScrollFade();

            mPaint = new TextPaint();
            mPaint.setAntiAlias(true);
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setTextSize(mOverlaySize / 3f);
            mPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            TypedArray ta;
            try {
                ta = context.getTheme().obtainStyledAttributes(new int[]{mTextColor});
            } catch (Resources.NotFoundException e) {
                ta = context.getTheme()
                            .obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
            }

            ColorStateList csl = ta.getColorStateList(ta.getIndex(0));
            // is there ever a null situation if we pass in a valid attribute?
            if (csl != null) {
                mPaint.setColor(csl.getDefaultColor());
            }
            ta.recycle();

            mState = STATE_NONE;

            // Send a fake onSizeChanged so that overlay position is correct if
            // this is called after Activity is stable
            final int w = mList.getWidth();
            final int h = mList.getHeight();
            onSizeChanged(w, h);
        }

        private int getState() {
            return mState;
        }

        private void setState(final int state) {
            switch (state) {
                case STATE_NONE:
                    mHandler.removeCallbacks(mScrollFade);
                    mList.invalidate();
                    break;
                case STATE_VISIBLE:
                    if (mState != STATE_VISIBLE) { // Optimization
                        resetThumbPos();
                    }
                    // Fall through
                case STATE_DRAGGING:
                    mHandler.removeCallbacks(mScrollFade);
                    break;
                case STATE_EXIT:
                    mList.invalidate();
                    break;
            }
            mState = state;
        }

        private void resetThumbPos() {
            final int viewWidth = mList.getWidth();
            // Bounds are always top right. Y coordinate get's translated during draw
            // For reference, the thumb itself is approximately 50% as wide as the underlying graphic
            // so 1/6th of the width means the thumb is approximately 1/12 the width.
            mThumbW = (int) (sLargeTextScaledSizeInSp * 2.5); // viewWidth / 6 ; //mOverlaySize *3/4 ; //64; //mCurrentThumb.getIntrinsicWidth();
            mThumbH = (int) (sLargeTextScaledSizeInSp * 2.5); //viewWidth / 6 ; //mOverlaySize *3/4; //52; //mCurrentThumb.getIntrinsicHeight();

            mThumbDrawable.setBounds(viewWidth - mThumbW, 0, viewWidth, mThumbH);
            mThumbDrawable.setAlpha(ScrollFade.ALPHA_MAX);
        }

        void stop() {
            setState(STATE_NONE);
        }

        @SuppressWarnings("WeakerAccess")
        public void draw(@NonNull final Canvas canvas) {
            if (mState == STATE_NONE) {
                return;
            }

            final int y = mThumbY;
            final int viewWidth = mList.getWidth();

            int alpha = -1;
            if (mState == STATE_EXIT) {
                alpha = mScrollFade.getAlpha();
                if (alpha < ScrollFade.ALPHA_MAX / 2) {
                    mThumbDrawable.setAlpha(alpha * 2);
                }
                int left = viewWidth - (mThumbW * alpha) / ScrollFade.ALPHA_MAX;
                mThumbDrawable.setBounds(left, 0, viewWidth, mThumbH);
                mChangedBounds = true;
            }

            canvas.translate(0, y);
            mThumbDrawable.draw(canvas);
            canvas.translate(0, -y);

            // If user is dragging the scroll bar, draw the alphabet overlay
            // mDrawOverlay==true when we have section text.
            if (mState == STATE_DRAGGING && mDrawOverlay) {
                final TextPaint paint = mPaint;
                final RectF rectF = mOverlayPos;

                String line1;
                String line2 = null;
                // If there is no V2 data, use the V1 data
                if (mSectionTextV2 == null) {
                    //noinspection ConstantConditions
                    line1 = getText(mSectionTextV1, paint);
                    line2 = null;
                } else {
                    line1 = getText(mSectionTextV2[0], paint);
                    // is there a line 2?
                    if ((mSectionTextV2.length > 1
                            && mSectionTextV2[1] != null
                            && !mSectionTextV2[1].isEmpty())) {
                        line2 = getText(mSectionTextV2[1], paint);
                    }
                }

                // If there are two lines, expand the box
                if (line2 != null) {
                    Rect pos = mOverlayDrawable.getBounds();
                    Rect posSave = new Rect(pos);
                    pos.set(pos.left, pos.top, pos.right, pos.bottom + pos.height() / 2);
                    mOverlayDrawable.setBounds(pos);
                    mOverlayDrawable.draw(canvas);
                    // reset for next time
                    mOverlayDrawable.setBounds(posSave);
                } else {
                    mOverlayDrawable.draw(canvas);
                }

                float tx = (int) (rectF.left + rectF.right) / 2f;
                float ty = (int) (rectF.bottom + rectF.top) / 2f + mOverlaySize / 6f;

                // Draw the first line with base of text at - descent : so it is vertically centred
                canvas.drawText(line1, tx, ty - paint.descent(), paint);

                if (line2 != null) {
                    // Draw the second line, but smaller than first
                    float s = paint.getTextSize();
                    paint.setTextSize(s * 0.7f);
                    canvas.drawText(getText(line2, paint), tx, ty + s, paint);
                    // reset for next time
                    paint.setTextSize(s);
                }

            } else if (mState == STATE_EXIT) {
                if (alpha == 0) { // Done with exit
                    setState(STATE_NONE);
                } else {
                    mList.invalidate();
                }
            }
        }

        private String getText(@NonNull final String line,
                               @NonNull final TextPaint paint) {
            float avail = (mOverlayPos.right - mOverlayPos.left) * 0.8f;
            return TextUtils.ellipsize(line, paint, avail, TextUtils.TruncateAt.END).toString();
        }

        void onSizeChanged(final int w,
                           final int h) {
            mThumbDrawable.setBounds(w - mThumbW, 0, w, mThumbH);
            final RectF pos = mOverlayPos;
            // Now, Make it 75% of total available space
            pos.left = w / 8f;
            pos.right = pos.left + w * 0.75f;
            pos.top = h / 10f; // 10% from top
            pos.bottom = pos.top + mOverlaySize;
            mOverlayDrawable.setBounds((int) pos.left, (int) pos.top,
                                       (int) pos.right, (int) pos.bottom);
        }

        void onScroll(final int firstVisibleItem,
                      final int visibleItemCount,
                      final int totalItemCount) {
            // Are there enough pages to require fast scroll? Recompute only if total count changes
            if (mItemCount != totalItemCount && visibleItemCount > 0) {
                mItemCount = totalItemCount;
                mLongList = mItemCount / visibleItemCount >= MIN_PAGES;
            }
            if (!mLongList) {
                if (mState != STATE_NONE) {
                    setState(STATE_NONE);
                }
                return;
            }
            if (totalItemCount - visibleItemCount > 0 && mState != STATE_DRAGGING) {
                mThumbY = ((mList.getHeight() - mThumbH) * firstVisibleItem)
                        / (totalItemCount - visibleItemCount);
                if (mChangedBounds) {
                    resetThumbPos();
                    mChangedBounds = false;
                }
            }
            mScrollCompleted = true;
            if (firstVisibleItem == mVisibleItem) {
                return;
            }
            mVisibleItem = firstVisibleItem;
            if (mState != STATE_DRAGGING) {
                setState(STATE_VISIBLE);
                mHandler.postDelayed(mScrollFade, 1500);
            }
        }

        private void getSections() {
            Adapter adapter = mList.getAdapter();
            mSectionIndexerV1 = null;
            mSectionIndexerV2 = null;
            if (adapter instanceof HeaderViewListAdapter) {
                mListOffset = ((HeaderViewListAdapter) adapter).getHeadersCount();
                adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
            }
            if (mList instanceof ExpandableListView) {
                ExpandableListAdapter expAdapter = ((ExpandableListView) mList).getExpandableListAdapter();
                if (expAdapter instanceof SectionIndexer) {
                    mSectionIndexerV1 = (SectionIndexer) expAdapter;
                    mListAdapter = (BaseAdapter) adapter;
                    mSections = mSectionIndexerV1.getSections();
                } else if (expAdapter instanceof SectionIndexerV2) {
                    mSectionIndexerV2 = (SectionIndexerV2) expAdapter;
                    mListAdapter = (BaseAdapter) adapter;
                }
            } else {
                if (adapter instanceof SectionIndexer) {
                    mListAdapter = (BaseAdapter) adapter;
                    mSectionIndexerV1 = (SectionIndexer) adapter;
                    mSections = mSectionIndexerV1.getSections();
                } else if (adapter instanceof SectionIndexerV2) {
                    mListAdapter = (BaseAdapter) adapter;
                    mSectionIndexerV2 = (SectionIndexerV2) adapter;
                } else {
                    mListAdapter = (BaseAdapter) adapter;
                    mSections = new String[]{" "};
                }
            }
        }

        private void scrollTo(final float position) {
            int count = mList.getCount();
            mScrollCompleted = false;
            final Object[] sections = mSections;
            int sectionIndex;

            int index = (int) (position * count);
            // This INCLUDES ExpandableListView
            if (mList instanceof ListView) {
                mList.setSelectionFromTop(index + mListOffset, 0);
            } else {
                mList.setSelection(index + mListOffset);
            }
            if (mSectionIndexerV2 != null) {
                mSectionTextV2 = mSectionIndexerV2.getSectionTextForPosition(index);
            } else {
                if ((sections != null && sections.length > 1) && (mSectionIndexerV1 != null)) {
                    sectionIndex = mSectionIndexerV1.getSectionForPosition(index);
                    if (sectionIndex >= 0 && sectionIndex < sections.length) {
                        mSectionTextV1 = sections[sectionIndex].toString();
                    } else {
                        mSectionTextV1 = null;
                    }
                } else {
                    mSectionTextV1 = null;
                }
            }

            mDrawOverlay = (mSectionTextV2 != null) || (mSectionTextV1 != null && !mSectionTextV1.isEmpty());
        }

        private void cancelFling() {
            // Cancel the list fling
            MotionEvent cancelFling = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            mList.onTouchEvent(cancelFling);
            cancelFling.recycle();
        }

        boolean onInterceptTouchEvent(@NonNull final MotionEvent ev) {
            if (mState > STATE_NONE && ev.getAction() == MotionEvent.ACTION_DOWN) {
                if (ev.getX() > mList.getWidth() - mThumbW
                        && ev.getY() >= mThumbY
                        && ev.getY() <= mThumbY + mThumbH) {
                    setState(STATE_DRAGGING);
                    return true;
                }
            }
            return false;
        }

        boolean onTouchEvent(@NonNull final MotionEvent me) {
            if (mState == STATE_NONE) {
                return false;
            }
            switch (me.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (me.getX() > mList.getWidth() - mThumbW
                            && me.getY() >= mThumbY
                            && me.getY() <= mThumbY + mThumbH) {

                        setState(STATE_DRAGGING);
                        if (mListAdapter == null) {
                            getSections();
                        }

                        cancelFling();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mState == STATE_DRAGGING) {
                        setState(STATE_VISIBLE);
                        final Handler handler = mHandler;
                        handler.removeCallbacks(mScrollFade);
                        handler.postDelayed(mScrollFade, 1000);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mState == STATE_DRAGGING) {
                        final int viewHeight = mList.getHeight();
                        // Jitter
                        int newThumbY = (int) me.getY() - mThumbH + 10;
                        if (newThumbY < 0) {
                            newThumbY = 0;
                        } else if (newThumbY + mThumbH > viewHeight) {
                            newThumbY = viewHeight - mThumbH;
                        }
                        // ENHANCE: would be nice to use ViewConfiguration.get(context).getScaledTouchSlop()???
                        if (Math.abs(mThumbY - newThumbY) < 2) {
                            return true;
                        }
                        mThumbY = newThumbY;
                        // If the previous scrollTo is still pending
                        if (mScrollCompleted) {
                            scrollTo((float) mThumbY / (viewHeight - mThumbH));
                        }
                        return true;
                    }
                    break;
            }
            return false;
        }

        /**
         * Better interface that just gets text for rows as needed rather
         * than having to build a huge index at start.
         */
        public interface SectionIndexerV2 {

            @Nullable
            String[] getSectionTextForPosition(int position);
        }

        class ScrollFade
                implements Runnable {

            static final int ALPHA_MAX = 208;
            static final long FADE_DURATION = 200;
            long mStartTime;
            long mFadeDuration;

            void startFade() {
                mFadeDuration = FADE_DURATION;
                mStartTime = SystemClock.uptimeMillis();
                setState(STATE_EXIT);
            }

            int getAlpha() {
                if (getState() != STATE_EXIT) {
                    return ALPHA_MAX;
                }
                int alpha;
                long now = SystemClock.uptimeMillis();
                if (now > mStartTime + mFadeDuration) {
                    alpha = 0;
                } else {
                    alpha = (int) (ALPHA_MAX - ((now - mStartTime) * ALPHA_MAX) / mFadeDuration);
                }
                return alpha;
            }

            @Override
            public void run() {
                if (getState() != STATE_EXIT) {
                    startFade();
                    return;
                }

                if (getAlpha() > 0) {
                    mList.invalidate();
                } else {
                    setState(STATE_NONE);
                }
            }
        }
    }
}

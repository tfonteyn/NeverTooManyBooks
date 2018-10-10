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
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.ListView;

/**
 * Subclass of ListView that uses a local implementation of FastScroller to bypass
 * the deficiencies in the original Android version. See fastScroller.java for a discussion.
 *
 * We need to subclass ListView because we need access to events that are only provided
 * by the subclass.
 *
 * @author Philip Warner
 */
public class FastScrollListView extends ListView {

    /** Active scroller, if any */
    @Nullable
    private FastScroller mScroller = null;

    @Nullable
    private OnScrollListener mOnScrollListener = null;
    @Nullable
    private final OnScrollListener mOnScrollDispatcher = new OnScrollListener() {
        @Override
        public void onScroll(@NonNull final AbsListView view, final int firstVisibleItem, final int visibleItemCount, final int totalItemCount) {
            if (mScroller != null) {
                mScroller.onScroll(firstVisibleItem, visibleItemCount, totalItemCount);
            }
            if (mOnScrollListener != null) {
                mOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
            }
        }

        @Override
        public void onScrollStateChanged(@NonNull final AbsListView view, final int scrollState) {
            if (mOnScrollListener != null) {
                mOnScrollListener.onScrollStateChanged(view, scrollState);
            }
        }
    };

    {
        super.setOnScrollListener(mOnScrollDispatcher);
    }

    public FastScrollListView(@NonNull final Context context) {
        super(context);
    }

    public FastScrollListView(@NonNull final Context context, @NonNull final AttributeSet attrs) {
        super(context, attrs);
    }

    public FastScrollListView(@NonNull final Context context, @NonNull final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setOnScrollListener(final OnScrollListener listener) {
        mOnScrollListener = listener;
    }

    /**
     * Called to create and start a new FastScroller if none already exists.
     */
    private void initScroller() {
        if (mScroller != null) {
            return;
        }

        mScroller = new FastScroller(this.getContext(), this);
    }

    /**
     * Pass to scroller if defined, otherwise perform default actions.
     */
    @Override
    public boolean onInterceptTouchEvent(@NonNull final MotionEvent ev) {
        return mScroller != null && mScroller.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);

    }

    /**
     * Pass to scroller if defined, otherwise perform default actions.
     */
    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mScroller != null) {
            mScroller.onSizeChanged(w, h);
        }
    }

    /**
     * Pass to scroller if defined, otherwise perform default actions.
     */
    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent ev) {
        return mScroller != null && mScroller.onTouchEvent(ev) || super.onTouchEvent(ev);

    }

    /**
     * Send draw() to the scroller as well.
     */
    @Override
    public void draw(@NonNull final Canvas canvas) {
        super.draw(canvas);
        if (mScroller != null) {
            mScroller.draw(canvas);
        }
    }

    /**
     * Depending on 'enabled', either stop or start the scroller.
     */
    @Override
    public void setFastScrollEnabled(final boolean enabled) {
        if (!enabled) {
            if (mScroller != null) {
                mScroller.stop();
                mScroller = null;
            }
        } else {
            if (mScroller == null) {
                initScroller();
            }
        }
    }

}

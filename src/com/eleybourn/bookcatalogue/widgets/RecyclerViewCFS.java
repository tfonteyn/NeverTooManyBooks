package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;

/**
 * Replacement for {@link FastScrollListView}.
 * The overlay part could use more work, but it's functionally equal now.
 *
 * FastScroller initialisation code lifted from "recyclerview-1.1.0-alpha01-sources.jar"
 *
 * 1. xml attributes to enable the fast-scroller (and set the thumb etc)
 * 2. xml attribute to set the Overlay drawable; this does not enable it!
 *
 * 3. Call {@link #setAdapter} with a 'normal' Adapter ==> independent from 1+2 above
 * OR
 * 3. Call {@link #setAdapter}with Adapter that implements {@link SectionIndexerV2}
 * and the fast scroller will use it.
 *
 *
 */
public class RecyclerViewCFS
        extends RecyclerView {

    /** The FastScroller. */
    @Nullable
    private RecyclerCFS mRecyclerCFS;
    /** The overlay. */
    @Nullable
    private RecyclerViewCFSOverlay mOverlay;
    /** The overlay. */
    @Nullable
    private Drawable mOverlayDrawable;

    public RecyclerViewCFS(@NonNull Context context) {
        this(context, null);
    }

    public RecyclerViewCFS(@NonNull Context context,
                           @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerViewCFS(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyle) {
        super(context, attrs, defStyle);

        if (attrs != null) {
            int defStyleRes = 0;
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecyclerViewCFS,
                                                          defStyle, defStyleRes);

            boolean enableFastScroller = a
                    .getBoolean(R.styleable.RecyclerViewCFS_cfsEnabled, false);

            if (enableFastScroller) {
                StateListDrawable verticalThumbDrawable = (StateListDrawable) a
                        .getDrawable(R.styleable.RecyclerViewCFS_cfsVerticalThumbDrawable);
                Drawable verticalTrackDrawable = a
                        .getDrawable(R.styleable.RecyclerViewCFS_cfsVerticalTrackDrawable);
                StateListDrawable horizontalThumbDrawable = (StateListDrawable) a
                        .getDrawable(R.styleable.RecyclerViewCFS_cfsHorizontalThumbDrawable);
                Drawable horizontalTrackDrawable = a
                        .getDrawable(R.styleable.RecyclerViewCFS_cfsHorizontalTrackDrawable);

                mOverlayDrawable = a
                        .getDrawable(R.styleable.RecyclerViewCFS_cfsOverlayDrawable);

                if (verticalThumbDrawable == null || verticalTrackDrawable == null
                        || horizontalThumbDrawable == null || horizontalTrackDrawable == null
                        || mOverlayDrawable == null) {
                    throw new IllegalArgumentException(
                            "Trying to set fast scroller without all required drawables.");
                }

                Resources resources = getContext().getResources();
                mRecyclerCFS = new RecyclerCFS(this,
                                               verticalThumbDrawable, verticalTrackDrawable,
                                               horizontalThumbDrawable, horizontalTrackDrawable,
                                               resources.getDimensionPixelSize(
                                                       R.dimen.cfs_default_thickness),
                                               resources.getDimensionPixelSize(
                                                       R.dimen.cfs_minimum_range),
                                               resources.getDimensionPixelOffset(
                                                       R.dimen.cfs_margin));

            }
            a.recycle();
        }
    }

    @Override
    public void setAdapter(@Nullable final Adapter adapter) {
        super.setAdapter(adapter);
        if ((adapter instanceof SectionIndexerV2) && mRecyclerCFS != null) {
            //noinspection ConstantConditions
            mOverlay = new RecyclerViewCFSOverlay(this, mOverlayDrawable);
        }
    }

    @Override
    @CallSuper
    protected void onSizeChanged(final int w,
                                 final int h,
                                 final int oldw,
                                 final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mOverlay != null) {
            mOverlay.onSizeChanged(w, h);
        }
    }

    /**
     * Computes the position of the row in the adapter and set it on the overlay.
     *
     * @param offsetInPercentage the % offset in the list.
     */
    public void computeAndSetPosition(final float offsetInPercentage) {
        //noinspection ConstantConditions
        int adapterItemCount = getAdapter().getItemCount();
        // transform the % to an actual row. This is not exact...
        // but plenty good enough for our purpose here.
        int position = Math.round(offsetInPercentage * adapterItemCount);
        // make sure it's in range.
        position = MathUtils.clamp(position, 0, adapterItemCount - 1);
        //noinspection ConstantConditions
        mOverlay.setPosition(position);
    }

    /**
     * Proxy method for drawing the overlay.
     */
    public void drawIndexerOverlay(@NonNull final Canvas c,
                                   @NonNull final RecyclerView.State state) {
        if (mOverlay != null) {
            mOverlay.draw(c, state);
        }
    }
}

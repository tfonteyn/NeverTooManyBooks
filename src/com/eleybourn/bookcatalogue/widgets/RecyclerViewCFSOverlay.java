package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;

/**
 * Dimensions used for 1st and 2nd lines of text:
 * -   R.dimen.cfs_text_size_large;     fallback: 22sp
 * -   R.dimen.cfs_text_size_medium;    fallback: 18sp
 */
public class RecyclerViewCFSOverlay {

    /** Default value in SP as taken from the Android sources. */
    private static final int TEXT_SIZE_LARGE_IN_SP = 22;
    /** Default value in SP as taken from the Android sources. */
    private static final int TEXT_SIZE__MEDIUM_IN_SP = 18;

    /**
     * The overlay size is based on SIZE_MULTIPLIER * mPrimaryTextSize.
     * This allows for 2 lines of text with margins.
     */
    private static final int SIZE_MULTIPLIER = 3;

    /** The adapter. */
    @NonNull
    private final SectionIndexerV2 mSectionIndexerV2;
    /** Drawable for the overlay. */
    @NonNull
    private final Drawable mOverlayDrawable;
    /** The paint used for the text (e.g. color, size, style). */
    @NonNull
    private final TextPaint mTextPaint;
    /** Text size for 1st line. Also ued to base the overlay size on. */
    private final float mPrimaryTextSize;
    /** Text size for 2nd line. Smaller then 1st. */
    private final float mSecondaryTextSize;
    /** The current size. */
    private final int mOverlaySize;
    /** The current position for the overlay. */
    @NonNull
    private final RectF mOverlayPos = new RectF();
    /** cached local storage for the bounds. */
    private final Rect mOverlayBounds;
    /** cached local storage for the decent. */
    private final float mTextDecent;

    /** The current absolute position in the adapter backed list. */
    private int mCurrentPositionInList;
    /** The x-coordinate of the origin of the text being drawn. */
    private float mTx;
    /** The y-coordinate of the baseline of the text being drawn. */
    private float mTy;


    /**
     * Constructor.
     *
     * @param recyclerView    to use
     * @param overlayDrawable drawable for the overlay.
     */
    RecyclerViewCFSOverlay(@NonNull final RecyclerView recyclerView,
                           @NonNull final Drawable overlayDrawable) {

        Context context = recyclerView.getContext();
        //noinspection ConstantConditions
        mSectionIndexerV2 = (SectionIndexerV2) recyclerView.getAdapter();
        mOverlayDrawable = overlayDrawable;
        mOverlayBounds = mOverlayDrawable.copyBounds();

        Resources res = context.getResources();
        float size;
        try {
            size = res.getDimension(R.dimen.cfs_text_size_large);
        } catch (Resources.NotFoundException e) {
            size = TEXT_SIZE_LARGE_IN_SP * res.getDisplayMetrics().scaledDensity;
        }
        mPrimaryTextSize = size;

        try {
            size = res.getDimension(R.dimen.cfs_text_size_medium);
        } catch (Resources.NotFoundException e) {
            size = TEXT_SIZE__MEDIUM_IN_SP * res.getDisplayMetrics().scaledDensity;
        }
        mSecondaryTextSize = size;

        // Determine the overlay size
        mOverlaySize = (int) (SIZE_MULTIPLIER * mPrimaryTextSize);

        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(mPrimaryTextSize);
        mTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mTextDecent = mTextPaint.descent();

        // Send a fake onSizeChanged to initialize the overlay position
        onSizeChanged(recyclerView.getWidth(), recyclerView.getHeight());
    }

    /**
     * Ellipsize as needed.
     *
     * @return the text, ellipsized if needed, or the empty string.
     */
    @NonNull
    private String ellipsize(@Nullable final String text) {
        if (text != null && !text.isEmpty()) {
            // allow 80% of the space to be used.
            float avail = (mOverlayPos.right - mOverlayPos.left) * 0.8f;
            return TextUtils.ellipsize(text, mTextPaint, avail,
                                       TextUtils.TruncateAt.END).toString();
        }
        return "";
    }

    /**
     * Recalculate the overlay position and bounds.
     * Should be called from the {@link RecyclerViewCFS#onSizeChanged(int, int, int, int)}.
     */
    void onSizeChanged(final int w,
                       final int h) {

        // 75% of total available width
        mOverlayPos.left = w / 8f;
        mOverlayPos.right = mOverlayPos.left + w * 0.75f;
        // 10% from top
        mOverlayPos.top = h / 10f;
        mOverlayPos.bottom = mOverlayPos.top + mOverlaySize;

        mOverlayDrawable.setBounds((int) mOverlayPos.left, (int) mOverlayPos.top,
                                   (int) mOverlayPos.right, (int) mOverlayPos.bottom);

        mTx = (int) (mOverlayPos.left + mOverlayPos.right) / 2f;
        mTy = (int) (mOverlayPos.bottom + mOverlayPos.top) / 2f
                + mOverlaySize / (float) (SIZE_MULTIPLIER * 2);
    }

    /**
     * do as little as possible in this function!
     * <p>
     * ItemDecoration API: draw *over* the view.
     */
    void draw(@NonNull final Canvas c,
              @NonNull final RecyclerView.State state) {

        // the unformatted line(s) of text to display.
        String[] rawText = mSectionIndexerV2.getSectionTextForPosition(mCurrentPositionInList);
        if (rawText != null && rawText.length != 0) {
            // ellipsize and "un-null" as needed.
            String[] formattedText = new String[rawText.length];
            for (int i = 0; i < rawText.length; i++) {
                formattedText[i] = ellipsize(rawText[i]);
            }

            // Draw the overlay itself.
            if (formattedText.length > 1 && !formattedText[1].isEmpty()) {
                // Expand the box to fit the 2nd line.
                Rect tmpBounds = mOverlayDrawable.getBounds();
                tmpBounds.set(tmpBounds.left, tmpBounds.top, tmpBounds.right,
                              tmpBounds.bottom + tmpBounds.height() / 2);
                mOverlayDrawable.setBounds(tmpBounds);
                mOverlayDrawable.draw(c);
                // restore bounds
                mOverlayDrawable.setBounds(mOverlayBounds);

            } else {
                mOverlayDrawable.draw(c);
            }

            // Draw the 1st line with base of text at - descent : so it is vertically centred
            c.drawText(formattedText[0], mTx, mTy - mTextDecent, mTextPaint);

            if (formattedText.length > 1 && !formattedText[1].isEmpty()) {
                // Draw the 2nd line smaller than 1st.
                mTextPaint.setTextSize(mSecondaryTextSize);
                // but use the original text size for the y coordinate!
                c.drawText(formattedText[1], mTx, mTy + mPrimaryTextSize, mTextPaint);
                // restore text size
                mTextPaint.setTextSize(mPrimaryTextSize);
            }
        }
    }

    /**
     * do as little as possible in this function!
     */
    void setPosition(final int currentPositionInList) {
        mCurrentPositionInList = currentPositionInList;
    }
}

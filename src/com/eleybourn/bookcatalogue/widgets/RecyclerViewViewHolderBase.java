package com.eleybourn.bookcatalogue.widgets;

import android.graphics.Color;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.widgets.ddsupport.ItemTouchHelperViewHolder;

/**
 * Holder pattern for each row in a RecyclerView.
 * <p>
 * Extends the original with support for:
 * <ul>
 * <li>typed encapsulated item</li>
 * <li>a 'delete' button</li>
 * <li>a 'checkable' button</li>
 * <li>{@link ItemTouchHelperViewHolder}</li>
 * </ul>
 * Uses pre-defined ID's:
 * <ul>
 * <li>R.id.TLV_ROW_DETAILS</li>
 * <li>R.id.TLV_ROW_DELETE</li>
 * <li>R.id.TLV_ROW_CHECKABLE</li>
 * <li>R.id.TLV_ROW_GRABBER</li>
 * </ul>
 */
public class RecyclerViewViewHolderBase
        extends RecyclerView.ViewHolder
        implements ItemTouchHelperViewHolder {

    /** optional row checkable button. */
    @Nullable
    public final CompoundButton mCheckableButton;
    /** The details part of the row (or the row itself). */
    @NonNull
    public final View rowDetailsView;
    /** optional drag handle button for drag/drop support. */
    @Nullable
    final ImageView mDragHandleView;
    /** optional row delete button. */
    @Nullable
    final View mDeleteButton;

    protected RecyclerViewViewHolderBase(@NonNull final View itemView) {
        super(itemView);

        // Don't enable the whole row, so buttons keep working
        View rd = itemView.findViewById(R.id.ROW_DETAILS);
        // but if we did not define a details row subview, use the row itself anyhow.
        rowDetailsView = rd != null ? rd : itemView;
        rowDetailsView.setFocusable(false);

        // optional
        mDeleteButton = itemView.findViewById(R.id.ROW_DELETE_BTN);
        mCheckableButton = itemView.findViewById(R.id.ROW_CHECKABLE_BTN);
        mDragHandleView = itemView.findViewById(R.id.ROW_GRABBER_ICON);
    }

    @Override
    public void onItemSelected() {
        //ENHANCE: style this.
        itemView.setBackgroundColor(Color.LTGRAY);
    }

    @Override
    public void onItemClear() {
        //ENHANCE: maybe style this.
        itemView.setBackgroundColor(Color.TRANSPARENT);
    }
}

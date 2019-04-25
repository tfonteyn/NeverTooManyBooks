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
 * <ul>Extends the original with support for:
 * <li>typed encapsulated item</li>
 * <li>a 'delete' button</li>
 * <li>a 'checkable' button</li>
 * <li>{@link ItemTouchHelperViewHolder}</li>
 * </ul>
 * <ul>Uses pre-defined id's:
 * <li>R.id.TLV_ROW_DETAILS</li>
 * <li>R.id.TLV_ROW_DELETE</li>
 * <li>R.id.TLV_ROW_CHECKABLE</li>
 * <li>R.id.TLV_ROW_GRABBER</li>
 * </ul>
 *
 * @param <T> type of the encapsulated item for the row.
 */
public class RecyclerViewViewHolderBase<T>
        extends RecyclerView.ViewHolder
        implements ItemTouchHelperViewHolder {

    /** optional row checkable button. */
    @Nullable
    public final CompoundButton mCheckableButton;
    /** optional drag handle button for drag/drop support. */
    @Nullable
    final ImageView mDragHandleView;
    /** The details part of the row (or the row itself). */
    @NonNull
    protected final View rowDetailsView;
    /** optional row delete button. */
    @Nullable
    protected final View mDeleteButton;
    /** The item that is represented by this row. */
    protected T item;

    public RecyclerViewViewHolderBase(@NonNull final View itemView) {
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

    public T getItem() {
        return item;
    }

    public void setItem(final T item) {
        this.item = item;
    }

    @Override
    public void onItemSelected() {
        itemView.setBackgroundColor(Color.LTGRAY);
    }

    @Override
    public void onItemClear() {
        itemView.setBackgroundColor(Color.TRANSPARENT);
    }
}

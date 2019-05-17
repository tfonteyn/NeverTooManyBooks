package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.widgets.SectionIndexerV2;

/**
 * Makes the {@link MultiTypeListCursorAdapter} suitable for use with
 * a {@link androidx.recyclerview.widget.RecyclerView}.
 * <p>
 * TODO: merge with {@link MultiTypeListCursorAdapter}
 */
public class MultiTypeListCursorAdapterWrapper
        extends CursorAdapterWrapper
        implements SectionIndexerV2 {

    private View.OnClickListener mOnItemClick;
    private View.OnLongClickListener mOnItemLongClick;

    public MultiTypeListCursorAdapterWrapper(@NonNull final Context context,
                                             final MultiTypeListCursorAdapter multiTypeListCursorAdapter) {
        super(context, multiTypeListCursorAdapter);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        super.onBindViewHolder(holder, position);

        // temp tag for the position, so the click-listeners get get it.
        holder.itemView.setTag(R.id.TAG_POSITION, position);
        holder.itemView.setOnClickListener(mOnItemClick);
        holder.itemView.setOnLongClickListener(mOnItemLongClick);
    }

    public int getAbsolutePosition(@NonNull final View v) {
        return ((MultiTypeListCursorAdapter) mCursorAdapter).getAbsolutePosition(v);
    }

    @Nullable
    @Override
    public String[] getSectionTextForPosition(final int position) {
        return ((SectionIndexerV2) mCursorAdapter).getSectionTextForPosition(position);
    }

    public void setOnItemClickListener(@NonNull final View.OnClickListener onItemClick) {
        mOnItemClick = onItemClick;
    }

    public void setOnItemLongClickListener(@NonNull final View.OnLongClickListener onItemLongClick) {
        mOnItemLongClick = onItemLongClick;
    }
}

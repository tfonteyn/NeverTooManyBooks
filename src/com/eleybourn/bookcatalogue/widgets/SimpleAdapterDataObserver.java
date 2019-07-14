package com.eleybourn.bookcatalogue.widgets;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Really dumb implementation that simple delegates everything to {@link #onChanged()}.
 */
public abstract class SimpleAdapterDataObserver
        extends RecyclerView.AdapterDataObserver {

    @Override
    public void onItemRangeChanged(final int positionStart,
                                   final int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeInserted(final int positionStart,
                                    final int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeRemoved(final int positionStart,
                                   final int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeMoved(final int fromPosition,
                                 final int toPosition,
                                 final int itemCount) {
        onChanged();
    }
}

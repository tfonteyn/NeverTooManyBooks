package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.widgets.SectionIndexerV2;

/**
 * Cursor adapter for flattened multi-typed ListViews.
 * Simplifies the implementation of such lists.
 * <p>
 * Users of this class need to implement MultiTypeListHandler to manage the creation
 * and display of each view.
 * The {@link RecyclerView.ViewHolder} as used here is oblivious about the real view members.
 * <p>
 * Original author Philip Warner; now rewritten to use RecyclerView
 */
public class MultiTypeListCursorAdapter
        extends RecyclerView.Adapter<MultiTypeListCursorAdapter.Holder>
        implements SectionIndexerV2 {

    @NonNull
    private final Context mContext;
    @NonNull
    private final Cursor mCursor;
    @NonNull
    private final MultiTypeListHandler mListHandler;
    @Nullable
    private View.OnClickListener mOnItemClick;
    @Nullable
    private View.OnLongClickListener mOnItemLongClick;

    /**
     * Constructor.
     */
    public MultiTypeListCursorAdapter(@NonNull final Context context,
                                      @NonNull final Cursor cursor,
                                      @NonNull final MultiTypeListHandler listHandler) {
        mContext = context;
        mCursor = cursor;
        mListHandler = listHandler;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        View itemView = mListHandler.newView(mContext, mCursor, parent);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        mCursor.moveToPosition(position);
        mListHandler.bindView(holder.itemView, mContext, mCursor);

        // temp tag for the position, so the click-listeners get get it.
        holder.itemView.setTag(R.id.TAG_POSITION, position);
        holder.itemView.setOnClickListener(mOnItemClick);
        holder.itemView.setOnLongClickListener(mOnItemLongClick);
    }

    @Override
    public int getItemCount() {
        return mCursor.getCount();
    }

    @Override
    public int getItemViewType(final int position) {
        mCursor.moveToPosition(position);
        return mListHandler.getItemViewType(mCursor);
    }

    /**
     * Return the absolute position in the list for the passed View.
     *
     * @param view to find
     *
     * @return position
     */
    public int getAbsolutePosition(@NonNull final View view) {
        return mListHandler.getAbsolutePosition(view);
    }

    /**
     * Provide the text for the fast-scroller overlay of ListView
     * and {@link com.eleybourn.bookcatalogue.widgets.RecyclerViewCFS}
     * <p>
     * The actual text comes from {@link MultiTypeListHandler#getSectionText}}.
     */
    @Override
    @Nullable
    public String[] getSectionTextForPosition(final int position) {
        // sanity check.
        if (position < 0 || position >= mCursor.getCount()) {
            return null;
        }

        // temporary move the cursor to the requested position, restore after we got the text.
        final int savedPos = mCursor.getPosition();
        mCursor.moveToPosition(position);
        final String[] section = mListHandler.getSectionText(mContext.getResources(), mCursor);
        mCursor.moveToPosition(savedPos);

        return section;
    }

    public void setOnItemClickListener(@NonNull final View.OnClickListener onItemClick) {
        mOnItemClick = onItemClick;
    }

    public void setOnItemLongClickListener(@NonNull final View.OnLongClickListener onItemLongClick) {
        mOnItemLongClick = onItemLongClick;
    }


    /**
     * Interface for handling the View-related tasks in a multi-type ListView.
     *
     * @author Philip Warner
     */
    public interface MultiTypeListHandler {

        /**
         * Return the view type that will be used for any row of the type represented by
         * the current cursor position.
         *
         * @param cursor Cursor positioned at representative row.
         *
         * @return view type
         */
        int getItemViewType(@NonNull Cursor cursor);

        /**
         * @return the total number of view types that can be returned.
         */
        int getViewTypeCount();

        /**
         * Return the absolute position in the list for the passed View.
         *
         * @param view to find
         *
         * @return position
         */
        int getAbsolutePosition(@NonNull final View view);

        /**
         * Get the text to display in ListView for row at current cursor position.
         *
         * @param resources caller context
         * @param cursor    Cursor, correctly positioned.
         *
         * @return the section text as an array.
         */
        String[] getSectionText(@NonNull Resources resources,
                                @NonNull Cursor cursor);

        /**
         * Create a new view based on the current row of the cursor.
         *
         * @param cursor Cursor, positioned at current row
         * @param parent Parent view group
         *
         * @return new view.
         */
        @NonNull
        View newView(@NonNull Context context,
                     @NonNull Cursor cursor,
                     @NonNull ViewGroup parent);


        /**
         * Fill the view in with details pointed to by the current cursor.
         *
         * @param view   Pointer to reusable view of correct type.
         * @param cursor Cursor, positioned at current row
         */
        void bindView(@NonNull View view,
                      @NonNull Context context,
                      @NonNull Cursor cursor);
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        Holder(@NonNull final View itemView) {
            super(itemView);
        }
    }
}

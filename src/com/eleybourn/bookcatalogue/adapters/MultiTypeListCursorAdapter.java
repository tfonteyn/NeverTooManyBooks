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

package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.widgets.SectionIndexerV2;

/**
 * Cursor adapter for flattened multi-typed ListViews.
 * Simplifies the implementation of such lists.
 * <p>
 * Users of this class need to implement MultiTypeListHandler to manage the creation
 * and display of each view.
 *
 * @author Philip Warner
 */
public class MultiTypeListCursorAdapter
        extends CursorAdapter
        implements SectionIndexerV2 {

    @NonNull
    private final Context mContext;
    @NonNull
    private final MultiTypeListHandler mListHandler;

    public MultiTypeListCursorAdapter(@NonNull final Context context,
                                      @NonNull final Cursor cursor,
                                      @NonNull final MultiTypeListHandler handler) {
        super(context, cursor);
        mContext = context;
        mListHandler = handler;
    }

    @Override
    public int getItemViewType(final int position) {
        final Cursor listCursor = getCursor();
        listCursor.moveToPosition(position);
        return mListHandler.getItemViewType(listCursor);
    }

    @Override
    public int getViewTypeCount() {
        return mListHandler.getViewTypeCount();
    }

    public int getAbsolutePosition(@NonNull final View v) {
        return mListHandler.getAbsolutePosition(v);
    }

    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        return mListHandler.newView(context, cursor, parent);
    }

    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final Cursor cursor) {
        mListHandler.bindView(view, context, cursor);
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
        final Cursor listCursor = getCursor();
        // sanity check.
        if (position < 0 || position >= listCursor.getCount()) {
            return null;
        }

        // temporary move the cursor to the requested position, restore after we got the text.
        final int savedPos = listCursor.getPosition();
        listCursor.moveToPosition(position);
        final String[] section = mListHandler.getSectionText(mContext, listCursor);
        listCursor.moveToPosition(savedPos);

        return section;
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
         * @param context caller context
         * @param cursor  Cursor, correctly positioned.
         *
         * @return the section text as an array.
         */
        String[] getSectionText(@NonNull Context context,
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
}

/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

public class BooklistAdapter
        extends RecyclerView.Adapter<RowViewHolder>
        implements FastScroller.PopupTextProvider {

    /** Log tag. */
    private static final String TAG = "BooklistAdapter";

    @NonNull
    private final LayoutInflater inflater;
    /** Top margin to use for Level 1. */
    @Dimension
    private final int level1topMargin;
    /** The padding indent (in pixels) added for each level: padding = (level-1) * levelIndent. */
    @Dimension
    private final int levelIndent;
    @NonNull
    private final Style style;
    @Dimension
    private final int groupRowHeight;
    /** Longest side for a cover in pixels. */
    @Dimension
    private final int coverLongestSide;
    @NonNull
    private final Formatter formatter;
    private final Languages languages;


    /** The cursor is the equivalent of the 'list of items'. */
    @Nullable
    private Cursor cursor;
    @Nullable
    private Booklist booklist;
    /** provides read only access to the row data. */
    @Nullable
    private DataHolder rowData;
    @Nullable
    private OnRowClickListener rowClickListener;
    @Nullable
    private OnRowClickListener rowShowMenuListener;
    @Nullable
    private ShowContextMenu contextMenuMode;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   to use
     */
    public BooklistAdapter(@NonNull final Context context,
                           @NonNull final Style style) {
        this.inflater = LayoutInflater.from(context);
        this.style = style;

        languages = ServiceLocator.getInstance().getLanguages();

        final Resources res = context.getResources();
        levelIndent = res.getDimensionPixelSize(R.dimen.bob_group_level_padding_start);
        level1topMargin = res.getDimensionPixelSize(R.dimen.bob_group_level_1_margin_top);

        formatter = new Formatter(context, style);

        groupRowHeight = this.style.getGroupRowHeight(context);

        if (this.style.isShowField(Style.Screen.List, FieldVisibility.COVER[0])) {
            @Style.CoverScale
            final int frontCoverScale = this.style.getCoverScale();

            // The thumbnail scale is used to retrieve the cover dimensions
            // We use a square space for the image so both portrait/landscape images work out.
            final TypedArray coverSizes = context
                    .getResources().obtainTypedArray(R.array.cover_book_list_longest_side);
            try {
                coverLongestSide = coverSizes.getDimensionPixelSize(frontCoverScale, 0);
            } finally {
                coverSizes.recycle();
            }
        } else {
            coverLongestSide = 0;
        }

        // getItemId returns the rowId
        setHasStableIds(true);
    }

    /**
     * Set the Booklist.
     * <p>
     * This will trigger a {@link #notifyDataSetChanged()}.
     *
     * @param booklist the 'list of items' or {@code null} to clear
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setBooklist(@Nullable final Booklist booklist) {
        if (booklist == null) {
            this.booklist = null;
            cursor = null;
            rowData = null;
        } else {
            this.booklist = booklist;
            cursor = booklist.getNewListCursor();
            rowData = new CursorRow(cursor);
        }
        notifyDataSetChanged();
    }

    /**
     * Set the {@link OnRowClickListener} for a click on a row.
     *
     * @param listener to set
     */
    public void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
        this.rowClickListener = listener;
    }

    /**
     * Set the {@link OnRowClickListener} for showing the context menu on a row.
     *
     * @param listener to receive clicks
     */
    public void setOnRowShowMenuListener(@NonNull final ShowContextMenu contextMenuMode,
                                         @Nullable final OnRowClickListener listener) {
        this.rowShowMenuListener = listener;
        this.contextMenuMode = contextMenuMode;
    }

    /**
     * Read the data row on the given position.
     *
     * @param position The position of the item within the adapter's data set.
     *
     * @return the active DataHolder
     */
    @Nullable
    public DataHolder readDataAt(final int position) {
        //noinspection ConstantConditions
        if (!cursor.moveToPosition(position)) {
            // We should never get here... flw
            return null;
        }
        return rowData;
    }

    /**
     * Refresh the list data for the given positions.
     */
    public void requery(final int[] positions) {
        // Yes, requery() is deprecated but see BooklistCursor were we do the right thing.
        //noinspection deprecation,ConstantConditions
        cursor.requery();

        for (final int pos : positions) {
            notifyItemChanged(pos);
        }
    }

    @Override
    public long getItemId(final int position) {
        if (cursor != null && cursor.moveToPosition(position)) {
            // return the rowId of the list-table
            //noinspection ConstantConditions
            return rowData.getLong(DBKey.PK_ID);
        } else {
            return RecyclerView.NO_ID;
        }
    }

    @Override
    public int getItemCount() {
        return cursor != null ? cursor.getCount() : 0;
    }

    /**
     * Returns a {@link BooklistGroup.Id} as the view type.
     *
     * @param position position to query
     *
     * @return integer value identifying the type of the view
     */
    @Override
    @BooklistGroup.Id
    public int getItemViewType(final int position) {
        if (cursor != null && cursor.moveToPosition(position)) {
            //noinspection ConstantConditions
            return rowData.getInt(DBKey.BL_NODE_GROUP);
        } else {
            // bogus, should not happen
            return BooklistGroup.BOOK;
        }
    }

    @SuppressLint("SwitchIntDef")
    @Override
    @NonNull
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            @BooklistGroup.Id final int groupId) {
        @LayoutRes
        final int layoutId;
        switch (groupId) {
            case BooklistGroup.BOOK:
                layoutId = R.layout.booksonbookshelf_row_book;
                break;

            case BooklistGroup.RATING:
                layoutId = R.layout.booksonbookshelf_group_rating;
                break;

            default:
                layoutId = R.layout.booksonbookshelf_group_generic;
                break;
        }

        final View itemView = inflater.inflate(layoutId, parent, false);

        //noinspection ConstantConditions
        final int level = rowData.getInt(DBKey.BL_NODE_LEVEL);

        if (groupId != BooklistGroup.BOOK) {
            // set an indentation depending on level (2..)
            if (level > 1) {
                itemView.setPaddingRelative((level - 1) * levelIndent, 0, 0, 0);
            }
            // adjust row height and margins depending on level (1..)
            if (level > 0) {
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        itemView.getLayoutParams();

                // Adjust the line spacing as required
                lp.height = groupRowHeight;
                // Adjust the level 1 top margin if allowed
                if (level == 1 && groupRowHeight != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    lp.setMargins(0, level1topMargin, 0, 0);
                }
            }
        }

        // Scale text/padding (recursively) if required
        final int textScale = style.getTextScale();
        if (textScale != Style.DEFAULT_TEXT_SCALE) {
            scaleTextViews(itemView, textScale);
        }


        final RowViewHolder holder;

        // NEWTHINGS: BooklistGroup - add a new holder type if needed
        switch (groupId) {
            case BooklistGroup.BOOK:
                holder = new BookHolder(itemView, style, languages, coverLongestSide);
                break;

            case BooklistGroup.AUTHOR:
                holder = new AuthorHolder(itemView, style, level, formatter);
                break;

            case BooklistGroup.SERIES:
                holder = new SeriesHolder(itemView, style, level, formatter);
                break;

            case BooklistGroup.RATING:
                holder = new RatingHolder(itemView, style);
                break;

            default:
                holder = new GenericStringHolder(itemView, style, groupId, level, formatter);
                break;
        }

        if (BuildConfig.DEBUG /* always */) {
            if (holder instanceof GenericStringHolder) {
                ((GenericStringHolder) holder).setDebugPosition(this::getDbgRowColor);
            }
        }

        holder.setOnRowClickListener(rowClickListener);
        holder.setOnRowShowContextMenuListener(contextMenuMode, rowShowMenuListener);

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {

        //noinspection ConstantConditions
        cursor.moveToPosition(position);

        //noinspection unchecked,ConstantConditions
        ((BindableViewHolder<DataHolder>) holder).onBind(rowData);
    }

    private void scaleTextViews(@NonNull final View view,
                                @Style.TextScale final int textScale) {
        final Resources res = view.getContext().getResources();
        TypedArray ta;
        final float fontSizeInSpUnits;
        ta = res.obtainTypedArray(R.array.bob_text_size_in_sp);
        try {
            fontSizeInSpUnits = ta.getFloat(textScale, 0);
        } finally {
            ta.recycle();
        }

        final float paddingFactor;
        ta = res.obtainTypedArray(R.array.bob_text_padding_in_percent);
        try {
            paddingFactor = ta.getFloat(textScale, 0);
        } finally {
            ta.recycle();
        }

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(fontSizeInSpUnits, "fontSizeInSpUnits");
            SanityCheck.requirePositiveValue(paddingFactor, "paddingFactor");
        }

        scaleTextViews(view, fontSizeInSpUnits, paddingFactor);
    }

    /**
     * Scale text in a View (and recursively its children).
     *
     * @param root              the view (and its children) we'll scale
     * @param textSizeInSpUnits the text size in SP units (e.g. 14,18,32)
     * @param scaleFactor       to apply to the element padding
     */
    private void scaleTextViews(@NonNull final View root,
                                @Dimension(unit = Dimension.SP) final float textSizeInSpUnits,
                                final float scaleFactor) {
        if (root instanceof TextView) {
            ((TextView) root).setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeInSpUnits);
        }

        // all Views get scaled padding; using the absolute padding values.
        root.setPadding((int) (scaleFactor * root.getPaddingLeft()),
                        (int) (scaleFactor * root.getPaddingTop()),
                        (int) (scaleFactor * root.getPaddingRight()),
                        (int) (scaleFactor * root.getPaddingBottom()));

        // go recursive if needed
        if (root instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                scaleTextViews(viewGroup.getChildAt(i), textSizeInSpUnits, scaleFactor);
            }
        }
    }

    /**
     * Get the full set of 'level' texts for the given position.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @NonNull
    public CharSequence[] getPopupText(final int position) {
        return new CharSequence[]{
                getLevelText(1, position),
                getLevelText(2, position)};
    }

    /**
     * Get the text associated with the matching level group for the given position.
     *
     * @param level    the level in the Booklist tree
     * @param position to get the text for
     *
     * @return the text for that level, or {@code null} if none present.
     */
    @Nullable
    public CharSequence getLevelText(@IntRange(from = 1) final int level,
                                     final int position) {

        // sanity check.
        if (BuildConfig.DEBUG /* always */) {
            final int groupCount = style.getGroupCount() + 1;
            if (level > groupCount) {
                throw new IllegalArgumentException(
                        "level=" + level + "> (getGroupCount+1)=" + groupCount);
            }
        }

        // make sure it's still in range.
        final int clampedPosition = MathUtils.clamp(position, 0, getItemCount() - 1);
        if (cursor == null || !cursor.moveToPosition(clampedPosition)) {
            return null;
        }

        try {
            if (level > (style.getGroupCount())) {
                // it's a book; use the title (no need to take the group.format round-trip).
                //noinspection ConstantConditions
                return rowData.getString(DBKey.TITLE);

            } else {
                // it's a group; use the display domain as the text
                final BooklistGroup group = style.getGroupByLevel(level);
                final String key = group.getDisplayDomainExpression().getDomain().getName();
                //noinspection ConstantConditions
                return formatter.format(group.getId(), rowData, key);
            }
        } catch (@NonNull final CursorIndexOutOfBoundsException e) {
            // Seen a number of times. No longer reproducible, but paranoia...
            LoggerFactory.getLogger().e(TAG, e, "|level=" + level);
        }
        return null;
    }

    /**
     * DEBUG.
     * <p>
     * Get a ColorInt for the given row.
     * Green: expanded
     * Transparent: collapsed.
     *
     * @param rowId to check
     *
     * @return color
     */
    @ColorInt
    private int getDbgRowColor(final int rowId) {
        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(booklist);
            if (booklist.isNodeExpanded(rowId)) {
                return Color.GREEN;
            } else {
                return Color.TRANSPARENT;
            }
        }
        throw new IllegalStateException("Not in debug");
    }
}

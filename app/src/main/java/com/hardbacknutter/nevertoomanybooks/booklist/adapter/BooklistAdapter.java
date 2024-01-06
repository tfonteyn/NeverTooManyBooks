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
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Dimension;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
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

    /** Shared across all {@link BookHolder}s. */
    private final RealNumberParser realNumberParser;
    @NonNull
    private final Style.Layout layout;
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
     * @param context          Current context
     * @param style            to use
     * @param layout           to use
     * @param coverLongestSide Longest side for a cover in pixels
     */
    public BooklistAdapter(@NonNull final Context context,
                           @NonNull final Style style,
                           @NonNull final Style.Layout layout,
                           final int coverLongestSide) {
        this.inflater = LayoutInflater.from(context);
        this.style = style;
        this.layout = layout;
        this.coverLongestSide = coverLongestSide;

        final List<Locale> locales = LocaleListUtils.asList(context);
        realNumberParser = new RealNumberParser(locales);
        formatter = new Formatter(context, style, locales);

        final Resources res = context.getResources();
        levelIndent = res.getDimensionPixelSize(R.dimen.bob_group_level_padding_start);
        level1topMargin = res.getDimensionPixelSize(R.dimen.bob_group_level_1_margin_top);

        if (style.isGroupRowUsesPreferredHeight()) {
            groupRowHeight = AttrUtils.getDimensionPixelSize(
                    context, com.google.android.material.R.attr.listPreferredItemHeightSmall);
        } else {
            groupRowHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
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
     * @param contextMenuMode how to show context menus
     * @param listener        to receive clicks
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
        //noinspection DataFlowIssue
        if (!cursor.moveToPosition(position)) {
            // We should never get here... flw
            return null;
        }
        return rowData;
    }

    /**
     * Refresh the list data for the given positions.
     *
     * @param positions to refresh
     */
    public void requery(@NonNull final int[] positions) {
        // Yes, requery() is deprecated but see BooklistCursor were we do the right thing.
        //noinspection deprecation,DataFlowIssue
        cursor.requery();

        for (final int pos : positions) {
            notifyItemChanged(pos);
        }
    }

    @Override
    public long getItemId(final int position) {
        if (cursor != null && cursor.moveToPosition(position)) {
            // return the rowId of the list-table
            //noinspection DataFlowIssue
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
            //noinspection DataFlowIssue
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
                if (layout == Style.Layout.List) {
                    layoutId = R.layout.booksonbookshelf_row_book;
                } else {
                    layoutId = R.layout.booksonbookshelf_grid_book;
                }
                break;

            case BooklistGroup.RATING:
                layoutId = R.layout.booksonbookshelf_group_rating;
                break;

            default:
                layoutId = R.layout.booksonbookshelf_group_generic;
                break;
        }

        final View itemView = inflater.inflate(layoutId, parent, false);

        //noinspection DataFlowIssue
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
        final TextScale textScale = style.getTextScale();
        if (textScale != TextScale.DEFAULT) {
            scaleTextViews(itemView, textScale);
        }


        final RowViewHolder holder;

        // NEWTHINGS: BooklistGroup - add a new holder type if needed
        switch (groupId) {
            case BooklistGroup.BOOK:
                switch (layout) {
                    case List:
                        holder = new BookHolder(itemView, style, coverLongestSide,
                                                realNumberParser);
                        break;
                    case Grid:
                        holder = new BookGridHolder(itemView, style, coverLongestSide);
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
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
                Objects.requireNonNull(booklist);
                ((GenericStringHolder) holder).setDebugPosition(
                        rowId -> booklist.getDbgRowColor(rowId));
            }
        }

        holder.setOnRowClickListener(rowClickListener);
        holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {

        //noinspection DataFlowIssue
        cursor.moveToPosition(position);

        //noinspection unchecked,DataFlowIssue
        ((BindableViewHolder<DataHolder>) holder).onBind(rowData);
    }

    private void scaleTextViews(@NonNull final View view,
                                @NonNull final TextScale textScale) {

        final Context context = view.getContext();
        final float fontSizeInSpUnits = textScale.getFontSizeInSp(context);
        final float paddingFactor = textScale.getPaddingFactor(context);

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
     *
     * @throws IllegalArgumentException if the level is higher than the group count
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
                //noinspection DataFlowIssue
                return rowData.getString(DBKey.TITLE);

            } else {
                // it's a group; use the display domain as the text
                final BooklistGroup group = style.getGroupByLevel(level);
                final String key = group.getDisplayDomainExpression().getDomain().getName();
                //noinspection DataFlowIssue
                return formatter.format(group.getId(), rowData, key);
            }
        } catch (@NonNull final CursorIndexOutOfBoundsException e) {
            // Seen a number of times. No longer reproducible, but paranoia...
            LoggerFactory.getLogger().e(TAG, e, "|level=" + level);
        }
        return null;
    }

}

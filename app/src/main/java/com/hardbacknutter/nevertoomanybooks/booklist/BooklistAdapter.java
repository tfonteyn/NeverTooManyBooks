/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.math.MathUtils;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfRowBookBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * Handles all views in a multi-type list showing Book, Author, Series etc.
 * <p>
 * Each row(level) needs to have a layout like:
 * <pre>
 *     {@code
 *          <layout id="@id/ROW_INFO">
 *          <TextView id="@id/name" />
 *          ...
 *      }
 * </pre>
 * <p>
 * ROW_INFO is important, as it's that one that gets shown/hidden when needed.
 */
public class BooklistAdapter
        extends RecyclerView.Adapter<BooklistAdapter.RowViewHolder>
        implements FastScroller.PopupTextProvider {

    /** Log tag. */
    private static final String TAG = "BooklistAdapter";

    /**
     * 0.6 is based on a standard paperback 17.5cm x 10.6cm
     * -> width = 0.6 * maxHeight.
     * See {@link #coverLongestSide}.
     */
    private static final float HW_RATIO = 0.6f;

    /** Cached locale. */
    @NonNull
    private final Locale userLocale;

    /** The padding indent (in pixels) added for each level: padding = (level-1) * levelIndent. */
    private final int levelIndent;

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;

    /** caching the book condition strings. */
    @NonNull
    private final String[] conditionDescriptions;

    /** Whether to use the covers DAO caching. */
    private boolean imageCachingEnabled;

    private boolean reorderTitleForDisplaying;

    /** List style to apply. */
    private Style style;

    private int groupRowHeight;

    /** Top margin to use for Level 1 <strong>if</strong> the {@link #groupRowHeight} is wrap. */
    private int groupLevel1topMargin;

    /** Longest side for a cover in pixels. */
    private int coverLongestSide;

    /** The cursor is the equivalent of the 'list of items'. */
    @Nullable
    private Cursor cursor;
    @Nullable
    private Booklist booklist;


    /** provides read only access to the row data. */
    @Nullable
    private DataHolder nodeData;

    @Nullable
    private OnRowClickListener rowClickListener;
    @Nullable
    private OnRowLongClickListener rowLongClickListener;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public BooklistAdapter(@NonNull final Context context) {
        inflater = LayoutInflater.from(context);

        final Resources resources = context.getResources();
        userLocale = resources.getConfiguration().getLocales().get(0);
        levelIndent = resources.getDimensionPixelSize(R.dimen.bob_group_level_padding_start);
        conditionDescriptions = resources.getStringArray(R.array.conditions_book);

        // getItemId returns the rowId
        setHasStableIds(true);
    }

    /**
     * Set the style and prepare the related data.
     *
     * @param context Current context
     * @param style   Style reference.
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final Style style) {

        imageCachingEnabled = ImageUtils.isImageCachingEnabled();
        reorderTitleForDisplaying = ReorderHelper.forDisplay(context);

        this.style = style;
        groupRowHeight = this.style.getGroupRowHeight(context);

        final Resources resources = context.getResources();

        if (groupRowHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            groupLevel1topMargin = resources
                    .getDimensionPixelSize(R.dimen.bob_group_level_1_margin_top);
        }

        if (this.style.isShowField(Style.Screen.List, FieldVisibility.COVER[0])) {
            @Style.CoverScale
            final int frontCoverScale = this.style.getCoverScale();

            // The thumbnail scale is used to retrieve the cover dimensions
            // We use a square space for the image so both portrait/landscape images work out.
            final TypedArray coverSizes = resources
                    .obtainTypedArray(R.array.cover_book_list_longest_side);
            try {
                coverLongestSide = coverSizes.getDimensionPixelSize(frontCoverScale, 0);
            } finally {
                coverSizes.recycle();
            }
        } else {
            coverLongestSide = 0;
        }
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
        return nodeData;
    }

    /**
     * Refresh the list data.
     *
     * @throws NullPointerException if the cursor is not initialised - which would be a bug.
     */
    public void requery() {
        // Yes, requery() is deprecated but see BooklistCursor were we do the right thing.
        //noinspection deprecation,ConstantConditions
        cursor.requery();
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
            nodeData = null;
        } else {
            this.booklist = booklist;
            cursor = booklist.getNewListCursor();
            nodeData = new CursorRow(cursor);
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(final int position) {
        if (cursor != null && cursor.moveToPosition(position)) {
            // return the rowId of the list-table
            //noinspection ConstantConditions
            return nodeData.getLong(DBKey.PK_ID);
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
            return nodeData.getInt(DBKey.BL_NODE_GROUP);
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

        final View itemView = createView(parent, groupId);
        final RowViewHolder holder;

        // NEWTHINGS: BooklistGroup.KEY add a new holder type if needed
        switch (groupId) {
            case BooklistGroup.BOOK:
                holder = new BookHolder(this, itemView);
                break;

            case BooklistGroup.AUTHOR:
                holder = new AuthorHolder(this, itemView, style.requireGroupById(groupId));
                break;

            case BooklistGroup.SERIES:
                holder = new SeriesHolder(this, itemView, style.requireGroupById(groupId));
                break;

            case BooklistGroup.RATING:
                holder = new RatingHolder(itemView, style.requireGroupById(groupId));
                break;

            default:
                holder = new GenericStringHolder(this, itemView,
                                                 style.requireGroupById(groupId));
                break;
        }

        // test for the listener inside the lambda, this allows changing it if needed
        holder.onClickTargetView.setOnClickListener(v -> {
            if (rowClickListener != null) {
                rowClickListener.onClick(holder.getBindingAdapterPosition());
            }
        });

        holder.onClickTargetView.setOnLongClickListener(v -> {
            if (rowLongClickListener != null) {
                return rowLongClickListener.onLongClick(v, holder.getBindingAdapterPosition());
            }
            return false;
        });

        return holder;
    }

    /**
     * Create the View for the specified group.
     *
     * @param parent  The ViewGroup into which the new View will be added after it is bound to
     *                an adapter position.
     * @param groupId The view type of the new View == the group id
     *
     * @return the view
     */
    @NonNull
    private View createView(@NonNull final ViewGroup parent,
                            @BooklistGroup.Id final int groupId) {
        //noinspection ConstantConditions
        final int level = nodeData.getInt(DBKey.BL_NODE_LEVEL);

        @LayoutRes
        final int layoutId;
        if (groupId == BooklistGroup.BOOK) {
            layoutId = R.layout.booksonbookshelf_row_book;

        } else if (groupId == BooklistGroup.RATING) {
            layoutId = R.layout.booksonbookshelf_group_rating;

        } else {
            // for all other types, the level determines the view
            switch (level) {
                case 1:
                    layoutId = R.layout.booksonbookshelf_group_level_1;
                    break;
                case 2:
                    layoutId = R.layout.booksonbookshelf_group_level_2;
                    break;
                default:
                    // level 0 is a book, see above
                    // level 3 and higher all use the same layout.
                    layoutId = R.layout.booksonbookshelf_group_level_3;
                    break;
            }
        }

        final View view = inflater.inflate(layoutId, parent, false);

        if (groupId == BooklistGroup.BOOK) {
            // Don't indent books
            view.setPaddingRelative(0, 0, 0, 0);

        } else {
            // Indent (0..) based on level (1..)
            view.setPaddingRelative((level - 1) * levelIndent, 0, 0, 0);

            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                    view.getLayoutParams();

            // Adjust the line spacing as required
            lp.height = groupRowHeight;
            if (level == 1 && groupLevel1topMargin != 0) {
                lp.setMargins(0, groupLevel1topMargin, 0, 0);
            }
        }

        // Scale text/padding (recursively) if required
        final int textScale = style.getTextScale();
        if (textScale != Style.DEFAULT_TEXT_SCALE) {
            scaleTextViews(view, textScale);
        }
        return view;
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {

        //noinspection ConstantConditions
        cursor.moveToPosition(position);

        // further binding depends on the type of row (i.e. holder).
        //noinspection ConstantConditions
        holder.onBindViewHolder(position, nodeData, style);
    }

    /**
     * Format the source string according to the BooklistGroup id.
     *
     * @param context Current context
     * @param groupId the BooklistGroup id
     * @param text    value (as a String) to reformat
     * @param locale  optional, if a locale is needed but not passed in,
     *                the user-locale will be used.
     *
     * @return Formatted string,
     *         or original string when no special format was needed or on any failure
     */
    @NonNull
    private String format(@NonNull final Context context,
                          @BooklistGroup.Id final int groupId,
                          @Nullable final String text,
                          @Nullable final Locale locale) {

        switch (groupId) {
            case BooklistGroup.AUTHOR: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_author);
                } else {
                    return text;
                }
            }
            case BooklistGroup.SERIES: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_series);

                } else if (reorderTitleForDisplaying) {
                    return ReorderHelper.reorder(context, text, locale);
                } else {
                    return text;
                }
            }
            case BooklistGroup.PUBLISHER: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_publisher);

                } else if (reorderTitleForDisplaying) {
                    return ReorderHelper.reorder(context, text, locale);
                } else {
                    return text;
                }
            }
            case BooklistGroup.READ_STATUS: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_read_status);
                } else {
                    if (ParseUtils.parseBoolean(text, true)) {
                        return context.getString(R.string.lbl_read);
                    } else {
                        return context.getString(R.string.lbl_unread);
                    }
                }
            }
            case BooklistGroup.LANGUAGE: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_language);
                } else {
                    return ServiceLocator.getInstance().getLanguages()
                                         .getDisplayNameFromISO3(context, text);
                }
            }
            case BooklistGroup.CONDITION: {
                if (text != null && !text.isEmpty()) {
                    try {
                        final int i = Integer.parseInt(text);
                        if (i < conditionDescriptions.length) {
                            return conditionDescriptions[i];
                        }
                    } catch (@NonNull final NumberFormatException ignore) {
                        // ignore
                    }
                }
                return context.getString(R.string.unknown);
            }
            case BooklistGroup.RATING: {
                // This is the text based formatting, as used by the level/scroller text.
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_rating);
                } else {
                    try {
                        // Locale independent.
                        final int i = Integer.parseInt(text);
                        // If valid, get the name
                        if (i >= 0 && i <= Book.RATING_STARS) {
                            return context.getResources()
                                          .getQuantityString(R.plurals.n_stars, i, i);
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        if (BuildConfig.DEBUG /* always */) {
                            Logger.d(TAG, e, "RATING=" + text);
                        }
                    }
                    return text;
                }
            }
            case BooklistGroup.LENDING: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.lbl_available);
                } else {
                    return text;
                }
            }

            case BooklistGroup.DATE_ACQUIRED_YEAR:
            case BooklistGroup.DATE_ADDED_YEAR:
            case BooklistGroup.DATE_LAST_UPDATE_YEAR:
            case BooklistGroup.DATE_PUBLISHED_YEAR:
            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR:
            case BooklistGroup.DATE_READ_YEAR: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_year);
                } else {
                    return text;
                }
            }

            case BooklistGroup.DATE_ACQUIRED_MONTH:
            case BooklistGroup.DATE_ADDED_MONTH:
            case BooklistGroup.DATE_LAST_UPDATE_MONTH:
            case BooklistGroup.DATE_PUBLISHED_MONTH:
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH:
            case BooklistGroup.DATE_READ_MONTH: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_month);
                } else {
                    try {
                        final int m = Integer.parseInt(text);
                        // If valid, get the short name
                        if (m > 0 && m <= 12) {
                            return Month.of(m).getDisplayName(
                                    TextStyle.FULL_STANDALONE,
                                    Objects.requireNonNullElse(locale, userLocale));
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.e(TAG, "|text=`" + text + '`', e);
                        }
                    }
                    return text;
                }
            }

            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_LAST_UPDATE_DAY:
            case BooklistGroup.DATE_READ_DAY: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_day);
                } else {
                    return text;
                }
            }

            // BooklistGroup.BOOK only here to please lint
            case BooklistGroup.BOOK:
            case BooklistGroup.FORMAT:
            case BooklistGroup.GENRE:
            case BooklistGroup.LOCATION:
            case BooklistGroup.BOOKSHELF:
            case BooklistGroup.COLOR:
            case BooklistGroup.BOOK_TITLE_1ST_LETTER:
            case BooklistGroup.SERIES_TITLE_1ST_LETTER:
            default: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_field);
                } else {
                    return text;
                }
            }
        }
    }

    @NonNull
    public Locale getUserLocale() {
        return userLocale;
    }

    private boolean isImageCachingEnabled() {
        return imageCachingEnabled;
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
                                final float textSizeInSpUnits,
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
    public String[] getPopupText(final int position) {
        return new String[]{getLevelText(position, 1),
                            getLevelText(position, 2)};
    }

    /**
     * Get the text associated with the matching level group for the given position.
     *
     * @param position to use
     * @param level    to get
     *
     * @return the text for that level, or {@code null} if none present.
     */
    @Nullable
    public String getLevelText(final int position,
                               @IntRange(from = 1) final int level) {

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
                return nodeData.getString(DBKey.TITLE);

            } else {
                // it's a group; use the display domain as the text
                final BooklistGroup group = style.getGroupByLevel(level);
                //noinspection ConstantConditions
                final String value = nodeData.getString(
                        group.getDisplayDomainExpression().getDomain().getName());
                if (!value.isEmpty()) {
                    return format(inflater.getContext(), group.getId(), value, null);
                }
            }
        } catch (@NonNull final CursorIndexOutOfBoundsException e) {
            // Seen a number of times. No longer reproducible, but paranoia...
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "|level=" + level, e);
            }
        }
        return null;
    }

    public void setRowClickListener(@Nullable final OnRowClickListener listener) {
        this.rowClickListener = listener;
    }

    public void setRowLongClickListener(@Nullable final OnRowLongClickListener listener) {
        this.rowLongClickListener = listener;
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

    @FunctionalInterface
    public interface OnRowClickListener {

        /**
         * User clicked a row.
         *
         * @param position The position of the item within the adapter's data set.
         */
        void onClick(int position);
    }

    @FunctionalInterface
    public interface OnRowLongClickListener {

        /**
         * User long-clicked a row.
         *
         * @param v        View clicked
         * @param position The position of the item within the adapter's data set.
         *
         * @return true if the callback consumed the long click, false otherwise.
         */
        boolean onLongClick(@NonNull View v,
                            int position);
    }

    /**
     * Base for all {@link BooklistGroup} ViewHolder classes.
     */
    abstract static class RowViewHolder
            extends RecyclerView.ViewHolder {

        /**
         * The view to install on-click listeners on. Can be the same as the itemView.
         * This is also the view where we can/should add tags,
         * as it is this View that will be passed to the onClick handlers.
         */
        @NonNull
        View onClickTargetView;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         */
        RowViewHolder(@NonNull final View itemView) {
            super(itemView);
            // if present, redirect all clicks to this view
            // 2022-09-07: not used for now, but keeping for future usage
            onClickTargetView = itemView.findViewById(R.id.ROW_ONCLICK_TARGET);
            if (onClickTargetView == null) {
                // if not, then just let the main view get them.
                onClickTargetView = itemView;
            }
        }

        /**
         * Bind the data to the views in the holder.
         *
         * @param position The position of the item within the adapter's data set.
         * @param rowData  with data to bind
         * @param style    to use
         */
        abstract void onBindViewHolder(int position,
                                       @NonNull DataHolder rowData,
                                       @NonNull Style style);
    }

    /**
     * ViewHolder for a {@link BooklistGroup#BOOK} row.
     */
    @SuppressWarnings("WeakerAccess")
    static class BookHolder
            extends RowViewHolder {

        /** The parent adapter. */
        @NonNull
        private final BooklistAdapter adapter;

        /** Format string. */
        @NonNull
        private final String a_bracket_b_bracket;

        @NonNull
        private final BooksonbookshelfRowBookBinding vb;
        @NonNull
        private final Languages languages;
        /** Only active when running in debug mode; displays the "position/rowId" for a book. */
        @Nullable
        private TextView dbgRowIdView;
        /** each holder has its own loader - the more cores the cpu has, the faster we load. */
        @Nullable
        private ImageViewLoader imageLoader;
        @Nullable
        private UseFields use;

        /**
         * Constructor.
         * <p>
         * <strong>Note:</strong> the itemView can be re-used.
         * Hence make sure to explicitly set visibility.
         *
         * @param adapter  the hosting adapter
         * @param itemView the view specific for this holder
         */
        BookHolder(@NonNull final BooklistAdapter adapter,
                   @NonNull final View itemView) {
            super(itemView);
            this.adapter = adapter;

            languages = ServiceLocator.getInstance().getLanguages();

            vb = BooksonbookshelfRowBookBinding.bind(itemView);

            final Context context = itemView.getContext();

            a_bracket_b_bracket = context.getString(R.string.a_bracket_b_bracket);

            if (adapter.style.isShowField(Style.Screen.List, FieldVisibility.COVER[0])) {
                // Do not go overkill here by adding a full-blown CoverHandler.
                // We only provide zooming by clicking on the image.
                vb.coverImage0.setOnClickListener(this::onZoomCover);

                imageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                                  adapter.coverLongestSide,
                                                  adapter.coverLongestSide);
            } else {
                // hide it if not in use.
                vb.coverImage0.setVisibility(View.GONE);
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                // add a text view to display the "position/rowId" for a book
                dbgRowIdView = new TextView(context);
                dbgRowIdView.setId(View.generateViewId());
                dbgRowIdView.setTextColor(Color.BLUE);
                dbgRowIdView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);

                final ConstraintLayout parentLayout = itemView.findViewById(R.id.card_frame);
                parentLayout.addView(dbgRowIdView, 0);

                final ConstraintSet set = new ConstraintSet();
                set.clone(parentLayout);
                set.connect(dbgRowIdView.getId(), ConstraintSet.TOP,
                            R.id.cover_image_0, ConstraintSet.BOTTOM);
                set.connect(dbgRowIdView.getId(), ConstraintSet.BOTTOM,
                            ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                set.connect(dbgRowIdView.getId(), ConstraintSet.END,
                            R.id.col1, ConstraintSet.START);
                set.setVerticalBias(dbgRowIdView.getId(), 1.0f);

                set.applyTo(parentLayout);
            }
        }

        /**
         * Zoom the given cover.
         *
         * @param coverView passed in to allow for future expansion
         */
        private void onZoomCover(@NonNull final View coverView) {
            final String uuid = (String) coverView.getTag(R.id.TAG_THUMBNAIL_UUID);
            Book.getPersistedCoverFile(uuid, 0).ifPresent(file -> {
                final FragmentActivity activity = (FragmentActivity) coverView.getContext();
                ZoomedImageDialogFragment.launch(activity.getSupportFragmentManager(), file);
            });
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final Style style) {
            if (use == null) {
                // init once
                use = new UseFields(rowData, style);
            }

            // Titles (book/series) are NOT reordered here.
            // It does not make much sense in this particular view/holder,
            // and slows down scrolling to much.

            // {@link BoBTask#fixedDomainList}
            vb.title.setText(rowData.getString(DBKey.TITLE));

            // {@link BoBTask#fixedDomainList}
            vb.iconRead.setVisibility(
                    rowData.getBoolean(DBKey.READ__BOOL) ? View.VISIBLE : View.GONE);

            if (use.signed) {
                final boolean isSet = rowData.getBoolean(DBKey.SIGNED__BOOL);
                vb.iconSigned.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }

            if (use.edition) {
                final boolean isSet = (rowData.getLong(DBKey.EDITION__BITMASK)
                                       & Book.Edition.FIRST) != 0;
                vb.iconFirstEdition.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }

            if (use.loanee) {
                final boolean isSet = !rowData.getString(DBKey.LOANEE_NAME).isEmpty();
                vb.iconLendOut.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }

            if (use.cover0) {
                setImageView(rowData.getString(DBKey.BOOK_UUID));
            }

            if (use.series) {
                if (style.hasGroup(BooklistGroup.SERIES)) {
                    vb.seriesTitle.setVisibility(View.GONE);
                    showOrHideSeriesNumber(rowData);
                } else {
                    vb.seriesNum.setVisibility(View.GONE);
                    vb.seriesNumLong.setVisibility(View.GONE);
                    showOrHideSeriesText(rowData);
                }
            }

            if (use.rating) {
                final float rating = rowData.getFloat(DBKey.RATING);
                if (rating > 0) {
                    vb.rating.setRating(rating);
                    vb.rating.setVisibility(View.VISIBLE);
                } else {
                    vb.rating.setVisibility(View.GONE);
                }
            }

            if (use.author) {
                showOrHide(vb.author, rowData.getString(DBKey.AUTHOR_FORMATTED));
            }

            if (use.publisher || use.publicationDate) {
                showOrHidePublisher(rowData, use.publisher, use.publicationDate);
            }

            // {@link BoBTask#fixedDomainList}
            if (use.isbn) {
                showOrHide(vb.isbn, rowData.getString(DBKey.BOOK_ISBN));
            }

            if (use.format) {
                showOrHide(vb.format, rowData.getString(DBKey.FORMAT));
            }

            if (use.condition) {
                final int condition = rowData.getInt(DBKey.BOOK_CONDITION);
                if (condition > 0) {
                    showOrHide(vb.condition, adapter.conditionDescriptions[condition]);
                } else {
                    // Hide "Unknown" condition
                    vb.condition.setVisibility(View.GONE);
                }
            }

            // {@link BoBTask#fixedDomainList}
            if (use.language) {
                final String language = languages.getDisplayNameFromISO3(
                        vb.language.getContext(), rowData.getString(DBKey.LANGUAGE));
                showOrHide(vb.language, language);
            }

            if (use.location) {
                showOrHide(vb.location, rowData.getString(DBKey.LOCATION));
            }

            if (use.bookshelves) {
                showOrHide(vb.shelves, rowData.getString(DBKey.BOOKSHELF_NAME_CSV));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                if (dbgRowIdView != null) {
                    final String txt = String.valueOf(position) + '/'
                                       + rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
                    dbgRowIdView.setText(txt);
                }
            }
        }

        /**
         * The combined (primary) Series title + number.
         * Shown if we're NOT grouping by title AND the user enabled this.
         * <p>
         * The views {@code vb.seriesNum} and {@code vb.seriesNumLong} will are hidden.
         *
         * @param rowData with the data
         */
        private void showOrHideSeriesText(@NonNull final DataHolder rowData) {
            if (rowData.contains(DBKey.SERIES_TITLE)) {
                String seriesTitle = rowData.getString(DBKey.SERIES_TITLE);
                if (!seriesTitle.isBlank()) {
                    if (rowData.contains(DBKey.SERIES_BOOK_NUMBER)) {
                        final String number = rowData.getString(DBKey.SERIES_BOOK_NUMBER);
                        if (!number.isBlank()) {
                            seriesTitle = String.format(a_bracket_b_bracket, seriesTitle, number);
                        }
                    }
                    vb.seriesTitle.setVisibility(View.VISIBLE);
                    vb.seriesTitle.setText(seriesTitle);
                    return;
                }
            }
            vb.seriesTitle.setVisibility(View.GONE);
        }

        /**
         * Shown the Series number if we're grouping by Series AND the user enabled this.
         * The view {@code vb.seriesTitle} is hidden.
         * <p>
         * If the Series number is a short piece of text (len <= 4 characters).
         * we show it in {@code vb.seriesNum}.
         * If it is a long piece of text (len > 4 characters)
         * we show it in {@code vb.seriesNumLong}.
         *
         * @param rowData with the data
         */
        private void showOrHideSeriesNumber(@NonNull final DataHolder rowData) {
            if (rowData.contains(DBKey.SERIES_BOOK_NUMBER)) {
                final String number = rowData.getString(DBKey.SERIES_BOOK_NUMBER);
                if (!number.isBlank()) {
                    // Display it in one of the views, based on the size of the text.
                    // 4 characters is based on e.g. "1.12" being considered short
                    // and e.g. "1|omnibus" being long.
                    if (number.length() > 4) {
                        vb.seriesNum.setVisibility(View.GONE);
                        vb.seriesNumLong.setText(number);
                        vb.seriesNumLong.setVisibility(View.VISIBLE);
                    } else {
                        vb.seriesNum.setText(number);
                        vb.seriesNum.setVisibility(View.VISIBLE);
                        vb.seriesNumLong.setVisibility(View.GONE);
                    }
                    return;
                }
            }
            vb.seriesNum.setVisibility(View.GONE);
            vb.seriesNumLong.setVisibility(View.GONE);
        }

        private void showOrHidePublisher(@NonNull final DataHolder rowData,
                                         final boolean usePub,
                                         final boolean usePubDate) {
            String text = null;
            if (usePub) {
                text = rowData.getString(DBKey.PUBLISHER_NAME_CSV);
            }

            String date = null;
            if (usePubDate) {
                final String dateStr = rowData.getString(DBKey.BOOK_PUBLICATION__DATE);
                date = new PartialDate(dateStr).toDisplay(adapter.getUserLocale(), dateStr);
            }

            if (text != null && !text.isBlank() && date != null && !date.isBlank()) {
                // Combine Publisher and date
                showOrHide(vb.publisher, String.format(a_bracket_b_bracket, text, date));

            } else {
                // there was no publisher, just use the date
                showOrHide(vb.publisher, date);
            }
        }

        /**
         * Conditionally display 'text'.
         *
         * @param view to populate
         * @param text to set
         */
        private void showOrHide(@NonNull final TextView view,
                                @Nullable final String text) {
            if (text != null && !text.isEmpty()) {
                view.setText(text);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }

        /**
         * Load the image owned by the UUID/cIdx into the destination ImageView.
         * Handles checking & storing in the cache.
         * <p>
         * Images and placeholder will always be scaled to a fixed size.
         *
         * @param uuid UUID of the book
         */
        void setImageView(@NonNull final String uuid) {
            // store the uuid for use in the OnClickListener
            vb.coverImage0.setTag(R.id.TAG_THUMBNAIL_UUID, uuid);

            final Context context = vb.coverImage0.getContext();

            // 1. If caching is used, and we don't have cache building happening, check it.
            if (adapter.isImageCachingEnabled()) {
                final CoverCacheDao coverCacheDao = ServiceLocator.getInstance().getCoverCacheDao();
                if (!coverCacheDao.isBusy()) {
                    final Bitmap bitmap = coverCacheDao.getCover(context, uuid, 0,
                                                                 adapter.coverLongestSide,
                                                                 adapter.coverLongestSide);

                    if (bitmap != null) {
                        //noinspection ConstantConditions
                        imageLoader.fromBitmap(vb.coverImage0, bitmap);
                        return;
                    }
                }
            }

            // 2. Cache did not have it, or we were not allowed to check.
            final Optional<File> file = Book.getPersistedCoverFile(uuid, 0);
            // Check if the file exists; if it does not...
            if (file.isEmpty()) {
                // leave the space blank, but preserve the width BASED on the coverLongestSide!
                final ViewGroup.LayoutParams lp = vb.coverImage0.getLayoutParams();
                lp.width = (int) (adapter.coverLongestSide * HW_RATIO);
                lp.height = 0;
                vb.coverImage0.setLayoutParams(lp);
                vb.coverImage0.setImageDrawable(null);
                return;
            }

            // Once we get here, we know the file is valid
            if (adapter.isImageCachingEnabled()) {
                // 1. Gets the image from the file system and display it.
                // 2. Start a subsequent task to send it to the cache.
                //noinspection ConstantConditions
                imageLoader.fromFile(vb.coverImage0, file.get(), bitmap -> {
                    if (bitmap != null) {
                        ServiceLocator.getInstance().getCoverCacheDao().saveCover(
                                uuid, 0, bitmap,
                                adapter.coverLongestSide,
                                adapter.coverLongestSide);
                    }
                });
            } else {
                // Cache not used: Get the image from the file system and display it.
                //noinspection ConstantConditions
                imageLoader.fromFile(vb.coverImage0, file.get(), null);
            }
        }

        /**
         * Cache the 'use' flags for {@link #onBindViewHolder(int, DataHolder, Style)}.
         */
        private static class UseFields {
            final boolean isbn;
            final boolean signed;
            final boolean edition;
            final boolean loanee;
            final boolean cover0;
            final boolean rating;
            final boolean author;
            final boolean publisher;
            final boolean publicationDate;
            final boolean format;
            final boolean condition;
            final boolean language;
            final boolean location;
            final boolean bookshelves;
            final boolean series;

            UseFields(@NonNull final DataHolder rowData,
                      @NonNull final Style style) {
                isbn = style.isShowField(Style.Screen.List, DBKey.BOOK_ISBN);
                series = style.isShowField(Style.Screen.List, DBKey.FK_SERIES);

                signed = style.isShowField(Style.Screen.List, DBKey.SIGNED__BOOL)
                         && rowData.contains(DBKey.SIGNED__BOOL);
                edition = style.isShowField(Style.Screen.List, DBKey.EDITION__BITMASK)
                          && rowData.contains(DBKey.EDITION__BITMASK);
                loanee = style.isShowField(Style.Screen.List, DBKey.LOANEE_NAME)
                         && rowData.contains(DBKey.LOANEE_NAME);
                cover0 = style.isShowField(Style.Screen.List, FieldVisibility.COVER[0])
                         && rowData.contains(DBKey.BOOK_UUID);
                rating = style.isShowField(Style.Screen.List, DBKey.RATING)
                         && rowData.contains(DBKey.RATING);
                author = style.isShowField(Style.Screen.List, DBKey.FK_AUTHOR)
                         && rowData.contains(DBKey.AUTHOR_FORMATTED);
                publisher = style.isShowField(Style.Screen.List, DBKey.FK_PUBLISHER)
                            && rowData.contains(DBKey.PUBLISHER_NAME_CSV);
                publicationDate = style.isShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE)
                                  && rowData.contains(DBKey.BOOK_PUBLICATION__DATE);
                format = style.isShowField(Style.Screen.List, DBKey.FORMAT)
                         && rowData.contains(DBKey.FORMAT);
                condition = style.isShowField(Style.Screen.List, DBKey.BOOK_CONDITION)
                            && rowData.contains(DBKey.BOOK_CONDITION);
                language = style.isShowField(Style.Screen.List, DBKey.LANGUAGE);
                location = style.isShowField(Style.Screen.List, DBKey.LOCATION)
                           && rowData.contains(DBKey.LOCATION);
                bookshelves = style.isShowField(Style.Screen.List, DBKey.FK_BOOKSHELF)
                              && rowData.contains(DBKey.BOOKSHELF_NAME_CSV);
            }
        }
    }

    static class RatingHolder
            extends RowViewHolder {

        /**
         * Key of the related data column.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @NonNull
        private final String key;
        @NonNull
        private final RatingBar ratingBar;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param group    the group this holder represents
         */
        RatingHolder(@NonNull final View itemView,
                     @NonNull final BooklistGroup group) {
            super(itemView);
            key = group.getDisplayDomainExpression().getDomain().getName();
            ratingBar = itemView.findViewById(R.id.rating);
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final Style style) {
            ratingBar.setRating(rowData.getInt(key));
        }
    }

    /**
     * ViewHolder to handle any field that can be displayed as a string.
     * <p>
     * Assumes there is a 'name' TextView.
     */
    static class GenericStringHolder
            extends RowViewHolder {

        /*** Default resource id for the View to populate. */
        @IdRes
        private static final int textViewId = R.id.name;

        /**
         * The group this holder represents.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @BooklistGroup.Id
        final int groupId;
        /*** View to populate. */
        @NonNull
        final TextView textView;
        /** The parent adapter. */
        @NonNull
        final BooklistAdapter adapter;
        /**
         * Key of the related data column.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @NonNull
        private final String key;

        /**
         * Constructor.
         *
         * @param adapter  the hosting adapter
         * @param itemView the view specific for this holder
         * @param group    the group this holder represents
         */
        GenericStringHolder(@NonNull final BooklistAdapter adapter,
                            @NonNull final View itemView,
                            @NonNull final BooklistGroup group) {
            super(itemView);
            this.adapter = adapter;
            groupId = group.getId();
            key = group.getDisplayDomainExpression().getDomain().getName();
            textView = itemView.findViewById(textViewId);
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final Style style) {
            textView.setText(format(rowData.getString(key)));

            // Debugger help: color the row according to state
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                itemView.setBackgroundColor(adapter.getDbgRowColor(rowData.getInt(DBKey.PK_ID)));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                final String dbgText = " " + position + '/'
                                       + rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);

                // just hang it of the existing text view.
                final CharSequence text = textView.getText();
                final SpannableString dbg = new SpannableString(text + dbgText);
                dbg.setSpan(new ForegroundColorSpan(Color.BLUE), text.length(), dbg.length(), 0);
                dbg.setSpan(new RelativeSizeSpan(0.7f), text.length(), dbg.length(), 0);

                textView.setText(dbg);
            }
        }

        /**
         * For a simple row, use the default group formatter to format it.
         *
         * @param text String to display; can be {@code null} or empty
         *
         * @return the formatted text
         */
        @NonNull
        public String format(@Nullable final String text) {
            return adapter.format(itemView.getContext(), groupId, text, null);
        }
    }

    /**
     * ViewHolder for a row that displays a generic string with a checkable icon at the 'end'.
     */
    static class CheckableStringHolder
            extends GenericStringHolder {

        @NonNull
        final ImageView completeView;
        /** Column name of related boolean column. */
        private String completeKey;

        /**
         * Constructor.
         *
         * @param adapter  the hosting adapter
         * @param itemView the view specific for this holder
         * @param group    the group this holder represents
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        CheckableStringHolder(@NonNull final BooklistAdapter adapter,
                              @NonNull final View itemView,
                              @NonNull final BooklistGroup group) {
            super(adapter, itemView, group);

            completeView = itemView.findViewById(R.id.cbx_is_complete);
        }

        /**
         * Set the Column name to use for the 'isComplete' status.
         *
         * @param columnKey key name
         */
        void setIsCompleteColumnKey(@NonNull final String columnKey) {
            completeKey = columnKey;
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final Style style) {
            super.onBindViewHolder(position, rowData, style);
            completeView.setVisibility(rowData.getBoolean(
                    completeKey) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * ViewHolder for an Author.
     */
    static class AuthorHolder
            extends CheckableStringHolder {

        /**
         * Constructor.
         *
         * @param adapter  the hosting adapter
         * @param itemView the view specific for this holder
         * @param group    the group this holder represents
         */
        AuthorHolder(@NonNull final BooklistAdapter adapter,
                     @NonNull final View itemView,
                     @NonNull final BooklistGroup group) {
            super(adapter, itemView, group);
            setIsCompleteColumnKey(DBKey.AUTHOR_IS_COMPLETE);
        }
    }

    /**
     * ViewHolder for a Series.
     */
    static class SeriesHolder
            extends CheckableStringHolder {

        /** Stores this value in between the #onBindViewHolder and the #format methods. */
        private String bookLanguage;

        /**
         * Constructor.
         *
         * @param adapter  the hosting adapter
         * @param itemView the view specific for this holder
         * @param group    the group this holder represents
         */
        SeriesHolder(@NonNull final BooklistAdapter adapter,
                     @NonNull final View itemView,
                     @NonNull final BooklistGroup group) {
            super(adapter, itemView, group);
            setIsCompleteColumnKey(DBKey.SERIES_IS_COMPLETE);
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final Style style) {
            // grab the book language first for use in #format
            bookLanguage = rowData.getString(DBKey.LANGUAGE);

            super.onBindViewHolder(position, rowData, style);
        }

        @Override
        @NonNull
        public String format(@Nullable final String text) {
            final Context context = itemView.getContext();
            // FIXME: translated series are reordered in the book's language
            // It should be done using the Series language
            // but as long as we don't store the Series language there is no point
            @Nullable
            final Locale bookLocale = ServiceLocator.getInstance().getAppLocale()
                                                    .getLocale(context, bookLanguage);
            return adapter.format(context, groupId, text, bookLocale);
        }
    }
}

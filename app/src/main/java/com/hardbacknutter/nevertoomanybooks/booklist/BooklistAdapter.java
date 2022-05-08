/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.SharedPreferences;
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
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
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
     * See {@link #mCoverLongestSide}.
     */
    private static final float HW_RATIO = 0.6f;

    /** Cached locale. */
    @NonNull
    private final Locale mUserLocale;
    /** The padding indent (in pixels) added for each level: padding = (level-1) * mLevelIndent. */
    private final int mLevelIndent;
    /** Cached inflater. */
    @NonNull
    private final LayoutInflater mInflater;
    /** Whether to use the covers DAO caching. */
    private final boolean mImageCachingEnabled;
    private final boolean mReorderTitleForDisplaying;
    /** caching the book condition strings. */
    private final String[] mConditionDescriptions;
    /** A collection of 'in-use' flags for the fields we might display. */
    private FieldsInUse mFieldsInUse;
    /** List style to apply. */
    private ListStyle mStyle;
    private int mGroupRowHeight;
    /** Top margin to use for Level 1 <strong>if</strong> the {@link #mGroupRowHeight} is wrap. */
    private int mGroupLevel1topMargin;
    @LayoutRes
    private int mBookLayoutId;
    /** Longest side for a cover in pixels. */
    private int mCoverLongestSide;
    /** The cursor is the equivalent of the 'list of items'. */
    @Nullable
    private Cursor mCursor;
    /** provides read only access to the row data. */
    @Nullable
    private DataHolder mNodeData;
    /** The combined click and long-click listeners for a single row. */
    @Nullable
    private OnRowClickedListener mOnRowClickedListener;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public BooklistAdapter(@NonNull final Context context) {

        mInflater = LayoutInflater.from(context);
        mUserLocale = context.getResources().getConfiguration().getLocales().get(0);
        mImageCachingEnabled = ImageUtils.isImageCachingEnabled();

        mLevelIndent = context.getResources()
                              .getDimensionPixelSize(R.dimen.bob_group_level_padding_start);
        mConditionDescriptions = context.getResources().getStringArray(R.array.conditions_book);

        mReorderTitleForDisplaying = ReorderHelper.forDisplay(context);

        // getItemId is implemented.
        setHasStableIds(true);
    }

    /**
     * Set the Cursor and related BooklistStyle.
     *
     * @param context Current context
     * @param cursor  cursor with the 'list of items'
     * @param style   Style reference.
     */
    public void setCursor(@NonNull final Context context,
                          @NonNull final Cursor cursor,
                          @NonNull final ListStyle style) {
        // First set the style and prepare the related data
        mStyle = style;
        mFieldsInUse = new FieldsInUse(mStyle);

        mGroupRowHeight = mStyle.getGroupRowHeight(context);
        if (mGroupRowHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            mGroupLevel1topMargin = context
                    .getResources().getDimensionPixelSize(R.dimen.bob_group_level_1_margin_top);
        }

        @ListStyle.CoverScale
        final int frontCoverScale = mStyle.getListScreenBookFields().getCoverScale();

        // The thumbnail scale is used to retrieve the cover dimensions
        // We use a square space for the image so both portrait/landscape images work out.
        final TypedArray coverSizes = context
                .getResources().obtainTypedArray(R.array.cover_book_list_longest_side);
        mCoverLongestSide = coverSizes.getDimensionPixelSize(frontCoverScale, 0);
        coverSizes.recycle();

        // The thumbnail scale defines the Book layout file to use.
        // The layout names ending in 2/3 are ONLY as reference,
        // with the hardcoded values in them always replaced at runtime.
        if (frontCoverScale > ListStyle.IMAGE_SCALE_2_MEDIUM) {
            // Large
            mBookLayoutId = R.layout.booksonbookshelf_row_book_scale_3;
        } else {
            // Small and Medium
            mBookLayoutId = R.layout.booksonbookshelf_row_book_scale_2;
        }

        // now the actual new cursor
        mCursor = cursor;
        mNodeData = new CursorRow(mCursor);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearCursor() {
        mCursor = null;
        mNodeData = null;
        notifyDataSetChanged();
    }

    /**
     * Check if the adapter is ready to serve data.
     * i.e. if it has a valid Cursor.
     *
     * @return cursor
     */
    boolean hasCursor() {
        return mCursor != null;
    }

    /**
     * Get the cursor.
     *
     * @return cursor
     *
     * @throws NullPointerException if the cursor is not initialised - which would be a bug.
     */
    @NonNull
    public Cursor getCursor() {
        return Objects.requireNonNull(mCursor, "mCursor");
    }

    @Override
    public long getItemId(final int position) {
        if (mCursor != null && mCursor.moveToPosition(position)) {
            //noinspection ConstantConditions
            return mNodeData.getLong(DBKey.PK_ID);
        } else {
            return RecyclerView.NO_ID;
        }
    }

    @Override
    public int getItemCount() {
        return mCursor != null ? mCursor.getCount() : 0;
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
        if (mCursor != null && mCursor.moveToPosition(position)) {
            //noinspection ConstantConditions
            return mNodeData.getInt(DBKey.KEY_BL_NODE_GROUP);
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
                holder = new BookHolder(this, itemView, mFieldsInUse, mCoverLongestSide);
                break;

            case BooklistGroup.AUTHOR:
                holder = new AuthorHolder(this, itemView,
                                          mStyle.getGroups().getGroupByIdOrCrash(groupId));
                break;

            case BooklistGroup.SERIES:
                holder = new SeriesHolder(this, itemView,
                                          mStyle.getGroups().getGroupByIdOrCrash(groupId));
                break;

            case BooklistGroup.RATING:
                holder = new RatingHolder(itemView,
                                          mStyle.getGroups().getGroupByIdOrCrash(groupId));
                break;

            default:
                holder = new GenericStringHolder(this, itemView,
                                                 mStyle.getGroups().getGroupByIdOrCrash(groupId));
                break;
        }

        // test for the mOnRowClickedListener inside the lambda, this allows changing it if needed
        holder.onClickTargetView.setOnClickListener(v -> {
            if (mOnRowClickedListener != null) {
                mOnRowClickedListener.onItemClick(holder.getBindingAdapterPosition());
            }
        });

        holder.onClickTargetView.setOnLongClickListener(v -> {
            if (mOnRowClickedListener != null) {
                return mOnRowClickedListener.onItemLongClick(v, holder.getBindingAdapterPosition());
            }
            return false;
        });

        return holder;
    }

    /**
     * Create the View for the specified group.
     *
     * @param parent     The ViewGroup into which the new View will be added after it is bound to
     *                   an adapter position.
     * @param groupKeyId The view type of the new View == the group id
     *
     * @return the view
     */
    @NonNull
    private View createView(@NonNull final ViewGroup parent,
                            @BooklistGroup.Id final int groupKeyId) {
        //noinspection ConstantConditions
        final int level = mNodeData.getInt(DBKey.KEY_BL_NODE_LEVEL);

        @LayoutRes
        final int layoutId;
        if (groupKeyId == BooklistGroup.BOOK) {
            layoutId = mBookLayoutId;

        } else if (groupKeyId == BooklistGroup.RATING) {
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

        final View view = mInflater.inflate(layoutId, parent, false);

        if (groupKeyId == BooklistGroup.BOOK) {
            // Don't indent books
            view.setPaddingRelative(0, 0, 0, 0);

        } else {
            // Indent (0..) based on level (1..)
            view.setPaddingRelative((level - 1) * mLevelIndent, 0, 0, 0);

            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                    view.getLayoutParams();

            // Adjust the line spacing as required
            lp.height = mGroupRowHeight;
            if (level == 1 && mGroupLevel1topMargin != 0) {
                lp.setMargins(0, mGroupLevel1topMargin, 0, 0);
            }
        }

        // Scale text/padding (recursively) if required
        final TextScale textScale = mStyle.getTextScale();
        if (!textScale.isDefaultScale()) {
            final Context context = parent.getContext();
            scaleTextViews(view,
                           textScale.getFontSizeInSpUnits(context),
                           textScale.getPaddingFactor(context));
        }
        return view;
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {

        //noinspection ConstantConditions
        mCursor.moveToPosition(position);

        // further binding depends on the type of row (i.e. holder).
        //noinspection ConstantConditions
        holder.onBindViewHolder(position, mNodeData, mStyle);
    }

    /**
     * Format the source string according to the GroupKey (id).
     *
     * @param context    Current context
     * @param groupKeyId the GroupKey id
     * @param text       value (as a String) to reformat
     * @param locale     optional, if a locale is needed but not passed in,
     *                   the user-locale will be used.
     *
     * @return Formatted string,
     *         or original string when no special format was needed or on any failure
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    private String format(@NonNull final Context context,
                          @BooklistGroup.Id final int groupKeyId,
                          @Nullable final String text,
                          @Nullable final Locale locale) {

        switch (groupKeyId) {
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

                } else if (mReorderTitleForDisplaying) {
                    return ReorderHelper.reorder(context, text, locale);
                } else {
                    return text;
                }
            }
            case BooklistGroup.PUBLISHER: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_publisher);

                } else if (mReorderTitleForDisplaying) {
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
                        if (i < mConditionDescriptions.length) {
                            return mConditionDescriptions[i];
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
                        Logger.error(TAG, e);
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
                                    Objects.requireNonNullElse(locale, mUserLocale));
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

            case BooklistGroup.FORMAT:
            case BooklistGroup.GENRE:
            case BooklistGroup.LOCATION:
            case BooklistGroup.BOOKSHELF:
            case BooklistGroup.COLOR:
            default: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.bob_empty_field);
                } else {
                    return text;
                }
            }
        }
    }

    /**
     * Get the level for the given position.
     *
     * @param position Adapter position to query
     *
     * @return the level, or {@code 0} if unknown
     */
    int getLevel(final int position) {
        if (mCursor != null && mCursor.moveToPosition(position)) {
            //noinspection ConstantConditions
            return mNodeData.getInt(DBKey.KEY_BL_NODE_LEVEL);
        } else {
            return 0;
        }
    }

    @NonNull
    public Locale getUserLocale() {
        return mUserLocale;
    }

    private boolean isImageCachingEnabled() {
        return mImageCachingEnabled;
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
            final int groupCount = mStyle.getGroups().size() + 1;
            if (level > groupCount) {
                throw new IllegalArgumentException(
                        "level=" + level + "> (getGroupCount+1)=" + groupCount);
            }
        }

        // make sure it's still in range.
        final int clampedPosition = MathUtils.clamp(position, 0, getItemCount() - 1);
        if (mCursor == null || !mCursor.moveToPosition(clampedPosition)) {
            return null;
        }

        try {
            if (level > (mStyle.getGroups().size())) {
                // it's a book; use the title (no need to take the group.format round-trip).
                //noinspection ConstantConditions
                return mNodeData.getString(DBKey.KEY_TITLE);

            } else {
                // it's a group; use the display domain as the text
                final BooklistGroup group = mStyle.getGroups().getGroupByLevel(level);
                //noinspection ConstantConditions
                final String value = mNodeData.getString(
                        group.getDisplayDomainExpression().getDomain().getName());
                if (!value.isEmpty()) {
                    return format(mInflater.getContext(), group.getId(), value, null);
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

    public void setOnRowClickedListener(@Nullable final OnRowClickedListener onRowClickedListener) {
        mOnRowClickedListener = onRowClickedListener;
    }

    /**
     * Extended {@link View.OnClickListener} / {@link View.OnLongClickListener}.
     */
    public interface OnRowClickedListener {

        /**
         * User clicked a row.
         *
         * @param position The position of the item within the adapter's data set.
         */
        default void onItemClick(final int position) {
        }

        /**
         * User long-clicked a row.
         *
         * @param v        View clicked
         * @param position The position of the item within the adapter's data set.
         *
         * @return true if the callback consumed the long click, false otherwise.
         */
        default boolean onItemLongClick(@NonNull final View v,
                                        final int position) {
            return false;
        }
    }

    /**
     * Value class, initialized by the adapter, updated when the first rowData is fetched.
     * Reused by the holders.
     * <p>
     * These are the fields optionally shown on the Book level (row).
     */
    private static class FieldsInUse {

        /** Book row details. Based on global visibility user preference. */
        boolean edition;
        /** Book row details. Based on global visibility user preference. */
        boolean lending;
        /** Book row details. Based on global visibility user preference. */
        boolean series;
        /** Book row details. Based on global visibility user preference. */
        boolean signed;

        /** Book row details - Based on style. */
        boolean author;
        /** Book row details - Based on style. */
        boolean bookshelf;
        /** Book row details - Based on style. */
        boolean cover;
        /** Book row details - Based on style. */
        boolean format;
        /** Book row details - Based on style. */
        boolean isbn;
        /** Book row details - Based on style. */
        boolean location;
        /** Book row details - Based on style. */
        boolean pubDate;
        /** Book row details - Based on style. */
        boolean publisher;
        /** Book row details - Based on style. */
        boolean rating;

        /** Set to true after {@link #set} is called. */
        boolean isSet;

        /**
         * Constructor. Initialized by the adapter.
         *
         * @param style Style reference.
         */
        FieldsInUse(@NonNull final ListStyle style) {
            final SharedPreferences global = ServiceLocator.getGlobalPreferences();

            // Based on global visibility user preference.
            edition = DBKey.isUsed(global, DBKey.BITMASK_EDITION);
            lending = DBKey.isUsed(global, DBKey.KEY_LOANEE);
            series = DBKey.isUsed(global, DBKey.KEY_SERIES_TITLE);
            signed = DBKey.isUsed(global, DBKey.BOOL_SIGNED);

            final ListScreenBookFields bookFields = style.getListScreenBookFields();

            // Based on style
            author = bookFields.isShowField(global, ListScreenBookFields.PK_AUTHOR);
            bookshelf = bookFields.isShowField(global, ListScreenBookFields.PK_BOOKSHELVES);
            cover = bookFields.isShowField(global, ListScreenBookFields.PK_COVERS);
            format = bookFields.isShowField(global, ListScreenBookFields.PK_FORMAT);
            isbn = bookFields.isShowField(global, ListScreenBookFields.PK_ISBN);
            location = bookFields.isShowField(global, ListScreenBookFields.PK_LOCATION);
            pubDate = bookFields.isShowField(global, ListScreenBookFields.PK_PUB_DATE);
            publisher = bookFields.isShowField(global, ListScreenBookFields.PK_PUBLISHER);
            rating = bookFields.isShowField(global, ListScreenBookFields.PK_RATING);
        }

        /**
         * Update the in-use flags with row-data available fields.
         * Call this once only.
         *
         * @param rowData to read fields from
         */
        void set(@NonNull final DataHolder rowData) {
            if (isSet) {
                return;
            }
            isSet = true;

            edition = edition && rowData.contains(DBKey.BITMASK_EDITION);
            lending = lending && rowData.contains(DBKey.KEY_LOANEE);
            series = series && rowData.contains(DBKey.KEY_BOOK_NUM_IN_SERIES);
            signed = signed && rowData.contains(DBKey.BOOL_SIGNED);

            author = author && rowData.contains(DBKey.KEY_AUTHOR_FORMATTED);
            bookshelf = bookshelf && rowData.contains(DBKey.KEY_BOOKSHELF_NAME_CSV);
            cover = cover && rowData.contains(DBKey.KEY_BOOK_UUID);
            format = format && rowData.contains(DBKey.KEY_FORMAT);
            isbn = isbn && rowData.contains(DBKey.KEY_ISBN);
            location = location && rowData.contains(DBKey.KEY_LOCATION);
            pubDate = pubDate && rowData.contains(DBKey.DATE_BOOK_PUBLICATION);
            publisher = publisher && rowData.contains(DBKey.KEY_PUBLISHER_NAME);
            rating = rating && rowData.contains(DBKey.KEY_RATING);
        }
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
                                       @NonNull ListStyle style);
    }

    /**
     * ViewHolder for a {@link BooklistGroup#BOOK} row.
     */
    static class BookHolder
            extends RowViewHolder {

        /** The parent adapter. */
        @NonNull
        private final BooklistAdapter mAdapter;
        /** Format string. */
        @NonNull
        private final String mX_bracket_Y_bracket;

        private final int mCoverLongestSide;

        /** Whether to re-order the title as per global preference. */
        private final boolean mReorderTitle;
        /** A collection of 'in-use' flags for the fields we might display. */
        private final FieldsInUse mInUse;


        /** View that stores the related book field. */
        private final TextView mTitleView;
        /** The "I've read it" icon. */
        private final ImageView mReadIconView;
        /** The "signed" icon. */
        private final ImageView mSignedIconView;
        /** The "1th edition" icon. */
        private final ImageView mEditionIconView;
        /** The "lend out" icon. */
        private final ImageView mLendOutIconView;
        /** View that stores the related book field. */
        private final ImageView mCoverView;
        /** View that stores the Series number when it is a short piece of text. */
        private final TextView mSeriesNumView;
        /** View that stores the Series number when it is a long piece of text. */
        private final TextView mSeriesNumLongView;
        /** View that stores the related book field. */
        private final RatingBar mRatingBar;
        /** View that stores the related book field. */
        private final TextView mAuthorView;
        /** View that stores the related book field. */
        private final TextView mPublisherView;
        /** View that stores the related book field. */
        private final TextView mIsbnView;
        /** View that stores the related book field. */
        private final TextView mFormatView;
        /** View that stores the related book field. */
        private final TextView mLocationView;
        /** View that stores the related book field. */
        private final TextView mBookshelvesView;

        /** Only active when running in debug mode; displays the "position/rowId" for a book. */
        @Nullable
        private TextView mDbgRowIdView;

        @Nullable
        private ImageViewLoader mImageLoader;

        /**
         * Constructor.
         *
         * <strong>Note:</strong> the itemView can be re-used.
         * Hence make sure to explicitly set visibility.
         *
         * @param adapter          the hosting adapter
         * @param itemView         the view specific for this holder
         * @param fieldsInUse      which fields are used
         * @param coverLongestSide Longest side for a cover in pixels.
         */
        BookHolder(@NonNull final BooklistAdapter adapter,
                   @NonNull final View itemView,
                   @NonNull final FieldsInUse fieldsInUse,
                   final int coverLongestSide) {
            super(itemView);

            final Context context = itemView.getContext();

            mAdapter = adapter;

            // disabled (for now?) as it makes less sense in this particular view/holder,
            // and slows down scrolling.
            // mReorderTitle = ItemWithTitle.isReorderTitleForDisplaying(context);
            mReorderTitle = false;

            mInUse = fieldsInUse;

            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);

            // always visible
            mTitleView = itemView.findViewById(R.id.title);

            // hidden by default
            mReadIconView = itemView.findViewById(R.id.icon_read);
            mSignedIconView = itemView.findViewById(R.id.icon_signed);
            mEditionIconView = itemView.findViewById(R.id.icon_first_edition);
            mLendOutIconView = itemView.findViewById(R.id.icon_lend_out);

            mSeriesNumView = itemView.findViewById(R.id.series_num);
            mSeriesNumLongView = itemView.findViewById(R.id.series_num_long);

            mRatingBar = itemView.findViewById(R.id.rating);
            mAuthorView = itemView.findViewById(R.id.author);
            mPublisherView = itemView.findViewById(R.id.publisher);
            mIsbnView = itemView.findViewById(R.id.isbn);
            mFormatView = itemView.findViewById(R.id.format);
            mLocationView = itemView.findViewById(R.id.location);
            mBookshelvesView = itemView.findViewById(R.id.shelves);

            mCoverLongestSide = coverLongestSide;
            mCoverView = itemView.findViewById(R.id.cover_image_0);
            if (mInUse.cover) {
                // Do not go overkill here by adding a full-blown CoverHandler.
                // We only provide zooming by clicking on the image.
                mCoverView.setOnClickListener(this::onZoomCover);

                mImageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                                   mCoverLongestSide, mCoverLongestSide);
            } else {
                // hide it if not in use.
                mCoverView.setVisibility(View.GONE);
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                // add a text view to display the "position/rowId" for a book
                mDbgRowIdView = new TextView(context);
                mDbgRowIdView.setId(View.generateViewId());
                mDbgRowIdView.setTextColor(Color.BLUE);
                mDbgRowIdView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                final LinearLayout parentLayout = itemView.findViewById(R.id.icon_sidebar);
                mDbgRowIdView.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                parentLayout.addView(mDbgRowIdView);
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
                              @NonNull final ListStyle style) {
            // update the in-use flags with row-data available fields. Do this once only.
            if (!mInUse.isSet) {
                mInUse.set(rowData);
            }

            final String title;
            if (mReorderTitle) {
                final Context context = itemView.getContext();
                final String language = rowData.getString(DBKey.KEY_LANGUAGE);
                final Locale locale = Languages.toLocale(context, language);

                title = ReorderHelper.reorder(context, rowData.getString(DBKey.KEY_TITLE), locale);
            } else {
                title = rowData.getString(DBKey.KEY_TITLE);
            }
            mTitleView.setText(title);

            mReadIconView.setVisibility(rowData.getBoolean(DBKey.BOOL_READ) ? View.VISIBLE
                                                                            : View.GONE);

            if (mInUse.signed) {
                final boolean isSet = rowData.getBoolean(DBKey.BOOL_SIGNED);
                mSignedIconView.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }

            if (mInUse.edition) {
                final boolean isSet = (rowData.getLong(DBKey.BITMASK_EDITION)
                                       & Book.Edition.FIRST) != 0;
                mEditionIconView.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }

            if (mInUse.lending) {
                final boolean isSet = !rowData.getString(DBKey.KEY_LOANEE).isEmpty();
                mLendOutIconView.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }

            if (mInUse.cover) {
                setImageView(rowData.getString(DBKey.KEY_BOOK_UUID));
            }

            if (mInUse.series) {
                final String number = rowData.getString(DBKey.KEY_BOOK_NUM_IN_SERIES);
                if (number.isEmpty()) {
                    mSeriesNumView.setVisibility(View.GONE);
                    mSeriesNumLongView.setVisibility(View.GONE);
                } else {
                    // Display it in one of the views, based on the size of the text.
                    // 4 characters is based on e.g. "1.12" being considered short
                    // and e.g. "1|omnibus" being long.
                    if (number.length() > 4) {
                        mSeriesNumView.setVisibility(View.GONE);
                        mSeriesNumLongView.setText(number);
                        mSeriesNumLongView.setVisibility(View.VISIBLE);
                    } else {
                        mSeriesNumView.setText(number);
                        mSeriesNumView.setVisibility(View.VISIBLE);
                        mSeriesNumLongView.setVisibility(View.GONE);
                    }
                }
            }

            if (mInUse.rating) {
                final float rating = rowData.getFloat(DBKey.KEY_RATING);
                if (rating > 0) {
                    mRatingBar.setRating(rating);
                    mRatingBar.setVisibility(View.VISIBLE);
                } else {
                    mRatingBar.setVisibility(View.GONE);
                }
            }
            if (mInUse.author) {
                showOrHide(mAuthorView, rowData.getString(DBKey.KEY_AUTHOR_FORMATTED));
            }
            if (mInUse.publisher || mInUse.pubDate) {
                showOrHide(mPublisherView, getPublisherAndPubDateText(rowData));
            }
            if (mInUse.isbn) {
                showOrHide(mIsbnView, rowData.getString(DBKey.KEY_ISBN));
            }
            if (mInUse.format) {
                showOrHide(mFormatView, rowData.getString(DBKey.KEY_FORMAT));
            }
            if (mInUse.location) {
                showOrHide(mLocationView, rowData.getString(DBKey.KEY_LOCATION));
            }
            if (mInUse.bookshelf) {
                showOrHide(mBookshelvesView, rowData.getString(DBKey.KEY_BOOKSHELF_NAME_CSV));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                if (mDbgRowIdView != null) {
                    final String txt = String.valueOf(position) + '/'
                                       + rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                    mDbgRowIdView.setText(txt);
                }
            }
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final DataHolder rowData) {
            final String name;
            if (mInUse.publisher) {
                name = rowData.getString(DBKey.KEY_PUBLISHER_NAME);
            } else {
                name = null;
            }

            final String date;
            if (mInUse.pubDate) {
                final String dateStr = rowData.getString(DBKey.DATE_BOOK_PUBLICATION);
                date = new PartialDate(dateStr).toDisplay(mAdapter.getUserLocale(), dateStr);
            } else {
                date = null;
            }

            if (name != null && !name.isEmpty() && date != null && !date.isEmpty()) {
                // Combine Publisher and date
                return String.format(mX_bracket_Y_bracket, name, date);

            } else if (name != null && !name.isEmpty()) {
                // there was no date, just use the publisher
                return name;

            } else if (date != null && !date.isEmpty()) {
                // there was no publisher, just use the date
                return date;

            } else {
                // Neither is present
                return null;
            }
        }

        /**
         * Conditionally display 'text'.
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
            mCoverView.setTag(R.id.TAG_THUMBNAIL_UUID, uuid);

            final Context context = mCoverView.getContext();

            // 1. If caching is used, and we don't have cache building happening, check it.
            if (mAdapter.isImageCachingEnabled()) {
                final CoverCacheDao coverCacheDao = ServiceLocator.getInstance().getCoverCacheDao();
                if (!coverCacheDao.isBusy()) {
                    final Bitmap bitmap = coverCacheDao
                            .getCover(context, uuid, 0, mCoverLongestSide, mCoverLongestSide);

                    if (bitmap != null) {
                        //noinspection ConstantConditions
                        mImageLoader.fromBitmap(mCoverView, bitmap);
                        return;
                    }
                }
            }

            // 2. Cache did not have it, or we were not allowed to check.
            final Optional<File> file = Book.getPersistedCoverFile(uuid, 0);
            // Check if the file exists; if it does not...
            //noinspection SimplifyOptionalCallChains
            if (!file.isPresent()) {
                // leave the space blank, but preserve the width BASED on the mMaxHeight!
                final ViewGroup.LayoutParams lp = mCoverView.getLayoutParams();
                lp.width = (int) (mCoverLongestSide * HW_RATIO);
                lp.height = 0;
                mCoverView.setLayoutParams(lp);
                mCoverView.setImageDrawable(null);
                return;
            }

            // Once we get here, we know the file is valid
            if (mAdapter.isImageCachingEnabled()) {
                // 1. Gets the image from the file system and display it.
                // 2. Start a subsequent task to send it to the cache.
                //noinspection ConstantConditions
                mImageLoader.fromFile(mCoverView, file.get(), bitmap -> {
                    if (bitmap != null) {
                        ServiceLocator.getInstance().getCoverCacheDao().saveCover(
                                uuid, 0, bitmap, mCoverLongestSide, mCoverLongestSide);
                    }
                });
            } else {
                // Cache not used: Get the image from the file system and display it.
                //noinspection ConstantConditions
                mImageLoader.fromFile(mCoverView, file.get(), null);
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
        private final String mKey;
        @NonNull
        private final RatingBar mRatingBar;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param group    the group this holder represents
         */
        RatingHolder(@NonNull final View itemView,
                     @NonNull final BooklistGroup group) {
            super(itemView);
            mKey = group.getDisplayDomainExpression().getDomain().getName();
            mRatingBar = itemView.findViewById(R.id.rating);
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final ListStyle style) {
            mRatingBar.setRating(rowData.getInt(mKey));
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
        private static final int mTextViewId = R.id.name;

        /**
         * The group this holder represents.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @BooklistGroup.Id
        final int mGroupKeyId;
        /*** View to populate. */
        @NonNull
        final TextView mTextView;
        /** The parent adapter. */
        @NonNull
        final BooklistAdapter mAdapter;
        /**
         * Key of the related data column.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @NonNull
        private final String mKey;

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
            mAdapter = adapter;
            mGroupKeyId = group.getId();
            mKey = group.getDisplayDomainExpression().getDomain().getName();
            mTextView = itemView.findViewById(mTextViewId);
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final ListStyle style) {
            mTextView.setText(format(rowData.getString(mKey)));

            // Debugger help: color the row according to state
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                final int rowId = rowData.getInt(DBKey.PK_ID);
                final BooklistCursor cursor = (BooklistCursor) mAdapter.getCursor();
                itemView.setBackgroundColor(cursor.getDbgRowColor(rowId));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
                final String dbgText = " " + position + '/'
                                       + rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);

                final CharSequence text = mTextView.getText();
                final SpannableString dbg = new SpannableString(text + dbgText);
                dbg.setSpan(new ForegroundColorSpan(Color.BLUE), text.length(), dbg.length(), 0);
                dbg.setSpan(new RelativeSizeSpan(0.7f), text.length(), dbg.length(), 0);

                mTextView.setText(dbg);
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
            return mAdapter.format(itemView.getContext(), mGroupKeyId, text, null);
        }
    }

    /**
     * ViewHolder for a row that displays a generic string with a checkable icon at the 'end'.
     */
    static class CheckableStringHolder
            extends GenericStringHolder {

        @NonNull
        final ImageView mIsCompleteView;
        /** Column name of related boolean column. */
        private String mIsCompleteKey;

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

            mIsCompleteView = itemView.findViewById(R.id.cbx_is_complete);
        }

        /**
         * @param columnKey Column name to use for the 'isComplete' status
         */
        void setIsCompleteColumnKey(@NonNull final String columnKey) {
            mIsCompleteKey = columnKey;
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final ListStyle style) {
            super.onBindViewHolder(position, rowData, style);
            mIsCompleteView.setVisibility(rowData.getBoolean(
                    mIsCompleteKey) ? View.VISIBLE : View.GONE);
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
            setIsCompleteColumnKey(DBKey.BOOL_AUTHOR_IS_COMPLETE);
        }
    }

    /**
     * ViewHolder for a Series.
     */
    static class SeriesHolder
            extends CheckableStringHolder {

        /** Stores this value in between the #onBindViewHolder and the #format methods. */
        private String mBookLanguage;

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
            setIsCompleteColumnKey(DBKey.BOOL_SERIES_IS_COMPLETE);
        }

        @Override
        void onBindViewHolder(final int position,
                              @NonNull final DataHolder rowData,
                              @NonNull final ListStyle style) {
            // grab the book language first for use in #format
            mBookLanguage = rowData.getString(DBKey.KEY_LANGUAGE);

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
                                                    .getLocale(context, mBookLanguage);
            return mAdapter.format(context, mGroupKeyId, text, bookLocale);
        }
    }
}

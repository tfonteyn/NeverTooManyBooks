/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageScale;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithTitle;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.fastscroller.FastScroller;

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
    /** Cached locale. */
    @NonNull
    private final Locale mUserLocale;
    /** The cursor is the equivalent of the 'list of items'. */
    @NonNull
    private final Cursor mCursor;
    /** The padding indent (in pixels) added for each level: padding = (level-1) * mLevelIndent. */
    private final int mLevelIndent;
    /** Cached inflater. */
    @NonNull
    private final LayoutInflater mInflater;
    /** List style to apply. */
    @NonNull
    private final BooklistStyle mStyle;
    /** provides read only access to the row data. */
    @NonNull
    private final DataHolder mNodeData;
    /** A collection of 'in-use' flags for the fields we might display. */
    @NonNull
    private final FieldsInUse mFieldsInUse;
    /** The combined click and long-click listeners for a single row. */
    @Nullable
    private OnRowClickedListener mOnRowClickedListener;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   The style is used by (some) individual rows.
     * @param cursor  cursor with the 'list of items'.
     */
    public BooklistAdapter(@NonNull final Context context,
                           @NonNull final BooklistStyle style,
                           @NonNull final Cursor cursor) {
        mInflater = LayoutInflater.from(context);
        mUserLocale = LocaleUtils.getUserLocale(context);
        mStyle = style;
        mCursor = cursor;
        mNodeData = new CursorRow(mCursor);
        mLevelIndent = context.getResources().getDimensionPixelSize(R.dimen.bob_level_indent);

        mFieldsInUse = new FieldsInUse(context, style);

        setHasStableIds(true);
    }

    @NonNull
    public Locale getUserLocale() {
        return mUserLocale;
    }

    @NonNull
    public Cursor getCursor() {
        return mCursor;
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
     * or original string when no special format was needed or on any failure
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    String format(@NonNull final Context context,
                  @BooklistGroup.Id final int groupKeyId,
                  @Nullable final String text,
                  @Nullable final Locale locale) {
        switch (groupKeyId) {
            case BooklistGroup.AUTHOR: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.hint_empty_author);
                } else {
                    return text;
                }
            }
            case BooklistGroup.SERIES: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.hint_empty_series);

                } else if (ItemWithTitle.isReorderTitleForDisplaying(context)) {
                    Locale tmpLocale;
                    if (locale != null) {
                        tmpLocale = locale;
                    } else {
                        tmpLocale = mUserLocale;
                    }
                    return ItemWithTitle.reorder(context, text, tmpLocale);
                } else {
                    return text;
                }
            }
            case BooklistGroup.PUBLISHER: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.hint_empty_publisher);

                } else if (ItemWithTitle.isReorderTitleForDisplaying(context)) {
                    Locale tmpLocale;
                    if (locale != null) {
                        tmpLocale = locale;
                    } else {
                        tmpLocale = mUserLocale;
                    }
                    return ItemWithTitle.reorder(context, text, tmpLocale);
                } else {
                    return text;
                }
            }
            case BooklistGroup.READ_STATUS: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.hint_empty_read_status);
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
                    return context.getString(R.string.hint_empty_language);
                } else {
                    return LanguageUtils.getDisplayNameFromISO3(context, text);
                }
            }
            case BooklistGroup.CONDITION: {
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.unknown);
                } else {
                    try {
                        int i = Integer.parseInt(text);
                        return context.getResources().getStringArray(R.array.conditions_book)[i];
                    } catch (@NonNull final NumberFormatException
                            | IndexOutOfBoundsException ignore) {
                        return context.getString(R.string.unknown);
                    }
                }
            }
            case BooklistGroup.RATING: {
                // This is the text based formatting, as used by the level/scroller text.
                if (text == null || text.isEmpty()) {
                    return context.getString(R.string.hint_empty_rating);
                } else {
                    try {
                        // Locale independent.
                        int i = Integer.parseInt(text);
                        // If valid, get the name
                        if (i >= 0 && i <= Book.RATING_STARS) {
                            return context.getResources()
                                          .getQuantityString(R.plurals.n_stars, i, i);
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        Logger.error(context, TAG, e);
                    }
                    return text;
                }
            }
            case BooklistGroup.ON_LOAN: {
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
                    return context.getString(R.string.hint_empty_year);
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
                    return context.getString(R.string.hint_empty_month);
                } else {
                    try {
                        int m = Integer.parseInt(text);
                        // If valid, get the short name
                        if (m > 0 && m <= 12) {
                            Locale tmpLocale;
                            if (locale != null) {
                                tmpLocale = locale;
                            } else {
                                tmpLocale = mUserLocale;
                            }
                            return Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, tmpLocale);
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
                    return context.getString(R.string.hint_empty_day);
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
                    return context.getString(R.string.hint_empty_field);
                } else {
                    return text;
                }
            }
        }
    }

    public void setOnRowClickedListener(@Nullable final OnRowClickedListener onRowClickedListener) {
        mOnRowClickedListener = onRowClickedListener;
    }

    @SuppressLint("SwitchIntDef")
    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            @BooklistGroup.Id final int groupKeyId) {

        final View itemView = createView(parent, groupKeyId);

        // NEWTHINGS: GROUP_KEY_x add a new holder type if needed
        switch (groupKeyId) {
            case BooklistGroup.BOOK:
                return new BookHolder(itemView, mUserLocale, mStyle, mFieldsInUse);

            case BooklistGroup.AUTHOR:
                return new CheckableStringHolder(this, itemView, mStyle.getGroupById(groupKeyId),
                                                 DBDefinitions.KEY_AUTHOR_IS_COMPLETE);

            case BooklistGroup.SERIES:
                return new SeriesHolder(this, itemView, mStyle.getGroupById(groupKeyId));

            case BooklistGroup.RATING:
                return new RatingHolder(itemView, mStyle.getGroupById(groupKeyId));

            default:
                return new GenericStringHolder(this, itemView, mStyle.getGroupById(groupKeyId));
        }
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
    @SuppressLint("SwitchIntDef")
    private View createView(@NonNull final ViewGroup parent,
                            @BooklistGroup.Id final int groupKeyId) {

        final Context context = parent.getContext();

        final int level = mNodeData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
        // Indent (0..) based on level (1..)
        int indent = level - 1;

        @LayoutRes
        final int layoutId;

        switch (groupKeyId) {
            case BooklistGroup.BOOK: {
                @ImageScale.Scale
                final int scale = mStyle.getThumbnailScale(context);
                switch (scale) {
                    case ImageScale.SCALE_2X_LARGE:
                        layoutId = R.layout.booksonbookshelf_row_book_2x_large_image;
                        break;

                    case ImageScale.SCALE_X_LARGE:
                        layoutId = R.layout.booksonbookshelf_row_book_1x_large_image;
                        break;


                    case ImageScale.SCALE_LARGE:
                    case ImageScale.SCALE_MEDIUM:
                    case ImageScale.SCALE_SMALL:
                    case ImageScale.SCALE_X_SMALL:
                    case ImageScale.SCALE_NOT_DISPLAYED:
                    default:
                        layoutId = R.layout.booksonbookshelf_row_book;
                        break;
                }

                // Don't indent books
                indent = 0;
                break;
            }
            case BooklistGroup.RATING: {
                layoutId = R.layout.booksonbookshelf_group_rating;
                break;
            }
            default: {
                // for all other types, the level determines the view
                switch (level) {
                    case 1:
                        layoutId = R.layout.booksonbookshelf_group_level_1;
                        break;
                    case 2:
                        layoutId = R.layout.booksonbookshelf_group_level_2;
                        break;

                    default:
                        // level 3 and higher all use the same layout.
                        layoutId = R.layout.booksonbookshelf_group_level_3;
                        break;
                }
                break;
            }
        }

        final View view = mInflater.inflate(layoutId, parent, false);
        view.setPaddingRelative(indent * mLevelIndent, 0, 0, 0);

        // Scale text/padding if required
        if (mStyle.getTextScale(context) != BooklistStyle.FONT_SCALE_MEDIUM) {
            scaleTextViews(view, mStyle.getTextSpUnits(context),
                           mStyle.getTextPaddingFactor(context));
        }
        return view;
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {

        mCursor.moveToPosition(position);

        holder.onClickTargetView.setTag(R.id.TAG_BL_POSITION, position);

        holder.onClickTargetView.setOnClickListener(v -> {
            if (mOnRowClickedListener != null) {
                final Integer rowPos = (Integer) v.getTag(R.id.TAG_BL_POSITION);
                Objects.requireNonNull(rowPos, ErrorMsg.NULL_ROW_POS);
                mOnRowClickedListener.onItemClick(rowPos);
            }
        });

        holder.onClickTargetView.setOnLongClickListener(v -> {
            if (mOnRowClickedListener != null) {
                final Integer rowPos = (Integer) v.getTag(R.id.TAG_BL_POSITION);
                Objects.requireNonNull(rowPos, ErrorMsg.NULL_ROW_POS);
                return mOnRowClickedListener.onItemLongClick(rowPos);
            }
            return false;
        });

        // further binding depends on the type of row (i.e. holder).
        holder.onBindViewHolder(mNodeData, mStyle);
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
        if (mCursor.moveToPosition(position)) {
            return mNodeData.getInt(DBDefinitions.KEY_BL_NODE_GROUP);
        } else {
            // bogus, should not happen
            return BooklistGroup.BOOK;
        }
    }

    @Override
    public int getItemCount() {
        return mCursor.getCount();
    }

    @Override
    public long getItemId(final int position) {
        if (hasStableIds() && mCursor.moveToPosition(position)) {
            return mNodeData.getLong(DBDefinitions.KEY_PK_ID);
        } else {
            return RecyclerView.NO_ID;
        }
    }

    /**
     * Get the level for the given position.
     *
     * @return the level, or {@code 0} if unknown
     */
    int getLevel(final int position) {
        if (mCursor.moveToPosition(position)) {
            return mNodeData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
        } else {
            return 0;
        }
    }

    /**
     * Scale text in a View (and children) as per user preferences.
     * <p>
     * Note that ImageView experiments from the original code never worked.
     * Bottom line is that Android will scale *down* (i.e. image to big ? make it smaller)
     * but will NOT scale up to fill the provided space. This means scaling needs to be done
     * at bind time (as we need <strong>actual</strong> size of the image), not at create time
     * of the view.
     * <br>So this method only deals with TextView instances.
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
    @Nullable
    @Override
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
            if (level > (mStyle.getGroupCount() + 1)) {
                throw new IllegalArgumentException(
                        "level=" + level + "> (getGroupCount+1)=" + mStyle.getGroupCount() + 1);
            }
        }

        // make sure it's still in range.
        final int clampedPosition = MathUtils.clamp(position, 0, getItemCount() - 1);
        if (!mCursor.moveToPosition(clampedPosition)) {
            return null;
        }

        try {
            if (level > (mStyle.getGroupCount())) {
                // it's a book; use the title (no need to take the group.format round-trip).
                return mNodeData.getString(DBDefinitions.KEY_TITLE);

            } else {
                // it's a group; use the display domain as the text
                final BooklistGroup group = mStyle.getGroupByLevel(level);
                final String value = mNodeData.getString(group.getDisplayDomain().getName());
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
         * @param position The position of the item within the adapter's data set.
         *
         * @return true if the callback consumed the long click, false otherwise.
         */
        default boolean onItemLongClick(final int position) {
            return false;
        }
    }

    /**
     * Value class, initialized by the adapter, updated when the first rowData is fetched.
     * Reused by the holders.
     */
    private static class FieldsInUse {

        /** Book level. */
        private boolean read;
        /** Book level. */
        private boolean signed;
        /** Book level. */
        private boolean edition;
        /** Book level. */
        private boolean lending;
        /** Book level. */
        private boolean series;
        /** Book row details - Based on style. */
        private boolean cover;

        /** Book row details - Based on style. */
        private boolean rating;
        /** Book row details - Based on style. */
        private boolean author;
        /** Book row details - Based on style. */
        private boolean publisher;
        /** Book row details - Based on style. */
        private boolean pubDate;
        /** Book row details - Based on style. */
        private boolean isbn;
        /** Book row details - Based on style. */
        private boolean format;
        /** Book row details - Based on style. */
        private boolean location;
        /** Book row details - Based on style. */
        private boolean bookshelf;

        /** Set to true after {@link #set} is called. */
        private boolean isSet;

        /**
         * Constructor. Initialized by the adapter.
         *
         * @param context Current context
         * @param style   Current style
         */
        FieldsInUse(@NonNull final Context context,
                    @NonNull final BooklistStyle style) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            read = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_READ);
            signed = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SIGNED);
            edition = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_EDITION_BITMASK);
            lending = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE);
            series = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SERIES_TITLE);

            cover = style.isBookDetailUsed(context, prefs, DBDefinitions.PREFS_IS_USED_THUMBNAIL);

            rating = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_RATING);
            author = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_AUTHOR_FORMATTED);
            publisher = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_PUBLISHER_NAME);
            pubDate = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_DATE_PUBLISHED);
            isbn = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_ISBN);
            format = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_FORMAT);
            location = style.isBookDetailUsed(context, prefs, DBDefinitions.KEY_LOCATION);
            bookshelf = style.isBookDetailUsed(context, prefs,
                                               DBDefinitions.KEY_BOOKSHELF_NAME_CSV);
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

            read = read && rowData.contains(DBDefinitions.KEY_READ);
            signed = signed && rowData.contains(DBDefinitions.KEY_SIGNED);
            edition = edition && rowData.contains(DBDefinitions.KEY_EDITION_BITMASK);
            lending = lending && rowData.contains(DBDefinitions.KEY_LOANEE_AS_BOOLEAN);
            cover = cover && rowData.contains(DBDefinitions.KEY_BOOK_UUID);
            series = series && rowData.contains(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            bookshelf = bookshelf && rowData.contains(DBDefinitions.KEY_BOOKSHELF_NAME_CSV);
            author = author && rowData.contains(DBDefinitions.KEY_AUTHOR_FORMATTED);
            isbn = isbn && rowData.contains(DBDefinitions.KEY_ISBN);
            format = format && rowData.contains(DBDefinitions.KEY_FORMAT);
            location = location && rowData.contains(DBDefinitions.KEY_LOCATION);
            rating = rating && rowData.contains(DBDefinitions.KEY_RATING);
            publisher = publisher && rowData.contains(DBDefinitions.KEY_PUBLISHER_NAME);
            pubDate = pubDate && rowData.contains(DBDefinitions.KEY_DATE_PUBLISHED);
        }
    }

    /**
     * Base for all {@link BooklistGroup} ViewHolder classes.
     */
    abstract static class RowViewHolder
            extends RecyclerView.ViewHolder {

        /**
         * The view to install on-click listeners on. Can be the same as the itemView.
         * This is also the view where we will add tags with rowId etc,
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
            onClickTargetView = itemView.findViewById(R.id.ROW_ONCLICK_TARGET);
            if (onClickTargetView == null) {
                onClickTargetView = itemView;
            }
        }

        /**
         * Bind the data to the views in the holder.
         *
         * @param rowData with data to bind
         * @param style   to use
         */
        abstract void onBindViewHolder(@NonNull DataHolder rowData,
                                       @NonNull BooklistStyle style);
    }

    /**
     * ViewHolder for a {@link BooklistGroup#BOOK} row.
     */
    static class BookHolder
            extends RowViewHolder {

        /** Format string. */
        @NonNull
        private final String mX_bracket_Y_bracket;

        /** Extras - Based on style. */
        private final int mMaxCoverSize;

        /** View that stores the related book field. */
        private final TextView mTitleView;

        /** The "I've read it" checkbox. */
        private final CompoundButton mReadView;
        /** The "signed" checkbox. */
        private final CompoundButton mSignedView;
        /** The "1th edition" checkbox. */
        private final CompoundButton mEditionView;
        /** The "on loan" checkbox. */
        private final CompoundButton mOnLoanView;

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

        /** Cache the locale. */
        @NonNull
        private final Locale mLocale;

        /** Whether to re-order the title as per global preference. */
        private final boolean mReorderTitle;

        /** A collection of 'in-use' flags for the fields we might display. */
        private final FieldsInUse mInUse;

        /**
         * Constructor.
         *
         * <strong>Note:</strong> the itemView can be re-used.
         * Hence make sure to explicitly set visibility.
         *
         * @param itemView    the view specific for this holder
         * @param userLocale  to use
         * @param style       to use
         * @param fieldsInUse which fields are used
         */
        BookHolder(@NonNull final View itemView,
                   @NonNull final Locale userLocale,
                   @NonNull final BooklistStyle style,
                   @NonNull final FieldsInUse fieldsInUse) {
            super(itemView);

            // disabled (for now?) as it makes less sense in this particular view/holder,
            // and slows down scrolling.
            // mReorderTitle = ItemWithTitle.isReorderTitleForDisplaying(itemView.getContext());
            mReorderTitle = false;

            mInUse = fieldsInUse;
            final Context context = itemView.getContext();
            mLocale = userLocale;

            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);

            // always visible
            mTitleView = itemView.findViewById(R.id.title);

            // hidden by default
            mReadView = itemView.findViewById(R.id.cbx_read);
            mSignedView = itemView.findViewById(R.id.cbx_signed);
            mEditionView = itemView.findViewById(R.id.cbx_first_edition);
            mOnLoanView = itemView.findViewById(R.id.cbx_on_loan);
            mSeriesNumView = itemView.findViewById(R.id.series_num);
            mSeriesNumLongView = itemView.findViewById(R.id.series_num_long);

            mRatingBar = itemView.findViewById(R.id.rating);
            mAuthorView = itemView.findViewById(R.id.author);
            mPublisherView = itemView.findViewById(R.id.publisher);
            mIsbnView = itemView.findViewById(R.id.isbn);
            mFormatView = itemView.findViewById(R.id.format);
            mLocationView = itemView.findViewById(R.id.location);
            mBookshelvesView = itemView.findViewById(R.id.shelves);

            // We use a square space for the image so both portrait/landscape images work out.
            mMaxCoverSize = ImageScale.getSize(context, style.getThumbnailScale(context));
            mCoverView = itemView.findViewById(R.id.coverImage0);
            if (!mInUse.cover) {
                // shown by default, so hide it if not in use.
                mCoverView.setVisibility(View.GONE);
            }
        }

        @Override
        void onBindViewHolder(@NonNull final DataHolder rowData,
                              @NonNull final BooklistStyle style) {
            // update the in-use flags with row-data available fields. Do this once only.
            if (!mInUse.isSet) {
                mInUse.set(rowData);
            }

            final String title;
            if (mReorderTitle) {
                String bookLanguage = rowData.getString(DBDefinitions.KEY_LANGUAGE);
                if (!bookLanguage.isEmpty()) {
                    title = ItemWithTitle.reorder(itemView.getContext(),
                                                  rowData.getString(DBDefinitions.KEY_TITLE),
                                                  bookLanguage);
                } else {
                    title = ItemWithTitle.reorder(itemView.getContext(),
                                                  rowData.getString(DBDefinitions.KEY_TITLE),
                                                  mLocale);
                }
            } else {
                title = rowData.getString(DBDefinitions.KEY_TITLE);
            }
            mTitleView.setText(title);

            if (mInUse.read) {
                final boolean isSet = rowData.getBoolean(DBDefinitions.KEY_READ);
                mReadView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mReadView.setChecked(isSet);
            }

            if (mInUse.signed) {
                final boolean isSet = rowData.getBoolean(DBDefinitions.KEY_SIGNED);
                mSignedView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mSignedView.setChecked(isSet);
            }

            if (mInUse.edition) {
                final boolean isSet = (rowData.getInt(DBDefinitions.KEY_EDITION_BITMASK)
                                       & Book.Edition.FIRST) != 0;
                mEditionView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mEditionView.setChecked(isSet);
            }

            if (mInUse.lending) {
                final boolean isSet = !rowData.getBoolean(DBDefinitions.KEY_LOANEE_AS_BOOLEAN);
                mOnLoanView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mOnLoanView.setChecked(isSet);
            }

            if (mInUse.cover) {
                final String uuid = rowData.getString(DBDefinitions.KEY_BOOK_UUID);
                // store the uuid for use in the OnClickListener
                mCoverView.setTag(R.id.TAG_ITEM, uuid);
                final boolean isSet = ImageUtils.setImageView(mCoverView, mMaxCoverSize,
                                                              mMaxCoverSize, uuid, 0);
                if (isSet) {
                    // We do not go overkill here by adding a CoverHandler
                    // but only provide zooming by clicking on the image
                    mCoverView.setOnClickListener(v -> {
                        final FragmentActivity activity = (FragmentActivity) v.getContext();
                        final String currentUuid = (String) v.getTag(R.id.TAG_ITEM);
                        final File image = AppDir.getCoverFile(activity, currentUuid, 0);
                        if (image.exists()) {
                            ZoomedImageDialogFragment
                                    .newInstance(image)
                                    .show(activity.getSupportFragmentManager(),
                                          ZoomedImageDialogFragment.TAG);
                        }
                    });
                }
            }

            if (mInUse.series) {
                final String number = rowData.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
                if (!number.isEmpty()) {
                    // Display it in one of the views, based on the size of the text.
                    if (number.length() > 4) {
                        mSeriesNumView.setVisibility(View.GONE);
                        mSeriesNumLongView.setText(number);
                        mSeriesNumLongView.setVisibility(View.VISIBLE);
                    } else {
                        mSeriesNumView.setText(number);
                        mSeriesNumView.setVisibility(View.VISIBLE);
                        mSeriesNumLongView.setVisibility(View.GONE);
                    }
                } else {
                    mSeriesNumView.setVisibility(View.GONE);
                    mSeriesNumLongView.setVisibility(View.GONE);
                }
            }

            if (mInUse.rating) {
                final int rating = rowData.getInt(DBDefinitions.KEY_RATING);
                if (rating != 0) {
                    mRatingBar.setRating(rating);
                    mRatingBar.setVisibility(View.VISIBLE);
                } else {
                    mRatingBar.setVisibility(View.GONE);
                }
            }
            if (mInUse.author) {
                showOrHide(mAuthorView, rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
            }
            if (mInUse.publisher || mInUse.pubDate) {
                showOrHide(mPublisherView, getPublisherAndPubDateText(rowData));
            }
            if (mInUse.isbn) {
                showOrHide(mIsbnView, rowData.getString(DBDefinitions.KEY_ISBN));
            }
            if (mInUse.format) {
                showOrHide(mFormatView, rowData.getString(DBDefinitions.KEY_FORMAT));
            }
            if (mInUse.location) {
                showOrHide(mLocationView, rowData.getString(DBDefinitions.KEY_LOCATION));
            }
            if (mInUse.bookshelf) {
                showOrHide(mBookshelvesView,
                           rowData.getString(DBDefinitions.KEY_BOOKSHELF_NAME_CSV));
            }
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final DataHolder rowData) {
            final String publicationDate;
            if (mInUse.pubDate) {
                publicationDate = LocaleUtils.toPrettyDate(
                        itemView.getContext(), rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
            } else {
                publicationDate = null;
            }

            final String publisherName;
            if (mInUse.publisher) {
                publisherName = rowData.getString(DBDefinitions.KEY_PUBLISHER_NAME);
            } else {
                publisherName = null;
            }

            if (publisherName != null && !publisherName.isEmpty()
                && publicationDate != null && !publicationDate.isEmpty()) {
                // Combine Publisher and date
                return String.format(mX_bracket_Y_bracket, publisherName, publicationDate);
            } else if (publisherName != null && !publisherName.isEmpty()) {
                // there was no date, just use the publisher
                return publisherName;

            } else if (publicationDate != null && !publicationDate.isEmpty()) {
                // there was no publisher, just use the date
                return publicationDate;

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
    }

    static class RatingHolder
            extends RowViewHolder {

        @NonNull
        final String mKey;
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
            mKey = group.getDisplayDomain().getName();
            mRatingBar = itemView.findViewById(R.id.rating);
        }

        @Override
        void onBindViewHolder(@NonNull final DataHolder rowData,
                              @NonNull final BooklistStyle style) {
            final int rating = rowData.getInt(mKey);
            mRatingBar.setRating(rating);
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
        static final int mTextViewId = R.id.name;
        /**
         * The group this holder represents.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @BooklistGroup.Id
        final int mGroupKeyId;
        /**
         * Key of the related data column.
         * It's ok to store this as it's intrinsically linked with the ViewType.
         */
        @NonNull
        final String mKey;
        /*** View to populate. */
        @NonNull
        final TextView mTextView;
        /** The parent adapter. */
        @NonNull
        final BooklistAdapter mAdapter;

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
            mKey = group.getDisplayDomain().getName();
            mTextView = itemView.findViewById(mTextViewId);
        }

        @Override
        void onBindViewHolder(@NonNull final DataHolder rowData,
                              @NonNull final BooklistStyle style) {
            mTextView.setText(format(rowData.getString(mKey)));

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                final int rowId = rowData.getInt(DBDefinitions.KEY_PK_ID);
                final RowStateDAO.Node node = ((BooklistCursor) mAdapter.getCursor())
                        .getBooklistBuilder().getNodeByNodeId(rowId);
                if (node.isExpanded) {
                    itemView.setBackgroundColor(Color.GREEN);
                } else {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        }

        /**
         * For a simple row, use the default group formatter to format it.
         *
         * @param text String to display; can be {@code null} or empty
         *
         * @return the formatted text
         */
        public String format(@Nullable final String text) {
            return mAdapter.format(itemView.getContext(), mGroupKeyId, text, null);
        }
    }

    /**
     * ViewHolder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    static class CheckableStringHolder
            extends GenericStringHolder {

        /** Column name of related boolean column. */
        private final String mCheckableColumnKey;
        /** The 'lock' aka 'is complete' icon drawable. */
        @NonNull
        private final Drawable mLock;

        /**
         * Constructor.
         *
         * @param adapter            the hosting adapter
         * @param itemView           the view specific for this holder
         * @param group              the group this holder represents
         * @param checkableColumnKey Column name to use for the boolean 'lock' status
         */
        CheckableStringHolder(@NonNull final BooklistAdapter adapter,
                              @NonNull final View itemView,
                              @NonNull final BooklistGroup group,
                              @NonNull final String checkableColumnKey) {
            super(adapter, itemView, group);
            mCheckableColumnKey = checkableColumnKey;
            //noinspection ConstantConditions
            mLock = itemView.getContext().getDrawable(R.drawable.ic_lock);
            //noinspection ConstantConditions
            mLock.setBounds(0, 0, mLock.getIntrinsicWidth(), mLock.getIntrinsicHeight());
        }

        @Override
        void onBindViewHolder(@NonNull final DataHolder rowData,
                              @NonNull final BooklistStyle style) {
            // do the text part first
            super.onBindViewHolder(rowData, style);

            final Drawable lock = rowData.getBoolean(mCheckableColumnKey) ? mLock : null;
            final Drawable[] drawables = mTextView.getCompoundDrawablesRelative();
            mTextView.setCompoundDrawablesRelative(drawables[0], drawables[1], lock, drawables[3]);
        }
    }

    /**
     * ViewHolder for a Series.
     */
    static class SeriesHolder
            extends CheckableStringHolder {

        /** Stores this value in between the #onBindViewHolder and the #setText methods. */
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
            super(adapter, itemView, group, DBDefinitions.KEY_SERIES_IS_COMPLETE);
        }

        @Override
        void onBindViewHolder(@NonNull final DataHolder rowData,
                              @NonNull final BooklistStyle style) {
            // grab the book language first
            mBookLanguage = rowData.getString(DBDefinitions.KEY_LANGUAGE);

            super.onBindViewHolder(rowData, style);
        }

        @Override
        public String format(@Nullable final String text) {
            final Context context = itemView.getContext();
            // FIXME: translated series are reordered in the book's language
            // It should be done using the Series language
            // but as long as we don't store the Series language there is no point
            Locale bookLocale = LocaleUtils.getLocale(context, mBookLanguage);
            if (bookLocale == null) {
                bookLocale = mAdapter.getUserLocale();
            }
            return mAdapter.format(context, mGroupKeyId, text, bookLocale);
        }
    }
}

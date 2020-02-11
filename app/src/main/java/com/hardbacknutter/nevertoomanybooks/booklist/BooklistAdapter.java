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

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.math.MathUtils;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup.RowKind;
import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
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

    /** The padding indent (in pixels) added for each level: padding = (level-1) * mLevelIndent. */
    private final int mLevelIndent;
    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final BooklistStyle mStyle;
    /** The cursor is the equivalent of the 'list of items'. */
    @NonNull
    private final Cursor mCursor;
    /** provides read only access to the row data. */
    @NonNull
    private final RowDataHolder mRowData;

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
        mStyle = style;
        mCursor = cursor;
        mRowData = new CursorRow(mCursor);
        mLevelIndent = context.getResources().getDimensionPixelSize(R.dimen.booklist_level_indent);

        setHasStableIds(true);
    }

    public void setOnRowClickedListener(@Nullable final OnRowClickedListener onRowClickedListener) {
        mOnRowClickedListener = onRowClickedListener;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            @RowKind.Id final int viewType) {

        View itemView = createView(parent, viewType);

        // a BookHolder is based on multiple columns, the holder itself will sort them out.
        if (viewType == RowKind.BOOK) {
            return new BookHolder(itemView, mStyle);
        }

        String columnName = RowKind.get(viewType).getFormattedDomain().getName();

        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (mCursor.getColumnIndex(columnName) < 0) {
                throw new ColumnNotPresentException(columnName);
            }
        }

        switch (viewType) {
            // NEWTHINGS: ROW_KIND_x

            case RowKind.AUTHOR:
                return new AuthorHolder(itemView, columnName);

            case RowKind.SERIES:
                return new SeriesHolder(itemView, columnName);

            // Months are displayed by name
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUB_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(itemView, columnName);

            // some special formatting holders
            case RowKind.RATING:
                return new RatingHolder(itemView, columnName);
            case RowKind.LANGUAGE:
                return new LanguageHolder(itemView, columnName);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(itemView, columnName);

            //noinspection ConstantConditions
            case RowKind.BOOK:
                throw new IllegalStateException();

            // plain old Strings
            case RowKind.BOOKSHELF:
            case RowKind.DATE_ACQUIRED_DAY:
            case RowKind.DATE_ACQUIRED_YEAR:
            case RowKind.DATE_ADDED_DAY:
            case RowKind.DATE_ADDED_YEAR:
            case RowKind.DATE_FIRST_PUB_YEAR:
            case RowKind.DATE_LAST_UPDATE_DAY:
            case RowKind.DATE_LAST_UPDATE_YEAR:
            case RowKind.DATE_PUBLISHED_YEAR:
            case RowKind.DATE_READ_DAY:
            case RowKind.DATE_READ_YEAR:
            case RowKind.FORMAT:
            case RowKind.COLOR:
            case RowKind.GENRE:
            case RowKind.LOANED:
            case RowKind.LOCATION:
            case RowKind.PUBLISHER:
            case RowKind.TITLE_LETTER:
            case RowKind.SERIES_TITLE_LETTER:
            default:
                return new GenericStringHolder(itemView, columnName,
                                               R.string.hint_empty_field);
        }
    }

    private View createView(@NonNull final ViewGroup parent,
                            @RowKind.Id final int viewType) {

        int level = mRowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
        // Indent (0..) based on level (1..)
        int indent = level - 1;

        @LayoutRes
        int layoutId;

        // A Book occurs always at the lowest level regardless of the groups in the style.
        if (viewType == RowKind.BOOK) {
            @ImageUtils.Scale
            int scale = mStyle.getThumbnailScale();
            switch (scale) {
                case ImageUtils.SCALE_2X_LARGE:
                    layoutId = R.layout.booksonbookshelf_row_book_2x_large_image;
                    break;

                case ImageUtils.SCALE_X_LARGE:
                    layoutId = R.layout.booksonbookshelf_row_book_1x_large_image;
                    break;


                case ImageUtils.SCALE_LARGE:
                case ImageUtils.SCALE_MEDIUM:
                case ImageUtils.SCALE_SMALL:
                case ImageUtils.SCALE_X_SMALL:
                case ImageUtils.SCALE_NOT_DISPLAYED:
                default:
                    layoutId = R.layout.booksonbookshelf_row_book;
                    break;
            }

            // "out-dent" books. Looks better.
            if (indent > 0) {
                --indent;
            }

        } else {
            // for all other types, the level determines the view
            switch (level) {
                case 1:
                    layoutId = R.layout.booksonbookshelf_row_level_1;
                    break;
                case 2:
                    layoutId = R.layout.booksonbookshelf_row_level_2;
                    break;

                default:
                    // level 3 and higher all use the same layout.
                    layoutId = R.layout.booksonbookshelf_row_level_3;
                    break;
            }
        }

        View view = mInflater.inflate(layoutId, parent, false);
        view.setPaddingRelative(indent * mLevelIndent, 0, 0, 0);

        // Scale text if required
        float scale = mStyle.getTextScaleFactor();
        if (scale != 1.0f) {
            scaleTextViews(scale, view);
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
                Integer rowPos = (Integer) v.getTag(R.id.TAG_BL_POSITION);
                Objects.requireNonNull(rowPos, ErrorMsg.NULL_ROW_POS);
                mOnRowClickedListener.onItemClick(rowPos);
            }
        });

        holder.onClickTargetView.setOnLongClickListener(v -> {
            if (mOnRowClickedListener != null) {
                Integer rowPos = (Integer) v.getTag(R.id.TAG_BL_POSITION);
                Objects.requireNonNull(rowPos, ErrorMsg.NULL_ROW_POS);
                return mOnRowClickedListener.onItemLongClick(rowPos);
            }
            return false;
        });

        // further binding depends on the type of row (i.e. holder).
        holder.onBindViewHolder(mRowData, mStyle);
    }

    @Override
    @RowKind.Id
    public int getItemViewType(final int position) {
        if (mCursor.moveToPosition(position)) {
            return mRowData.getInt(DBDefinitions.KEY_BL_NODE_KIND);
        } else {
            // bogus, should not happen
            return RowKind.BOOK;
        }
    }

    @Override
    public int getItemCount() {
        return mCursor.getCount();
    }

    @Override
    public long getItemId(final int position) {
        if (hasStableIds() && mCursor.moveToPosition(position)) {
            return mRowData.getLong(DBDefinitions.KEY_PK_ID);
        } else {
            return RecyclerView.NO_ID;
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
     * @param scaleFactor to apply
     * @param root        the view (and its children) we'll scale
     */
    private void scaleTextViews(final float scaleFactor,
                                @NonNull final View root) {
        // text gets scaled
        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            float px = textView.getTextSize();
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scaleFactor);
        }

        // all elements get scaled padding; using the absolute padding values.
        root.setPadding((int) (scaleFactor * root.getPaddingLeft()),
                        (int) (scaleFactor * root.getPaddingTop()),
                        (int) (scaleFactor * root.getPaddingRight()),
                        (int) (scaleFactor * root.getPaddingBottom()));

        // go recursive if needed
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                scaleTextViews(scaleFactor, viewGroup.getChildAt(i));
            }
        }
    }

    /**
     * Get the text to display for the row at the <strong>passed</strong> cursor position.
     * i.e. NOT for the current position.
     * <p>
     * <br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String[] getPopupText(@NonNull final Context context,
                                 final int position) {
        // make sure it's still in range.
        int clampedPosition = MathUtils.clamp(position, 0, getItemCount() - 1);

        String[] section = null;

        // temporary move the cursor to the requested position, restore after we got the text.
        synchronized (this) {
            final int savedPos = mCursor.getPosition();
            if (mCursor.moveToPosition(clampedPosition)) {
                section = getLevelText(context);
                // checking the move would be pointless
                mCursor.moveToPosition(savedPos);
            }
        }

        return section;
    }

    /**
     * Get the full set of 'level' texts for the <strong>current</strong> row.
     *
     * @param context Current context
     *
     * @return level-text array
     */
    @NonNull
    public String[] getLevelText(@NonNull final Context context) {
        return new String[]{getLevelText(context, 1),
                            getLevelText(context, 2)};
    }

    /**
     * Get the text associated with the matching level group for the <strong>current</strong> row.
     *
     * @param context Current context
     * @param level   to get
     *
     * @return the text for that level, or {@code null} if none present.
     */
    @Nullable
    public String getLevelText(@NonNull final Context context,
                               @IntRange(from = 1) final int level) {

        // sanity check.
        if (BuildConfig.DEBUG /* always */) {
            if (level > (mStyle.groupCount() + 1)) {
                throw new IllegalArgumentException(
                        "level=" + level + "> (groupCount+1)=" + mStyle.groupCount() + 1);
            }
        }

        try {
            if (level > (mStyle.groupCount())) {
                // it's a book.
                return mRowData.getString(DBDefinitions.KEY_TITLE);

            } else {
                // it's a group.
                int index = level - 1;
                int kind = mStyle.getGroupKindAt(index);

                String columnName = mStyle.getGroupAt(index).getFormattedDomain().getName();
                String text = mRowData.getString(columnName);
                return RowKind.format(context, kind, text);
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
     * Base for all row ViewHolder classes.
     */
    abstract static class RowViewHolder
            extends RecyclerView.ViewHolder {

        /**
         * The view to install on-click listeners on. Can be the same as the itemView.
         * This is also the view where we will add tags with rowId etc,
         * as it is this View that will be passed to the onClick handlers.
         */
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

        @NonNull
        Context getContext() {
            return itemView.getContext();
        }

        @NonNull
        Locale getLocale() {
            return LocaleUtils.getUserLocale(itemView.getContext());
        }

        /**
         * Bind the data to the views in the holder.
         *
         * @param rowData with data to bind
         * @param style   to use
         */
        @CallSuper
        void onBindViewHolder(@NonNull final RowDataHolder rowData,
                              @NonNull final BooklistStyle style) {
        }
    }

    /**
     * ViewHolder for a {@link RowKind#BOOK} row.
     */
    static class BookHolder
            extends RowViewHolder {

        /** Format string. */
        @NonNull
        private final String mX_bracket_Y_bracket;

        /** Whether titles should be reordered. */
        private final boolean mReorderTitle;

        /** Book level - Based on style. */
        private final boolean mReadIsUsed;
        /** Book level - Based on style. */
        private final boolean mSignedIsUsed;
        /** Book level - Based on style. */
        private final boolean mEditionIsUsed;
        /** Book level - Based on style. */
        private final boolean mLendingIsUsed;
        /** Book level - Based on style. */
        private final boolean mSeriesIsUsed;

        /** Extras - Based on style. */
        private final boolean mBookshelfIsUsed;
        /** Extras - Based on style. */
        private final boolean mAuthorIsUsed;
        /** Extras - Based on style. */
        private final boolean mIsbnIsUsed;
        /** Extras - Based on style. */
        private final boolean mFormatIsUsed;
        /** Extras - Based on style. */
        private final boolean mLocationIsUsed;
        /** Extras - Based on style. */
        private final boolean mPublisherIsUsed;
        /** Extras - Based on style. */
        private final boolean mPubDateIsUsed;
        /** Extras - Based on style. */
        private final boolean mCoverIsUsed;
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

        /**
         * Constructor.
         *
         * <strong>Note:</strong> the itemView can be re-used.
         * Hence make sure to explicitly set visibility.
         *
         * @param itemView the view specific for this holder
         * @param style    to use
         */
        BookHolder(@NonNull final View itemView,
                   @NonNull final BooklistStyle style) {
            super(itemView);

            Context context = getContext();

            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);

            mReorderTitle = Prefs.reorderTitleForDisplaying(context);

            // now predetermine field usage & visibility.

            // always visible
            mTitleView = itemView.findViewById(R.id.title);

            // visibility depends on actual data; hidden by default in the layout
            mReadIsUsed = style.isUsed(DBDefinitions.KEY_READ);
            mReadView = itemView.findViewById(R.id.cbx_read);

            // visibility depends on actual data; hidden by default in the layout
            mSignedIsUsed = style.isUsed(DBDefinitions.KEY_SIGNED);
            mSignedView = itemView.findViewById(R.id.cbx_signed);

            // visibility depends on actual data; hidden by default in the layout
            mEditionIsUsed = style.isUsed(DBDefinitions.KEY_EDITION_BITMASK);
            mEditionView = itemView.findViewById(R.id.cbx_first_edition);

            // visibility depends on actual data; hidden by default in the layout
            mLendingIsUsed = style.isUsed(DBDefinitions.KEY_LOANEE);
            mOnLoanView = itemView.findViewById(R.id.cbx_on_loan);

            // visibility depends on actual data; but if not in use make sure to hide.
            mSeriesIsUsed = style.isUsed(DBDefinitions.KEY_SERIES_TITLE);
            mSeriesNumView = itemView.findViewById(R.id.series_num);
            mSeriesNumLongView = itemView.findViewById(R.id.series_num_long);
            if (!mSeriesIsUsed) {
                mSeriesNumView.setVisibility(View.GONE);
                mSeriesNumLongView.setVisibility(View.GONE);
            }

            // The 'extras' fields are hidden by default in the layout to prevent
            // white-space showing up and subsequently disappearing.
            // visibility is independent from actual data, so this is final.
            mBookshelfIsUsed = style.isExtraUsed(DBDefinitions.KEY_BOOKSHELF_CSV);
            mBookshelvesView = itemView.findViewById(R.id.shelves);
            mAuthorIsUsed = style.isExtraUsed(DBDefinitions.KEY_AUTHOR_FORMATTED);
            mAuthorView = itemView.findViewById(R.id.author);
            mIsbnIsUsed = style.isExtraUsed(DBDefinitions.KEY_ISBN);
            mIsbnView = itemView.findViewById(R.id.isbn);
            mFormatIsUsed = style.isExtraUsed(DBDefinitions.KEY_FORMAT);
            mFormatView = itemView.findViewById(R.id.format);
            mLocationIsUsed = style.isExtraUsed(DBDefinitions.KEY_LOCATION);
            mLocationView = itemView.findViewById(R.id.location);
            mPublisherIsUsed = style.isExtraUsed(DBDefinitions.KEY_PUBLISHER);
            mPubDateIsUsed = style.isExtraUsed(DBDefinitions.KEY_DATE_PUBLISHED);
            mPublisherView = itemView.findViewById(R.id.publisher);

            mCoverIsUsed = style.isExtraUsed(UniqueId.BKEY_THUMBNAIL);
            // We use a square space for the image so both portrait/landscape images work out.
            mMaxCoverSize = ImageUtils.getMaxImageSize(context, style.getThumbnailScale());
            mCoverView = itemView.findViewById(R.id.coverImage0);
            if (!mCoverIsUsed) {
                mCoverView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final RowDataHolder rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            String title = rowData.getString(DBDefinitions.KEY_TITLE);

            if (mReorderTitle) {
                Context context = mTitleView.getContext();
                String language = rowData.getString(DBDefinitions.KEY_LANGUAGE);
                title = LocaleUtils.reorderTitle(context, title, language);
            }
            mTitleView.setText(title);

            if (mReadIsUsed && rowData.contains(DBDefinitions.KEY_READ)) {
                boolean isSet = rowData.getBoolean(DBDefinitions.KEY_READ);
                mReadView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mReadView.setChecked(isSet);
            }

            if (mSignedIsUsed && rowData.contains(DBDefinitions.KEY_SIGNED)) {
                boolean isSet = rowData.getBoolean(DBDefinitions.KEY_SIGNED);
                mSignedView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mSignedView.setChecked(isSet);
            }

            if (mEditionIsUsed && rowData.contains(DBDefinitions.KEY_EDITION_BITMASK)) {
                boolean isSet = (rowData.getInt(DBDefinitions.KEY_EDITION_BITMASK)
                                 & Book.EDITION_FIRST) != 0;
                mEditionView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mEditionView.setChecked(isSet);
            }

            if (mLendingIsUsed && rowData.contains(DBDefinitions.KEY_LOANEE_AS_BOOLEAN)) {
                boolean isSet = !rowData.getBoolean(DBDefinitions.KEY_LOANEE_AS_BOOLEAN);
                mOnLoanView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mOnLoanView.setChecked(isSet);
            }

            if (mCoverIsUsed && rowData.contains(DBDefinitions.KEY_BOOK_UUID)) {
                String uuid = rowData.getString(DBDefinitions.KEY_BOOK_UUID);
                // store the uuid for use in the OnClickListener
                mCoverView.setTag(R.id.TAG_ITEM, uuid);
                boolean isSet = ImageUtils.setImageView(mCoverView, uuid, 0,
                                                        mMaxCoverSize, mMaxCoverSize);
                if (isSet) {
                    //Allow zooming by clicking on the image
                    mCoverView.setOnClickListener(v -> {
                        FragmentActivity activity = (FragmentActivity) v.getContext();
                        String currentUuid = (String) v.getTag(R.id.TAG_ITEM);
                        File file = StorageUtils.getCoverFileForUuid(activity, currentUuid, 0);
                        if (file.exists()) {
                            ZoomedImageDialogFragment.show(activity.getSupportFragmentManager(),
                                                           file);
                        }
                    });
                }
            }

            if (mSeriesIsUsed && rowData.contains(DBDefinitions.KEY_BOOK_NUM_IN_SERIES)) {
                String number = rowData.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
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

            if (mBookshelfIsUsed && rowData.contains(DBDefinitions.KEY_BOOKSHELF_CSV)) {
                showOrHide(mBookshelvesView, rowData.getString(DBDefinitions.KEY_BOOKSHELF_CSV));
            }
            if (mAuthorIsUsed && rowData.contains(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                showOrHide(mAuthorView, rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
            }
            if (mIsbnIsUsed && rowData.contains(DBDefinitions.KEY_ISBN)) {
                showOrHide(mIsbnView, rowData.getString(DBDefinitions.KEY_ISBN));
            }
            if (mFormatIsUsed && rowData.contains(DBDefinitions.KEY_FORMAT)) {
                showOrHide(mFormatView, rowData.getString(DBDefinitions.KEY_FORMAT));
            }
            if (mLocationIsUsed && rowData.contains(DBDefinitions.KEY_LOCATION)) {
                showOrHide(mLocationView, rowData.getString(DBDefinitions.KEY_LOCATION));
            }
            if ((mPublisherIsUsed && rowData.contains(DBDefinitions.KEY_PUBLISHER))
                || (mPubDateIsUsed && rowData.contains(DBDefinitions.KEY_DATE_PUBLISHED))) {
                showOrHide(mPublisherView, getPublisherAndPubDateText(getLocale(),
                                                                      rowData,
                                                                      mPublisherIsUsed,
                                                                      mPubDateIsUsed));
            }
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final Locale locale,
                                          @NonNull final RowDataHolder rowData,
                                          final boolean publisherIsUsed,
                                          final boolean pubDateIsUsed) {
            String tmp = null;
            if (publisherIsUsed) {
                tmp = rowData.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if (pubDateIsUsed) {
                tmpPubDate = rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED);
            }
            // show combined Publisher and Pub. Date
            if ((tmp != null) && (tmpPubDate != null)) {
                if (tmpPubDate.length() == 4) {
                    // 4 digits is just the year.
                    tmp = String.format(mX_bracket_Y_bracket, tmp, tmpPubDate);
                } else if (tmpPubDate.length() > 4) {
                    // parse/format the date
                    tmp = String.format(mX_bracket_Y_bracket, tmp,
                                        DateUtils.toPrettyDate(locale, tmpPubDate));
                }
            } else if (tmpPubDate != null) {
                // there was no publisher, just use the date
                if (tmpPubDate.length() == 4) {
                    tmp = tmpPubDate;
                } else if (tmpPubDate.length() > 4) {
                    tmp = DateUtils.toPrettyDate(locale, tmpPubDate);
                }
            }

            return tmp;
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

    /**
     * ViewHolder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup called ROW_INFO.
     */
    static class GenericStringHolder
            extends RowViewHolder {

        /** Key of the related data column. */
        @NonNull
        final String mKey;
        /** String id to use when data is blank. */
        @StringRes
        final int mNoDataId;

        /*** View to populate. */
        @NonNull
        final
        TextView mTextView;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         * @param noDataId String id to use when data is blank
         */
        GenericStringHolder(@NonNull final View itemView,
                            @NonNull final String key,
                            @StringRes final int noDataId) {
            super(itemView);
            mKey = key;
            mNoDataId = noDataId;

            mTextView = itemView.findViewById(R.id.name);
        }

        @Override
        public void onBindViewHolder(@NonNull final RowDataHolder rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            setText(rowData.getString(mKey),
                    rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
        }

        /**
         * For a simple row, just set the text.
         *
         * @param textId String to display
         * @param level  for this row
         */
        public void setText(@StringRes final int textId,
                            @SuppressWarnings("unused") @IntRange(from = 1) final int level) {
            mTextView.setText(mTextView.getContext().getString(textId));
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * <strong>Developer note:</strong> child implementation currently have overlapping code
         * with {@link com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup.RowKind
         * #format(Context, int, String)}
         *
         * @param text  String to display; can be {@code null} or empty
         * @param level for this row
         */
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                // if we have text, show it.
                mTextView.setText(text);
            } else {
                mTextView.setText(mNoDataId);
            }
        }
    }

    /**
     * ViewHolder for a row that displays a 'rating'.
     */
    static class RatingHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         */
        RatingHolder(@NonNull final View itemView,
                     @NonNull final String key) {
            super(itemView, key, R.string.hint_empty_rating);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null) {
                try {
                    // Locale independent.
                    int i = (int) Float.parseFloat(text);
                    // If valid, format the description
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        super.setText(
                                getContext().getResources()
                                            .getQuantityString(R.plurals.n_stars, i, i),
                                level);
                        return;
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(TAG, e);
                }
            }

            super.setText(text, level);
        }
    }

    /**
     * ViewHolder for a row that displays a 'language'.
     */
    static class LanguageHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         */
        @SuppressWarnings("SameParameterValue")
        LanguageHolder(@NonNull final View itemView,
                       @NonNull final String key) {
            super(itemView, key, R.string.hint_empty_language);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                super.setText(LanguageUtils.getDisplayName(getContext(), text), level);
            } else {
                super.setText(text, level);
            }
        }
    }

    /**
     * ViewHolder for a row that displays a 'read/unread' (as text) status.
     */
    static class ReadUnreadHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         */
        ReadUnreadHolder(@NonNull final View itemView,
                         @NonNull final String key) {
            super(itemView, key, R.string.hint_empty_read_status);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (ParseUtils.parseBoolean(text, true)) {
                super.setText(R.string.lbl_read, level);
            } else {
                super.setText(R.string.lbl_unread, level);
            }
        }
    }

    /**
     * ViewHolder for a row that displays a 'month'.
     * This code turns a month number into a Locale based month name.
     */
    static class MonthHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         */
        MonthHolder(@NonNull final View itemView,
                    @NonNull final String key) {
            super(itemView, key, R.string.hint_empty_month);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                try {
                    int i = Integer.parseInt(text);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        super.setText(DateUtils.getMonthName(getLocale(), i, false), level);
                        return;
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(TAG, e);
                }
            }
            super.setText(text, level);
        }
    }

    /**
     * ViewHolder for a Series.
     */
    static class SeriesHolder
            extends CheckableStringHolder {

        /** Whether titles should be reordered. */
        private final boolean mReorderTitle;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         */
        SeriesHolder(@NonNull final View itemView,
                     @NonNull final String key) {
            super(itemView, key, R.string.hint_empty_series,
                  DBDefinitions.KEY_SERIES_IS_COMPLETE);

            mReorderTitle = Prefs.reorderTitleForDisplaying(getContext());
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty() && mReorderTitle) {
                // URGENT: translated series are not reordered unless the app runs in that language
                // solution/problem: we would need the Series id (and not just the titel)
                // to call {@link DAO#getSeriesLanguage(long)}
                super.setText(LocaleUtils.reorderTitle(getContext(), text, getLocale()), level);
            } else {
                super.setText(text, level);
            }
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
         * @param itemView the view specific for this holder
         * @param key      key into the data set
         */
        AuthorHolder(@NonNull final View itemView,
                     @NonNull final String key) {
            super(itemView, key, R.string.hint_empty_author,
                  DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        }
    }

    /**
     * ViewHolder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    abstract static class CheckableStringHolder
            extends GenericStringHolder {

        /** Column name of related boolean column. */
        private final String mIsLockedSourceCol;

        /**
         * Constructor.
         *
         * @param itemView       the view specific for this holder
         * @param key            key into the data set
         * @param noDataId       String id to use when data is blank
         * @param isLockedSource Column name to use for the boolean 'lock' status
         */
        CheckableStringHolder(@NonNull final View itemView,
                              @NonNull final String key,
                              @StringRes final int noDataId,
                              @NonNull final String isLockedSource) {
            super(itemView, key, noDataId);
            mIsLockedSourceCol = isLockedSource;
        }

        @Override
        public void onBindViewHolder(@NonNull final RowDataHolder rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            Drawable lock = null;
            if (rowData.getBoolean(mIsLockedSourceCol)) {
                lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
            }

            mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null, null, lock, null);
        }
    }
}

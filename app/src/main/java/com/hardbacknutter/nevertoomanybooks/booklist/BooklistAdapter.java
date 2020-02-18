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
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithTitle;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
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
    @NonNull
    private final FieldsInUse mFieldsInUse;
    /** Whether titles should be reordered. */
    private final boolean mReorderTitle;

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

        mReorderTitle = ItemWithTitle.isReorderTitleForDisplaying(context);
        mFieldsInUse = new FieldsInUse(context, style);

        setHasStableIds(true);
    }

    public void setOnRowClickedListener(@Nullable final OnRowClickedListener onRowClickedListener) {
        mOnRowClickedListener = onRowClickedListener;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            @BooklistGroup.Id final int rowGroupId) {

        final View itemView = createView(parent, rowGroupId);

        switch (rowGroupId) {
            case BooklistGroup.BOOK:
                return new BookHolder(itemView, mStyle, mReorderTitle, mFieldsInUse);

            case BooklistGroup.AUTHOR:
                return new AuthorHolder(itemView);

            case BooklistGroup.SERIES:
                return new SeriesHolder(itemView, mReorderTitle);

            case BooklistGroup.RATING:
                return new RatingHolder(itemView);

            case BooklistGroup.LANGUAGE:
                return new LanguageHolder(itemView);

            case BooklistGroup.READ_STATUS:
                return new ReadUnreadHolder(itemView);

            // Months are displayed by name
            case BooklistGroup.DATE_PUBLISHED_MONTH:
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH:
            case BooklistGroup.DATE_ACQUIRED_MONTH:
            case BooklistGroup.DATE_ADDED_MONTH:
            case BooklistGroup.DATE_READ_MONTH:
            case BooklistGroup.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(itemView, rowGroupId);

            // NEWTHINGS: ROW_KIND_x
            case BooklistGroup.BOOKSHELF:
            case BooklistGroup.COLOR:
            case BooklistGroup.DATE_ACQUIRED_DAY:
            case BooklistGroup.DATE_ACQUIRED_YEAR:
            case BooklistGroup.DATE_ADDED_DAY:
            case BooklistGroup.DATE_ADDED_YEAR:
            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR:
            case BooklistGroup.DATE_LAST_UPDATE_DAY:
            case BooklistGroup.DATE_LAST_UPDATE_YEAR:
            case BooklistGroup.DATE_PUBLISHED_YEAR:
            case BooklistGroup.DATE_READ_DAY:
            case BooklistGroup.DATE_READ_YEAR:
            case BooklistGroup.FORMAT:
            case BooklistGroup.GENRE:
            case BooklistGroup.BOOK_TITLE_LETTER:
            case BooklistGroup.SERIES_TITLE_LETTER:
            case BooklistGroup.PUBLISHER:
            case BooklistGroup.LOCATION:
            case BooklistGroup.LOANED:
            default:
                return new GenericStringHolder(itemView, rowGroupId, R.string.hint_empty_field);
        }
    }

    private View createView(@NonNull final ViewGroup parent,
                            @BooklistGroup.Id final int rowGroupId) {

        final int level = mRowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
        // Indent (0..) based on level (1..)
        int indent = level - 1;

        @LayoutRes
        final int layoutId;

        // A Book occurs always at the lowest level regardless of the groups in the style.
        if (rowGroupId == BooklistGroup.BOOK) {
            @ImageUtils.Scale
            final int scale = mStyle.getThumbnailScale();
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

        final View view = mInflater.inflate(layoutId, parent, false);
        view.setPaddingRelative(indent * mLevelIndent, 0, 0, 0);

        // Scale text if required
        if (mStyle.getTextScale() != BooklistStyle.FONT_SCALE_MEDIUM) {
            scaleTextViews(view, mStyle.getTextSpUnits(), mStyle.getTextPaddingFactor());
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
        holder.onBindViewHolder(mRowData, mStyle);
    }

    @Override
    @BooklistGroup.Id
    public int getItemViewType(final int position) {
        if (mCursor.moveToPosition(position)) {
            return mRowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP);
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
     * @param root              the view (and its children) we'll scale
     * @param textSizeInSpUnits the text size in SP units (e.g. 14,18,22)
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
        final int clampedPosition = MathUtils.clamp(position, 0, getItemCount() - 1);

        final String[] section;
        // temporary move the cursor to the requested position, restore after we got the text.
        synchronized (this) {
            final int savedPos = mCursor.getPosition();
            if (mCursor.moveToPosition(clampedPosition)) {
                section = getLevelText(context);
                mCursor.moveToPosition(savedPos);
            } else {
                section = null;
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
            if (level > (mStyle.getGroupCount() + 1)) {
                throw new IllegalArgumentException(
                        "level=" + level + "> (getGroupCount+1)=" + mStyle.getGroupCount() + 1);
            }
        }

        try {
            if (level > (mStyle.getGroupCount())) {
                // it's a book.
                return mRowData.getString(DBDefinitions.KEY_TITLE);

            } else {
                // it's a group.
                final BooklistGroup group = mStyle.getGroupForLevel(level);
                final String value = mRowData.getString(group.getDisplayDomain().getName());
                return group.format(context, value);
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
        private boolean bookshelf;
        /** Book row details - Based on style. */
        private boolean author;
        /** Book row details - Based on style. */
        private boolean isbn;
        /** Book row details - Based on style. */
        private boolean format;
        /** Book row details - Based on style. */
        private boolean location;
        /** Book row details - Based on style. */
        private boolean publisher;
        /** Book row details - Based on style. */
        private boolean pubDate;
        /** Book row details - Based on style. */
        private boolean cover;

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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            read = App.isUsed(prefs, DBDefinitions.KEY_READ);
            signed = App.isUsed(prefs, DBDefinitions.KEY_SIGNED);
            edition = App.isUsed(prefs, DBDefinitions.KEY_EDITION_BITMASK);
            lending = App.isUsed(prefs, DBDefinitions.KEY_LOANEE);
            series = App.isUsed(prefs, DBDefinitions.KEY_SERIES_TITLE);

            bookshelf = style.isBookDetailUsed(prefs, DBDefinitions.KEY_BOOKSHELF_CSV);
            author = style.isBookDetailUsed(prefs, DBDefinitions.KEY_AUTHOR_FORMATTED);
            isbn = style.isBookDetailUsed(prefs, DBDefinitions.KEY_ISBN);
            format = style.isBookDetailUsed(prefs, DBDefinitions.KEY_FORMAT);
            location = style.isBookDetailUsed(prefs, DBDefinitions.KEY_LOCATION);
            publisher = style.isBookDetailUsed(prefs, DBDefinitions.KEY_PUBLISHER);
            pubDate = style.isBookDetailUsed(prefs, DBDefinitions.KEY_DATE_PUBLISHED);
            cover = style.isBookDetailUsed(prefs, UniqueId.BKEY_THUMBNAIL);
        }

        /**
         * Update the in-use flags with row-data available fields.
         * Call this once only.
         *
         * @param rowData to read fields from
         */
        void set(@NonNull final RowDataHolder rowData) {
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
            bookshelf = bookshelf && rowData.contains(DBDefinitions.KEY_BOOKSHELF_CSV);
            author = author && rowData.contains(DBDefinitions.KEY_AUTHOR_FORMATTED);
            isbn = isbn && rowData.contains(DBDefinitions.KEY_ISBN);
            format = format && rowData.contains(DBDefinitions.KEY_FORMAT);
            location = location && rowData.contains(DBDefinitions.KEY_LOCATION);
            publisher = publisher && rowData.contains(DBDefinitions.KEY_PUBLISHER);
            pubDate = pubDate && rowData.contains(DBDefinitions.KEY_DATE_PUBLISHED);
        }
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

        private final boolean mReorderTitle;
        private final FieldsInUse mInUse;

        /**
         * Constructor.
         *
         * <strong>Note:</strong> the itemView can be re-used.
         * Hence make sure to explicitly set visibility.
         *
         * @param itemView    the view specific for this holder
         * @param style       to use
         * @param fieldsInUse which fields are used
         */
        BookHolder(@NonNull final View itemView,
                   @NonNull final BooklistStyle style,
                   final boolean reorderTitle,
                   @NonNull final FieldsInUse fieldsInUse) {
            super(itemView);
            mReorderTitle = reorderTitle;
            mInUse = fieldsInUse;
            Context context = itemView.getContext();
            mLocale = LocaleUtils.getUserLocale(context);

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
            mBookshelvesView = itemView.findViewById(R.id.shelves);
            mAuthorView = itemView.findViewById(R.id.author);
            mIsbnView = itemView.findViewById(R.id.isbn);
            mFormatView = itemView.findViewById(R.id.format);
            mLocationView = itemView.findViewById(R.id.location);
            mPublisherView = itemView.findViewById(R.id.publisher);

            // We use a square space for the image so both portrait/landscape images work out.
            mMaxCoverSize = ImageUtils.getMaxImageSize(context, style.getThumbnailScale());
            mCoverView = itemView.findViewById(R.id.coverImage0);
            if (!mInUse.cover) {
                // shown by default, so hide it if not in use.
                mCoverView.setVisibility(View.GONE);
            }
        }

        @Override
        void onBindViewHolder(@NonNull final RowDataHolder rowData,
                              @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);
            // update the in-use flags with row-data available fields. Do this once only.
            if (!mInUse.isSet) {
                mInUse.set(rowData);
            }

            final String title;
            if (mReorderTitle) {
                String bookLanguage = rowData.getString(DBDefinitions.KEY_LANGUAGE);
                if (!bookLanguage.isEmpty()) {
                    title = ItemWithTitle.reorderTitle(itemView.getContext(),
                                                       rowData.getString(DBDefinitions.KEY_TITLE),
                                                       bookLanguage);
                } else {
                    title = ItemWithTitle.reorderTitle(itemView.getContext(),
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
                final boolean isSet = ImageUtils.setImageView(mCoverView, uuid, 0,
                                                              mMaxCoverSize, mMaxCoverSize);
                if (isSet) {
                    //Allow zooming by clicking on the image
                    mCoverView.setOnClickListener(v -> {
                        final FragmentActivity activity = (FragmentActivity) v.getContext();
                        final String currentUuid = (String) v.getTag(R.id.TAG_ITEM);
                        final File file = StorageUtils.getCoverFileForUuid(
                                activity, currentUuid, 0);
                        if (file.exists()) {
                            ZoomedImageDialogFragment
                                    .show(activity.getSupportFragmentManager(), file);
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

            if (mInUse.bookshelf) {
                showOrHide(mBookshelvesView, rowData.getString(DBDefinitions.KEY_BOOKSHELF_CSV));
            }
            if (mInUse.author) {
                showOrHide(mAuthorView, rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
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
            if (mInUse.publisher || mInUse.pubDate) {
                showOrHide(mPublisherView, getPublisherAndPubDateText(rowData));
            }
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final RowDataHolder rowData) {
            final String publicationDate;
            if (mInUse.pubDate) {
                publicationDate = DateUtils.toPrettyDate(
                        LocaleUtils.getUserLocale(itemView.getContext()),
                        rowData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
            } else {
                publicationDate = null;
            }

            final String publisher;
            if (mInUse.publisher) {
                publisher = rowData.getString(DBDefinitions.KEY_PUBLISHER);
            } else {
                publisher = null;
            }

            if (publisher != null) {
                if (publicationDate != null) {
                    // Combine Publisher and date
                    return String.format(mX_bracket_Y_bracket, publisher, publicationDate);
                } else {
                    // there was no date, just use the publisher
                    return publisher;
                }
            } else {
                // return the date (or null)
                return publicationDate;
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

        GenericStringHolder(@NonNull final View itemView,
                            @BooklistGroup.Id final int rowGroupId,
                            @StringRes final int noDataId) {
            super(itemView);
            mKey = BooklistGroup.getCompoundKey(rowGroupId).getDomain().getName();
            mNoDataId = noDataId;

            mTextView = itemView.findViewById(R.id.name);
        }

        @Override
        void onBindViewHolder(@NonNull final RowDataHolder rowData,
                              @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            setText(rowData.getString(mKey), rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
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
         * with {@link BooklistGroup#format}
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
         */
        RatingHolder(@NonNull final View itemView) {
            super(itemView, BooklistGroup.RATING, R.string.hint_empty_rating);
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
                        Resources resources = itemView.getContext().getResources();
                        super.setText(resources.getQuantityString(R.plurals.n_stars, i, i), level);
                        return;
                    }
                } catch (@NonNull final NumberFormatException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.e(TAG, "|text=`" + text + '`', e);
                    }
                }
            }
            // null or NumberFormatException
            super.setText(mNoDataId, level);
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
         */
        @SuppressWarnings("SameParameterValue")
        LanguageHolder(@NonNull final View itemView) {
            super(itemView, BooklistGroup.LANGUAGE, R.string.hint_empty_language);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                super.setText(LanguageUtils.getDisplayName(itemView.getContext(), text), level);
            } else {
                super.setText(mNoDataId, level);
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
         */
        ReadUnreadHolder(@NonNull final View itemView) {
            super(itemView, BooklistGroup.READ_STATUS, R.string.hint_empty_read_status);
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
         * @param itemView   the view specific for this holder
         * @param rowGroupId Group id
         */
        MonthHolder(@NonNull final View itemView,
                    @BooklistGroup.Id final int rowGroupId) {
            super(itemView, rowGroupId, R.string.hint_empty_month);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                try {
                    int m = Integer.parseInt(text);
                    Locale locale = LocaleUtils.getUserLocale(itemView.getContext());
                    // If valid, get the name
                    if (m > 0 && m <= 12) {
                        super.setText(DateUtils.getMonthName(locale, m, false), level);
                        return;
                    }
                } catch (@NonNull final NumberFormatException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.e(TAG, "|text=`" + text + '`', e);
                    }
                }
            }
            // null, empty or NumberFormatException
            super.setText(mNoDataId, level);
        }
    }

    /**
     * ViewHolder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    abstract static class CheckableStringHolder
            extends GenericStringHolder {

        /** Column name of related boolean column. */
        private final String mCheckableColumnKey;

        /**
         * Constructor.
         *
         * @param itemView           the view specific for this holder
         * @param key                key into the data set
         * @param noDataId           String id to use when data is blank
         * @param checkableColumnKey Column name to use for the boolean 'lock' status
         */
        CheckableStringHolder(@NonNull final View itemView,
                              @NonNull final String key,
                              @StringRes final int noDataId,
                              @NonNull final String checkableColumnKey) {
            super(itemView, key, noDataId);
            mCheckableColumnKey = checkableColumnKey;
        }

        @Override
        void onBindViewHolder(@NonNull final RowDataHolder rowData,
                              @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            final Drawable lock;
            if (rowData.getBoolean(mCheckableColumnKey)) {
                lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
            } else {
                lock = null;
            }

            mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null, null, lock, null);
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
         */
        AuthorHolder(@NonNull final View itemView) {
            super(itemView, DBDefinitions.KEY_AUTHOR_FORMATTED, R.string.hint_empty_author,
                  DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        }
    }

    /**
     * ViewHolder for a Series.
     */
    static class SeriesHolder
            extends CheckableStringHolder {

        /** Whether titles should be reordered. */
        private final boolean mReorderTitle;
        /** Cache the locale. */
        @NonNull
        private final Locale mLocale;

        /** rowData.getString is never null. */
        @SuppressWarnings("NullableProblems")
        @NonNull
        private String mBookLanguage;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         */
        SeriesHolder(@NonNull final View itemView,
                     final boolean reorderTitle) {
            super(itemView, DBDefinitions.KEY_SERIES_TITLE, R.string.hint_empty_series,
                  DBDefinitions.KEY_SERIES_IS_COMPLETE);

            mReorderTitle = reorderTitle;
            mLocale = LocaleUtils.getUserLocale(itemView.getContext());
        }

        @Override
        void onBindViewHolder(@NonNull final RowDataHolder rowData,
                              @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);
            // grab the book language
            mBookLanguage = rowData.getString(DBDefinitions.KEY_LANGUAGE);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (mReorderTitle && text != null && !text.isEmpty()) {
                // FIXME: translated series are reordered in the book's language
                // It should be done using the Series language
                // but as long as we don't store the Series language there is no point
                Context context = itemView.getContext();
                if (!mBookLanguage.isEmpty()) {
                    super.setText(ItemWithTitle.reorderTitle(context, text, mBookLanguage), level);
                } else {
                    super.setText(ItemWithTitle.reorderTitle(context, text, mLocale), level);
                }
            } else {
                super.setText(text, level);
            }
        }
    }
}

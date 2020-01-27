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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
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
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.math.MathUtils;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup.RowKind;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
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
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final BooklistStyle mStyle;
    /** The cursor is the equivalent of the 'list of items'. */
    @NonNull
    private final Cursor mCursor;
    @NonNull
    private final CursorRow mCursorRow;

    @Nullable
    private OnRowClickedListener mOnRowClickedListener;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   The style is used by (some) individual rows.
     * @param db      Database Access
     * @param cursor  cursor with the 'list of items'.
     */
    public BooklistAdapter(@NonNull final Context context,
                           @NonNull final BooklistStyle style,
                           @NonNull final DAO db,
                           @NonNull final Cursor cursor) {
        mInflater = LayoutInflater.from(context);
        mStyle = style;
        mDb = db;
        mCursor = cursor;
        mCursorRow = new CursorRow(mCursor);
        mLevelIndent = context.getResources().getDimensionPixelSize(R.dimen.booklist_level_indent);

        setHasStableIds(true);
    }

    void setOnRowClickedListener(@Nullable final OnRowClickedListener onRowClickedListener) {
        mOnRowClickedListener = onRowClickedListener;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            @RowKind.Id final int viewType) {

        View itemView = createView(parent, viewType);

        // a BookHolder is based on multiple columns, the holder itself will sort them out.
        if (viewType == RowKind.BOOK) {
            return new BookHolder(itemView, mDb, mStyle);
        }

        String columnName = RowKind.get(viewType).getFormattedDomain().getName();
        int columnIndex = mCursor.getColumnIndex(columnName);
        if (columnIndex < 0) {
            throw new ColumnNotPresentException(columnName);
        }

        switch (viewType) {
            // NEWTHINGS: ROW_KIND_x

            case RowKind.AUTHOR:
                return new AuthorHolder(itemView, columnIndex);

            case RowKind.SERIES:
                return new SeriesHolder(itemView, columnIndex);

            // Months are displayed by name
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUB_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(itemView, columnIndex);

            // some special formatting holders
            case RowKind.RATING:
                return new RatingHolder(itemView, columnIndex);
            case RowKind.LANGUAGE:
                return new LanguageHolder(itemView, columnIndex);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(itemView, columnIndex);

            // Sanity check
            //noinspection ConstantConditions
            case RowKind.BOOK:
                throw new UnexpectedValueException(viewType);

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
                return new GenericStringHolder(itemView, columnIndex,
                                               R.string.hint_empty_field);
        }
    }

    private View createView(@NonNull final ViewGroup parent,
                            @RowKind.Id final int viewType) {

        int level = mCursorRow.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
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
                Objects.requireNonNull(rowPos);
                mOnRowClickedListener.onItemClick(rowPos);
            }
        });

        holder.onClickTargetView.setOnLongClickListener(v -> {
            if (mOnRowClickedListener != null) {
                Integer rowPos = (Integer) v.getTag(R.id.TAG_BL_POSITION);
                Objects.requireNonNull(rowPos);
                return mOnRowClickedListener.onItemLongClick(rowPos);
            }
            return false;
        });

        // further binding depends on the type of row (i.e. holder).
        holder.onBindViewHolder(mCursorRow, mStyle);
    }

    @Override
    @RowKind.Id
    public int getItemViewType(final int position) {
        if (mCursor.moveToPosition(position)) {
            return mCursorRow.getInt(DBDefinitions.KEY_BL_NODE_KIND);
        } else {
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
            return mCursorRow.getLong(DBDefinitions.KEY_PK_ID);
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
    @NonNull
    @Override
    public String[] getPopupText(@NonNull final Context context,
                                 final int position) {
        // make sure it's still in range.
        int clampedPosition = MathUtils.clamp(position, 0, getItemCount() - 1);

        String[] section;

        // temporary move the cursor to the requested position, restore after we got the text.
        synchronized (this) {
            final int savedPos = mCursor.getPosition();
            mCursor.moveToPosition(clampedPosition);
            section = getLevelText(context);
            mCursor.moveToPosition(savedPos);
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
    String[] getLevelText(@NonNull final Context context) {
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
    String getLevelText(@NonNull final Context context,
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
                return mCursorRow.getString(DBDefinitions.KEY_TITLE);

            } else {
                // it's a group.
                int index = level - 1;
                int kind = mStyle.getGroupKindAt(index);

                String columnName = mStyle.getGroupAt(index).getFormattedDomain().getName();
                String text = mCursorRow.getString(columnName);
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
     * Background task to get 'extra' details for a book row.
     * Doing this in a background task keeps the booklist cursor simple and small.
     * Used by {@link BookHolder}.
     * <p>
     * See {@link BooklistStyle#useTaskForExtras()}
     */
    private static class GetBookExtrasTask
            extends AsyncTask<Void, Void, Boolean> {

        /** Log tag. */
        private static final String TAG = "GetBookExtrasTask";

        /** Format string. */
        @NonNull
        private final String mX_bracket_Y_bracket;

        /** The listener for the tasks result. */
        @NonNull
        private final WeakReference<TaskFinishedListener> mTaskListener;

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /** The book id to fetch. */
        private final long mBookId;
        /** Bit mask with the fields that should be fetched. */
        @BooklistStyle.ExtraOption
        private final int mExtraFields;

        /** Resulting data. */
        private final Bundle mResults = new Bundle();

        /**
         * Constructor.
         *
         * @param context      Current context
         * @param db           Database Access
         * @param bookId       Book to fetch
         * @param taskListener View holder for the book, used as callback for task results.
         * @param extraFields  bit mask with the fields that should be fetched.
         */
        @UiThread
        GetBookExtrasTask(@NonNull final Context context,
                          @NonNull final DAO db,
                          final long bookId,
                          @NonNull final TaskFinishedListener taskListener,
                          @BooklistStyle.ExtraOption final int extraFields) {

            mDb = db;
            mBookId = bookId;
            mTaskListener = new WeakReference<>(taskListener);
            mExtraFields = extraFields;

            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookExtrasTask " + mBookId);

            //We ALWAYS fetch the full set of extras columns from the database.
            // Performance tests have shown that returning only selected columns makes
            // no difference with returning the full set.
            try (Cursor cursor = mDb.fetchBookExtrasById(mBookId)) {
                // Bail out if we don't have a book.
                if (!cursor.moveToFirst()) {
                    return false;
                }

                final CursorRow cursorRow = new CursorRow(cursor);

                String tmp;
                if ((mExtraFields & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    tmp = cursorRow.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, tmp);
                        // no author type for now.
//                        mResults.putInt(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK,
//                                        mCursorRow.getInt(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK));
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    tmp = cursorRow.getString(DBDefinitions.KEY_LOCATION);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_LOCATION, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    tmp = cursorRow.getString(DBDefinitions.KEY_FORMAT);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_FORMAT, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    tmp = cursorRow.getString(DBDefinitions.KEY_BOOKSHELF);
                    if (!tmp.isEmpty()) {
                        // note the destination is KEY_BOOKSHELF_CSV
                        mResults.putString(DBDefinitions.KEY_BOOKSHELF_CSV, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_ISBN) != 0) {
                    tmp = cursorRow.getString(DBDefinitions.KEY_ISBN);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_ISBN, tmp);
                    }
                }

                tmp = getPublisherAndPubDateText(cursorRow);
                if (tmp != null && !tmp.isEmpty()) {
                    mResults.putString(DBDefinitions.KEY_PUBLISHER, tmp);
                }

            } catch (@NonNull final NumberFormatException e) {
                Logger.error(TAG, e);
                return false;
            }
            return true;
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final CursorRow cursorRow) {
            String tmp = null;
            if ((mExtraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                tmp = cursorRow.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if ((mExtraFields & BooklistStyle.EXTRAS_PUB_DATE) != 0) {
                tmpPubDate = cursorRow.getString(DBDefinitions.KEY_DATE_PUBLISHED);
            }
            // show combined Publisher and Pub. Date
            if ((tmp != null) && (tmpPubDate != null)) {
                if (tmpPubDate.length() == 4) {
                    // 4 digits is just the year.
                    tmp = String.format(mX_bracket_Y_bracket, tmp, tmpPubDate);
                } else if (tmpPubDate.length() > 4) {
                    // parse/format the date
                    tmp = String.format(mX_bracket_Y_bracket, tmp,
                                        DateUtils.toPrettyDate(tmpPubDate));
                }
            } else if (tmpPubDate != null) {
                // there was no publisher, just use the date
                if (tmpPubDate.length() == 4) {
                    tmp = tmpPubDate;
                } else if (tmpPubDate.length() > 4) {
                    tmp = DateUtils.toPrettyDate(tmpPubDate);
                }
            }

            return tmp;
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Boolean result) {
            if (!result) {
                return;
            }
            // Fields not used will be null.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mResults);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onPostExecute|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }

        interface TaskFinishedListener {

            /**
             * Results from fetching the extras.
             * Theoretically individual fields could be {@code null} (but shouldn't).
             *
             * @param results a bundle with the result field.
             */
            void onTaskFinished(@NonNull Bundle results);
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
         * @param cursorRow with data to bind
         * @param style     to use
         */
        @CallSuper
        void onBindViewHolder(@NonNull final CursorRow cursorRow,
                              @NonNull final BooklistStyle style) {
        }
    }

    /**
     * ViewHolder for a {@link RowKind#BOOK} row.
     */
    public static class BookHolder
            extends RowViewHolder {

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /** Bookshelves label resource string. */
        @NonNull
        private final String mShelvesLabel;
        /** Location label resource string. */
        @NonNull
        private final String mLocationLabel;
        /** Format string. */
        @NonNull
        private final String mName_colon_value;
        /** Format string. */
        @NonNull
        private final String mX_bracket_Y_bracket;

        /** Whether titles should be reordered. */
        private final boolean mReorderTitle;

        /** Based on style; bitmask with the extra fields to use. */
        @BooklistStyle.ExtraOption
        private final int mExtraFieldsUsed;

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
         * Receives the results of one {@link GetBookExtrasTask} and populates the fields.
         */
        private final GetBookExtrasTask.TaskFinishedListener mBookExtrasTaskFinishedListener =
                new GetBookExtrasTask.TaskFinishedListener() {
                    @Override
                    public void onTaskFinished(@NonNull final Bundle results) {
                        // do not re-test usage here. If not in use, we wouldn't have fetched them.
                        showOrHide(mBookshelvesView, mShelvesLabel,
                                   results.getString(DBDefinitions.KEY_BOOKSHELF_CSV));
                        showOrHide(mAuthorView,
                                   results.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                        showOrHide(mIsbnView,
                                   results.getString(DBDefinitions.KEY_ISBN));
                        showOrHide(mFormatView,
                                   results.getString(DBDefinitions.KEY_FORMAT));
                        showOrHide(mLocationView, mLocationLabel,
                                   results.getString(DBDefinitions.KEY_LOCATION));
                        showOrHide(mPublisherView,
                                   results.getString(DBDefinitions.KEY_PUBLISHER));
                    }
                };

        /**
         * Constructor.
         *
         * <strong>Note:</strong> the itemView can be re-used.
         * Hence make sure to explicitly set visibility.
         *
         * @param itemView the view specific for this holder
         * @param db       Database Access.
         * @param style    to use
         */
        BookHolder(@NonNull final View itemView,
                   @NonNull final DAO db,
                   @NonNull final BooklistStyle style) {
            super(itemView);
            mDb = db;

            Context context = itemView.getContext();
            // fetch once and re-use later.
            mName_colon_value = context.getString(R.string.name_colon_value);
            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);
            mShelvesLabel = context.getString(R.string.lbl_bookshelves);
            mLocationLabel = context.getString(R.string.lbl_location);

            mReorderTitle = Prefs.reorderTitleForDisplaying(context);

            // quick & compact value to pass to the background task.
            mExtraFieldsUsed = style.getExtraFieldsStatus();

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
        public void onBindViewHolder(@NonNull final CursorRow cursorRow,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(cursorRow, style);

            String title = cursorRow.getString(DBDefinitions.KEY_TITLE);

            if (mReorderTitle) {
                Context context = mTitleView.getContext();
                String language = cursorRow.getString(DBDefinitions.KEY_LANGUAGE);
                title = LocaleUtils.reorderTitle(context, title,
                                                 LocaleUtils.getLocale(context, language));
            }
            mTitleView.setText(title);

            if (mReadIsUsed && cursorRow.contains(DBDefinitions.KEY_READ)) {
                boolean isSet = cursorRow.getBoolean(DBDefinitions.KEY_READ);
                mReadView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mReadView.setChecked(isSet);
            }

            if (mSignedIsUsed && cursorRow.contains(DBDefinitions.KEY_SIGNED)) {
                boolean isSet = cursorRow.getBoolean(DBDefinitions.KEY_SIGNED);
                mSignedView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mSignedView.setChecked(isSet);
            }

            if (mEditionIsUsed && cursorRow.contains(DBDefinitions.KEY_EDITION_BITMASK)) {
                boolean isSet = (cursorRow.getInt(DBDefinitions.KEY_EDITION_BITMASK)
                                 & Book.EDITION_FIRST) != 0;
                mEditionView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mEditionView.setChecked(isSet);
            }

            if (mLendingIsUsed && cursorRow.contains(DBDefinitions.KEY_LOANEE_AS_BOOLEAN)) {
                boolean isSet = !cursorRow.getBoolean(DBDefinitions.KEY_LOANEE_AS_BOOLEAN);
                mOnLoanView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                mOnLoanView.setChecked(isSet);
            }

            if (mCoverIsUsed && cursorRow.contains(DBDefinitions.KEY_BOOK_UUID)) {
                String uuid = cursorRow.getString(DBDefinitions.KEY_BOOK_UUID);
                // store the uuid for use in the OnClickListener
                mCoverView.setTag(R.id.TAG_UUID, uuid);
                boolean isSet = ImageUtils.setImageView(mCoverView, uuid, 0,
                                                        mMaxCoverSize, mMaxCoverSize);
                if (isSet) {
                    //Allow zooming by clicking on the image
                    mCoverView.setOnClickListener(v -> {
                        FragmentActivity activity = (FragmentActivity) v.getContext();
                        String currentUuid = (String) v.getTag(R.id.TAG_UUID);
                        File file = StorageUtils.getCoverFileForUuid(activity, currentUuid, 0);
                        if (file.exists()) {
                            ZoomedImageDialogFragment.show(activity.getSupportFragmentManager(),
                                                           file);
                        }
                    });
                }
            }

            if (mSeriesIsUsed && cursorRow.contains(DBDefinitions.KEY_BOOK_NUM_IN_SERIES)) {
                String number = cursorRow.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
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

            if (!style.useTaskForExtras()) {
                if (mBookshelfIsUsed && cursorRow.contains(DBDefinitions.KEY_BOOKSHELF_CSV)) {
                    showOrHide(mBookshelvesView,
                               cursorRow.getString(DBDefinitions.KEY_BOOKSHELF_CSV));
                }
                if (mAuthorIsUsed && cursorRow.contains(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                    showOrHide(mAuthorView,
                               cursorRow.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                }
                if (mIsbnIsUsed && cursorRow.contains(DBDefinitions.KEY_ISBN)) {
                    showOrHide(mIsbnView,
                               cursorRow.getString(DBDefinitions.KEY_ISBN));
                }
                if (mFormatIsUsed && cursorRow.contains(DBDefinitions.KEY_FORMAT)) {
                    showOrHide(mFormatView,
                               cursorRow.getString(DBDefinitions.KEY_FORMAT));
                }
                if (mLocationIsUsed && cursorRow.contains(DBDefinitions.KEY_LOCATION)) {
                    showOrHide(mLocationView,
                               cursorRow.getString(DBDefinitions.KEY_LOCATION));
                }
                if ((mPublisherIsUsed && cursorRow.contains(DBDefinitions.KEY_PUBLISHER))
                    || (mPubDateIsUsed && cursorRow.contains(DBDefinitions.KEY_DATE_PUBLISHED))) {
                    showOrHide(mPublisherView,
                               getPublisherAndPubDateText(cursorRow,
                                                          mPublisherIsUsed,
                                                          mPubDateIsUsed));
                }
            } else {
                // Start a background task if there are extras to get.
                // Note that the cover image is already handled in an earlier background task
                if ((mExtraFieldsUsed & BooklistStyle.EXTRAS_BY_TASK) != 0) {
                    // Fill in the extras field as blank initially.
                    mBookshelvesView.setText("");
                    mAuthorView.setText("");
                    mIsbnView.setText("");
                    mFormatView.setText("");
                    mLocationView.setText("");
                    mPublisherView.setText("");

                    // Queue the task.
                    new GetBookExtrasTask(itemView.getContext(), mDb,
                                          cursorRow.getLong(DBDefinitions.KEY_FK_BOOK),
                                          mBookExtrasTaskFinishedListener, mExtraFieldsUsed)
                            .execute();
                }
            }
        }

        /**
         * Temporarily duplication.
         * {@link GetBookExtrasTask#getPublisherAndPubDateText}
         */
        @Nullable
        String getPublisherAndPubDateText(@NonNull final CursorRow cursorRow,
                                          final boolean publisherIsUsed,
                                          final boolean pubDateIsUsed) {
            String tmp = null;
            if (publisherIsUsed) {
                tmp = cursorRow.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if (pubDateIsUsed) {
                tmpPubDate = cursorRow.getString(DBDefinitions.KEY_DATE_PUBLISHED);
            }
            // show combined Publisher and Pub. Date
            if ((tmp != null) && (tmpPubDate != null)) {
                if (tmpPubDate.length() == 4) {
                    // 4 digits is just the year.
                    tmp = String.format(mX_bracket_Y_bracket, tmp, tmpPubDate);
                } else if (tmpPubDate.length() > 4) {
                    // parse/format the date
                    tmp = String.format(mX_bracket_Y_bracket, tmp,
                                        DateUtils.toPrettyDate(tmpPubDate));
                }
            } else if (tmpPubDate != null) {
                // there was no publisher, just use the date
                if (tmpPubDate.length() == 4) {
                    tmp = tmpPubDate;
                } else if (tmpPubDate.length() > 4) {
                    tmp = DateUtils.toPrettyDate(tmpPubDate);
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

        /**
         * Conditionally display 'label: text'.
         */
        private void showOrHide(@NonNull final TextView view,
                                @NonNull final String label,
                                @Nullable final String text) {
            if (text != null && !text.isEmpty()) {
                view.setText(String.format(mName_colon_value, label, text));
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
    public static class GenericStringHolder
            extends RowViewHolder {

        /** Index of related data column. */
        final int mSourceCol;
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
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         * @param noDataId    String id to use when data is blank
         */
        GenericStringHolder(@NonNull final View itemView,
                            final int columnIndex,
                            @StringRes final int noDataId) {
            super(itemView);
            mSourceCol = columnIndex;
            mNoDataId = noDataId;

            mTextView = itemView.findViewById(R.id.name);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorRow cursorRow,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(cursorRow, style);

            setText(cursorRow.getString(mSourceCol),
                    cursorRow.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
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
    public static class RatingHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         */
        RatingHolder(@NonNull final View itemView,
                     final int columnIndex) {
            super(itemView, columnIndex, R.string.hint_empty_rating);
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
                                itemView.getResources().getQuantityString(R.plurals.n_stars, i, i),
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
    public static class LanguageHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         */
        @SuppressWarnings("SameParameterValue")
        LanguageHolder(@NonNull final View itemView,
                       final int columnIndex) {
            super(itemView, columnIndex, R.string.hint_empty_language);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                super.setText(LanguageUtils.getDisplayName(itemView.getContext(), text), level);
            } else {
                super.setText(text, level);
            }
        }
    }

    /**
     * ViewHolder for a row that displays a 'read/unread' (as text) status.
     */
    public static class ReadUnreadHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         */
        ReadUnreadHolder(@NonNull final View itemView,
                         final int columnIndex) {
            super(itemView, columnIndex, R.string.hint_empty_read_status);
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
    public static class MonthHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         */
        MonthHolder(@NonNull final View itemView,
                    final int columnIndex) {
            super(itemView, columnIndex, R.string.hint_empty_month);
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()) {
                try {
                    int i = Integer.parseInt(text);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        super.setText(DateUtils.getMonthName(i, false), level);
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
    public static class SeriesHolder
            extends CheckableStringHolder {

        /** Whether titles should be reordered. */
        private final boolean mReorderTitle;

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         */
        SeriesHolder(@NonNull final View itemView,
                     final int columnIndex) {
            super(itemView, columnIndex, R.string.hint_empty_series,
                  DBDefinitions.KEY_SERIES_IS_COMPLETE);

            mReorderTitle = Prefs.reorderTitleForDisplaying(itemView.getContext());
        }

        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty() && mReorderTitle) {
                // URGENT: translated series are not reordered unless the app runs in that language
                // solution/problem: we would need the Series id (and not just the titel)
                // to call {@link DAO#getSeriesLanguage(long)}
                super.setText(LocaleUtils.reorderTitle(itemView.getContext(), text,
                                                       Locale.getDefault()), level);
            } else {
                super.setText(text, level);
            }
        }
    }

    /**
     * ViewHolder for an Author.
     */
    public static class AuthorHolder
            extends CheckableStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         */
        AuthorHolder(@NonNull final View itemView,
                     final int columnIndex) {
            super(itemView, columnIndex, R.string.hint_empty_author,
                  DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        }
    }

    /**
     * ViewHolder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    public abstract static class CheckableStringHolder
            extends GenericStringHolder {

        /** Column name of related boolean column. */
        private final String mIsLockedSourceCol;

        /**
         * Constructor.
         *
         * @param itemView       the view specific for this holder
         * @param columnIndex    index in SQL result set
         * @param noDataId       String id to use when data is blank
         * @param isLockedSource Column name to use for the boolean 'lock' status
         */
        CheckableStringHolder(@NonNull final View itemView,
                              final int columnIndex,
                              @StringRes final int noDataId,
                              @NonNull final String isLockedSource) {
            super(itemView, columnIndex, noDataId);
            mIsLockedSourceCol = isLockedSource;
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorRow cursorRow,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(cursorRow, style);

            Drawable lock = null;
            if (cursorRow.getBoolean(mIsLockedSourceCol)) {
                lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
            }

            mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null, null, lock, null);
        }
    }
}

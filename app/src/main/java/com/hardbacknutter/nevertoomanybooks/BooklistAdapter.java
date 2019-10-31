/*
 * @Copyright 2019 HardBackNutter
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
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
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
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup.RowKind;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistMappedCursorRow;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.CursorRowProvider;
import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
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
import com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay;

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
        implements FastScrollerOverlay.SectionIndexerV2 {

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
    @Nullable
    private View.OnClickListener mOnItemClick;
    @Nullable
    private View.OnLongClickListener mOnItemLongClick;

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
        mLevelIndent = context.getResources().getDimensionPixelSize(R.dimen.booklist_level_indent);
    }

    void setOnItemClickListener(@NonNull final View.OnClickListener onItemClick) {
        mOnItemClick = onItemClick;
    }

    void setOnItemLongClickListener(@NonNull final View.OnLongClickListener onItemLongClick) {
        mOnItemLongClick = onItemLongClick;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            @RowKind.Kind final int viewType) {

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
            default:
                return new GenericStringHolder(itemView, columnIndex,
                                               R.string.hint_empty_field);
        }
    }

    private View createView(@NonNull final ViewGroup parent,
                            @RowKind.Kind final int viewType) {

        CursorMapper row = ((CursorRowProvider) mCursor).getCursorMapper();
        int level = row.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
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
                    layoutId = R.layout.booksonbookshelf_row_book_3x_large_image;
                    break;

                case ImageUtils.SCALE_X_LARGE:
                    layoutId = R.layout.booksonbookshelf_row_book_2x_large_image;
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
        // tag for the position, so the click-listeners can get it.
        holder.itemView.setTag(R.id.TAG_POSITION, position);
        holder.itemView.setOnClickListener(mOnItemClick);
        holder.itemView.setOnLongClickListener(mOnItemLongClick);

        mCursor.moveToPosition(position);
        CursorMapper row = ((CursorRowProvider) mCursor).getCursorMapper();
        // actual binding depends on the type of row (i.e. holder), so let the holder do it.
        holder.onBindViewHolder(row, mStyle);
    }

    @Override
    @RowKind.Kind
    public int getItemViewType(final int position) {
        mCursor.moveToPosition(position);
        CursorMapper row = ((CursorRowProvider) mCursor).getCursorMapper();
        return row.getInt(DBDefinitions.KEY_BL_NODE_KIND);
    }

    @Override
    public int getItemCount() {
        return mCursor.getCount();
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
     * Get the text to display for the row at the current cursor position.
     * <p>
     * <br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String[] getSectionText(@NonNull final Context context,
                                   final int position) {

        // sanity check.
        if (position < 0 || position >= getItemCount()) {
            return null;
        }

        String[] section;

        // temporary move the cursor to the requested position, restore after we got the text.
        synchronized (this) {
            final int savedPos = mCursor.getPosition();
            mCursor.moveToPosition(position);
            BooklistMappedCursorRow row = ((CursorRowProvider) mCursor).getCursorRow();
            section = row.getLevelText(context);
            mCursor.moveToPosition(savedPos);
        }
        return section;
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

            try (Cursor cursor = mDb.fetchBookExtrasById(mBookId)) {
                // Bail out if we don't have a book.
                if (!cursor.moveToFirst()) {
                    return false;
                }

                CursorMapper mapper = new CursorMapper(cursor);

                String tmp;
                if ((mExtraFields & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, tmp);
                        // no author type for now.
//                        mResults.putInt(DBDefinitions.KEY_AUTHOR_TYPE,
//                                        mapper.getInt(DBDefinitions.KEY_AUTHOR_TYPE));
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_LOCATION);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_LOCATION, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_FORMAT);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_FORMAT, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_BOOKSHELF);
                    if (!tmp.isEmpty()) {
                        // note the destination is KEY_BOOKSHELF_CSV
                        mResults.putString(DBDefinitions.KEY_BOOKSHELF_CSV, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_ISBN) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_ISBN);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_ISBN, tmp);
                    }
                }

                tmp = getPublisherAndPubDateText(mapper);
                if (tmp != null && !tmp.isEmpty()) {
                    mResults.putString(DBDefinitions.KEY_PUBLISHER, tmp);
                }

            } catch (@NonNull final NumberFormatException e) {
                Logger.error(this, e);
                return false;
            }
            return true;
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final CursorMapper mapper) {
            String tmp = null;
            if ((mExtraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                tmp = mapper.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if ((mExtraFields & BooklistStyle.EXTRAS_PUB_DATE) != 0) {
                tmpPubDate = mapper.getString(DBDefinitions.KEY_DATE_PUBLISHED);
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
                    Logger.debug(this, "onPostExecute",
                                 Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
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
     * Base for all row Holder classes.
     */
    abstract static class RowViewHolder
            extends RecyclerView.ViewHolder {

        /** Absolute position of this row. */
        int absolutePosition;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         */
        RowViewHolder(@NonNull final View itemView) {
            super(itemView);
        }

        /**
         * Bind the data to the views in the holder.
         *
         * @param rowData the data to bind
         * @param style   to use
         */
        @CallSuper
        void onBindViewHolder(@NonNull final CursorMapper rowData,
                              @NonNull final BooklistStyle style) {
            absolutePosition = rowData.getInt(DBDefinitions.KEY_BL_ABSOLUTE_POSITION);
        }
    }

    /**
     * Holder for a {@link RowKind#BOOK} row.
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

        /** Based on style; bitmask with the extra fields to use. */
        @BooklistStyle.ExtraOption
        private final int mExtraFieldsUsed;

        /** Book level - Based on style. */
        private final boolean mReadIsUsed;
        /** Book level - Based on style. */
        private final boolean mLoaneeIsUsed;
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
                        // do not re-test usage here. If not in use, we won't have fetched them.
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
            LocaleUtils.insanityCheck(itemView.getContext());
            // fetch once and re-use later.
            mName_colon_value = context.getString(R.string.name_colon_value);
            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);
            mShelvesLabel = context.getString(R.string.lbl_bookshelves);
            mLocationLabel = context.getString(R.string.lbl_location);

            // quick & compact value to pass to the background task.
            mExtraFieldsUsed = style.getExtraFieldsStatus();

            // now predetermine field usage & visibility.

            // always visible
            mTitleView = itemView.findViewById(R.id.title);

            // visibility is independent from actual data, so this is final.
            mReadIsUsed = style.isUsed(DBDefinitions.KEY_READ);
            mReadView = itemView.findViewById(R.id.cbx_read);
            mReadView.setVisibility(mReadIsUsed ? View.VISIBLE : View.GONE);

            // visibility is independent from actual data, so this is final.
            mLoaneeIsUsed = style.isUsed(DBDefinitions.KEY_LOANEE);
            mOnLoanView = itemView.findViewById(R.id.cbx_on_loan);
            mOnLoanView.setVisibility(mLoaneeIsUsed ? View.VISIBLE : View.GONE);

            // visibility is independent from actual data, so this is final.
            mCoverIsUsed = style.isUsed(UniqueId.BKEY_IMAGE);
            mMaxCoverSize = ImageUtils.getMaxImageSize(style.getThumbnailScale());
            mCoverView = itemView.findViewById(R.id.coverImage);
            mCoverView.setVisibility(mCoverIsUsed ? View.VISIBLE : View.GONE);

            // visibility depends on actual data; but if not in use at all, we pre-hide them here.
            mSeriesIsUsed = style.isUsed(DBDefinitions.KEY_SERIES_TITLE);
            mSeriesNumView = itemView.findViewById(R.id.series_num);
            mSeriesNumLongView = itemView.findViewById(R.id.series_num_long);
            // hide by default
            mSeriesNumView.setVisibility(View.GONE);
            mSeriesNumLongView.setVisibility(View.GONE);

            // Lookup all the 'extras' fields and hide them all by default.
            // this prevents white-space showing up and subsequently disappearing.
            mBookshelfIsUsed = style.isUsed(DBDefinitions.KEY_BOOKSHELF_CSV);
            mBookshelvesView = itemView.findViewById(R.id.shelves);
            mBookshelvesView.setVisibility(View.GONE);

            mAuthorIsUsed = style.isUsed(DBDefinitions.KEY_AUTHOR_FORMATTED);
            mAuthorView = itemView.findViewById(R.id.author);
            mAuthorView.setVisibility(View.GONE);
            mIsbnIsUsed = style.isUsed(DBDefinitions.KEY_ISBN);
            mIsbnView = itemView.findViewById(R.id.isbn);
            mIsbnView.setVisibility(View.GONE);
            mFormatIsUsed = style.isUsed(DBDefinitions.KEY_FORMAT);
            mFormatView = itemView.findViewById(R.id.format);
            mFormatView.setVisibility(View.GONE);
            mLocationIsUsed = style.isUsed(DBDefinitions.KEY_LOCATION);
            mLocationView = itemView.findViewById(R.id.location);
            mLocationView.setVisibility(View.GONE);
            mPublisherIsUsed = style.isUsed(DBDefinitions.KEY_PUBLISHER);
            mPubDateIsUsed = style.isUsed(DBDefinitions.KEY_DATE_PUBLISHED);
            mPublisherView = itemView.findViewById(R.id.publisher);
            mPublisherView.setVisibility(View.GONE);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            String title = rowData.getString(DBDefinitions.KEY_TITLE);
            if (Prefs.reorderTitleForDisplaying(App.getAppContext())) {
                String language = rowData.getString(DBDefinitions.KEY_LANGUAGE);
                title = LocaleUtils.reorderTitle(App.getLocalizedAppContext(), title,
                                                 LocaleUtils.getLocale(language));
            }
            mTitleView.setText(title);

            if (mReadIsUsed && rowData.contains(DBDefinitions.KEY_READ)) {
                mReadView.setChecked(rowData.getInt(DBDefinitions.KEY_READ) != 0);
            }

            if (mLoaneeIsUsed && rowData.contains(DBDefinitions.KEY_LOANEE_AS_BOOLEAN)) {
                mOnLoanView.setChecked(!rowData.getBoolean(DBDefinitions.KEY_LOANEE_AS_BOOLEAN));
            }

            if (mCoverIsUsed && rowData.contains(DBDefinitions.KEY_BOOK_UUID)) {
                // store the uuid for use in the onClick
                mCoverView.setTag(R.id.TAG_UUID, rowData.getString(DBDefinitions.KEY_BOOK_UUID));

                String uuid = rowData.getString(DBDefinitions.KEY_BOOK_UUID);
                boolean isSet = ImageUtils.setImageView(mCoverView, uuid,
                                                        mMaxCoverSize, mMaxCoverSize);
                if (isSet) {
                    //Allow zooming by clicking on the image
                    mCoverView.setOnClickListener(v -> {
                        FragmentActivity activity = (FragmentActivity) v.getContext();
                        String currentUuid = (String) v.getTag(R.id.TAG_UUID);
                        ZoomedImageDialogFragment.show(activity.getSupportFragmentManager(),
                                                       StorageUtils
                                                               .getCoverFileForUuid(currentUuid));
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

            if (!style.useTaskForExtras()) {
                if (mBookshelfIsUsed && rowData.contains(DBDefinitions.KEY_BOOKSHELF_CSV)) {
                    showOrHide(mBookshelvesView,
                               rowData.getString(DBDefinitions.KEY_BOOKSHELF_CSV));
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
                    showOrHide(mPublisherView, getPublisherAndPubDateText(style, rowData));
                }
            } else {
                // Start a background task if there are extras to get.
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
                                          rowData.getLong(DBDefinitions.KEY_FK_BOOK),
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
        String getPublisherAndPubDateText(@NonNull final BooklistStyle style,
                                          @NonNull final CursorMapper mapper) {
            String tmp = null;
            if (style.isUsed(DBDefinitions.KEY_PUBLISHER)) {
                tmp = mapper.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if (style.isUsed(DBDefinitions.KEY_DATE_PUBLISHED)) {
                tmpPubDate = mapper.getString(DBDefinitions.KEY_DATE_PUBLISHED);
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
     * Holder to handle any field that can be displayed as a simple string.
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
        /** Pointer to the container of all info for this row. */
        @NonNull
        private final View mRowDetailsView;
        /**
         * (optional) Pointer to the constraint group that controls visibility of all widgets
         * inside a ViewGroup. Used with ConstraintLayout only.
         */
        @Nullable
        private final View mVisibilityControlView;

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

            mRowDetailsView = itemView.findViewById(R.id.BLB_ROW_DETAILS);
            mTextView = itemView.findViewById(R.id.name);
            // optional
            mVisibilityControlView = mRowDetailsView.findViewById(R.id.group);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            setText(rowData.getString(mSourceCol),
                    rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
        }

        /**
         * Syntax sugar.
         *
         * @return {@code true} if the details view is visible.
         */
        public boolean isVisible() {
            return mRowDetailsView.getVisibility() == View.VISIBLE;
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
            // we used to hide levels > 1 when they had no text.
            // We're always displaying them now... but maybe this will change later.
            int visibility = View.VISIBLE;

            if (text != null && !text.isEmpty()) {
                // if we have text, show it.
                mTextView.setText(text);
            } else {
                mTextView.setText(mNoDataId);
            }

            mRowDetailsView.setVisibility(visibility);

            /*
                this is really annoying: setting visibility of the ConstraintLayout to GONE
                does NOT shrink it to size zero. You're forced to set all widgets inside also.
                Potentially this could be solved by fiddling with the constraints more.
            */
            if (mVisibilityControlView != null) {
                mVisibilityControlView.setVisibility(visibility);
            }
        }
    }

    /**
     * Holder for a row that displays a 'rating'.
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
                    Logger.error(this, e);
                }
            }

            super.setText(text, level);
        }
    }

    /**
     * Holder for a row that displays a 'language'.
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
                super.setText(LanguageUtils.getDisplayName(text), level);
            } else {
                super.setText(text, level);
            }
        }
    }

    /**
     * Holder for a row that displays a 'read/unread' (as text) status.
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
     * Holder for a row that displays a 'month'.
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
                    Logger.error(this, e);
                }
            }
            super.setText(text, level);
        }
    }

    /**
     * Holder for a Series.
     */
    public static class SeriesHolder
            extends CheckableStringHolder {

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
        }

        /**
         * @param text  String to display; can be {@code null} or empty
         * @param level for this row
         */
        @Override
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            if (text != null && !text.isEmpty()
                && Prefs.reorderTitleForDisplaying(App.getAppContext())) {
                // URGENT: translated series are not reordered unless the app runs in that language
                // solution/problem: we would need the Series id (and not just the titel)
                // to call {@link DAO#getSeriesLanguage(long)}
                super.setText(LocaleUtils.reorderTitle(App.getLocalizedAppContext(), text,
                                                       Locale.getDefault()), level);
            } else {
                super.setText(text, level);
            }
        }
    }

    /**
     * Holder for an Author.
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
     * Holder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
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
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            Drawable lock = null;
            if (isVisible() && rowData.getBoolean(mIsLockedSourceCol)) {
                lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
            }

            mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null, null, lock, null);
        }
    }
}

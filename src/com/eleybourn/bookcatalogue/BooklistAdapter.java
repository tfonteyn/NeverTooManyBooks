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

package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
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
import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKind;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistSupportProvider;
import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.ZoomedImageDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.widgets.FastScrollerOverlay;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PUBLISHER;

/**
 * Handles all views in a multi-type list showing books, authors, series etc.
 * <p>
 * Each row(level) needs to have a layout like:
 * <layout id="@id/ROW_INFO">
 * <TextView id="@id/name" />
 * ... more fields...
 * </layout>
 * <p>
 * ROW_INFO is important, as it's that one that gets shown/hidden when needed.
 *
 * @author Philip Warner
 */
public class BooklistAdapter
        extends RecyclerView.Adapter<BooklistAdapter.RowViewHolder>
        implements FastScrollerOverlay.SectionIndexerV2 {

    /** The padding indent (in pixels) added for each level: padding = (level-1) * mLevelIndent. */
    private final int mLevelIndent;

    private final AtomicInteger debugNewViewCounter = new AtomicInteger();
    private final AtomicInteger debugBindViewCounter = new AtomicInteger();

    /** Database access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final Cursor mCursor;

    @NonNull
    private final BooklistStyle mStyle;

    @NonNull
    private final LayoutInflater mInflater;

    @Nullable
    private View.OnClickListener mOnItemClick;
    @Nullable
    private View.OnLongClickListener mOnItemLongClick;

    /**
     * Constructor.
     *
     * @param db    the database
     * @param style The style is used by (some) individual rows.
     */
    public BooklistAdapter(@NonNull final LayoutInflater layoutInflater,
                           @NonNull final DAO db,
                           @NonNull final Cursor cursor,
                           @NonNull final BooklistStyle style) {
        mDb = db;
        mCursor = cursor;
        mStyle = style;
        mInflater = layoutInflater;

        mLevelIndent = layoutInflater.getContext().getResources()
                                     .getDimensionPixelSize(R.dimen.booklist_level_indent);
    }

    void setOnItemClickListener(@NonNull final View.OnClickListener onItemClick) {
        mOnItemClick = onItemClick;
    }

    void setOnItemLongClickListener(@NonNull final View.OnLongClickListener onItemLongClick) {
        mOnItemLongClick = onItemLongClick;
    }

    public int getItemViewType(final int position) {
        mCursor.moveToPosition(position);
        BooklistCursorRow row = ((BooklistSupportProvider) mCursor).getCursorRow();
        return row.getRowKind();
    }

    public int getItemCount() {
        return mCursor.getCount();
    }

    @NonNull
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            final int viewType) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECYCLER_VIEW_IS_RECYCLING) {
            debugNewViewCounter.incrementAndGet();
            Logger.debug(this, "onCreateViewHolder",
                         "debugNewViewCounter=" + debugNewViewCounter.get(),
                         "viewType=" + viewType);
        }

        BooklistCursorRow row = ((BooklistSupportProvider) mCursor).getCursorRow();

        // The view depends on the viewType + level.
        View view = createView(parent, viewType, row.getLevel());

        // do holder type dependent init.
        return createHolder(viewType, view, row);
    }

    private View createView(@NonNull final ViewGroup parent,
                            final int viewType,
                            final int level) {
        @LayoutRes
        int layoutId;

        // Indent (0..) based on level (1..)
        // we assume rows of a given type only occur at the same level
        int indent = level - 1;

        // A Book occurs always at the lowest level regardless of the groups in the style.
        if (viewType == RowKind.BOOK) {
            layoutId = R.layout.booksonbookshelf_row_book;
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
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                        Logger.debug(this, "getLevelLayoutId",
                                     "level=" + level);
                    }
                    // level 3 and higher all use the same layout.
                    layoutId = R.layout.booksonbookshelf_row_level_3;
            }
        }

        View view = mInflater.inflate(layoutId, parent, false);

        view.setPadding(indent * mLevelIndent, 0, 0, 0);

        // Scale text if required
        float scale = mStyle.getScaleFactor();
        if (scale != 1.0f) {
            scaleTextViews(scale, view);
        }
        return view;
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
     * @param scale to use, with 1.0f no scaling.
     * @param root  the view (and its children) we'll scale
     */
    private void scaleTextViews(final float scale,
                                @NonNull final View root) {

        // text gets scaled
        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            float px = textView.getTextSize();
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scale);
        }

        // all elements get scaled padding.
        root.setPadding((int) (scale * root.getPaddingLeft()),
                        (int) (scale * root.getPaddingTop()),
                        (int) (scale * root.getPaddingRight()),
                        (int) (scale * root.getPaddingBottom()));

        // go recursive if needed
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                scaleTextViews(scale, viewGroup.getChildAt(i));
            }
        }
    }

    /**
     * Return a 'Holder' object for the row pointed to by row.
     *
     * @return the holder
     */
    private RowViewHolder createHolder(final int viewType,
                                       @NonNull final View itemView,
                                       @NonNull final BooklistCursorRow row) {

        // a BookHolder is based on multiple columns, the holder itself will sort them out.
        if (viewType == RowKind.BOOK) {
            return new BookHolder(itemView, mDb, mStyle);
        }

        // other rows are based on a single column
        // (except CheckableStringHolder which uses an additional fixed column).
        String columnName = RowKind.get(viewType).getDisplayDomain().name;
        int columnIndex = row.getColumnIndex(columnName);
        if (columnIndex < 0) {
            throw new ColumnNotPresentException(columnName);
        }

        switch (viewType) {
            // NEWKIND: ROW_KIND_x

            case RowKind.AUTHOR:
                return new CheckableStringHolder(itemView, columnIndex,
                                                 DBDefinitions.DOM_AUTHOR_IS_COMPLETE.name,
                                                 R.string.field_not_set_with_brackets);

            case RowKind.SERIES:
                return new CheckableStringHolder(itemView, columnIndex,
                                                 DBDefinitions.DOM_SERIES_IS_COMPLETE.name,
                                                 R.string.field_not_set_with_brackets);

            // Months are displayed by name
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUBLICATION_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(itemView, columnIndex, R.string.field_not_set_with_brackets);

            // some special formatting holders
            case RowKind.RATING:
                return new RatingHolder(itemView, columnIndex,
                                        R.string.field_not_set_with_brackets);
            case RowKind.LANGUAGE:
                return new LanguageHolder(itemView, columnIndex,
                                          R.string.field_not_set_with_brackets);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(itemView, columnIndex,
                                            R.string.field_not_set_with_brackets);

            // plain old Strings
//            case RowKind.TITLE_LETTER:
//            case RowKind.PUBLISHER:
//            case RowKind.GENRE:
//            case RowKind.FORMAT:
//            case RowKind.LOCATION:
//            case RowKind.LOANED:
//            case RowKind.BOOKSHELF:
//            case RowKind.DATE_PUBLISHED_YEAR:
//            case RowKind.DATE_FIRST_PUBLICATION_YEAR:
//            case RowKind.DATE_ACQUIRED_YEAR:
//            case RowKind.DATE_ACQUIRED_DAY:
//            case RowKind.DATE_ADDED_YEAR:
//            case RowKind.DATE_ADDED_DAY:
//            case RowKind.DATE_READ_YEAR:
//            case RowKind.DATE_READ_DAY:
//            case RowKind.DATE_LAST_UPDATE_YEAR:
//            case RowKind.DATE_LAST_UPDATE_DAY:
            default:
                return new GenericStringHolder(itemView, columnIndex,
                                               R.string.field_not_set_with_brackets);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECYCLER_VIEW_IS_RECYCLING) {
            debugBindViewCounter.incrementAndGet();
            Logger.debug(this, "onBindViewHolder",
                         "debugBindViewCounter=" + debugBindViewCounter.get());
        }

        // tag for the position, so the click-listeners can get it.
        holder.itemView.setTag(R.id.TAG_POSITION, position);
        holder.itemView.setOnClickListener(mOnItemClick);
        holder.itemView.setOnLongClickListener(mOnItemLongClick);

        // position the data we need to bind.
        mCursor.moveToPosition(position);
        BooklistCursorRow row = ((BooklistSupportProvider) mCursor).getCursorRow();
        // actual binding depends on the type of row (i.e. holder), so let the holder do it.
        holder.onBindViewHolder(row);
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
        synchronized (mCursor) {
            final int savedPos = mCursor.getPosition();
            mCursor.moveToPosition(position);
            BooklistCursorRow row = ((BooklistSupportProvider) mCursor).getCursorRow();
            section = row.getLevelText(context);
            mCursor.moveToPosition(savedPos);
        }
        return section;
    }

    /**
     * TOMF: this really needs performance testing. Can we not simply add the columns to the real cursor?
     * <p>
     * Background task to get 'extra' details for a book row.
     * Doing this in a background task keeps the booklist cursor simple and small.
     * Used by {@link BookHolder}.
     */
    private static class GetBookExtrasTask
            extends AsyncTask<Void, Void, Boolean> {

        /** Format string (not allowed to cache the context, so get in constructor). */
        @NonNull
        private final String mA_bracket_b_bracket;

        /** The listener for the tasks result. */
        @NonNull
        private final WeakReference<GetBookExtrasTaskFinishedListener> mTaskListener;

        /** Database access. */
        @NonNull
        private final DAO mDb;

        /** Locale to use for formatting. */
        @NonNull
        private final Locale mLocale;

        /** The book ID to fetch. */
        private final long mBookId;

        private final int mExtraFields;

        /** Resulting location data. */
        private String mLocation;
        /** Resulting publisher data. */
        private String mPublisher;
        /** Resulting Format data. */
        private String mFormat;
        /** Resulting author data. */
        private String mAuthor;
        /** Resulting shelves data. */
        private String mShelves;

        /**
         * Constructor.
         *
         * @param bookId       Book to fetch
         * @param taskListener View holder for the book, used as callback for task results.
         */
        @UiThread
        GetBookExtrasTask(@NonNull final Resources resources,
                          @NonNull final DAO db,
                          final long bookId,
                          @NonNull final GetBookExtrasTaskFinishedListener taskListener,
                          @NonNull final BooklistStyle style) {

            mLocale = resources.getConfiguration().locale;
            mDb = db;
            mBookId = bookId;
            mTaskListener = new WeakReference<>(taskListener);
            mExtraFields = style.getExtraFieldsStatus();

            mA_bracket_b_bracket = resources.getString(R.string.a_bracket_b_bracket);
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookExtrasTask " + mBookId);

            try (Cursor cursor = mDb.fetchBookExtrasById(mBookId, mExtraFields)) {
                // Bail out if we don't have a book.
                if (!cursor.moveToFirst()) {
                    return false;
                }

                ColumnMapper mapper = new ColumnMapper(cursor, null,
                                                       DOM_AUTHOR_FORMATTED,
                                                       DOM_BOOK_LOCATION,
                                                       DOM_BOOK_FORMAT,
                                                       DOM_BOOK_PUBLISHER,
                                                       DOM_BOOK_DATE_PUBLISHED,
                                                       DOM_BOOKSHELF);

                if ((mExtraFields & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    mAuthor = mapper.getString(DOM_AUTHOR_FORMATTED);
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    mLocation = mapper.getString(DOM_BOOK_LOCATION);
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    mFormat = mapper.getString(DOM_BOOK_FORMAT);
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    mShelves = mapper.getString(DOM_BOOKSHELF);
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                    mPublisher = mapper.getString(DOM_BOOK_PUBLISHER);
                    String tmpPubDate = mapper.getString(DOM_BOOK_DATE_PUBLISHED);
                    // over optimisation ?
                    if (tmpPubDate.length() == 4) {
                        // 4 digits is just the year.
                        mPublisher = String.format(mA_bracket_b_bracket, mPublisher, tmpPubDate);
                    } else if (tmpPubDate.length() > 4) {
                        // parse/format the date
                        mPublisher = String.format(mA_bracket_b_bracket, mPublisher,
                                                   DateUtils.toPrettyDate(mLocale, tmpPubDate));
                    }
                }
            } catch (NumberFormatException e) {
                Logger.error(this, e);
                return false;
            }
            return true;
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Boolean result) {
            if (!result) {
                return;
            }
            // Fields not used will be null.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetBookExtrasTaskFinished(mAuthor, mPublisher,
                                                                mFormat, mShelves, mLocation);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }

        interface GetBookExtrasTaskFinishedListener {

            /**
             * Results from fetching the extras. Fields not used/fetched will be {@code null}.
             * Theoretically they could also be {@code null} when fetched (but shouldn't).
             */
            void onGetBookExtrasTaskFinished(@Nullable String author,
                                             @Nullable String publisher,
                                             @Nullable String format,
                                             @Nullable String shelves,
                                             @Nullable String location);
        }
    }

    /**
     * Base for all row 'holder' classes.
     */
    public abstract static class RowViewHolder
            extends RecyclerView.ViewHolder {

        /** Absolute position of this row. */
        int absolutePosition;

        RowViewHolder(@NonNull final View itemView) {
            super(itemView);
        }

        @CallSuper
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            absolutePosition = rowData.getAbsolutePosition();
        }
    }

    /**
     * Holder for a {@link RowKind#BOOK} row.
     */
    public static class BookHolder
            extends RowViewHolder {

        /** Database access. */
        @NonNull
        private final DAO mDb;

        @NonNull
        private final BooklistStyle mStyle;
        /** Bookshelves label resource string. */
        @NonNull
        private final String mShelvesLabel;
        /** Location label resource string. */
        @NonNull
        private final String mLocationLabel;
        /** Format string. */
        @NonNull
        private final String mName_colon_value;

        /** Pointer to the view that stores the related book field. */
        TextView titleView;
        /** Pointer to the view that stores the related book field. */
        TextView authorView;
        /** Pointer to the view that stores the related book field. */
        TextView bookshelvesView;
        /** Pointer to the view that stores the related book field. */
        TextView locationView;
        /** Pointer to the view that stores the related book field. */
        TextView publisherView;
        /** Pointer to the view that stores the related book field. */
        TextView formatView;
        private final GetBookExtrasTask.GetBookExtrasTaskFinishedListener mTaskListener =
                new GetBookExtrasTask.GetBookExtrasTaskFinishedListener() {
                    @Override
                    public void onGetBookExtrasTaskFinished(@Nullable final String author,
                                                            @Nullable final String publisher,
                                                            @Nullable final String format,
                                                            @Nullable final String shelves,
                                                            @Nullable final String location) {
                        showOrHide(authorView, author);
                        showOrHide(publisherView, publisher);
                        showOrHide(formatView, format);
                        showOrHide(bookshelvesView, mShelvesLabel, shelves);
                        showOrHide(locationView, mLocationLabel, location);
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
                };
        /** Pointer to the view that stores the series number when it is a short piece of text. */
        TextView seriesNumView;
        /** Pointer to the view that stores the series number when it is a long piece of text. */
        TextView seriesNumLongView;
        /** The "I've read it" checkbox. */
        CompoundButton readView;
        /** Pointer to the view that stores the related book field. */
        private ImageView coverView;

        /**
         * Constructor.
         *
         * @param db the database.
         */
        BookHolder(@NonNull final View itemView,
                   @NonNull final DAO db,
                   @NonNull final BooklistStyle style) {
            super(itemView);
            mDb = db;
            mStyle = style;

            Resources resources = itemView.getResources();
            // fetch once and re-use later.
            mName_colon_value = resources.getString(R.string.name_colon_value);
            mShelvesLabel = resources.getString(R.string.lbl_bookshelves);
            mLocationLabel = resources.getString(R.string.lbl_location);

            // always visible
            titleView = itemView.findViewById(R.id.title);

            // visibility is independent from actual data, so set here.
            readView = itemView.findViewById(R.id.read);
            readView.setVisibility(App.isUsed(DBDefinitions.KEY_READ) ? View.VISIBLE
                                                                      : View.GONE);

            int extraFields = mStyle.getExtraFieldsStatus();

            // visibility is independent from actual data, so set here.
            coverView = itemView.findViewById(R.id.coverImage);
            if (App.isUsed(UniqueId.BKEY_COVER_IMAGE)
                    && (extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                coverView.setVisibility(View.VISIBLE);
            } else {
                coverView.setVisibility(View.GONE);
            }

            // visibility depends on actual data
            seriesNumView = itemView.findViewById(R.id.series_num);
            seriesNumLongView = itemView.findViewById(R.id.series_num_long);

            // visibility depends on actual data, but we need to set initial visibility
            // as actual visibility is postponed to a background task.
            //
            // iow: they are (and stay) hidden when not in use;
            // and we'll hide the used ones if empty.
            bookshelvesView = itemView.findViewById(R.id.shelves);
            bookshelvesView.setVisibility((extraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0
                                          ? View.VISIBLE : View.GONE);

            authorView = itemView.findViewById(R.id.author);
            authorView.setVisibility((extraFields & BooklistStyle.EXTRAS_AUTHOR) != 0
                                     ? View.VISIBLE : View.GONE);

            locationView = itemView.findViewById(R.id.location);
            locationView.setVisibility((extraFields & BooklistStyle.EXTRAS_LOCATION) != 0
                                       ? View.VISIBLE : View.GONE);

            publisherView = itemView.findViewById(R.id.publisher);
            publisherView.setVisibility((extraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0
                                        ? View.VISIBLE : View.GONE);

            formatView = itemView.findViewById(R.id.format);
            formatView.setVisibility((extraFields & BooklistStyle.EXTRAS_FORMAT) != 0
                                     ? View.VISIBLE : View.GONE);

        }

        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            int extraFields = mStyle.getExtraFieldsStatus();
            final int imageMaxSize = mStyle.getScaledCoverImageMaxSize(itemView.getContext());

            // Title
            titleView.setText(rowData.getTitle());

            // Read
            if (App.isUsed(DBDefinitions.KEY_READ)) {
                readView.setChecked(rowData.isRead());
            }

            // Series number
            if (App.isUsed(DBDefinitions.KEY_SERIES)
                    && rowData.hasSeriesNumber()) {

                String number = rowData.getSeriesNumber();
                if (number != null && !number.isEmpty()) {
                    // Display it in one of the views, based on the size of the text.
                    if (number.length() > 4) {
                        seriesNumView.setVisibility(View.GONE);
                        seriesNumLongView.setText(number);
                        seriesNumLongView.setVisibility(View.VISIBLE);
                    } else {
                        seriesNumView.setText(number);
                        seriesNumView.setVisibility(View.VISIBLE);
                        seriesNumLongView.setVisibility(View.GONE);
                    }
                } else {
                    seriesNumView.setVisibility(View.GONE);
                    seriesNumLongView.setVisibility(View.GONE);
                }
            } else {
                seriesNumView.setVisibility(View.GONE);
                seriesNumLongView.setVisibility(View.GONE);
            }

            if (App.isUsed(UniqueId.BKEY_COVER_IMAGE)
                    && (extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                // store the uuid for use in the onClick
                coverView.setTag(R.id.TAG_UUID, rowData.getBookUuid());

                ImageUtils.setImageView(coverView, rowData.getBookUuid(),
                                        imageMaxSize, imageMaxSize);

                //Allow zooming by clicking on the image
                coverView.setOnClickListener(v -> {
                    FragmentActivity activity = (FragmentActivity) v.getContext();
                    String uuid = (String) v.getTag(R.id.TAG_UUID);
                    ZoomedImageDialogFragment.show(activity.getSupportFragmentManager(),
                                                   StorageUtils.getCoverFile(uuid));
                });
            }

            // If there are extras to get, start a background task.
            if ((extraFields & BooklistStyle.EXTRAS_LOWER16) != 0) {
                // Fill in the extras field as blank initially.
                bookshelvesView.setText("");
                locationView.setText("");
                publisherView.setText("");
                formatView.setText("");
                authorView.setText("");
                // Queue the task.
                new GetBookExtrasTask(itemView.getContext().getResources(),
                                      mDb, rowData.getBookId(), mTaskListener, mStyle)
                        .execute();
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
        /** String ID to use when data is blank. */
        @StringRes
        final int mNoDataId;

        /*** View to populate. */
        @NonNull
        TextView mTextView;
        /** Pointer to the container of all info for this row. */
        @NonNull
        private View mRowDetailsView;
        /**
         * (optional) Pointer to the constraint group that controls visibility of all widgets
         * inside a ViewGroup. Used with ConstraintLayout only.
         */
        @Nullable
        private View mVisibilityControlView;

        /**
         * Constructor.
         *
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
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

        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            setText(rowData.getString(mSourceCol), rowData.getLevel());
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
         * For a simple row, just set the text (or hide it).
         *
         * @param textId String to display
         * @param level  for this row
         */
        public void setText(@StringRes final int textId,
                            @IntRange(from = 1) final int level) {
            setText(mTextView.getContext().getString(textId), level);
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * @param text  String to display; can be {@code null} or empty
         * @param level for this row
         */
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            int visibility = View.VISIBLE;

            if (text != null && !text.isEmpty()) {
                // if we have text, show it.
                mTextView.setText(text);
            } else {
                // we don't have text, but...
                if (level == 1) {
                    // we never hide level 1 and show the place holder text instead.
                    mTextView.setText(mNoDataId);
                } else {
                    visibility = View.GONE;
                }
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
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        RatingHolder(@NonNull final View itemView,
                     final int columnIndex,
                     @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            String s = rowData.getString(mSourceCol);
            if (s != null) {
                try {
                    int i = (int) Float.parseFloat(s);
                    // If valid, format the description
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        s = itemView.getResources().getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(this, e);
                }
            }
            setText(s, rowData.getLevel());
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
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        LanguageHolder(@NonNull final View itemView,
                       final int columnIndex,
                       @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            String s = rowData.getString(mSourceCol);
            if (s != null && !s.isEmpty()) {
                s = LocaleUtils.getDisplayName(itemView.getContext(), s);
            }
            setText(s, rowData.getLevel());
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
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        ReadUnreadHolder(@NonNull final View itemView,
                         final int columnIndex,
                         @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            if (Datum.toBoolean(rowData.getString(mSourceCol), true)) {
                setText(R.string.lbl_read, rowData.getLevel());
            } else {
                setText(R.string.lbl_unread, rowData.getLevel());
            }
        }
    }

    /**
     * Holder for a row that displays a 'month'.
     * This code turns a month number into a locale-based month name.
     */
    public static class MonthHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        MonthHolder(@NonNull final View itemView,
                    final int columnIndex,
                    @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            String s = rowData.getString(mSourceCol);
            if (s != null) {
                Locale locale = LocaleUtils.from(itemView.getContext());
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        s = DateUtils.getMonthName(locale, i, false);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(this, e);
                }
            }
            setText(s, rowData.getLevel());
        }
    }

    /**
     * Holder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    public static class CheckableStringHolder
            extends GenericStringHolder {

        /** Column name of related boolean column. */
        private final String mIsLockedSourceCol;

        /**
         * Constructor.
         *
         * @param columnIndex    index in SQL result set
         * @param isLockedSource Column name to use for the boolean 'lock' status
         * @param noDataId       String ID to use when data is blank
         */
        CheckableStringHolder(@NonNull final View itemView,
                              final int columnIndex,
                              @NonNull final String isLockedSource,
                              @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
            mIsLockedSourceCol = isLockedSource;
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData) {
            super.onBindViewHolder(rowData);

            if (isVisible()) {
                Drawable lock = null;
                if (rowData.getBoolean(mIsLockedSourceCol)) {
                    lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
                    //noinspection ConstantConditions
                    lock.setTint(App.getAttr(R.attr.completed_icon_tint));
                }
                mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        null, null, lock, null);

            } else {
                mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        null, null, null, null);
            }
        }
    }
}

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

import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.Locale;

import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListRowHolder;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
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
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

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
public class BooksMultiTypeListHandler
        implements MultiTypeListCursorAdapter.MultiTypeListHandler {

    /** Database access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final BooklistStyle mStyle;

    @NonNull
    private final LayoutInflater mInflater;

    /**
     * Constructor.
     *
     * @param db the database
     */
    public BooksMultiTypeListHandler(@NonNull final LayoutInflater layoutInflater,
                                     @NonNull final DAO db,
                                     @NonNull final BooklistStyle style) {
        mDb = db;
        mStyle = style;
        mInflater = layoutInflater;
    }

    @Override
    public int getViewTypeCount() {
        return BooklistGroup.RowKind.size();
    }

    @Override
    public int getItemViewType(@NonNull final Cursor cursor) {
        BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();
        return row.getRowKind();
    }

    @NonNull
    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();

        RowViewHolder holder = createHolder(context.getResources(), row);
        View view = holder.onCreateView(row, mInflater, parent);
        holder.onCreateViewHolder(row, view);
        view.setTag(R.id.TAG_VIEW_HOLDER, holder);

        // Indent based on level; we assume rows of a given type only occur at the same level
        view.setPadding((row.getLevel() - 1) * 5, 0, 0, 0);

        // Scale if necessary
        float scale = mStyle.getScale();
        if (scale != 1.0f) {
            scaleView(scale, view);
        }

        return view;
    }

    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final Cursor cursor) {

        final BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();
        final RowViewHolder holder = (RowViewHolder) view.getTag(R.id.TAG_VIEW_HOLDER);
        holder.absolutePosition = row.getAbsolutePosition();
        holder.onBindViewHolder(row, view);
    }

    /**
     * @return the *absolute* position of the passed view in the list of books.
     */
    public int getAbsolutePosition(@NonNull final View view) {
        final RowViewHolder holder = (RowViewHolder) view.getTag(R.id.TAG_VIEW_HOLDER);
        return holder.absolutePosition;
    }

    /**
     * Get the text to display for the row at the current cursor position.
     * <p>
     * called by {@link MultiTypeListCursorAdapter#getSectionTextForPosition(int)}}
     * <p>
     * <br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String[] getSectionText(@NonNull final Resources resources,
                                   @NonNull final Cursor cursor) {
        BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();
        return new String[]{row.getLevelText(resources, 1),
                            row.getLevelText(resources, 2)};
    }


    /**
     * Scale text in a View (and children) as per user preferences.
     *
     * @param scale to use, with 1.0f no scaling.
     * @param root  the view (and its children) we'll scale
     */
    private void scaleView(final float scale,
                           @NonNull final View root) {

        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            float px = textView.getTextSize();
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scale);

        }
//        else if (root instanceof ImageView) {
        // experiments from the original code never worked.
        // Bottom line is that Android will scale *down* (i.e. image to big ? make it smaller)
        // but will NOT scale up to fill the provided space. This means scaling needs to be done
        // at bind time, not at create time of the view. Which in turn means we might as well
        // do the scaling at the time of actual population of the view.
//        }

        root.setPadding((int) (scale * root.getPaddingLeft()),
                        (int) (scale * root.getPaddingTop()),
                        (int) (scale * root.getPaddingRight()),
                        (int) (scale * root.getPaddingBottom()));

        // go recursive if needed.
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                scaleView(scale, viewGroup.getChildAt(i));
            }
        }
    }

    /**
     * Return a 'Holder' object for the row pointed to by row.
     *
     * @return the holder
     */
    private RowViewHolder createHolder(@NonNull final Resources resources,
                                       @NonNull final BooklistCursorRow row) {

        RowKind rowKind = RowKind.get(row.getRowKind());
        int rowKindId = rowKind.getKind();

        // a BookHolder is based on multiple columns, the holder itself will sort them out.
        if (rowKindId == RowKind.BOOK) {
            return new BookHolder(resources, mDb, mStyle);
        }

        // other rows are based on a single column (except CheckableStringHolder).
        String columnName = rowKind.getDisplayDomain().name;
        int columnIndex = row.getColumnIndex(columnName);
        if (columnIndex < 0) {
            throw new ColumnNotPresentException(columnName);
        }

        switch (rowKindId) {
            // NEWKIND: ROW_KIND_x

            case RowKind.AUTHOR:
                return new CheckableStringHolder(columnIndex,
                                                 DBDefinitions.DOM_AUTHOR_IS_COMPLETE.name,
                                                 R.string.field_not_set_with_brackets);

            case RowKind.SERIES:
                return new CheckableStringHolder(columnIndex,
                                                 DBDefinitions.DOM_SERIES_IS_COMPLETE.name,
                                                 R.string.field_not_set_with_brackets);

            // plain old Strings
            case RowKind.TITLE_LETTER:
            case RowKind.PUBLISHER:
            case RowKind.GENRE:
            case RowKind.FORMAT:
            case RowKind.LOCATION:
            case RowKind.LOANED:
            case RowKind.BOOKSHELF:
            case RowKind.DATE_PUBLISHED_YEAR:
            case RowKind.DATE_FIRST_PUBLICATION_YEAR:
            case RowKind.DATE_ACQUIRED_YEAR:
            case RowKind.DATE_ACQUIRED_DAY:
            case RowKind.DATE_ADDED_YEAR:
            case RowKind.DATE_ADDED_DAY:
            case RowKind.DATE_READ_YEAR:
            case RowKind.DATE_READ_DAY:
            case RowKind.DATE_LAST_UPDATE_YEAR:
            case RowKind.DATE_LAST_UPDATE_DAY:
                return new GenericStringHolder(columnIndex, R.string.field_not_set_with_brackets);

            // Months are displayed by name
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUBLICATION_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(columnIndex, R.string.field_not_set_with_brackets);

            // some special formatting holders
            case RowKind.RATING:
                return new RatingHolder(columnIndex, R.string.field_not_set_with_brackets);
            case RowKind.LANGUAGE:
                return new LanguageHolder(columnIndex, R.string.field_not_set_with_brackets);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(columnIndex, R.string.field_not_set_with_brackets);

            default:
                throw new IllegalTypeException(String.valueOf(rowKindId));
        }
    }

    /**
     * TOMF: this really needs performance testing
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

        @NonNull
        private final BooklistStyle mStyle;
        private final boolean mWithBookshelves;
        private final boolean mWithLocation;
        private final boolean mWithFormat;
        private final boolean mWithAuthor;
        private final boolean mWithPublisher;

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
            mStyle = style;

            mWithBookshelves = mStyle.getExtraField(BooklistStyle.EXTRAS_BOOKSHELVES).isRequested();
            mWithLocation = mStyle.getExtraField(BooklistStyle.EXTRAS_LOCATION).isRequested();
            mWithFormat = mStyle.getExtraField(BooklistStyle.EXTRAS_FORMAT).isRequested();
            mWithAuthor = mStyle.getExtraField(BooklistStyle.EXTRAS_AUTHOR).isRequested();
            mWithPublisher = mStyle.getExtraField(BooklistStyle.EXTRAS_PUBLISHER).isRequested();

            mA_bracket_b_bracket = resources.getString(R.string.a_bracket_b_bracket);
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookExtrasTask " + mBookId);
            //A performance run (in UIThread!) on 983 books showed:
            // 1. withBookshelves==false; t=799.380.500
            // 2. withBookshelves==true and complex SQL; t=806.311.600
            // 3. withBookshelves==true, simpler SQL,
            // and extra getBookshelvesByBookId call; t=1.254.915.700
            //
            // so nothing too spectacular between 1/2,
            // but avoiding the extra fetch of option 3. is worth it.

            try (Cursor cursor = mDb.fetchBookExtrasById(mBookId, mWithBookshelves)) {
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

                if (mWithAuthor) {
                    mAuthor = mapper.getString(DOM_AUTHOR_FORMATTED);
                }

                if (mWithLocation) {
                    mLocation = mapper.getString(DOM_BOOK_LOCATION);
                }

                if (mWithFormat) {
                    mFormat = mapper.getString(DOM_BOOK_FORMAT);
                }

                if (mWithBookshelves) {
                    mShelves = mapper.getString(DOM_BOOKSHELF);
                }

                if (mWithPublisher) {
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
    private abstract static class RowViewHolder
            implements MultiTypeListRowHolder<BooklistCursorRow> {

        /** Absolute position of this row. */
        int absolutePosition;
    }

    /**
     * Holder for a {@link RowKind#BOOK} row.
     */
    private static class BookHolder
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
         * @param resources for strings; not cached, only used in constructor.
         * @param db        the database.
         */
        BookHolder(@NonNull final Resources resources,
                   @NonNull final DAO db,
                   @NonNull final BooklistStyle style) {
            mDb = db;
            mStyle = style;

            // fetch once and re-use later.
            mName_colon_value = resources.getString(R.string.name_colon_value);
            mShelvesLabel = resources.getString(R.string.lbl_bookshelves);
            mLocationLabel = resources.getString(R.string.lbl_location);
        }

        @Override
        public View onCreateView(@NonNull final BooklistCursorRow rowData,
                                 @NonNull final LayoutInflater inflater,
                                 @NonNull final ViewGroup parent) {
            // level==3; but all book rows have the same type of view.
            return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
        }


        @Override
        public void onCreateViewHolder(@NonNull final BooklistCursorRow rowData,
                                       @NonNull final View view) {

            // always visible
            titleView = view.findViewById(R.id.title);

            // visibility is independent from actual data, so set here.
            readView = view.findViewById(R.id.read);
            readView.setVisibility(App.isUsed(DBDefinitions.KEY_READ) ? View.VISIBLE
                                                                      : View.GONE);

            // visibility is independent from actual data, so set here.
            coverView = view.findViewById(R.id.coverImage);
            if (App.isUsed(UniqueId.BKEY_COVER_IMAGE)
                    && mStyle.getExtraField(BooklistStyle.EXTRAS_THUMBNAIL).isRequested()) {
                coverView.setVisibility(View.VISIBLE);
            } else {
                coverView.setVisibility(View.GONE);
            }

            // visibility depends on actual data
            seriesNumView = view.findViewById(R.id.series_num);
            seriesNumLongView = view.findViewById(R.id.series_num_long);

            // visibility depends on actual data, but we need to set initial visibility
            // as actual visibility is postponed to a background task.
            //
            // iow: they are (and stay) hidden when not in use;
            // and we'll hide the used ones if empty.
            bookshelvesView = view.findViewById(R.id.shelves);
            bookshelvesView.setVisibility(
                    mStyle.getExtraField(BooklistStyle.EXTRAS_BOOKSHELVES).isRequested()
                    ? View.VISIBLE : View.GONE);

            authorView = view.findViewById(R.id.author);
            authorView.setVisibility(
                    mStyle.getExtraField(BooklistStyle.EXTRAS_AUTHOR).isRequested()
                    ? View.VISIBLE : View.GONE);

            locationView = view.findViewById(R.id.location);
            locationView.setVisibility(
                    mStyle.getExtraField(BooklistStyle.EXTRAS_LOCATION).isRequested()
                    ? View.VISIBLE : View.GONE);

            publisherView = view.findViewById(R.id.publisher);
            publisherView.setVisibility(
                    mStyle.getExtraField(BooklistStyle.EXTRAS_PUBLISHER).isRequested()
                    ? View.VISIBLE : View.GONE);

            formatView = view.findViewById(R.id.format);
            formatView.setVisibility(
                    mStyle.getExtraField(BooklistStyle.EXTRAS_FORMAT).isRequested()
                    ? View.VISIBLE : View.GONE);

            // The default is to indent all views based on the level, but with book covers on
            // the far left, it looks better if we 'out-dent' one step.
            int level = rowData.getLevel();
            if (level > 0) {
                --level;
            }
            view.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {

            final int imageMaxSize = mStyle.getScaledCoverImageMaxSize(view.getContext());

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
                    && mStyle.getExtraField(BooklistStyle.EXTRAS_THUMBNAIL).isRequested()) {
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
            if (mStyle.hasExtraDetailFields()) {
                // Fill in the extras field as blank initially.
                bookshelvesView.setText("");
                locationView.setText("");
                publisherView.setText("");
                formatView.setText("");
                authorView.setText("");
                // Queue the task.
                new GetBookExtrasTask(view.getContext().getResources(),
                                      mDb, rowData.getBookId(), mTaskListener, mStyle)
                        .execute();
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
    private static class GenericStringHolder
            extends RowViewHolder {

        /** Index of related data column. */
        final int mSourceCol;
        /** String ID to use when data is blank. */
        @StringRes
        final int mNoDataId;

        /*** View to populate. */
        @SuppressWarnings("NullableProblems")
        @NonNull
        TextView mTextView;
        /** Pointer to the container of all info for this row. */
        @SuppressWarnings("NullableProblems")
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
        GenericStringHolder(final int columnIndex,
                            @StringRes final int noDataId) {
            mSourceCol = columnIndex;
            mNoDataId = noDataId;
        }

        /**
         * Used to get the 'default' layout to use for differing row levels.
         *
         * @param level Level of layout
         *
         * @return Layout ID
         */
        @LayoutRes
        private int getDefaultLayoutId(final int level) {
            switch (level) {
                case 1:
                    // top-level uses a larger font
                    return R.layout.booksonbookshelf_row_level_1;
                case 2:
                    // second level uses a smaller font
                    return R.layout.booksonbookshelf_row_level_2;
                default:
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKS_ON_BOOKSHELF) {
                        Logger.debug(this, "getDefaultLayoutId",
                                     "level=" + level);
                    }
                    // this is in fact either level 3 or 4 for non-Book rows; uses a smaller font
                    return R.layout.booksonbookshelf_row_level_3;
            }
        }

        @Override
        public View onCreateView(@NonNull final BooklistCursorRow rowData,
                                 @NonNull final LayoutInflater inflater,
                                 @NonNull final ViewGroup parent) {

            return inflater.inflate(getDefaultLayoutId(rowData.getLevel()), parent, false);
        }

        @Override
        public void onCreateViewHolder(@NonNull final BooklistCursorRow rowData,
                                       @NonNull final View view) {
            mRowDetailsView = view.findViewById(R.id.BLB_ROW_DETAILS);
            mTextView = view.findViewById(R.id.name);
            // optional
            mVisibilityControlView = mRowDetailsView.findViewById(R.id.group);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {
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
    private static class RatingHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        RatingHolder(final int columnIndex,
                     @StringRes final int noDataId) {
            super(columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {
            String s = rowData.getString(mSourceCol);
            if (s != null) {
                try {
                    int i = (int) Float.parseFloat(s);
                    // If valid, format the description
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        s = view.getResources().getQuantityString(R.plurals.n_stars, i, i);
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
    private static class LanguageHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        LanguageHolder(final int columnIndex,
                       @StringRes final int noDataId) {
            super(columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {
            String s = rowData.getString(mSourceCol);
            if (s != null && !s.isEmpty()) {
                s = LocaleUtils.getDisplayName(view.getContext().getResources(), s);
            }
            setText(s, rowData.getLevel());
        }
    }

    /**
     * Holder for a row that displays a 'read/unread' (as text) status.
     */
    private static class ReadUnreadHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        ReadUnreadHolder(final int columnIndex,
                         @StringRes final int noDataId) {
            super(columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {
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
    private static class MonthHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        MonthHolder(final int columnIndex,
                    @StringRes final int noDataId) {
            super(columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {
            String s = rowData.getString(mSourceCol);
            if (s != null) {
                Locale locale = LocaleUtils.from(view.getContext().getResources());
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
    private static class CheckableStringHolder
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
        CheckableStringHolder(final int columnIndex,
                              @NonNull final String isLockedSource,
                              @StringRes final int noDataId) {
            super(columnIndex, noDataId);
            mIsLockedSourceCol = isLockedSource;
        }

        @Override
        public void onBindViewHolder(@NonNull final BooklistCursorRow rowData,
                                     @NonNull final View view) {
            super.onBindViewHolder(rowData, view);
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

/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfRowBookBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

/**
 * ViewHolder for a {@link BooklistGroup#BOOK} row.
 * <p>
 * TODO: adapt {@link Formatter} to support the BookHolder class.
 */
public class BookHolder
        extends RowViewHolder
        implements BindableViewHolder<DataHolder> {

    /**
     * The length of a series number is considered short if it's 4 character or less.
     * E.g. "1.12" is considered short and "1|omnibus" is long.
     */
    private static final int SHORT_SERIES_NUMBER = 4;

    /** Format string. */
    @NonNull
    private final String a_bracket_b_bracket;

    @NonNull
    private final BooksonbookshelfRowBookBinding vb;

    /** caching the book condition strings. */
    @NonNull
    private final String[] conditionDescriptions;
    @NonNull
    private final RealNumberParser realNumberParser;
    @NonNull
    private final Style style;
    @Nullable
    private final CoverHelper coverHelper;
    /** Only active when running in debug mode; displays the "position/rowId" for a book. */
    @Nullable
    private TextView dbgRowIdView;
    @Nullable
    private Set<String> use;

    /** Formatter for showing the page-number field. */
    @Nullable
    private FieldFormatter<String> pagesFormatter;

    /**
     * Constructor.
     *
     * @param itemView         the view specific for this holder
     * @param style            to use
     * @param coverScale       to use
     * @param realNumberParser the shared parser
     */
    BookHolder(@NonNull final View itemView,
               @NonNull final Style style,
               @NonNull final CoverScale coverScale,
               @NonNull final RealNumberParser realNumberParser) {
        super(itemView);
        vb = BooksonbookshelfRowBookBinding.bind(itemView);

        final Context context = itemView.getContext();

        this.style = style;
        this.realNumberParser = realNumberParser;

        final Resources res = context.getResources();
        conditionDescriptions = res.getStringArray(R.array.conditions_book);
        a_bracket_b_bracket = res.getString(R.string.a_bracket_b_bracket);

        if (style.isShowField(FieldVisibility.Screen.List, DBKey.COVER[0])) {
            final int maxWidth = coverScale.getMaxWidthInPixels(context, Style.Layout.List);
            final int maxHeight = (int) (maxWidth / CoverScale.HW_RATIO);
            coverHelper = new CoverHelper(ImageView.ScaleType.FIT_START,
                                          ImageViewLoader.MaxSize.Enforce,
                                          maxWidth, maxHeight);
        } else {
            coverHelper = null;
            vb.coverImage0.setVisibility(View.GONE);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            // Add a text view to display the "position/rowId" for a book.
            // Displayed on top of the image so the layout is not changed.
            dbgRowIdView = new TextView(context);
            dbgRowIdView.setId(View.generateViewId());
            dbgRowIdView.setTextColor(Color.BLUE);
            dbgRowIdView.setBackgroundColor(Color.WHITE);
            //noinspection CheckStyle
            dbgRowIdView.setZ(5);
            //noinspection CheckStyle
            dbgRowIdView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);

            final ConstraintLayout parentLayout = itemView.findViewById(R.id.card_frame);
            parentLayout.addView(dbgRowIdView, 0);

            final ConstraintSet set = new ConstraintSet();
            set.clone(parentLayout);
            set.connect(dbgRowIdView.getId(), ConstraintSet.TOP,
                        R.id.cover_image_0, ConstraintSet.TOP);
            set.connect(dbgRowIdView.getId(), ConstraintSet.START,
                        R.id.cover_image_0, ConstraintSet.START);
            set.setVerticalBias(dbgRowIdView.getId(), 1.0f);

            set.applyTo(parentLayout);
        }
    }

    @Override
    public void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
        super.setOnRowClickListener(listener);

        if (listener != null) {
            if (style.isShowField(FieldVisibility.Screen.List, DBKey.COVER[0])) {
                // Tapping the cover image will open the book-details page
                if (style.getCoverClickAction() == Style.CoverClickAction.OpenBookDetails) {
                    vb.coverImage0.setOnClickListener(v -> listener
                            .onClick(v, getBindingAdapterPosition()));
                } else {
                    // Tapping the cover image will zoom the image
                    // Do not go overkill here by adding a full CoverHandler.
                    //noinspection DataFlowIssue
                    vb.coverImage0.setOnClickListener(coverHelper::onZoomCover);
                }
            }
        }
    }

    /**
     * NEWTHINGS: BookLevelField: add an if (use.contains(DBKey....)) {...
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        if (use == null) {
            // init once
            use = style.getFieldVisibilityKeys(FieldVisibility.Screen.List, false)
                       .stream()
                       // Sanity check making sure the domain is present
                       .filter(key -> rowData.contains(MapDBKey.getDomainName(key)))
                       .collect(Collectors.toSet());

            if (use.contains(DBKey.PAGE_COUNT)) {
                pagesFormatter = new PagesFormatter();
            }
        }

        // Titles (book/series) are NOT reordered here.
        // It does not make much sense in this particular view/holder,
        // and slows down scrolling to much.
        vb.title.setText(rowData.getString(DBKey.TITLE));

        // Always show the 'read' icon.
        showOrHide(vb.iconRead, rowData.getBoolean(DBKey.READ__BOOL));

        if (use.contains(DBKey.READ_PROGRESS)) {
            showOrHideReadingProgress(rowData);
        }

        if (use.contains(DBKey.COVER[0])) {
            //noinspection DataFlowIssue
            final boolean hasImage = coverHelper.setImageView(vb.coverImage0,
                                                              rowData.getString(DBKey.BOOK_UUID));
            if (!hasImage) {
                vb.coverImage0.setVisibility(View.GONE);
            }
        }

        if (use.contains(DBKey.FK_AUTHOR)) {
            //ENHANCE: maybe add support for real-name
            showOrHide(vb.author, rowData.getString(DBKey.AUTHOR_FORMATTED));
        }

        if (use.contains(DBKey.FK_SERIES)) {
            if (style.hasGroup(BooklistGroup.SERIES)) {
                vb.seriesTitle.setVisibility(View.GONE);
                showOrHideSeriesNumber(rowData);
            } else {
                vb.seriesNum.setVisibility(View.GONE);
                vb.seriesNumLong.setVisibility(View.GONE);
                showOrHideSeriesText(rowData);
            }
        }

        final boolean usePub = use.contains(DBKey.FK_PUBLISHER);
        final boolean usePubDate = use.contains(DBKey.BOOK_PUBLICATION__DATE);
        if (usePub || usePubDate) {
            showOrHidePublisher(rowData, usePub, usePubDate);
        }

        if (use.contains(DBKey.FK_BOOKSHELF)) {
            showOrHide(vb.shelves, rowData.getString(DBKey.BOOKSHELF_NAME_CSV));
        }

        if (use.contains(DBKey.TITLE_ORIGINAL_LANG)) {
            showOrHide(vb.originalTitle, rowData.getString(DBKey.TITLE_ORIGINAL_LANG));
        }

        if (use.contains(DBKey.BOOK_CONDITION)) {
            final int condition = rowData.getInt(DBKey.BOOK_CONDITION);
            if (condition > 0) {
                showOrHide(vb.condition, conditionDescriptions[condition]);
            } else {
                // Hide "Unknown" condition
                vb.condition.setVisibility(View.GONE);
            }
        }

        if (use.contains(DBKey.BOOK_ISBN)) {
            showOrHide(vb.isbn, rowData.getString(DBKey.BOOK_ISBN));
        }

        if (use.contains(DBKey.FORMAT)) {
            showOrHide(vb.format, rowData.getString(DBKey.FORMAT));
        }

        if (use.contains(DBKey.LANGUAGE)) {
            // We could use the LanguageFormatter but there is really no point here
            final String language = ServiceLocator
                    .getInstance().getLanguages().getDisplayNameFromISO3(
                            itemView.getContext(), rowData.getString(DBKey.LANGUAGE));
            showOrHide(vb.language, language);
        }

        if (use.contains(DBKey.LOCATION)) {
            showOrHide(vb.location, rowData.getString(DBKey.LOCATION));
        }

        if (use.contains(DBKey.RATING)) {
            final float rating = rowData.getFloat(DBKey.RATING, realNumberParser);
            if (rating > 0) {
                vb.rating.setRating(rating);
                vb.rating.setVisibility(View.VISIBLE);
            } else {
                vb.rating.setVisibility(View.GONE);
            }
        }

        if (use.contains(DBKey.PAGE_COUNT)) {
            //noinspection DataFlowIssue
            showOrHide(vb.pages, pagesFormatter.format(itemView.getContext(),
                                                       rowData.getString(DBKey.PAGE_COUNT)));
        }

        if (use.contains(DBKey.SIGNED__BOOL)) {
            showOrHide(vb.iconSigned, rowData.getBoolean(DBKey.SIGNED__BOOL));
        }

        if (use.contains(DBKey.EDITION__BITMASK)) {
            showOrHide(vb.iconFirstEdition, (rowData.getLong(DBKey.EDITION__BITMASK)
                                             & Book.Edition.FIRST) != 0);
        }

        if (use.contains(DBKey.LOANEE_NAME)) {
            showOrHide(vb.iconLendOut, !rowData.getString(DBKey.LOANEE_NAME).isEmpty());
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            if (dbgRowIdView != null) {
                final String txt = String.valueOf(getBindingAdapterPosition()) + '/'
                                   + rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);
                dbgRowIdView.setText(txt);
            }
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
     * Conditionally show an icon (Image).
     *
     * @param view to process
     * @param show flag
     */
    private void showOrHide(@NonNull final ImageView view,
                            final boolean show) {
        view.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Conditionally show the detailed reading-progress information.
     *
     * @param rowData with the data
     */
    private void showOrHideReadingProgress(@NonNull final DataHolder rowData) {
        String txt = rowData.getString(DBKey.READ_PROGRESS);
        if (txt.isEmpty()) {
            // no details available
            vb.readProgress.setVisibility(View.GONE);
        } else {
            final ReadingProgress readingProgress = ReadingProgress.fromJson(txt);
            final int percentage = readingProgress.getPercentage();
            if (percentage == 0 || percentage == 100) {
                // The Read/Unread status is already indicated by vb.iconRead
                vb.readProgress.setVisibility(View.GONE);
            } else {
                txt = readingProgress.toFormattedText(itemView.getContext());
                showOrHide(vb.readProgress, txt);
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
     * Show the Series number if we're grouping by Series AND the user enabled this.
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
                if (number.length() > SHORT_SERIES_NUMBER) {
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

    /**
     * Show a suitable combination of the publisher name and book publication date.
     *
     * @param rowData     with the data
     * @param showPubName flag
     * @param showPubDate flag
     */
    private void showOrHidePublisher(@NonNull final DataHolder rowData,
                                     final boolean showPubName,
                                     final boolean showPubDate) {

        boolean showName = false;
        boolean showDate = false;

        String name = null;
        if (showPubName) {
            name = rowData.getString(DBKey.PUBLISHER_NAME);
            showName = !name.isBlank();
        }

        String date = null;
        if (showPubDate) {
            final String dateStr = rowData.getString(DBKey.BOOK_PUBLICATION__DATE);
            date = new PartialDate(dateStr).toDisplay(itemView.getContext().getResources()
                                                              .getConfiguration().getLocales()
                                                              .get(0),
                                                      dateStr);
            showDate = !date.isBlank();
        }

        if (showName && showDate) {
            showOrHide(vb.publisher, String.format(a_bracket_b_bracket, name, date));
        } else if (showName) {
            showOrHide(vb.publisher, name);
        } else if (showDate) {
            showOrHide(vb.publisher, date);
        } else {
            vb.publisher.setVisibility(View.GONE);
        }
    }
}

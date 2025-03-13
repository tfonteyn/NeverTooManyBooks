/*
 * @Copyright 2018-2025 HardBackNutter
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
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.Locale;
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
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
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
    /** Format string. */
    @NonNull
    private final String a_space_b;

    @NonNull
    private final BooksonbookshelfRowBookBinding vb;

    /** caching the book condition strings. */
    @NonNull
    private final String[] conditionDescriptions;
    @NonNull
    private final RealNumberParser realNumberParser;
    @NonNull
    private final DateParser<PartialDate> partialDateParser;

    @NonNull
    private final Style style;
    @Nullable
    private final CoverHelper coverHelper;
    @NonNull
    private final Locale locale;
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
        this.partialDateParser = new PartialDateParser();

        final Resources res = context.getResources();
        conditionDescriptions = res.getStringArray(R.array.lbl_book_condition);
        a_bracket_b_bracket = res.getString(R.string.a_bracket_b_bracket);
        a_space_b = res.getString(R.string.a_space_b);

        locale = res.getConfiguration().getLocales().get(0);

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
            // Init once. We do this here because we want to check the rowData (once)
            use = style.getFieldVisibilityKeys(FieldVisibility.Screen.List, false)
                       .stream()
                       // Sanity check making sure the domain is present
                       .filter(key -> rowData.contains(MapDBKey.getDomainName(key)))
                       .collect(Collectors.toSet());

            if (use.contains(DBKey.PAGES)) {
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
            showOrHide(vb.author, rowData.getString(DBKey.AUTHOR.FORMATTED_FULL_NAME));
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
        final boolean usePubDate = use.contains(DBKey.PUBLICATION_DATE);
        if (usePub || usePubDate) {
            showOrHidePublisher(rowData, usePub, usePubDate);
        }

        if (use.contains(DBKey.FIRST_PUBLICATION_DATE)) {
            showOrHideDate(vb.dateFirstPublication,
                           rowData.getString(DBKey.FIRST_PUBLICATION_DATE, null),
                           R.string.lbl_date_first_publication_as_single_char);
        }

        final boolean useDateAdded = use.contains(DBKey.DATE_ADDED__UTC);
        final boolean useDateUpdated = use.contains(DBKey.DATE_LAST_UPDATED__UTC);
        if (useDateAdded || useDateUpdated) {
            showOrHideDateAddedAndLastUpdated(rowData, useDateAdded, useDateUpdated);
        }

        if (use.contains(DBKey.DATE_ACQUIRED)) {
            showOrHideDate(vb.dateAcquired, rowData.getString(DBKey.DATE_ACQUIRED, null),
                           R.string.lbl_date_acquired_as_single_char);
        }

        if (use.contains(DBKey.FK_BOOKSHELF)) {
            showOrHide(vb.shelves, rowData.getString(DBKey.BOOKSHELF.BOOK_BOOKSHELF_NAMES_AS_CSV));
        }

        if (use.contains(DBKey.TRANSLATION_ORIGINAL_TITLE)) {
            showOrHide(vb.originalTitle, rowData.getString(DBKey.TRANSLATION_ORIGINAL_TITLE));
        }

        if (use.contains(DBKey.TRANSLATION_ORIGINAL_LANGUAGE)) {
            showOrHideLanguage(vb.originalLanguage,
                               rowData.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE));
        }

        if (use.contains(DBKey.CONDITION_BOOK)) {
            final int condition = rowData.getInt(DBKey.CONDITION_BOOK);
            if (condition > 0) {
                vb.condition.setText(conditionDescriptions[condition]);
                vb.condition.setVisibility(View.VISIBLE);
            } else {
                // Hide "Unknown" condition
                vb.condition.setVisibility(View.GONE);
            }
        }

        if (use.contains(DBKey.ISBN)) {
            showOrHide(vb.isbn, rowData.getString(DBKey.ISBN));
        }

        if (use.contains(DBKey.FORMAT)) {
            showOrHide(vb.format, rowData.getString(DBKey.FORMAT));
        }

        if (use.contains(DBKey.LANGUAGE)) {
            showOrHideLanguage(vb.language, rowData.getString(DBKey.LANGUAGE));
        }

        if (use.contains(DBKey.LOCATION)) {
            // TODO: maybe add 📍 (U+1F4CD)
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

        if (use.contains(DBKey.PAGES)) {
            showOrHidePages(vb.pages, rowData.getString(DBKey.PAGES, null));
        }

        if (use.contains(DBKey.SIGNED__BOOL)) {
            showOrHide(vb.iconSigned, rowData.getBoolean(DBKey.SIGNED__BOOL));
        }

        if (use.contains(DBKey.EDITION)) {
            showOrHide(vb.iconFirstEdition, (rowData.getLong(DBKey.EDITION)
                                             & Book.Edition.FIRST) != 0);
        }

        if (use.contains(DBKey.LOANEE_NAME)) {
            showOrHide(vb.iconLendOut, !rowData.getString(DBKey.LOANEE_NAME).isEmpty());
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            if (dbgRowIdView != null) {
                final String txt = String.valueOf(getBindingAdapterPosition()) + '/'
                                   + rowData.getLong(DBKey.BL_NODE.ROW_ID);
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
     * Conditionally display a language.
     *
     * @param view to populate
     * @param iso3 language code
     */
    private void showOrHideLanguage(@NonNull final TextView view,
                                    @Nullable final String iso3) {
        // We could use the LanguageFormatter but there is really no point here
        if (iso3 != null && !iso3.isEmpty()) {
            final String language = ServiceLocator
                    .getInstance().getLanguages()
                    .getDisplayLanguageFromISO3(view.getContext(), iso3);
            view.setText(language);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Conditionally display the page count/description.
     *
     * @param view  to populate
     * @param pages to display
     */
    private void showOrHidePages(@NonNull final TextView view,
                                 @Nullable final String pages) {
        if (pages != null && !pages.isBlank()) {
            //noinspection DataFlowIssue
            view.setText(pagesFormatter.format(itemView.getContext(), pages));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Conditionally display a date.
     *
     * @param view   to populate
     * @param text   to set
     * @param symbol to combine/display
     */
    private void showOrHideDate(@NonNull final TextView view,
                                @Nullable final String text,
                                @StringRes final int symbol) {
        if (text != null && !text.isEmpty()) {
            view.setText(formatDate(view.getContext(), symbol, text));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Conditionally show the detailed reading-progress information.
     *
     * @param rowData with the data
     */
    private void showOrHideReadingProgress(@NonNull final DataHolder rowData) {
        final String text = rowData.getString(DBKey.READ_PROGRESS);
        if (text.isEmpty()) {
            // no details available
            vb.readProgress.setVisibility(View.GONE);
        } else {
            final ReadingProgress readingProgress = ReadingProgress.fromJson(text);
            final int percentage = readingProgress.getPercentage();
            if (percentage == 0 || percentage == 100) {
                // The Read/Unread status is already indicated by vb.iconRead
                vb.readProgress.setVisibility(View.GONE);
            } else {
                vb.readProgress.setText(readingProgress.format(itemView.getContext()));
                vb.readProgress.setVisibility(View.VISIBLE);
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
        if (rowData.contains(DBKey.SERIES.TITLE)) {
            String seriesTitle = rowData.getString(DBKey.SERIES.TITLE);
            if (!seriesTitle.isBlank()) {
                if (rowData.contains(DBKey.SERIES.BOOK_SERIES_NUMBER)) {
                    final String number = rowData.getString(DBKey.SERIES.BOOK_SERIES_NUMBER);
                    if (!number.isBlank()) {
                        seriesTitle = String.format(locale, a_bracket_b_bracket,
                                                    seriesTitle, number);
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
        if (rowData.contains(DBKey.SERIES.BOOK_SERIES_NUMBER)) {
            final String number = rowData.getString(DBKey.SERIES.BOOK_SERIES_NUMBER);
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
     * @param rowData    with the data
     * @param usePub     flag
     * @param usePubDate flag
     */
    private void showOrHidePublisher(@NonNull final DataHolder rowData,
                                     final boolean usePub,
                                     final boolean usePubDate) {

        boolean showName = false;
        boolean showDate = false;

        String name = null;
        if (usePub) {
            name = rowData.getString(DBKey.PUBLISHER.NAME);
            showName = !name.isBlank();
        }

        String date = null;
        if (usePubDate) {
            date = formatDate(rowData.getString(DBKey.PUBLICATION_DATE));
            showDate = !date.isBlank();
        }

        if (showName && showDate) {
            final String text = String.format(locale, a_bracket_b_bracket, name, date);
            vb.publisher.setText(text);
            vb.publisher.setVisibility(View.VISIBLE);
        } else if (showName) {
            vb.publisher.setText(name);
            vb.publisher.setVisibility(View.VISIBLE);
        } else if (showDate) {
            vb.publisher.setText(date);
            vb.publisher.setVisibility(View.VISIBLE);
        } else {
            vb.publisher.setVisibility(View.GONE);
        }
    }

    /**
     * Show a suitable combination of the date-added and date-updated.
     *
     * @param rowData        with the data
     * @param useDateAdded   flag
     * @param useDateUpdated flag
     */
    private void showOrHideDateAddedAndLastUpdated(@NonNull final DataHolder rowData,
                                                   final boolean useDateAdded,
                                                   final boolean useDateUpdated) {
        final Context context = vb.dateAddedAndLastUpdated.getContext();

        boolean showAdded = false;
        boolean showUpdated = false;

        String dateAdded = null;
        if (useDateAdded) {
            dateAdded = rowData.getString(DBKey.DATE_ADDED__UTC, null);
            if (dateAdded != null) {
                dateAdded = formatDate(context,
                                       R.string.lbl_date_added_as_single_char,
                                       dateAdded);
                showAdded = true;
            }
        }

        String dateLastUpdated = null;
        if (useDateUpdated) {
            dateLastUpdated = rowData.getString(DBKey.DATE_LAST_UPDATED__UTC, null);
            if (dateLastUpdated != null) {
                dateLastUpdated = formatDate(context,
                                             R.string.lbl_date_last_updated_as_single_char,
                                             dateLastUpdated);
                showUpdated = true;
            }
        }

        if (showAdded && showUpdated) {
            vb.dateAddedAndLastUpdated.setText(context.getString(
                    R.string.a_space_b, dateAdded, dateLastUpdated));
            vb.dateAddedAndLastUpdated.setVisibility(View.VISIBLE);
        } else if (showAdded) {
            vb.dateAddedAndLastUpdated.setText(dateAdded);
            vb.dateAddedAndLastUpdated.setVisibility(View.VISIBLE);
        } else if (showUpdated) {
            vb.dateAddedAndLastUpdated.setText(dateLastUpdated);
            vb.dateAddedAndLastUpdated.setVisibility(View.VISIBLE);
        } else {
            vb.dateAddedAndLastUpdated.setVisibility(View.GONE);
        }
    }

    /**
     * Parse an ISO (partial) date and return a formatted version combined with the given symbol.
     *
     * @param context    Current context
     * @param symbol     to add
     * @param isoDateStr to parse
     *
     * @return formatted date
     */
    @NonNull
    private String formatDate(@NonNull final Context context,
                              @StringRes final int symbol,
                              @NonNull final String isoDateStr) {
        return String.format(locale, a_space_b, context.getString(symbol), formatDate(isoDateStr));
    }

    /**
     * Parse an ISO (partial) date and return a formatted version.
     *
     * @param isoDateStr to parse
     *
     * @return formatted date
     */
    @NonNull
    private String formatDate(final String isoDateStr) {
        return partialDateParser
                .parse(isoDateStr)
                .map(d -> d.toDisplay(locale, isoDateStr))
                .orElse("");
    }
}

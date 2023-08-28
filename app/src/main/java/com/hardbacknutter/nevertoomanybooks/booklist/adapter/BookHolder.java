/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfRowBookBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

/**
 * ViewHolder for a {@link BooklistGroup#BOOK} row.
 */
public class BookHolder
        extends RowViewHolder
        implements BindableViewHolder<DataHolder> {

    /**
     * 0.6 is based on a standard paperback 17.5cm x 10.6cm
     * -> width = 0.6 * maxHeight.
     *
     * @see #coverLongestSide
     */
    private static final float HW_RATIO = 0.6f;

    /** Format string. */
    @NonNull
    private final String a_bracket_b_bracket;

    @NonNull
    private final BooksonbookshelfRowBookBinding vb;
    private final int coverLongestSide;
    private final boolean imageCachingEnabled;
    /** caching the book condition strings. */
    @NonNull
    private final String[] conditionDescriptions;
    @NonNull
    private final Style style;
    @NonNull
    private final RealNumberParser realNumberParser;
    /** Only active when running in debug mode; displays the "position/rowId" for a book. */
    @Nullable
    private TextView dbgRowIdView;
    /** each holder has its own loader - the more cores the cpu has, the faster we load. */
    @Nullable
    private ImageViewLoader imageLoader;
    @Nullable
    private Set<String> use;
    @Nullable
    private FieldFormatter<String> pagesFormatter;

    /**
     * Constructor.
     * <p>
     * <strong>Note:</strong> the itemView can be re-used.
     * Hence make sure to explicitly set visibility.
     *
     * @param itemView         the view specific for this holder
     * @param style            to use
     * @param realNumberParser the shared parser
     * @param coverLongestSide Longest side for a cover in pixels
     */
    BookHolder(@NonNull final View itemView,
               @NonNull final Style style,
               @NonNull final RealNumberParser realNumberParser,
               @Dimension final int coverLongestSide) {
        super(itemView);
        this.style = style;
        this.realNumberParser = realNumberParser;
        this.coverLongestSide = coverLongestSide;

        final Context context = itemView.getContext();

        imageCachingEnabled = ServiceLocator.getInstance().getCoverStorage()
                                            .isImageCachingEnabled();

        final Resources res = context.getResources();
        conditionDescriptions = res.getStringArray(R.array.conditions_book);

        vb = BooksonbookshelfRowBookBinding.bind(itemView);

        a_bracket_b_bracket = context.getString(R.string.a_bracket_b_bracket);

        if (this.style.isShowField(Style.Screen.List, DBKey.COVER[0])) {
            // Do not go overkill here by adding a full-blown CoverHandler.
            // We only provide zooming by clicking on the image.
            vb.coverImage0.setOnClickListener(this::onZoomCover);

            imageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                              coverLongestSide, coverLongestSide);
        } else {
            // hide it if not in use.
            vb.coverImage0.setVisibility(View.GONE);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            // add a text view to display the "position/rowId" for a book
            dbgRowIdView = new TextView(context);
            dbgRowIdView.setId(View.generateViewId());
            dbgRowIdView.setTextColor(Color.BLUE);
            dbgRowIdView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);

            final ConstraintLayout parentLayout = itemView.findViewById(R.id.card_frame);
            parentLayout.addView(dbgRowIdView, 0);

            final ConstraintSet set = new ConstraintSet();
            set.clone(parentLayout);
            set.connect(dbgRowIdView.getId(), ConstraintSet.TOP,
                        R.id.cover_image_0, ConstraintSet.BOTTOM);
            set.connect(dbgRowIdView.getId(), ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.connect(dbgRowIdView.getId(), ConstraintSet.END,
                        R.id.col1, ConstraintSet.START);
            set.setVerticalBias(dbgRowIdView.getId(), 1.0f);

            set.applyTo(parentLayout);
        }
    }

    /**
     * Zoom the given cover.
     *
     * @param coverView passed in to allow for future expansion
     */
    private void onZoomCover(@NonNull final View coverView) {
        final String uuid = (String) coverView.getTag(R.id.TAG_THUMBNAIL_UUID);
        ServiceLocator.getInstance().getCoverStorage().getPersistedFile(uuid, 0).ifPresent(
                file -> {
                    final FragmentActivity activity = (FragmentActivity) coverView.getContext();
                    ZoomedImageDialogFragment.launch(activity.getSupportFragmentManager(), file);
                });
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        if (use == null) {
            // init once
            use = style.getBookLevelFieldsUsage(rowData);
            if (use.contains(DBKey.PAGE_COUNT)) {
                pagesFormatter = new PagesFormatter();
            }
        }

        // Titles (book/series) are NOT reordered here.
        // It does not make much sense in this particular view/holder,
        // and slows down scrolling to much.
        vb.title.setText(rowData.getString(DBKey.TITLE));

        if (use.contains(DBKey.TITLE_ORIGINAL_LANG)) {
            vb.originalTitle.setText(rowData.getString(DBKey.TITLE_ORIGINAL_LANG));
        }

        vb.iconRead.setVisibility(
                rowData.getBoolean(DBKey.READ__BOOL) ? View.VISIBLE : View.GONE);

        if (use.contains(DBKey.SIGNED__BOOL)) {
            final boolean isSet = rowData.getBoolean(DBKey.SIGNED__BOOL);
            vb.iconSigned.setVisibility(isSet ? View.VISIBLE : View.GONE);
        }

        if (use.contains(DBKey.EDITION__BITMASK)) {
            final boolean isSet = (rowData.getLong(DBKey.EDITION__BITMASK)
                                   & Book.Edition.FIRST) != 0;
            vb.iconFirstEdition.setVisibility(isSet ? View.VISIBLE : View.GONE);
        }

        if (use.contains(DBKey.LOANEE_NAME)) {
            final boolean isSet = !rowData.getString(DBKey.LOANEE_NAME).isEmpty();
            vb.iconLendOut.setVisibility(isSet ? View.VISIBLE : View.GONE);
        }

        if (use.contains(DBKey.COVER[0])) {
            setImageView(rowData.getString(DBKey.BOOK_UUID));
        }

        if (use.contains(DBKey.FK_SERIES)) {
            if (this.style.hasGroup(BooklistGroup.SERIES)) {
                vb.seriesTitle.setVisibility(View.GONE);
                showOrHideSeriesNumber(rowData);
            } else {
                vb.seriesNum.setVisibility(View.GONE);
                vb.seriesNumLong.setVisibility(View.GONE);
                showOrHideSeriesText(rowData);
            }
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
            final String pages = rowData.getString(DBKey.PAGE_COUNT);
            //noinspection DataFlowIssue
            showOrHide(vb.pages, pagesFormatter.format(itemView.getContext(), pages));
        }

        if (use.contains(DBKey.FK_AUTHOR)) {
            //ENHANCE: maybe add support for real-name
            showOrHide(vb.author, rowData.getString(DBKey.AUTHOR_FORMATTED));
        }

        final boolean usePub = use.contains(DBKey.FK_PUBLISHER);
        final boolean usePubDate = use.contains(DBKey.BOOK_PUBLICATION__DATE);
        if (usePub || usePubDate) {
            showOrHidePublisher(rowData, usePub, usePubDate);
        }

        if (use.contains(DBKey.BOOK_ISBN)) {
            showOrHide(vb.isbn, rowData.getString(DBKey.BOOK_ISBN));
        }

        if (use.contains(DBKey.FORMAT)) {
            showOrHide(vb.format, rowData.getString(DBKey.FORMAT));
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

        if (use.contains(DBKey.LANGUAGE)) {
            final String language = ServiceLocator
                    .getInstance().getLanguages().getDisplayNameFromISO3(
                            vb.language.getContext(), rowData.getString(DBKey.LANGUAGE));
            showOrHide(vb.language, language);
        }

        if (use.contains(DBKey.LOCATION)) {
            showOrHide(vb.location, rowData.getString(DBKey.LOCATION));
        }

        if (use.contains(DBKey.FK_BOOKSHELF)) {
            showOrHide(vb.shelves, rowData.getString(DBKey.BOOKSHELF_NAME_CSV));
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
     * Shown the Series number if we're grouping by Series AND the user enabled this.
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
                // 4 characters is based on e.g. "1.12" being considered short
                // and e.g. "1|omnibus" being long.
                if (number.length() > 4) {
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

    private void showOrHidePublisher(@NonNull final DataHolder rowData,
                                     final boolean usePub,
                                     final boolean usePubDate) {

        boolean showName = false;
        boolean showDate = false;

        String name = null;
        if (usePub) {
            name = rowData.getString(DBKey.PUBLISHER_NAME);
            showName = !name.isBlank();
        }

        String date = null;
        if (usePubDate) {
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
     * Load the image owned by the UUID/cIdx into the destination ImageView.
     * Handles checking & storing in the cache.
     * <p>
     * Images and placeholder will always be scaled to a fixed size.
     *
     * @param uuid UUID of the book
     */
    private void setImageView(@NonNull final String uuid) {
        // store the uuid for use in the OnClickListener
        vb.coverImage0.setTag(R.id.TAG_THUMBNAIL_UUID, uuid);

        // 1. If caching is used, check it.
        if (imageCachingEnabled) {
            final Bitmap bitmap = ServiceLocator.getInstance().getCoverStorage()
                                                .getCachedBitmap(uuid, 0,
                                                                 coverLongestSide,
                                                                 coverLongestSide);
            if (bitmap != null) {
                //noinspection DataFlowIssue
                imageLoader.fromBitmap(vb.coverImage0, bitmap);
                return;
            }
        }

        // 2. Cache did not have it, or we were not allowed to check.
        final Optional<File> file = ServiceLocator.getInstance().getCoverStorage()
                                                  .getPersistedFile(uuid, 0);
        // Check if the file exists; if it does not...
        if (file.isEmpty()) {
            // leave the space blank, but preserve the width BASED on the coverLongestSide!
            final ViewGroup.LayoutParams lp = vb.coverImage0.getLayoutParams();
            lp.width = (int) (coverLongestSide * HW_RATIO);
            lp.height = 0;
            vb.coverImage0.setLayoutParams(lp);
            vb.coverImage0.setImageDrawable(null);
            return;
        }

        // Once we get here, we know the file is valid
        if (imageCachingEnabled) {
            // 1. Gets the image from the file system and display it.
            // 2. Start a subsequent task to send it to the cache.
            //noinspection DataFlowIssue
            imageLoader.fromFile(vb.coverImage0, file.get(), bitmap -> {
                if (bitmap != null) {
                    ServiceLocator.getInstance().getCoverStorage().saveToCache(
                            uuid, 0, bitmap, coverLongestSide, coverLongestSide);
                }
            });
        } else {
            // Cache not used: Get the image from the file system and display it.
            //noinspection DataFlowIssue
            imageLoader.fromFile(vb.coverImage0, file.get(), null);
        }
    }
}

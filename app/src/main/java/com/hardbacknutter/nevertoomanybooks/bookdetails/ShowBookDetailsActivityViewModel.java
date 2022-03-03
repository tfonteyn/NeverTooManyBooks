/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldImpl;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BooleanIndicatorAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Contains data in the <strong>Activity</strong> scope;
 * i.e. shared between all fragments in the {@link ShowBookPagerFragment} pager adapter.
 */
public class ShowBookDetailsActivityViewModel
        extends ViewModel {

    private final List<MenuHandler> mMenuHandlers = new ArrayList<>();
    /** the list with all fields. */
    private final List<Field<?, ? extends View>> mFields = new ArrayList<>();

    private ListStyle mStyle;

    private boolean mModified;

    /**
     * Part of the fragment result data.
     * This informs the BoB whether it should rebuild its list.
     *
     * @return {@code true} if the book was changed and successfully saved.
     */
    public boolean isModified() {
        return mModified;
    }

    void updateFragmentResult() {
        mModified = true;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mStyle == null) {
            // the style can be 'null' here. If so, the default one will be used.
            final String styleUuid = args.getString(ListStyle.BKEY_UUID);
            mStyle = ServiceLocator.getInstance().getStyles().getStyleOrDefault(context, styleUuid);

            initFields(context);

            mMenuHandlers.add(new ViewBookOnWebsiteHandler());
            mMenuHandlers.add(new AmazonHandler());
        }
    }

    @NonNull
    public ListStyle getStyle() {
        return mStyle;
    }

    @NonNull
    List<MenuHandler> getMenuHandlers() {
        return mMenuHandlers;
    }

    boolean useLoanee(@NonNull final Context context) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        return DBKey.isUsed(global, DBKey.KEY_LOANEE);
    }

    boolean useToc(@NonNull final Context context) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        return DBKey.isUsed(global, DBKey.BITMASK_TOC);
    }

    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>The fragment has no access to the style -> return the global visibility</li>
     *     <li>The global style is set to HIDE -> {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param global Global preferences
     * @param cIdx   0..n image index
     *
     * @return {@code true} if in use
     */
    boolean isCoverUsed(@NonNull final SharedPreferences global,
                        @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx])) {
            return false;
        }

        // let the style decide
        return mStyle.getDetailScreenBookFields().isShowCover(global, cIdx);
    }

    @NonNull
    List<Field<?, ? extends View>> getFields() {
        return mFields;
    }

    /**
     * Return the Field associated with the passed ID.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field.
     * @param id  Field/View ID
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field id was not found
     */
    @NonNull
    <T, V extends View> Field<T, V> requireField(@IdRes final int id) {
        //noinspection unchecked
        return (Field<T, V>) mFields
                .stream()
                .filter(field -> field.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + id));
    }

    private void initFields(@NonNull final Context context) {

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        // These FieldFormatters can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale);
        final FieldFormatter<String> htmlFormatter = new HtmlFormatter<>(true, true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> languageFormatter = new LanguageFormatter(userLocale);

        // book fields
        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.title,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_TITLE));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.author,
                                    new TextViewAccessor<>(new AuthorListFormatter(
                                            Author.Details.Full, false, true)),
                                    Book.BKEY_AUTHOR_LIST,
                                    DBKey.FK_AUTHOR)
                            .setRelatedFields(R.id.lbl_author));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.series_title,
                                    new TextViewAccessor<>(new SeriesListFormatter(
                                            Series.Details.Full, false, true)),
                                    Book.BKEY_SERIES_LIST,
                                    DBKey.KEY_SERIES_TITLE)
                            .setRelatedFields(R.id.lbl_series));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.isbn,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_ISBN)
                            .setRelatedFields(R.id.lbl_isbn));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.description,
                                    new TextViewAccessor<>(htmlFormatter),
                                    DBKey.KEY_DESCRIPTION));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.genre,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_GENRE)
                            .setRelatedFields(R.id.lbl_genre));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.language,
                                    new TextViewAccessor<>(languageFormatter),
                                    DBKey.KEY_LANGUAGE)
                            .setRelatedFields(R.id.lbl_language));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.pages,
                                    new TextViewAccessor<>(new PagesFormatter()),
                                    DBKey.KEY_PAGES));
        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.format,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_FORMAT));
        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.color,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_COLOR));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.publisher,
                                    new TextViewAccessor<>(new CsvFormatter()),
                                    Book.BKEY_PUBLISHER_LIST,
                                    DBKey.KEY_PUBLISHER_NAME));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.date_published,
                                    new TextViewAccessor<>(dateFormatter),
                                    DBKey.DATE_BOOK_PUBLICATION)
                            .setRelatedFields(R.id.lbl_date_published));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.first_publication,
                                    new TextViewAccessor<>(dateFormatter),
                                    DBKey.DATE_FIRST_PUBLICATION)
                            .setRelatedFields(R.id.lbl_first_publication));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.edition,
                                    new BitmaskChipGroupAccessor(Book.Edition::getEditions, false),
                                    DBKey.BITMASK_EDITION)
                            .setRelatedFields(R.id.lbl_edition));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.print_run,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_PRINT_RUN));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.price_listed,
                                    new TextViewAccessor<>(moneyFormatter),
                                    DBKey.PRICE_LISTED)
                            .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed));


        // Personal fields
        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.bookshelves,
                                    new EntityListChipGroupAccessor(() -> new ArrayList<>(
                                            ServiceLocator.getInstance().getBookshelfDao()
                                                          .getAll()),
                                                                    false),
                                    Book.BKEY_BOOKSHELF_LIST,
                                    DBKey.FK_BOOKSHELF)
                            .setRelatedFields(R.id.lbl_bookshelves));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.date_acquired,
                                    new TextViewAccessor<>(dateFormatter),
                                    DBKey.DATE_ACQUIRED)
                            .setRelatedFields(R.id.lbl_date_acquired));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.location,
                                    new TextViewAccessor<>(),
                                    DBKey.KEY_LOCATION)
                            .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.rating,
                                    new RatingBarAccessor(false),
                                    DBKey.KEY_RATING));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.condition,
                                    new TextViewAccessor<>(new StringArrayResFormatter(
                                            context, R.array.conditions_book)),
                                    DBKey.KEY_BOOK_CONDITION)
                            .setRelatedFields(R.id.lbl_condition));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.condition_cover,
                                    new TextViewAccessor<>(new StringArrayResFormatter(
                                            context, R.array.conditions_dust_cover)),
                                    DBKey.KEY_BOOK_CONDITION_COVER)
                            .setRelatedFields(R.id.lbl_condition_cover));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.notes,
                                    new TextViewAccessor<>(htmlFormatter),
                                    DBKey.KEY_PRIVATE_NOTES)
                            .setRelatedFields(R.id.lbl_notes));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.read,
                                    new BooleanIndicatorAccessor(),
                                    DBKey.BOOL_READ));
        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.read_start,
                                    new TextViewAccessor<>(dateFormatter),
                                    DBKey.DATE_READ_START)
                            .setRelatedFields(R.id.lbl_read_start));
        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.read_end,
                                    new TextViewAccessor<>(dateFormatter),
                                    DBKey.DATE_READ_END)
                            .setRelatedFields(R.id.lbl_read_end));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.signed,
                                    new BooleanIndicatorAccessor(),
                                    DBKey.BOOL_SIGNED));

        mFields.add(new FieldImpl<>(FragmentId.Main, R.id.price_paid,
                                    new TextViewAccessor<>(moneyFormatter),
                                    DBKey.PRICE_PAID)
                            .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid));
    }
}

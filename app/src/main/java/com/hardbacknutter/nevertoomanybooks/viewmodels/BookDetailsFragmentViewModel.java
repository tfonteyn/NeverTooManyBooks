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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.CompoundButtonAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

public class BookDetailsFragmentViewModel
        extends BookBaseFragmentViewModel {

    /** Log tag. */
    private static final String TAG = "BookDetailsFragmentViewModel";

    /** Table name of the {@link FlattenedBooklist}. */
    public static final String BKEY_NAV_TABLE = TAG + ":FBLTable";
    public static final String BKEY_NAV_ROW_ID = TAG + ":FBLRow";

    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    @Override
    protected void onCleared() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
        }
        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context, will not get cached.
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args,
                     @NonNull final Book book) {
        super.init(args);

        if (args != null) {
            // got list ?
            final String navTableName = args.getString(BKEY_NAV_TABLE);
            if (navTableName != null && !navTableName.isEmpty()) {
                // ok, we have a list, get the rowId we need to be on.
                final long rowId = args.getLong(BKEY_NAV_ROW_ID, 0);
                if (rowId > 0) {
                    mFlattenedBooklist = new FlattenedBooklist(mDb, navTableName);
                    // move to book.
                    if (!mFlattenedBooklist.moveTo(rowId)
                        // Paranoia: is it the book we wanted ?
                        || mFlattenedBooklist.getBookId() != book.getId()) {
                        // Should never happen... flw
                        mFlattenedBooklist.closeAndDrop();
                        mFlattenedBooklist = null;
                    }
                }
            }
        }

        if (getFields().isEmpty()) {
            onInitFields(context);
        }
    }

    /**
     * Define all Fields, giving them an ID, a type and an optional formatter.
     * Fields get setup with the domains they will display.
     *
     * @param context Current context, will not get cached.
     */
    private void onInitFields(@NonNull final Context context) {
        final Fields fields = getFields();

        final Locale userLocale = LocaleUtils.getUserLocale(context);

        // These FieldFormatter's can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();
        final FieldFormatter<String> htmlFormatter = new HtmlFormatter<>(true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> languageFormatter = new LanguageFormatter();

        // book fields
        fields.add(R.id.title, DBDefinitions.KEY_TITLE, new TextAccessor<String>());

        fields.add(R.id.author, UniqueId.BKEY_AUTHOR_ARRAY,
                   new TextAccessor<>(new AuthorListFormatter(Author.Details.Full, false, true)),
                   DBDefinitions.KEY_FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author);

        fields.add(R.id.series_title, UniqueId.BKEY_SERIES_ARRAY,
                   new TextAccessor<>(new SeriesListFormatter(Series.Details.Full, false, true)),
                   DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.isbn, DBDefinitions.KEY_ISBN, new TextAccessor<String>())
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.description, DBDefinitions.KEY_DESCRIPTION,
                   new TextAccessor<>(htmlFormatter))
              .setRelatedFields(R.id.lbl_description);

        fields.add(R.id.genre, DBDefinitions.KEY_GENRE, new TextAccessor<String>())
              .setRelatedFields(R.id.lbl_genre);

        fields.add(R.id.language, DBDefinitions.KEY_LANGUAGE,
                   new TextAccessor<>(languageFormatter))
              .setRelatedFields(R.id.lbl_language);

        fields.add(R.id.pages, DBDefinitions.KEY_PAGES, new TextAccessor<>(new PagesFormatter()));
        fields.add(R.id.format, DBDefinitions.KEY_FORMAT, new TextAccessor<String>());
        fields.add(R.id.color, DBDefinitions.KEY_COLOR, new TextAccessor<String>());
        fields.add(R.id.publisher, DBDefinitions.KEY_PUBLISHER, new TextAccessor<String>());

        fields.add(R.id.date_published, DBDefinitions.KEY_DATE_PUBLISHED,
                   new TextAccessor<>(dateFormatter))
              .setRelatedFields(R.id.lbl_date_published);

        fields.add(R.id.first_publication, DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                   new TextAccessor<>(dateFormatter))
              .setRelatedFields(R.id.lbl_first_publication);

        fields.add(R.id.print_run, DBDefinitions.KEY_PRINT_RUN, new TextAccessor<String>())
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.price_listed, DBDefinitions.KEY_PRICE_LISTED,
                   new TextAccessor<>(moneyFormatter))
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        // Personal fields
        fields.add(R.id.bookshelves, UniqueId.BKEY_BOOKSHELF_ARRAY,
                   new EntityListChipGroupAccessor(new ArrayList<>(mDb.getBookshelves()), false),
                   DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        fields.add(R.id.date_acquired, DBDefinitions.KEY_DATE_ACQUIRED,
                   new TextAccessor<>(dateFormatter))
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.add(R.id.edition, DBDefinitions.KEY_EDITION_BITMASK, new BitmaskChipGroupAccessor(
                Book.Edition.getEditions(context), false));

        fields.add(R.id.location, DBDefinitions.KEY_LOCATION, new TextAccessor<String>())
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.add(R.id.rating, DBDefinitions.KEY_RATING, new RatingBarAccessor())
              .setRelatedFields(R.id.lbl_rating);

        fields.add(R.id.condition, DBDefinitions.KEY_BOOK_CONDITION, new TextAccessor<>(
                new StringArrayResFormatter(context, R.array.conditions_book)))
              .setRelatedFields(R.id.lbl_condition);
        fields.add(R.id.condition_cover, DBDefinitions.KEY_BOOK_CONDITION_COVER, new TextAccessor<>(
                new StringArrayResFormatter(context, R.array.conditions_dust_cover)))
              .setRelatedFields(R.id.lbl_condition_cover);

        fields.add(R.id.notes, DBDefinitions.KEY_PRIVATE_NOTES, new TextAccessor<>(htmlFormatter))
              .setRelatedFields(R.id.lbl_notes);

        fields.add(R.id.read_start, DBDefinitions.KEY_READ_START, new TextAccessor<>(dateFormatter))
              .setRelatedFields(R.id.lbl_read_start);
        fields.add(R.id.read_end, DBDefinitions.KEY_READ_END, new TextAccessor<>(dateFormatter))
              .setRelatedFields(R.id.lbl_read_end);

        fields.add(R.id.cbx_read, DBDefinitions.KEY_READ, new CompoundButtonAccessor());

        fields.add(R.id.cbx_signed, DBDefinitions.KEY_SIGNED, new CompoundButtonAccessor())
              .setRelatedFields(R.id.lbl_signed);

        fields.add(R.id.price_paid, DBDefinitions.KEY_PRICE_PAID,
                   new TextAccessor<>(moneyFormatter))
              .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);
    }

    /**
     * Called after the user swipes back/forwards through the flattened booklist.
     *
     * @param book    Current book
     * @param forward flag; move to the next or previous book relative to the passed book.
     *
     * @return {@code true} if we moved
     */
    public boolean move(@NonNull final Book book,
                        final boolean forward) {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.move(forward);
            final long bookId = mFlattenedBooklist.getBookId();
            // reload if it's a different book
            if (bookId != book.getId()) {
                book.reload(mDb, bookId);
                return true;
            }
        }
        return false;
    }
}

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
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.CompoundButtonAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
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
        fields.add(R.id.title, new TextAccessor<String>(), DBDefinitions.KEY_TITLE);

        fields.add(R.id.author,
                   new TextAccessor<>(new AuthorListFormatter(Author.Details.Full, false, true)),
                   UniqueId.BKEY_AUTHOR_ARRAY,
                   DBDefinitions.KEY_FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author);

        fields.add(R.id.series_title,
                   new TextAccessor<>(new SeriesListFormatter(Series.Details.Full, false, true)),
                   UniqueId.BKEY_SERIES_ARRAY,
                   DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.isbn, new TextAccessor<String>(), DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.description, new TextAccessor<>(htmlFormatter),
                   DBDefinitions.KEY_DESCRIPTION
                  )
              .setRelatedFields(R.id.lbl_description);

        fields.add(R.id.genre, new TextAccessor<String>(), DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        fields.add(R.id.language, new TextAccessor<>(languageFormatter), DBDefinitions.KEY_LANGUAGE
                  )
              .setRelatedFields(R.id.lbl_language);

        fields.add(R.id.pages, new TextAccessor<>(new PagesFormatter()), DBDefinitions.KEY_PAGES);
        fields.add(R.id.format, new TextAccessor<String>(), DBDefinitions.KEY_FORMAT);
        fields.add(R.id.color, new TextAccessor<String>(), DBDefinitions.KEY_COLOR);
        fields.add(R.id.publisher, new TextAccessor<String>(), DBDefinitions.KEY_PUBLISHER);

        fields.add(R.id.date_published, new TextAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_PUBLISHED
                  )
              .setRelatedFields(R.id.lbl_date_published);

        fields.add(R.id.first_publication, new TextAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION
                  )
              .setRelatedFields(R.id.lbl_first_publication);

        fields.add(R.id.print_run, new TextAccessor<String>(), DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.price_listed, new TextAccessor<>(moneyFormatter),
                   DBDefinitions.KEY_PRICE_LISTED
                  )
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        // Personal fields
        fields.add(R.id.bookshelves,
                   new EntityListChipGroupAccessor(new ArrayList<>(mDb.getBookshelves()), false),
                   UniqueId.BKEY_BOOKSHELF_ARRAY,
                   DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        fields.add(R.id.date_acquired, new TextAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_ACQUIRED
                  )
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.add(R.id.edition, new BitmaskChipGroupAccessor(
                Book.Edition.getEditions(context), false), DBDefinitions.KEY_EDITION_BITMASK);

        fields.add(R.id.location, new TextAccessor<String>(), DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.add(R.id.rating, new RatingBarAccessor(), DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.add(R.id.condition, new TextAccessor<>(
                           new StringArrayResFormatter(context, R.array.conditions_book)),
                   DBDefinitions.KEY_BOOK_CONDITION)
              .setRelatedFields(R.id.lbl_condition);
        fields.add(R.id.condition_cover, new TextAccessor<>(
                           new StringArrayResFormatter(context, R.array.conditions_dust_cover)),
                   DBDefinitions.KEY_BOOK_CONDITION_COVER)
              .setRelatedFields(R.id.lbl_condition_cover);

        fields.add(R.id.notes, new TextAccessor<>(htmlFormatter), DBDefinitions.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        fields.add(R.id.read_start, new TextAccessor<>(dateFormatter), DBDefinitions.KEY_READ_START)
              .setRelatedFields(R.id.lbl_read_start);
        fields.add(R.id.read_end, new TextAccessor<>(dateFormatter), DBDefinitions.KEY_READ_END)
              .setRelatedFields(R.id.lbl_read_end);

        fields.add(R.id.cbx_read, new CompoundButtonAccessor(), DBDefinitions.KEY_READ);

        fields.add(R.id.cbx_signed, new CompoundButtonAccessor(), DBDefinitions.KEY_SIGNED)
              .setRelatedFields(R.id.lbl_signed);

        fields.add(R.id.price_paid, new TextAccessor<>(moneyFormatter), DBDefinitions.KEY_PRICE_PAID
                  )
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

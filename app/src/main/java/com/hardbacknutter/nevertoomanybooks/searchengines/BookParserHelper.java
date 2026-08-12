/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.util.logger.LoggerFactory;

public class BookParserHelper {

    private final SearchEngineConfig config;
    private final LocaleListResolver localeListResolver;
    private final ISODateParser isoDateParser;

    private final IdentifierDao identifierDao;

    public BookParserHelper(@NonNull final SearchEngineConfig config) {
        this(config, LocaleListResolverDefault.INSTANCE);
    }

    public BookParserHelper(@NonNull final SearchEngineConfig config,
                            @NonNull final LocaleListResolver localeListResolver) {
        this.config = config;
        this.localeListResolver = localeListResolver;

        identifierDao = ServiceLocator.getInstance().getIdentifierDao();

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        isoDateParser = new ISODateParser(systemLocale);
    }

    @NonNull
    private DateParser<LocalDateTime> getFullDateParser(@NonNull final Context context,
                                                        @NonNull final Locale locale) {
        final List<Locale> allLocales = localeListResolver.resolveLocales(context, locale);
        return new FullDateParser(isoDateParser, allLocales);
    }

    /**
     * Add or merge the given Author with/to the list of Authors already present
     * on the book.
     *
     * @param currentAuthor     to add
     * @param currentAuthorRole role
     * @param book              Bundle to update
     * @param addAsFirst        set to {@code true} if new ones should
     *                          be added at the top of the list.
     *                          Otherwise, they are appended as normal.
     */
    public void addAuthor(@NonNull final Author currentAuthor,
                          @AuthorRole.Role final int currentAuthorRole,
                          @NonNull final Book book,
                          final boolean addAsFirst) {
        boolean add = true;
        // check if already present
        for (final Author author : book.getAuthors()) {
            if (author.equals(currentAuthor)) {
                // merge roles.
                author.addRole(currentAuthorRole);
                // merge identifiers
                final List<Identifier.Value> all = new ArrayList<>(author.getIdentifiers());
                all.addAll(currentAuthor.getIdentifiers());
                // We could have duplicate identifiers. Not supported,
                // Simply let the first id "win"
                identifierDao.pruneList(all);
                author.setIdentifiers(all);

                add = false;
                // keep looping
            }
        }

        if (add) {
            currentAuthor.addRole(currentAuthorRole);
            if (addAsFirst) {
                book.getAuthors().add(0, currentAuthor);
            } else {
                book.add(currentAuthor);
            }
        }
    }

    /**
     * Process the publication-date field according to the given site locale.
     * <p>
     * If the given date-string consists of 4 characters, it is assumed it's
     * a year-value and the simplified form will be set on the book.
     * Otherwise, full parsing is done.
     * <p>
     * Note that the input <strong>MUST</strong> be either a 4-digit year,
     * or a full-date string in one of the supported formats.
     * Partial date-strings will <strong>FAIL</strong>
     *
     * @param context Current context
     * @param locale  for parsing
     * @param dateStr the date field as retrieved
     * @param book    Bundle to update
     */
    public void addPublicationDate(@NonNull final Context context,
                                   @NonNull final Locale locale,
                                   @Nullable final String dateStr,
                                   @NonNull final Book book) {

        if (dateStr == null || dateStr.isBlank()) {
            return;
        }

        if (dateStr.length() == 4) {
            // we have a 4-digit year, use the simplified notation.
            try {
                book.setPublicationDate(Integer.parseInt(dateStr));
                return;
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore and continue with full parsing
            }
        }

        // error or not 4 digits? Do a full parse.
        getFullDateParser(context, locale)
                .parse(dateStr)
                .ifPresent(book::setPublicationDate);
    }

    /**
     * Process the price-listed field according to the given site locale.
     *
     * @param moneyParser for parsing
     * @param priceStr    the field as retrieved with or without currency embedded
     * @param currencyStr (optional) default currency string to use
     *                    when the priceStr does not have one
     * @param book        Bundle to update
     */
    public void addPriceListed(@NonNull final MoneyParser moneyParser,
                               @NonNull final String priceStr,
                               @Nullable final String currencyStr,
                               @NonNull final Book book) {

        // First ignore the given currency string (if any) and try parsing
        final Optional<Money> oMoney = moneyParser.parse(priceStr);
        if (oMoney.isPresent()) {
            Money money = oMoney.get();
            if (money.getCurrency() != null) {
                // We have parsed both the value+currency
                book.setPriceListed(money);
                return;

            } else if (currencyStr != null && !currencyStr.isBlank()) {
                try {
                    // use the default currency string
                    final Currency currency = Currency.getInstance(currencyStr);
                    money = new Money(money.getValue(), currency);
                    book.setPriceListed(money);
                    return;
                } catch (@NonNull final IllegalArgumentException ignore) {
                    // ignore
                }
            }
        }

        // Log this as we need to understand WHY it failed.
        LoggerFactory.getLogger().w(config.getEngineId().toString(),
                                    "processPriceListed failed priceStr=" + priceStr);
    }

    /**
     * Process the list of tag names, remove blank, duplicates and unwanted.
     *
     * @param tagNames to use
     * @param book     Bundle to update
     */
    public void setTags(@NonNull final Collection<String> tagNames,
                           @NonNull final Book book) {

        final Set<String> tagsToIgnore = config.getTagsToIgnore();
        final List<Tag> tags = tagNames.stream()
                                       .filter(t -> !t.isBlank())
                                       .filter(t -> !tagsToIgnore.contains(t))
                                       .distinct()
                                       .map(Tag::new)
                                       .collect(Collectors.toList());
        book.setTags(tags);
    }
}

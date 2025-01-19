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

package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

public class TagMapper
        implements Mapper {

    /** Genre string migration splitter characters. */
    private static final Pattern SPLITTER_PATTERN = Pattern.compile("[/,;>]");

    @Override
    public void map(@NonNull final Context context,
                    @NonNull final Book book) {
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        book.setTags(map(context, book.getTags(), locale));
    }

    /**
     * Process the list of tags according to the user registered mappings.
     *
     * @param context Current context
     * @param tags    to process
     * @param locale  for case manipulations
     *
     * @return list
     */
    @NonNull
    private List<Tag> map(@NonNull final Context context,
                          @NonNull final List<Tag> tags,
                          @NonNull final Locale locale) {
        if (tags.isEmpty()) {
            return List.of();
        }

        final Map<String, Set<String>> all =
                ServiceLocator.getInstance().getTagMappingDao().getAll();
        if (all.isEmpty()) {
            return List.of();
        }

        final TagDao tagDao = ServiceLocator.getInstance().getTagDao();
        final List<Tag> result = new ArrayList<>();
        tags.forEach(tag -> {
            final Set<String> replacement = all.get(tag.getName().toLowerCase(locale));
            if (replacement == null) {
                result.add(tag);
            } else {
                replacement.stream()
                           .map(Tag::new)
                           .forEach(result::add);
            }
        });

        tagDao.pruneList(context, result, tag -> locale);
        return result;
    }

    @NonNull
    public List<Tag> migrateGenre(@NonNull final Context context,
                                  @NonNull final String genre,
                                  @NonNull final Locale locale) {
        // sanity
        if (genre.isBlank()) {
            return List.of();
        }
        final List<Tag> tags = Arrays.stream(SPLITTER_PATTERN.split(genre))
                                     .map(String::strip)
                                     .map(Tag::new)
                                     .collect(Collectors.toList());
        return map(context, tags, locale);
    }
}

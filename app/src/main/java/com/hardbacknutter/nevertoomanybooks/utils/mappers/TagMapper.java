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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

/**
 * Maps tags case-insensitive to a set of other tags as defined by the user.
 */
public class TagMapper
        implements Mapper {

    private final List<TagMapping> mappings;
    private final Locale locale;
    private final TagDao tagDao;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public TagMapper(@NonNull final Context context) {
        locale = context.getResources().getConfiguration().getLocales().get(0);
        tagDao = ServiceLocator.getInstance().getTagDao();
        mappings = ServiceLocator.getInstance().getTagMappingDao().getAll();
    }

    @Override
    public void map(@NonNull final Context context,
                    @NonNull final Book book) {
        book.setTags(map(context, book.getTags()));
    }

    /**
     * Run the mappings on the given list.
     *
     * @param context Current context
     * @param source  tags to map
     *
     * @return mapped tags
     */
    @NonNull
    public List<Tag> map(@NonNull final Context context,
                         @NonNull final Collection<Tag> source) {
        if (source.isEmpty() || mappings.isEmpty()) {
            return List.of();
        }

        final List<Tag> result = new ArrayList<>();
        source.forEach(tag -> {
            final List<Tag> replacement = mappings
                    .stream()
                    .filter(tm -> tm.getName().equalsIgnoreCase(tag.getName()))
                    .flatMap(tm -> tm.getMappings().stream())
                    .map(Tag::new)
                    .collect(Collectors.toList());

            if (replacement.isEmpty()) {
                result.add(tag);
            } else {
                result.addAll(replacement);
            }
        });

        tagDao.pruneList(context, result, tag -> locale);
        return result;
    }
}

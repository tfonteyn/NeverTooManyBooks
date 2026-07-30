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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagMappingDao;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;
import com.hardbacknutter.util.logger.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public class TagAdminViewModel
        extends ViewModel {

    private static final String TAG = "TagAdminViewModel";

    private final TagMapperTask mapperTask = new TagMapperTask();
    private final TagDao tagDao;
    private final TagMappingDao tagMappingDao;
    private final List<TagMapping> mappings;
    private final List<Tag> tags;

    private boolean modified;

    /**
     * Constructor.
     */
    public TagAdminViewModel() {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        tagDao = serviceLocator.getTagDao();
        tagMappingDao = serviceLocator.getTagMappingDao();

        tags = tagDao.getAll();
        mappings = tagMappingDao.getAll();
    }

    @Override
    protected void onCleared() {
        mapperTask.cancel();
    }

    void reloadTags() {
        tags.clear();
        tags.addAll(tagDao.getAll());
    }

    @NonNull
    List<Tag> getTags() {
        return tags;
    }

    /**
     * Case-sensitive.
     *
     * @param tagName to find
     *
     * @return position, or {@code -1} if not found
     */
    @IntRange(from = -1)
    int findTagPosition(@NonNull final String tagName) {
        return tags.stream()
                   .map(Tag::getName)
                   .collect(Collectors.toList())
                   .indexOf(tagName);
    }

    /**
     * Insert the given {@link Tag} into the database; update the cached list.
     *
     * @param tag to insert
     *
     * @return the <strong>position</strong> in the list
     *
     * @throws DaoWriteException on failure
     */
    int insert(@NonNull final Tag tag)
            throws DaoWriteException {

        tagDao.insert(tag);

        // find insertion point using a brute-force sequential search...
        int position = 0;
        while (position < tags.size() && tags.get(position).compareTo(tag) < 0) {
            position++;
        }
        tags.add(position, tag);

        return position;
    }

    void update(@NonNull final Tag tag)
            throws DaoWriteException {
        tagDao.update(tag);
    }

    void deleteTag(final int position) {
        final Tag tag = tags.remove(position);
        tagDao.delete(tag);
        setModified();
    }

    boolean moveBooks(@NonNull final Context context,
                      final int position,
                      final int existingPos) {
        final Tag source = tags.get(position);
        final Tag target = tags.get(existingPos);
        try {
            tagDao.moveBooks(context, source, target);
            tags.remove(position);
            return true;
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, source);
            return false;
        }
    }

    @NonNull
    List<TagMapping> getTagMappings() {
        // used directly by the adapter.
        return mappings;
    }

    /**
     * Case-sensitive.
     *
     * @param mappingName to find
     *
     * @return position, or {@code -1} if not found
     */
    @IntRange(from = -1)
    int findTagMappingPosition(@NonNull final String mappingName) {
        return mappings.stream()
                       .map(TagMapping::getTagName)
                       .collect(Collectors.toList())
                       .indexOf(mappingName);
    }

    /**
     * Insert the given {@link TagMapping} into the database; update the cached list.
     *
     * @param tagMapping to insert
     *
     * @return the <strong>position</strong> in the list
     *
     * @throws DaoWriteException on failure
     */
    int insert(@NonNull final TagMapping tagMapping)
            throws DaoWriteException {

        tagMappingDao.insert(tagMapping);

        // find insertion point using a brute-force sequential search...
        int position = 0;
        while (position < mappings.size()
               && mappings.get(position).compareTo(tagMapping) < 0) {
            position++;
        }
        mappings.add(position, tagMapping);

        return position;
    }

    void update(@NonNull final TagMapping tagMapping)
            throws DaoWriteException {
        tagMappingDao.update(tagMapping);
    }

    void deleteTagMapping(final int position) {
        final TagMapping mapping = mappings.remove(position);
        tagMappingDao.delete(mapping);
        setModified();
    }

    boolean isModified() {
        return modified;
    }

    void setModified() {
        this.modified = true;
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onTagMapperFailure() {
        return mapperTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Map<TagMapperTask.Options, Integer>>> onTagMapperCancelled() {
        return mapperTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<Map<TagMapperTask.Options, Integer>>> onTagMapperFinished() {
        return mapperTask.onFinished();
    }

    void startTagMapper(@NonNull final Set<TagMapperTask.Options> options) {
        mapperTask.start(options);
    }

    void cancelTagMapper() {
        mapperTask.cancel();
    }

    int countBooks(@NonNull final Tag tag) {
        return tagDao.countBooks(tag);
    }
}

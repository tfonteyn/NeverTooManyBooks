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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.DetailScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListScreenBookFields;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;

/**
 * It's not possible to use PreferenceDataStore for individual preferences:
 * FIXME: https://issuetracker.google.com/issues/232206237
 */
public class StyleDataStore
        extends PreferenceDataStore {

    @NonNull
    private final UserStyle style;

    private boolean modified;

    StyleDataStore(@NonNull final UserStyle style) {
        this.style = style;
    }

    public boolean isModified() {
        return modified;
    }

    @Override
    public void putInt(@NonNull final String key,
                       final int value) {
        switch (key) {
            case BooklistStyle.PK_LEVELS_EXPANSION:
                style.setExpansionLevel(value);
                break;

            case ListScreenBookFields.PK_COVER_SCALE:
                style.getListScreenBookFields().setCoverScale(value);
                break;

            case TextScale.PK_TEXT_SCALE:
                style.getTextScale().setScale(value);
                break;

            default:
                super.putInt(key, value);
                break;
        }
        modified = true;
    }

    @Override
    public int getInt(@NonNull final String key,
                      final int defValue) {
        switch (key) {
            case BooklistStyle.PK_LEVELS_EXPANSION:
                return style.getExpansionLevel();

            case ListScreenBookFields.PK_COVER_SCALE:
                return style.getListScreenBookFields().getCoverScale();

            case TextScale.PK_TEXT_SCALE:
                return style.getTextScale().getScale();

            default:
                return super.getInt(key, defValue);
        }
    }

    @Override
    public void putBoolean(final String key,
                           final boolean value) {
        switch (key) {
            case ListScreenBookFields.PK_COVERS:
            case ListScreenBookFields.PK_AUTHOR:
            case ListScreenBookFields.PK_PUBLISHER:
            case ListScreenBookFields.PK_PUB_DATE:
            case ListScreenBookFields.PK_FORMAT:
            case ListScreenBookFields.PK_LOCATION:
            case ListScreenBookFields.PK_RATING:
            case ListScreenBookFields.PK_BOOKSHELVES:
            case ListScreenBookFields.PK_ISBN:
                style.getListScreenBookFields().setValue(key, value);
                break;

            case BooklistStyle.PK_SCALE_GROUP_ROW:
                style.setUseGroupRowPreferredHeight(value);
                break;

            case BooklistStyle.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                style.setShowAuthorByGivenName(value);
                break;

            case BooklistStyle.PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                style.setSortAuthorByGivenName(value);
                break;

            case AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachAuthor(value);
                break;

            case SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachSeries(value);
                break;

            case PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachPublisher(value);
                break;

            case BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachBookshelf(value);
                break;

            default:
                if (key.equals(DetailScreenBookFields.PK_COVER[0])
                    || key.equals(DetailScreenBookFields.PK_COVER[1])) {
                    style.getDetailScreenBookFields().setValue(key, value);
                } else {
                    super.putBoolean(key, value);
                }
                break;
        }
        modified = true;
    }

    @Override
    public boolean getBoolean(final String key,
                              final boolean defValue) {
        switch (key) {
            case ListScreenBookFields.PK_COVERS:
            case ListScreenBookFields.PK_AUTHOR:
            case ListScreenBookFields.PK_PUBLISHER:
            case ListScreenBookFields.PK_PUB_DATE:
            case ListScreenBookFields.PK_FORMAT:
            case ListScreenBookFields.PK_LOCATION:
            case ListScreenBookFields.PK_RATING:
            case ListScreenBookFields.PK_BOOKSHELVES:
            case ListScreenBookFields.PK_ISBN:
                return style.getListScreenBookFields().getValue(key);

            case BooklistStyle.PK_SCALE_GROUP_ROW:
                return style.getUseGroupRowPreferredHeight();

            case BooklistStyle.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                return style.isShowAuthorByGivenName();

            case BooklistStyle.PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                return style.isSortAuthorByGivenName();

            case AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachAuthor();

            case SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachSeries();

            case PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachPublisher();

            case BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachBookshelf();

            default:
                if (key.equals(DetailScreenBookFields.PK_COVER[0])
                    || key.equals(DetailScreenBookFields.PK_COVER[1])) {
                    return style.getDetailScreenBookFields().getValue(key);
                }
                return super.getBoolean(key, defValue);
        }
    }

    @Override
    public void putString(@NonNull final String key,
                          @Nullable final String value) {
        switch (key) {
            case UserStyle.PK_STYLE_NAME:
                style.setName(value);
                break;

            default:
                super.putString(key, value);
                break;
        }
        modified = true;
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        switch (key) {
            case UserStyle.PK_STYLE_NAME:
                return style.getName();

            default:
                return super.getString(key, defValue);
        }
    }

    @Override
    public void putStringSet(@NonNull final String key,
                             @Nullable final Set<String> values) {
        switch (key) {
            case BooklistStyle.PK_LIST_HEADER:
                style.setShowHeaderInfo(values);
                break;

            case AuthorBooklistGroup.PK_PRIMARY_TYPE:
                style.setPrimaryAuthorTypes(values);
                break;

            default:
                super.putStringSet(key, values);
                break;
        }
        modified = true;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        switch (key) {
            case BooklistStyle.PK_LIST_HEADER:
                return style.getShowHeaderInfo();

            case AuthorBooklistGroup.PK_PRIMARY_TYPE:
                return style.getPrimaryAuthorTypes();

            default:
                return super.getStringSet(key, defValues);
        }
    }
}

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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;

/**
 * Specialized BooklistGroup representing a {@link Publisher} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomain()} returns a customized display domain
 * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
 */
public class PublisherBooklistGroup
        extends BooklistGroup {

    /** Style - PreferenceScreen/PreferenceCategory Key. */
    private static final String PSK_STYLE_PUBLISHER = "psk_style_publisher";

    private static final String PK_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.publisher.show.all";

    /** Customized domain with display data. */
    @NonNull
    private final DomainExpression mDisplayDomain;
    /** Show a book under each {@link Publisher} it is linked to. */
    private PBoolean mUnderEach;

    /**
     * Constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     */
    PublisherBooklistGroup(final boolean isPersistent,
                           @NonNull final ListStyle style) {
        super(PUBLISHER, isPersistent, style);
        mDisplayDomain = createDisplayDomain();

        initPrefs();
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent flag
     * @param style Style reference.
     * @param group to copy from
     */
    PublisherBooklistGroup(final boolean isPersistent,
                           @NonNull final ListStyle style,
                           @NonNull final PublisherBooklistGroup group) {
        super(isPersistent, style, group);
        mDisplayDomain = createDisplayDomain();

        mUnderEach = new PBoolean(mPersisted, mPersistence, group.mUnderEach);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    public static boolean showBooksUnderEachDefault(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(PK_SHOW_BOOKS_UNDER_EACH, false);
    }

    private void initPrefs() {
        mUnderEach = new PBoolean(mPersisted, mPersistence, PK_SHOW_BOOKS_UNDER_EACH);
    }

    @NonNull
    private DomainExpression createDisplayDomain() {
        // Not sorted; we sort on the OB domain as defined in the GroupKey.
        return new DomainExpression(DOM_PUBLISHER_NAME, TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME));
    }

    @NonNull
    @Override
    public DomainExpression getDisplayDomain() {
        return mDisplayDomain;
    }

    @NonNull
    @Override
    @CallSuper
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = super.getRawPreferences();
        map.put(mUnderEach.getKey(), mUnderEach);
        return map;
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(PSK_STYLE_PUBLISHER);
        if (category != null) {
            final String[] keys = {PK_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    /**
     * Get this preference.
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    public boolean showBooksUnderEach() {
        return mUnderEach.isTrue();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final PublisherBooklistGroup that = (PublisherBooklistGroup) o;
        return mDisplayDomain.equals(that.mDisplayDomain)
               && Objects.equals(mUnderEach, that.mUnderEach);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mDisplayDomain, mUnderEach);
    }

    @Override
    @NonNull
    public String toString() {
        return "PublisherBooklistGroup{"
               + super.toString()
               + ", mDisplayDomain=" + mDisplayDomain
               + ", mUnderEach=" + mUnderEach
               + '}';
    }
}

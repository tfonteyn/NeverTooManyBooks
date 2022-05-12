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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;

/**
 * Support for Foreign Key fields.
 */
public abstract class AbstractLinkedTableBooklistGroup
        extends BooklistGroup {

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression mDisplayDomainExpression;

    /** Show a book under each item it is linked to. */
    @NonNull
    private final PBoolean mUnderEach;

    AbstractLinkedTableBooklistGroup(@Id final int id,
                                     final boolean isPersistent,
                                     @NonNull final ListStyle style,
                                     @NonNull final String pkUnderEach) {
        super(id, isPersistent, style);
        mDisplayDomainExpression = createDisplayDomainExpression();

        mUnderEach = new PBoolean(mPersisted, mPersistenceLayer, pkUnderEach, false);
    }

    AbstractLinkedTableBooklistGroup(final boolean isPersistent,
                                     @NonNull final ListStyle style,
                                     @NonNull final AbstractLinkedTableBooklistGroup group) {
        super(isPersistent, style, group);
        mDisplayDomainExpression = createDisplayDomainExpression();

        mUnderEach = new PBoolean(mPersisted, mPersistenceLayer, group.mUnderEach);
    }

    @NonNull
    protected abstract DomainExpression createDisplayDomainExpression();

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return mDisplayDomainExpression;
    }

    /**
     * Get this preference.
     *
     * @return {@code true} if we want to show a book under each of its linked items.
     */
    public boolean showBooksUnderEach() {
        return mUnderEach.isTrue();
    }

    public void setShowBooksUnderEach(final boolean value) {
        mUnderEach.set(value);
    }

    @Override
    @CallSuper
    @NonNull
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = super.getRawPreferences();
        map.put(mUnderEach.getKey(), mUnderEach);
        return map;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!super.equals(o)) {
            return false;
        }
        final AbstractLinkedTableBooklistGroup that = (AbstractLinkedTableBooklistGroup) o;
        return Objects.equals(mDisplayDomainExpression, that.mDisplayDomainExpression)
               && Objects.equals(mUnderEach, that.mUnderEach);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mDisplayDomainExpression, mUnderEach);
    }

    @Override
    @NonNull
    public String toString() {
        return super.toString()
               + ", mDisplayDomainExpression=" + mDisplayDomainExpression
               + ", mUnderEach=" + mUnderEach;
    }
}

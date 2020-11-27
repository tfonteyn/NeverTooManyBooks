/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * StringList factory for a Publisher.
 * <ul>Format:
 *      <li>Name</li>
 * </ul>
 *
 * <strong>Note:</strong> In the format definition, the " * {json}" suffix is optional
 * and can be missing.
 */
public class PublisherCoder
        implements StringList.Factory<Publisher> {

    @NonNull
    private final char[] mEscapeChars = {'(', ')'};

    @Override
    @NonNull
    public Publisher decode(@NonNull final String element) {
        return Publisher.from(element);
    }

    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    @NonNull
    @Override
    public String encode(@NonNull final Publisher publisher) {
        return escape(publisher.getName(), mEscapeChars);
    }
}

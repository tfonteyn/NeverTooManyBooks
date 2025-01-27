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

package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

public class IdentifierField<V extends EditText>
        extends EditTextField<String, V> {

    public IdentifierField(@NonNull final FragmentId fragmentId,
                           final int fieldViewId,
                           @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey);
    }

    public IdentifierField(@NonNull final FragmentId fragmentId,
                           final int fieldViewId,
                           @NonNull final String fieldKey,
                           @NonNull final FieldFormatter<String> formatter,
                           final boolean enableReformat) {
        super(fragmentId, fieldViewId, fieldKey, formatter, enableReformat);
    }

    @Override
    void internalSave(@NonNull final DataManager target) {
        ((Book) target).setIdentifierValue(getFieldKey(), getValue());
    }

    @Override
    public void load(@NonNull final Context context,
                     @NonNull final DataManager source,
                     @NonNull final RealNumberParser realNumberParser) {
        internalLoad(((Book) source).getIdentifierValue(getFieldKey()).orElse(null));
    }
}

/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Format.
 */
public class EditFormatDialogFragment
        extends EditStringBaseDialogFragment {

    /**
     * No-arg constructor for OS use.
     */
    public EditFormatDialogFragment() {
        super(R.string.lbl_format, R.string.lbl_format);
    }

    @NonNull
    @Override
    protected List<String> getList() {
        return ServiceLocator.getInstance().getFormatDao().getList();
    }

    @Override
    @NonNull
    String onSave(@NonNull final String originalText,
                  @NonNull final String currentText) {
        ServiceLocator.getInstance().getFormatDao().rename(originalText, currentText);
        return currentText;
    }
}

/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.text.InputType;
import android.widget.EditText;

import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EditTextFieldTest
        extends BaseDBTest {

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void mask() {

        final EditText editText = new EditText(context);

        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        // default is 'words', overrides 'nothing'
        EditTextField.Capitalization.Title.apply(editText);
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_CLASS_TEXT,
                     editText.getInputType());

        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditTextField.Capitalization.Title.apply(editText);
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                     editText.getInputType());
    }

    @Test
    public void setting() {

        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         // "1" words
                         .putString(EditTextField.Capitalization.Title.getPrefKey(), "1")
                         .apply();

        final EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);

        int before = editText.getInputType();
        EditTextField.Capitalization.Title.apply(editText);

        assertEquals(before | InputType.TYPE_TEXT_FLAG_CAP_WORDS, editText.getInputType());

    }
}
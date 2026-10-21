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

package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditTextFieldTest
        extends BaseDBTest {

    @Test
    void mask() {
        final Context context = createContext();

        final EditText editText = new EditText(context);

        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        // default is 'words', overrides 'nothing'
        EditTextField.Capitalization.Title.apply(editText);
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_CLASS_TEXT,
                     editText.getInputType());

        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditTextField.Capitalization.Title.apply(editText);
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS, editText.getInputType());
    }

    @Test
    void setting() {
        final Context context = createContext();

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      // "1" words
                      .putString(EditTextField.Capitalization.Title.getPrefKey(), "1")
                      .apply();

        final EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);

        final int before = editText.getInputType();
        EditTextField.Capitalization.Title.apply(editText);

        assertEquals(before | InputType.TYPE_TEXT_FLAG_CAP_WORDS, editText.getInputType());
    }
}
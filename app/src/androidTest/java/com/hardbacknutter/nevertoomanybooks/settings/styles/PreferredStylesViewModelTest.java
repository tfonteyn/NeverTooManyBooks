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

package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("MissingJavadoc")
public class PreferredStylesViewModelTest
        extends BaseDBTest {

    private static final String NAME_CLONE_BUILTIN = "CLONE_BUILTIN";
    private static final String NAME_CLONE_USER = "CLONE_USER";
    /**
     * LiveData requirement.
     */
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();
    private PreferredStylesViewModel listVm;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        final Style aDefault = ServiceLocator.getInstance().getStyles().getDefault();
        listVm = new PreferredStylesViewModel();
        listVm.init(createArgs(aDefault));
    }

    @AfterClass
    public static void afterwards() {
        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
        // delete any user-styles we created in previous tests.
        for (final String prefix : List.of(NAME_CLONE_BUILTIN, NAME_CLONE_USER)) {
            stylesHelper.getStyles(true)
                        .stream()
                        .filter(style -> style.getType() == StyleType.User)
                        .map(style -> (UserStyle) style)
                        .filter(userStyle -> userStyle.getName().startsWith(prefix))
                        .forEach(stylesHelper::delete);
        }
    }

    @NonNull
    private Bundle createArgs(@NonNull final Style style) {
        final String uuid = style.getUuid();
        final Bundle args = new Bundle();
        args.putString(Style.BKEY_UUID, uuid);
        return args;
    }

    @Test
    public void cloneBuiltinAsPreferred() {
        cloneBuiltin(true);
    }

    @Test
    public void cloneBuiltinAsNotPreferred() {
        cloneBuiltin(false);
    }

    private void cloneBuiltin(final boolean asPreferred) {
        listVm.refreshStyleList();

        final List<Style> styleList = listVm.getStyleList();
        final int initialSize = styleList.size();
        // sanity check
        assertTrue(initialSize > 10);

        // Find a random builtin style
        Style initialStyle;
        int initialPosition = styleList.size() - 10;
        initialStyle = styleList.get(initialPosition);
        try {
            // Skip all entries until we find a Builtin style.
            while (initialStyle.getType() != StyleType.Builtin) {
                initialStyle = styleList.get(++initialPosition);
            }
        } catch (@NonNull final IndexOutOfBoundsException e) {
            fail("No Builtin styles?");
        }

        initialStyle.setPreferred(asPreferred);

        // Prepare editing
        final StyleViewModel styleVm = new StyleViewModel();
        final Bundle args = createArgs(initialStyle);
        args.putBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED, initialStyle.isPreferred());
        args.putInt(EditStyleContract.BKEY_ACTION, EditStyleContract.ACTION_CLONE);
        styleVm.init(context, args);
        assertNotNull(styleVm.getStyleDataStore());


        // Test the prepared style
        final WritableStyle editedStyle = styleVm.getStyle();
        // It's not saved to the db yet
        assertEquals(0, editedStyle.getId());
        // but should have a new UUID assigned to it
        assertNotEquals(initialStyle.getUuid(), editedStyle.getUuid());


        // Modify the name, just to modify 'something'
        final String modifiedName = NAME_CLONE_BUILTIN + System.nanoTime();
        styleVm.getStyleDataStore().putString(StyleDataStore.PK_NAME, modifiedName);
        // pretend leaving the style-editor, this will trigger a call to:
        final boolean dbResult = styleVm.insertOrUpdateStyle(context);
        assertTrue(dbResult);

        final long editedStyleId = editedStyle.getId();
        final String editedStyleUuid = editedStyle.getUuid();
        final String editedStyleName = editedStyle.getLabel(context);
        final boolean editedStylePreferred = editedStyle.isPreferred();

        listVm.onStyleEdited(context, editedStyle, initialStyle.getUuid());

        // We added a style
        assertEquals(initialSize + 1, styleList.size());

        // The new one should be at the same position
        final Style addedStyle = listVm.getStyle(initialPosition);
        assertEquals(addedStyle.getId(), editedStyleId);
        assertEquals(addedStyle.getUuid(), editedStyleUuid);
        assertEquals(modifiedName, editedStyleName);
        assertEquals(addedStyle.isPreferred(), editedStylePreferred);

        // The initial one should be demoted
        assertFalse(initialStyle.isPreferred());

        final int movedInitialPosition = listVm.findRow(initialStyle);
        if (asPreferred) {
            // The original one should now be at the end of the list
            assertEquals(listVm.getStyleList().size() - 1, movedInitialPosition);
        } else {
            // The original one should now be one lower on the list
            assertEquals(initialPosition + 1, movedInitialPosition);
        }
    }

    /**
     * Relies on finding a user-defined style!
     */
    @Test
    public void cloneUserDefined() {
        listVm.refreshStyleList();

        final List<Style> styleList = listVm.getStyleList();
        final int initialSize = styleList.size();
        // sanity check
        assertTrue(initialSize > 10);

        // Find a random user-defined style
        Style initialStyle;
        int initialPosition = 0;
        initialStyle = styleList.get(initialPosition);
        try {
            // Skip all entries until we find a User style.
            while (initialStyle.getType() != StyleType.User) {
                initialStyle = styleList.get(++initialPosition);
            }
        } catch (@NonNull final IndexOutOfBoundsException e) {
            fail("There were no user-defined styles. Create one and restart the test.");
        }

        // Prepare editing
        final StyleViewModel styleVm = new StyleViewModel();
        final Bundle args = createArgs(initialStyle);
        args.putBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED, initialStyle.isPreferred());
        args.putInt(EditStyleContract.BKEY_ACTION, EditStyleContract.ACTION_CLONE);
        styleVm.init(context, args);
        assertNotNull(styleVm.getStyleDataStore());


        // Test the prepared style
        final WritableStyle editedStyle = styleVm.getStyle();
        // It's not saved to the db yet
        assertEquals(0, editedStyle.getId());
        // but should have a new UUID assigned to it
        assertNotEquals(initialStyle.getUuid(), editedStyle.getUuid());


        // Modify the name, just to modify 'something'
        final String modifiedName = NAME_CLONE_USER + System.nanoTime();
        styleVm.getStyleDataStore().putString(StyleDataStore.PK_NAME, modifiedName);
        // pretend leaving the style-editor, this will trigger a call to:
        styleVm.insertOrUpdateStyle(context);

        final long editedStyleId = editedStyle.getId();
        final String editedStyleUuid = editedStyle.getUuid();
        final String editedStyleName = editedStyle.getLabel(context);
        final boolean editedStylePreferred = editedStyle.isPreferred();

        listVm.onStyleEdited(context, editedStyle, initialStyle.getUuid());

        // We added a style
        assertEquals(initialSize + 1, styleList.size());

        // The new one should be at the same position
        final Style addedStyle = listVm.getStyle(initialPosition);
        assertEquals(addedStyle.getId(), editedStyleId);
        assertEquals(addedStyle.getUuid(), editedStyleUuid);
        assertEquals(modifiedName, editedStyleName);
        assertEquals(addedStyle.isPreferred(), editedStylePreferred);

        final int movedInitialPosition = listVm.findRow(initialStyle);
        // The original one should now be one lower on the list
        assertEquals(initialPosition + 1, movedInitialPosition);
    }

    /**
     * Relies on finding a user-defined style!
     */
    @Test
    public void editExisting() {
        listVm.refreshStyleList();

        final List<Style> styleList = listVm.getStyleList();
        final int initialSize = styleList.size();
        // sanity check
        assertTrue(initialSize > 10);

        // Find a random user-defined style
        Style initialStyle;
        int initialPosition = 0;
        initialStyle = styleList.get(initialPosition);
        try {
            // Skip all entries until we find a User style.
            while (initialStyle.getType() != StyleType.User) {
                initialStyle = styleList.get(++initialPosition);
            }
        } catch (@NonNull final IndexOutOfBoundsException e) {
            fail("There were no user-defined styles. Create one and restart the test.");
        }

        // Prepare editing
        final StyleViewModel styleVm = new StyleViewModel();
        final Bundle args = createArgs(initialStyle);
        args.putBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED, initialStyle.isPreferred());
        args.putInt(EditStyleContract.BKEY_ACTION, EditStyleContract.ACTION_EDIT);
        styleVm.init(context, args);
        assertNotNull(styleVm.getStyleDataStore());


        // Test the prepared style
        final WritableStyle editedStyle = styleVm.getStyle();
        // id/uuid is kept
        assertEquals(initialStyle.getId(), editedStyle.getId());
        assertEquals(initialStyle.getUuid(), editedStyle.getUuid());


        // Modify the name, just to modify 'something'
        final String modifiedName = NAME_CLONE_USER + System.nanoTime();
        styleVm.getStyleDataStore().putString(StyleDataStore.PK_NAME, modifiedName);
        // pretend leaving the style-editor, this will trigger a call to:
        styleVm.insertOrUpdateStyle(context);

        final long editedStyleId = editedStyle.getId();
        final String editedStyleUuid = editedStyle.getUuid();
        final String editedStyleName = editedStyle.getLabel(context);
        final boolean editedStylePreferred = editedStyle.isPreferred();

        listVm.onStyleEdited(context, editedStyle, initialStyle.getUuid());

        // We only edited a style
        assertEquals(initialSize, styleList.size());

        // The new one should be at the same position
        final Style addedStyle = listVm.getStyle(initialPosition);
        assertEquals(addedStyle.getId(), editedStyleId);
        assertEquals(addedStyle.getUuid(), editedStyleUuid);
        assertEquals(modifiedName, editedStyleName);
        assertEquals(addedStyle.isPreferred(), editedStylePreferred);
    }
}

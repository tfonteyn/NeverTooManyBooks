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

package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.InstantTaskExecutorExtension;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** LiveData requirement: {@code @ExtendWith(InstantTaskExecutorExtension.class)} */
@ExtendWith(InstantTaskExecutorExtension.class)
@SuppressWarnings("StringConcatenationMissingWhitespace")
class PreferredStylesViewModelTest
        extends BaseDBTest {

    private static final String NAME_CLONE_BUILTIN = "CLONE_BUILTIN";
    private static final String NAME_CLONE_USER = "CLONE_USER";

    private PreferredStylesViewModel listVm;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
        // delete any user-styles we created in previous tests.
        for (final String prefix : List.of(NAME_CLONE_BUILTIN, NAME_CLONE_USER)) {
            stylesHelper.getStyles(true)
                        .stream()
                        .filter(style -> style.getType() == Style.Type.User)
                        .map(style -> (UserStyle) style)
                        .filter(userStyle -> userStyle.getName().startsWith(prefix))
                        .forEach(stylesHelper::delete);
        }

        final Style aDefault = stylesHelper.getDefault();
        listVm = new PreferredStylesViewModel();
        final Bundle args = new Bundle(1);
        args.putString(Style.BKEY_UUID, aDefault.getUuid());
        listVm.init(args);
    }

    @Test
    void cloneUserDefinedAsPreferred() {
        cloneBuiltin(true);
        cloneUserDefined(true);
    }

    @Test
    void cloneUserDefinedAsNotPreferred() {
        cloneBuiltin(false);
        cloneUserDefined(false);
    }

    @Test
    void editExistingAsPreferred() {
        cloneBuiltin(true);
        editExisting(true);
    }

    @Test
    void editExistingAsNotPreferred() {
        cloneBuiltin(false);
        editExisting(false);
    }


    private void cloneBuiltin(final boolean asPreferred) {
        listVm.refreshList();

        final List<Style> styleList = listVm.getList();
        final int initialSize = styleList.size();
        // sanity check
        assertTrue(initialSize > 10);

        // Find a random built-in Style
        int initialPosition = 3;
        Style initialStyle = styleList.get(initialPosition);
        try {
            // Skip all entries until we find a Builtin style.
            while (initialStyle.getType() != Style.Type.Builtin) {
                initialStyle = styleList.get(++initialPosition);
            }
        } catch (@NonNull final IndexOutOfBoundsException e) {
            fail("No Builtin styles?");
        }

        initialStyle.setPreferred(asPreferred);

        // Prepare editing
        final StyleViewModel styleVm = initVm(
                initialStyle, EditStyleContract.ACTION_CLONE);


        // Test the prepared style
        final Style editedStyle = styleVm.getStyle();
        // It's not saved to the db yet
        assertEquals(0, editedStyle.getId());
        // but should have a new UUID assigned to it
        assertNotEquals(initialStyle.getUuid(), editedStyle.getUuid());


        // Modify the name, just to modify 'something'
        final String modifiedName = NAME_CLONE_BUILTIN + System.nanoTime();
        styleVm.getStyleDataStore().putString(StyleDataStore.PK_NAME, modifiedName);
        // pretend leaving the style-editor, this will trigger a call to:
        final StyleViewModel.Saved dbResult = styleVm.insertOrUpdateStyle(context);
        assertTrue(dbResult.isSuccess());
        assertTrue(dbResult.isModified());

        listVm.onStyleEdited(context, editedStyle.getUuid(), initialStyle.getUuid());

        // We added a style
        assertEquals(initialSize + 1, styleList.size());

        // the vm reloaded the list from the database, resorting "preferred"
        // styles at the top.

        if (!asPreferred) {
            // The new one should be at the same position
            final Style addedStyle = listVm.getStyle(initialPosition);
            assertEquals(addedStyle.getId(), editedStyle.getId());
            assertEquals(addedStyle.getUuid(), editedStyle.getUuid());
            assertEquals(modifiedName, editedStyle.getLabel(context));
            assertEquals(addedStyle.isPreferred(), editedStyle.isPreferred());
        }

        // The initial one should be demoted
        assertFalse(initialStyle.isPreferred());

        final int movedInitialPosition = listVm.findPosition(initialStyle.getUuid());
        // The original one should now be one lower on the list
        assertEquals(initialPosition + 1, movedInitialPosition);
    }

    private void cloneUserDefined(final boolean asPreferred) {
        listVm.refreshList();

        final List<Style> styleList = listVm.getList();
        final int initialSize = styleList.size();
        // sanity check
        assertTrue(initialSize > 10);

        // Find a random user-defined style
        int initialPosition = 0;
        Style initialStyle = styleList.get(initialPosition);
        try {
            // Skip all entries until we find a User style.
            while (initialStyle.getType() != Style.Type.User) {
                initialStyle = styleList.get(++initialPosition);
            }
        } catch (@NonNull final IndexOutOfBoundsException e) {
            fail("There were no user-defined styles. Create one and restart the test.");
        }

        initialStyle.setPreferred(asPreferred);

        // Prepare editing
        final StyleViewModel styleVm = initVm(
                initialStyle, EditStyleContract.ACTION_CLONE);


        // Test the prepared style
        final Style editedStyle = styleVm.getStyle();
        // It's not saved to the db yet
        assertEquals(0, editedStyle.getId());
        // but should have a new UUID assigned to it
        assertNotEquals(initialStyle.getUuid(), editedStyle.getUuid());


        // Modify the name, just to modify 'something'
        final String modifiedName = NAME_CLONE_USER + System.nanoTime();
        styleVm.getStyleDataStore().putString(StyleDataStore.PK_NAME, modifiedName);
        // pretend leaving the style-editor, this will trigger a call to:
        final StyleViewModel.Saved dbResult = styleVm.insertOrUpdateStyle(context);
        assertTrue(dbResult.isSuccess());
        assertTrue(dbResult.isModified());

        listVm.onStyleEdited(context, editedStyle.getUuid(), initialStyle.getUuid());

        // We added a style
        assertEquals(initialSize + 1, styleList.size());

        // the vm reloaded the list from the database, resorting "preferred"
        // styles at the top.

        if (!asPreferred) {
            // The new one should be at the same position
            final Style addedStyle = listVm.getStyle(initialPosition);
            assertEquals(addedStyle.getId(), editedStyle.getId());
            assertEquals(addedStyle.getUuid(), editedStyle.getUuid());
            assertEquals(modifiedName, editedStyle.getLabel(context));
            assertEquals(addedStyle.isPreferred(), editedStyle.isPreferred());
        }

        // The initial one should not be changed
        assertEquals(asPreferred, initialStyle.isPreferred());

        final int movedInitialPosition = listVm.findPosition(initialStyle.getUuid());
        // The original one should now be one lower on the list
        assertEquals(initialPosition + 1, movedInitialPosition);
    }

    /**
     * Relies on finding a user-defined style!
     */
    private void editExisting(final boolean asPreferred) {
        listVm.refreshList();

        final List<Style> styleList = listVm.getList();
        final int initialSize = styleList.size();
        // sanity check
        assertTrue(initialSize > 10);

        // Find a random user-defined style
        int initialPosition = 0;
        Style initialStyle = styleList.get(initialPosition);
        try {
            // Skip all entries until we find a User style.
            while (initialStyle.getType() != Style.Type.User) {
                initialStyle = styleList.get(++initialPosition);
            }
        } catch (@NonNull final IndexOutOfBoundsException e) {
            fail("There were no user-defined styles. Create one and restart the test.");
        }

        initialStyle.setPreferred(asPreferred);

        // Prepare editing
        final StyleViewModel styleVm = initVm(
                initialStyle, EditStyleContract.ACTION_EDIT);


        // Test the prepared style
        final Style editedStyle = styleVm.getStyle();
        // id/uuid is kept
        assertEquals(initialStyle.getId(), editedStyle.getId());
        assertEquals(initialStyle.getUuid(), editedStyle.getUuid());


        // Modify the name, just to modify 'something'
        final String modifiedName = NAME_CLONE_USER + System.nanoTime();
        styleVm.getStyleDataStore().putString(StyleDataStore.PK_NAME, modifiedName);
        // pretend leaving the style-editor, this will trigger a call to:
        final StyleViewModel.Saved dbResult = styleVm.insertOrUpdateStyle(context);
        assertTrue(dbResult.isSuccess());
        assertTrue(dbResult.isModified());

        listVm.onStyleEdited(context, editedStyle.getUuid(), initialStyle.getUuid());

        // We only edited a style
        assertEquals(initialSize, styleList.size());

        // the vm reloaded the list from the database, resorting "preferred"
        // styles at the top.

        if (!asPreferred) {
            // The new one should be at the same position
            final Style addedStyle = listVm.getStyle(initialPosition);
            assertEquals(addedStyle.getId(), editedStyle.getId());
            assertEquals(addedStyle.getUuid(), editedStyle.getUuid());
            assertEquals(modifiedName, editedStyle.getLabel(context));
            assertEquals(addedStyle.isPreferred(), editedStyle.isPreferred());
        }
    }

    @NonNull
    private StyleViewModel initVm(@NonNull final Style initialStyle,
                                  final int action) {
        final StyleViewModel styleVm = new StyleViewModel();
        final Bundle args = new Bundle(3);
        args.putString(Style.BKEY_UUID, initialStyle.getUuid());
        args.putBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED, initialStyle.isPreferred());
        args.putInt(EditStyleContract.BKEY_ACTION, action);
        styleVm.init(context, args);
        assertNotNull(styleVm.getStyleDataStore());
        return styleVm;
    }

}

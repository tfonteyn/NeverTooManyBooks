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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.author;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Visibility of the {@link DBKey#FK_AUTHOR_REAL_AUTHOR} and {@link DBKey.AUTHOR#BOOK_AUTHOR_ROLE}
 * is based on <strong>global USAGE</strong>.
 */
@SuppressWarnings("WeakerAccess")
public class EditAuthorViewModel
        extends ViewModel {

    private static final String TAG = "EditAuthorViewModel";

    /** The Author we're editing. */
    private Author original;

    /** Current edit. */
    private Author currentEdit;
    /**
     * Current edit. We don't use the real-author directly to avoid unneeded validation
     * at each key-stroke from the user.
     */
    @Nullable
    private String currentRealAuthorName;

    private boolean useRealAuthorName;
    /** Whether roles are globally enabled. */
    private boolean useAuthorRole;
    /** The state of the rolesSwitch. */
    private boolean rolesAreShown;
    private AuthorDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getAuthorDao();

            original = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                              EditParcelableLauncher.BKEY_ITEM);

            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            useRealAuthorName = serviceLocator.isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR);
            useAuthorRole = serviceLocator.isFieldEnabled(DBKey.AUTHOR.BOOK_AUTHOR_ROLE);

            currentEdit = new Author(original, true);
            final Author tmp = currentEdit.getRealAuthor();
            currentRealAuthorName = tmp != null ? tmp.getFormattedName(false) : null;

            rolesAreShown = useAuthorRole && currentEdit.getRole() != AuthorRole.UNKNOWN;
        }
    }

    /**
     * Has the user enabled the use of the real-author-name.
     *
     * @return {@code true} if we need to show the real-author-name for books
     *         where the author name is a pen-name
     */
    public boolean showRealAuthorName() {
        return useRealAuthorName;
    }

    /**
     * Has the user enable the use of Author-roles.
     *
     * @return {@code true} if extended author-roles should be shown
     */
    public boolean showAuthorRole() {
        return useAuthorRole;
    }

    public boolean isRolesAreShown() {
        return rolesAreShown;
    }

    public void setRolesAreShown(final boolean rolesAreShown) {
        this.rolesAreShown = rolesAreShown;
    }

    /**
     * Get the list of names for the given name-field key.
     * i.e. one of:
     * {@link DBKey.AUTHOR#FAMILY_NAME}
     * {@link DBKey.AUTHOR#GIVEN_NAMES}
     * {@link DBKey.AUTHOR#FORMATTED_FULL_NAME}
     *
     * @param key field
     *
     * @return list
     */
    List<String> getAllNames(@NonNull final String key) {
        return dao.getNames(key);
    }

    /**
     * The original/non-modified Author we're editing.
     *
     * @return author
     */
    @NonNull
    public Author getOriginal() {
        return original;
    }

    /**
     * The copy-of/modified Author we're editing.
     *
     * @return author
     */
    @NonNull
    public Author getCurrentEdit() {
        return currentEdit;
    }

    @Nullable
    public String getCurrentRealAuthorName() {
        return currentRealAuthorName;
    }

    public void setCurrentRealAuthorName(@Nullable final String name) {
        this.currentRealAuthorName = name;
    }

    /**
     * Check if there is a real-author set, and whether it is an existing one.
     *
     * @param context Current context
     * @param locale  Locale to use if the item has none set
     * @param create  {@code true} if a non-existent Author should be created
     *
     * @return {@code true} if the 'real' Author was validated and set.
     *         {@code false} if the real author did not exist and we were not allowed to create them
     *         (or if creating threw an error)
     */
    public boolean validateAndSetRealAuthor(@NonNull final Context context,
                                            @NonNull final Locale locale,
                                            final boolean create) {
        // no pseudonym?
        if (currentRealAuthorName == null || currentRealAuthorName.isBlank()) {
            currentEdit.setRealAuthor(null);
            return true;
        }

        // If we have a pseudonym set, it must be a valid/existing author.
        final Author tmpRealAuthor = Author.from(currentRealAuthorName);

        final Optional<Author> existing = dao.findByName(context, tmpRealAuthor, locale);
        if (existing.isPresent()) {
            currentEdit.setRealAuthor(existing.get());
            return true;
        }

        // If for whatever reason we have a non-existing real-author set
        // while the user has globally switched off support for real-author,
        // we simply remove that real-author and return success.
        if (!useRealAuthorName) {
            currentEdit.setRealAuthor(null);
            return true;
        }

        if (!create) {
            // force the caller to ask the user to try again the 2nd time allowing creating.
            return false;
        }

        try {
            dao.insert(context, tmpRealAuthor, locale);
            currentEdit.setRealAuthor(tmpRealAuthor);
            return true;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, tmpRealAuthor);
            return false;
        }
    }

    /**
     * Were any of the fields changed?
     *
     * @return {@code true} if modified
     */
    public boolean isModified() {
        // We're not linked to a book here, hence no author roles.
        return !(original.isSameName(currentEdit)
                 && Objects.equals(original.getBirthDate(), currentEdit.getBirthDate())
                 && Objects.equals(original.getDeathDate(), currentEdit.getDeathDate())
                 && Objects.equals(original.getRealAuthor(), currentEdit.getRealAuthor())
                 && original.isComplete() == currentEdit.isComplete());
    }

    /**
     * Check if the current user entered Author name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing Author indicating failure to save.
     *
     * @param context Current context
     *
     * @return an empty Optional for SUCCESS, or else the existing Author.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<Author> saveIfUnique(@NonNull final Context context)
            throws DaoWriteException {

        // FIRST check if the name was changed
        final boolean sameName = original.isSameName(currentEdit);

        // now copy changes, including the name and any other attributes
        original.copyFrom(currentEdit, false);

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (original.getId() != 0 && sameName) {
            dao.update(context, original, locale);
            return Optional.empty();
        }

        // Check if there is an another one with the same new name.
        final Optional<Author> existingEntity = dao.findByName(context, original, locale);
        if (existingEntity.isPresent()) {
            return existingEntity;
        }

        // Just insert or update as needed
        if (original.getId() == 0) {
            dao.insert(context, original, locale);
        } else {
            dao.update(context, original, locale);
        }
        // return SUCCESS
        return Optional.empty();
    }

    void move(@NonNull final Context context,
              @NonNull final Author destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, original, destination);
    }
}

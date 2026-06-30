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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.util.StringList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.StringCoder;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageOwner;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Represents an Author.
 * <p>
 * Visibility of the {@link DBKey#FK_AUTHOR_REAL_AUTHOR} and {@link DBKey.AUTHOR#BOOK_AUTHOR_ROLE}
 * is based on <strong>global USAGE</strong>.
 *
 * <p>
 * <strong>Note:</strong> "role" is a column of {@link DBDefinitions#TBL_BOOK_AUTHOR}
 * So this class does not strictly represent an Author, but a "BookAuthor"
 * When the role is disregarded, it is a real Author representation.
 * <p>
 * Author roles:
 * <a href="http://www.loc.gov/marc/relators/relaterm.html">
 * http://www.loc.gov/marc/relators/relaterm.html</a>
 * <p>
 * TODO: further cleanup of the {@link #getStyledName} and {@link #getStyledName} methods
 * <p>
 * ENHANCE: The Author Locale should be based on the main language the author writes in.
 */
public class Author
        implements Parcelable, Entity, Mergeable, IdentifierOwner, ImageOwner {

    /** {@link Parcelable}. */
    public static final Creator<Author> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Author createFromParcel(@NonNull final Parcel source) {
            return new Author(source);
        }

        @Override
        @NonNull
        public Author[] newArray(final int size) {
            return new Author[size];
        }
    };

    private static final String TAG = "Author";

    /**
     * Handles recognition of a limited set of special prefixes to a family name.
     * <p>
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     * <p>
     * Typical Dutch: there are too many to list them all...
     * "van der X"  added as quite common
     * "van den X"  not added, usually all in one word
     * <p>
     * ENHANCE: make the family-name prefix editable by the user
     */
    private static final Pattern FAMILY_NAME_PREFIX_PATTERN =
            Pattern.compile("^(le|de|du|van|von|van der)$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Handles recognition of a (typical American?) name suffix.
     * <p>
     * First character "j" and "s" can be lower-case or upper-case.
     * <p>
     * Foo Bar Jr.
     * Foo Bar Jr
     * Foo Bar Junior
     * Foo Bar Sr.
     * Foo Bar Sr
     * Foo Bar Senior
     * Foo Bar II
     * Charles Emerson Winchester III
     * <p>
     * same as above, but with comma:
     * Foo Bar, Jr.
     * <p>
     * Not covered yet, and seen in the wild:
     * "James jr. Tiptree" -> suffix as a middle name.
     * "Dr. Asimov" -> titles... pre or suffixed
     * <p>
     * 2025-05-05: ISFDB registers these as suffixes:
     * II, III, IV, V, VI, VII, VIII, IX, X, B.A., B.Sc., D.D., D.Sc., Ed.D., J.D.,
     * Jr., Lit.D., Litt.D., M.B.I.F., M.B.I.S., M.A., M.D., M.E., M.S., Ph.D.,
     * P.J.F., R.I., Sr., U.S.A.
     */
    private static final Pattern FAMILY_NAME_SUFFIX_PATTERN =
            Pattern.compile("jr\\.|jr|junior|sr\\.|sr|senior|II|III",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Handles recognition of a bracket section with optional text before and after.
     * <p>
     * 1. "Robert Velter (Rob-vel,Bozz)"
     * 2. "Robert Velter (Rob Vel)"
     * 3. "Ange (1/2)"
     * 4. "Don (*3)"
     * 5. [法] 保罗·霍尔特   ==>  [France] Paul Holt
     *
     * <p>
     * 1+2: The () part are pseudonyms.
     * 3: there are 2 people with the same name "Ange"; 1/2 and 2/2 makes the distinction.
     * 4: presumably there are 3 Dons?
     * 5: the [] part is the country/nationality of the Author
     * <p>
     * For backwards compatibility, we also handle "(*3), Don",
     * i.e. in older versions we treated above 4 as having a given name == "Don"
     * and a family name "(*3)". We must make sure that those are decoded as before.
     * <p>
     * There is no automated way to determine whether to use the name or the pseudonym(s)
     * to create the Author as we cannot know what the book is published under.
     * <p>
     * Hence, for now, we stick with decoding the whole text, and then sticking
     * the bracket section back on behind the family name.
     * <p>
     * group 2, if found, <strong>includes</strong> the {@code []} or {@code ()} bracket pair.
     */
    private static final Pattern PATTERN_BRACKETS =
            Pattern.compile("(.*)([(\\[].+[)\\]])(.*)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String ERROR_RESOLVE_REAL_AUTHOR = "resolveRealAuthor";

    @NonNull
    private final List<Identifier.Value> identifiers = new ArrayList<>();

    /** Row ID. */
    private long id;
    /** Family name(s). (NotNullFieldNotInitialized: see copy-constructor). */
    @SuppressWarnings("NotNullFieldNotInitialized")
    @NonNull
    private String familyName;
    /** Given name(s). (NotNullFieldNotInitialized: see copy-constructor). */
    @SuppressWarnings("NotNullFieldNotInitialized")
    @NonNull
    private String givenNames;
    @Nullable
    private String birthDate;
    @Nullable
    private String deathDate;
    /**
     * Not stored in the database. This is temporarily used:
     * - set during a search/resolve
     * - read and displayed when editing the author
     * - read, file stored, and cleared.
     * In this case the file uuid is set in {@link #imageUuid} and stored in the database
     */
    @Nullable
    private String tmpPictureFileSpec;

    /**
     * Stored file uuid.
     *
     * @see #tmpPictureFileSpec
     */
    @Nullable
    private String imageUuid;

    /** whether we have all we want from this Author. */
    private boolean complete;
    /**
     * If this Author is a pseudonym, then 'realAuthorId' points to that author.
     * When {@code null} this IS a real author.
     */
    @VisibleForTesting
    @Nullable
    public Author realAuthor;
    /** can be {@code 0}. Should be a {@code Long} but we need parcelling ... */
    @VisibleForTesting
    public long realAuthorId;

    /** Bitmask. */
    @AuthorRole.Role
    private int role = AuthorRole.UNKNOWN;

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public Author(@NonNull final String familyName,
                  @Nullable final String givenNames) {
        this.familyName = familyName.strip();
        this.givenNames = givenNames == null ? "" : givenNames.strip();
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Author in the database.
     * @param rowData with data
     */
    public Author(final long id,
                  @NonNull final DataHolder rowData) {
        this.id = id;
        familyName = rowData.getString(DBKey.AUTHOR.FAMILY_NAME);
        givenNames = rowData.getString(DBKey.AUTHOR.GIVEN_NAMES);
        birthDate = rowData.getString(DBKey.AUTHOR.BIRTH_DATE, null);
        deathDate = rowData.getString(DBKey.AUTHOR.DEATH_DATE, null);
        imageUuid = rowData.getString(DBKey.AUTHOR.PICTURE_UUID, null);
        complete = rowData.getBoolean(DBKey.AUTHOR.COMPLETE);

        setIdentifiers(ServiceLocator.getInstance().getAuthorIdentifierDao().getByFkId(this.id));

        if (rowData.contains(DBKey.AUTHOR.BOOK_AUTHOR_ROLE)) {
            role = rowData.getInt(DBKey.AUTHOR.BOOK_AUTHOR_ROLE);
        }

        if (rowData.contains(DBKey.FK_AUTHOR_REAL_AUTHOR)) {
            realAuthorId = rowData.getLong(DBKey.FK_AUTHOR_REAL_AUTHOR);
            // We're NOT loading the real-author here to avoid
            // ANY possible recursion.
        }
    }

    /**
     * Copy constructor.
     *
     * @param author            to copy
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public Author(@NonNull final Author author,
                  final boolean includeBookFields) {
        copyFrom(author, includeBookFields);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Author(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        familyName = in.readString();
        //noinspection DataFlowIssue
        givenNames = in.readString();
        birthDate = in.readString();
        deathDate = in.readString();
        imageUuid = in.readString();
        tmpPictureFileSpec = in.readString();

        complete = in.readByte() != 0;
        role = in.readInt();
        realAuthorId = in.readLong();
        realAuthor = in.readParcelable(getClass().getClassLoader());
        ParcelUtils.readParcelableList(in, identifiers, getClass().getClassLoader());
    }

    /**
     * A precaution to names like {@literal "<someName>"}.
     * The various {@link #getLabel} and {@link #getStyledName} use this method to
     * prevent HTML failures. It's not foolproof, but should be enough for now.
     * <p>
     * FIXME: prevent getting the above special name getting into the db in the first place...
     *
     * @param name to filter
     *
     * @return filtered name
     */
    @NonNull
    private static String filterLtG(@NonNull final String name) {
        if (name.startsWith("<") && name.endsWith(">")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    @NonNull
    private static Author createWithOptionalBrackets(@NonNull final String familyName,
                                                     @Nullable final String givenNames,
                                                     @Nullable final String bracketSection) {
        if (bracketSection == null || bracketSection.isEmpty()) {
            return new Author(familyName, givenNames);

        } else if (familyName.isEmpty()) {
            return new Author(bracketSection, givenNames);

        } else {
            return new Author(familyName + ' ' + bracketSection, givenNames);
        }
    }

    /**
     * Create a suitable "unknown" Author.
     *
     * @param context Current context
     *
     * @return an Author with a localised "Unknown Author" family name
     */
    @NonNull
    public static Author createUnknownAuthor(@NonNull final Context context) {
        final String unknownAuthor = context.getString(R.string.unknown_author);
        return new Author(unknownAuthor, "");
    }

    /**
     * Parse a string into a family/given name pair.
     * <p>
     * If the string contains a comma (and the part after it is not a recognised suffix)
     * then the string is assumed to be in the format of "family, given-names"
     * All other formats are decoded as complete as possible.
     * Also see {@link #PATTERN_BRACKETS} on how brackets are handled.
     * <p>
     * Recognised pre/suffixes: see {@link #FAMILY_NAME_PREFIX_PATTERN}
     * and {@link #FAMILY_NAME_SUFFIX_PATTERN}
     * <ul>Not covered:
     *      <li>multiple, and not concatenated, family names.</li>
     *      <li>more than 1 un-encoded comma.</li>
     * </ul>
     *
     * <strong>Note:</strong> uses a simple String decoder.
     * Any complex decoding for JSON format must be done before calling here.
     *
     * @param name a String containing the name
     *
     * @return Author
     */
    @NonNull
    public static Author from(@NonNull final String name) {
        String uName = StringCoder.unEscape(name);

        // First step, check for a bracket section.
        String bracketSection = null;
        final Matcher brackets = PATTERN_BRACKETS.matcher(uName);
        if (brackets.find()) {
            // Grab the full string before the brackets for further decoding as the name
            String group = brackets.group(1);
            if (group != null) {
                uName = group.strip();
            }
            // If we did find a brackets parts, preserve it for later concatenation.
            if (brackets.groupCount() > 1) {
                group = brackets.group(2);
                if (group != null) {
                    group = group.strip();
                    if (!group.isEmpty()) {
                        bracketSection = group;
                    }
                }
            }
            // If there is another piece of text after the brackets parts
            if (brackets.groupCount() > 2) {
                group = brackets.group(3);
                if (group != null) {
                    group = group.strip();
                    if (!group.isEmpty()) {
                        if (uName.isEmpty() && bracketSection != null && group.startsWith(", ")) {
                            // assume it's the format "(blah), name" and decode
                            // BACKWARDS compatible:
                            return new Author(bracketSection, group.substring(2));
                        } else {
                            // this is far to complicated to make sense...
                            // Just concat with the name part
                            uName += ' ' + group;
                        }
                    }
                }
            }
        }

        // check for commas
        final List<String> tmp = StringList.newInstance(',')
                                           .decodeList(uName, true);
        if (tmp.size() > 1) {
            final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(tmp.get(1));
            if (suffixMatcher.find()) {
                // GivenNames FamilyName, suffix
                // concatenate without the comma. Further processing will take care of the suffix.
                uName = tmp.get(0) + ' ' + tmp.get(1);
            } else {
                // FamilyName, GivenNames
                // no suffix, assume the names are already formatted.
                return createWithOptionalBrackets(tmp.get(0), tmp.get(1), bracketSection);
            }
        }

        final String[] names = uName.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                return createWithOptionalBrackets(names[0], "", bracketSection);
            case 2:
                return createWithOptionalBrackets(names[1], names[0], bracketSection);
            default:
                break;
        }

        // we have 3 or more parts, check the family name for suffixes and prefixes
        final StringBuilder buildFamilyName = new StringBuilder();
        // the position to check, start at the end.
        int pos = names.length - 1;

        final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(names[pos]);
        if (suffixMatcher.find()) {
            // suffix and the element before it are part of the last name.
            buildFamilyName.append(names[pos - 1]).append(' ').append(names[pos]);
            pos -= 2;
        } else {
            // no suffix.
            buildFamilyName.append(names[pos]);
            pos--;
        }

        // the last name could also have a prefix
        final Matcher middleNameMatcher = FAMILY_NAME_PREFIX_PATTERN.matcher(names[pos]);
        if (middleNameMatcher.find()) {
            // insert it at the front of the family name
            buildFamilyName.insert(0, names[pos] + ' ');
            pos--;
        }

        // everything else are considered given names
        final StringBuilder buildGivenNames = new StringBuilder();
        for (int i = 0; i <= pos; i++) {
            buildGivenNames.append(names[i]).append(' ');
        }


        return createWithOptionalBrackets(buildFamilyName.toString(), buildGivenNames.toString(),
                                          bracketSection);
    }

    /**
     * Get the label to use for <strong>displaying</strong> a list of Authors.
     * If there is more than one, we get the first Author + an ellipsis.
     *
     * @param context Current context
     * @param authors list to condense
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public static String getLabel(@NonNull final Context context,
                                  @NonNull final List<Author> authors) {
        // could/should? use ListFormatter
        if (authors.isEmpty()) {
            return "";
        } else {
            final String text = authors.get(0).getLabel(context);
            if (authors.size() > 1) {
                return context.getString(R.string.and_others, text);
            }
            return text;
        }
    }

    private static String smallerText(@NonNull final String text) {
        return " <small><i>" + text + "</i></small>";
    }

    /**
     * Return the formatted version of the name.
     *
     * @param givenNameFirst {@code true} if we want "given-names family-name" formatted name.
     *                       {@code false} for "last-family, first-names"
     *
     * @return formatted, but unfiltered, name
     */
    @NonNull
    public String getFormattedName(final boolean givenNameFirst) {
        if (givenNames.isEmpty()) {
            return familyName;
        } else {
            if (givenNameFirst) {
                return givenNames + ' ' + familyName;
            } else {
                return familyName + ", " + givenNames;
            }
        }
    }

    /**
     * Get the 'complete' status of the Author.
     *
     * @return {@code true} if the Author is complete
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Set the 'complete' status of the Author.
     *
     * @param isComplete Flag indicating the user considers this Author to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        complete = isComplete;
    }

    /**
     * CLEANER ACCESS ONLY.
     *
     * @return the real-author id; no loading/resolving
     */
    public long getRealAuthorId() {
        return realAuthorId;
    }

    /**
     * If this Author is a pen-name (pseudonym), return the real Author.
     *
     * @return the real-author,
     *         or {@code null} if {@code this} author <strong>is</strong> the real-author
     */
    @Nullable
    public Author getRealAuthor() {
        // conditions spelled out in long for readability...

        if (realAuthorId == 0 && realAuthor != null) {
            // We have a 'new' real author.
            return realAuthor;
        }

        if (realAuthorId == 0) {
            // this IS a real author
            return null;
        }

        if (realAuthor != null) {
            // Was loaded previously.
            return realAuthor;
        }

        // We have an id, load and resolve!
        realAuthor = ServiceLocator.getInstance().getAuthorDao()
                                   .findById(realAuthorId)
                                   .map(this::resolveRealAuthor)
                                   .orElse(null);
        if (realAuthor == null) {
            // We get here if resolving null'd the real-author
            realAuthorId = 0;
        }

        return realAuthor;
    }

    /**
     * Set the real-author for this Author.
     *
     * @param author to use; use {@code null} to remove
     *
     * @return the resolved real-author,
     *         or {@code null} if {@code this} author <strong>is</strong> the real-author
     */
    @Nullable
    public Author setRealAuthor(@Nullable final Author author) {
        // Don't allow null or self-reference.
        if (author == null || author == this) {
            realAuthor = null;
            realAuthorId = 0;
        } else {
            realAuthor = resolveRealAuthor(author);
            realAuthorId = realAuthor != null ? realAuthor.id : 0;
        }
        return realAuthor;
    }

    /**
     * Resolve any nested and 1:1 circular references.
     * <p>
     * <strong>Important</strong>: the database is NOT updated in this method,
     * but {@code this} object <strong>may</strong> be updated.
     * The caller may update the database.
     * <p>
     * TODO: implement {@link EntityStage} for the author class.
     *
     * @param author to resolve
     *
     * @return the resolved real-author, can be {@code null}.
     */
    @Nullable
    private Author resolveRealAuthor(@Nullable final Author author) {
        if (author == null) {
            // duh...
            return null;
        }

        if (author == this) {
            // that's a bug... we should never be called on ourselves.
            // Log it for shaming, but ignore
            LoggerFactory.getLogger().w(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                        "called on this", new Throwable());
            return null;
        }

        if (author.realAuthorId == 0 && author.realAuthor == null) {
            // there is no parent real-author,
            // but check names to detect 1:1 circular reference.
            if (this.isSameName(author)) {
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().d(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                                "circular1", author);
                }
                return null;
            }
            // endpoint, and different, all done.
            return author;
        }

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                        "resolve any nested reference", author);
        }

        @Nullable
        Author current = author;
        do {
            // current.realAuthorId contains a valid, != 0 value.
            // Load the real-author for the current real-author
            current = ServiceLocator.getInstance().getAuthorDao()
                                    .findById(current.realAuthorId)
                                    .orElse(null);

            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                            "loaded", current);
            }

            // conditions spelled out in long for readability...

            if (current == null) {
                // none found, we should not have got here... flw
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().d(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                                "none found");
                }
                return null;
            }

            // If we found THIS author, we have a circular reference, quit
            if (current.id == this.id || this.isSameName(current)) {
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().d(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                                "circular2", current);
                }
                // note we do NOT fix the database here!
                return null;
            }
            // if the current on has a parent, loop to resolve that one
        } while (current.realAuthorId != 0);

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, ERROR_RESOLVE_REAL_AUTHOR,
                                        "resolved", current);
        }
        // endpoint, and different, all done.
        return current;
    }

    @Override
    @NonNull
    public List<Identifier.Value> getIdentifiers() {
        return identifiers;
    }

    @Override
    public void setIdentifiers(@NonNull final Collection<Identifier.Value> ivs) {
        // The incoming list might be physically OUR list
        // ONLY clear/update if it's not; otherwise no action needed
        if (ivs != identifiers) {
            identifiers.clear();
            identifiers.addAll(ivs);
        }
    }

    /**
     * Get the role(s) of the author related to the book this Author object is attached to.
     *
     * @return role(s)
     */
    @AuthorRole.Role
    public int getRole() {
        return role;
    }

    /**
     * Set the role(s) of the author related to the book this Author object is attached to.
     *
     * @param role to set
     *
     * @return {@code this} for chaining
     */
    public Author setRole(@AuthorRole.Role final int role) {
        this.role = role & AuthorRole.BITMASK_ALL;
        return this;
    }

    /**
     * Add a role to the current role(s).
     *
     * @param role to add
     */
    public void addRole(@AuthorRole.Role final int role) {
        this.role |= role & AuthorRole.BITMASK_ALL;
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    /**
     * Get the label to use for <strong>displaying</strong>.
     * Suitable for (and may contain) HTML output.
     *
     * <ul>
     *     <li>{@link Details#Full}: standard formatted name combined
     *          (if enabled) with the real-author name.
     *          (if enabled) with the author role.
     *     </li>
     *     <li>{@link Details#Normal}, {@link Details#AutoSelect}: standard formatted name.</li>
     *     <li>{@link Details#Short}: initial + family-name</li>
     * </ul>
     *
     * @param context Current context
     * @param details the amount of details wanted
     * @param style   (optional) to use
     *
     * @return the label to use.
     */
    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @NonNull final Details details,
                           @NonNull final Style style) {
        String label;
        switch (details) {
            case Full: {
                label = getFormattedName(style.isShowAuthorByGivenName());

                final ServiceLocator serviceLocator = ServiceLocator.getInstance();
                if (serviceLocator.isFieldEnabled(DBKey.FK_AUTHOR_REAL_AUTHOR)) {
                    final Author author = getRealAuthor();
                    if (author != null) {
                        label += smallerText(context.getString(
                                R.string.lbl_author_pseudonym_of_X,
                                author.getFormattedName(style.isShowAuthorByGivenName())));
                    }
                }

                if (serviceLocator.isFieldEnabled(DBKey.AUTHOR.BOOK_AUTHOR_ROLE)) {
                    final String roleLabels = getRoleLabels(context);
                    if (!roleLabels.isEmpty()) {
                        label += smallerText(roleLabels);
                    }
                }
                break;
            }
            case AutoSelect:
            case Normal: {
                label = getFormattedName(style.isShowAuthorByGivenName());
                break;
            }
            case Short: {
                if (givenNames.isEmpty()) {
                    label = familyName;
                } else {
                    if (style.isShowAuthorByGivenName()) {
                        label = givenNames.substring(0, 1) + ' ' + familyName;
                    } else {
                        label = familyName + ' ' + givenNames.charAt(0);
                    }
                }
                break;
            }
            default:
                throw new IllegalArgumentException("details=" + details);
        }

        return filterLtG(label);
    }

    /**
     * Syntax sugar for {@link #getStyledName(Context, Style, CharSequence)}.
     * <p>
     * <strong>IMPORTANT: will only display correctly when used with a TextView.</strong>
     * <p>
     * Call this method if {@code this} is the pseudonym Author itself; otherwise call
     * {@link #getStyledName(Context, Style, Author)} or
     * {@link #getStyledName(Context, Style, CharSequence)}.
     * <p>
     * If this Author is a pseudonym, then the return value will be a 2-lines styled
     * {@link SpannableString} with both pen-name and real-name of this Author.
     *
     * @param context Current context
     * @param style   to use
     *
     * @return styled and formatted name
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public CharSequence getStyledName(@NonNull final Context context,
                                      @NonNull final Style style) {
        final CharSequence name = getStyledName(context, style, (CharSequence) null);
        final Author ra = getRealAuthor();
        if (ra == null) {
            return name;
        } else {
            return ra.getStyledName(context, style, name);
        }
    }

    /**
     * Syntax sugar for {@link #getStyledName(Context, Style, CharSequence)}.
     * <p>
     * <strong>IMPORTANT: will only display correctly when used with a TextView.</strong>
     *
     * @param context   Current context
     * @param style     to use
     * @param pseudonym optional Author to combine with the actual name
     *
     * @return styled and formatted name
     *
     * @see #getStyledName(Context, Style, CharSequence)
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public CharSequence getStyledName(@NonNull final Context context,
                                      @NonNull final Style style,
                                      @Nullable final Author pseudonym) {
        final CharSequence penName =
                pseudonym == null ? null : pseudonym.getStyledName(context, style,
                                                                   (CharSequence) null);
        return getStyledName(context, style, penName);
    }

    /**
     * TODO: try to unify this with {@link Entity#getLabel(Context, Details, Style)}
     *  using a either the Details object or a new style flag to decide whether
     *  to add the realAuthor name
     * <p>
     * Return the <strong>styled and formatted</strong> version of the name
     * combined with the given pseudonym.
     * <p>
     * <strong>IMPORTANT: will only display correctly when used with a TextView.</strong>
     * <p>
     * Call this method if {@code this} is the real Author; otherwise call
     * {@link #getStyledName(Context, Style)}.
     * <p>
     * If this Author has a pseudonym, then the return value will be a styled
     * {@link SpannableString} with both pseudonym and real name of this Author.
     *
     * @param context Current context
     * @param style   to use
     * @param penName optional Author pen-name to combine with the actual name
     *
     * @return styled and formatted name
     *
     * @see #getStyledName(Context, Style, Author)
     */
    @NonNull
    public CharSequence getStyledName(@NonNull final Context context,
                                      @NonNull final Style style,
                                      @Nullable final CharSequence penName) {

        final String realName = getFormattedName(style.isShowAuthorByGivenName());

        if (penName == null) {
            return realName;

        } else {
            final String filteredPenName = filterLtG(penName.toString());

            // Display the pseudonym as the 'normal' Author, but add the real
            // author ('this') name in a smaller italic font.
            final String fullName =
                    String.format("%1s %2s", filteredPenName,
                                  context.getString(R.string.lbl_author_pseudonym_of_X, realName));

            final Spannable span = new SpannableString(fullName);
            final float relSize = ResourcesCompat
                    .getFloat(context.getResources(), R.dimen.author_pseudonym_size);
            span.setSpan(new RelativeSizeSpan(relSize),
                         filteredPenName.length(), span.length(), 0);
            span.setSpan(new StyleSpan(Typeface.ITALIC),
                         filteredPenName.length(), span.length(), 0);
            return span;
        }
    }


    /**
     * Get a CSV string with the role of this author; or the empty string
     * if no specific roles are set.
     *
     * @param context Current context
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    private String getRoleLabels(@NonNull final Context context) {
        if (role != AuthorRole.UNKNOWN) {
            final List<String> list = AuthorRole
                    .ROLES
                    .entrySet()
                    .stream()
                    .filter(entry -> (entry.getKey() & (long) role) != 0)
                    .map(Map.Entry::getValue)
                    .map(context::getString)
                    .collect(Collectors.toList());

            if (!list.isEmpty()) {
                return context.getString(R.string.brackets, String.join(", ", list));
            }
        }
        return "";
    }

    /**
     * Set the names.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public void setName(@NonNull final String familyName,
                        @NonNull final String givenNames) {
        this.familyName = familyName;
        this.givenNames = givenNames;
    }

    /**
     * Get the family name of this Author.
     *
     * @return family name
     */
    @NonNull
    public String getFamilyName() {
        return familyName;
    }

    /**
     * Get the given name ('first' name) of this Author.
     * Will be {@code ""} if unknown.
     *
     * @return given-name
     */
    @NonNull
    public String getGivenNames() {
        return givenNames;
    }

    /**
     * Get the birthdate of this Author.
     *
     * @return ISO formatted (partial) date
     */
    @NonNull
    public Optional<String> getBirthDate() {
        return birthDate == null || birthDate.isEmpty() ? Optional.empty()
                                                        : Optional.of(birthDate);
    }

    /**
     * Set the birthdate of this Author.
     *
     * @param date ISO formatted (partial) date
     */
    public void setBirthDate(@Nullable final String date) {
        this.birthDate = date;
    }

    /**
     * Get the death-date of this Author.
     *
     * @return ISO formatted (partial) date
     */
    @NonNull
    public Optional<String> getDeathDate() {
        return deathDate == null || deathDate.isEmpty() ? Optional.empty()
                                                        : Optional.of(deathDate);
    }

    /**
     * Set the death-date of this Author.
     *
     * @param date ISO formatted (partial) date
     */
    public void setDeathDate(@Nullable final String date) {
        this.deathDate = date;
    }


    /**
     * Get the <strong>current</strong> picture for this author.
     * <p>
     * This method gets the temporary picture or the persisted picture as available.
     * <p>
     * Any {@link StorageException} is <strong>IGNORED</strong>
     *
     * @param context Current context
     * @param cIdx    0..n image index; pass in {@code 0} for future compatibility
     *
     * @return file
     */
    @Override
    @NonNull
    public Optional<File> getImage(@NonNull final Context context,
                                   @IntRange(from = 0, to = 0) final int cIdx) {
        final Optional<String> oFileSpec = getTmpPictureFileSpec();
        if (oFileSpec.isPresent()) {
            final File file = new File(oFileSpec.get());
            // If it exists, it will be a valid file as we check before storing it
            if (file.exists()) {
                return Optional.of(file);
            } else {
                // we had a fileSpec but no file - should never get here
                tmpPictureFileSpec = null;
            }
        }

        final Optional<String> uuid = getImageUuid();
        if (uuid.isPresent()) {
            final Optional<File> oFile = ServiceLocator.getInstance().getCoverStorage()
                                                       .getPersistedFile(uuid.get(), cIdx);
            if (oFile.isPresent()) {
                // all done
                return oFile;
            }

            // we had a uuid, but no file
            // This could happen when the user imports authors without the images
            this.imageUuid = null;
            updateInDatabase(context);
        }

        return Optional.empty();
    }

    /**
     * Remove the image at the given index.
     *
     * @param context Current context
     * @param cIdx    0..n image index; pass in {@code 0} for future compatibility
     */
    @Override
    @WorkerThread
    public void removeImage(@NonNull final Context context,
                            @IntRange(from = 0, to = 0) final int cIdx) {
        // we need to delete any existing file, and remove any existing uuid.
        final Optional<String> oUuid = getImageUuid();
        if (oUuid.isEmpty()) {
            // there is no UUID, hence there is no File,
            // nothing to do,
            return;
        }
        // we have a uuid, remove the physical file, if any
        final String uuid = oUuid.get();
        ASyncExecutor.STORAGE_WRITES.execute(
                () -> ServiceLocator.getInstance()
                                    .getCoverStorage()
                                    .delete(uuid, cIdx));
        // remove the uuid
        imageUuid = null;
        updateInDatabase(context);
    }

    /**
     * Update the ImageOwner with the given {@link File}.
     * This method may set a temporary cover, or persists the cover to storage.
     *
     * @param context Current context
     * @param cIdx    0..n image index; pass in {@code 0} for future compatibility
     * @param file    cover file or {@code null} to delete the cover
     *                The file instance passed in MUST be discarded.
     *                If applicable, the caller can/must use the {@link File}
     *                as returned by this method.
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on generic/other IO failures
     */
    @Override
    @WorkerThread
    public void setImage(@NonNull final Context context,
                         @IntRange(from = 0, to = 0) final int cIdx,
                         @Nullable final File file)
            throws IOException, CoverStorageException {

        if (file == null) {
            removeImage(context, cIdx);
            return;
        }

        @Nullable
        String uuid = getImageUuid().orElse(null);
        if (uuid != null && file.getName().startsWith(uuid)) {
            // No further action needed as we have the image "in-place"
            // ... not actually sure when this would be the case; keep an eye on logs
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger()
                             .e(TAG, new Throwable("setImage"),
                                "readOnly"
                                + "|bookId=" + getId()
                                + "|cIdx=" + cIdx
                                + "|uuid, in-place"
                             );
            }
        } else if (uuid != null) {
            // we already had an image, just replace it with the new file
            ServiceLocator.getInstance().getCoverStorage().persist(file, uuid, cIdx);
        } else {
            // it's the first time we persist an image
            // Rename the temp file to a new uuid based permanent file name
            uuid = UUID.randomUUID().toString();
            ServiceLocator.getInstance().getCoverStorage().persist(file, uuid, cIdx);
            imageUuid = uuid;
            updateInDatabase(context);
        }
    }

    private void updateInDatabase(@NonNull final Context context) {
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        try {
            ServiceLocator.getInstance().getAuthorDao().update(context, this, locale);
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, this);
        }
    }

    /**
     * Get the UUID for the picture file.
     * <p>
     * Formatted as a 20 character UUID string, i.e. with 4 '-' separators.
     *
     * @return uuid
     */
    @Override
    @NonNull
    public Optional<String> getImageUuid() {
        return imageUuid == null || imageUuid.isEmpty()
               ? Optional.empty() : Optional.of(imageUuid);
    }

    /**
     * Set the UUID of the permanently persisted picture.
     *
     * @param imageUuid to set
     */
    public void setImageUuid(@Nullable final String imageUuid) {
        this.imageUuid = imageUuid;
    }

    /**
     * Get the <strong>temporary</strong> fileSpec for a picture.
     *
     * @return fileSpec
     */
    @NonNull
    public Optional<String> getTmpPictureFileSpec() {
        return tmpPictureFileSpec == null || tmpPictureFileSpec.isEmpty()
               ? Optional.empty() : Optional.of(tmpPictureFileSpec);
    }

    /**
     * Set the <strong>temporary</strong> fileSpec for a picture.
     *
     * @param tmpPictureFileSpec fileSpec
     */
    public void setTmpPictureFileSpec(@Nullable final String tmpPictureFileSpec) {
        this.tmpPictureFileSpec = tmpPictureFileSpec;
    }

    /**
     * <strong>Replace</strong> local details with those from the given Author.
     *
     * @param source            to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Author source,
                         final boolean includeBookFields) {
        familyName = source.familyName;
        givenNames = source.givenNames;
        birthDate = source.birthDate;
        deathDate = source.deathDate;
        imageUuid = source.imageUuid;
        tmpPictureFileSpec = source.tmpPictureFileSpec;

        complete = source.complete;

        // Do not deep copy! We WANT the same/original object
        realAuthorId = source.realAuthorId;
        realAuthor = source.realAuthor;

        identifiers.clear();
        // deep copy
        identifiers.addAll(source.identifiers.stream()
                                             .map(Identifier.Value::new)
                                             .collect(Collectors.toList()));

        if (includeBookFields) {
            role = source.role;
        }
    }

    /**
     * <strong>Merge</strong> local details with those from the given Author.
     * The <em>family</em> and <em>given</em> names are never merged.
     *
     * @param source            to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     *
     * @return {@code true} if this Author was modified in any way
     */
    public boolean merge(@NonNull final Author source,
                         final boolean includeBookFields) {

        // Make sure to resolve both!
        final Author sourceRealAuthor = source.getRealAuthor();
        final Author currentRealAuthor = getRealAuthor();
        // If both have a real-author set, and they are different,
        // abort, we can't merge.
        if (currentRealAuthor != null && sourceRealAuthor != null
            && !currentRealAuthor.equals(sourceRealAuthor)) {
            return false;
        }

        if (includeBookFields) {
            // always merge using an OR
            role |= source.getRole();
        }

        // overwrite the id unless we're 'new'
        if (source.getId() > 0) {
            id = source.getId();
        }

        // Other fields are copied when this object does not have values for them.

        if (currentRealAuthor == null) {
            setRealAuthor(sourceRealAuthor);
        }

        mergeDates(this::getBirthDate, source::getBirthDate, this::setBirthDate);
        mergeDates(this::getDeathDate, source::getDeathDate, this::setDeathDate);

        if (getImageUuid().isEmpty()) {
            source.getImageUuid().ifPresent(this::setImageUuid);
        }
        if (getTmpPictureFileSpec().isEmpty() && getImageUuid().isEmpty()) {
            source.getTmpPictureFileSpec().ifPresent(this::setTmpPictureFileSpec);
        }
        if (source.isComplete()) {
            // OR with true, just set it
            complete = true;
        }

        identifiers.addAll(source.getIdentifiers());
        ServiceLocator.getInstance().getIdentifierDao().pruneList(identifiers);

        return true;
    }

    /**
     * Merge two dates. If both are present, use the longest date string.
     *
     * @param myDate     this object
     * @param sourceDate the object we're merging
     * @param setDate    method to set the final date
     */
    private void mergeDates(@NonNull final Supplier<Optional<String>> myDate,
                            @NonNull final Supplier<Optional<String>> sourceDate,
                            @NonNull final Consumer<String> setDate) {
        final Optional<String> oDate1 = myDate.get();
        final Optional<String> oDate2 = sourceDate.get();

        if (oDate1.isEmpty()) {
            // We don't have a value, copy from them if they have a value
            oDate2.ifPresent(setDate);
        } else if (oDate2.isPresent()) {
            // Both have values
            final String bd1 = oDate1.get();
            final String bd2 = oDate2.get();
            // longest string wins
            if (bd2.length() > bd1.length()) {
                setDate.accept(bd2);
            }
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(familyName);
        dest.writeString(givenNames);
        dest.writeString(birthDate);
        dest.writeString(deathDate);
        dest.writeString(imageUuid);
        dest.writeString(tmpPictureFileSpec);

        dest.writeByte((byte) (complete ? 1 : 0));
        dest.writeInt(role);
        dest.writeLong(realAuthorId);
        dest.writeParcelable(realAuthor, flags);
        ParcelUtils.writeParcelableList(dest, identifiers, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(familyName, givenNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, familyName, givenNames, realAuthorId, realAuthor);
    }

    /**
     * Enhanced {@link #equals(Object)}.
     *
     * @param that to compare to
     *
     * @return {@code true} if equals
     */
    public boolean isIdentical(@Nullable final Author that) {
        return equals(that)
               && complete == that.complete
               && role == that.role
               && identifiers.equals(that.identifiers);
    }

    /**
     * Equality: <strong>id, family and given-names, birth/dead dates, image,
     * realAuthorId, realAuthor(id,names)</strong>
     * (see code for details on the image).
     * <ul>
     *   <li>'complete' is a user setting and is ignored here.</li>
     *   <li>'role' is a book field and is ignored here.</li>
     *   <li>'identifiers' is ignored here.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     * <p>
     * <strong>GitHub #200</strong>: adding the below notes for easy reference:
     * <pre>
     * Author equality is based on:
     *
     *     name
     *     birth/death dates
     *     alias: i.e. if both authors are aliases to the same real (author) name
     *     image
     *
     * NOT based on
     *
     *     role
     *     identifiers
     *     complete flag
     *
     * So basically, if you add
     *
     *     Joe Bar, with a birthdate
     *     Joe Bar, without a birthdate
     *     Joe Bar, without a birthdate, but with a death-date
     *
     * They are considered THREE authors while editing. Mainly due to performance...
     * this avoids repeated merge operations (with database access) during edits.
     *
     * When subsequently saving the book, the authors will be merged as much as possible.
     * In the above example, the 3 entries have an identical name and no conflicting
     * other fields, so they get merged into one.
     *
     * If there are conflicting fields, for example:
     *
     *     Joe Bar, with a birthdate
     *     Joe Bar, without a birthdate
     *     Joe Bar, with a different birthdate
     *
     * After saving, you would have:
     *
     *     1+2 merged
     *     3
     *
     * So while it may be a little confusing during edits, it's working as designed
     * and I think is acceptable behaviour.
     *
     * </pre>
     *
     * @see #isIdentical(Author)
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Author that = (Author) o;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        // The ids MAY be different, but at least one is != 0

        // The file-spec has a timestamp element.
        if (!ImageFileInfo.isTempFilenameEquals(tmpPictureFileSpec,
                                                that.tmpPictureFileSpec)) {
            return false;
        }

        return Objects.equals(familyName, that.familyName)
               && Objects.equals(givenNames, that.givenNames)
               && Objects.equals(birthDate, that.birthDate)
               && Objects.equals(deathDate, that.deathDate)
               && Objects.equals(imageUuid, that.imageUuid)
               && Objects.equals(realAuthorId, that.realAuthorId)
               && Objects.equals(realAuthor, that.realAuthor);
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner sj = new StringJoiner("|", "Role{", "}");

        if ((role & AuthorRole.WRITER) != 0) {
            sj.add("WRITER");
        }
        //        if ((role & AuthorRole.ORIGINAL_SCRIPT_WRITER) != 0) {
        //            sj.add("ORIGINAL_SCRIPT_WRITER");
        //        }
        if ((role & AuthorRole.FOREWORD) != 0) {
            sj.add("FOREWORD");
        }
        if ((role & AuthorRole.AFTERWORD) != 0) {
            sj.add("AFTERWORD");
        }

        if ((role & AuthorRole.TRANSLATOR) != 0) {
            sj.add("TRANSLATOR");
        }
        if ((role & AuthorRole.INTRODUCTION) != 0) {
            sj.add("INTRODUCTION");
        }
        if ((role & AuthorRole.EDITOR) != 0) {
            sj.add("EDITOR");
        }
        if ((role & AuthorRole.CONTRIBUTOR) != 0) {
            sj.add("CONTRIBUTOR");
        }

        if ((role & AuthorRole.COVER_ARTIST) != 0) {
            sj.add("COVER_ARTIST");
        }
        if ((role & AuthorRole.COVER_INKING) != 0) {
            sj.add("COVER_INKING");
        }
        if ((role & AuthorRole.NARRATOR) != 0) {
            sj.add("NARRATOR");
        }
        if ((role & AuthorRole.COVER_COLORIST) != 0) {
            sj.add("COVER_COLORIST");
        }

        if ((role & AuthorRole.ARTIST) != 0) {
            sj.add("ARTIST");
        }
        if ((role & AuthorRole.INKING) != 0) {
            sj.add("INKING");
        }
        if ((role & AuthorRole.STORYBOARD) != 0) {
            sj.add("STORYBOARD");
        }
        if ((role & AuthorRole.COLORIST) != 0) {
            sj.add("COLORIST");
        }

        if ((role & AuthorRole.PSEUDONYM) != 0) {
            sj.add("PSEUDONYM");
        }
        if ((role & AuthorRole.LETTERING) != 0) {
            sj.add("LETTERING");
        }
        return "Author{"
               + "id=" + id
               + ", familyName=`" + familyName + '`'
               + ", givenNames=`" + givenNames + '`'
               + ", birthDate=`" + birthDate + '`'
               + ", deathDate=`" + deathDate + '`'
               + ", pictureUuid=`" + imageUuid + '`'
               + ", tmpPictureFileSpec=`" + tmpPictureFileSpec + '`'
               + ", complete=" + complete
               + ", role=0b" + Integer.toBinaryString(role) + ": " + sj
               + ", identifiers=" + identifiers
               + ", realAuthorId=" + realAuthorId
               + ", realAuthor=" + realAuthor
               + '}';
    }
}

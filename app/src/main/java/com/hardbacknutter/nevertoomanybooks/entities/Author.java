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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.StringList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.StringCoder;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Represents an Author.
 * <p>
 * Visibility of the {@link DBKey#AUTHOR_REAL_AUTHOR} and {@link DBKey#AUTHOR_TYPE__BITMASK}
 * is based on <strong>global USAGE</strong>.
 *
 * <p>
 * <strong>Note:</strong> "type" is a column of {@link DBDefinitions#TBL_BOOK_AUTHOR}
 * So this class does not strictly represent an Author, but a "BookAuthor"
 * When the type is disregarded, it is a real Author representation.
 * <p>
 * Author types:
 * <a href="http://www.loc.gov/marc/relators/relaterm.html">
 * http://www.loc.gov/marc/relators/relaterm.html</a>
 * <p>
 * TODO: further cleanup of the {@link #getStyledName} and {@link #getStyledName} methods
 * <p>
 * ENHANCE: The Author Locale should be based on the main language the author writes in.
 */
public class Author
        implements Parcelable, Entity, Mergeable {

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
    /** Generic Author; the default. A single person created the book. */
    public static final int TYPE_UNKNOWN = 0;

    /**
     * {@link DBDefinitions#DOM_BOOK_AUTHOR_TYPE_BITMASK}.
     * NEWTHINGS: author type: add a bit flag
     * Never change the bit value!
     * <p>
     * WRITER: primary or only writer. i.e. in contrast to any of the below.
     */
    public static final int TYPE_WRITER = 1;
    /**
     * WRITER: not distinguished for now. If we do, use TYPE_ORIGINAL_SCRIPT_WRITER = 1 << 1;
     * <p>
     * <strong>Dev. note:</strong> do NOT Do not set "= TYPE_WRITER"
     * as Android Studio formatting will be [censored]
     * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
     */
    public static final int TYPE_ORIGINAL_SCRIPT_WRITER = 1;

    /** WRITER: the foreword. */
    public static final int TYPE_FOREWORD = 1 << 2;
    /** WRITER: the afterword. */
    public static final int TYPE_AFTERWORD = 1 << 3;
    /** WRITER: translator. */
    public static final int TYPE_TRANSLATOR = 1 << 4;
    /** WRITER: introduction. (some sites makes a distinction with a foreword). */
    public static final int TYPE_INTRODUCTION = 1 << 5;


    /** editor (e.g. of an anthology). */
    public static final int TYPE_EDITOR = 1 << 6;
    /** generic collaborator. */
    public static final int TYPE_CONTRIBUTOR = 1 << 7;


    /** ARTIST: cover. */
    public static final int TYPE_COVER_ARTIST = 1 << 8;
    /** ARTIST: cover inking (if different from above). */
    public static final int TYPE_COVER_INKING = 1 << 9;

    /** Audio books. */
    public static final int TYPE_NARRATOR = 1 << 10;

    /** COLOR: cover. */
    public static final int TYPE_COVER_COLORIST = 1 << 11;


    /** ARTIST: art work; could be illustrations, or the pages of a comic. */
    public static final int TYPE_ARTIST = 1 << 12;
    /** ARTIST: art work inking (if different from above). */
    public static final int TYPE_INKING = 1 << 13;

    // unused for now
    // public static final int TYPE_ = 1 << 14;

    /** COLOR: internal colorist. */
    public static final int TYPE_COLORIST = 1 << 15;

    /**
     * Any: indicate that this name entry is a pseudonym.
     *
     * @deprecated as a flag, this is useless.
     *         (I think this flag is a legacy from when we had goodreads integration)
     */
    @Deprecated
    public static final int TYPE_PSEUDONYM = 1 << 16;

    /**
     * All valid bits for the type.
     * NEWTHINGS: author type: add to the mask
     */
    private static final int TYPE_BITMASK_ALL =
            TYPE_UNKNOWN
            | TYPE_WRITER | TYPE_ORIGINAL_SCRIPT_WRITER | TYPE_FOREWORD | TYPE_AFTERWORD
            | TYPE_TRANSLATOR | TYPE_INTRODUCTION | TYPE_EDITOR | TYPE_CONTRIBUTOR
            | TYPE_COVER_ARTIST | TYPE_COVER_INKING | TYPE_NARRATOR | TYPE_COVER_COLORIST
            | TYPE_ARTIST | TYPE_INKING | TYPE_COLORIST | TYPE_PSEUDONYM;

    /** This is the global setting! There is also a specific style-level setting. */
    private static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST = "show.author.name.given_first";
    /** Maps the type-bit to a string resource for the type-label. */
    private static final Map<Integer, Integer> TYPES = new LinkedHashMap<>();

    /**
     * Handles recognition of a limited set of special prefixes to a family name.
     * <p>
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     */
    private static final Pattern FAMILY_NAME_PREFIX_PATTERN =
            Pattern.compile("(le|de|van|von)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Handles recognition of a (typical american?) name suffix.
     * <p>
     * First character "j" and "s" can be lower or upper case.
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
     */
    private static final Pattern FAMILY_NAME_SUFFIX_PATTERN =
            Pattern.compile("jr\\.|jr|junior|sr\\.|sr|senior|II|III",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Handles recognition of a trailing bracket section.
     * <p>
     * 1. "Robert Velter (Rob-vel,Bozz)"
     * 2. "Robert Velter (Rob Vel)"
     * 3. "Ange (1/2)"
     * 4. "Don (*3)"
     * above examples are from the lastdodo site (see the search-engine for lastdodo
     * for more comments on handling these).
     * <p>
     * 1+2: The () part are pseudonyms.
     * 3: there are 2 people with the same name "Ange"; 1/2 and 2/2 makes the distinction.
     * 4: presumably there are 3 Don's?
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
     */
    private static final Pattern NAME_BRACKET_TEXT_BRACKET_TEXT =
            Pattern.compile("(.*)\\((.*)\\)(.*)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /*
     * NEWTHINGS: author type: add the label for the type
     * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
     */
    static {
        TYPES.put(TYPE_WRITER, R.string.lbl_author_type_writer);
        TYPES.put(TYPE_CONTRIBUTOR, R.string.lbl_author_type_contributor);
        TYPES.put(TYPE_INTRODUCTION, R.string.lbl_author_type_intro);
        TYPES.put(TYPE_TRANSLATOR, R.string.lbl_author_type_translator);
        TYPES.put(TYPE_EDITOR, R.string.lbl_author_type_editor);
        TYPES.put(TYPE_NARRATOR, R.string.lbl_author_type_narrator);

        TYPES.put(TYPE_ARTIST, R.string.lbl_author_type_artist);
        TYPES.put(TYPE_INKING, R.string.lbl_author_type_inking);
        TYPES.put(TYPE_COLORIST, R.string.lbl_author_type_colorist);

        TYPES.put(TYPE_COVER_ARTIST, R.string.lbl_author_type_cover_artist);
        TYPES.put(TYPE_COVER_INKING, R.string.lbl_author_type_cover_inking);
        TYPES.put(TYPE_COVER_COLORIST, R.string.lbl_author_type_cover_colorist);
    }

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
    /** whether we have all we want from this Author. */
    private boolean complete;

    /**
     * If this Author is a pseudonym, then 'realAuthorId' points to that author.
     * When {@code null} this IS a real author.
     */
    @Nullable
    private Author realAuthor;

    /** Bitmask. */
    @Type
    private int type = TYPE_UNKNOWN;

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public Author(@NonNull final String familyName,
                  @Nullable final String givenNames) {
        this.familyName = familyName.trim();
        this.givenNames = givenNames == null ? "" : givenNames.trim();
    }

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     * @param isComplete whether an Author is completed, i.e if the user has all they
     *                   want from this Author.
     */
    public Author(@NonNull final String familyName,
                  @Nullable final String givenNames,
                  final boolean isComplete) {
        this.familyName = familyName.trim();
        this.givenNames = givenNames == null ? "" : givenNames.trim();
        complete = isComplete;
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
        familyName = rowData.getString(DBKey.AUTHOR_FAMILY_NAME);
        givenNames = rowData.getString(DBKey.AUTHOR_GIVEN_NAMES);
        complete = rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);

        if (rowData.contains(DBKey.AUTHOR_TYPE__BITMASK)) {
            type = rowData.getInt(DBKey.AUTHOR_TYPE__BITMASK);
        }

        if (rowData.contains(DBKey.AUTHOR_REAL_AUTHOR)) {
            realAuthor = ServiceLocator.getInstance().getAuthorDao().getById(
                    rowData.getLong(DBKey.AUTHOR_REAL_AUTHOR)).orElse(null);
        }
    }

    /**
     * Copy constructor.
     *
     * @param author to copy
     */
    public Author(@NonNull final Author author) {
        copyFrom(author, true);
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
        complete = in.readByte() != 0;
        type = in.readInt();
        realAuthor = in.readParcelable(getClass().getClassLoader());
    }

    /**
     * A precaution to names like {@literal "<someName>"}.
     * The various {@link #getLabel} and {@link #getStyledName} use this method to
     * prevent html failures. It's not foolproof, but should be enough for now.
     * <p>
     * FIXME: prevent getting the above special name getting into the db in the first place...
     *
     * @param name to filter
     *
     * @return filtered name
     */
    private static String filterLtG(@NonNull final String name) {
        if (name.startsWith("<") && name.endsWith(">")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    private static Author createWithBrackets(@NonNull final String familyName,
                                             @Nullable final String givenNames,
                                             @Nullable final String bracketSection) {
        if (bracketSection == null || bracketSection.isEmpty()) {
            return new Author(familyName, givenNames);

        } else if (familyName.isEmpty()) {
            return new Author("(" + bracketSection + ")", givenNames);

        } else {
            return new Author(familyName + " (" + bracketSection + ")", givenNames);
        }
    }

    /**
     * Create a suitable "unknown" Author.
     *
     * @param context Current context
     *
     * @return an Author with a localized "Unknown Author" family name
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
     * Also see {@link #NAME_BRACKET_TEXT_BRACKET_TEXT} on how brackets are handled.
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

        // First step, check for a trailing bracket section.
        String bracketSection = null;
        final Matcher brackets = NAME_BRACKET_TEXT_BRACKET_TEXT.matcher(uName);
        if (brackets.find()) {
            // Grab he full string before the brackets for further decoding as the name
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
                            return new Author("(" + bracketSection + ")",
                                              group.substring(2));
                        } else {
                            // this is far to complicated to make sense...
                            // Just concat with the name part
                            uName += " " + group;
                        }
                    }
                }
            }
        }

        // check for commas
        final List<String> tmp = StringList.newInstance().decode(uName, ',', true);
        if (tmp.size() > 1) {
            final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(tmp.get(1));
            if (suffixMatcher.find()) {
                // GivenNames FamilyName, suffix
                // concatenate without the comma. Further processing will take care of the suffix.
                uName = tmp.get(0) + ' ' + tmp.get(1);
            } else {
                // FamilyName, GivenNames
                // no suffix, assume the names are already formatted.
                return createWithBrackets(tmp.get(0), tmp.get(1), bracketSection);
            }
        }

        final String[] names = uName.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                return createWithBrackets(names[0], "", bracketSection);
            case 2:
                return createWithBrackets(names[1], names[0], bracketSection);
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


        return createWithBrackets(buildFamilyName.toString(), buildGivenNames.toString(),
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
     * Get the global preference: should an Author be shown with the given first.
     *
     * @param context Current context
     *
     * @return {@code true} if the given name should be shown before family name
     */
    public static boolean isGivenNameFirst(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false);
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
     * Return the formatted version of the name.
     *
     * @param context Current context
     * @param style   to apply
     *
     * @return formatted, but unfiltered, name
     */
    @NonNull
    private String getFormattedName(@NonNull final Context context,
                                    @Nullable final Style style) {
        final boolean givenNameFirst;
        if (style != null) {
            givenNameFirst = style.isShowAuthorByGivenName();
        } else {
            givenNameFirst = isGivenNameFirst(context);
        }

        return getFormattedName(givenNameFirst);
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
     * If this Author is a pen-name (pseudonym), then this method returns the real Author.
     *
     * @return the resolved real-author; or {@code null} if there is none
     */
    @Nullable
    public Author getRealAuthor() {
        // always assume the worst; resolve here AGAIN
        realAuthor = resolveRealAuthor(realAuthor);
        return realAuthor;
    }

    /**
     * Set the real-author for this Author.
     *
     * @param author to use
     *
     * @return the resolved real-author; or {@code null} if there is none
     */
    @Nullable
    public Author setRealAuthor(@Nullable final Author author) {
        realAuthor = resolveRealAuthor(author);
        return realAuthor;
    }

    @Nullable
    private Author resolveRealAuthor(@Nullable final Author author) {
        @Nullable
        Author a = author;
        if (a != null) {
            // resolve any nested reference
            while (a.getRealAuthor() != null) {
                a = a.getRealAuthor();
            }
        }

        // resolve 1:1 circular reference
        if (a != null && (a.isSameName(this))) {
            a = null;
        }
        return a;
    }

    @Type
    public int getType() {
        return type;
    }

    /**
     * Set the type(s).
     *
     * @param type to set
     */
    public void setType(@Type final int type) {
        this.type = type & TYPE_BITMASK_ALL;
    }

    /**
     * Add a type to the current type(s).
     *
     * @param type to add
     */
    public void addType(@Type final int type) {
        this.type |= type & TYPE_BITMASK_ALL;
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
     *          (if enabled) with the author type.
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
                           @Nullable final Style style) {
        String label;
        switch (details) {
            case Full: {
                label = getFormattedName(context, style);

                final FieldVisibility fieldVisibility = ServiceLocator.getInstance()
                                                                      .getGlobalFieldVisibility();
                if (fieldVisibility.isShowField(DBKey.AUTHOR_REAL_AUTHOR).orElse(true)) {
                    final Author author = getRealAuthor();
                    if (author != null) {
                        label += smallerText(context.getString(
                                R.string.lbl_author_pseudonym_of_X,
                                author.getFormattedName(context, style)));
                    }
                }

                if (fieldVisibility.isShowField(DBKey.AUTHOR_TYPE__BITMASK).orElse(true)) {
                    final String typeLabels = getTypeLabels(context);
                    if (!typeLabels.isEmpty()) {
                        label += smallerText(typeLabels);
                    }
                }
                break;
            }
            case AutoSelect:
            case Normal: {
                label = getFormattedName(context, style);
                break;
            }
            case Short: {
                if (givenNames.isEmpty()) {
                    label = familyName;
                } else {
                    final boolean givenNameFirst;
                    if (style != null) {
                        givenNameFirst = style.isShowAuthorByGivenName();
                    } else {
                        givenNameFirst = isGivenNameFirst(context);
                    }

                    if (givenNameFirst) {
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
     * @param style   (optional) to use
     *
     * @return styled and formatted name
     */
    @NonNull
    public CharSequence getStyledName(@NonNull final Context context,
                                      @Nullable final Style style) {
        final CharSequence name = getStyledName(context, style, (CharSequence) null);
        if (realAuthor == null) {
            return name;
        } else {
            return realAuthor.getStyledName(context, style, name);
        }
    }

    /**
     * Syntax sugar for {@link #getStyledName(Context, Style, CharSequence)}.
     * <p>
     * <strong>IMPORTANT: will only display correctly when used with a TextView.</strong>
     *
     * @param context   Current context
     * @param style     (optional) to use
     * @param pseudonym optional Author to combine with the actual name
     *
     * @return styled and formatted name
     *
     * @see #getStyledName(Context, Style, CharSequence)
     */
    @NonNull
    public CharSequence getStyledName(@NonNull final Context context,
                                      @Nullable final Style style,
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
     * @param style   (optional) to use
     * @param penName optional Author pen-name to combine with the actual name
     *
     * @return styled and formatted name
     *
     * @see #getStyledName(Context, Style, Author)
     */
    @NonNull
    public CharSequence getStyledName(@NonNull final Context context,
                                      @Nullable final Style style,
                                      @Nullable final CharSequence penName) {

        final String realName = getFormattedName(context, style);

        if (penName == null) {
            return realName;

        } else {
            final String filteredPenName = filterLtG(penName.toString());

            // Display the pseudonym as the 'normal' Author, but add the real
            // author ('this') name in a smaller italic font.
            final String fullName =
                    String.format("%1s %2s", filteredPenName,
                                  context.getString(R.string.lbl_author_pseudonym_of_X, realName));

            final SpannableString span = new SpannableString(fullName);
            final float relSize = ResourcesCompat
                    .getFloat(context.getResources(), R.dimen.bob_author_pseudonym_size);
            span.setSpan(new RelativeSizeSpan(relSize),
                         filteredPenName.length(), span.length(), 0);
            span.setSpan(new StyleSpan(Typeface.ITALIC),
                         filteredPenName.length(), span.length(), 0);
            return span;
        }
    }


    /**
     * Get a CSV string with the type of this author; or the empty string if no specific types
     * are set.
     *
     * @param context Current context
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    private String getTypeLabels(@NonNull final Context context) {
        if (type != TYPE_UNKNOWN) {
            final List<String> list = TYPES.entrySet()
                                           .stream()
                                           .filter(entry -> (entry.getKey() & (long) type) != 0)
                                           .map(entry -> context.getString(entry.getValue()))
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
     * Replace local details from another author.
     *
     * @param source            Author to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Author source,
                         final boolean includeBookFields) {
        familyName = source.familyName;
        givenNames = source.givenNames;
        complete = source.complete;
        realAuthor = source.realAuthor;

        if (includeBookFields) {
            type = source.type;
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(familyName);
        dest.writeString(givenNames);
        dest.writeByte((byte) (complete ? 1 : 0));
        dest.writeInt(type);
        dest.writeParcelable(realAuthor, flags);
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
        return Objects.hash(id, familyName, givenNames, realAuthor);
    }

    /**
     * Equality: <strong>id, family and given-names, realAuthor</strong>.
     * <ul>
     *   <li>'type' is a book field and is ignored here.</li>
     *   <li>'isComplete' is a user setting and is ignored here.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Author that = (Author) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(familyName, that.familyName)
               && Objects.equals(givenNames, that.givenNames)
               && Objects.equals(realAuthor, that.realAuthor);
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner sj = new StringJoiner(",", "Type{", "}");

        if ((type & TYPE_WRITER) != 0) {
            sj.add("TYPE_WRITER");
        }
        //        if ((mType & TYPE_ORIGINAL_SCRIPT_WRITER) != 0) {
        //            sj.add("TYPE_ORIGINAL_SCRIPT_WRITER");
        //        }
        if ((type & TYPE_FOREWORD) != 0) {
            sj.add("TYPE_FOREWORD");
        }
        if ((type & TYPE_AFTERWORD) != 0) {
            sj.add("TYPE_AFTERWORD");
        }


        if ((type & TYPE_TRANSLATOR) != 0) {
            sj.add("TYPE_TRANSLATOR");
        }
        if ((type & TYPE_INTRODUCTION) != 0) {
            sj.add("TYPE_INTRODUCTION");
        }
        if ((type & TYPE_EDITOR) != 0) {
            sj.add("TYPE_EDITOR");
        }
        if ((type & TYPE_CONTRIBUTOR) != 0) {
            sj.add("TYPE_CONTRIBUTOR");
        }


        if ((type & TYPE_COVER_ARTIST) != 0) {
            sj.add("TYPE_COVER_ARTIST");
        }
        if ((type & TYPE_COVER_INKING) != 0) {
            sj.add("TYPE_COVER_INKING");
        }
        if ((type & TYPE_NARRATOR) != 0) {
            sj.add("TYPE_NARRATOR");
        }
        if ((type & TYPE_COVER_COLORIST) != 0) {
            sj.add("TYPE_COVER_COLORIST");
        }


        if ((type & TYPE_ARTIST) != 0) {
            sj.add("TYPE_ARTIST");
        }
        if ((type & TYPE_INKING) != 0) {
            sj.add("TYPE_INKING");
        }
        if ((type & TYPE_COLORIST) != 0) {
            sj.add("TYPE_COLORIST");
        }
        if ((type & TYPE_PSEUDONYM) != 0) {
            sj.add("TYPE_PSEUDONYM");
        }

        return "Author{"
               + "id=" + id
               + ", familyName=`" + familyName + '`'
               + ", givenNames=`" + givenNames + '`'
               + ", complete=" + complete
               + ", type=0b" + Integer.toBinaryString(type) + ": " + sj
               + ", realAuthor=" + realAuthor
               + '}';
    }

    // NEWTHINGS: author type: add to the IntDef
    @IntDef(flag = true,
            value = {TYPE_UNKNOWN,
                    TYPE_WRITER, TYPE_FOREWORD, TYPE_AFTERWORD,
                    TYPE_TRANSLATOR, TYPE_INTRODUCTION, TYPE_EDITOR, TYPE_CONTRIBUTOR,
                    TYPE_COVER_ARTIST, TYPE_COVER_INKING, TYPE_NARRATOR, TYPE_COVER_COLORIST,
                    TYPE_ARTIST, TYPE_INKING, TYPE_COLORIST, TYPE_PSEUDONYM
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }
}

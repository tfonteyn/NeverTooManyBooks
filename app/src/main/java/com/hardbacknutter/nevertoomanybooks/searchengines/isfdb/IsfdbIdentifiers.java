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

package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * Taken from the calibre data table {@code identifier_sites}.
 * The nr in the comment is {@code identifier_site_id}.
 * <p>
 * SQL to pull usage from the ISFDB database:
 * <pre>
 *  SELECT count(*) AS c, identifiers.identifier_type_id, identifier_type_name,
 *    identifier_type_full_name
 *    FROM identifiers JOIN identifier_types ON
 *      identifiers.identifier_type_id=identifier_types.identifier_type_id
 *    GROUP BY identifiers.identifier_type_id
 *    ORDER BY c DESC
 * </pre>
 * December 2024:
 * <pre>
 *      c  identifier_type_id  identifier_type_name  identifier_type_full_name
 * ------  ------------------  --------------------  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 * 222724                   1  ASIN                  Amazon Standard Identification Number
 * 180395                  12  OCLC/WorldCat         Online Computer Library Center
 *  33242                   8  Goodreads             Goodreads social cataloging site
 *  32210                  10  LCCN                  Library of Congress Control Number
 *  30486                  17  Audible-ASIN          Audible ASIN
 *  29314                   6  DNB                   Deutsche Nationalbibliothek
 *  24139                  21  Reginald-3            Robert Reginald. Science Fiction and Fantasy Literature, 1975-1991: A Bibliography of Science Fiction, Fantasy, and Horror Fiction Books and Nonfiction Monographs. Gale Research Inc., 1992, 1512 p.
 *  17298                  20  Reginald-1            R. Reginald. Science Fiction and Fantasy Literature: A Checklist, 1700-1974, with Contemporary Science Fiction Authors II. Gale Research Co., 1979, 1141p.
 *   9655                  13  Open Library          Open Library
 *   5548                   2  BL                    The British Library
 *   5358                   4  BNF                   BibliothÃ¨que nationale de France
 *   5325                  26  NooSFere              NooSFere
 *   5018                  16  PPN                   De Nederlandse Bibliografie Pica Productie Nummer
 *   4204                   7  FantLab               Laboratoria Fantastiki
 *   3689                  15  BN                    Barnes and Noble
 *   2521                  18  LTF                   La Tercera Fundaci&#243;n
 *   1909                  24  Bleiler Early Years   Richard Bleiler, Everett F. Bleiler. Science-Fiction: The Early Years. Kent State University Press, 1991, xxiii+998 p.
 *   1901                  23  Bleiler Supernatural  Everett F. Bleiler. The Guide to Supernatural Fiction. Kent State University Press, 1983, xii+723 p.
 *   1823                   3  BNB                   The British National Bibliography
 *   1607                  35  FMI                   The FictionMags Index
 *   1331                  25  NILF                  Numero Identificativo della Letteratura Fantastica / Fantascienza
 *    843                  31  Libris XL             Libris XL - National Library of Sweden (new interface)
 *    830                  30  Libris                Libris - National Library of Sweden
 *    693                   9  JNB/JPNO              The Japanese National Bibliography
 *    647                  29  PORBASE               Biblioteca Nacional de Portugal
 *    420                  19  KBR                   De Belgische Bibliografie/La Bibliographie de Belgique
 *    408                  14  SFBG                  Catalog of books published in Bulgaria
 *    406                  27  SF-Leihbuch           Science Fiction-Leihbuch-Datenbank
 *    314                   5  COPAC (defunct)       UK/Irish union catalog
 *    185                  11  NDL                   National Diet Library
 *    167                  33  COBISS.BG             Co-operative Online Bibliographic Systems and Services - Bulgaria
 *    138                  22  Bleiler Gernsback     Everett F. Bleiler, Richard Bleiler. Science-Fiction: The Gernsback Years. Kent State University Press, 1998, xxxii+730pp
 *    130                  32  Biblioman             Библиоман (Biblioman)
 *     39                  28  NLA                   National Library of Australia
 *     28                  34  COBISS.SR             Co-operative Online Bibliographic Systems and Services - Serbia
 * </pre>
 * The patterns can have TWO groups which will be concatenated.
 * Can be generalised when needed in {@link IsfdbSearchEngine#parseSid(CharSequence)}.
 */
@SuppressWarnings("LongLine")
final class IsfdbIdentifiers {

    private static final Map<Pattern, String> IDENTIFIER_MAPPING = Map.ofEntries(
            // 1,2,3,4,5,6,22,23,24,25,26,27,28,29,37,38
            // "https://www.amazon.com/dp/%s?ie=UTF8&;tag=isfdb-20&;linkCode=as2&;camp=1789&;creative=9325"
            // "https://www.amazon.co.uk/dp/%s?ie=UTF8&;tag=isfdb-21"
            // "https://www.amazon.de/dp/%s"  (other country sites similar)
            Map.entry(Pattern.compile("https://www\\.amazon.+/dp/(.*?)(?:\\?|$)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_ASIN),
            // 18
            // "https://www.worldcat.org/oclc/%s"
            Map.entry(Pattern.compile("https://www\\.worldcat\\.org/oclc/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_OCLC),
            // 14
            // "https://www.goodreads.com/book/show/%s"
            Map.entry(Pattern.compile("https://www\\.goodreads\\.com/book/show/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_GOODREADS),
            // 16
            // "https://lccn.loc.gov/%s"
            Map.entry(Pattern.compile("https://lccn\\.loc\\.gov/(.*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_LCCN),
            // 31
            // "https://www.audible.com/pd/%s"
            Map.entry(Pattern.compile("https://www\\.audible\\.com/pd/(.*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_AUDIBLE),
            // 12
            // "https://d-nb.info/%s"
            Map.entry(Pattern.compile("https://d-nb\\.info/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_DNB),
            // 19
            // "https://openlibrary.org/books/%s"
            Map.entry(Pattern.compile("https://openlibrary\\.org/books/(.*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_OPEN_LIBRARY),
            // 7,8
            // "http://explore.bl.uk/primo_library/libweb/action/dlDisplay.do?vid=BLVU1&docId=%s"
            // "http://search.bl.uk/primo_library/libweb/action/search.do?fn=search&vl(freeText0)=%s"
            // Map.entry(Pattern.compile(".bl.uk",
            //          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE,
            //          Identifier.SID_BRITISH_LIBRARY),

            // 9
            // "https://catalogue.bnf.fr/ark:/12148/%s"
            Map.entry(Pattern.compile("https://catalogue\\.bnf\\.fr/ark:/12148/(.*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_BNF),
            // 35
            // "https://www.noosfere.org/livres/niourf.asp?numlivre=%s"
            Map.entry(Pattern.compile(
                              "https://www\\.noosfere\\.org/livres/niourf\\.asp\\?numlivre=(\\d*)",
                              Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_NOOSFERE),
            // 30
            // The site uses the picarta link which is defunct.
            // But the PPN is valid for https://kb.nl, i.e. the oclc link.
            // We look for both in case ISFDB starts using the new link unexpectedly.
            // https://webggc.oclc.org/cbs/DB=2.37/XMLPRS=Y/PPN?PPN=%s
            // http://picarta.pica.nl/xslt/DB=3.9/XMLPRS=Y/PPN?PPN=%s
            Map.entry(Pattern.compile("http.*?/PPN\\?PPN=(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_KBNL),
            // 13
            // "https://fantlab.ru/edition%s"
            Map.entry(Pattern.compile("https://fantlab\\.ru/edition(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_FANTLAB),
            // 21
            // "https://www.barnesandnoble.com/s/%s"
            Map.entry(Pattern.compile("https://www\\.barnesandnoble\\.com/s/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_BARNES_AND_NOBLE),
            // 32
            // "https://tercerafundacion.net/biblioteca/ver/libro/%s"
            Map.entry(Pattern.compile("https://tercerafundacion\\.net/biblioteca/ver/libro/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_TERCERA_FUNDACION),
            // 48
            // "http://www.philsp.com/homeville/FMI/ZZPERMLINK.ASP?NAME='%s'"
            Map.entry(Pattern.compile(
                              "http://www\\.philsp\\.com/homeville/FMI/ZZPERMLINK\\.ASP\\?NAME='(.*)'",
                              Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "fmi"),
            // 34
            // https://www.fantascienza.com/catalogo/volumi/NILF%s
            Map.entry(Pattern.compile("https://www\\.fantascienza\\.com/catalogo/volumi/NILF(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_NILF),

            // 41
            // "https://libris.kb.se/bib/%s"
            Map.entry(Pattern.compile("https://libris\\.kb\\.se/bib/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_LIBRIS),
            // 41
            // "https://libris.kb.se/resource/bib/%s"
            Map.entry(Pattern.compile("https://libris\\.kb\\.se/resource/bib/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_LIBRIS),
            // 43
            // "https://libris.kb.se/katalogisering/%s"
            Map.entry(Pattern.compile("https://libris\\.kb\\.se/katalogisering/(.*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_LIBRIS_XL),
            // 44
            // "https://libris.kb.se/%s"
            // do not accept any '/' except for the one after the host
            Map.entry(Pattern.compile("https://libris\\.kb\\.se/([^/]*)$",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_LIBRIS_XL),

            // 15
            // "https://iss.ndl.go.jp/api/openurl?ndl_jpno=%s&locale=en"
            // The Japanese National Bibliography
            Map.entry(Pattern.compile("https://iss\\.ndl\\.go\\.jp/api/openurl\\?ndl_jpno=(\\d*)"
                                      + "&locale=en",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "jpno"),

            // 40
            // "http://id.bnportugal.gov.pt/bib/porbase/%s"
            Map.entry(Pattern.compile("http://id\\.bnportugal\\.gov\\.pt/bib/porbase/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_PORBASE),
            // 33
            // "https://opac.kbr.be/Library/doc/SYRACUSE/%s/"
            Map.entry(Pattern.compile("https://opac\\.kbr\\.be/Library/doc/SYRACUSE/(.*)/",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      Identifier.SID_KBR),


            // 20
            // "http://www.sfbg.us/book/%s"
            //  Catalog of books published in Bulgaria
            Map.entry(Pattern.compile("http://www\\.sfbg\\.us/book/(.*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "sfbg"),
            // 36
            // "http://www.sf-leihbuch.de/index.cfm?bid=%s"
            Map.entry(Pattern.compile("http://www\\.sf-leihbuch\\.de/index\\.cfm\\?bid=(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "sf-leihbuch"),


            // National Diet Library (Japan)
            Map.entry(Pattern.compile("https://id\\.ndl\\.go\\.jp/bib/(\\d*)/eng",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "ndl"),

            // 39
            // https://nla.gov.au/nla.cat-vn%s
            Map.entry(Pattern.compile("https://nla\\.gov\\.au/nla\\.cat-vn(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "nla"),
            // 45
            // https://biblioman.chitanka.info/books/%s
            // Bulgarian
            Map.entry(Pattern.compile("https://biblioman.chitanka.info/books/(\\d*)",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "biblioman"),
            // 46
            // https://plus.bg.cobiss.net/opac7/bib/%s#full
            // Slovenia, Serbia, and surrounding
            Map.entry(Pattern.compile("https://plus\\.bg\\.cobiss\\.net/opac7/bib/(\\d*)#full",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "cobiss.bg"),
            // 47
            // https://plus.sr.cobiss.net/opac7/bib/%s#full
            Map.entry(Pattern.compile("https://plus\\.sr\\.cobiss\\.net/opac7/bib/(\\d*)#full",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                      "cobiss.sr")
    );

    private IsfdbIdentifiers() {
    }

    @NonNull
    static Set<Map.Entry<Pattern, String>> entrySet() {
        return IDENTIFIER_MAPPING.entrySet();
    }
}

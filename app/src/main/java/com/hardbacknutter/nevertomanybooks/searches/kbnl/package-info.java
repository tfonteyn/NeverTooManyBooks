/**
 *
 * Covers:
 *
 * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
 *
 *
 *
 * http://opc4.kb.nl/
 * <p>
 * OCLC / PiCarta  based.
 * <p>
 * Need to check if API is open.
 * <p>
 * <p>
 * Full:
 * http://opc4imgbase.kb.nl/psi_help/?DB=1&LNG=NE&COOKIE=&MENUIKT=1016,4,2001,1004,1009,5200,5040,4050,4014,53,1007,2,3003#search%20results
 * <p>
 * Simple:
 * <p>
 * base url:
 * http://opc4.kb.nl/DB=1/SET=1/TTL=1/LNG=NE/
 * <p>
 * LNG=NE  : Nederlands (dutch)
 * LNG=EN  : english
 * <p>
 * ISBN form:
 *
 * <form class="form" method="GET" action="CMD">
 * ACT="SRCHA"
 * IKT="1007"
 * SRT="YOP"
 * TRM="978-9-46373-145-4"
 * <p>
 * http://opc4.kb.nl/DB=1/SET=1/TTL=1/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=978-9-46373-145-4
 * http://opc4.kb.nl/DB=1/SET=1/TTL=1/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=9022990559
 * <p>
 * <p>
 * Full form:
 *
 * <form method="GET" action="CMD">
 *
 * <select name="ACT">
 * <option value="SRCHA" selected="">search [and]</option>
 * <option value="SRCH">search [or]</option>
 * <option value="BRWS">browse</option>
 * <option value="AND">restrict</option>
 * <option value="OR">enlarge</option>
 * <option value="NOT">except</option>
 * <option value="RLV">rerank</option>
 * </select>
 *
 * <select name="IKT">
 * <option value="1016">all words</option>
 * <option value="4">title words</option>
 * <option value="2001">full title journal</option>
 * <option value="1004">author</option>
 * <option value="1009">subject: person</option>
 * <option value="5200">subject-Brinkman</option>
 * <option value="5040">subject heading (GOO)</option>
 * <option value="4050">basic-code (GOO)</option>
 * <option value="4014">systematic code</option>
 * <option value="53">request number</option>
 * <option value="1007" selected="">isbn, issn</option>
 * <option value="2">corporation</option>
 * <option value="3003">publisher</option>
 * </select>
 *
 * <select name="SRT" size="1" class="cmdsel">
 * <option value="YOP" selected="">year of publication</option>
 * <option value="RLV">relevance</option>
 * <option value="LST_aty">author</option>
 * <option value="LST_tay">title</option>
 * <option value="LST_dtay">selection date</option>
 * </select>
 *
 * <input type="text" name="TRM" value="978-9-46373-145-4">
 * <input type="submit" value="search">
 *
 * <input type="checkbox" name="FUZZY" value="Y">
 *
 * </form>
 */
package com.hardbacknutter.nevertomanybooks.searches.kbnl;
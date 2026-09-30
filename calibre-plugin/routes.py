#  @Copyright 2018-2026 HardBackNutter
#  @License GNU General Public License
#
#  This file is part of NeverTooManyBooks.
#
#  NeverTooManyBooks is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  NeverTooManyBooks is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
#  See the GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.

from calibre.srv.errors import HTTPBadRequest
from calibre.srv.routes import endpoint, json
from calibre.srv.utils import get_db


@endpoint('/nevertoomanybooks/library-info', postprocess=json)
def library_info(ctx, rd):
    """
    Return info about available libraries.
    This is an extended version of '/ajax/library-info'.

    The 'library_map' and 'default_library' are kept identical with
    the original Calibre call.

    All extra information is put in 'library_data' keyed by the same libraryStringId
    as used in 'library_map'.
    """
    library_map, default_library = ctx.library_info(rd)
    library_data = {}

    for libId in library_map:
        db = get_db(ctx, rd, libId)
        with db.safe_read_lock:
            details = {
                'uuid': db.library_id,
                # repeated for completeness
                'name': library_map[libId],
                # total number of books
                'total_num': len(db.all_book_ids()),
                'custom_fields': db.field_metadata.custom_field_metadata()
            }

            virtual_libraries = db.pref('virtual_libraries')
            if virtual_libraries is not None:
                details['virtual_libraries'] = virtual_libraries

            library_data[libId] = details

    return {
        'library_map': library_map,
        'default_library': default_library,
        'library_data': library_data
    }


@endpoint('/nevertoomanybooks/virtual-libraries-for-books/{book_ids}/{library_id=None}',
          postprocess=json)
def virtual_libraries_for_books(ctx, rd, book_ids, library_id):
    """
     Return the virtual libraries for each of the book ids
    """
    db = get_db(ctx, rd, library_id)
    with db.safe_read_lock:
        try:
            ids = {int(x) for x in book_ids.split(',')}
        except Exception:
            raise HTTPBadRequest('invalid book_ids: {}'.format(book_ids))
        return db.virtual_libraries_for_books(ids)
    return None

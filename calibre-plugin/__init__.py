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

from calibre.customize import ContentServerPlugin

class NeverTooManyBooksPlugin(ContentServerPlugin):
    name = 'NeverTooManyBooks Calibre plugin'
    description = 'Provides endpoints to get virtual library info'
    supported_platforms = ['windows', 'osx', 'linux']
    author = 'hardbacknutter'
    version = (1, 0, 0)
    minimum_calibre_version = (9, 12, 0)

    # noinspection method-may-be-static
    def content_server_endpoints(self):
        """
        Registers custom REST endpoints with Calibre's CCS.
        """

        # lazy import to prevent circular initialisation issues.
        from .routes import library_info, virtual_libraries_for_books

        return [library_info, virtual_libraries_for_books]

# Third-party notices

PureTV additions and documentation are covered by the MIT licence in [LICENSE](LICENSE).
This does not replace the notices applying to incorporated third-party code.

## Launcher and shared web code

Portions originate from Free My TV and its contributors:
https://github.com/simbasjungles/free-my-tv

The original copyright and full MIT permission notice are preserved in
[third_party/LICENSE-launcher-MIT.txt](third_party/LICENSE-launcher-MIT.txt).
The source distribution records pinned revisions and file hashes alongside its third-party notices.
This is a source attribution, not the current product name or an endorsement.

The webOS installation flow credits danielthejoker18's MIT-licensed work:
https://github.com/danielthejoker18/FreeMyTvWebOS

## Other components

- Inter font: SIL Open Font License, retained in app/assets/fonts/OFL.txt.
- Material Symbols by Google: Apache 2.0, retained in app/assets/symbols/LICENSE-material-symbols.txt. The interface converts symbols to PNG.
- Web SVG app logos: Simple Icons, CC0 1.0. Cropping/tinting is identified in the SVG comments. The full text is in LICENSE-simple-icons-CC0.txt and web packages include it under third_party/.
- The inherited web/icons/youtube.png and prime.png are recorded in the pinned upstream MIT source snapshot; they are not claimed as Simple Icons vectors. Names and logos remain their owners' trademarks.
- Optional AirPlay receiver: jqssun/android-airplay-server v0.0.36, GPL-3.0. It is a separate application, with its own licence and full corresponding source distribution. No receiver GPL source is copied into the MIT launcher.
- Weather data: Open-Meteo (https://open-meteo.com/), CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Location search: GeoNames (https://www.geonames.org/), CC BY 4.0. Values are formatted and converted to selected units.
- TMDB: This product uses the TMDB API but is not endorsed or certified by TMDB. https://www.themoviedb.org/ . The unmodified TMDB blue-short logo comes from its official developer documentation (https://files.readme.io/29c6fee-blue_short.svg); the PNG is a raster export. It is used for attribution, not relicensed as MIT.
- Optional TMDB/Watchmode integrations require the builder's own key and compliance with the provider's service terms. The generic package includes no API credentials. Catalogue images, app-published recommendation artwork and installed-app logos remain their owners' property; the launcher licence does not relicense them.

PureTV documentation illustrations are original project contributions. They are labelled as illustrations,
not physical-device screenshots. No vendor affiliation is claimed.
The PureTV application icon is original MIT artwork in tools/artwork/puretv-icon.svg.
The separate TMDB attribution mark in that folder is third-party artwork governed by TMDB's branding terms.

The component inventory and exact notice/asset hashes are recorded in component-inventory.json in the source distribution.

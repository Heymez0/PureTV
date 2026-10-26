# PureTV icon source

Original PureTV project artwork, MIT-licensed with the repository. The charcoal, warm gold and white match the interface.
`puretv-icon.svg` is the editable master for the Cinema design: a gold TV frame with an ivory play mark.
The Android TV wide banner is a separate asset.
The platform PNGs are checked in, so building PureTV needs no image tooling.

Optional asset regeneration requires Node.js and the development-only `sharp` package:

```sh
node tools/artwork/render-icon.cjs
```

The image exporter is not part of any APK/IPK/WGT and adds no runtime dependency.
# Third-party attribution artwork

`tmdb-logo.svg` is the unmodified blue-short TMDB mark published in the official developer documentation:
https://files.readme.io/29c6fee-blue_short.svg . Its raster export is bundled only for credits.
It is governed by TMDB's branding terms and is excluded from the original PureTV artwork licence.

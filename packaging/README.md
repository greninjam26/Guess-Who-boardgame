# Packaging

Icons for the installers, drawn by [`../tools/ApplicationIcon.java`](../tools/ApplicationIcon.java).

    java tools/ApplicationIcon.java
    iconutil --convert icns packaging/GuessWho.iconset --output packaging/GuessWho.icns

The first command writes `GuessWho.ico` for Windows, `icon.png`, and the
`GuessWho.iconset` directory. The second turns that directory into the macOS
`GuessWho.icns`, and only exists on macOS — which is why the finished `.icns`
is committed rather than built on demand.

The intermediate `.iconset` is not committed; the first command recreates it.

Each size is drawn at its own size rather than scaled down from one large
picture, because the sixteen-pixel version is the one people see most and it
goes muddy if it is only a shrunken copy.

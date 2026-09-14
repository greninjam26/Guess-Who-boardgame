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

## Release endpoint

`build-installer.sh` defaults to `http://localhost:8080` for local development.
A distributable build must set `GUESSWHO_SERVER_URL` to the public HTTPS origin:

```bash
GUESSWHO_SERVER_URL=https://greninja-guesswho.duckdns.org \
  ./packaging/build-installer.sh
```

The tagged-release workflow refuses to publish without that repository
variable. Before tagging v2.0, download both manual-workflow artifacts and
launch the `.dmg` on Apple silicon macOS and the `.msi` on Windows. A successful
CI build proves that `jpackage` produced each file; release acceptance also
proves that each installed app launches and contacts the public server without a
JVM override.

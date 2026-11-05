# Front-end integration (ES-DE & Beacon)

WinHub can be launched from Android front-ends like **ES-DE** and **Beacon**, so your exported
Windows shortcuts show up as games. It doesn't work out of the box — you have to point the front-end at
WinHub's launch activity and hand it the shortcut. Both front-ends below do the same thing: launch
`XServerDisplayActivity` with a `shortcut_path` extra pointing at your exported `.desktop` file. A plain
app launch instead opens WinHub's games list (its default `MainActivity`).

> Thanks to **xabbu33** for the original pull request and tutorial this guide is based on.

## Package & activity

WinHub's installed package (applicationId) is **`com.winhub.hernandez`**, and its launch
activity class is **`com.winhub.hernandez.XServerDisplayActivity`**. The two alternate-package builds
below install under a different package name but keep the same activity class, so always use the
**fully-qualified** component — the `/.XServerDisplayActivity` shorthand only works for the standard build:

```
com.winhub.hernandez/com.winhub.hernandez.XServerDisplayActivity
```

> **Installed a differently-named build?** The alternate flavors ship under a different package —
> swap the first half accordingly. The activity part is identical on every flavor.
>
> | Flavor | Package (applicationId) |
> |---|---|
> | WinHub (standard) | `com.winhub.hernandez` |
> | WinHub Ludashi | `com.ludashi.benchmark` |
> | WinHub PuBG | `com.tencent.ig` |

## The `am start` command

Use the block for the build you installed. Replace the quoted path with the full path to an exported
WinHub `.desktop` shortcut. (A front-end substitutes this automatically — see ES-DE / Beacon
below for the token each one uses.)

**WinHub — standard (`com.winhub.hernandez`):**

```
am start \
  -n com.winhub.hernandez/com.winhub.hernandez.XServerDisplayActivity \
  -e shortcut_path "/storage/emulated/0/Winlator/Shortcuts/Your Game.desktop" \
  --activity-clear-task \
  --activity-clear-top
```

**WinHub Ludashi (`com.ludashi.benchmark`):**

```
am start \
  -n com.ludashi.benchmark/com.winhub.hernandez.XServerDisplayActivity \
  -e shortcut_path "/storage/emulated/0/Winlator/Shortcuts/Your Game.desktop" \
  --activity-clear-task \
  --activity-clear-top
```

**WinHub PuBG (`com.tencent.ig`):**

```
am start \
  -n com.tencent.ig/com.winhub.hernandez.XServerDisplayActivity \
  -e shortcut_path "/storage/emulated/0/Winlator/Shortcuts/Your Game.desktop" \
  --activity-clear-task \
  --activity-clear-top
```

## ES-DE setup

Add this to your `custom_systems`/`es_find_rules.xml`:

```xml
<emulator name="BANNERLATOR">
    <rule type="androidpackage">
        <entry>com.winhub.hernandez/com.winhub.hernandez.XServerDisplayActivity</entry>
    </rule>
</emulator>
```

And in `es_systems.xml`:

```xml
<system>
    <name>windows</name>
    <fullname>Microsoft Windows</fullname>
    <path>%ROMPATH%/windows</path>
    <extension>.desktop .DESKTOP</extension>
    <command label="WinHub (Standalone)">%EMULATOR_BANNERLATOR% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %EXTRA_shortcut_path%=%ROM%</command>
    <platform>windows</platform>
    <theme>windows</theme>
</system>
```

Drop your exported `.desktop` shortcuts from WinHub into `ROMs/windows/` and they'll show up as
games in ES-DE.

## Beacon setup

Beacon's default "launch the app" behaviour opens WinHub's games list, because it starts the
default launcher activity (`MainActivity`) without the shortcut. You have to use Beacon's **custom
launch** so it targets `XServerDisplayActivity` and passes the `shortcut_path` extra.

In Beacon: **Settings → + (add platform)** → Platform Type **Custom**, pick your WinHub build as
the **Player app**, and set the **ROMs folder** to where your exported `.desktop` shortcuts live (e.g.
`/storage/emulated/0/Winlator/Shortcuts/`). Under **Advanced** set **File handling: Default** and
**Use custom launch: True**, then paste the command for the build you installed.

`{file_path}` is Beacon's absolute-file-path token — it becomes the selected `.desktop`'s path, so
WinHub opens **that game** directly. (Beacon's file tokens are `{file_path}`, `{file_content}`
and `{file_uri}` — use `{file_path}` here.)

**WinHub — standard (`com.winhub.hernandez`):**

```
am start -n com.winhub.hernandez/com.winhub.hernandez.XServerDisplayActivity -e shortcut_path {file_path}
```

**WinHub Ludashi (`com.ludashi.benchmark`):**

```
am start -n com.ludashi.benchmark/com.winhub.hernandez.XServerDisplayActivity -e shortcut_path {file_path}
```

**WinHub PuBG (`com.tencent.ig`):**

```
am start -n com.tencent.ig/com.winhub.hernandez.XServerDisplayActivity -e shortcut_path {file_path}
```

> `-e` passes a **string** extra (`--es` works too). Use the `{file_path}` token — **not** `{file.path}`
> (dot), which Beacon doesn't recognise and would pass literally, and **not** `{file_content}`, which
> passes the file's *contents* instead of its path. If Beacon ever foregrounds an already-open session
> instead of launching fresh, add ` --activity-clear-task --activity-clear-top`.

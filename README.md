<img src="https://gitea.angry.im/PeterCxy/OpenEUICC/media/branch/master/art/OpenEUICCBG.svg" width="512" height="300">

A fully free and open-source Local Profile Assistant implementation for Android devices.

Roam2World B2B UI work is tracked on `feature/r2w-mockup-implementation`.
Wallet and reports polish is applied in small focused commits.

__Google is threatening to lock down the Android platform in 2026. OpenEUICC and EasyEUICC, as Free Software projects, will never accept Google's proposed third-party developer verification flow. Join us in Keeping Android Open at <https://keepandroidopen.org/>.__

There are two variants of this project, OpenEUICC and [EasyEUICC](https://easyeuicc.org):

|                               |            OpenEUICC            |      EasyEUICC      |
|:------------------------------|:-------------------------------:|:-------------------:|
| Privileged                    | Must be installed as system app |         No          |
| Internal eSIM                 |            Supported            |     Unsupported     |
| External eSIM [^1]            |            Supported            |      Supported      |
| USB Readers                   |            Supported            |      Supported      |
| Requires allowlisting by eSIM |               No                |  Yes -- except USB  |
| System Integration            |          Partial [^2]           |         No          |
| Minimum Android Version       |      Android 11 or higher       | Android 9 or higher |

[^1]: Also known as "Removable eSIM"
[^2]: Carrier Partner API unimplemented yet

Some side notes:

1. When privileged, OpenEUICC supports any eUICC chip that implements the [SGP.22] standard, internal or external.
   However, there is **no guarantee** that external (removable) eSIMs actually follow the standard.
   Please **DO NOT** submit bug reports for non-functioning removable eSIMs.
   They are **NOT** officially supported unless they also support / are supported by EasyEUICC, the unprivileged
   variant.
2. Both variants support accessing eUICC chips through USB CCID readers,
   regardless of whether the chip contains the correct ARA-M hash to allow for unprivileged access.
   However, only `T=0` readers that use the standard [USB CCID protocol][usb-ccid] are supported.
3. Prebuilt release-mode EasyEUICC apks can be downloaded [here][releases].
   For OpenEUICC, no official release is currently provided and only debug mode APKs and Magisk modules can be found in
   the [CI page][actions].
4. For removable eSIM chip vendors: to have your chip supported by official builds of EasyEUICC when inserted,
   include the ARA-M hash `2A2FA878BC7C3354C2CF82935A5945A3EDAE4AFA`.

[sgp.22]: https://www.gsma.com/solutions-and-impact/technologies/esim/gsma_resources/sgp-22-v2-2-2/ "SGP.22 v2.2.2"

[usb-ccid]: https://en.wikipedia.org/wiki/CCID_%28protocol%29 "USB CCID Protocol"

[releases]: https://gitea.angry.im/PeterCxy/OpenEUICC/releases "EasyEUICC Releases"

[actions]: https://gitea.angry.im/PeterCxy/OpenEUICC/actions "OpenEUICC Actions"

**This project is Free Software licensed under GNU GPL v3, WITHOUT the "or later" clause.**
Any modification and derivative work **MUST** be released under the SAME license, which means, at the very least, that
 the source code **MUST** be available upon request.

**If you are releasing a modification of this app, you are kindly asked to make changes to at least the app name and
package name.**

# Building (Gradle)

Make sure you have all submodules cloned and updated by running

```shell
git submodule update --init
```

A file `keystore.properties` is required in the root directory. Template:

```ini
storePassword=my-store-password
keyPassword=my-password
keyAlias=my-key
unprivKeyPassword=my-unpriv-password
unprivKeyAlias=my-unpriv-key
storeFile=/path/to/android/keystore
```

Note that you must have a Java-compatible keystore generated first.

To build the privileged OpenEUICC:

```shell
./gradlew :app:assembleRelease
```

For EasyEUICC:
# wolfHSM (Yocto/OE recipe)

Stages [wolfHSM](https://github.com/wolfSSL/wolfHSM) sources and headers into
the sysroot so other recipes can compile them into their own binaries.

## Recipes

| Recipe | Purpose |
|---|---|
| `wolfhsm_1.5.0.bb` | Stages `wolfhsm/` (headers), `src/` and the selected `port/` directories to `${datadir}/wolfhsm`, plus a `wolfhsm.mk` build fragment. Also installs the headers at `${includedir}/wolfhsm`. |

## Why this stages source instead of building a library

wolfHSM is configured by the application that uses it. `wolfhsm/wh_settings.h`
does `#include "wolfhsm_cfg.h"` whenever `WOLFHSM_CFG` is defined, and that
header selects the transport, the NVM backend, buffer sizes, whether crypto is
compiled in at all, and much else besides. Two consumers with different
`wolfhsm_cfg.h` files do not share an ABI, so there is no single `libwolfhsm`
that would be correct to ship.

wolfHSM also has no build system to drive: its top-level `Makefile` only
recurses into `test/`, `benchmark/`, `tools/` and `examples/`, each of which
brings its own `wolfhsm_cfg.h`. Upstream expects you to compile `src/*.c` and
one `port/*/` directory directly into your application. This recipe makes that
possible from a Yocto build without vendoring a checkout.

## Consuming it from a recipe

```bitbake
DEPENDS += "wolfhsm"

do_compile() {
    oe_runmake WOLFHSM_DIR="${STAGING_DATADIR}/wolfhsm"
}
```

Your Makefile then compiles `$(WOLFHSM_DIR)/src/*.c` and
`$(WOLFHSM_DIR)/port/posix/*.c` with `-I$(WOLFHSM_DIR) -DWOLFHSM_CFG` and an
include path pointing at your own `wolfhsm_cfg.h`.

### wolfSSL requirements

`wolfhsm/wh_settings.h` includes `<wolfssl/options.h>` and the wolfCrypt
headers unless `WOLFHSM_CFG_NO_CRYPTO` is defined, so the recipe carries
`DEPENDS += "virtual/wolfssl"` and the headers are in your sysroot without you
asking for them. Two things it cannot do for you:

- wolfSSL must be configured with `--enable-cryptocb --enable-keygen`. Add
  them in `local.conf`, e.g.
  `EXTRA_OECONF:append:pn-wolfssl = " --enable-cryptocb --enable-keygen"`.
- Do not compile the staged sources with a strict `-std=c99` (or `-std=c90`);
  use `-std=gnu99` or later. Worth stating because upstream's own
  `examples/posix` Makefiles do exactly that
  (`wh_posix_server` sets `-std=c99`, `wh_posix_client` sets `CSTD ?=
  -std=c90`), so copying one of them verbatim into a recipe will not build.

Alternatively, include the staged fragment and use the variables it defines:

```make
include $(WOLFHSM_DIR)/wolfhsm.mk
CFLAGS += $(WOLFHSM_INC) -DWOLFHSM_CFG -I$(MY_CONFIG_DIR)
SRC    += $(WOLFHSM_SRC) $(WOLFHSM_PORT_SRC)
```

`wolfhsm.mk` resolves `WOLFHSM_DIR` from its own location, so it works
unchanged from a recipe sysroot, an SDK sysroot, or a plain copy.

## Selecting ports

wolfHSM ships ports for `posix`, `skeleton`, `armv8m-tz`, `microchip`,
`infineon`, `stmicro`, `renesas` and `ti`. Only `posix` is staged by default;
staging all of them would put a lot of unrelated vendor code in every sysroot.
Override in `local.conf` or a bbappend:

```bitbake
WOLFHSM_PORTS = "posix infineon"
```

Naming a port that does not exist in the source tree is a `bbfatal` rather
than a silent no-op.

## Packaging

Everything lands in `wolfhsm-dev`; `FILES` for `${PN}` is explicitly emptied so
the default `${datadir}/${BPN}` claim cannot pull the staging directory into a
runtime package. wolfHSM source is a build input, not a runtime artifact, and
should never appear in a target rootfs. `RDEPENDS` for `${PN}-dev` is cleared
for the same reason: bitbake's default would make `wolfhsm-dev` depend on a
runtime `wolfhsm` package that is deliberately never produced.

Both are set from an anonymous python function via `wolfssl_varSet()` from
`wolfssl-compatibility.bbclass`, because the colon override syntax
(`FILES:${PN}`) does not parse on releases older than honister, which this
layer still supports.

To cross-compile a wolfHSM consumer from an SDK, have the consumer's own `-dev`
package pull the headers in:

```bitbake
RDEPENDS:${PN}-dev += "wolfhsm-dev"
```

or, if there is no such consumer package, add it to the SDK directly:

```bitbake
TOOLCHAIN_TARGET_TASK:append = " wolfhsm-dev"
```

## Pinning

`wolfhsm.inc` pins the release with `nobranch=1;rev=<sha>` in `SRC_URI`, the
same way the other recipes in this layer do, and the `.bb` filename carries
the matching version. To build a different revision, override the whole
`SRC_URI` from a bbappend rather than setting `SRCREV`, which has no effect
when the revision is given in the URL.

The snippets above use the colon override syntax of honister and later. On
sumo through hardknott, write them with underscores instead
(`RDEPENDS_${PN}-dev`, `TOOLCHAIN_TARGET_TASK_append`).

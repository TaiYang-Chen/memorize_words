# Basis Universal runtime subset

This directory vendors the transcoder-only files needed by the Android KTX2
sprite runtime. No encoder or command-line tool sources are included.

- Upstream: https://github.com/BinomialLLC/basis_universal
- Upstream commit: `5f6a0a0ca66c34e1dad6da7ce43d1d34ca8fef4d`
- Retrieved from the upstream `master` branch on 2026-07-19.
- Basis Universal license: Apache License 2.0; see `LICENSE` and `NOTICE`.
- Bundled Zstandard decoder license: BSD 3-Clause; see `zstd/LICENSE`.

The local CMake target defines the feature switches that retain KTX2, Zstd,
UASTC LDR, ETC1 RGB, and ETC2 RGBA while disabling unrelated output formats.
`transcoder/basisu_transcoder.cpp` has one local build-only modification: the
XBC7 header and implementation includes are guarded when
`BASISD_SUPPORT_XUASTC=0`. The upstream declarations and implementations are
already feature-gated, but the includes themselves are unconditional in the
pinned revision and fail a minimal XUASTC-disabled build. The modified source
contains the notice required by Apache-2.0 section 4(b).

The source files were downloaded from immutable raw GitHub URLs of the form:

`https://raw.githubusercontent.com/BinomialLLC/basis_universal/5f6a0a0ca66c34e1dad6da7ce43d1d34ca8fef4d/<path>`


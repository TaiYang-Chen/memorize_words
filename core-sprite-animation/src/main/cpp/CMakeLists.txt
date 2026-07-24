cmake_minimum_required(VERSION 3.18.1)

project(sprite_basis LANGUAGES C CXX)

add_library(
    sprite_basis
    SHARED
    sprite_basis_jni.cpp
    third_party/basisu/transcoder/basisu_transcoder.cpp
    third_party/basisu/zstd/zstddeclib.c
)

target_compile_features(sprite_basis PRIVATE cxx_std_17)

# This runtime only accepts single-level UASTC LDR KTX2 texture arrays and
# transcodes them to the two formats used by the Android renderer. Disabling
# unrelated targets removes large lookup tables and dead transcoder paths.
target_compile_definitions(
    sprite_basis
    PRIVATE
    BASISD_SUPPORT_KTX2=1
    BASISD_SUPPORT_KTX2_ZSTD=1
    BASISD_SUPPORT_UASTC=1
    BASISD_SUPPORT_UASTC_HDR=0
    BASISD_SUPPORT_XUASTC=0
    BASISD_SUPPORT_DXT1=0
    BASISD_SUPPORT_DXT5A=0
    BASISD_SUPPORT_BC7=0
    BASISD_SUPPORT_BC7_MODE5=0
    BASISD_SUPPORT_PVRTC1=0
    BASISD_SUPPORT_ETC2_EAC_A8=1
    BASISD_SUPPORT_ASTC=0
    BASISD_SUPPORT_ASTC_HIGHER_OPAQUE_QUALITY=0
    BASISD_SUPPORT_ATC=0
    BASISD_SUPPORT_ETC2_EAC_RG11=0
    BASISD_SUPPORT_FXT1=0
    BASISD_SUPPORT_PVRTC2=0
)

target_compile_options(
    sprite_basis
    PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

target_link_options(
    sprite_basis
    PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map
)


cmake_minimum_required(VERSION 3.18.1)

project(basis_ktx2_validator LANGUAGES C CXX)

set(
    BASISU_ROOT
    "${CMAKE_CURRENT_LIST_DIR}/../../core-sprite-animation/src/main/cpp/third_party/basisu"
)

add_executable(
    basis_ktx2_validator
    basis_ktx2_validator.cpp
    "${BASISU_ROOT}/transcoder/basisu_transcoder.cpp"
    "${BASISU_ROOT}/zstd/zstddeclib.c"
)

target_include_directories(
    basis_ktx2_validator
    PRIVATE
    "${BASISU_ROOT}/transcoder"
)

target_compile_features(basis_ktx2_validator PRIVATE cxx_std_17)

# Keep this feature set aligned with the Android runtime transcoder. The release validator must
# exercise exactly the ETC2 RGBA and ETC1 RGB/alpha targets used on devices.
target_compile_definitions(
    basis_ktx2_validator
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

if(MSVC)
    target_compile_options(basis_ktx2_validator PRIVATE /O2 /EHsc)
else()
    target_compile_options(basis_ktx2_validator PRIVATE -O3)
endif()

#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

#include "basisu_transcoder.h"

namespace {

constexpr std::uint64_t kMaxKtx2FileBytes = 256ULL * 1024ULL * 1024ULL;
constexpr std::uint32_t kMaxPages = 16U;
constexpr std::uint32_t kUastcLdrColorModel = 166U;
constexpr std::uint32_t kUastcRgbaChannel = 3U;
constexpr std::uint32_t kStraightAlphaFlags = 0U;
constexpr std::uint32_t kBt709Primaries = 1U;
constexpr const char* kVersion =
    "basis_ktx2_validator 1 basisu 5f6a0a0ca66c34e1dad6da7ce43d1d34ca8fef4d";

int fail(const std::string& message) {
    std::cerr << "error: " << message << '\n';
    return 2;
}

bool parse_positive_u32(const char* text, std::uint32_t* output) {
    if (text == nullptr || *text == '\0') {
        return false;
    }
    errno = 0;
    char* end = nullptr;
    const unsigned long long value = std::strtoull(text, &end, 10);
    if (errno != 0 || end == text || *end != '\0' || value == 0ULL ||
        value > std::numeric_limits<std::uint32_t>::max()) {
        return false;
    }
    *output = static_cast<std::uint32_t>(value);
    return true;
}

bool read_file(const std::string& path, std::vector<std::uint8_t>* output) {
    std::ifstream stream(path, std::ios::binary | std::ios::ate);
    if (!stream) {
        return false;
    }
    const std::streamoff stream_size = stream.tellg();
    if (stream_size <= 0 ||
        static_cast<std::uint64_t>(stream_size) > kMaxKtx2FileBytes ||
        static_cast<std::uint64_t>(stream_size) > std::numeric_limits<std::uint32_t>::max()) {
        return false;
    }
    output->resize(static_cast<std::size_t>(stream_size));
    stream.seekg(0, std::ios::beg);
    stream.read(
        reinterpret_cast<char*>(output->data()),
        static_cast<std::streamsize>(stream_size));
    return stream.good() ||
           (stream.eof() && stream.gcount() == static_cast<std::streamsize>(stream_size));
}

std::uint32_t page_count(const basist::ktx2_transcoder& transcoder) {
    const std::uint32_t layers = transcoder.get_layers();
    return layers == 0U ? 1U : layers;
}

bool transcode_page(
    basist::ktx2_transcoder* transcoder,
    std::uint32_t page,
    std::uint32_t expected_width,
    std::uint32_t expected_height,
    basist::transcoder_texture_format format,
    std::uint32_t decode_flags,
    const char* label,
    std::string* error) {
    basist::ktx2_image_level_info info{};
    if (!transcoder->get_image_level_info(info, 0, page, 0)) {
        *error = "cannot read page " + std::to_string(page) + " metadata";
        return false;
    }
    if (info.m_orig_width != expected_width || info.m_orig_height != expected_height) {
        *error = "page " + std::to_string(page) + " dimensions do not match the manifest";
        return false;
    }
    const std::uint64_t expected_blocks_x = (static_cast<std::uint64_t>(expected_width) + 3U) / 4U;
    const std::uint64_t expected_blocks_y = (static_cast<std::uint64_t>(expected_height) + 3U) / 4U;
    const std::uint64_t expected_blocks = expected_blocks_x * expected_blocks_y;
    if (expected_blocks == 0U || info.m_total_blocks != expected_blocks) {
        *error = "page " + std::to_string(page) + " has an invalid 4x4 block count";
        return false;
    }
    const std::uint32_t bytes_per_block = basist::basis_get_bytes_per_block_or_pixel(format);
    const std::uint64_t output_size = expected_blocks * bytes_per_block;
    if (bytes_per_block == 0U || output_size > std::numeric_limits<std::size_t>::max()) {
        *error = "page " + std::to_string(page) + " output size is invalid for " + label;
        return false;
    }
    std::vector<std::uint8_t> output(static_cast<std::size_t>(output_size));
    if (!transcoder->transcode_image_level(
            0,
            page,
            0,
            output.data(),
            info.m_total_blocks,
            format,
            decode_flags)) {
        *error = "page " + std::to_string(page) + " failed full transcode to " + label;
        return false;
    }
    return true;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc == 2 && std::string(argv[1]) == "--version") {
        std::cout << kVersion << '\n';
        return 0;
    }
    if (argc != 5) {
        return fail(
            "usage: basis_ktx2_validator <file.ktx2> <width> <height> <page-count>");
    }

    std::uint32_t expected_width = 0;
    std::uint32_t expected_height = 0;
    std::uint32_t expected_pages = 0;
    if (!parse_positive_u32(argv[2], &expected_width) ||
        !parse_positive_u32(argv[3], &expected_height) ||
        !parse_positive_u32(argv[4], &expected_pages) || expected_pages > kMaxPages) {
        return fail("expected dimensions/page-count are invalid");
    }

    std::vector<std::uint8_t> data;
    if (!read_file(argv[1], &data)) {
        return fail("cannot read KTX2 input or it exceeds the 256 MiB validator limit");
    }

    basist::basisu_transcoder_init();
    basist::ktx2_transcoder transcoder;
    if (!transcoder.init(data.data(), static_cast<std::uint32_t>(data.size()))) {
        return fail("official Basis Universal parser rejected the KTX2 file");
    }
    if (transcoder.get_basis_tex_format() != basist::basis_tex_format::cUASTC_LDR_4x4 ||
        transcoder.get_levels() != 1U || transcoder.get_faces() != 1U ||
        transcoder.get_width() != expected_width || transcoder.get_height() != expected_height ||
        page_count(transcoder) != expected_pages) {
        return fail("KTX2 is not the expected single-level UASTC LDR texture array");
    }
    if (!transcoder.get_has_alpha() || !transcoder.is_srgb() ||
        transcoder.get_dfd_color_model() != kUastcLdrColorModel ||
        transcoder.get_dfd_total_samples() != 1U ||
        transcoder.get_dfd_channel_id0() != kUastcRgbaChannel ||
        transcoder.get_dfd_flags() != kStraightAlphaFlags ||
        static_cast<std::uint32_t>(transcoder.get_dfd_color_primaries()) != kBt709Primaries) {
        return fail("KTX2 DFD must be sRGB UASTC RGBA with straight alpha and BT.709 primaries");
    }
    if (!transcoder.start_transcoding()) {
        return fail("official Basis Universal transcoder could not initialize the KTX2 payload");
    }

    for (std::uint32_t page = 0; page < expected_pages; ++page) {
        std::string error;
        if (!transcode_page(
                &transcoder,
                page,
                expected_width,
                expected_height,
                basist::transcoder_texture_format::cTFETC2_RGBA,
                0U,
                "ETC2 RGBA",
                &error) ||
            !transcode_page(
                &transcoder,
                page,
                expected_width,
                expected_height,
                basist::transcoder_texture_format::cTFETC1_RGB,
                0U,
                "ETC1 RGB",
                &error) ||
            !transcode_page(
                &transcoder,
                page,
                expected_width,
                expected_height,
                basist::transcoder_texture_format::cTFETC1_RGB,
                basist::cDecodeFlagsTranscodeAlphaDataToOpaqueFormats,
                "ETC1 alpha",
                &error)) {
            return fail(error);
        }
    }

    std::cout << "ok: " << expected_pages
              << " page(s) fully transcoded to ETC2 RGBA, ETC1 RGB, and ETC1 alpha\n";
    return 0;
}

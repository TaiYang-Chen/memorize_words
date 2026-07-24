#include <jni.h>

#include <atomic>
#include <cstdint>
#include <fstream>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "third_party/basisu/transcoder/basisu_transcoder.h"

namespace {

constexpr std::uint64_t kMaxKtx2FileBytes = 256ULL * 1024ULL * 1024ULL;
constexpr jint kTargetEtc1Rgb = 0;
constexpr jint kTargetEtc2Rgba = 1;

struct BasisKtx2Handle {
    explicit BasisKtx2Handle(std::vector<std::uint8_t> file_data)
        : data(std::move(file_data)) {}

    std::vector<std::uint8_t> data;
    basist::ktx2_transcoder transcoder;
    std::mutex transcoder_mutex;
};

std::once_flag g_transcoder_init_once;
std::mutex g_handles_mutex;
std::unordered_map<jlong, std::shared_ptr<BasisKtx2Handle>> g_handles;
std::atomic<jlong> g_next_handle{1};

void throw_java(JNIEnv* env, const char* class_name, const char* message) {
    if (env->ExceptionCheck()) {
        return;
    }
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
        env->DeleteLocalRef(exception_class);
    }
}

void throw_illegal_argument(JNIEnv* env, const char* message) {
    throw_java(env, "java/lang/IllegalArgumentException", message);
}

void throw_illegal_state(JNIEnv* env, const char* message) {
    throw_java(env, "java/lang/IllegalStateException", message);
}

void throw_out_of_memory(JNIEnv* env, const char* message) {
    throw_java(env, "java/lang/OutOfMemoryError", message);
}

std::shared_ptr<BasisKtx2Handle> find_handle(jlong handle_id) {
    if (handle_id <= 0) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    const auto iterator = g_handles.find(handle_id);
    return iterator == g_handles.end() ? nullptr : iterator->second;
}

jlong register_handle(std::shared_ptr<BasisKtx2Handle> handle) {
    jlong handle_id = g_next_handle.fetch_add(1, std::memory_order_relaxed);
    if (handle_id <= 0) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    const auto inserted = g_handles.emplace(handle_id, std::move(handle));
    return inserted.second ? handle_id : 0;
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

std::uint32_t page_count(const BasisKtx2Handle& handle) {
    const std::uint32_t layers = handle.transcoder.get_layers();
    return layers == 0 ? 1U : layers;
}

bool is_supported_runtime_texture(const basist::ktx2_transcoder& transcoder) {
    return transcoder.get_basis_tex_format() == basist::basis_tex_format::cUASTC_LDR_4x4 &&
           transcoder.get_levels() == 1U &&
           transcoder.get_faces() == 1U &&
           transcoder.get_width() > 0U &&
           transcoder.get_height() > 0U;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_chen_memorizewords_core_sprite_BasisKtx2Native_nativeCreate(
    JNIEnv* env,
    jclass,
    jstring path) {
    if (path == nullptr) {
        throw_illegal_argument(env, "KTX2 path must not be null");
        return 0;
    }

    const char* path_chars = env->GetStringUTFChars(path, nullptr);
    if (path_chars == nullptr) {
        return 0;
    }
    const std::string native_path(path_chars);
    env->ReleaseStringUTFChars(path, path_chars);

    try {
        std::vector<std::uint8_t> file_data;
        if (!read_file(native_path, &file_data)) {
            return 0;
        }

        std::call_once(g_transcoder_init_once, [] {
            basist::basisu_transcoder_init();
        });

        auto handle = std::make_shared<BasisKtx2Handle>(std::move(file_data));
        if (!handle->transcoder.init(
                handle->data.data(),
                static_cast<std::uint32_t>(handle->data.size())) ||
            !is_supported_runtime_texture(handle->transcoder) ||
            !handle->transcoder.start_transcoding()) {
            return 0;
        }

        return register_handle(std::move(handle));
    } catch (const std::bad_alloc&) {
        throw_out_of_memory(env, "Unable to allocate KTX2 transcoder state");
        return 0;
    } catch (...) {
        throw_illegal_state(env, "Unexpected KTX2 initialization failure");
        return 0;
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_chen_memorizewords_core_sprite_BasisKtx2Native_nativeGetInfo(
    JNIEnv* env,
    jclass,
    jlong handle_id) {
    const auto handle = find_handle(handle_id);
    if (handle == nullptr) {
        throw_illegal_state(env, "KTX2 transcoder handle is closed or invalid");
        return nullptr;
    }

    const std::uint32_t pages = page_count(*handle);
    if (handle->transcoder.get_width() > static_cast<std::uint32_t>(std::numeric_limits<jint>::max()) ||
        handle->transcoder.get_height() > static_cast<std::uint32_t>(std::numeric_limits<jint>::max()) ||
        pages > static_cast<std::uint32_t>(std::numeric_limits<jint>::max())) {
        throw_illegal_state(env, "KTX2 dimensions exceed the JNI integer range");
        return nullptr;
    }

    const jint values[] = {
        static_cast<jint>(handle->transcoder.get_width()),
        static_cast<jint>(handle->transcoder.get_height()),
        static_cast<jint>(pages),
        handle->transcoder.get_has_alpha() ? 1 : 0,
        handle->transcoder.is_srgb() ? 1 : 0,
        static_cast<jint>(handle->transcoder.get_dfd_color_model()),
        static_cast<jint>(handle->transcoder.get_dfd_total_samples()),
        static_cast<jint>(handle->transcoder.get_dfd_channel_id0()),
        static_cast<jint>(handle->transcoder.get_dfd_flags()),
        static_cast<jint>(handle->transcoder.get_dfd_color_primaries()),
    };
    jintArray result = env->NewIntArray(10);
    if (result != nullptr) {
        env->SetIntArrayRegion(result, 0, 10, values);
    }
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_chen_memorizewords_core_sprite_BasisKtx2Native_nativeTranscodePage(
    JNIEnv* env,
    jclass,
    jlong handle_id,
    jint page_index,
    jint target,
    jboolean decode_alpha) {
    const auto handle = find_handle(handle_id);
    if (handle == nullptr) {
        throw_illegal_state(env, "KTX2 transcoder handle is closed or invalid");
        return nullptr;
    }
    if (target != kTargetEtc1Rgb && target != kTargetEtc2Rgba) {
        throw_illegal_argument(env, "Unsupported KTX2 transcode target");
        return nullptr;
    }
    if (decode_alpha == JNI_TRUE && target != kTargetEtc1Rgb) {
        throw_illegal_argument(env, "Alpha-plane decoding requires the ETC1_RGB target");
        return nullptr;
    }

    const std::uint32_t pages = page_count(*handle);
    if (page_index < 0 || static_cast<std::uint32_t>(page_index) >= pages) {
        throw_illegal_argument(env, "KTX2 page index is out of range");
        return nullptr;
    }
    if (decode_alpha == JNI_TRUE && !handle->transcoder.get_has_alpha()) {
        throw_illegal_argument(env, "KTX2 texture does not contain an alpha channel");
        return nullptr;
    }

    const auto output_format = target == kTargetEtc1Rgb
        ? basist::transcoder_texture_format::cTFETC1_RGB
        : basist::transcoder_texture_format::cTFETC2_RGBA;

    try {
        std::lock_guard<std::mutex> lock(handle->transcoder_mutex);

        basist::ktx2_image_level_info level_info{};
        if (!handle->transcoder.get_image_level_info(
                level_info,
                0,
                static_cast<std::uint32_t>(page_index),
                0)) {
            throw_illegal_state(env, "Unable to read KTX2 page information");
            return nullptr;
        }

        const std::uint32_t bytes_per_block =
            basist::basis_get_bytes_per_block_or_pixel(output_format);
        const std::uint64_t output_size =
            static_cast<std::uint64_t>(level_info.m_total_blocks) * bytes_per_block;
        if (level_info.m_total_blocks == 0U || bytes_per_block == 0U ||
            output_size > static_cast<std::uint64_t>(std::numeric_limits<jsize>::max())) {
            throw_illegal_state(env, "KTX2 transcoded page size is invalid");
            return nullptr;
        }

        std::vector<std::uint8_t> output(static_cast<std::size_t>(output_size));
        const std::uint32_t decode_flags = decode_alpha == JNI_TRUE
            ? basist::cDecodeFlagsTranscodeAlphaDataToOpaqueFormats
            : 0U;
        if (!handle->transcoder.transcode_image_level(
                0,
                static_cast<std::uint32_t>(page_index),
                0,
                output.data(),
                level_info.m_total_blocks,
                output_format,
                decode_flags)) {
            throw_illegal_state(env, "Basis Universal failed to transcode the KTX2 page");
            return nullptr;
        }

        jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
        if (result == nullptr) {
            return nullptr;
        }
        env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(output.size()),
            reinterpret_cast<const jbyte*>(output.data()));
        return result;
    } catch (const std::bad_alloc&) {
        throw_out_of_memory(env, "Unable to allocate the transcoded KTX2 page");
        return nullptr;
    } catch (...) {
        throw_illegal_state(env, "Unexpected KTX2 transcode failure");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_chen_memorizewords_core_sprite_BasisKtx2Native_nativeDestroy(
    JNIEnv*,
    jclass,
    jlong handle_id) {
    if (handle_id <= 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    g_handles.erase(handle_id);
}


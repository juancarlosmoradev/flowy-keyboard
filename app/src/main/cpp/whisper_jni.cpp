#include <jni.h>
#include <algorithm>
#include <thread>
#include <string>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_dev_flowy_keyboard_WhisperNative_initialize(JNIEnv* env, jobject, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;

    whisper_context_params params = whisper_context_default_params();
    whisper_context* context = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_flowy_keyboard_WhisperNative_transcribe(
        JNIEnv* env, jobject, jlong context_ptr, jfloatArray audio_samples) {
    auto* context = reinterpret_cast<whisper_context*>(context_ptr);
    if (context == nullptr || audio_samples == nullptr) {
        return env->NewStringUTF("");
    }

    jfloat* samples = env->GetFloatArrayElements(audio_samples, nullptr);
    if (samples == nullptr) return env->NewStringUTF("");
    const jsize sample_count = env->GetArrayLength(audio_samples);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "es";
    params.translate = false;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    const unsigned int cores = std::max(1u, std::thread::hardware_concurrency());
    params.n_threads = static_cast<int>(std::min(4u, cores));

    std::string transcript;
    if (sample_count > 0 &&
        whisper_full(context, params, samples, static_cast<int>(sample_count)) == 0) {
        const int segments = whisper_full_n_segments(context);
        for (int i = 0; i < segments; ++i) {
            const char* segment = whisper_full_get_segment_text(context, i);
            if (segment != nullptr) transcript += segment;
        }
    }

    env->ReleaseFloatArrayElements(audio_samples, samples, JNI_ABORT);
    return env->NewStringUTF(transcript.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_flowy_keyboard_WhisperNative_release(JNIEnv*, jobject, jlong context_ptr) {
    auto* context = reinterpret_cast<whisper_context*>(context_ptr);
    if (context != nullptr) whisper_free(context);
}

cmake_minimum_required(VERSION 3.22.1)
project(FlowyWhisper LANGUAGES C CXX)

include(FetchContent)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG aba02defd6bb6ff5de63721c21e3456645c153e2
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(flowy_whisper SHARED whisper_jni.cpp)
target_include_directories(flowy_whisper PRIVATE ${whisper_cpp_SOURCE_DIR}/include)
find_library(log-lib log)
target_link_libraries(flowy_whisper PRIVATE whisper ${log-lib} android)
target_compile_options(flowy_whisper PRIVATE -O3)

cmake_minimum_required(VERSION 3.22.1)
project(easytier_ffi)

# Ensure Cargo is available
find_program(CARGO_EXECUTABLE cargo)
if(NOT CARGO_EXECUTABLE)
    message(FATAL_ERROR "cargo not found. Please install Rust and Cargo.")
endif()

# We need to map the Android ABI to Rust target triples
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(RUST_TARGET "armv7-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(RUST_TARGET "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "x86")
    set(RUST_TARGET "i686-linux-android")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(RUST_TARGET "x86_64-linux-android")
else()
    message(FATAL_ERROR "Unsupported Android ABI: ${ANDROID_ABI}")
endif()

# Set output directory for Cargo
set(RUST_BUILD_DIR ${CMAKE_CURRENT_BINARY_DIR}/rust_build)
set(RUST_TARGET_DIR ${RUST_BUILD_DIR}/${RUST_TARGET}/release)

# Add custom target to build the Rust project
add_custom_target(
    build_rust_ffi ALL
    COMMAND ${CMAKE_COMMAND} -E env
            "CARGO_TARGET_DIR=${RUST_BUILD_DIR}"
            cargo ndk -t ${ANDROID_ABI} -o ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} build --release
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/src/main/rust
    COMMENT "Building Rust library with cargo-ndk for ${ANDROID_ABI}"
)

# Declare the shared library, but we won't compile it with CMake
# We use IMPORTED to tell CMake it's built externally
add_library(easytier SHARED IMPORTED)
set_target_properties(easytier PROPERTIES IMPORTED_LOCATION ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libeasytier.so)
add_dependencies(easytier build_rust_ffi)

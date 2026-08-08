cmake_minimum_required(VERSION 3.22)
project(husk_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# husk_core is deliberately free of JNI and Android headers so it can be built and tested on the
# host in milliseconds. The JNI shim links against it and lives in a separate target.
add_library(husk_core STATIC
    core/linalg.cpp
    core/lfd.cpp
    core/bridge.cpp
)

target_include_directories(husk_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

if(MSVC)
    target_compile_options(husk_core PRIVATE /W4 /WX)
else()
    target_compile_options(husk_core PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()

# The JNI shim, built only for Android. On the host there is no VM to bind to, and husk_core is
# linked straight into the test binary instead.
if(ANDROID)
    add_library(husk_jni SHARED jni/husk_jni.cpp)
    target_link_libraries(husk_jni PRIVATE husk_core)
    target_compile_options(husk_jni PRIVATE -Wall -Wextra -Wpedantic -Werror)

    # Nothing outside the one JNI entry point needs to be reachable from the VM, and hiding the rest
    # lets the linker drop what the app never calls.
    target_link_options(husk_jni PRIVATE -Wl,--exclude-libs,ALL)
    set_target_properties(husk_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
endif()

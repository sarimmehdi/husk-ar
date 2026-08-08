cmake_minimum_required(VERSION 3.22)
project(husk_core_tests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp husk_core_build)

# GoogleTest ships inside the Android NDK, so the host tests need no network and no vendored copy.
# HUSK_GOOGLETEST_DIR overrides the probe when the NDK lives somewhere unusual.
if(NOT HUSK_GOOGLETEST_DIR)
    foreach(candidate $ENV{ANDROID_NDK_ROOT} $ENV{ANDROID_NDK_HOME})
        if(EXISTS ${candidate}/sources/third_party/googletest)
            set(HUSK_GOOGLETEST_DIR ${candidate}/sources/third_party/googletest)
            break()
        endif()
    endforeach()
endif()

if(NOT EXISTS ${HUSK_GOOGLETEST_DIR}/src/gtest-all.cc)
    message(FATAL_ERROR
        "GoogleTest not found. Set ANDROID_NDK_ROOT to an installed NDK, or pass "
        "-DHUSK_GOOGLETEST_DIR=<path to a googletest checkout>.")
endif()

# Built from the two amalgamated sources rather than through GoogleTest's own CMakeLists. The copy
# bundled with the NDK expects a parent project to define GOOGLETEST_VERSION, and without it the
# configure step fails on an empty set_target_properties argument. Compiling the sources directly
# needs only the include paths and sidesteps that entirely.
add_library(gtest STATIC ${HUSK_GOOGLETEST_DIR}/src/gtest-all.cc)
target_include_directories(gtest
    PUBLIC ${HUSK_GOOGLETEST_DIR}/include
    PRIVATE ${HUSK_GOOGLETEST_DIR})

add_library(gtest_main STATIC ${HUSK_GOOGLETEST_DIR}/src/gtest_main.cc)
target_include_directories(gtest_main PRIVATE ${HUSK_GOOGLETEST_DIR})
target_link_libraries(gtest_main PUBLIC gtest)

find_package(Threads REQUIRED)
target_link_libraries(gtest PUBLIC Threads::Threads)

enable_testing()
add_executable(husk_core_tests
    linalg_test.cpp
    lfd_test.cpp
    bridge_test.cpp
)
target_link_libraries(husk_core_tests PRIVATE husk_core gtest_main)
add_test(NAME husk_core_tests COMMAND husk_core_tests)

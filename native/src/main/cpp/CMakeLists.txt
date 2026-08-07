cmake_minimum_required(VERSION 3.22)
project(husk_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# husk_core is deliberately free of JNI and Android headers so it can be built and tested on the
# host in milliseconds. The JNI shim links against it and lives in a separate target.
add_library(husk_core STATIC
    core/linalg.cpp
    core/lfd.cpp

)

target_include_directories(husk_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

if(MSVC)
    target_compile_options(husk_core PRIVATE /W4 /WX)
else()
    target_compile_options(husk_core PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()


find_program(PROTOC protoc)
if(NOT PROTOC)
	message(FATAL_ERROR "Could not find protoc")
endif()

# The nanopb generator needs nanopb_pb2.py. A fresh submodule checkout does not contain
# it, and generating it requires protoc on PATH, so ship a prebuilt copy with the app.
set(NANOPB_PB2_BUNDLED "${CMAKE_CURRENT_SOURCE_DIR}/nanopb_pb2.py")
set(NANOPB_GENERATOR_PROTO_DIR "${CMAKE_SOURCE_DIR}/third-party/nanopb/generator/proto")
if(EXISTS "${NANOPB_PB2_BUNDLED}" AND NOT EXISTS "${NANOPB_GENERATOR_PROTO_DIR}/nanopb_pb2.py")
	file(COPY "${NANOPB_PB2_BUNDLED}" DESTINATION "${NANOPB_GENERATOR_PROTO_DIR}")
endif()

add_custom_command(OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/takion.pb"
		COMMAND "${PROTOC}" "-o${CMAKE_CURRENT_BINARY_DIR}/takion.pb" "${CMAKE_CURRENT_SOURCE_DIR}/takion.proto" "-I${CMAKE_CURRENT_SOURCE_DIR}"
		MAIN_DEPENDENCY "${CMAKE_CURRENT_SOURCE_DIR}/takion.proto")

set(SOURCE_FILES "${CMAKE_CURRENT_BINARY_DIR}/takion.pb.c")
set(HEADER_FILES "${CMAKE_CURRENT_BINARY_DIR}/takion.pb.h")

if(UNIX AND NOT CMAKE_HOST_WIN32 AND IS_ABSOLUTE "${PROTOC}")
	# make sure protoc is in PATH when invoking the generator below, which needs it.
	get_filename_component(PROTOC_PATH "${PROTOC}" DIRECTORY)
	set(GEN_PREFIX "${CMAKE_COMMAND}" -E env "PATH=${PROTOC_PATH}:$ENV{PATH}")
else()
	set(GEN_PREFIX "")
endif()

add_custom_command(OUTPUT ${SOURCE_FILES} ${HEADER_FILES}
		COMMAND ${GEN_PREFIX} "${PYTHON_EXECUTABLE}" "${NANOPB_GENERATOR_PY}" "${CMAKE_CURRENT_BINARY_DIR}/takion.pb"
		MAIN_DEPENDENCY "${CMAKE_CURRENT_BINARY_DIR}/takion.pb")

set(CHIAKI_LIB_PROTO_SOURCE_FILES "${SOURCE_FILES}" PARENT_SCOPE)
set(CHIAKI_LIB_PROTO_HEADER_FILES "${HEADER_FILES}" PARENT_SCOPE)
set(CHIAKI_LIB_PROTO_INCLUDE_DIR "${CMAKE_CURRENT_BINARY_DIR}" PARENT_SCOPE)

add_custom_target(chiaki-pb DEPENDS ${SOURCE_FILES} ${HEADER_FILES})

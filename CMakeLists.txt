cmake_minimum_required(VERSION 3.5)
project(daut)

find_package(ament_cmake REQUIRED)

add_custom_target(
  sbt_publish_local ALL
  COMMAND sbt --batch -Dsbt.server.forcestart=true publishLocal
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
  COMMENT "Build using SBT and publish to local machine"
)

ament_package()

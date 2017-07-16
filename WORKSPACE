workspace(name = "io_bazel_rules_scala")



load("//scala:scala.bzl", "scala_repositories", "scala_mvn_artifact")
scala_repositories()

load("//twitter_scrooge:twitter_scrooge.bzl", "twitter_scrooge", "scrooge_scala_library")
twitter_scrooge()

load("//tut_rule:tut.bzl", "tut_repositories")
tut_repositories()

load("//jmh:jmh.bzl", "jmh_repositories")
jmh_repositories()

load("//specs2:specs2_junit.bzl","specs2_junit_repositories")
specs2_junit_repositories()

# test adding a scala jar:
maven_jar(
  name = "com_twitter__scalding_date",
  artifact = scala_mvn_artifact("com.twitter:scalding-date:0.16.0-RC4"),
  sha1 = "659eb2d42945dea37b310d96af4e12bf83f54d14"
)

# test of a plugin
maven_jar(
  name = "org_psywerx_hairyfotr__linter",
  artifact = scala_mvn_artifact("org.psywerx.hairyfotr:linter:0.1.13"),
  sha1 = "e5b3e2753d0817b622c32aedcb888bcf39e275b4")

# test of strict deps (scalac plugin UT + E2E)
maven_jar(
    name = "com_google_guava_guava_21_0",
    artifact = "com.google.guava:guava:21.0",
    sha1 = "3a3d111be1be1b745edfa7d91678a12d7ed38709"
)

maven_jar(
    name = "org_apache_commons_commons_lang_3_5",
    artifact = "org.apache.commons:commons-lang3:3.5",
    sha1 = "6c6c702c89bfff3cd9e80b04d668c5e190d588c6"
)
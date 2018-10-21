# TODO: this should really be a bazel provider, but we are using old-style rule outputs
# we need to document better what the intellij dependencies on this code actually are
def create_scala_provider(ijar, class_jar, compile_jars,
                          transitive_runtime_jars, deploy_jar, full_jars, source_jar,
                          statsfile):

  formatted_for_intellij = [
      struct(class_jar = jar, ijar = None, source_jar = None, source_jars = [source_jar])
      for jar in full_jars
  ]

  rule_outputs = struct(
      ijar = ijar,
      class_jar = class_jar,
      deploy_jar = deploy_jar,
      jars = formatted_for_intellij,
      statsfile = statsfile,
  )
  # Note that, internally, rules only care about compile_jars and transitive_runtime_jars
  # in a similar manner as the java_library and JavaProvider
  return struct(
      outputs = rule_outputs,
      compile_jars = compile_jars,
      transitive_runtime_jars = transitive_runtime_jars,
      transitive_exports = []  #needed by intellij plugin
  )

ScalacProvider = provider(
    doc = "ScalacProvider",
    fields = [
        "default_classpath",
        "default_macro_classpath",
        "default_repl_classpath",
    ])

def _declare_scalac_provider(ctx):
  return [
      ScalacProvider(
          default_classpath = ctx.attr.default_classpath,
          default_repl_classpath = ctx.attr.default_repl_classpath,
          default_macro_classpath = ctx.attr.default_macro_classpath,
      )
  ]

declare_scalac_provider = rule(
    implementation = _declare_scalac_provider,
    attrs = {
        "default_classpath": attr.label_list(allow_files = True),
        "default_repl_classpath": attr.label_list(allow_files = True),
        "default_macro_classpath": attr.label_list(allow_files = True),
    })

bin/gen-schema.sh
lein do clean, uberjar
# See https://github.com/heroku/heroku-jvm-application-deployer/releases
# -b is per-build, not persisted on the app -- must be passed every deploy, apt first so
# `dot` (graphviz) is on PATH before the jvm buildpack runs. -i ships our Aptfile since
# the deployer only auto-includes Procfile/system.properties/.jdk-overlay/project.toml.
# NB: the jar (positional "file" arg) must come before -b/-i -- those have variadic
# arity and will swallow a trailing bare path, so the tool never sees a main file and
# ships an empty Procfile (silently dropping the web process type -- happened once).
HEROKU_API_KEY=$(heroku auth:token) java -jar bin/heroku-jvm-application-deployer-4.0.12.jar \
  --app=ontogeny \
  target/uberjar/ontogeny-standalone.jar \
  -b heroku-community/apt -b heroku/jvm \
  -i Aptfile


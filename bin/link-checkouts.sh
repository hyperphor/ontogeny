#!/bin/sh
# Sets up Leiningen `checkouts/` symlinks for way/ellum/alzabo, developed
# alongside ontogeny, so editing their source is picked up live (no `lein
# install` round trip per edit). See eli's identical script/rationale.
#
# Assumes the sibling repos live at ../../hyperphor/<name> relative to this
# repo -- adjust if your checkout layout differs.

set -e
cd "$(dirname "$0")/.."
mkdir -p checkouts
ln -sf /opt/mt/repos/hyperphor/way checkouts/way
ln -sf /opt/mt/repos/hyperphor/ellum checkouts/ellum
ln -sf /opt/mt/repos/hyperphor/alzabo checkouts/alzabo
echo "Linked checkouts/{way,ellum,alzabo}"
echo "(Run 'lein install' in each at least once so its own transitive deps"
echo " resolve normally; checkouts only live-overrides its own source.)"

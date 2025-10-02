#!/usr/bin/env bash

# TODO
# takes falgs
# * --push=true|false (required)
# * --branch=branch-name (defaults to main)
# * --bump=MAJOR|MINOR|PATCH (required)
# cd $REPO_ROOT
# checkout latest $BRANCH
# ASSERT
# - git state is clean
# look in ./src/main/resources/braintrust.properties for sdk.version prop
# CURRENT_SNAPSHOT = (whatever is in the props file)
# CURRENT_RELEASE_VERSION = ($CURRENT_SNAPSHOT without the -SNAPSHOT)
# NEW_SNAPSHOT = (bump semver depending on $BUMP)
# NEW_SNAPSHOT_RELEASE_VERSION = ($NEW_SNAPSHOT without the -SNAPSHOT)
#
# write $CURRENT_RELEASE_VERSION over the existing value in braintrust.properties
# git commit -m "release $CURRENT_RELEASE_VERSION"
# tag "v$CURRENT_RELEASE_VERSION"
# write $NEW_SNAPSHOT over the existing value in braintrust.properties
# git commit -m "begin $NEW_SNAPSHOT"
# if $PUSH
#   push commits and tag (or fail if we're out of date)

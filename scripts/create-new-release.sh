#!/usr/bin/env bash

set -euo pipefail

# Default values
BRANCH="main"
PUSH=""
SEMVER_SEGMENT_TO_BUMP=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --push=*)
            PUSH="${1#*=}"
            shift
            ;;
        --branch=*)
            BRANCH="${1#*=}"
            shift
            ;;
        --semver-segment-to-bump=*)
            SEMVER_SEGMENT_TO_BUMP="${1#*=}"
            shift
            ;;
        *)
            echo "Unknown option $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$PUSH" ]]; then
    echo "Error: --push=true|false is required"
    exit 1
fi

if [[ -z "$SEMVER_SEGMENT_TO_BUMP" ]]; then
    echo "Error: --semver-segment=MAJOR|MINOR|PATCH is required"
    exit 1
fi

if [[ "$PUSH" != "true" && "$PUSH" != "false" ]]; then
    echo "Error: --push must be 'true' or 'false'"
    exit 1
fi

if [[ "$SEMVER_SEGMENT_TO_BUMP" != "MAJOR" && "$SEMVER_SEGMENT_TO_BUMP" != "MINOR" && "$SEMVER_SEGMENT_TO_BUMP" != "PATCH" ]]; then
    echo "Error: --semver-segment must be 'MAJOR', 'MINOR', or 'PATCH'"
    exit 1
fi

# Find repository root
REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

# Checkout and update the specified branch
echo "Checking out branch: $BRANCH"
git checkout "$BRANCH"
git pull origin "$BRANCH"

# Assert git state is clean
if [[ -n $(git status --porcelain) ]]; then
    echo "Error: Git working directory is not clean"
    git status
    exit 1
fi

# Read current version from braintrust.properties
PROPERTIES_FILE="./src/main/resources/braintrust.properties"
if [[ ! -f "$PROPERTIES_FILE" ]]; then
    echo "Error: $PROPERTIES_FILE not found"
    exit 1
fi

CURRENT_SNAPSHOT=$(grep "^sdk.version=" "$PROPERTIES_FILE" | cut -d'=' -f2)
if [[ -z "$CURRENT_SNAPSHOT" ]]; then
    echo "Error: sdk.version not found in $PROPERTIES_FILE"
    exit 1
fi

echo "Current snapshot version: $CURRENT_SNAPSHOT"

# Validate current version ends with -SNAPSHOT
if [[ ! "$CURRENT_SNAPSHOT" =~ -SNAPSHOT$ ]]; then
    echo "Error: Current version '$CURRENT_SNAPSHOT' does not end with '-SNAPSHOT'"
    exit 1
fi

# Remove -SNAPSHOT to get current release version
CURRENT_RELEASE_VERSION="${CURRENT_SNAPSHOT%-SNAPSHOT}"
echo "Current release version: $CURRENT_RELEASE_VERSION"

# Parse version components
if [[ ! "$CURRENT_RELEASE_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Error: Version '$CURRENT_RELEASE_VERSION' is not in semver format (x.y.z)"
    exit 1
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

# Calculate new version based on semver segment to bump
case "$SEMVER_SEGMENT_TO_BUMP" in
    "MAJOR")
        NEW_MAJOR=$((MAJOR + 1))
        NEW_MINOR=0
        NEW_PATCH=0
        ;;
    "MINOR")
        NEW_MAJOR=$MAJOR
        NEW_MINOR=$((MINOR + 1))
        NEW_PATCH=0
        ;;
    "PATCH")
        NEW_MAJOR=$MAJOR
        NEW_MINOR=$MINOR
        NEW_PATCH=$((PATCH + 1))
        ;;
esac

case "$SEMVER_SEGMENT_TO_BUMP" in
    # For MAJOR/MINOR bumps, release the .0 version and prepare the next patch snapshot
    "MAJOR"|"MINOR")
        # Release version is the bumped version with patch 0
        CURRENT_RELEASE_VERSION="${NEW_MAJOR}.${NEW_MINOR}.0"
        # Next snapshot is patch 1
        NEW_SNAPSHOT="${NEW_MAJOR}.${NEW_MINOR}.1-SNAPSHOT"
        ;;
    "PATCH")
        # For patch bumps, use the original logic
        NEW_SNAPSHOT="${NEW_MAJOR}.${NEW_MINOR}.${NEW_PATCH}-SNAPSHOT"
        ;;
esac

echo "New snapshot version: $NEW_SNAPSHOT"

# Update properties file with current release version
echo "Creating release commit for version: $CURRENT_RELEASE_VERSION"
sed -i "s/^sdk.version=.*/sdk.version=$CURRENT_RELEASE_VERSION/" "$PROPERTIES_FILE"

# Commit release version
git add "$PROPERTIES_FILE"
git commit -m "release $CURRENT_RELEASE_VERSION"

# Create tag
git tag "v$CURRENT_RELEASE_VERSION"
echo "Created tag: v$CURRENT_RELEASE_VERSION"

# Update properties file with new snapshot version
echo "Creating snapshot commit for version: $NEW_SNAPSHOT"
sed -i "s/^sdk.version=.*/sdk.version=$NEW_SNAPSHOT/" "$PROPERTIES_FILE"

# Commit new snapshot version
git add "$PROPERTIES_FILE"
git commit -m "begin $NEW_SNAPSHOT"

git log --oneline -n 3 # print out what we just did
# Push if requested
if [[ "$PUSH" == "true" ]]; then
    echo "Pushing commits and tags to origin..."
    git push origin "$BRANCH"
    git push origin "v$CURRENT_RELEASE_VERSION"
    echo "Successfully pushed release $CURRENT_RELEASE_VERSION and new snapshot $NEW_SNAPSHOT"
else
    echo "Skipping push (--push=false)"
    echo "To push manually, run:"
    echo "  git push origin $BRANCH"
    echo "  git push origin v$CURRENT_RELEASE_VERSION"
fi

echo "Release process completed successfully!"

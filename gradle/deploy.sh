#!/bin/bash

SLUG="vrendina/RxQueue"
JDK="oraclejdk8"
BRANCH="master"

set -e

echo "Checking if we should deploy ${TRAVIS_REPO_SLUG} on branch ${TRAVIS_BRANCH}..."

if [ "$TRAVIS_REPO_SLUG" != "$SLUG" ]; then
  echo "Skipping deployment: wrong repository. Expected '$SLUG' but was '$TRAVIS_REPO_SLUG'."
elif [ "$TRAVIS_JDK_VERSION" != "$JDK" ]; then
  echo "Skipping deployment: wrong JDK. Expected '$JDK' but was '$TRAVIS_JDK_VERSION'."
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping deployment: was pull request."
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
  echo "Skipping deployment: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'."
else
  # If we are on the correct branch and have a tag that starts with 'v' we need to deploy a release and update the javadoc
  if [[ "$TRAVIS_TAG" == v* ]]; then
    echo "Deploying release for version ${TRAVIS_TAG}..."
    ./gradlew bintrayUpload -x test -Dsnapshot=false -Dbintray.user=${BINTRAY_USER} -Dbintray.key=${BINTRAY_KEY} -Dbuild.number=${TRAVIS_BUILD_NUMBER}
  else
    echo "Deploying snapshot for build ${TRAVIS_BUILD_NUMBER}..."
    ./gradlew artifactoryPublish -x test -Dsnapshot=true -Dbintray.user=${BINTRAY_USER} -Dbintray.key=${BINTRAY_KEY} -Dbuild.number=${TRAVIS_BUILD_NUMBER}
  fi
fi
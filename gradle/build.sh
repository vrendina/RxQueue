#!/bin/bash

SLUG="vrendina/RxQueue"
JDK="oraclejdk8"
BRANCH="master"

set -e

# Always run a clean build regardless of branch or repo
echo "Clean build for ${TRAVIS_REPO_SLUG} on branch ${TRAVIS_BRANCH}..."
./gradlew clean build

echo "Checking if we should deploy ${TRAVIS_REPO_SLUG} on branch ${TRAVIS_BRANCH}..."

if [ "$TRAVIS_REPO_SLUG" != "$SLUG" ]; then
  echo "Skipping deployment: wrong repository. Expected '$SLUG' but was '$TRAVIS_REPO_SLUG'."
elif [ "$TRAVIS_JDK_VERSION" != "$JDK" ]; then
  echo "Skipping deployment: wrong JDK. Expected '$JDK' but was '$TRAVIS_JDK_VERSION'."
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping deployment: was pull request."
else
  # If we have a tag that starts with 'v' we need to deploy a release and update the javadoc
  if [[ "$TRAVIS_TAG" == v* ]]; then
    echo "Deploying release for version ${TRAVIS_TAG}..."
    ./gradlew bintrayUpload -x test -Dsnapshot=false -Dbintray.user=${BINTRAY_USER} -Dbintray.key=${BINTRAY_KEY} -Dbuild.number=${TRAVIS_BUILD_NUMBER}

    echo "Deploying javadoc for release ${TRAVIS_TAG}..."
    git config --global user.email "travis@travis-ci.org"
    git config --global user.name "travis-ci"
    git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/vrendina/RxQueue.git gh-pages
    cd gh-pages
    rm -rf *
    cp -Rf ${TRAVIS_BUILD_DIR}/build/docs/javadoc/* ./
    git add .
    git commit -m "Latest javadoc for release ${TRAVIS_TAG} (build ${TRAVIS_BUILD_NUMBER})"
    git push origin gh-pages
  # If we are on the correct branch but don't have a release tag deploy the snapshot
  elif [ "$TRAVIS_BRANCH" == "$BRANCH" ]; then
    echo "Deploying snapshot for build ${TRAVIS_BUILD_NUMBER}..."
#    ./gradlew artifactoryPublish -x test -Dsnapshot=true -Dbintray.user=${BINTRAY_USER} -Dbintray.key=${BINTRAY_KEY} -Dbuild.number=${TRAVIS_BUILD_NUMBER}
  else
    echo "Skipping deployment: ${TRAVIS_BRANCH} was not ${BRANCH} and tag was '${TRAVIS_TAG}'"
  fi
fi

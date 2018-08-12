#!/bin/bash

export GRADLE_OPTS=-Xmx1024m

echo 'Branch ['${TRAVIS_BRANCH}']  Tag ['${TRAVIS_TAG}']  Pull Request ['${TRAVIS_PULL_REQUEST}'] Slug ['${TRAVIS_REPO_SLUG}']'

./gradlew clean build
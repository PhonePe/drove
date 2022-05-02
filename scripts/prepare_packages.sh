#! /bin/bash

#pushd ..
#mvn clean package -DskipTests
#popd

mkdir packages
pushd packages
cp ../../drove-controller/target/drove-controller-1.0-SNAPSHOT.deb .
cp ../../drove-executor/target/drove-executor-1.0-SNAPSHOT.deb .

popd
zip -r packages.zip packages
rm -rf packages

#!/bin/bash


rm -r _output

# PcscOverBle

git clone 
rm -r _output/PcscOverBle/PcscOverBleApp/src
cp -r projects/PcscOverBleApp/src _output/PcscOverBle/PcscOverBleApp
cp projects/PcscOverBleApp/build.gradle _output/PcscOverBle/PcscOverBleApp/build.gradle

rm -r _output/PcscOverBle/PcscApp/src
cp -r projects/PcscApp/src _output/PcscOverBle/PcscApp
cp projects/PcscApp/build.gradle _output/PcscOverBle/PcscApp/build.gradle

rm _output/PcscOverBle/libs/*.aar
cp -r projects/PcscLib/build/outputs/*.aar _output/PcscOverBle/libs/

echo "" > _output/PcscOverBle/PcscLib/build.gradle
echo "configurations.maybeCreate(\"default\")" >> _output/PcscOverBle/PcscLib/build.gradle
echo "artifacts.add(\"default\", file(\'SpringCardPcscAndroidLibrary_0.51-28-gddce810f-dirty_2019_02_19_release.aar\'))">> _output/PcscOverBle/PcscLib/build.gradle

# TODO change aar name in

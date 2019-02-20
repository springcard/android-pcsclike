#!/bin/bash


# PcscOverBle

#git clone 

cp projects/build.gradle _output/PcscOverBle/build.gradle

rm -r _output/PcscOverBle/PcscOverBleApp/src
cp -r projects/PcscOverBleApp/src _output/PcscOverBle/PcscOverBleApp
cp projects/PcscOverBleApp/build.gradle _output/PcscOverBle/PcscOverBleApp/build.gradle

rm -r _output/PcscOverBle/PcscApp/src
cp -r projects/PcscApp/src _output/PcscOverBle/PcscApp
cp projects/PcscApp/build.gradle _output/PcscOverBle/PcscApp/build.gradle

mkdir -p _output/PcscOverBle/libs/
rm _output/PcscOverBle/libs/*.aar
cp -r projects/PcscLib/build/outputs/aar/*.aar _output/PcscOverBle/libs/

GRADE_LIB_FILENAME='_output/PcscOverBle/PcscLib/build.gradle'
AAR_FILENAME=$(ls projects/PcscLib/build/outputs/aar/ | grep release | tail -1)

echo "" > $GRADE_LIB_FILENAME
echo "configurations.maybeCreate(\"default\")" >> $GRADE_LIB_FILENAME
echo "artifacts.add(\"default\", file('$AAR_FILENAME'))">> $GRADE_LIB_FILENAME

# TODO change aar name in

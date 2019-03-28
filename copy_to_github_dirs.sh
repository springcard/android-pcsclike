#!/bin/bash


#git clone https://github.com/springcard/$REPO.git

    TAG=$(git describe --abbrev=0)
    REV=$(git describe --tags)

copyAppFilesTogithub() 
{ 
    REPO=$1
    APP=$2

    
    cp projects/build.gradle _output/$REPO/build.gradle

    rm -r _output/$REPO/$APP/src
    cp -r projects/$APP/src _output/$REPO/$APP
    cp projects/$APP/build.gradle _output/$REPO/$APP/build.gradle

    rm -r _output/$REPO/PcscLikeSample/src
    cp -r projects/PcscLikeSample/src _output/$REPO/PcscLikeSample
    cp projects/PcscLikeSample/build.gradle _output/$REPO/PcscLikeSample/build.gradle

    mkdir -p _output/$REPO/libs/
    rm _output/$REPO/libs/*.aar
    cp -r projects/PcscLike/build/outputs/aar/*.aar _output/$REPO/libs/

    #GRADE_LIB_FILENAME='_output/PcscOverBle/PcscLike/build.gradle'
    #AAR_FILENAME=$(ls projects/PcscLike/build/outputs/aar/ | grep release | tail -1)

    cp -r LICENSE.txt _output/$REPO/
    cp -r projects/$APP/README.md _output/$REPO/

    #TAG=$(git describe --abbrev=0)
    #REV=$(git describe --tags)

    #cd  _output/$REPO/
    #git tag -a "$TAG" -m "$REV"
    #cd  ../..

    #echo "" > $GRADE_LIB_FILENAME
    #echo "configurations.maybeCreate(\"default\")" >> $GRADE_LIB_FILENAME
    #echo "artifacts.add(\"default\", file('$AAR_FILENAME'))">> $GRADE_LIB_FILENAME

    # TODO change aar name in

}


copyLibFilesTogithub() 
{ 
    REPO=$1
    APP=$2
    
    cp projects/build.gradle _output/$REPO/build.gradle

    rm -r _output/$REPO/$APP/src
    cp -r projects/$APP/src _output/$REPO/$APP
    cp projects/$APP/build.gradle _output/$REPO/$APP/build.gradle

    
    cp -r LICENSE.txt _output/$REPO/
    cp -r projects/$APP/README.md _output/$REPO/

    #cd  _output/$REPO/
    #git tag -a "$TAG" -m "$REV"
    #cd  ../..
}

# appel de ma fonction

copyAppFilesTogithub 'android-pcsclike-sample-ble' 'PcscLikeSampleBle'
#copyAppFilesTogithub 'android-pcsclike-sample-usb' 'PcscLikeSampleUsb'

#copyLibFilesTogithub 'android-pcsclike' 'PcscLike'


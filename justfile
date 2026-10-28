build:
    ./gradlew assembleRelease

clean:
    ./gradlew clean
    rm -rf .gradle .kotlin

install:
    ./gradlew installRelease

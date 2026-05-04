# Makefile for HUR Source Switcher

# Use gradlew if available, otherwise fallback to gradle
GRADLE = gradle
ifneq ("$(wildcard ./gradlew)","")
    GRADLE = ./gradlew
endif

APP_NAME = hur-source-switcher
PACKAGE = com.hur.sourceswitcher
DIST_DIR = dist
APK_OUT = build/outputs/apk/debug/hur-source-switcher-debug.apk
APK_DIST = $(DIST_DIR)/$(APP_NAME).apk

.PHONY: all build dist clean install run logcat help

all: help

help:
	@echo "HUR Source Switcher Build Commands:"
	@echo "  make build      - Build debug APK using Gradle"
	@echo "  make dist       - Build and copy APK to $(DIST_DIR)/ folder"
	@echo "  make clean      - Clean build and $(DIST_DIR) directories"
	@echo "  make install    - Install APK to connected device via ADB"
	@echo "  make run        - Launch MainActivity on device"
	@echo "  make logcat     - Follow application logs"

build:
	$(GRADLE) assembleDebug

dist: build
	@mkdir -p $(DIST_DIR)
	@cp $(APK_OUT) $(APK_DIST)
	@echo "--------------------------------------------"
	@echo "Done! APK copied to: $(APK_DIST)"

clean:
	$(GRADLE) clean
	@rm -rf $(DIST_DIR)

install:
	adb install -r $(APK_OUT)

run:
	adb shell am start -n $(PACKAGE)/.MainActivity

logcat:
	adb logcat -s HURSourceSwitcher:*

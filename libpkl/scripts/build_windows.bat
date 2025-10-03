::===----------------------------------------------------------------------===//
:: Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
::
:: Licensed under the Apache License, Version 2.0 (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
::
::     https://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.
::===----------------------------------------------------------------------===//
@echo off

REM This script intercepts compiler arguments from graalvm native-image to build libpkl.
REM
REM Use with --native-compiler-path=%pathToThisScript%

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set SRC_DIR=%SCRIPT_DIR%..\src\main\c
set TEMP_OUTPUT_PATH=%PKL_TEMP_OUTPUT_PATH%
set OUTPUT_PATH=%PKL_OUTPUT_PATH%
set OBJECTS_DIR=%TEMP_OUTPUT_PATH%\objects
set PKL_OBJECT_FILE=%OBJECTS_DIR%\pkl.obj

REM Clean and create objects directory
if exist "%OBJECTS_DIR%" rmdir /s /q "%OBJECTS_DIR%"
mkdir "%OBJECTS_DIR%"

REM Determine the project name based on the output .dll argument
set LIB_NAME=
for %%a in (%*) do (
    echo %%a | findstr /C:".dll" >nul
    if !errorlevel! equ 0 (
        for %%f in (%%a) do set LIB_NAME=%%~nf
        goto :found_lib
    )
)
:found_lib

REM Do a simple forward for any calls that are used to compile individual C files
if "%LIB_NAME%"=="" (
    cmd /c cl %*
    exit /b !errorlevel!
)

REM Create a debug log in %output%\logs
set LOG_PATH=%TEMP_OUTPUT_PATH%\logs
set LOG_FILE=%LOG_PATH%\compiler_commands.txt

if exist "%LOG_PATH%" rmdir /s /q "%LOG_PATH%"
mkdir "%LOG_PATH%"

set CC_ARGS=%*

echo Working directory: %CD% > "%LOG_FILE%"
echo Output path: %TEMP_OUTPUT_PATH% >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"
echo ===== Original args ===== >> "%LOG_FILE%"
echo %* >> "%LOG_FILE%"

echo. >> "%LOG_FILE%"
echo ===== Build pkl.obj ===== >> "%LOG_FILE%"

REM Compile pkl.c to pkl.obj
set PKL_CC_ARGS=/c "%SRC_DIR%\pkl.c" /I"%TEMP_OUTPUT_PATH%" /I"%SRC_DIR%" /DPKL_VERSION=\"%PKL_VERSION%\" /Fo"%PKL_OBJECT_FILE%"

echo cl %PKL_CC_ARGS% >> "%LOG_FILE%"
cl %PKL_CC_ARGS%
if !errorlevel! neq 0 exit /b !errorlevel!

echo. >> "%LOG_FILE%"
echo ===== Extract objects ===== >> "%LOG_FILE%"

set LIB_ARGS=/OUT:"%OUTPUT_PATH%\libpkl.lib" "%OBJECTS_DIR%\*.obj"

REM Extract objects from .lib archives
for %%a in (%CC_ARGS%) do (
    echo %%a | findstr /C:".lib" >nul
    if !errorlevel! equ 0 (
        echo %%a >> "%LOG_FILE%"
        for %%f in (%%a) do set ARCHIVE_NAME=%%~nf
        set ARCHIVE_DIR=%OBJECTS_DIR%\!ARCHIVE_NAME!
        mkdir "!ARCHIVE_DIR!"
        copy "%%a" "!ARCHIVE_DIR!" >nul
        pushd "!ARCHIVE_DIR!"
        lib /NOLOGO /LIST "%%~nxa" > objlist.txt
        for /f "delims=" %%o in (objlist.txt) do (
            lib /NOLOGO /EXTRACT:"%%o" "%%~nxa"
        )
        popd
        set LIB_ARGS=!LIB_ARGS! "!ARCHIVE_DIR!\*.obj"
    )
    echo %%a | findstr /C:".obj" >nul
    if !errorlevel! equ 0 (
        copy "%%a" "%OBJECTS_DIR%" >nul
    )
)

echo. >> "%LOG_FILE%"
echo ===== Objects ===== >> "%LOG_FILE%"
dir /s /b "%OBJECTS_DIR%\*.obj" >> "%LOG_FILE%"

echo. >> "%LOG_FILE%"
echo ===== Build shared library ===== >> "%LOG_FILE%"

REM Build the DLL
set SHARED_CC_ARGS=
for %%a in (%CC_ARGS%) do (
    set ARG=%%a
    echo !ARG! | findstr /C:".dll" >nul
    if !errorlevel! equ 0 (
        REM Replace output dll with our own, placed into our own output dir
        set SHARED_CC_ARGS=!SHARED_CC_ARGS! "%OUTPUT_PATH%\libpkl.dll"
    ) else (
        echo !ARG! | findstr /C:"%LIB_NAME%.obj" >nul
        if !errorlevel! equ 0 (
            REM Insert pkl.obj as an object file
            set SHARED_CC_ARGS=!SHARED_CC_ARGS! "%PKL_OBJECT_FILE%" "%TEMP_OUTPUT_PATH%\objects\%LIB_NAME%.obj"
        ) else (
            echo !ARG! | findstr /C:"exported_symbols.list" >nul
            if !errorlevel! equ 0 (
                REM Add symbols from pkl.obj to exported_symbols.list
                for /f "tokens=2* delims=," %%x in ("!ARG!") do set SYMBOL_LIST_FILE=%%y
                echo Adding symbols to !SYMBOL_LIST_FILE! >> "%LOG_FILE%"
                REM Extract public symbols from pkl.obj
                dumpbin /SYMBOLS "%PKL_OBJECT_FILE%" | findstr /C:"External" | findstr /C:"SECT" >> "!SYMBOL_LIST_FILE!"
                set SHARED_CC_ARGS=!SHARED_CC_ARGS! !ARG!
            ) else (
                set SHARED_CC_ARGS=!SHARED_CC_ARGS! !ARG!
            )
        )
    )
)

echo cl !SHARED_CC_ARGS! >> "%LOG_FILE%"
cl !SHARED_CC_ARGS!
if !errorlevel! neq 0 exit /b !errorlevel!

REM Build static library using lib.exe
REM On Windows we need to call 'lib' on all .obj files.
REM In order to also include all static library dependencies, we first extract the
REM .obj files and then include them as well.

echo. >> "%LOG_FILE%"
echo ===== Build static library ===== >> "%LOG_FILE%"

echo lib %LIB_ARGS% >> "%LOG_FILE%"
lib /NOLOGO %LIB_ARGS%
if !errorlevel! neq 0 exit /b !errorlevel!

echo. >> "%LOG_FILE%"
echo ===== Copy header file ===== >> "%LOG_FILE%"
set CP_ARGS="%SRC_DIR%\pkl.h" "%OUTPUT_PATH%\pkl.h"
echo copy %CP_ARGS% >> "%LOG_FILE%"
copy %CP_ARGS% >nul
if !errorlevel! neq 0 exit /b !errorlevel!

endlocal

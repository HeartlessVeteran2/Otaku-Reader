# Discord Rich Presence Implementation Plan

## 1. Dependencies
- Add Kizzy library to gradle/libs.versions.toml and app/build.gradle.kts

## 2. Core Module (core/discord)
Create a new core module for Discord integration:
- DiscordRpcService - Manages Discord connection and presence updates
- DiscordRpcManager - Handles the business logic and state

## 3. Preferences
- Add `discord_rpc_enabled` preference key to AppPreferences
- Add toggle in Settings screen

## 4. Reader Integration
- Update ReaderScreen/ViewModel to report reading state to Discord service
- Track elapsed reading time per chapter

## 5. Application Integration
- Initialize Discord service in OtakuReaderApplication
- Connect/disconnect based on preference

## Files to Modify:
1. gradle/libs.versions.toml - Add Kizzy dependency
2. app/build.gradle.kts - Include new module and dependency
3. core/preferences/AppPreferences.kt - Add discordRpcEnabled preference
4. feature/settings/SettingsMvi.kt - Add Discord event/state
5. feature/settings/SettingsViewModel.kt - Handle Discord toggle
6. feature/settings/SettingsScreen.kt - Add Discord toggle UI
7. feature/reader/ReaderScreen.kt - Report reading state
8. app/src/main/OtakuReaderApplication.kt - Initialize service

## New Files:
1. core/discord/build.gradle.kts
2. core/discord/src/main/DiscordRpcService.kt
3. core/discord/src/main/di/DiscordModule.kt

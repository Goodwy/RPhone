# Offline Caller ID (CNAP) — Codebase Map & Copyright Notice

> **Project:** Rill Phone (`dev.goodwy.rphone`)  
> **License:** GNU General Public License v3.0 (GPL-3.0)  
> **Copyright © 2024–2026 Goodwy**  
> All rights reserved under the terms of the GNU General Public License v3.0.

---

## 1. Copyright Notice Header

Add this header to any source file that is part of the Offline Caller ID (CNAP) subsystem:

```kotlin
/*
 * Offline Caller ID (CNAP) Subsystem
 *
 * Copyright (C) 2024–2026 Goodwy
 *
 * This file is part of Rill Phone.
 *
 * Rill Phone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rill Phone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rill Phone. If not, see <https://www.gnu.org/licenses/>.
 */
```

---

## 2. Overview of CNAP in Rill Phone

**CNAP (Calling Name Presentation)** is a carrier-network telephony service (standardized under **ITU-T Q.731.3** and **ETSI EN 300 196**). When a carrier supports CNAP, it transmits the registered caller's name alongside incoming call signaling over cellular networks (SIP / SS7 / VoLTE / VoNR). This happens **100% offline** without needing internet access, cloud databases, or third-party APIs.

Android's Telecom Framework exposes CNAP data via:
- `Call.Details.getCallerDisplayName()` — The caller's name delivered by the carrier.
- `Call.Details.getCallerDisplayNamePresentation()` — Evaluated against `TelecomManager.PRESENTATION_ALLOWED`.

In Rill Phone, CNAP serves as a **privacy-first fallback identification layer**:
1. If the contact exists locally in the user's phonebook with a valid name, **local contact details always take priority**.
2. If the number is unsaved (or has no name fields), the carrier's **CNAP name is displayed**.
3. If neither is available, it gracefully falls back to the raw **phone number**.

---

## 3. Architecture & Data Flow

```
                                      +------------------------------------+
                                      | 📡 Carrier Network (Cellular Tower)|
                                      +-----------------+------------------+
                                                        |
                                                        | Incoming Call + CNAP
                                                        v
                                      +------------------------------------+
                                      |   Android Telecom Framework (OS)   |
                                      |  details.callerDisplayName         |
                                      |  PRESENTATION_ALLOWED              |
                                      +-----------------+------------------+
                                                        |
                                                        | onCallAdded / onDetailsChanged
                                                        v
+-----------------------+             +------------------------------------+
|  CallActivity / Screen| <---------- |       CallService (InCallService)  |
|  (Compose UI Layer)   |             +-----------------+------------------+
+-----------^-----------+                               |
            |                                           | onNewCallReceived(number, cnam)
            | collects callerMetadata                   v
+-----------+-----------+             +------------------------------------+
|     CallViewModel     | <---------- |        CallStateManager            |
|   (AndroidViewModel)  |             |      (Singleton Source of Truth)   |
+-----------------------+             +-----------------+------------------+
                                                        |
                                                        | invokes on Dispatchers.IO
                                                        v
                                      +------------------------------------+
                                      |      GetCallerNameUseCase          |
                                      |   (Core CNAP Resolution Logic)     |
                                      +-----------------+------------------+
                                                        |
                                                        | getContactByNumber()
                                                        v
                                      +------------------------------------+
                                      |       ICallerRepository            |
                                      |    (CallerRepositoryImpl)          |
                                      +-----------------+------------------+
                                                        |
                                                        v
                                      +------------------------------------+
                                      |       ContactsRepository           |
                                      |  (Local SQLite / Contacts Provider)|
                                      +------------------------------------+
```

---

## 4. Complete Codebase Map of Offline Caller ID (CNAP)

### A. Core Domain & Business Logic

| File | Path | Key Role in CNAP |
|---|---|---|
| [`GetCallerNameUseCase.kt`](app/src/main/java/dev/goodwy/rphone/controller/GetCallerNameUseCase.kt) | `dev.goodwy.rphone.controller` | **Core Use Case:** Implements 4-tier resolution priority: local contact with name > clean CNAP name > contact display name > raw number. |
| [`CallerMetadata.kt`](app/src/main/java/dev/goodwy/rphone/modal/data/CallerMetadata.kt) | `dev.goodwy.rphone.modal.data` | **Data Model:** Holds resolved `number`, `name`, `isLocalContact: Boolean`, and `photoUri`. |
| [`ICallerRepository.kt`](app/src/main/java/dev/goodwy/rphone/modal/interface/ICallerRepository.kt) | `dev.goodwy.rphone.modal.interface` | **Interface:** Abstraction for number-to-contact lookup, decoupling logic from Android APIs for unit testing. |
| [`CallerRepositoryImpl.kt`](app/src/main/java/dev/goodwy/rphone/modal/repository/CallerRepositoryImpl.kt) | `dev.goodwy.rphone.modal.repository` | **Implementation:** Bridges `ICallerRepository` to `ContactsRepository`. |

### B. State Management & Telecom Service

| File | Path | Key Role in CNAP |
|---|---|---|
| [`CallStateManager.kt`](app/src/main/java/dev/goodwy/rphone/controller/CallStateManager.kt) | `dev.goodwy.rphone.controller` | **Singleton State Manager:** Manages active call metadata map (`StateFlow<Map<String, CallerMetadata>>`), handles concurrency, job cancellation, and memory bounds (max 50 calls). |
| [`CallService.kt`](app/src/main/java/dev/goodwy/rphone/controller/CallService.kt) | `dev.goodwy.rphone.controller` | **Android InCallService:** Extracts `callerDisplayName` on `onCallAdded`, `onDetailsChanged`, and `handleDisconnect` (missed calls) when `callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED`. |
| [`CallViewModel.kt`](app/src/main/java/dev/goodwy/rphone/controller/CallViewModel.kt) | `dev.goodwy.rphone.controller` | **ViewModel:** Exposes `callerMetadata` StateFlow to the UI by combining call sessions with `CallStateManager`. |

### C. Call Log & History Integration

| File | Path | Key Role in CNAP |
|---|---|---|
| [`CallLog.kt`](app/src/main/java/dev/goodwy/rphone/modal/data/CallLog.kt) | `dev.goodwy.rphone.modal.data` | Contains `isCallerIdName: Boolean` flag in `CallLogEntry` to track whether the displayed name originated from CNAP cache. |
| [`CallLogRepository.kt`](app/src/main/java/dev/goodwy/rphone/modal/repository/CallLogRepository.kt) | `dev.goodwy.rphone.modal.repository` | Reads carrier-cached CNAP names via `CallLog.Calls.CACHED_NAME`, setting `isCallerIdName = contactName.isNullOrEmpty() && !cachedName.isNullOrEmpty()`. |
| [`Search.kt`](app/src/main/java/dev/goodwy/rphone/view/screen/Search.kt) | `dev.goodwy.rphone.view.screen` | Enables users to search call log entries by carrier-provided CNAP names (`entry.isCallerIdName && entry.name.contains(...)`). |

### D. User Interface (UI) Layer

| File | Path | Key Role in CNAP |
|---|---|---|
| [`CallActivity.kt`](app/src/main/java/dev/goodwy/rphone/controller/CallActivity.kt) | `dev.goodwy.rphone.controller` | Observes and renders caller identity in the in-call window, extracting CNAP name as immediate placeholder while contact query completes. |
| [`CallScreen.kt`](app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt) | `dev.goodwy.rphone.view.screen` | Compose UI rendering active call screen with CNAP support for single calls, conference child calls, and swap calls. |

### E. Dependency Injection

| File | Path | Key Role in CNAP |
|---|---|---|
| [`CommonModule.kt`](app/src/main/java/dev/goodwy/rphone/CommonModule.kt) | `dev.goodwy.rphone` | Registers `ICallerRepository`, `GetCallerNameUseCase`, and `CallStateManager` as Koin singletons. |

### F. Unit Tests

| File | Path | Key Role in CNAP |
|---|---|---|
| [`GetCallerNameUseCaseTest.kt`](app/src/test/java/dev/goodwy/rphone/controller/GetCallerNameUseCaseTest.kt) | `dev.goodwy.rphone.controller` | Comprehensive unit tests verifying all 6 resolution scenarios (local contact wins, CNAP fallback, blank CNAP handling, contact without name fields, etc.). |

---

## 5. Summary of Key Resolution Code

```kotlin
// dev/goodwy/rphone/controller/GetCallerNameUseCase.kt
class GetCallerNameUseCase(private val repository: ICallerRepository) {
    suspend operator fun invoke(incomingNumber: String, cnamName: String?): CallerMetadata {
        val localContact = repository.getContactByNumber(incomingNumber)
        val cleanCnam = cnamName?.takeIf { it.isNotBlank() }
        val hasLocalName = localContact?.hasLocalName() == true

        return when {
            hasLocalName -> {
                // 1. Local contact with name always wins
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            cleanCnam != null -> {
                // 2. Carrier CNAP name used as fallback
                CallerMetadata(
                    number = incomingNumber,
                    name = cleanCnam,
                    isLocalContact = false,
                    photoUri = localContact?.photoUri
                )
            }
            localContact != null -> {
                // 3. Contact exists without custom name (e.g. number only)
                CallerMetadata(
                    number = incomingNumber,
                    name = localContact.displayName,
                    isLocalContact = true,
                    photoUri = localContact.photoUri
                )
            }
            else -> {
                // 4. Default fallback to raw incoming number
                CallerMetadata(
                    number = incomingNumber,
                    name = incomingNumber,
                    isLocalContact = false
                )
            }
        }
    }
}
```

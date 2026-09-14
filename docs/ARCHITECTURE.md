# Architecture

KabelWacht is a small single-module Android app (plus a `:tunnel` module that
builds WireGuard from source). It uses Jetpack Compose with a lightweight MVVM
structure and manual dependency injection — no Hilt/Dagger.

## Modules

- **`:app`** — the application: UI, data, and VPN control.
- **`:tunnel`** — thin wrapper that builds the WireGuard tunnel library from the
  pinned `third_party/wireguard-android` submodule (Java lib + `libwg-go.so`,
  `libwg.so`, `libwg-quick.so`). See the README's "WireGuard is built from source".

## `:app` layout

```
com.github.muelli.kabelwacht
├── KabelWachtApp        Application; owns AppContainer (manual DI)
├── MainActivity         hosts the Compose NavHost
├── data/
│   ├── TunnelProfile    name + parsed com.wireguard.config.Config
│   ├── ConfigStore      one wg-quick .conf per tunnel in filesDir/tunnels/
│   ├── TunnelRepository single source of truth; exposes StateFlow<List<…>>
│   ├── TunnelSearch     ranked list search (name first, then config data)
│   ├── TunnelRunConditions  automation rules + pure shouldRun() evaluation
│   └── ConditionsStore  one key=value rules file per tunnel in filesDir/conditions/
├── vpn/
│   ├── WgTunnel         com.wireguard.android.backend.Tunnel adapter
│   ├── TunnelManager    wraps GoBackend; one active tunnel; VPN consent
│   └── automation/
│       ├── NetworkStateMonitor    physical-network (never VPN) snapshots
│       ├── AutomationEngine       debounces, evaluates rules, drives TunnelManager
│       ├── ConditionMonitorService  foreground service, alive only while rules exist
│       └── BootReceiver           resumes monitoring after reboot
└── ui/
    ├── list/            tunnel list screen + ViewModel (activate/delete/search)
    ├── edit/            create/edit/import screen + ViewModel (validates config)
    ├── conditions/      per-tunnel run-conditions screen + ViewModel
    ├── nav/             navigation graph
    ├── theme/           Material 3 theme
    └── AppViewModelProvider  ViewModel factories wired from AppContainer
```

## Data flow

1. **Storage.** `ConfigStore` persists each profile as a wg-quick `.conf` file.
   `TunnelRepository` reads them into an observable `StateFlow` that the UI collects.
2. **Editing/import.** QR scans (ZXing) and file imports both produce wg-quick text,
   which the edit screen validates via `com.wireguard.config.Config.parse` before
   saving through the repository.
3. **Connecting.** `TunnelManager` holds a single `GoBackend`. Bringing a tunnel up
   first requests VPN consent (`VpnService.prepare`) if needed, then calls
   `backend.setState(tunnel, UP, config)`. Only one tunnel is active at a time.
4. **Automation.** `NetworkStateMonitor` observes physical networks (a
   `NET_CAPABILITY_NOT_VPN` request, so the tunnel can never re-trigger itself);
   `AutomationEngine` combines those snapshots with the stored
   `TunnelRunConditions`, debounces, and connects/disconnects via `TunnelManager` —
   respecting a manual off-toggle until the network next changes. The foreground
   `ConditionMonitorService` exists only while at least one tunnel has rules
   enabled.

## Dependency injection

`AppContainer` (created in `KabelWachtApp`) constructs the repository and tunnel
manager once and exposes them. `AppViewModelProvider` builds ViewModels from the
container. This keeps wiring explicit and dependency-free.

## Why the tunnel is a separate module

Building WireGuard from source requires the NDK and Go and produces native
libraries with the app's package baked in. Isolating that in `:tunnel` keeps the
`:app` build simple and lets the native build be cached independently.

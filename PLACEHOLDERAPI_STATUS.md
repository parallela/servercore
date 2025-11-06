# PlaceholderAPI Integration - Complete Summary ✅

## Overview
PlaceholderAPI support has been **fully integrated** across all features of ServerCore!

---

## ✅ Integration Status

### 1. **Tab List** ✅ ENABLED
- **File**: `TabListener.java`
- **Method**: `replacePlaceholders(String text, Player player)`
- **Line**: Uses `PlaceholderUtil.applyPlaceholdersWithBrackets(player, text)`
- **Supports**: Both `%placeholder%` and `{placeholder}` formats
- **Player Context**: YES (full player context available)

### 2. **Player MOTD (Join Welcome)** ✅ ENABLED
- **File**: `MotdListener.java`
- **Method**: `displayMotd(Player player, ConfigurationSection section)`
- **Line**: Uses `PlaceholderUtil.applyPlaceholdersWithBrackets(player, formatted)`
- **Supports**: Both `%placeholder%` and `{placeholder}` formats
- **Player Context**: YES (full player context available)

### 3. **Server MOTD (Server List)** ✅ ENABLED
- **File**: `ServerMotdListener.java`
- **Method**: `replacePlaceholders(String text, int online, int max)`
- **Line**: Uses `PlaceholderUtil.applyPlaceholders(text)`
- **Supports**: `%placeholder%` format (no player context)
- **Player Context**: NO (server list has no player context)
- **Note**: Can use server-level placeholders like `%server_online%`, `%server_tps%`

### 4. **Join Messages** ✅ ENABLED
- **File**: `JoinListener.java`
- **Method**: `onPlayerJoin(PlayerJoinEvent event)`
- **Line**: Uses `PlaceholderUtil.applyPlaceholdersWithBrackets(player, raw)`
- **Supports**: Both `%placeholder%` and `{placeholder}` formats
- **Player Context**: YES (full player context available)

### 5. **List Command** ✅ ENABLED
- **File**: `ListCommand.java`
- **Method**: `onCommand(...)`
- **Line**: Uses `PlaceholderUtil.applyPlaceholdersWithBrackets(player, formatted)`
- **Supports**: Both `%placeholder%` and `{placeholder}` formats
- **Player Context**: YES (per-player in list)

---

## 📋 Summary Table

| Feature | File | PlaceholderAPI | Player Context | Formats Supported |
|---------|------|----------------|----------------|-------------------|
| Tab List | TabListener.java | ✅ | ✅ | `%...%` & `{...}` |
| Player MOTD | MotdListener.java | ✅ | ✅ | `%...%` & `{...}` |
| Server MOTD | ServerMotdListener.java | ✅ | ❌ | `%...%` only |
| Join Messages | JoinListener.java | ✅ | ✅ | `%...%` & `{...}` |
| List Command | ListCommand.java | ✅ | ✅ | `%...%` & `{...}` |

---

## 🔧 Implementation Details

### Initialization
```java
// Main.java - Line 26
PlaceholderUtil.initialize();
```
- Called during plugin startup
- Automatically detects PlaceholderAPI
- Logs detection status to console

### Usage Pattern

#### With Player Context (Most Features)
```java
formatted = PlaceholderUtil.applyPlaceholdersWithBrackets(player, formatted);
```
- Supports: `%player_name%`, `%player_level%`, `%vault_rank%`, etc.
- Supports: `{player}`, `{online}`, etc. (built-in)

#### Without Player Context (Server MOTD)
```java
text = PlaceholderUtil.applyPlaceholders(text);
```
- Supports: `%server_online%`, `%server_tps%`, etc.
- Limited to server-level placeholders

---

## 📝 Example Usage

### Tab List with PAPI
```yaml
tab-list:
  header:
    - "<gold>%player_displayname%</gold>"
    - "Level: %player_level%"
    - "Rank: %vault_rank%"
```

### Player MOTD with PAPI
```yaml
motd:
  player:
    lines:
      - "Welcome, %player_displayname%!"
      - "Balance: $%vault_eco_balance%"
```

### Join Message with PAPI
```yaml
join-message:
  message: "%vault_prefix%%player_name% joined! [Level %player_level%]"
```

### List Command with PAPI
```yaml
commands:
  list:
    format:
      player-format: "%vault_prefix%{player} (%player_health%❤)"
```

### Server MOTD with PAPI
```yaml
motd:
  server:
    motd-lines:
      - "ServerCore | TPS: %server_tps%"
      - "Players: {online}/{max}"
```

---

## 🎯 Key Features

### 1. Automatic Detection
- ✅ Detects PlaceholderAPI on startup
- ✅ Works without PAPI (graceful fallback)
- ✅ No configuration needed

### 2. Dual Format Support
- ✅ `%placeholder%` - PlaceholderAPI format
- ✅ `{placeholder}` - Built-in format
- ✅ Both work together seamlessly

### 3. Graceful Fallback
- ✅ If PAPI not installed, placeholders remain as-is
- ✅ No errors or crashes
- ✅ Built-in placeholders still work

### 4. Player Context Aware
- ✅ Passes player object when available
- ✅ Uses player-specific placeholders
- ✅ Each player sees their own data

---

## 🚀 Testing

### Verify PlaceholderAPI Integration

1. **Check Detection**
   ```
   Server console on startup:
   [ServerCore] PlaceholderAPI found! Placeholder support enabled.
   ```

2. **Test in Tab List**
   ```yaml
   header:
     - "Your name: %player_name%"
   ```
   Should show actual player name

3. **Test in MOTD**
   ```yaml
   lines:
     - "Level: %player_level%"
   ```
   Should show actual player level

4. **Use PAPI Parse Command**
   ```
   /papi parse me %player_name%
   ```
   Should return your name

---

## ✅ Checklist

- [x] PlaceholderAPI dependency added to pom.xml
- [x] PlaceholderUtil utility class created
- [x] Initialization in Main.java
- [x] TabListener integration
- [x] MotdListener integration
- [x] ServerMotdListener integration
- [x] JoinListener integration
- [x] ListCommand integration
- [x] Config documentation updated
- [x] Soft dependency in plugin.yml

---

## 🎉 Result

**PlaceholderAPI is now fully integrated across ALL features!**

Every message, text, and display in ServerCore now supports:
- ✅ 1000+ PlaceholderAPI placeholders
- ✅ All expansions (Player, Server, Vault, Essentials, etc.)
- ✅ Custom placeholders from other plugins
- ✅ Dynamic, real-time data display

**Installation**: Just add PlaceholderAPI.jar to your plugins folder and restart! 🚀


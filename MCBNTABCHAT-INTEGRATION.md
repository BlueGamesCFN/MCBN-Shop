# MCBNTabChat Integration

## Übersicht

MCBN-Shops bietet eine vollständige Integration mit **MCBNTabChat**, sodass das Shop-Tutorial über das zentrale `/tutorial` Command verfügbar ist.

## Features

### ✅ Automatische Integration
- Das Plugin erkennt MCBNTabChat automatisch beim Start
- Keine manuelle Konfiguration erforderlich
- Funktioniert auch ohne MCBNTabChat (fallback auf eigenes Broadcast-System)

### ✅ Tutorial-Verwaltung
- Tutorial wird bei MCBNTabChat unter dem Namen **"MCBN-Shops"** registriert
- Spieler können das Tutorial jederzeit mit `/tutorial` abrufen
- Inhalte werden aus `config.yml` geladen und können angepasst werden

### ✅ Automatische Broadcasts
- Tutorial wird alle **40 Minuten** im Chat angezeigt
- Intervall kann in `config.yml` angepasst werden
- MCBNTabChat übernimmt die Broadcast-Verwaltung automatisch

## Konfiguration

### config.yml

```yaml
tutorial:
  # Aktiviert/Deaktiviert das Tutorial-System
  enabled: true

  # Broadcast-Intervall in Minuten
  broadcast-interval-minutes: 40

  # Tutorial-Nachrichten (mit Color-Codes)
  messages:
    - '&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
    - '&e&l     MCBN-Shops Tutorial'
    - '&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
    - ''
    - '&7&lWichtige Commands:'
    - '&8▸ &a/shop create &7- Erstelle einen Shop'
    - '&8▸ &a/auction start &7- Starte eine Auktion'
    - # ... weitere Nachrichten
```

### Color-Codes

Das Plugin unterstützt sowohl `&` als auch `§` Color-Codes:
- `&a` = Grün
- `&c` = Rot
- `&e` = Gelb
- `&6` = Gold
- `&l` = Fett
- `&m` = Durchgestrichen

## Technische Details

### Plugin-Dependency

```yaml
# plugin.yml
softdepend:
  - MCBNTabChat
```

Die Dependency ist **optional** (softdepend). Das Plugin funktioniert auch ohne MCBNTabChat.

### Integration-Klasse

Die Klasse `MCBNTabChatIntegration` verwendet **Reflection**, um mit MCBNTabChat zu kommunizieren:

```java
MCBNTabChatIntegration integration = new MCBNTabChatIntegration(plugin);
boolean success = integration.integrate();
```

Vorteile:
- ✅ Keine Compile-Time-Dependency auf MCBNTabChat
- ✅ Plugin kann ohne MCBNTabChat kompiliert werden
- ✅ Automatisches Fallback wenn MCBNTabChat fehlt

### TutorialBroadcastEvent API

Externe Plugins können das Broadcast-System steuern:

```java
@EventHandler
public void onTutorialBroadcast(TutorialBroadcastEvent event) {
    // Tutorial übernehmen
    event.setCancelled(true);

    // Oder Nachrichten modifizieren
    List<String> messages = event.getMessages();
    messages.add("Zusätzliche Info");
    event.setMessages(messages);
}
```

## Logging

### Erfolgreiche Integration
```
[MCBN-Shops] Tutorial erfolgreich bei MCBNTabChat registriert!
```

### MCBNTabChat nicht gefunden
```
[MCBN-Shops] MCBNTabChat nicht gefunden - Tutorial-Integration übersprungen
```

### Fehlerhafte Integration
```
[MCBN-Shops] MCBNTabChat ist installiert, aber getTutorialAPI() Methode fehlt - möglicherweise falsche Version?
```

## Befehle

| Command | Beschreibung | Permission |
|---------|-------------|-----------|
| `/tutorial` | Zeigt alle verfügbaren Tutorials (MCBNTabChat) | - |
| `/mcbnshops reload` | Lädt Config und Tutorial neu | `mcbn.admin` |

## Troubleshooting

### Tutorial erscheint nicht in `/tutorial`

**Prüfe:**
1. Ist MCBNTabChat installiert und gestartet?
2. Steht im Log: "Tutorial erfolgreich registriert"?
3. Ist `tutorial.enabled: true` in der config.yml?

**Lösung:**
```bash
/mcbnshops reload
```

### Broadcasts funktionieren nicht

**Prüfe:**
1. `tutorial.enabled: true` in config.yml
2. `tutorial.broadcast-interval-minutes` ist größer als 0
3. `tutorial.messages` ist nicht leer

### Falsches Intervall

MCBNTabChat kann ein eigenes Intervall verwenden. Prüfe die MCBNTabChat-Konfiguration.

## Kompatibilität

| MCBNTabChat Version | Status |
|-------------------|--------|
| 1.5.1+ | ✅ Vollständig unterstützt |
| 1.5.0 und älter | ⚠️ Möglicherweise inkompatibel |
| Nicht installiert | ✅ Eigenes Broadcast-System aktiv |

## API für Entwickler

### Tutorial programmatisch aktualisieren

```java
Main plugin = Main.get();
MCBNTabChatIntegration integration = plugin.tabChatIntegration();

if (integration != null && integration.isIntegrated()) {
    // Tutorial neu registrieren
    integration.unregister();
    integration.integrate();
}
```

### Tutorial-Broadcast manuell auslösen

```java
Main plugin = Main.get();
TutorialBroadcastService service = plugin.tutorialBroadcasts();
service.broadcastNow();
```

## Lizenz

Dieses Feature ist Teil von MCBN-Shops und unterliegt der gleichen Lizenz.

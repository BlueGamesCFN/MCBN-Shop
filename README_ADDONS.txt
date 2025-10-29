MCBN-Shops ADDONS (Shopkeeper + Floating Item)

Diese ZIP enthält alle NEUEN/GEÄNDERTEN Dateien:
- DisplayService.java (schwebendes Item über Kiste bei ausreichend Vorrat)
- KeeperManager.java, KeeperListener.java, KeeperCommands.java (Villager-Shopkeeper mit mehreren verknüpften Behältern)
- Main.java (bereits integriert)
- plugin.yml (neuer Command /shopkeeper)
- config.yml (floating-item.* & shopkeepers.enabled)

Einbau:
1) Entpacke diesen Ordner über dein Projekt (Quellpfade stimmen).
2) Prüfe eventuelle lokale Anpassungen in deiner vorhandenen Main.java / plugin.yml.
3) `mvn -DskipTests package`

Nutzung:
- /shopkeeper create  -> Villager an deiner Position erstellen
- /shopkeeper link    -> Während du eine Shop-Kiste anschaust, mit deinem Keeper verknüpfen
- /shopkeeper unlink  -> Verknüpfung wieder lösen
- /shopkeeper list    -> Deine Keeper auflisten
- /shopkeeper tp      -> Zum Keeper teleportieren
- /shopkeeper remove  -> Keeper entfernen

Kauf am Keeper:
- Keeper rechtsklicken -> GUI zeigt alle verknüpften Shops -> Item anklicken -> Chat fragt Bundles -> Kauf wie an der Kiste.

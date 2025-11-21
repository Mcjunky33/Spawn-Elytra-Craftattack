# SpawnElytraCraftattack – Ultimatives Elytra-Flug-System für deinen Server

**SpawnElytraCraftattack** ist eine leistungsstarke Fabric-Mod, die das Elytra-Fliegen auf deinem Minecraft-Server revolutioniert. Sie ermöglicht es, spezielle **Elytra-Areas** zu definieren, in denen Spieler automatisch starten können, Boosts erhalten und dynamische Flugmechaniken genießen – perfekt für Abenteuer- oder Minigame-Server!

---

## 🌟 Funktionen

- **Automatisches Elytra-Fliegen:** Spieler starten automatisch mit Elytra, sobald sie in der Luft sind.
- **Boost-System:** Spieler können mit Sneak-Tap einen Boost auslösen, ähnlich einer Feuerwerksrakete.
- **Flexible Flugbereiche:** Definiere beliebige Flugzonen – Radius, Box oder um den Worldspawn herum.
- **Maximale Boosts pro Area:** Jede Elytra-Area kann eigene Boost-Limits haben.
- **Dynamische Rücksetzung:** Landung, Wasser oder Reiten setzt Boosts automatisch zurück.
- **Persistente Konfiguration:** Alle Areas werden in `config/elytra_areas.json` gespeichert.
- **Kompatibel:** Unterstützt Survival, Adventure und Creative (Creative/Spectator wird ignoriert).

---

## ⚡ Elytra-Areas

Es gibt drei Arten von Areas:

| Typ           | Beschreibung                                                                 |
|---------------|-----------------------------------------------------------------------------|
| **Worldspawn**| Kreis um den Weltspawn mit definierbarem Radius.                             |
| **Radius**    | Kreisförmige Area an beliebiger Position.                                    |
| **Box**       | Rechteckige Area durch zwei Eckpunkte definiert.                              |

Jede Area kann individuelle **MaxBoosts** haben, um die Anzahl der Boosts pro Spieler zu limitieren.

---

## 🛠️ Befehle

Alle Befehle erfordern **Admin-Rechte** (Permission Level 2).

### Area Management

```bash
# Fügt eine Area um den Worldspawn hinzu
/selytra addarea <name> worldspawn <radius>

# Fügt eine radiusbasierte Area hinzu
/selytra addarea <name> radius <x> <y> <z> <radius>

# Fügt eine Box-Area hinzu
/selytra addarea <name> box <x1> <y1> <z1> <x2> <y2> <z2>

# Bearbeitet eine bestehende Area
/selytra editarea <name> worldspawn <radius>
/selytra editarea <name> radius <x> <y> <z> <radius>
/selytra editarea <name> box <x1> <y1> <z1> <x2> <y2> <z2>

# Legt die maximale Anzahl an Boosts für eine Area fest
/selytra editmaxboosts <name> <value>

# Löscht eine bestehende Area
/selytra removearea <name>

# Listet alle definierten Areas
/selytra listarea

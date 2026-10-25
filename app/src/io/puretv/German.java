package io.puretv;

/** German copy for text drawn directly by the custom UI. Source keys are the existing English copy. */
final class German {
    private German() {}

    static String text(String english) {
        switch (english) {
            case "Good morning": return "Guten Morgen";
            case "Good afternoon": return "Guten Tag";
            case "Good evening": return "Guten Abend";
            case "Good night": return "Gute Nacht";

            case "Content hidden on Home": return "Inhalte auf dem Startbildschirm ausgeblendet";
            case "Press OK to restore content in settings.": return "OK öffnet die Einstellungen zum Wiederherstellen der Inhalte.";
            case "Hide this content": return "Diesen Inhalt ausblenden";
            case "Restore hidden content": return "Ausgeblendete Inhalte wieder anzeigen";
            case "Storage": return "Speicher";
            case "available": return "verfügbar";
            case "Network, memory and storage": return "Netzwerk, Arbeitsspeicher und Speicher";
            case "Available / total. Values refresh when you open this screen.": return "Verfügbar / gesamt. Die Werte werden beim Öffnen aktualisiert.";
            case "Restore hidden content before hiding more.": return "Zeige ausgeblendete Inhalte wieder an, bevor du weitere ausblendest.";
            case "Content hidden. Restore it in Recently watched settings.": return "Inhalt ausgeblendet. Wiederherstellen unter Zuletzt angesehen.";

            case "Display & Network": return "Anzeige & Netzwerk";
            case "Clock format": return "Zeitformat";
            case "Lightweight mode": return "Leichter Modus";
            case "Lightweight mode reduces effects and animation. Off keeps the original appearance.": return "Der leichte Modus reduziert Effekte und Animationen. Aus behält das bisherige Aussehen.";
            case "Changes apply when you return home. Back returns to settings.": return "Änderungen gelten nach der Rückkehr zum Startbildschirm. Zurück führt zu den Einstellungen.";
            case "Network": return "Netzwerk";
            case "Internet available": return "Internet verfügbar";
            case "Internet not verified": return "Internet nicht bestätigt";
            case "Disconnected": return "Nicht verbunden";
            case "Status unavailable": return "Status nicht verfügbar";

            case "Live TV": return "Live-TV";
            case "All Apps": return "Alle Apps";
            case "Settings": return "Einstellungen";
            case "Home Settings": return "Start-Einstellungen";
            case "TV Settings": return "TV-Einstellungen";
            case "Open AirPlay Receiver": return "AirPlay-Empfänger öffnen";
            case "Connect this TV and your Apple device to the same Wi-Fi network.": return "Verbinde diesen Fernseher und dein Apple-Gerät mit demselben WLAN.";
            case "iPhone / iPad: Control Center → Screen Mirroring → select this TV.": return "iPhone / iPad: Kontrollzentrum → Bildschirmsynchronisierung → diesen Fernseher wählen.";
            case "Mac: Control Center → Screen Mirroring → select this TV.": return "Mac: Kontrollzentrum → Bildschirmsynchronisierung → diesen Fernseher wählen.";
            case "Protected video may not mirror.": return "Geschützte Videos lassen sich möglicherweise nicht spiegeln.";
            case "HomePod system audio output is not supported on Android 9.": return "Android 9 unterstützt keine Systemaudioausgabe an den HomePod.";
            case "Open the receiver, then select this TV from your Apple device.": return "Öffne den Empfänger und wähle diesen Fernseher auf deinem Apple-Gerät.";
            case "Could not open AirPlay Receiver. Check its installation on this TV.": return "AirPlay-Empfänger konnte nicht geöffnet werden. Prüfe die Installation auf diesem Fernseher.";
            case "AirPlay Receiver is not installed. Install it on this TV, then return here.": return "AirPlay-Empfänger ist nicht installiert. Installiere ihn auf diesem Fernseher und kehre hierher zurück.";
            case "HDMI Inputs": return "HDMI-Eingänge";
            case "Inputs": return "Eingänge";
            case "Shortcuts": return "Verknüpfungen";
            case "Weather": return "Wetter";
            case "No weather data right now.": return "Gerade keine Wetterdaten.";
            case "Sunrise ": return "Sonnenaufgang ";
            case "Sunset ": return "Sonnenuntergang ";
            case "Crypto": return "Krypto";
            case "24h change": return "Änderung in 24 h";
            case "For You": return "Für dich";
            case "Couldn't open ": return "Konnte nicht öffnen: ";
            case " of ": return " von ";
            case "← → to move · OK to drop · Back to cancel": return "← → verschieben · OK ablegen · Zurück abbrechen";
            case "Moving ": return "Verschiebe ";
            case ", position ": return ", Position ";
            case ", tab ": return ", Tab ";
            case " of 3": return " von 3";
            case "Dollar": return "Dollar";
            case "Series": return "Serie";
            case "prices from ": return "Kurse von ";
            case "Starting…": return "Wird gestartet…";
            case "Original": return "Original";
            case "Home Screen, ": return "Startbildschirm, ";
            case "Screensaver, preview": return "Bildschirmschoner, Vorschau";
            case "no ads, with the new screensaver": return "ohne Werbung, mit dem neuen Bildschirmschoner";
            case "the TV's factory home screen": return "der Startbildschirm ab Werk";
            case ". In use": return ". Aktiv";
            case " of 2": return " von 2";
            case "Your TV's home screen and screensaver": return "Startbildschirm und Bildschirmschoner des Fernsehers";
            case "Home Screen": return "Startbildschirm";
            case "Screensaver": return "Bildschirmschoner";
            case "Preview": return "Ansehen";
            case "On the TV: tick “Always allow” and press OK.": return "Am Fernseher: „Immer zulassen“ ankreuzen und OK drücken.";
            case "RECOMMENDATIONS": return "EMPFEHLUNGEN";
            case "No TMDB key: see the README (config.properties).": return "Kein TMDB-Schlüssel: siehe README (config.properties).";
            case "Error: ": return "Fehler: ";
            case "Fetching titles…": return "Titel werden geladen…";
            case "This product uses the TMDB API but is not endorsed or certified by TMDB.": return "Nutzt die TMDB-API, ohne Billigung oder Zertifizierung durch TMDB.";
            case "COMPARISON": return "VERGLEICH";
            case "Home button": return "Home-Taste";
            case "Free memory": return "Freier Speicher";
            case "Home screen memory": return "Speicher des Startbildschirms";
            case "NOTICES": return "HINWEISE";
            case "None yet.": return "Noch keine.";
            case "No ads, with the new screensaver": return "Ohne Werbung, mit dem neuen Bildschirmschoner";
            case "The TV's factory home screen": return "Der Startbildschirm ab Werk";
            case "Switching takes a few seconds; the screen blinks while it measures.": return "Der Wechsel dauert ein paar Sekunden; beim Messen flackert der Bildschirm.";
            case "now": return "jetzt";
            case "Measuring the original home screen…": return "Der ursprüngliche Startbildschirm wird gemessen…";
            case "Switching the home screen and screensaver…": return "Startbildschirm und Bildschirmschoner werden gewechselt…";
            case "Android didn't accept the new home screen": return "Android hat den neuen Startbildschirm nicht angenommen";
            case "Turning off apps (": return "Apps werden ausgeschaltet (";
            case "Measuring performance…": return "Leistung wird gemessen…";
            case "Couldn't turn on the home screen: ": return "Der Startbildschirm ließ sich nicht einschalten: ";
            case "Turning the apps back on…": return "Die Apps werden wieder eingeschaltet…";
            case "Turning apps back on (": return "Apps werden wieder eingeschaltet (";
            case "Restoring the original home screen…": return "Der ursprüngliche Startbildschirm wird wiederhergestellt…";
            case "Original mode: the factory home screen, screensaver and apps are back.": return "Originalmodus: Startbildschirm, Bildschirmschoner und Apps ab Werk sind zurück.";
            case "the original home screen": return "den ursprünglichen Startbildschirm";
            case "Original mode, but I couldn't turn back on: ": return "Originalmodus, aber das ließ sich nicht wieder einschalten: ";
            case "Couldn't go back to the original: ": return "Zurück zum Original ging nicht: ";
            case "The home screen crashed 3 times in a row; I switched back to the original one.": return "Der Startbildschirm ist 3-mal hintereinander abgestürzt; der ursprüngliche ist wieder aktiv.";
            case "the home screen": return "den Startbildschirm";
            case "the screensaver": return "den Bildschirmschoner";
            case "Couldn't check the home screen: ": return "Der Startbildschirm ließ sich nicht prüfen: ";
            case "The TV update turned back on ": return "Das TV-Update hat wieder eingeschaltet: ";
            case "Turned off what was missing: ": return "Ausgeschaltet, was noch fehlte: ";
            case "The TV was updated; your home screen is still on.": return "Der Fernseher wurde aktualisiert; der eigene Startbildschirm ist weiter aktiv.";
            case "Connecting…": return "Verbindung wird hergestellt…";
            case "Accept “Allow USB debugging?” on the TV (tick “Always allow”).": return "Am Fernseher „USB-Debugging zulassen?“ bestätigen („Immer zulassen“ ankreuzen).";
            case "The home screen ran into a problem.\nGoing back to the TV's original home screen…": return "Der Startbildschirm hatte ein Problem.\nZurück zum ursprünglichen Startbildschirm…";
            case "the TV's first setup saved the Wi-Fi, and turning it off would erase those networks. "
                    + "In Settings → Network & Internet, forget the network and connect again (networks out of range don't "
                    + "show there: Claude Code can remove them from your computer), then try once more.":
                return "die Ersteinrichtung des Fernsehers hat das WLAN gespeichert, und sie auszuschalten würde diese Netzwerke löschen. "
                        + "Unter Einstellungen → Netzwerk und Internet das Netzwerk entfernen und neu verbinden (Netzwerke außer Reichweite "
                        + "erscheinen dort nicht: Claude Code entfernt sie vom Computer aus), dann noch einmal versuchen.";
            // Home Settings, Shelf and Remote Buttons
            case "Language": return "Sprache";
            case "Temperature": return "Temperatur";
            case "Theme": return "Design";
            case "Automatic": return "Automatisch";
            case "Light": return "Hell";
            case "Dark": return "Dunkel";
            case "Weather widget": return "Wetter-Widget";
            // What TalkBack reads on the drawer's weather card (#47)
            case ". High ": return ". Höchstwert ";
            case ", low ": return ", Tiefstwert ";
            case " Next hours: ": return " Nächste Stunden: ";
            case " Sunrise ": return " Sonnenaufgang ";
            case " Sunset ": return " Sonnenuntergang ";
            case "City": return "Stadt";
            case "Searching…": return "Suche…";
            case "Type the city's name": return "Namen der Stadt eingeben";
            case "Search": return "Suchen";
            case "Cancel": return "Abbrechen";
            case "No city with that name.": return "Keine Stadt mit diesem Namen.";
            case "Couldn't search now: ": return "Suche gerade nicht möglich: ";
            case "Quotes widget": return "Kurs-Widget";
            case "On": return "An";
            case "Off": return "Aus";
            case "Shelf": return "Leiste";
            case "on the shelf, place ": return "in der Leiste, Platz ";
            case "not on the shelf": return "nicht in der Leiste";
            case "Remote Buttons": return "Fernbedienungstasten";
            case "Netflix button": return "Netflix-Taste";
            case "YouTube button": return "YouTube-Taste";
            case "Prime Video button": return "Prime-Video-Taste";
            case "TCL Channel button": return "TCL-Channel-Taste";
            case "Nothing": return "Nichts";
            case "Not installed": return "Nicht installiert";
            case ", selected": return ", ausgewählt";
            case "OK or the arrows change the value.": return "OK oder die Pfeiltasten ändern den Wert.";
            case "OK picks, Back cancels.": return "OK wählt aus, Zurück bricht ab.";
            case "Disney and Globoplay: the TV itself controls those buttons.": return "Disney und Globoplay: Diese Tasten steuert der Fernseher selbst.";
            case "YouTube, Prime Video and Google Play: the TV itself controls those buttons.": return "YouTube, Prime Video und Google Play: Diese Tasten steuert der Fernseher selbst.";
            case "These are the TCL remote's buttons: on this TV, the buttons work as they always did.": return "Das sind die Tasten der TCL-Fernbedienung: Auf diesem Fernseher funktionieren sie wie immer.";
            case "Connection, clock and appearance": return "Verbindung, Uhrzeit und Darstellung";
            case "Mirror your Apple device": return "Apple-Gerät spiegeln";
            case "Receiver": return "Empfänger";
            case "Device status": return "Gerätestatus";
            case "Edit shortcuts": return "Verknüpfungen bearbeiten";
            case "Bottom drawer shortcuts": return "Verknüpfungen im unteren Bereich";
            case "Limit of 6 shortcuts. Remove one before adding another.": return "Höchstens 6 Verknüpfungen. Zuerst eine entfernen.";
            case "OK adds or removes. Left/right reorders selected shortcuts.": return "OK fügt hinzu oder entfernt. ← → sortiert ausgewählte Verknüpfungen.";
            case "The app shelf is edited separately in Settings → Shelf.": return "Die App-Leiste wird separat unter Einstellungen → Leiste bearbeitet.";
            case "Installed": return "Installiert";
            case "AirPlay connection help": return "AirPlay-Verbindungshilfe";
            case "Connect and mirror": return "Verbinden und Bildschirm spiegeln";
            case "Network and AirPlay connection": return "Netzwerk und AirPlay-Verbindung";
            case "AirPlay receiver": return "AirPlay-Empfänger";
            case "Wi-Fi settings": return "WLAN-Einstellungen";
            case "This TV is offline. Open Wi-Fi settings to connect.": return "Der Fernseher ist offline. Zum Verbinden WLAN-Einstellungen öffnen.";
            case "Check this TV's connection in Wi-Fi settings.": return "Verbindung des Fernsehers in den WLAN-Einstellungen prüfen.";
            case "1. Connect your Apple device to the same Wi-Fi network.\n2. Open the receiver and check that it is started.\n3. On iPhone, iPad or Mac: Control Center → Screen Mirroring → select the receiver name.\n4. If asked for a PIN, enter the code shown on this TV.\n\nIf it is missing: check guest Wi-Fi, VPN and device isolation on the router.": return "1. Apple-Gerät mit demselben WLAN verbinden.\n2. Empfänger öffnen und prüfen, ob er gestartet ist.\n3. Auf iPhone, iPad oder Mac: Kontrollzentrum → Bildschirmsynchronisierung → Empfänger wählen.\n4. Falls angefordert, PIN vom Fernseher eingeben.\n\nFalls er fehlt: Gast-WLAN, VPN und Geräteisolierung am Router prüfen.";
            case "If AirPlay is missing, open connection help. The receiver app shows whether it is running.": return "Falls AirPlay fehlt, Verbindungshilfe öffnen. Der Empfänger zeigt seinen Betriebszustand in seiner App.";
            case "Could not open Wi-Fi settings.": return "WLAN-Einstellungen konnten nicht geöffnet werden.";
            case "Open": return "Öffnen";
            case "App info": return "App-Info";
            case "Remove from shortcuts": return "Aus Verknüpfungen entfernen";
            case "Add to shortcuts": return "Zu Verknüpfungen hinzufügen";
            case "Move on shelf": return "In der Leiste verschieben";
            case "Edit home screen": return "Startbildschirm bearbeiten";
            case "Choose a section": return "Bereich wählen";
            case "Home screen apps": return "Apps auf dem Startbildschirm";
            case "Other apps": return "Weitere Apps";
            case "Fixed": return "Fest";
            case "Only built-in functions can be added. All Apps stays in the first position.": return "Nur integrierte Funktionen können hinzugefügt werden. Alle Apps bleibt an erster Stelle.";
            case "In each section, hold OK and choose Move on shelf. OK confirms; Back cancels.": return "In jedem Bereich OK halten und In der Leiste verschieben wählen. OK bestätigt; Zurück bricht ab.";
            case "Arrows to move · OK confirms · Back cancels": return "Pfeile verschieben · OK bestätigt · Zurück bricht ab";
            default:
                if (english.startsWith("Home screen is on: new home screen and screensaver, ")) {
                    return "Startbildschirm ist an: neuer Startbildschirm und Bildschirmschoner, "
                            + english.substring("Home screen is on: new home screen and screensaver, ".length()).replace(" apps turned off.", " Apps ausgeschaltet.");
                }
                if (english.startsWith("On · ")) {
                    return "An · " + english.substring(5).replace(" titles from the TV's apps, 3 new ones every hour.",
                            " Titel aus den Apps des Fernsehers, 3 neue pro Stunde.");
                }
                if (english.startsWith("OK adds or removes (up to ")) {
                    return "OK fügt hinzu oder entfernt (bis zu " + english.substring("OK adds or removes (up to ".length())
                            .replace("). ← → change the order.", "). ← → ändern die Reihenfolge.");
                }
                if (english.endsWith(" min ago")) return "vor " + english.substring(0, english.length() - 8) + " Min.";
                if (english.endsWith(" h ago")) return "vor " + english.substring(0, english.length() - 6) + " Std.";
                if (english.endsWith(" days ago")) return "vor " + english.substring(0, english.length() - 9) + " Tagen";
                return english;
        }
    }
}

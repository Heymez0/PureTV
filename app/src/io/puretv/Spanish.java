package io.puretv;

/** Spanish copy for text drawn directly by the custom UI. Source keys are the existing English copy. */
final class Spanish {
    private Spanish() {}

    static String text(String english) {
        switch (english) {
            case "Good morning": return "Buenos días";
            case "Good afternoon": return "Buenas tardes";
            case "Good evening": return "Buenas noches";
            case "Good night": return "Buenas noches";

            case "Content hidden on Home": return "Hay contenido oculto en Inicio";
            case "Press OK to restore content in settings.": return "Pulsa OK para restaurar contenido en los ajustes.";
            case "Hide this content": return "Ocultar este contenido";
            case "Restore hidden content": return "Mostrar contenidos ocultos";
            case "Storage": return "Almacenamiento";
            case "available": return "disponibles";
            case "Network, memory and storage": return "Red, memoria y almacenamiento";
            case "Available / total. Values refresh when you open this screen.": return "Disponible / total. Los valores se actualizan al abrir esta pantalla.";
            case "Restore hidden content before hiding more.": return "Restaura los contenidos ocultos antes de ocultar más.";
            case "Content hidden. Restore it in Recently watched settings.": return "Contenido oculto. Puedes restaurarlo en Vistos recientemente.";

            case "Display & Network": return "Pantalla y red";
            case "Clock format": return "Formato de hora";
            case "Lightweight mode": return "Modo ligero";
            case "Lightweight mode reduces effects and animation. Off keeps the original appearance.": return "El modo ligero reduce efectos y animación. Desactivado mantiene el aspecto original.";
            case "Changes apply when you return home. Back returns to settings.": return "Los cambios se aplican al volver al inicio. Atrás vuelve a ajustes.";
            case "Network": return "Red";
            case "Internet available": return "Internet disponible";
            case "Internet not verified": return "Internet sin verificar";
            case "Disconnected": return "Sin conexión";
            case "Status unavailable": return "Estado no disponible";

            case "Live TV": return "TV en vivo";
            case "All Apps": return "Todas las apps";
            case "Settings": return "Ajustes";
            case "Home Settings": return "Ajustes de inicio";
            case "TV Settings": return "Ajustes del TV";
            case "Open AirPlay Receiver": return "Abrir receptor AirPlay";
            case "Connect this TV and your Apple device to the same Wi-Fi network.": return "Conecta este TV y tu dispositivo Apple a la misma red Wi-Fi.";
            case "iPhone / iPad: Control Center → Screen Mirroring → select this TV.": return "iPhone / iPad: Centro de control → Duplicar pantalla → selecciona este TV.";
            case "Mac: Control Center → Screen Mirroring → select this TV.": return "Mac: Centro de control → Duplicar pantalla → selecciona este TV.";
            case "Protected video may not mirror.": return "Es posible que los vídeos protegidos no se dupliquen.";
            case "HomePod system audio output is not supported on Android 9.": return "Android 9 no permite enviar el audio del sistema al HomePod.";
            case "Open the receiver, then select this TV from your Apple device.": return "Abre el receptor y selecciona este TV desde tu dispositivo Apple.";
            case "Could not open AirPlay Receiver. Check its installation on this TV.": return "No se pudo abrir el receptor AirPlay. Comprueba su instalación en este TV.";
            case "AirPlay Receiver is not installed. Install it on this TV, then return here.": return "El receptor AirPlay no está instalado. Instálalo en este TV y vuelve aquí.";
            case "HDMI Inputs": return "Entradas HDMI";
            case "Inputs": return "Entradas";
            case "Shortcuts": return "Accesos directos";
            case "Weather": return "Clima";
            case "No weather data right now.": return "No hay datos del clima ahora.";
            case "Sunrise ": return "Amanecer ";
            case "Sunset ": return "Atardecer ";
            case "Crypto": return "Cripto";
            case "24h change": return "variación en 24 h";
            case "For You": return "Para ti";
            case "Couldn't open ": return "No se pudo abrir ";
            case " of ": return " de ";
            case "← → to move · OK to drop · Back to cancel": return "← → para mover · OK para soltar · Atrás para cancelar";
            case ", tab ": return ", pestaña ";
            case " of 3": return " de 3";
            case "Dollar": return "Dólar";
            case "Series": return "Serie";
            case "prices from ": return "precios de ";
            case "Starting…": return "Iniciando…";
            case "Original": return "Original";
            case "Home Screen, ": return "Pantalla de inicio, ";
            case "Screensaver, preview": return "Protector de pantalla, vista previa";
            case "no ads, with the new screensaver": return "sin anuncios, con el nuevo protector de pantalla";
            case "the TV's factory home screen": return "la pantalla de inicio de fábrica del TV";
            case ". In use": return ". En uso";
            case " of 2": return " de 2";
            case "Your TV's home screen and screensaver": return "Pantalla de inicio y protector de pantalla del TV";
            case "Home Screen": return "Pantalla de inicio";
            case "Screensaver": return "Protector de pantalla";
            case "Preview": return "Ver ahora";
            case "On the TV: tick “Always allow” and press OK.": return "En el TV: marca “Permitir siempre” y presiona OK.";
            case "RECOMMENDATIONS": return "RECOMENDACIONES";
            case "No TMDB key: see the README (config.properties).": return "Sin clave de TMDB: consulta el README (config.properties).";
            case "Error: ": return "Error: ";
            case "Fetching titles…": return "Buscando títulos…";
            case "This product uses the TMDB API but is not endorsed or certified by TMDB.": return "Este producto usa la API de TMDB, sin respaldo ni certificación de TMDB.";
            case "COMPARISON": return "COMPARACIÓN";
            case "Home button": return "Botón Inicio";
            case "Free memory": return "Memoria libre";
            case "Home screen memory": return "Memoria de la pantalla de inicio";
            case "NOTICES": return "AVISOS";
            case "None yet.": return "Ninguno todavía.";
            case "No ads, with the new screensaver": return "Sin anuncios, con el nuevo protector de pantalla";
            case "The TV's factory home screen": return "La pantalla de inicio de fábrica del TV";
            case "Switching takes a few seconds; the screen blinks while it measures.": return "El cambio tarda unos segundos; la pantalla parpadea durante las mediciones.";
            case "now": return "ahora";
            case "Measuring the original home screen…": return "Midiendo la pantalla de inicio original…";
            case "Switching the home screen and screensaver…": return "Cambiando la pantalla de inicio y el protector de pantalla…";
            case "Android didn't accept the new home screen": return "Android no aceptó la nueva pantalla de inicio";
            case "Turning off apps (": return "Desactivando apps (";
            case "Measuring performance…": return "Midiendo rendimiento…";
            case "Couldn't turn on the home screen: ": return "No se pudo activar la pantalla de inicio: ";
            case "Turning the apps back on…": return "Reactivando las apps…";
            case "Turning apps back on (": return "Reactivando apps (";
            case "Restoring the original home screen…": return "Restaurando la pantalla de inicio original…";
            case "Original mode: the factory home screen, screensaver and apps are back.": return "Modo original: se restauraron la pantalla de inicio, el protector y las apps de fábrica.";
            case "the original home screen": return "la pantalla de inicio original";
            case "Original mode, but I couldn't turn back on: ": return "Modo original, pero no se pudo reactivar: ";
            case "Couldn't go back to the original: ": return "No se pudo volver al modo original: ";
            case "The home screen crashed 3 times in a row; I switched back to the original one.": return "La pantalla de inicio falló 3 veces seguidas; volví a la original.";
            case "the home screen": return "la pantalla de inicio";
            case "the screensaver": return "el protector de pantalla";
            case "Couldn't check the home screen: ": return "No se pudo comprobar la pantalla de inicio: ";
            case "The TV update turned back on ": return "La actualización del TV reactivó ";
            case "Turned off what was missing: ": return "Desactivé lo que faltaba: ";
            case "The TV was updated; your home screen is still on.": return "El TV se actualizó; la pantalla de inicio sigue activa.";
            case "Connecting…": return "Conectando…";
            case "Accept “Allow USB debugging?” on the TV (tick “Always allow”).": return "Acepta “¿Permitir depuración USB?” en el TV (marca “Permitir siempre”).";
            case "The home screen ran into a problem.\nGoing back to the TV's original home screen…": return "La pantalla de inicio tuvo un problema.\nVolviendo a la pantalla original del TV…";
            // Home Settings, Shelf and Remote Buttons (#15, #16, #17)
            case "Language": return "Idioma";
            case "Temperature": return "Temperatura";
            case "Theme": return "Tema";
            case "Automatic": return "Automático";
            case "Light": return "Claro";
            case "Dark": return "Oscuro";
            case "Weather widget": return "Widget del clima";
            // What TalkBack reads on the drawer's weather card (#47)
            case ". High ": return ". Máxima ";
            case ", low ": return ", mínima ";
            case " Next hours: ": return " Próximas horas: ";
            case " Sunrise ": return " Amanecer ";
            case " Sunset ": return " Atardecer ";
            case "City": return "Ciudad";
            case "Searching…": return "Buscando…";
            case "Type the city's name": return "Escribe el nombre de la ciudad";
            case "Search": return "Buscar";
            case "Cancel": return "Cancelar";
            case "No city with that name.": return "Ninguna ciudad con ese nombre.";
            case "Couldn't search now: ": return "No se pudo buscar ahora: ";
            case "Quotes widget": return "Widget de cotizaciones";
            case "On": return "Activado";
            case "Off": return "Desactivado";
            case "Shelf": return "Estante";
            case "on the shelf, place ": return "en el estante, posición ";
            case "not on the shelf": return "fuera del estante";
            case "Remote Buttons": return "Botones del control";
            case "Netflix button": return "Botón Netflix";
            case "YouTube button": return "Botón YouTube";
            case "Prime Video button": return "Botón Prime Video";
            case "TCL Channel button": return "Botón TCL Channel";
            case "Nothing": return "Nada";
            case "Not installed": return "No instalada";
            case ", selected": return ", seleccionado";
            case "OK or the arrows change the value.": return "OK o las flechas cambian el valor.";
            case "OK picks, Back cancels.": return "OK elige, Atrás cancela.";
            case "Disney and Globoplay: the TV itself controls those buttons.": return "Disney y Globoplay: el propio TV controla esos botones.";
            case "YouTube, Prime Video and Google Play: the TV itself controls those buttons.": return "YouTube, Prime Video y Google Play: el propio TV controla esos botones.";
            case "These are the TCL remote's buttons: on this TV, the buttons work as they always did.": return "Estos son los botones del control de TCL: en este TV, los botones funcionan como siempre.";
            case "Connection, clock and appearance": return "Conexión, hora y apariencia";
            case "Mirror your Apple device": return "Duplica tu dispositivo Apple";
            case "Receiver": return "Receptor";
            case "Device status": return "Estado del dispositivo";
            case "Edit shortcuts": return "Editar accesos directos";
            case "Bottom drawer shortcuts": return "Accesos del panel inferior";
            case "Limit of 6 shortcuts. Remove one before adding another.": return "Máximo de 6 accesos. Quita uno antes de añadir otro.";
            case "OK adds or removes. Left/right reorders selected shortcuts.": return "OK añade o quita. ← → cambia el orden de los accesos elegidos.";
            case "The app shelf is edited separately in Settings → Shelf.": return "La fila de apps se edita aparte en Ajustes → Estante.";
            case "Installed": return "Instalado";
            case "AirPlay connection help": return "Ayuda de conexión AirPlay";
            case "Connect and mirror": return "Conecta y duplica la pantalla";
            case "Network and AirPlay connection": return "Red y conexión AirPlay";
            case "AirPlay receiver": return "Receptor AirPlay";
            case "Wi-Fi settings": return "Ajustes de Wi-Fi";
            case "This TV is offline. Open Wi-Fi settings to connect.": return "La TV no tiene conexión. Abre los ajustes de Wi-Fi.";
            case "Check this TV's connection in Wi-Fi settings.": return "Comprueba la conexión en los ajustes de Wi-Fi.";
            case "1. Connect your Apple device to the same Wi-Fi network.\n2. Open the receiver and check that it is started.\n3. On iPhone, iPad or Mac: Control Center → Screen Mirroring → select the receiver name.\n4. If asked for a PIN, enter the code shown on this TV.\n\nIf it is missing: check guest Wi-Fi, VPN and device isolation on the router.": return "1. Conecta el dispositivo Apple a la misma red Wi-Fi.\n2. Abre el receptor y comprueba que esté iniciado.\n3. En iPhone, iPad o Mac: Centro de control → Duplicar pantalla → elige el receptor.\n4. Si se pide un PIN, introduce el código de la TV.\n\nSi no aparece: comprueba la red de invitados, VPN y el aislamiento del router.";
            case "If AirPlay is missing, open connection help. The receiver app shows whether it is running.": return "Si AirPlay no aparece, abre la ayuda. La app del receptor indica si está funcionando.";
            case "Could not open Wi-Fi settings.": return "No se pudieron abrir los ajustes de Wi-Fi.";
            case "Open": return "Abrir";
            case "App info": return "Información de la app";
            case "Remove from shortcuts": return "Quitar de accesos directos";
            case "Add to shortcuts": return "Añadir a accesos directos";
            case "Move on shelf": return "Mover en el estante";
            case "Edit home screen": return "Editar pantalla de inicio";
            case "Choose a section": return "Elige una sección";
            case "Home screen apps": return "Apps de inicio";
            case "Other apps": return "Otras apps";
            case "Fixed": return "Fijo";
            case "Only built-in functions can be added. All Apps stays in the first position.": return "Solo se pueden añadir funciones integradas. Todas las apps ocupa siempre la primera posición.";
            case "In each section, hold OK and choose Move on shelf. OK confirms; Back cancels.": return "En cada sección, mantén OK y elige Mover en el estante. OK confirma; Atrás cancela.";
            case "Arrows to move · OK confirms · Back cancels": return "Flechas para mover · OK confirma · Atrás cancela";
            default:
                if (english.startsWith("Home screen is on: new home screen and screensaver, ")) {
                    return "Pantalla de inicio activa: nueva pantalla de inicio y protector, "
                            + english.substring("Home screen is on: new home screen and screensaver, ".length()).replace(" apps turned off.", " apps desactivadas.");
                }
                if (english.startsWith("On · ")) {
                    return "Activas · " + english.substring(5).replace(" titles from the TV's apps, 3 new ones every hour.",
                            " títulos de las apps del TV, 3 nuevos cada hora.");
                }
                if (english.endsWith(" min ago")) return "hace " + english.substring(0, english.length() - 4);
                if (english.endsWith(" h ago")) return "hace " + english.substring(0, english.length() - 4);
                if (english.endsWith(" days ago")) return "hace " + english.substring(0, english.length() - 9) + " días";
                return english;
        }
    }
}

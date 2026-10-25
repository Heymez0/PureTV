package io.puretv;

/** Russian copy for the custom UI. Keys are the existing English copy, as in Spanish.java. */
final class Russian {
    private Russian() {}

    static String count(long n, String one, String few, String many) {
        long last = Math.abs(n % 100);
        String word = last >= 11 && last <= 14 ? many
                : last % 10 == 1 ? one : last % 10 >= 2 && last % 10 <= 4 ? few : many;
        return n + " " + word;
    }

    static String text(String english) {
        switch (english) {
            case "Good morning": return "Доброе утро";
            case "Good afternoon": return "Добрый день";
            case "Good evening": return "Добрый вечер";
            case "Good night": return "Доброй ночи";

            case "Content hidden on Home": return "На главном экране есть скрытый контент";
            case "Press OK to restore content in settings.": return "Нажмите OK, чтобы восстановить контент в настройках.";
            case "Hide this content": return "Скрыть этот контент";
            case "Restore hidden content": return "Показать скрытый контент";
            case "Storage": return "Хранилище";
            case "available": return "доступно";
            case "Network, memory and storage": return "Сеть, память и хранилище";
            case "Available / total. Values refresh when you open this screen.": return "Доступно / всего. Значения обновляются при открытии экрана.";
            case "Restore hidden content before hiding more.": return "Восстановите скрытый контент, прежде чем скрывать новый.";
            case "Content hidden. Restore it in Recently watched settings.": return "Контент скрыт. Его можно восстановить в настройках недавно просмотренного.";

            case "Display & Network": return "Экран и сеть";
            case "Clock format": return "Формат времени";
            case "Lightweight mode": return "Облегчённый режим";
            case "Lightweight mode reduces effects and animation. Off keeps the original appearance.": return "Облегчённый режим сокращает эффекты и анимацию. В выключенном состоянии оформление прежнее.";
            case "Changes apply when you return home. Back returns to settings.": return "Изменения применятся при возврате на главный экран. Назад возвращает к настройкам.";
            case "Network": return "Сеть";
            case "Internet available": return "Интернет доступен";
            case "Internet not verified": return "Интернет не подтверждён";
            case "Disconnected": return "Нет подключения";
            case "Status unavailable": return "Статус недоступен";

            case "Live TV": return "Телеканалы";
            case "All Apps": return "Приложения";
            case "Settings": return "Настройки";
            case "Home Settings": return "Главный экран";
            case "TV Settings": return "Настройки ТВ";
            case "Open AirPlay Receiver": return "Открыть приёмник AirPlay";
            case "Connect this TV and your Apple device to the same Wi-Fi network.": return "Подключите этот телевизор и устройство Apple к одной сети Wi-Fi.";
            case "iPhone / iPad: Control Center → Screen Mirroring → select this TV.": return "iPhone / iPad: Пункт управления → Повтор экрана → выберите этот телевизор.";
            case "Mac: Control Center → Screen Mirroring → select this TV.": return "Mac: Пункт управления → Повтор экрана → выберите этот телевизор.";
            case "Protected video may not mirror.": return "Защищённые видео могут не отображаться при повторе экрана.";
            case "HomePod system audio output is not supported on Android 9.": return "Android 9 не поддерживает вывод системного звука на HomePod.";
            case "Open the receiver, then select this TV from your Apple device.": return "Откройте приёмник и выберите этот телевизор на устройстве Apple.";
            case "Could not open AirPlay Receiver. Check its installation on this TV.": return "Не удалось открыть приёмник AirPlay. Проверьте его установку на этом телевизоре.";
            case "AirPlay Receiver is not installed. Install it on this TV, then return here.": return "Приёмник AirPlay не установлен. Установите его на этот телевизор и вернитесь сюда.";
            case "HDMI Inputs": return "Входы HDMI";
            case "Inputs": return "Входы";
            case "Shortcuts": return "Ярлыки";
            case "Weather": return "Погода";
            case "No weather data right now.": return "Сейчас нет данных о погоде.";
            case "Sunrise ": return "Восход ";
            case "Sunset ": return "Закат ";
            case "Crypto": return "Криптовалюты";
            case "24h change": return "изменение за 24 ч";
            case "For You": return "Для вас";
            case "← → to move · OK to drop · Back to cancel": return "← → — переместить · OK — оставить · Назад — отмена";
            case "Moving ": return "Перемещение: ";
            case ", position ": return ", место ";
            case "Couldn't open ": return "Не удалось открыть ";
            case " of ": return " из ";
            case ", tab ": return ", вкладка ";
            case " of 3": return " из 3";
            case "Dollar": return "Доллар";
            case "Series": return "Сериал";
            case "prices from ": return "котировки за ";
            case "Starting…": return "Запуск…";
            case "Original": return "Заводской";
            case "Home Screen, ": return "Главный экран, ";
            case "Screensaver, preview": return "Заставка, предпросмотр";
            case "no ads, with the new screensaver": return "без рекламы, с новой заставкой";
            case "the TV's factory home screen": return "заводской главный экран телевизора";
            case ". In use": return ". Используется";
            case " of 2": return " из 2";
            case "Your TV's home screen and screensaver": return "Главный экран и заставка телевизора";
            case "Home Screen": return "Главный экран";
            case "Screensaver": return "Заставка";
            case "Preview": return "Предпросмотр";
            case "On the TV: tick “Always allow” and press OK.": return "На телевизоре отметьте «Всегда разрешать» и нажмите OK.";
            case "RECOMMENDATIONS": return "РЕКОМЕНДАЦИИ";
            case "No TMDB key: see the README (config.properties).": return "Нет ключа TMDB: см. README (config.properties).";
            case "Error: ": return "Ошибка: ";
            case "Fetching titles…": return "Загрузка рекомендаций…";
            case "This product uses the TMDB API but is not endorsed or certified by TMDB.": return "Продукт использует API TMDB, но не одобрен и не сертифицирован TMDB.";
            case "COMPARISON": return "СРАВНЕНИЕ";
            case "Home button": return "Кнопка «Домой»";
            case "Free memory": return "Свободная память";
            case "Home screen memory": return "Память главного экрана";
            case "NOTICES": return "УВЕДОМЛЕНИЯ";
            case "None yet.": return "Пока нет.";
            case "No ads, with the new screensaver": return "Без рекламы, с новой заставкой";
            case "The TV's factory home screen": return "Заводской главный экран телевизора";
            case "Switching takes a few seconds; the screen blinks while it measures.": return "Переключение занимает несколько секунд; во время замеров экран мигает.";
            case "now": return "сейчас";
            case "Measuring the original home screen…": return "Замер заводского главного экрана…";
            case "Switching the home screen and screensaver…": return "Переключение главного экрана и заставки…";
            case "Android didn't accept the new home screen": return "Android не принял новый главный экран";
            case "Turning off apps (": return "Отключение приложений (";
            case "Measuring performance…": return "Замер производительности…";
            case "Couldn't turn on the home screen: ": return "Не удалось включить главный экран: ";
            case "Turning the apps back on…": return "Включение приложений…";
            case "Turning apps back on (": return "Включение приложений (";
            case "Restoring the original home screen…": return "Восстановление заводского главного экрана…";
            case "Original mode: the factory home screen, screensaver and apps are back.": return "Заводской режим: восстановлены главный экран, заставка и приложения.";
            case "the original home screen": return "заводской главный экран";
            case "Original mode, but I couldn't turn back on: ": return "Заводской режим, но не удалось включить: ";
            case "Couldn't go back to the original: ": return "Не удалось восстановить заводской режим: ";
            case "The home screen crashed 3 times in a row; I switched back to the original one.": return "Главный экран аварийно завершился 3 раза подряд; восстановлен заводской главный экран.";
            case "the home screen": return "главный экран";
            case "the screensaver": return "заставка";
            case "Couldn't check the home screen: ": return "Не удалось проверить главный экран: ";
            case "The TV update turned back on ": return "После обновления телевизора снова включены: ";
            case "Turned off what was missing: ": return "Дополнительно отключены: ";
            case "The TV was updated; your home screen is still on.": return "Телевизор обновлён; главный экран по-прежнему включён.";
            case "Connecting…": return "Подключение…";
            case "Accept “Allow USB debugging?” on the TV (tick “Always allow”).": return "На телевизоре разрешите отладку по USB и отметьте «Всегда разрешать».";
            case "Language": return "Язык";
            case "Temperature": return "Температура";
            case "Theme": return "Тема";
            case "Automatic": return "Автоматически";
            case "Light": return "Светлая";
            case "Dark": return "Тёмная";
            case "Weather widget": return "Виджет погоды";
            // What TalkBack reads on the drawer's weather card (#47)
            case ". High ": return ". Максимум ";
            case ", low ": return ", минимум ";
            case " Next hours: ": return " Ближайшие часы: ";
            case " Sunrise ": return " Восход ";
            case " Sunset ": return " Закат ";
            case "City": return "Город";
            case "Searching…": return "Поиск…";
            case "Type the city's name": return "Введите название города";
            case "Search": return "Найти";
            case "Cancel": return "Отмена";
            case "No city with that name.": return "Город с таким названием не найден.";
            case "Couldn't search now: ": return "Не удалось выполнить поиск: ";
            case "Quotes widget": return "Виджет котировок";
            case "On": return "Вкл.";
            case "Off": return "Выкл.";
            case "Shelf": return "Избранные приложения";
            case "on the shelf, place ": return "в избранном, позиция ";
            case "not on the shelf": return "не в избранном";
            case "Remote Buttons": return "Кнопки пульта";
            case "Netflix button": return "Кнопка Netflix";
            case "YouTube button": return "Кнопка YouTube";
            case "Prime Video button": return "Кнопка Prime Video";
            case "TCL Channel button": return "Кнопка TCL Channel";
            case "Nothing": return "Ничего";
            case "Not installed": return "Не установлено";
            case ", selected": return ", выбрано";
            case "OK or the arrows change the value.": return "Нажмите OK или стрелки, чтобы изменить значение.";
            case "OK picks, Back cancels.": return "OK — выбрать, «Назад» — отменить.";
            case "Disney and Globoplay: the TV itself controls those buttons.": return "Кнопками Disney и Globoplay управляет сам телевизор.";
            case "YouTube, Prime Video and Google Play: the TV itself controls those buttons.": return "Кнопками YouTube, Prime Video и Google Play управляет сам телевизор.";
            case "These are the TCL remote's buttons: on this TV, the buttons work as they always did.": return "Это кнопки пульта TCL: на этом телевизоре они работают как прежде.";
            case "Explore": return "Обзор";
            case "Movies": return "Фильмы";
            case "Shows": return "Сериалы";
            case "Clear": return "Ясно";
            case "Mostly Sunny": return "Малооблачно";
            case "Mostly Clear": return "Малооблачно";
            case "Partly Cloudy": return "Переменная облачность";
            case "Cloudy": return "Облачно";
            case "Fog": return "Туман";
            case "Drizzle": return "Морось";
            case "Heavy Rain": return "Сильный дождь";
            case "Snow": return "Снег";
            case "Thunderstorms": return "Гроза";
            case "Rain": return "Дождь";
            case "On · ": return "В избранном · ";
            case "OK adds or removes · ← → change the order": return "OK — добавить или убрать · ← → — изменить порядок";
            case "Couldn't open TV Settings": return "Не удалось открыть настройки телевизора";
            case "THIS MORNING": return "ЭТИМ УТРОМ";
            case "THIS AFTERNOON": return "СЕГОДНЯ ДНЁМ";
            case "TONIGHT": return "СЕГОДНЯ ВЕЧЕРОМ";
            case "Action & Adventure": return "Боевик и приключения";
            case "Sci-Fi & Fantasy": return "Фантастика и фэнтези";
            case "War & Politics": return "Война и политика";
            case "Kids": return "Для детей";
            case "News": return "Новости";
            case "Soap": return "Мыльная опера";
            case "Talk": return "Ток-шоу";
            case "": return "";
            case "The home screen ran into a problem.\nGoing back to the TV's original home screen…": return "Ошибка главного экрана.\nВозвращаемся к заводскому главному экрану…";
            case "debugging is off on the TV (port 5555 closed)": return "отладка на телевизоре выключена (порт 5555 закрыт)";
            case "debugging permission wasn't accepted on the TV": return "на телевизоре не разрешена отладка";
            case "the TV's ADB didn't respond": return "ADB телевизора не ответил";
            case "the TV asked for TLS (wireless debugging); use USB debugging": return "телевизор запросил TLS (беспроводная отладка); используйте отладку по USB";
            case "unexpected reply from ADB": return "неожиданный ответ ADB";
            case "the TV rejected the app's key": return "телевизор отклонил ключ приложения";
            case "ADB refused the command": return "ADB отклонил команду";
            case "invalid ADB message": return "некорректное сообщение ADB";
            case "ADB message too large": return "слишком большое сообщение ADB";
            case "the TV's first setup saved the Wi-Fi, and turning it off would erase those networks. "
                    + "In Settings → Network & Internet, forget the network and connect again (networks out of range don't "
                    + "show there: Claude Code can remove them from your computer), then try once more.":
                return "Сети Wi-Fi сохранены приложением первоначальной настройки телевизора. Его отключение удалит эти сети. "
                        + "В разделе «Настройки → Сеть и Интернет» забудьте сеть и подключитесь заново, затем повторите попытку. "
                        + "Недоступные сети там не отображаются: Claude Code может удалить их с компьютера.";
            case "Connection, clock and appearance": return "Сеть, часы и оформление";
            case "Mirror your Apple device": return "Трансляция с устройства Apple";
            case "Receiver": return "Приёмник";
            case "Device status": return "Состояние устройства";
            case "Edit shortcuts": return "Изменить ярлыки";
            case "Bottom drawer shortcuts": return "Ярлыки нижней панели";
            case "Limit of 6 shortcuts. Remove one before adding another.": return "Не более 6 ярлыков. Сначала удалите один.";
            case "OK adds or removes. Left/right reorders selected shortcuts.": return "OK добавляет или удаляет. ← → меняет порядок выбранных ярлыков.";
            case "The app shelf is edited separately in Settings → Shelf.": return "Панель приложений меняется отдельно: Настройки → Панель.";
            case "Installed": return "Установлен";
            case "AirPlay connection help": return "Помощь с подключением AirPlay";
            case "Connect and mirror": return "Подключение и трансляция экрана";
            case "Network and AirPlay connection": return "Сеть и подключение AirPlay";
            case "AirPlay receiver": return "Приёмник AirPlay";
            case "Wi-Fi settings": return "Настройки Wi-Fi";
            case "This TV is offline. Open Wi-Fi settings to connect.": return "Телевизор не в сети. Откройте настройки Wi-Fi.";
            case "Check this TV's connection in Wi-Fi settings.": return "Проверьте подключение телевизора в настройках Wi-Fi.";
            case "1. Connect your Apple device to the same Wi-Fi network.\n2. Open the receiver and check that it is started.\n3. On iPhone, iPad or Mac: Control Center → Screen Mirroring → select the receiver name.\n4. If asked for a PIN, enter the code shown on this TV.\n\nIf it is missing: check guest Wi-Fi, VPN and device isolation on the router.": return "1. Подключите устройство Apple к той же сети Wi-Fi.\n2. Откройте приёмник и убедитесь, что он запущен.\n3. iPhone, iPad или Mac: Пункт управления → Повтор экрана → выберите приёмник.\n4. Если требуется PIN, введите код с экрана телевизора.\n\nЕсли приёмника нет: проверьте гостевую сеть, VPN и изоляцию устройств на роутере.";
            case "If AirPlay is missing, open connection help. The receiver app shows whether it is running.": return "Если AirPlay не виден, откройте помощь. Состояние приёмника показано в его приложении.";
            case "Could not open Wi-Fi settings.": return "Не удалось открыть настройки Wi-Fi.";
            case "Open": return "Открыть";
            case "App info": return "О приложении";
            case "Remove from shortcuts": return "Убрать из ярлыков";
            case "Add to shortcuts": return "Добавить в ярлыки";
            case "Move on shelf": return "Переместить на панели";
            case "Edit home screen": return "Изменить главный экран";
            case "Choose a section": return "Выберите раздел";
            case "Home screen apps": return "Приложения главного экрана";
            case "Other apps": return "Другие приложения";
            case "Fixed": return "Закреплено";
            case "Only built-in functions can be added. All Apps stays in the first position.": return "Можно добавить только встроенные функции. Все приложения всегда на первом месте.";
            case "In each section, hold OK and choose Move on shelf. OK confirms; Back cancels.": return "В каждом разделе удерживайте OK и выберите Переместить на панели. OK сохраняет; Назад отменяет.";
            case "Arrows to move · OK confirms · Back cancels": return "Стрелки перемещают · OK сохраняет · Назад отменяет";
            default:
                // Notices are saved in the language in use when they were generated.
                String[] prefixes = {"Couldn't check the home screen: ", "Couldn't turn on the home screen: ",
                        "Couldn't go back to the original: ", "Original mode, but I couldn't turn back on: "};
                for (String p : prefixes) if (english.startsWith(p)) return text(p) + text(english.substring(p.length()));
                String shelfPrefix = "OK adds or removes (up to ";
                String shelfSuffix = "). ← → change the order.";
                if (english.startsWith(shelfPrefix) && english.endsWith(shelfSuffix)) {
                    String n = english.substring(shelfPrefix.length(), english.length() - shelfSuffix.length());
                    return "OK — добавить или убрать (до " + n + "). ← → — изменить порядок.";
                }
                String prefix = "Home screen is on: new home screen and screensaver, ";
                if (english.startsWith(prefix) && english.endsWith(" apps turned off.")) {
                    String n = english.substring(prefix.length(), english.length() - " apps turned off.".length());
                    return "Главный экран включён: новый главный экран и заставка; отключено приложений: " + n + ".";
                }
                if (english.startsWith("On · ") && english.endsWith(" titles from the TV's apps, 3 new ones every hour.")) {
                    String n = english.substring(5, english.length() - " titles from the TV's apps, 3 new ones every hour.".length());
                    return "Вкл. · Рекомендаций из приложений телевизора: " + n + "; 3 новых каждый час.";
                }
                if (english.endsWith(" min ago")) return english.substring(0, english.length() - 8) + " мин назад";
                if (english.endsWith(" h ago")) return english.substring(0, english.length() - 6) + " ч назад";
                if (english.endsWith(" days ago")) {
                    try { return count(Long.parseLong(english.substring(0, english.length() - 9)), "день", "дня", "дней") + " назад"; }
                    catch (NumberFormatException ignored) { return english; }
                }
                return english;
        }
    }
}

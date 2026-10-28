# PureTV

![PureTV](media/hero.svg)

**Uygulamalarınıza odaklanan sade bir TV ana ekranı.**

[English / ana sayfa](../README.md) · [APK indir](https://github.com/Heymez0/PureTV/releases/latest/download/PureTV.apk) · [Ayrıntılı kurulum](INSTALL.md) · [Kaynak derleme](DEVELOPMENT.md)

PureTV; Android TV, Google TV ve Android kutu/stick cihazlar için hafif bir başlatıcıdır.
LG webOS ve Samsung Tizen için ayrı web sürümleri bulunur. **Gerçek cihaz referansı Android TV 9 kullanan Mi Stick'tir.**
Ayrı Google TV yazılımı testi kayıtlı değildir. LG/Samsung sürümleri deneysel durumdadır ve gerçek TV donanımında test edilmemiştir.

## Özellikler

- Saat, son izlenenler veya son izlenenler ve önerilerin birlikte bulunduğu ana ekran seçenekleri.
- Ana uygulama rafı, kısayollar ve aşağı kaydırarak erişilen diğer uygulamalar.
- Uzun basma menüsü, raf içinde taşıma; uygulamayı gizleme, kaldırma ve zorla durdurma.
- Açık/koyu tema, yağış olasılığıyla hava durumu, 12/24 saat; koyu ekran koruyucu ya da TV'deki ve USB'deki kendi
  fotoğraflarınızla ekran koruyucu.
- Kompakt hızlı ayarlar, isteğe bağlı hafif mod, tek tuşla bellek boşaltma, açıldığında alınan RAM/depolama bilgisi.
- Ayar yedekleme/geri yükleme, seçilen uygulamalar için basit PIN kilidi ve özel ana ekran modunda isteğe bağlı sesli
  asistan kapatma.
- GitHub sürümlerinden güncelleme: bildirim ya da otomatik kurulum; yalnızca sizinkiyle aynı imzalı daha yeni sürümler.
- Kutu/stick ve TV profilleri; cihazın sunduğu TV girişleri, desteklenen kumanda tuşları ve TV açılınca açılacak bir
  uygulama, Canlı TV ya da giriş.
- İsteğe bağlı, ayrı AirPlay alıcısı: PureTV'den doğrulanarak kurulur, yalnız açtığınızda çalışır ve hızlı panelden yönetilir.
- 24 arayüz diline ait çeviri katalogları.

Son izlenenler ve öneriler, kurulu uygulamaların Android ile paylaştığı içeriklere bağlıdır.
Web sürümündeki isteğe bağlı katalog kartları kendi TMDB anahtarınızı kullanır.
Android'e özgü içerik sağlayıcıları, sistem ayarları ve AirPlay kontrolü LG/Samsung sürümlerinde bulunmaz.

## Önizleme

Görseller gerçek Mi Stick ekran görüntüleridir; arayüz dili İngilizcedir.
Öneri afişlerinin dili, içeriği sağlayan uygulamaya bağlıdır. İzleme geçmişi gösterilmez.

### Son izlenenler ve öneriler

![Son izlenenler ve öneriler ana ekranı](media/recommendations-en.png)

### Saatli ana ekran

![Saatli ana ekran](media/home-en.png)

| Hızlı ayarlar | Kısayollar | Ayarlar |
| --- | --- | --- |
| ![Hızlı ayarlar](media/quick-settings-en.png) | ![Kısayollar](media/shortcuts-en.png) | ![Ayarlar](media/settings-en.png) |

## Kurulum

### Android TV / Google TV: APK indir

**[PureTV.apk indir](https://github.com/Heymez0/PureTV/releases/latest/download/PureTV.apk)** · [Sürüm ve SHA256 doğrulaması](https://github.com/Heymez0/PureTV/releases/latest)

Bu, Android 9 ve üzeri için imzalı v1.1 sürümüdür. **PureTV.apk** dosyasını indirin;
GitHub'ın otomatik oluşturduğu Source code ZIP dosyaları geliştiriciler içindir.

**v1 kullanıyorsanız:** v1.1 yeni bir anahtarla imzalandı. Önce ayarlarınızı yedekleyin (Sistem ve hakkında → Ayarları
yedekle), PureTV v1'i (ve PureTV AirPlay'i) kaldırın, sonra v1.1'i kurup yedeği geri yükleyin. Sonraki sürümler
doğrudan, PureTV'nin Güncellemeler ekranından da güncellenir.

**USB ile:**

1. APK'yı bilgisayarınıza indirin ve USB belleğe kopyalayın.
2. USB belleği TV/kutuya bağlayın.
3. TV'deki dosya yöneticisiyle `PureTV.apk` dosyasını açın.
4. Android isterse bu dosya yöneticisine uygulama yükleme izni verin ve **Yükle**'yi seçin.
5. PureTV'yi uygulama listesinden açıp karşılama adımlarını tamamlayın.

USB depolama bağlayamadığınız sticklerde mevcut dosya aktarım uygulamanızla APK'yı TV'ye gönderip açabilir veya ADB kullanabilirsiniz.

**ADB ile:** Bilgisayarda Android platform-tools kurulu olmalı; TV'nin geliştirici/ADB erişimi açık ve iki cihaz aynı ağda olmalıdır.
APK'nın bulunduğu klasörde terminal açın. `TV_IP` yerine TV'nin yerel IP adresini yazın:

```sh
adb connect TV_IP:5555
adb install -r PureTV.apk
```

TV'de görünen bağlantı iznini onaylayın. Bazı cihazlarda farklı port veya kablosuz eşleştirme gerekir;
cihazın kendi eşleştirme yöntemini kullanın. [Android adımlarının tamamı](INSTALL.md#android-tv--google-tv).

### Easy to install: Claude Code veya Codex

Bilgisayarınızda ilgili araç kurulu ve hesabınız açıkken aşağıdaki **tek satırlık komutlardan birini** çalıştırın.
Asistan TV platformunuzu ve eksik cihaz bilgilerini sorar, uygun kurulum yolunu izler.
TV'deki geliştirici modu, bağlantı izni ve gerekirse imzalama adımlarını sizinle birlikte tamamlar.

**Claude Code**

```sh
claude "Install PureTV on my TV from https://github.com/Heymez0/PureTV. Read the repository AGENTS.md and docs/INSTALL.md, identify my TV platform, ask only for missing device details, then follow the matching installation steps and preserve the original HOME recovery."
```

**Codex**

```sh
codex "Install PureTV on my TV from https://github.com/Heymez0/PureTV. Read the repository AGENTS.md and docs/INSTALL.md, identify my TV platform, ask only for missing device details, then follow the matching installation steps and preserve the original HOME recovery."
```

### İlk açılış

1. Karşılama ekranında **Box/stick** veya **TV** seçin.
2. Açıklanan değişiklikleri okuyun ve TV'de gereken ADB iznini verin.
3. Ana ekrana geçişi TV üzerinden tamamlayın.

Daha sonra cihaz profili, **All settings → Home screen → Device mode** yolundan değişebilir.
Stickte TV profilini seçmek fiziksel HDMI girişi oluşturmaz. Girişler, TV'nin desteklediği API'lere bağlıdır.

### LG webOS — deneysel

APK yerine **IPK** kullanır. Python 3, OpenSSH ve TV'de LG Developer Mode gerekir.

1. GitHub'da **Code → Download ZIP** ile kaynakları indirin ve açın.
2. TV'de Developer Mode hesabınıza giriş yapın; Dev Mode Status ve yeniden başlatma sonrasında Key Server'ı açın.
3. Kaynakların ana klasöründe şu komutları çalıştırın; `TV_IP` yerine TV adresini yazın:

```sh
python webos/build.py --generic
python webos/install.py TV_IP
```

4. TV'nin gösterdiği parolayı kendi terminalinize girin. Developer Mode süresi dolmadan **EXTEND** kullanın.

[Ayrıntılı LG kurulumu](INSTALL.md#lg-webos). Üreticinin HOME ekranı kullanılabilir kalır.

### Samsung Tizen — deneysel

APK yerine TV sertifikanızla imzalanmış **WGT** kullanır. Python 3 ve Tizen Studio imzalama araçları gerekir.

1. Kaynak ZIP'i indirip açın.
2. TV'de Apps ekranında **1 2 3 4 5** tuşlarına basın. Developer Mode'u açın, Host PC IP'ye bilgisayar adresini girin ve TV'yi yeniden başlatın.
3. Tizen Studio'da TV için sertifika profili oluşturun; hesap/DUID/cihaz izinlerini tamamlayın.
4. Kaynak klasöründe `YOUR_PROFILE` ve `TV_IP` değerlerini kendinize göre değiştirerek çalıştırın:

```sh
python tizen/build.py --generic --sign YOUR_PROFILE
python tizen/install.py TV_IP
```

İmzasız WGT yalnızca paket incelemesi içindir. Güncellemelerde aynı yazar sertifikasını kullanın.
[Ayrıntılı Samsung kurulumu](INSTALL.md#samsung-tizen).

## Güncelleme ve eski ana ekrana dönüş

PureTV'nin paket adı `io.puretv`'dir. Önceki paket kimliklerinden ayrı kurulur; ayarlar otomatik aktarılmaz.
Eski bir başlatıcıdan geçerken önce onun **Original** seçeneğiyle üreticinin ana ekranını geri yükleyin.
Geri dönüş tamamlanana kadar eski uygulamanın verilerini koruyun.

PureTV içinden üreticinin ana ekranına dönmek için:
**All settings → System and about → Default home screen → Original**.
Etkin ana ekranı kaldırmadan önce bu işlemi tamamlayın. Güncellemeler aynı imza anahtarını gerektirir.
[Kimlik ve güncelleme ayrıntıları](INSTALL.md#identity-and-upgrades).

## Geliştirme ve lisanslar

Android kaynaklarını derlemek için Python 3.10+, JDK 17 ve Android SDK 34 gerekir.
LG/Samsung paketleri Python ile hazırlanır; Samsung kurulumu ayrıca imza profili ister.
[Derleme ve testler](DEVELOPMENT.md).

Ana sayfa İngilizce olan [README.md](../README.md)'dir; bu dosya ayrı Türkçe okuyucu rehberidir.
PureTV katkıları [MIT](../LICENSE) lisanslıdır. Dahil edilen bileşenlerin [lisans bildirimleri](licenses/NOTICE.md) korunur.
İsteğe bağlı AirPlay alıcısı ayrı GPL-3.0 uygulamasıdır; [alıcı rehberine](../tools/airplay/README.md) bakın.
Uygulama adları, logolar ve içerik afişleri sahiplerine aittir.

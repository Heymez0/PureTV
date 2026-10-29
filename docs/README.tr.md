# PureTV

![PureTV](media/hero.svg)

**Uygulamalarınıza odaklanan sade bir TV ana ekranı.**

[English / ana sayfa](../README.md) · [APK indir](https://github.com/Heymez0/PureTV/releases/latest/download/PureTV.apk) · [Ayrıntılı kurulum](INSTALL.md) · [Kaynak derleme](DEVELOPMENT.md)

**Güncel sürüm: [PureTV v1.1](https://github.com/Heymez0/PureTV/releases/tag/v1.1)** · Android 9+

PureTV; Android TV, Google TV ve Android TV kutuları/stickleri için hafif bir ana ekrandır (başlatıcı).
LG webOS ve Samsung Tizen için ayrı web sürümleri vardır. **Testlerin yapıldığı gerçek cihaz, Android TV 9 kullanan
Xiaomi Mi Stick'tir.** Ayrı bir Google TV yazılımında yapılmış test kaydı yoktur. LG ve Samsung sürümleri deneyseldir,
gerçek bir TV'de denenmemiştir.

Menü adları bu rehberde PureTV'nin Türkçe arayüzündeki gibi yazılmıştır; İngilizce arayüzdeki karşılıkları parantez
içindedir.

## Özellikler

- **Ana ekranınızı seçin:** yalnız saat, son izlenenler ya da son izlenenler ve öneriler bir arada.
- **Uygulamalar elinizin altında:** ayrı uygulama ve kısayol rafları, aşağıda diğer uygulamalar. OK'e basılı tutunca
  açılan menüden rafı düzenleyebilir, uygulamayı gizleyebilir, kaldırabilir ya da zorla durdurabilirsiniz.
- **Size göre:** açık/koyu tema, yağış olasılığıyla hava durumu, 12/24 saat. Siyah ekran koruyucu ya da TV'deki ve
  USB bellekteki kendi fotoğraflarınızla ekran koruyucu; saati ekran yanmasına karşı yavaşça kayar.
- **Kontrol sizde:** sade hızlı ayarlar; bellek için **Boşalt** düğmesi ve **uyku zamanlayıcısı**. İsteğe bağlı hafif
  mod ve otomatik bellek boşaltma, istediğinizde RAM/depolama bilgisi, ayarları yedekleme ve geri yükleme, seçtiğiniz
  uygulamalar için basit bir PIN kilidi.
- **Güncellemeler:** bu deponun GitHub sürümlerinden; yalnız bildirim ya da otomatik kurulum. Yalnızca kurulu sürümle
  aynı anahtarla imzalanmış, daha yeni sürümler kurulur.
- **TV'nizi kullanın:** kutu/stick ve televizyon modları, TV'nin sunduğu girişler, kumanda tuşu ayarları ve TV açılınca
  doğrudan açılacak bir uygulama, Canlı TV ya da giriş. Özel ana ekran modunda Google Asistan isteğe bağlı kapatılabilir.
- **AirPlay:** isteğe bağlı, ayrı bir alıcı uygulaması. PureTV'den doğrulanarak kurulur, yalnız siz açınca çalışır ve
  hızlı ayarlardan açılıp kapatılır.
- **24 arayüz dili.**

Son izlenenler ve öneriler, kurulu uygulamaların Android ile paylaştığı içeriğe bağlıdır. Web sürümündeki isteğe bağlı
katalog kartları için kendi TMDB anahtarınız gerekir. Android'e özgü içerik kaynakları, sistem ayarları ve AirPlay
kontrolü LG/Samsung sürümlerinde yoktur.

### v1.1'deki yenilikler

- **PureTV içinden güncelleme** (Sistem ve hakkında → Güncellemeler); Mi Stick'te baştan sona denendi.
- **Hızlı ayarlar:** bellek boşaltma ve 30–120 dakikalık uyku zamanlayıcısı.
- **Uygulama kilidi, uygulama gizleme, TV açılınca başlatma, kaldırma / zorla durdurma,** fotoğraflı ekran koruyucu ve
  otomatik bellek boşaltma.
- **AirPlay alıcısı 0.0.36-puretv.2:** yalnız gerektiğinde çalışır, PureTV'nin açık/koyu görünümüne uyar.
- **Güvenlik ve düzeltmeler:** başka bir uygulama artık sahte kurulum sonucu gönderemez; ayarlar geri yüklendikten
  sonra uygulama içerikleri görünür; Android 9–11'de ana ekran daha akıcı kayar.
- **Yeni imza anahtarı:** v1 kullananlar, v1.1'i kurmadan önce v1'i bir kez kaldırmalıdır (aşağıdaki Kurulum
  bölümüne bakın).

[Tüm değişiklikler](CHANGELOG.md)

## Önizleme

Görseller, PureTV v1.1'in Mi Stick'te alınmış gerçek ekran görüntüleridir; arayüz dili İngilizcedir. Öneri
afişlerinin dili, içeriği sağlayan uygulamaya bağlıdır. İzleme geçmişi gösterilmez.

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

Bu, Android 9 ve üzeri için imzalı v1.1 sürümüdür. **PureTV.apk** dosyasını indirin; GitHub'ın kendiliğinden
eklediği "Source code" ZIP dosyaları geliştiriciler içindir.

**v1 kullanıyorsanız:** v1.1 yeni bir anahtarla imzalandığı için Android onu v1'in üzerine kurmaz.

1. PureTV v1'de ayarlarınızı yedekleyin: **Tüm ayarlar → Sistem ve hakkında → Ayarları yedekle** (All settings →
   System and about → Back up settings). Özel ana ekran modundaysanız önce **Varsayılan ana ekran → Orijinal**'i seçin.
2. PureTV v1'i (ve kuruluysa PureTV AirPlay'i) kaldırın.
3. v1.1'i aşağıdaki gibi kurun, ardından **Ayarları geri yükle**'yi (Restore settings) seçin.

v1.1'den sonraki sürümler doğrudan güncellenir; PureTV'nin **Güncellemeler** ekranından da kurulabilir.

**USB ile:**

1. APK'yı bilgisayarınıza indirin ve USB belleğe kopyalayın.
2. USB belleği TV/kutuya bağlayın.
3. TV'deki dosya yöneticisiyle `PureTV.apk` dosyasını açın.
4. Android isterse bu dosya yöneticisine uygulama yükleme izni verin ve **Yükle**'yi seçin.
5. PureTV'yi uygulama listesinden açıp karşılama adımlarını tamamlayın.

USB bellek takılamayan sticklerde APK'yı kullandığınız bir dosya aktarma uygulamasıyla TV'ye gönderip açabilir ya da
ADB kullanabilirsiniz.

**ADB ile:** Bilgisayarda Android platform-tools kurulu olmalı; TV'nin geliştirici/ADB erişimi açık ve iki cihaz aynı ağda olmalıdır.
APK'nın bulunduğu klasörde terminal açın. `TV_IP` yerine TV'nin yerel IP adresini yazın:

```sh
adb connect TV_IP:5555
adb install -r PureTV.apk
```

TV'de görünen bağlantı iznini onaylayın. Bazı cihazlarda farklı port veya kablosuz eşleştirme gerekir;
cihazın kendi eşleştirme yöntemini kullanın. [Android adımlarının tamamı](INSTALL.md#android-tv--google-tv).

### Kolay kurulum: Claude Code veya Codex

Bilgisayarınızda ilgili araç kuruluysa ve hesabınıza giriş yaptıysanız, aşağıdaki **tek satırlık komutlardan birini**
çalıştırın. Asistan TV platformunuzu ve eksik cihaz bilgilerini sorar, uygun kurulum yolunu izler. TV'deki geliştirici
modu, bağlantı izni ve gerekiyorsa imzalama adımlarını sizinle birlikte tamamlar.

**Claude Code**

```sh
claude "Install PureTV on my TV from https://github.com/Heymez0/PureTV. Read the repository AGENTS.md and docs/INSTALL.md, identify my TV platform, ask only for missing device details, then follow the matching installation steps and preserve the original HOME recovery."
```

**Codex**

```sh
codex "Install PureTV on my TV from https://github.com/Heymez0/PureTV. Read the repository AGENTS.md and docs/INSTALL.md, identify my TV platform, ask only for missing device details, then follow the matching installation steps and preserve the original HOME recovery."
```

### İlk açılış

1. Karşılama ekranında **TV kutusu / stick** ya da **Televizyon**'u seçin (TV box / stick, Television).
2. Açıklanan değişiklikleri okuyun ve TV'nin sorduğu USB hata ayıklama (ADB) iznini verin.
3. **PureTV'yi etkinleştir**'i (Enable PureTV) seçip ana ekrana geçişi tamamlayın.

Cihaz modunu sonradan **Tüm ayarlar → Ana ekran → Cihaz modu** (All settings → Home screen → Device mode) yolundan
değiştirebilirsiniz. Stick'te Televizyon modunu seçmek fiziksel bir HDMI girişi eklemez; girişler TV'nin sunduğu
olanaklara bağlıdır.

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

[Ayrıntılı LG kurulumu](INSTALL.md#lg-webos). Üreticinin kendi ana ekranı yerinde kalır.

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

PureTV'nin paket adı `io.puretv`'dir. Başka paket adlı eski başlatıcılardan ayrı kurulur ve onların ayarlarını kendiliğinden
almaz. Eski bir başlatıcıdan geçerken önce onun **Orijinal** (Original) seçeneğiyle üreticinin ana ekranını geri getirin
ve bu tamamlanana kadar eski uygulamayı kaldırmayın.

PureTV'den üreticinin ana ekranına dönmek için:
**Tüm ayarlar → Sistem ve hakkında → Varsayılan ana ekran → Orijinal**
(All settings → System and about → Default home screen → Original).
Etkin ana ekranı kaldırmadan önce bunu mutlaka yapın. Güncellemeler aynı imza anahtarını gerektirir; v1.1'den sonraki
sürümler doğrudan ve PureTV'nin Güncellemeler ekranından güncellenir.
[Kimlik ve güncelleme ayrıntıları](INSTALL.md#identity-and-upgrades).

## Geliştirme ve lisanslar

Android kaynaklarını derlemek için Python 3.10+, JDK 17 ve Android SDK 34 gerekir.
LG/Samsung paketleri Python ile hazırlanır; Samsung kurulumu ayrıca imza profili ister.
[Derleme ve testler](DEVELOPMENT.md).

Projenin ana sayfası İngilizce [README.md](../README.md) dosyasıdır; bu dosya Türkçe okuyucular için ayrı bir
rehberdir. PureTV'ye yapılan katkılar [MIT](../LICENSE) lisanslıdır; projeye dahil edilen bileşenlerin
[lisans bildirimleri](licenses/NOTICE.md) korunur. İsteğe bağlı AirPlay alıcısı, GPL-3.0 lisanslı ayrı bir
uygulamadır; [alıcı rehberine](../tools/airplay/README.md) bakın.
Uygulama adları, logolar ve içerik afişleri sahiplerine aittir.

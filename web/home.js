/*
 * The home screen for web-app TVs (LG webOS, Samsung Tizen), with the Android app's design and behavior (HomeView):
 *  - top: the "For You" title and the tabs (Explore, Movies, Shows); icon-only glass buttons at the right (Live TV,
 *    HDMI Inputs, Settings) and the compact weather at the end of the tab row;
 *  - three picks for this hour in the selected tab; the background is the first one's photo, blurred and almost white;
 *  - the glass shelf with the apps; ↓ opens the drawer (inputs, shortcuts, weather); ↑ or Back closes it;
 *  - a night look from sunset to sunrise; a screensaver after a few idle minutes.
 * The remote's arrows, OK and Back move the focus; a pointer (LG's Magic Remote) focuses what it points at.
 */
(function () {
  'use strict';

  function $(id) { return document.getElementById(id); }

  function el(tag, cls, parent, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    if (parent) parent.appendChild(e);
    return e;
  }

  function sym(name, parent, size) {
    var s = el('span', 'sym', parent);
    s.style.webkitMaskImage = 'url(symbols/' + name + '.png)';
    s.style.maskImage = 'url(symbols/' + name + '.png)';
    if (size) { s.style.width = size + 'px'; s.style.height = size + 'px'; }
    return s;
  }

  function hasClass(e, c) { return (' ' + e.className + ' ').indexOf(' ' + c + ' ') >= 0; }
  function setClass(e, c, on) {
    if (!e) return;
    if (on && !hasClass(e, c)) e.className = (e.className + ' ' + c).replace(/^\s+/, '');
    else if (!on && hasClass(e, c)) e.className = (' ' + e.className + ' ').replace(' ' + c + ' ', ' ').replace(/^\s+|\s+$/g, '');
  }

  var stage = $('stage'), body = document.body;

  /** The 1920 × 1080 stage fills the window (on the TV it already is 1920 × 1080). */
  function fit() {
    var s = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
    stage.style.webkitTransform = stage.style.transform = s === 1 ? '' : 'scale(' + s + ')';
  }

  // MARK: - Apps on the shelf

  /** Shelf apps in this order when installed, like the Android app: key, LG app id, name on the TV, brand gradient. */
  var SHELF = [
    ['netflix', /^netflix$/, /netflix/i, ['#0A0A0A', '#000000']],
    ['youtube', /youtube/, /^youtube$/i, ['#1C1C1E', '#09090A']],
    ['prime', /^amazon$/, /prime video|amazon/i, ['#2AA8FF', '#0A68D4']],
    ['disney', /disney/, /disney/i, ['#173584', '#08143F']],
    ['max', /hbo|wbd/, /^(hbo )?max$|hbo max/i, ['#5C30E8', '#221684']],
    ['appletv', /appletv|apple\.tv/, /apple tv/i, ['#1C1C1E', '#000000']],
    ['hulu', /^hulu/, /hulu/i, ['#1CE783', '#0B8F4E']],
    ['paramount', /paramount|cbs/, /paramount/i, ['#0064FF', '#002A86']],
    ['peacock', /peacock/, /peacock/i, ['#1C1C1E', '#000000']],
    ['nfl', /nfl/, /^nfl/i, ['#013369', '#00163D']],
    ['globoplay', /globoplay/, /globoplay/i, ['#FF8A1F', '#FF2D5A']],
    ['jellyfin', /jellyfin/i, /jellyfin/i, ['#AA5CC3', '#00A4DC']],
    ['spotify', /spotify/, /spotify/i, ['#1DB954', '#0F7A36']]
  ];
  // Apps shown at once, as on Android: 8 (today's smallest tiles, 178 wide); the shelf scrolls for the rest. The
  // built-in list keeps 8 too.
  var SHELF_VISIBLE = 8, FEW_APPS = 8;

  function shelfKey(app) {
    for (var i = 0; i < SHELF.length; i++) if (SHELF[i][1].test(app.id) || SHELF[i][2].test(app.title)) return SHELF[i];
    return null;
  }

  function darken(hex, f) {
    var m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
    if (!m) return '#161618';
    var n = parseInt(m[1], 16), r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    return 'rgb(' + Math.round(r * f) + ',' + Math.round(g * f) + ',' + Math.round(b * f) + ')';
  }

  /** A name that fits the tile on one line (the font shrinks for long names). */
  function wordFit(title, width, max) {
    return Math.max(18, Math.min(max, Math.floor(width * 0.8 / (Math.max(4, title.length) * 0.56))));
  }

  // MARK: - Logos

  // The TV's icons are small squares with their own background (white behind Netflix, black behind Max). Like the
  // Android app does with the TV art, the logo is lifted off it once: the background goes transparent and the rest is
  // cropped to the logo, which then fills the tile on the brand's color.
  var lifted = {};   // icon url -> {src, bg: [r, g, b], ink: luminance histogram} or false (keep the icon as it is)
  var LIFT_SIZE = 240;

  // sRGB (0-255) to linear light, computed once: lift() needs it for every pixel, and old TVs are slow at Math.pow.
  var LINEAR = [];
  for (var li = 0; li < 256; li++) LINEAR[li] = li <= 10 ? li / 255 / 12.92 : Math.pow((li / 255 + 0.055) / 1.055, 2.4);

  /** Relative luminance (0-1) of an sRGB color (whole numbers, as read from the canvas). */
  function luminance(r, g, b) {
    return 0.2126 * LINEAR[r] + 0.7152 * LINEAR[g] + 0.0722 * LINEAR[b];
  }

  function rgbOf(color) {
    var m = /^#?([0-9a-f]{6})$/i.exec(color || '');
    if (m) { var n = parseInt(m[1], 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
    m = /rgb\((\d+),\s*(\d+),\s*(\d+)\)/.exec(color || '');
    return m ? [+m[1], +m[2], +m[3]] : [0, 0, 0];
  }

  function lift(img) {
    var w = img.naturalWidth, h = img.naturalHeight;
    if (!w || !h) return false;
    var f = Math.min(1, LIFT_SIZE / Math.max(w, h));
    w = Math.max(1, Math.round(w * f));
    h = Math.max(1, Math.round(h * f));
    var canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    var g = canvas.getContext('2d');
    var data;
    try {
      g.drawImage(img, 0, 0, w, h);
      data = g.getImageData(0, 0, w, h);
    } catch (e) {
      return false;   // a TV that doesn't let the page read its icons
    }
    var px = data.data;
    // The background: the most common color a little inside the edges (rounded icons have transparent corners).
    var votes = {}, best = null, bestN = 0, inset = Math.round(Math.min(w, h) * 0.08);
    for (var s = 0; s < 64; s++) {
      var t = s % 16 / 16, side = s >> 4;
      var x = side < 2 ? Math.round(inset + t * (w - 2 * inset)) : side === 2 ? inset : w - 1 - inset;
      var y = side < 2 ? (side === 0 ? inset : h - 1 - inset) : Math.round(inset + t * (h - 2 * inset));
      var i = (y * w + x) * 4;
      var key = px[i + 3] < 128 ? 'clear' : (px[i] >> 4) + ',' + (px[i + 1] >> 4) + ',' + (px[i + 2] >> 4);
      votes[key] = (votes[key] || 0) + 1;
      if (votes[key] > bestN) { bestN = votes[key]; best = key === 'clear' ? null : [px[i], px[i + 1], px[i + 2]]; }
    }
    var minX = w, minY = h, maxX = -1, maxY = -1, ink = [0, 0, 0, 0, 0, 0, 0, 0], inkSum = 0;
    for (var p = 0; p < w * h; p++) {
      var j = p * 4, a = px[j + 3] / 255;
      if (best) {
        // color to alpha: how far each channel is from the background, as a share of how far it could go, so the
        // anti-aliased edges come out as the logo's color, partly clear, without a fringe of the background
        var k = 0, c;
        for (c = 0; c < 3; c++) {
          var v = px[j + c], o = best[c];
          k = Math.max(k, v > o ? (v - o) / Math.max(1, 255 - o) : (o - v) / Math.max(1, o));
        }
        k = Math.max(0, Math.min(1, (k - 0.1) / 0.9));   // compression noise stays clear
        if (k > 0 && k < 1) {
          for (c = 0; c < 3; c++) px[j + c] = Math.max(0, Math.min(255, best[c] + (px[j + c] - best[c]) / k));
        }
        a *= k;
      }
      px[j + 3] = Math.round(a * 255);
      if (a > 0.3) {
        var px0 = p % w, py0 = (p / w) | 0;
        if (px0 < minX) minX = px0;
        if (px0 > maxX) maxX = px0;
        if (py0 < minY) minY = py0;
        if (py0 > maxY) maxY = py0;
        ink[Math.min(7, Math.floor(Math.sqrt(luminance(px[j], px[j + 1], px[j + 2])) * 8))] += a;
        inkSum += a;
      }
    }
    // Nothing left, or nothing taken away (a picture rather than a logo on a background): keep the icon.
    if (maxX < 0 || (maxX - minX > w * 0.96 && maxY - minY > h * 0.96)) return false;
    var crop = document.createElement('canvas');
    crop.width = maxX - minX + 1;
    crop.height = maxY - minY + 1;
    g.putImageData(data, 0, 0);
    crop.getContext('2d').drawImage(canvas, minX, minY, crop.width, crop.height, 0, 0, crop.width, crop.height);
    for (var b = 0; b < 8; b++) ink[b] /= inkSum;
    try {
      return {src: crop.toDataURL('image/png'), bg: best, ink: ink};
    } catch (e2) {
      return false;
    }
  }

  /**
   * Share of the logo that hardly shows on a tile of this luminance (YouTube's black type on a dark tile). Each bin
   * of the histogram holds the ink whose square-root luminance falls in it.
   */
  function unseen(ink, tileLum) {
    var share = 0;
    for (var b = 0; b < 8; b++) {
      var l = Math.pow((b + 0.5) / 8, 2);
      if ((Math.max(l, tileLum) + 0.05) / (Math.min(l, tileLum) + 0.05) < 1.8) share += ink[b];
    }
    return share;
  }

  function paint(box, top, bottom) {
    box.style.background = '-webkit-linear-gradient(top, ' + top + ', ' + bottom + ')';
    box.style.background = 'linear-gradient(' + top + ', ' + bottom + ')';
  }

  /** A color that is clearly a hue (Crunchyroll's orange), not black, white or a gray. */
  function colorful(rgb) {
    return Math.max(rgb[0], rgb[1], rgb[2]) - Math.min(rgb[0], rgb[1], rgb[2]) >= 96;
  }

  /** own: the tile has the TV's color for the app, not one of SHELF's. */
  function showLogo(img, box, top, bottom, logo, own) {
    img.className = 'logo';
    img.src = logo.src;
    var a = rgbOf(top), z = rgbOf(bottom);
    var tileLum = (luminance(a[0], a[1], a[2]) + luminance(z[0], z[1], z[2])) / 2;
    // The tile takes the icon's background when the logo only reads on it, and when that background is the brand's
    // color: LG's bgColor isn't always the icon's (Crunchyroll's is black, on an orange icon).
    if (logo.bg && ((own && colorful(logo.bg)) || unseen(logo.ink, tileLum) > 0.35)) {
      var bg = 'rgb(' + logo.bg.join(',') + ')';
      var hex = '#' + ((1 << 24) | (logo.bg[0] << 16) | (logo.bg[1] << 8) | logo.bg[2]).toString(16).slice(1);
      paint(box, bg, darken(hex, 0.93));
    }
  }

  /** An app's face: its logo on its brand color (or its name, when the icon can't be read). */
  function face(app, box, wordSize) {
    var k = shelfKey(app);
    var top = k ? k[3][0] : app.color ? darken(app.color, 0.92) : '#2A2A2E';
    var bottom = k ? k[3][1] : app.color ? darken(app.color, 0.62) : '#161618';
    paint(box, top, bottom);
    var word = el('div', 'word', box, app.title);
    if (wordSize) word.style.fontSize = wordSize + 'px';
    if (app.icon) {
      var img = el('img', '', box), url = app.icon;
      word.style.visibility = 'hidden';
      img.onerror = function () { if (img.parentNode) img.parentNode.removeChild(img); word.style.visibility = ''; };
      if (lifted[url]) { showLogo(img, box, top, bottom, lifted[url], !k); return; }
      if (lifted[url] !== false) {
        img.onload = function () {
          img.onload = null;
          if (lifted[url] === undefined) lifted[url] = lift(img);
          if (lifted[url]) showLogo(img, box, top, bottom, lifted[url], !k);
        };
      }
      img.src = url;
    }
  }

  var apps = [], shelf = [], others = [], inputs = [];

  /** shelf_apps in config.properties: the shelf in that order, by id or name (part of it). */
  var WANTED = (CONFIG.shelfApps || []).map(function (s) { return s.toLowerCase(); });

  function pickShelf() {
    if (WANTED.length && Prefs.shelfConfig() !== WANTED.join(',')) {
      Prefs.set('shelf', null);
      Prefs.set('shelfConfig', WANTED.join(','));
    }
    var chosen = Prefs.shelf(), out = [];
    if (chosen) {
      for (var i = 0; i < chosen.length; i++) {
        for (var j = 0; j < apps.length; j++) if (apps[j].id === chosen[i]) out.push(apps[j]);
      }
    } else if (WANTED.length) {
      for (var w = 0; w < WANTED.length; w++) {
        for (var k = 0; k < apps.length; k++) {
          var id = apps[k].id.toLowerCase(), title = apps[k].title.toLowerCase();
          if ((id.indexOf(WANTED[w]) >= 0 || title.indexOf(WANTED[w]) >= 0) && out.indexOf(apps[k]) < 0) { out.push(apps[k]); break; }
        }
      }
      if (!out.length) out = apps.slice(0, FEW_APPS);
    } else {
      for (var s = 0; s < SHELF.length && out.length < FEW_APPS; s++) {
        for (var a = 0; a < apps.length; a++) {
          if ((SHELF[s][1].test(apps[a].id) || SHELF[s][2].test(apps[a].title)) && out.indexOf(apps[a]) < 0) { out.push(apps[a]); break; }
        }
      }
      if (!out.length) out = apps.slice(0, FEW_APPS);
    }
    var gone = Prefs.hiddenApps();
    shelf = out.filter(function (x) { return gone.indexOf(x.id) < 0; });
    others = apps.filter(function (x) { return shelf.indexOf(x) < 0 && gone.indexOf(x.id) < 0; });
  }

  /** After hiding or showing an app: the shelf, All Apps and the drawer again. */
  function refreshApps() {
    pickShelf();
    renderShelf();
    renderApps();
    st.shelf = Math.min(st.shelf, Math.max(0, shelf.length - 1));
    updateFocus();
  }

  // MARK: - Focus

  var Z = {TOP: 'top', CARDS: 'cards', SHELF: 'shelf', DRAWER: 'drawer', APPS: 'apps', SETTINGS: 'settings', EDIT: 'edit', LANG: 'lang', QUICK: 'quick', ACTIONS: 'actions', PICK: 'pick', PIN: 'pin'};
  var st = {zone: Z.SHELF, top: 0, tab: 0, card: 0, shelf: 0, drawer: 0, grid: 0, row: 0, edit: 0, lang: 0, cat: 0, nav: true, quick: 0, action: 0, moving: false, pick: 0};
  var cards = [];

  function sheetOpen() { return st.zone === Z.APPS || st.zone === Z.SETTINGS || st.zone === Z.EDIT || st.zone === Z.LANG || st.zone === Z.QUICK || st.zone === Z.ACTIONS || st.zone === Z.PICK || st.zone === Z.PIN; }

  function updateFocus() {
    var i, list;
    list = $('tabs').children;
    for (i = 0; i < list.length; i++) {
      setClass(list[i], 'on', i === st.tab);
      setClass(list[i], 'focus', st.zone === Z.TOP && st.top === i);
    }
    list = $('buttons').children;
    for (i = 0; i < list.length; i++) setClass(list[i], 'focus', st.zone === Z.TOP && st.top === 3 + i);
    var pillFocus = st.zone === Z.TOP && st.top === 6;
    if (hasClass($('wpill'), 'focus') !== pillFocus) { setClass($('wpill'), 'focus', pillFocus); renderWeatherPanel(); }
    if ($('wpanel').className === 'open' && !pillFocus) renderWeatherPanel();
    settingFoot();
    list = $('cards').children;
    for (i = 0; i < list.length; i++) setClass(list[i], 'focus', st.zone === Z.CARDS && st.card === i);
    list = $('tiles').children;
    for (i = 0; i < list.length; i++) setClass(list[i], 'focus', st.zone === Z.SHELF && st.shelf === i);
    setClass($('tiles'), 'on', st.zone === Z.SHELF);
    setClass($('platter'), 'on', st.zone === Z.SHELF);
    revealShelf();
    list = document.querySelectorAll('#drawer .key');
    for (i = 0; i < list.length; i++) setClass(list[i], 'focus', st.zone === Z.DRAWER && st.drawer === i);
    list = document.querySelectorAll('#apps .cell');
    for (i = 0; i < list.length; i++) setClass(list[i], 'focus', st.zone === Z.APPS && st.grid === i);
    var rows = Math.max(1, Math.ceil(others.length / 5)), r = Math.floor(st.grid / 5), first = Math.max(0, Math.min(rows - 3, r - 1));
    $('apps').lastChild.style.webkitTransform = $('apps').lastChild.style.transform = 'translateY(' + (-first * 239) + 'px)';
    focusRows($('settings'), st.row, st.zone === Z.SETTINGS && !st.nav);
    var cats = document.querySelectorAll('#settings .cat');
    for (i = 0; i < cats.length; i++) setClass(cats[i], 'focus', st.zone === Z.SETTINGS && st.nav && st.cat === i);
    focusRows($('shelfedit'), st.edit, st.zone === Z.EDIT);
    focusRows($('langpick'), st.lang, st.zone === Z.LANG);
    focusRows($('quick'), st.quick, st.zone === Z.QUICK);
    focusRows($('actions'), st.action, st.zone === Z.ACTIONS);
    focusRows($('picker'), st.pick, st.zone === Z.PICK && !(pickInput && document.activeElement === pickInput));
    var tl = $('tiles').children;
    for (i = 0; i < tl.length; i++) setClass(tl[i], 'moving', st.moving && st.shelf === i);
    if (langHint) langHint.textContent = (st.lang + 1) + ' / ' + languageChoices().length + '  ·  ' + tr('OK picks, Back cancels.');
    setClass(body, 'drawer-open', st.zone === Z.DRAWER);
  }

  function focusRows(sheet, index, on) {
    var rows = sheet.querySelectorAll('.row');
    for (var i = 0; i < rows.length; i++) setClass(rows[i], 'focus', on && i === index);
    var holder = sheet.querySelector('.rows');
    if (!holder) return;
    var visible = 7, first = Math.max(0, Math.min(rows.length - visible, index - 3));
    holder.style.webkitTransform = holder.style.transform = 'translateY(' + (-first * 78) + 'px)';
  }

  function nearest(nodes, x) {
    var best = 0, bestD = 1e9;
    for (var i = 0; i < nodes.length; i++) {
      var r = nodes[i].getBoundingClientRect(), d = Math.abs((r.left + r.right) / 2 - x);
      if (d < bestD) { bestD = d; best = i; }
    }
    return best;
  }

  function centerX(node) {
    if (!node) return 960;
    var r = node.getBoundingClientRect();
    return (r.left + r.right) / 2;
  }

  // MARK: - Top: title, tabs, buttons, weather

  var TAB_KIND = [null, 'movie', 'tv'];

  /** The greeting in the header pill, by the TV's clock (as HomeView's). */
  function greeting() {
    var h = new Date().getHours();
    return tr(h >= 5 && h < 12 ? 'Good morning' : h >= 12 && h < 18 ? 'Good afternoon' : h >= 18 && h < 22 ? 'Good evening' : 'Good night');
  }

  /** Without a TMDB key there is no content to browse: the clock layout, without tabs (Android's Clock only). */
  function contentTabs() { return !!(CONFIG.tmdbKey || '').trim(); }
  function firstTop() { return contentTabs() ? 0 : 3; }

  function renderTop() {
    $('title').textContent = '';
    setClass(body, 'no-content', !contentTabs());
    var tabs = $('tabs');
    tabs.innerHTML = '';
    var names = [tr('Explore'), tr('Movies'), tr('Shows')];
    for (var i = 0; i < 3; i++) {
      var t = el('button', 'tab', tabs, names[i]);
      hover(t, Z.TOP, i);
    }
    var buttons = $('buttons');
    buttons.innerHTML = '';
    // The pill: the greeting and the gold gear (Home Settings), at the right edge; the two circles before it.
    var pill = el('button', 'pill', buttons);
    el('span', 'greet', pill, greeting());
    sym('settings', el('span', 'gear', pill));
    var defs = [['tv_settings', tr('TV Settings')], ['hdmi', tr('Inputs')]];
    for (var b = 0; b < 2; b++) {
      var r = el('button', 'round', buttons);
      sym(defs[b][0], r);
      el('div', 'tip', r, defs[b][1]);
      hover(r, Z.TOP, 3 + b);
    }
    hover(pill, Z.TOP, 5);
    if (!$('wpill').onmouseover) hover($('wpill'), Z.TOP, 6);
    buttons.appendChild(pill);   // after the circles, so the order matches the focus order
    placeButtons();
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(placeButtons);
  }

  /** The circles sit just left of the pill, whose width depends on the greeting's language. */
  function placeButtons() {
    var pill = $('buttons').querySelector('.pill'), rounds = $('buttons').querySelectorAll('.round');
    if (!pill) return;
    var left = 1840 - pill.offsetWidth;
    for (var i = rounds.length - 1; i >= 0; i--) { left -= 18 + 68; rounds[i].style.left = left + 'px'; }
  }

  function renderHeaderWeather() {
    var q = $('weather-summary'), w = Data.weather;
    q.innerHTML = '';
    renderClock();   // the clock layout's weather line
    if (!Prefs.weather() || !w || !cards.length) return;   // as on Android: only beside the content tabs
    sym(Data.symbol(w.code, w.isDay), q, 20);
    el('span', '', q, Data.degrees(w.temp) + (Data.city ? ' · ' + Data.city : ''));
  }

  // MARK: - Picks and background

  function renderCards() {
    var list = Data.picks(TAB_KIND[st.tab]);
    var holder = $('cards');
    setClass(body, 'no-picks', !list.length);
    renderScene();
    var same = list.length === cards.length;
    for (var i = 0; same && i < list.length; i++) same = list[i].id === cards[i].id && list[i].backdrop === cards[i].backdrop && list[i].meta === cards[i].meta;
    if (same && holder.children.length === list.length) return;
    cards = list;
    holder.innerHTML = '';
    var rects = [[80, 1040], [1160, 320], [1520, 320]];
    for (var c = 0; c < list.length; c++) {
      var p = list[c], card = el('div', 'card', holder);
      card.style.left = rects[c][0] + 'px';
      card.style.width = rects[c][1] + 'px';
      var img = el('div', 'img', card);
      img.style.backgroundImage = 'url(' + Data.image(p.backdrop, c === 0 ? 'w1280' : 'w780') + ')';
      el('div', 'name', card, p.title);
      if (c === 0 && p.meta) el('div', 'meta', card, p.meta);
      hover(card, Z.CARDS, c);
    }
    st.card = Math.min(st.card, Math.max(0, list.length - 1));
    if (!list.length && st.zone === Z.CARDS) st.zone = Z.SHELF;
    backdrop(list.length ? Data.image(list[0].backdrop, 'w300') : '');
    updateFocus();
  }

  var bgUrl = null, bgFront = 'bgA';

  /** The first pick's photo as the background: drawn tiny and stretched, it's already as soft as a heavy blur. */
  function backdrop(url) {
    if (url === bgUrl) return;
    bgUrl = url;
    var next = bgFront === 'bgA' ? 'bgB' : 'bgA';
    if (!url) {
      setClass($('bgA'), 'on', false);
      setClass($('bgB'), 'on', false);
      return;
    }
    var img = new Image();
    img.onload = function () {
      if (bgUrl !== url) return;
      var canvas = $(next), ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      setClass(canvas, 'on', true);
      setClass($(bgFront), 'on', false);
      bgFront = next;
    };
    img.src = url;
  }

  // MARK: - Background photo (Settings → Appearance → Background photo), as Android's Scenes

  var sceneNow = null;

  /** The photo now: only in the clock layout (no picks), never with Classic colours. */
  function sceneWanted() {
    var now = Date.now();
    return hasClass(body, 'no-picks') ? Scenes.pick(Prefs.scene(), Data.isNight(now), now) : null;
  }

  /** Shows or hides the photo, its front cut-out and credit, and the clock's glass and weight. */
  function renderScene() {
    var s = sceneWanted(), w = Prefs.clockWeight();
    setClass(body, 'w400', w === 400);
    setClass(body, 'w600', w === 600);
    if (s !== sceneNow) {
      sceneNow = s;
      var photo = $('scenephoto'), front = $('scenefront'), time = $('clock').querySelector('.time');
      if (s) {
        photo.src = 'scenes/' + s.id + '.webp';
        $('clock').style.top = (s.clockTop - 34) + 'px';
        time.style.backgroundImage = 'url(scenes/' + s.id + '-glow.webp)';
        // The texture covers the stage at 104%, centred: (1997 - 1920) / 2 and (1123 - 1080) / 2.
        time.style.backgroundPosition = '-38px ' + (-s.clockTop - 22) + 'px';
        if (s.front) {
          front.src = 'scenes/' + s.id + '-front.webp';
          front.style.left = s.front[0] + 'px'; front.style.top = s.front[1] + 'px';
          front.style.width = s.front[2] + 'px'; front.style.height = s.front[3] + 'px';
        }
        setClass(front, 'none', !s.front);
        $('scenecredit').textContent = s.author + ' · ' + s.licence;
      } else {
        photo.removeAttribute('src');
        front.removeAttribute('src');
        $('clock').style.top = '';
        time.style.backgroundImage = '';
        $('scenecredit').textContent = '';
      }
    }
    setClass(body, 'scene', !!s);
    setClass(body, 'night', Data.isNight(Date.now()) || !!s);   // the photo takes the night look's glass and ink
    renderWeatherPill();
  }

  /** The weather pill shows only on a photo, with the weather on and known (Settings → Clock and weather). */
  function pillShown() { return !!sceneNow && Prefs.weather() && !!Data.weather; }

  function renderWeatherPill() {
    var on = pillShown(), pill = $('wpill'), w = Data.weather;
    setClass(body, 'weather-on', on);
    if (!on && st.zone === Z.TOP && st.top === 6) st.top = 5;
    pill.innerHTML = '';
    if (!on) { renderWeatherPanel(); return; }
    sym(Data.symbol(w.code, w.isDay), pill);
    el('span', 't', pill, Data.degrees(w.temp));
    el('span', 'c', pill, Data.condition(w.code, w.isDay));
    if (Data.city) { el('span', 'sep', pill); el('span', 'c', pill, Data.city); }
    renderWeatherPanel();
  }

  /**
   * The next hours under the pill while it has focus. As wide as the room left of the centred clock allows, so it
   * never covers a digit: fewer hours, and no sunset, when the room is short.
   */
  function renderWeatherPanel() {
    var panel = $('wpanel'), open = pillShown() && st.zone === Z.TOP && st.top === 6, w = Data.weather;
    setClass(panel, 'open', open);
    panel.innerHTML = '';
    if (!open) return;
    var clockLeft = 960 - $('clock').querySelector('.tw').offsetWidth / 2;
    var width = Math.max(300, Math.min(520, clockLeft - 32 - 80));
    var n = Math.min(1 + w.hours.length, Math.floor((width - 60) / 84));
    panel.style.width = width + 'px';
    var hw = (width - 60) / n;
    for (var i = 0; i < n; i++) {
      var h = el('div', 'h' + (i === 0 ? ' now' : ''), panel), x = i === 0 ? null : w.hours[i - 1];
      h.style.left = (30 + hw * (i + 0.5)) + 'px';
      el('div', 'at', h, i === 0 ? tr('Now') : Data.hour(x.at));
      sym(i === 0 ? Data.symbol(w.code, w.isDay) : Data.symbol(x.code, x.day), h);
      el('div', 'd', h, Data.degrees(i === 0 ? w.temp : x.temp));
    }
    el('div', 'line', panel);
    var foot = el('div', 'foot', panel);
    foot.appendChild(document.createTextNode(trf('H:{max}', {max: Data.degrees(w.max)}) + '  ' + trf('L:{min}', {min: Data.degrees(w.min)})));
    if (w.sunset) {
      var sun = el('span', '', foot);
      sym('sunset', sun);
      sun.appendChild(document.createTextNode(trf('Sunset {time}', {time: Data.time(w.sunset)})));
      if (foot.offsetWidth > width - 60) foot.removeChild(sun);
    }
  }

  function clockPattern() { return Prefs.clock() === '24' ? 'format|HH:mm' : Prefs.clock() === '12' ? 'format|h:mm a' : 'format|h:mm'; }

  function renderClock() {
    var d = new Date(), c = $('clock'), lang = (window.I18N && I18N.lang) || 'en';
    var date = formatDate('format|EEEE, MMMM d', d.getTime());
    try { date = date.toLocaleUpperCase(lang); } catch (e) { date = date.toUpperCase(); }
    c.querySelector('.date').textContent = date;
    var time = formatDate(clockPattern(), d.getTime());
    var layers = ['.time', '.tshade', '.trim', '.tw'];
    for (var i = 0; i < layers.length; i++) c.querySelector(layers[i]).textContent = time;
    renderWeatherPill();
    var line = c.querySelector('.wline'), w = Data.weather;
    line.innerHTML = '';
    if (!Prefs.weather() || !w) return;
    sym(Data.symbol(w.code, w.isDay), line);
    el('b', '', line, Data.degrees(w.temp));
    el('span', '', line, Data.condition(w.code, w.isDay));
    el('span', 'dot', line, '·');
    el('span', '', line, trf('H:{max}', {max: Data.degrees(w.max)}) + ' ' + trf('L:{min}', {min: Data.degrees(w.min)}));
    if (Data.city) { el('span', 'dot', line, '·'); el('span', '', line, Data.city); }
  }

  /** On the minute, so the clock never runs late (the 30-second refresh could, by up to 30 s). */
  var clockTimer = 0;
  function tickClock() {
    clearTimeout(clockTimer);
    if (document.hidden) return;
    renderClock();
    clockTimer = setTimeout(tickClock, 60000 - Date.now() % 60000 + 50);
  }

  // MARK: - Shelf

  var shelfFirst = 0, tileStep = 0, tileLeft = 0;

  function renderShelf() {
    var n = Math.min(shelf.length, SHELF_VISIBLE), gap = 48, pad = 40;
    // 16:9 tiles, as on Android: never taller than 126, and the glass keeps its bottom edge.
    var tw = Math.min(126 * 16 / 9, (1760 - Math.max(0, n - 1) * gap) / Math.max(1, n)), th = tw * 9 / 16;
    var width = n * tw + Math.max(0, n - 1) * gap, left = (1920 - width) / 2;
    var platter = $('platter');
    platter.style.display = n ? '' : 'none';
    platter.style.left = (left - pad) + 'px';
    platter.style.width = (width + 2 * pad) + 'px';
    platter.style.top = (814 + 126 - th) + 'px';
    platter.style.height = (th + 2 * pad) + 'px';
    // Lined up with the shelf's right edge; with no apps to show (the list unavailable) it keeps its own place.
    $('weather-summary').style.right = n ? (1920 - left - width) + 'px' : '';
    var tiles = $('tiles');
    tiles.innerHTML = '';
    tileStep = tw + gap;
    tileLeft = left;
    shelfFirst = Math.max(0, Math.min(shelfFirst, shelf.length - n));
    for (var i = 0; i < shelf.length; i++) {
      var t = el('div', 'tile', tiles);
      t.style.top = (854 + 126 - th) + 'px';
      t.style.width = tw + 'px';
      t.style.height = th + 'px';
      face(shelf[i], t, wordFit(shelf[i].title, tw, 30));
      hover(t, Z.SHELF, i);
    }
    // More apps than fit: a small arrow inside the glass's edge on each side that has more, where no tile reaches
    // even focused (inside the screen's safe area, as on Android).
    platter.innerHTML = '';
    for (var a = 0; a < 2; a++) {
      var arrow = sym('chevron_right', platter);
      arrow.className += a ? ' more' : ' more back';
      arrow.style.left = (a ? width + 2 * pad - 14 : 14) - 8.5 + 'px';
      arrow.style.top = (pad + th / 2 - 8.5) + 'px';
    }
    st.shelf = Math.min(st.shelf, Math.max(0, shelf.length - 1));
    placeTiles();
  }

  /** The row slides (a transform on #tiles, which old TVs animate), and the apps it takes past the glass fade out. */
  function placeTiles() {
    var row = $('tiles'), tiles = row.children, n = Math.min(shelf.length, SHELF_VISIBLE);
    for (var i = 0; i < shelf.length; i++) {
      var shown = i >= shelfFirst && i < shelfFirst + n;
      tiles[i].style.left = (tileLeft + i * tileStep) + 'px';
      tiles[i].style.opacity = shown ? '' : '0';
      tiles[i].style.pointerEvents = shown ? '' : 'none';
    }
    row.style.webkitTransform = row.style.transform = 'translateX(' + (-shelfFirst * tileStep) + 'px)';
    var arrows = $('platter').children;
    arrows[0].style.visibility = shelfFirst > 0 ? '' : 'hidden';
    arrows[1].style.visibility = shelfFirst + n < shelf.length ? '' : 'hidden';
  }

  /** Scrolls the shelf, one app at a time, so the focused app is shown. */
  function revealShelf() {
    var n = Math.min(shelf.length, SHELF_VISIBLE);
    var first = Math.max(0, Math.min(Math.max(shelfFirst, st.shelf - n + 1), Math.min(st.shelf, shelf.length - n)));
    if (first === shelfFirst || !shelf.length) return;
    shelfFirst = first;
    placeTiles();
  }

  // MARK: - Drawer

  var drawerItems = [], drawerInputs = 0;

  function renderDrawer() {
    var d = $('drawer');
    d.innerHTML = '';
    drawerItems = [];
    for (var i = 0; i < inputs.length; i++) {
      var x = inputs[i];
      drawerItems.push({label: x.label || tr('Live TV'), symbol: x.symbol, off: !x.connected, run: launcher(x.id, x.label)});
    }
    drawerInputs = drawerItems.length;
    if (Prefs.shortcut('all')) drawerItems.push({label: tr('All Apps'), symbol: 'apps', run: openApps});
    if (Prefs.shortcut('settings')) drawerItems.push({label: tr('Home Settings'), symbol: 'settings', run: function () { openSettings(); }});
    if (Prefs.shortcut('tv')) drawerItems.push({label: tr('TV Settings'), symbol: 'tv_settings', run: tvSettings});
    var n = drawerItems.length, gap = 18, groupGap = 56, groups = drawerInputs > 0 && drawerInputs < n ? 2 : 1;
    var tw = Math.min(230, (1760 - Math.max(0, n - groups) * gap - (groups - 1) * groupGap) / Math.max(1, n));
    var xPos = 80;
    for (var k = 0; k < n; k++) {
      if (k === drawerInputs && k > 0) {
        xPos += groupGap - gap;
        el('div', 'row-title', d, tr('Shortcuts')).style.left = (xPos + 4) + 'px';
      }
      if (k === 0 && drawerInputs) el('div', 'row-title', d, tr('Inputs')).style.left = '84px';
      var item = drawerItems[k], key = el('div', 'key glass' + (item.off ? ' off' : ''), d);
      key.style.left = xPos + 'px';
      key.style.width = tw + 'px';
      sym(item.symbol, key);
      el('div', 'label', key, item.label);
      hover(key, Z.DRAWER, k);
      xPos += tw + gap;
    }
    fitKeyLabels(d);
    // Measured again once the web font is in: a fallback font's widths would leave a name cut.
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () { fitKeyLabels(d); });
    renderPanels();
    updateFocus();
  }

  /** Long names ("Ana ekran ayarları", "Telewizja na żywo") shrink a little to fit their tile instead of being cut. */
  function fitKeyLabels(d) {
    var labels = d.querySelectorAll('.key .label');
    for (var j = 0; j < labels.length; j++) {
      labels[j].style.fontSize = '';
      for (var size = 23; size > 17 && labels[j].scrollWidth > labels[j].clientWidth; size--) labels[j].style.fontSize = (size - 1) + 'px';
    }
  }

  function renderPanels() {
    var d = $('drawer'), old = d.querySelectorAll('.panel');
    for (var o = 0; o < old.length; o++) d.removeChild(old[o]);
    if (Prefs.weather()) {
      var w = el('div', 'panel glass', d);
      w.id = 'weather';w.style.left = '80px';w.style.width = '1760px';renderWeather(w);
    }
  }

  function renderWeather(card) {
    el('div', 'head', card, Data.city || tr('Weather'));
    var w = Data.weather;
    if (!w) {
      el('div', 'cond', card, tr('No weather data right now.')).style.top = '96px';
      return;
    }
    var now = el('div', 'now', card);
    sym(Data.symbol(w.code, w.isDay), now);
    el('span', 'temp', now, Data.degrees(w.temp));
    el('div', 'cond', card, Data.condition(w.code, w.isDay));
    el('div', 'range', card, trf('H:{max}', {max: Data.degrees(w.max)}) + '   ' + trf('L:{min}', {min: Data.degrees(w.min)}));
    el('div', 'split', card);
    // Home Settings → Forecast: the next 6 hours or the next 5 days (the hours until a forecast has days).
    var days = Prefs.forecast() === 'days' && w.days && w.days.length > 0, list = days ? w.days : w.hours, n = days ? 5 : 6;
    var hours = el('div', 'hours', card), width = parseFloat(card.style.width) - 30 - 422, hw = width / n;
    for (var i = 0; list && i < list.length && i < n; i++) {
      var h = el('div', 'hour', hours), x = list[i];
      h.style.left = (hw * i) + 'px';
      h.style.width = hw + 'px';
      el('div', 't', h, days ? Data.weekday(x.at) : Data.hour(x.at));
      sym(Data.symbol(x.code, days || x.day), h);
      var t = el('div', 'd', h, Data.degrees(days ? x.max : x.temp));
      if (days) el('span', 'low', t, Data.degrees(x.min));
    }
    var sun = el('div', 'sun', card);
    if (w.sunrise) { var a = el('span', '', sun); sym('sunrise', a); a.appendChild(document.createTextNode(trf('Sunrise {time}', {time: Data.time(w.sunrise)}))); }
    if (w.sunset) { var b = el('span', '', sun); sym('sunset', b); b.appendChild(document.createTextNode(trf('Sunset {time}', {time: Data.time(w.sunset)}))); }
    var credit = el('a', 'weather-credit', card, 'Weather: Open-Meteo · CC BY 4.0');
    credit.href = 'credits.html';
  }

  function openDrawer() {
    if (!drawerItems.length) return;
    var tiles = $('tiles').children;
    st.zone = Z.DRAWER;
    st.drawer = Math.min(drawerItems.length - 1, nearest(document.querySelectorAll('#drawer .key'), centerX(tiles[st.shelf])));
    updateFocus();
  }

  function closeDrawer() {
    var keys = document.querySelectorAll('#drawer .key');
    st.zone = Z.SHELF;
    if (shelf.length) st.shelf = nearest($('tiles').children, centerX(keys[st.drawer]));
    updateFocus();
  }

  // MARK: - All Apps

  function renderApps() {
    var sheet = $('apps');
    sheet.firstChild.textContent = tr('All Apps');
    var grid = sheet.lastChild;
    grid.innerHTML = '';
    for (var i = 0; i < others.length; i++) {
      var r = Math.floor(i / 5), c = i % 5, cell = el('div', 'cell', grid);
      cell.style.left = (130 + c * 340) + 'px';
      cell.style.top = (196 + r * 239) + 'px';
      face(others[i], el('div', 'face', cell), wordFit(others[i].title, 300, 34));
      el('div', 'label', cell, others[i].title);
      hover(cell, Z.APPS, i);
    }
  }

  function openApps() {
    renderApps();
    st.zone = Z.APPS;
    st.grid = 0;
    setClass($('apps'), 'open', true);
    updateFocus();
  }

  function closeSheets() {
    setClass($('apps'), 'open', false);
    setClass($('settings'), 'open', false);
    setClass($('shelfedit'), 'open', false);
    setClass($('langpick'), 'open', false);
    setClass($('quick'), 'open', false);
    setClass($('actions'), 'open', false);
    langHint = null;
    st.zone = Z.SHELF;
    updateFocus();
  }

  // MARK: - Home Settings

  /** Android's categories, with the rows a web TV app can offer: [name, symbol]. */
  var CATEGORIES = ['home', 'sparkles', 'partly_cloudy_day', 'tv_settings'];
  /** Written out, so tools/webapp.py packs these strings into the web app's catalogue. */
  function categoryName(c) {
    return c === 0 ? tr('Home screen') : c === 1 ? tr('Appearance') : c === 2 ? tr('Clock and weather') : tr('System and about');
  }
  var SHELF_CATEGORY = 0, LANGUAGE_CATEGORY = 3;

  function settingRows(category) {
    var unit = Prefs.temperature(), theme = Prefs.theme();
    var auto = tr('Automatic'), picked = pickedLanguage();
    switch (category) {
      case 0: return [
        // Everything the home screen shows in one place: the clock screen's look and the weather first.
        {label: tr('Background photo'), value: Scenes.label(Prefs.scene()), preview: true,
          hint: tr('A landscape behind the clock when the home screen has nothing to watch. Automatic shows a new photo every day and a night photo after sunset. OK changes it.'),
          step: function (d) { cycle('scene', Scenes.choices(), Prefs.scene(), d); renderScene(); }},
        {label: tr('Clock weight'), value: Scenes.weightName(Prefs.clockWeight()), preview: true,
          hint: tr('How bold the large clock is on a background photo. OK changes it.'),
          step: function (d) { cycle('clockWeight', Scenes.WEIGHTS, Prefs.clockWeight(), d); renderScene(); }},
        {label: tr('Weather widget'), toggle: Prefs.weather(),
          step: function () { Prefs.set('weather', !Prefs.weather()); renderPanels(); renderHeaderWeather(); renderWeatherPill(); }},
        {label: tr('Shelf'), value: '›', step: function (d) { if (d >= 0) openShelfEdit(); }},
        {label: tr('Shortcuts'), value: '›', step: function (d) { if (d >= 0) openShortcuts(); }},
        {label: tr('Hidden apps'), value: String(hiddenIds().length), opens: true, step: function (d) { if (d >= 0) openHidden(); }},
        {label: tr('App lock'), value: Prefs.pin() ? tr('On') : tr('Off'), opens: true, step: function (d) { if (d >= 0) openLock(); }}
      ];
      case 1: return [
        {label: tr('Theme'), value: theme === 'light' ? tr('Light') : theme === 'dark' ? tr('Dark') : auto,
          step: function (d) { cycle('theme', ['auto', 'light', 'dark'], theme, d); checkNight(); }},
        {label: tr('Screensaver'), value: Prefs.saver() ? trf('{n} min', {n: Prefs.saver()}) : tr('Off'),
          step: function (d) { cycle('saver', [0, 1, 5, 10, 15, 30], Prefs.saver(), d); }},
        {label: tr('Lightweight mode'), toggle: Prefs.lite(),
          step: function () { Prefs.set('lite', !Prefs.lite()); setClass(body, 'lite', Prefs.lite()); }}
      ];
      case 2: return [
        {label: tr('City'), value: Data.city || '—', opens: true, step: function (d) { if (d >= 0) openCity(); }},
        {label: tr('Clock format'), value: Prefs.clock() === 'auto' ? auto : Prefs.clock() + 'h',
          step: function (d) { cycle('clock', ['auto', '12', '24'], Prefs.clock(), d); renderClock(); }},
        {label: tr('Forecast'), value: Prefs.forecast() === 'days' ? tr('Next days') : tr('Next hours'),
          step: function (d) { cycle('forecast', ['hours', 'days'], Prefs.forecast(), d); renderPanels(); }},
        {label: tr('Temperature'), value: unit === 'C' ? '°C' : unit === 'F' ? '°F' : tr('unit|Automatic'),
          step: function (d) { cycle('temperature', ['auto', 'C', 'F'], unit, d); renderPanels(); renderHeaderWeather(); }}
      ];
      default: return [
        {label: tr('Language'), value: picked === 'auto' ? auto : I18N.languageName(picked), opens: true, language: true,
          step: function (d) { if (d >= 0) openLanguages(); }},
        {label: tr('Back up settings'), value: backupNote || (backupAt() ? formatDate('format|EEEE, MMMM d', backupAt()) : tr('Not saved yet')),
          step: function (d) { if (d >= 0) backupNote = backUp(); }},
        {label: tr('Restore settings'), value: backupAt() ? '›' : tr('No backup'), step: function (d) { if (d >= 0) restore(); }},
        {label: tr('TV Settings'), value: '›', step: function (d) { if (d >= 0) tvSettings(); }},
        {label: tr('Licences'), value: '›', step: function (d) { if (d >= 0) window.location.href = 'credits.html'; }}
      ];
    }
  }

  function cycle(name, values, current, d) {
    var i = values.indexOf(current);
    Prefs.set(name, values[(i + (d || 1) + values.length) % values.length]);
  }

  var rowsNow = [];

  function renderSettings() {
    var sheet = $('settings'), nav = sheet.querySelector('.nav'), box = sheet.querySelector('.box');
    nav.innerHTML = '';
    el('div', 'head', nav, tr('light|Settings'));
    for (var c = 0; c < CATEGORIES.length; c++) {
      var cat = el('div', 'cat' + (c === st.cat ? ' open' : ''), nav);
      sym(CATEGORIES[c], cat);
      el('span', '', cat, categoryName(c));
      hoverCategory(cat, c);
    }
    el('div', 'brand', nav, 'PURETV');
    box.innerHTML = '';
    var title = el('div', 'title', box);
    sym(CATEGORIES[st.cat], el('span', 'badge', title));
    el('span', 'name', title, categoryName(st.cat));
    el('div', 'sub', box, tr('Make yourself at home'));
    var list = el('div', 'list', box), holder = el('div', 'rows', list);
    rowsNow = settingRows(st.cat);
    list.style.height = Math.min(rowsNow.length, 7) * 78 + 'px';
    for (var i = 0; i < rowsNow.length; i++) {
      var r = rowsNow[i], row = el('div', 'row' + (r.dim ? ' dim' : ''), holder, r.label);
      if (r.toggle !== undefined) setClass(el('div', 'toggle', row), 'on', r.toggle);
      else {
        var v = el('span', 'value', row);
        if (r.value === '›') v.textContent = '›';
        else if (r.opens) { v.appendChild(document.createTextNode(r.value)); el('span', 'chev', v, '›'); }
        else { el('span', 'chev', v, '‹'); v.appendChild(document.createTextNode(r.value)); el('span', 'chev', v, '›'); }
      }
      hover(row, Z.SETTINGS, i);
    }
    el('div', 'hint', box);
    updateFocus();
  }

  var footShown = null;

  /** Under the rows: the focused row's explanation (or the general one) and, for the look, a picture of the home screen. */
  function settingFoot() {
    var hint = document.querySelector('#settings .hint');
    if (!hint) return;
    var r = st.zone === Z.SETTINGS && !st.nav ? rowsNow[st.row] : null;
    var key = r ? r.label + '|' + r.value : '';
    if (key === footShown && hint.firstChild) return;
    footShown = key;
    hint.innerHTML = '';
    if (r && r.preview) homePreview(el('div', 'preview', hint));
    hint.appendChild(document.createTextNode(r && r.hint ? r.hint : tr('Right opens options. Back returns to categories.')));
  }

  /**
   * A small picture of the home screen with the chosen background and clock weight: the photo (or the classic
   * colours), the clock at its place, and the shelf and buttons as plain shapes. An illustration only.
   */
  function homePreview(box) {
    var now = Date.now(), dark = Data.isNight(now), s = Scenes.pick(Prefs.scene(), dark, now), u = 0.2;
    var onPhoto = !!s, ink = onPhoto || dark ? '255,255,255' : '29,29,31';
    if (onPhoto) { var img = el('img', 'bg', box); img.src = 'scenes/' + s.id + '.webp'; }
    else el('div', 'bg ' + (dark ? 'dark' : 'light'), box);
    function part(l, t, r, b, radius, color) {
      var p = el('i', '', box);
      p.style.left = l * u + 'px'; p.style.top = t * u + 'px'; p.style.width = (r - l) * u + 'px'; p.style.height = (b - t) * u + 'px';
      p.style.borderRadius = radius * u + 'px'; p.style.background = color;
    }
    part(128, 820, 1792, 1012, 64, 'rgba(' + (onPhoto || dark ? '255,255,255' : '0,0,0') + ',0.16)');
    for (var i = 0; i < 6; i++) part(170 + i * 272, 858, 170 + i * 272 + 224, 976, 22, 'rgba(' + ink + ',0.34)');
    part(1432, 69, 1500, 137, 34, 'rgba(' + ink + ',0.24)');
    part(1518, 69, 1586, 137, 34, 'rgba(' + ink + ',0.24)');
    part(1604, 69, 1840, 137, 34, 'rgba(' + ink + ',0.24)');
    part(1778, 75, 1834, 131, 28, '#D2AB55');
    if (onPhoto && Prefs.weather()) part(80, 69, 470, 137, 34, 'rgba(' + ink + ',0.24)');
    var t = el('div', 'pt' + (onPhoto ? '' : ' left'), box, formatDate(clockPattern(), now));
    t.style.setProperty('font-weight', String(Prefs.clockWeight()), 'important');
    if (onPhoto) {
      part(840, s.clockTop - 34, 1080, s.clockTop - 14, 8, 'rgba(255,255,255,0.7)');
      t.style.top = (s.clockTop * u) + 'px';
    } else {
      part(84, 292, 430, 316, 6, 'rgba(' + ink + ',0.34)');
      t.style.left = 76 * u + 'px'; t.style.width = 'auto'; t.style.top = (536 - 236 * 0.97) * u + 'px';
      t.style.color = 'rgb(' + ink + ')';
    }
  }

  // MARK: - A list sheet for the settings' lists (city, hidden apps, shortcuts, app lock), on the backdrop

  var pickRows = [], pickBack = 0, pickInput = null;

  /** rows: {label, value, check, toggle, run}; input: a text field above them (the TV's keyboard), searched on Enter. */
  function openPicker(title, rows, hint, backTo, input) {
    pickRows = rows;
    pickBack = backTo;
    var box = $('picker').firstChild;
    box.innerHTML = '';
    el('div', 'title', box, title);
    pickInput = null;
    if (input) {
      pickInput = el('input', 'field', box);
      pickInput.type = 'text';
      pickInput.placeholder = input.placeholder;
      pickInput.value = input.value || '';
      pickInput.onkeydown = function (e) {
        if (e.keyCode === 13) { e.preventDefault(); e.stopPropagation(); input.search(pickInput.value); }
        else if (e.keyCode === TV.keys.down && pickRows.length) { e.preventDefault(); e.stopPropagation(); pickInput.blur(); st.pick = 0; updateFocus(); }
        else e.stopPropagation();   // letters and the arrows inside the field stay with it
      };
    }
    var list = el('div', 'list', box), holder = el('div', 'rows', list);
    list.style.height = Math.min(Math.max(rows.length, 1), 7) * 78 + 'px';
    for (var i = 0; i < rows.length; i++) {
      var r = rows[i], row = el('div', 'row', holder, r.label);
      if (r.toggle !== undefined) setClass(el('div', 'toggle', row), 'on', r.toggle);
      else if (r.check) sym('check', el('span', 'value', row));
      else if (r.value) el('span', 'value', row, r.value);
      hover(row, Z.PICK, i);
    }
    el('div', 'hint', box, hint || '');
    st.zone = Z.PICK;
    st.pick = Math.min(st.pick, Math.max(0, rows.length - 1));
    setClass($('settings'), 'open', false);
    setClass($('picker'), 'open', true);
    updateFocus();
    if (pickInput && !rows.length) setTimeout(function () { try { pickInput.focus(); } catch (e) {} }, 50);
  }

  function closePicker() {
    setClass($('picker'), 'open', false);
    if (pickInput) { try { pickInput.blur(); } catch (e) {} }
    openSettings(pickBack);
    st.nav = false;
    updateFocus();
  }

  // City: the same search as Android's Settings → City (Open-Meteo's geocoding; only the name is sent).
  function openCity(found, note) {
    var rows = (found || []).map(function (c) {
      return {label: c.label, run: function () { Prefs.set('place', {name: c.name, lat: c.lat, lon: c.lon}); location.reload(); }};
    });
    st.pick = 0;
    openPicker(tr('City'), rows, note || tr('Choose a city or search again. Back keeps the current city.'), 2,
      {placeholder: tr("Type the city's name"), search: searchCity});
  }

  function searchCity(name) {
    name = (name || '').trim();
    if (!name) return;
    openCity([], tr('Searching…'));
    var x = new XMLHttpRequest();
    x.open('GET', 'https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language=' + encodeURIComponent(I18N.lang || 'en')
      + '&name=' + encodeURIComponent(name), true);
    x.timeout = 10000;
    x.onreadystatechange = function () {
      if (x.readyState !== 4 || st.zone !== Z.PICK) return;
      var out = [], seen = {};
      try {
        var res = x.status === 200 ? (JSON.parse(x.responseText).results || []) : null;
        if (!res) throw new Error('HTTP ' + x.status);
        for (var i = 0; i < res.length; i++) {
          var r = res[i], label = r.name;
          if (!r.name || r.latitude === undefined) continue;
          if (r.admin1 && r.admin1 !== r.name) label += ', ' + r.admin1;
          if (r.country && r.country !== r.name) label += ', ' + r.country;
          if (seen[label]) continue;
          seen[label] = 1;
          out.push({label: label, name: r.name, lat: r.latitude.toFixed(4), lon: r.longitude.toFixed(4)});
        }
        openCity(out, out.length ? tr('Choose your city.') : tr('No city with that name.'));
      } catch (e) { openCity([], tr('Could not search for a city. Try again.')); }
      if (pickInput) pickInput.value = name;
    };
    x.send();
  }

  // Hidden apps: left out of the shelf and All Apps; OK here shows one again.
  function hiddenIds() { return Prefs.hiddenApps(); }

  function openHidden() {
    var ids = hiddenIds(), rows = [];
    for (var i = 0; i < apps.length; i++) {
      if (ids.indexOf(apps[i].id) < 0) continue;
      (function (a) {
        rows.push({label: a.title, value: tr('Show app'), run: function () {
          Prefs.set('hiddenApps', hiddenIds().filter(function (id) { return id !== a.id; }));
          refreshApps();
          openHidden();
        }});
      })(apps[i]);
    }
    openPicker(tr('Hidden apps'), rows, tr('OK shows the app in Other apps again. Hidden apps stay installed.'), 0);
  }

  // Shortcuts: which of All Apps, Home Settings and TV Settings the drawer shows.
  function openShortcuts() {
    var names = {all: tr('All Apps'), settings: tr('Home Settings'), tv: tr('TV Settings')}, rows = [];
    ['all', 'settings', 'tv'].forEach(function (k) {
      rows.push({label: names[k], toggle: Prefs.shortcut(k), run: function () {
        var s = Prefs.shortcuts();
        s[k] = !Prefs.shortcut(k);
        Prefs.set('shortcuts', s);
        renderDrawer();
        openShortcuts();
      }});
    });
    openPicker(tr('Shortcuts'), rows, '', 0);
  }

  // App lock: a PIN for chosen apps when PureTV opens them. A household lock, not a security boundary.
  function pinHash(pin, salt) {
    var h = 5381, s = salt + ':' + pin;
    for (var i = 0; i < s.length; i++) h = ((h * 33) ^ s.charCodeAt(i)) >>> 0;
    for (var round = 0; round < 2000; round++) h = ((h * 33) ^ (h >>> 7) ^ round) >>> 0;
    return h.toString(16);
  }

  function openLock() {
    var rows = [];
    if (!Prefs.pin()) {
      rows.push({label: tr('Set a PIN'), value: '›', run: function () { newPin(openLock); }});
      openPicker(tr('App lock'), rows, tr("Choose a PIN first, then the apps to lock. A simple household lock: apps opened another way (TV Settings, the remote's app buttons) are not locked."), 0);
      return;
    }
    var locked = Prefs.lockedApps();
    for (var i = 0; i < apps.length; i++) {
      (function (a) {
        rows.push({label: a.title, check: locked.indexOf(a.id) >= 0, run: function () {
          var l = Prefs.lockedApps(), at = l.indexOf(a.id);
          if (at >= 0) l.splice(at, 1); else l.push(a.id);
          Prefs.set('lockedApps', l);
          openLock();
        }});
      })(apps[i]);
    }
    rows.push({label: tr('Change PIN'), value: '›', run: function () { askPin(tr('Enter the PIN'), function () { newPin(openLock); }, openLock); }});
    rows.push({label: tr('Turn off app lock'), value: '›', run: function () {
      askPin(tr('Enter the PIN'), function () { Prefs.set('pin', null); Prefs.set('lockedApps', []); openLock(); }, openLock);
    }});
    openPicker(tr('App lock'), rows, tr('OK locks or unlocks an app. Locked apps ask for the PIN when opened from PureTV.'), 0);
  }

  function newPin(done) {
    enterPin(tr('New PIN (4 to 8 digits)'), function (first) {
      enterPin(tr('Enter the new PIN again'), function (second) {
        if (first !== second) { toast(tr("The PINs didn't match. Nothing changed.")); done(); return; }
        var salt = String(Math.floor(Math.random() * 1e9));
        Prefs.set('pin', {salt: salt, hash: pinHash(first, salt)});
        toast(tr('PIN saved.'));
        done();
      }, done);
    }, done);
  }

  var pinWrong = 0, pinWaitUntil = 0;

  /** Asks for the PIN; ok() once it is right, cancel() on Back. Five wrong ones wait 30 s. */
  function askPin(title, ok, cancel) {
    enterPin(title, function (pin) {
      if (Date.now() < pinWaitUntil) { toast(trf('Too many wrong PINs. Try again in {n} s.', {n: Math.ceil((pinWaitUntil - Date.now()) / 1000)})); cancel(); return; }
      var p = Prefs.pin();
      if (p && pinHash(pin, p.salt) === p.hash) { pinWrong = 0; ok(); return; }
      if (++pinWrong >= 5) { pinWrong = 0; pinWaitUntil = Date.now() + 30000; }
      toast(tr('Wrong PIN.'));
      cancel();
    }, cancel);
  }

  // The PIN pad: digits from the remote, or the on-screen keys (arrows and OK).
  var pad = null;
  var PAD_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '⌫', '0', 'OK'];

  function enterPin(title, done, cancel) {
    pad = {title: title, digits: '', done: done, cancel: cancel, from: st.zone, at: 0};
    setClass($('picker'), 'open', false);
    st.zone = Z.PIN;
    renderPad();
    setClass($('pin'), 'open', true);
  }

  function renderPad() {
    var box = $('pin').firstChild;
    box.innerHTML = '';
    el('div', 'title', box, pad.title);
    var dots = el('div', 'dots', box);
    for (var i = 0; i < Math.max(4, pad.digits.length); i++) setClass(el('span', 'dot', dots), 'on', i < pad.digits.length);
    var keys = el('div', 'keys', box);
    for (var k = 0; k < PAD_KEYS.length; k++) {
      var key = el('div', 'key' + (k === pad.at ? ' focus' : ''), keys, PAD_KEYS[k]);
      (function (n) { key.onclick = function () { pad.at = n; padPress(PAD_KEYS[n]); }; })(k);
    }
    el('div', 'hint', box, tr('Use 4 to 8 digits.'));
  }

  function padPress(k) {
    if (!pad) return;
    if (k === '⌫') pad.digits = pad.digits.slice(0, -1);
    else if (k === 'OK') { if (pad.digits.length >= 4) { finishPad(true); return; } }
    else if (pad.digits.length < 8) pad.digits += k;
    renderPad();
  }

  function finishPad(ok) {
    var p = pad;
    pad = null;
    setClass($('pin'), 'open', false);
    st.zone = Z.SHELF;
    if (ok) p.done(p.digits); else p.cancel();
    updateFocus();
  }

  function isLocked(id) { return !!Prefs.pin() && Prefs.lockedApps().indexOf(id) >= 0; }

  // Screensaver after, and backup: a copy of the settings on this TV.
  function backupAt() { var b = Prefs.backup(); return b ? b.at : 0; }
  var backupNote = '';
  function backUp() {
    try { localStorage.setItem('puretv.backup', JSON.stringify({at: Date.now(), prefs: localStorage.getItem('puretv.prefs')})); return tr('Saved.'); }
    catch (e) { return tr('Not saved yet'); }
  }
  function restore() {
    var b = Prefs.backup();
    if (!b) return;
    try { localStorage.setItem('puretv.prefs', b.prefs); } catch (e) { return; }
    location.reload();
  }

  // MARK: - Holding OK on an app (QuickActionDialog): add it to the shelf, remove it, or move it along the shelf

  var actionRows = [], actionApp = null, actionFrom = null, moveStart = null;

  function shelfIds() { return shelf.map(function (a) { return a.id; }); }

  function saveShelf(ids) {
    Prefs.set('shelf', ids);
    pickShelf();
    renderShelf();
  }

  function openActions() {
    var app = st.zone === Z.SHELF ? shelf[st.shelf] : st.zone === Z.APPS ? others[st.grid] : null;
    if (!app) return;
    actionApp = app;
    actionFrom = st.zone;
    var ids = shelfIds(), on = ids.indexOf(app.id) >= 0;
    actionRows = [];
    if (actionFrom === Z.SHELF && ids.length > 1) actionRows.push({label: tr('Move on shelf'), sym: 'chevron_right', run: startMove});
    if (on) actionRows.push({label: tr('Remove from home screen'), sym: 'clear_night', run: function () {
      saveShelf(ids.filter(function (id) { return id !== app.id; }));
      st.shelf = Math.min(st.shelf, Math.max(0, shelf.length - 1));
    }});
    else actionRows.push({label: tr('Add to home screen'), sym: 'home', run: function () { saveShelf(ids.concat([app.id])); }});
    actionRows.push({label: tr('Hide app'), run: function () {
      Prefs.set('hiddenApps', hiddenIds().concat([app.id]));
      saveShelf(shelfIds().filter(function (id) { return id !== app.id; }));
      refreshApps();
    }});
    var box = $('actions').firstChild;
    box.innerHTML = '';
    el('div', 'title', box, app.title);
    var list = el('div', 'list', box), holder = el('div', 'rows', list);
    list.style.height = actionRows.length * 78 + 'px';
    for (var i = 0; i < actionRows.length; i++) {
      var row = el('div', 'row', holder);
      el('span', 'label', row, actionRows[i].label);
      hover(row, Z.ACTIONS, i);
    }
    st.zone = Z.ACTIONS;
    st.action = 0;
    setClass($('actions'), 'open', true);
    updateFocus();
  }

  function closeActions() {
    setClass($('actions'), 'open', false);
    st.zone = actionFrom || Z.SHELF;
    updateFocus();
  }

  function runAction() {
    var r = actionRows[st.action];
    closeActions();
    if (r) r.run();
    updateFocus();
  }

  /** Move mode: ← → carry the app along the shelf, OK drops it there, Back puts it back. */
  function startMove() {
    st.zone = Z.SHELF;
    st.moving = true;
    moveStart = shelfIds();
    updateFocus();
  }

  function moveApp(dx) {
    var ids = shelfIds(), at = st.shelf, to = Math.max(0, Math.min(ids.length - 1, at + dx));
    if (to === at) return;
    var id = ids.splice(at, 1)[0];
    ids.splice(to, 0, id);
    saveShelf(ids);
    st.shelf = to;
    updateFocus();
  }

  function endMove(keep) {
    st.moving = false;
    if (!keep && moveStart) {
      var id = shelf[st.shelf] && shelf[st.shelf].id;
      saveShelf(moveStart);
      st.shelf = Math.max(0, moveStart.indexOf(id));
    }
    moveStart = null;
    updateFocus();
  }

  // MARK: - Quick panel: under the gear, as on Android; All settings opens the full settings

  var quickRows = [];

  function quickList() {
    var theme = Prefs.theme();
    return [
      {label: tr('Edit home screen'), sym: 'home', value: '›', step: function (d) { if (d >= 0) openShelfEdit(); }},
      {label: tr('Theme'), sym: 'sparkles', value: theme === 'light' ? tr('Light') : theme === 'dark' ? tr('Dark') : tr('Automatic'),
        step: function (d) { cycle('theme', ['auto', 'light', 'dark'], theme, d); checkNight(); }},
      {label: tr('Weather widget'), sym: 'partly_cloudy_day', toggle: Prefs.weather(),
        step: function () { Prefs.set('weather', !Prefs.weather()); renderPanels(); renderHeaderWeather(); renderWeatherPill(); }},
      {label: tr('TV Settings'), sym: 'tv_settings', value: '›', step: function (d) { if (d >= 0) tvSettings(); }},
      {label: tr('All settings'), sym: 'settings', value: '›', step: function (d) { if (d >= 0) openSettings(); }}
    ];
  }

  function renderQuick() {
    var box = $('quick').firstChild;
    box.innerHTML = '';
    var list = el('div', 'list', box), holder = el('div', 'rows', list);
    quickRows = quickList();
    list.style.height = quickRows.length * 78 + 'px';
    for (var i = 0; i < quickRows.length; i++) {
      var r = quickRows[i], row = el('div', 'row icon', holder);
      sym(r.sym, el('span', 'tile', row));
      el('span', 'label', row, r.label);
      if (r.toggle !== undefined) setClass(el('div', 'toggle', row), 'on', r.toggle);
      else el('span', 'value', row, r.value);
      hover(row, Z.QUICK, i);
    }
    updateFocus();
  }

  function openQuick() {
    st.zone = Z.QUICK;
    st.quick = 0;
    renderQuick();
    setClass($('quick'), 'open', true);
    updateFocus();
  }

  /** A row's job; the panel closes when it opened another screen. */
  function stepQuick(d) {
    var r = quickRows[st.quick];
    if (!r) return;
    var leaves = r.value === '›';
    if (leaves) setClass($('quick'), 'open', false);
    r.step(d);
    if (!leaves && st.zone === Z.QUICK) renderQuick();
  }

  /** The pointer on a category opens it, as the arrows do. */
  function hoverCategory(node, c) {
    node.onmouseover = function () { if (st.zone !== Z.SETTINGS) return; st.nav = true; if (c !== st.cat) { st.cat = c; st.row = 0; renderSettings(); } updateFocus(); };
    node.onclick = function () { node.onmouseover(); st.nav = false; updateFocus(); };
  }

  function openSettings(category) {
    st.zone = Z.SETTINGS;
    st.cat = category === undefined ? 0 : category;
    st.nav = category === undefined;
    st.row = 0;
    renderSettings();
    setClass($('settings'), 'open', true);
    updateFocus();
  }

  // MARK: - Language: a list of every catalogue in its own name, as on Android (moving through it doesn't switch the app)

  var langHint = null;

  function languageChoices() {
    return ['auto'].concat(I18N.languages());
  }

  /** The language in use: "auto", or the catalogue the setting resolved to ("pt-BR" in config.properties is "pt"). */
  function pickedLanguage() {
    return Prefs.language() === 'auto' ? 'auto' : I18N.lang;
  }

  function renderLanguages() {
    var box = $('langpick').firstChild, choices = languageChoices(), current = choices.indexOf(pickedLanguage());
    box.innerHTML = '';
    el('div', 'title', box, tr('Language'));
    var list = el('div', 'list', box), holder = el('div', 'rows', list);
    list.style.height = Math.min(choices.length, 7) * 78 + 'px';
    for (var i = 0; i < choices.length; i++) {
      var row = el('div', 'row', holder, choices[i] === 'auto' ? tr('Automatic') : I18N.languageName(choices[i]));
      if (i === current) sym('check', el('span', 'value', row));
      hover(row, Z.LANG, i);
    }
    langHint = el('div', 'hint', box);
    updateFocus();
  }

  function openLanguages() {
    setClass($('settings'), 'open', false);
    st.zone = Z.LANG;
    st.lang = Math.max(0, languageChoices().indexOf(pickedLanguage()));
    renderLanguages();
    setClass($('langpick'), 'open', true);
    updateFocus();
  }

  /** OK: the language under the focus; Back: none. Either way, back to Home Settings, on the Language row. */
  function closeLanguages(pick) {
    setClass($('langpick'), 'open', false);
    langHint = null;
    var code = languageChoices()[st.lang];
    if (pick && code !== pickedLanguage()) {
      Prefs.set('language', code);
      I18N.set(Prefs.language());
      renderAll();
      Data.refresh();   // the picks' titles and genres come in the new language
    }
    openSettings(LANGUAGE_CATEGORY);
    for (var i = 0; i < rowsNow.length; i++) if (rowsNow[i].language) st.row = i;
    updateFocus();
  }

  // MARK: - Shelf editor: which apps, in what order

  function editList() {
    var ids = shelf.map(function (a) { return a.id; });
    return {ids: ids, rows: apps.slice().sort(function (a, b) {
      var ia = ids.indexOf(a.id), ib = ids.indexOf(b.id);
      if (ia >= 0 || ib >= 0) return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
      return a.title.toLowerCase() < b.title.toLowerCase() ? -1 : 1;
    })};
  }

  function renderShelfEdit() {
    var box = $('shelfedit').firstChild, e = editList();
    box.innerHTML = '';
    var title = el('div', 'title', box);
    sym('apps', title);
    title.appendChild(document.createTextNode(tr('Shelf')));
    var list = el('div', 'list', box), holder = el('div', 'rows', list);
    list.style.height = Math.min(e.rows.length, 7) * 78 + 'px';
    for (var i = 0; i < e.rows.length; i++) {
      var a = e.rows[i], at = e.ids.indexOf(a.id), row = el('div', 'row', holder, a.title);
      var place = el('span', 'value', row, at >= 0 ? '#' + (at + 1) : tr('shelf|Off'));
      if (at >= 0) sym('check', place).style.marginLeft = '16px';   // on the shelf: a check, as in the pickers
      hover(row, Z.EDIT, i);
    }
    el('div', 'hint', box, tr('OK adds or removes an app. ← → change the order.'));
    updateFocus();
  }

  function openShelfEdit() {
    setClass($('settings'), 'open', false);
    st.zone = Z.EDIT;
    st.edit = 0;
    renderShelfEdit();
    setClass($('shelfedit'), 'open', true);
    updateFocus();
  }

  function editShelf(dx) {
    var e = editList(), app = e.rows[st.edit];
    if (!app) return;
    var ids = e.ids.slice(), at = ids.indexOf(app.id);
    if (dx === 0) {
      if (at >= 0) ids.splice(at, 1);
      else ids.push(app.id);
    } else {
      if (at < 0) return;
      var to = Math.max(0, Math.min(ids.length - 1, at + dx));
      ids.splice(at, 1);
      ids.splice(to, 0, app.id);
    }
    Prefs.set('shelf', ids);
    pickShelf();
    renderShelf();
    var again = editList();
    for (var i = 0; i < again.rows.length; i++) if (again.rows[i].id === app.id) st.edit = i;
    renderShelfEdit();
  }

  // MARK: - Opening things

  function launcher(id, name) {
    var open = rawLauncher(id, name);
    return function () {
      if (!isLocked(id)) { open(); return; }
      var from = st.zone;
      askPin(trf('PIN to open {app}', {app: name}), function () { st.zone = from; updateFocus(); open(); }, function () { st.zone = from; updateFocus(); });
    };
  }

  function rawLauncher(id, name) {
    return function () {
      TV.launch(id, null, function (ok) {
        if (!ok) toast(trf("Couldn't open {app}.", {app: name || id}));
      });
    };
  }

  function tvSettings() {
    TV.settings(function (ok) {
      if (!ok) toast(tr("Couldn't open TV Settings"));
    });
  }

  function liveTV() {
    for (var i = 0; i < inputs.length; i++) if (inputs[i].key === 'live') return inputs[i];
    return null;
  }

  function activate() {
    switch (st.zone) {
      case Z.TOP:
        if (st.top < 3 || st.top === 6) return;   // a tab is chosen by focus; the weather pill only opens its hours
        if (st.top === 3) tvSettings();
        else if (st.top === 4) { st.zone = Z.SHELF; openDrawer(); st.drawer = 0; updateFocus(); }
        else openQuick();
        return;
      case Z.CARDS:
        var p = cards[st.card], app = p && Data.appFor(p.app);
        if (app) launcher(app.id, app.title)();
        return;
      case Z.SHELF:
        if (st.moving) { endMove(true); return; }
        if (shelf[st.shelf]) launcher(shelf[st.shelf].id, shelf[st.shelf].title)();
        return;
      case Z.DRAWER:
        if (drawerItems[st.drawer]) drawerItems[st.drawer].run();
        return;
      case Z.APPS:
        if (others[st.grid]) launcher(others[st.grid].id, others[st.grid].title)();
        return;
      case Z.SETTINGS:
        if (st.nav) { st.nav = false; st.row = 0; updateFocus(); return; }
        if (rowsNow[st.row]) { rowsNow[st.row].step(1); if (st.zone === Z.SETTINGS) renderSettings(); }
        return;
      case Z.EDIT:
        editShelf(0);
        return;
      case Z.LANG:
        closeLanguages(true);
        return;
      case Z.QUICK:
        stepQuick(1);
        return;
      case Z.ACTIONS:
        runAction();
        return;
      case Z.PICK:
        if (pickRows[st.pick]) pickRows[st.pick].run();
        return;
      case Z.PIN:
        padPress(PAD_KEYS[pad.at]);
        return;
    }
  }

  function selectTab(i) {
    if (i === st.tab) return;
    st.tab = i;
    renderCards();
  }

  function move(dx, dy) {
    var tiles = $('tiles').children, cardNodes = $('cards').children;
    switch (st.zone) {
      case Z.SHELF:
        if (st.moving) { if (dx) moveApp(dx); return; }
        if (dx) st.shelf = Math.max(0, Math.min(shelf.length - 1, st.shelf + dx));
        else if (dy > 0) { openDrawer(); return; }
        else if (dy < 0) {
          if (cards.length) { st.zone = Z.CARDS; st.card = nearest(cardNodes, centerX(tiles[st.shelf])); }
          else { st.zone = Z.TOP; st.top = contentTabs() ? st.tab : 5; }
        }
        break;
      case Z.CARDS:
        if (dx) st.card = Math.max(0, Math.min(cards.length - 1, st.card + dx));
        else if (dy < 0) { st.zone = Z.TOP; st.top = st.tab; }
        else if (shelf.length) { st.zone = Z.SHELF; st.shelf = nearest(tiles, centerX(cardNodes[st.card])); }
        break;
      case Z.TOP:
        if (dx) {
          // Left to right: the weather pill (on a photo), the tabs, then the buttons.
          var order = [];
          if (pillShown()) order.push(6);
          for (var o = firstTop(); o <= 5; o++) order.push(o);
          var at = order.indexOf(st.top);
          st.top = order[Math.max(0, Math.min(order.length - 1, (at < 0 ? order.length - 1 : at) + dx))];
          if (st.top < 3) selectTab(st.top);   // as on Apple TV: focusing a tab selects it
        } else if (dy > 0) {
          if (cards.length) { st.zone = Z.CARDS; st.card = Math.min(cards.length - 1, st.top < 3 ? 0 : cards.length - 1); }
          else st.zone = Z.SHELF;
        }
        break;
      case Z.DRAWER:
        if (dx) st.drawer = Math.max(0, Math.min(drawerItems.length - 1, st.drawer + dx));
        else if (dy < 0) { closeDrawer(); return; }
        break;
      case Z.APPS:
        var g = st.grid + dx + dy * 5;
        if (g >= 0 && g < others.length && (dx === 0 || Math.floor(g / 5) === Math.floor(st.grid / 5))) st.grid = g;
        break;
      case Z.SETTINGS:
        if (st.nav) {
          if (dy) { var c = Math.max(0, Math.min(CATEGORIES.length - 1, st.cat + dy)); if (c !== st.cat) { st.cat = c; st.row = 0; renderSettings(); return; } }
          else if (dx > 0) { st.nav = false; st.row = 0; }
        } else if (dy) st.row = Math.max(0, Math.min(rowsNow.length - 1, st.row + dy));
        else if (dx < 0) st.nav = true;   // as on Android: Left returns to the categories
        else if (dx > 0 && rowsNow[st.row]) { rowsNow[st.row].step(1); if (st.zone === Z.SETTINGS) renderSettings(); return; }
        break;
      case Z.EDIT:
        if (dy) st.edit = Math.max(0, Math.min(apps.length - 1, st.edit + dy));
        else if (dx) editShelf(dx);
        break;
      case Z.LANG:
        if (dy) st.lang = Math.max(0, Math.min(languageChoices().length - 1, st.lang + dy));
        break;
      case Z.PICK:
        if (dy < 0 && st.pick === 0 && pickInput) { try { pickInput.focus(); } catch (e) {} break; }
        if (dy) st.pick = Math.max(0, Math.min(pickRows.length - 1, st.pick + dy));
        break;
      case Z.PIN:
        var at = pad.at + dx + dy * 3;
        if (at >= 0 && at < PAD_KEYS.length && (dx === 0 || Math.floor(at / 3) === Math.floor(pad.at / 3))) pad.at = at;
        renderPad();
        return;
      case Z.ACTIONS:
        if (dy) st.action = Math.max(0, Math.min(actionRows.length - 1, st.action + dy));
        break;
      case Z.QUICK:
        if (dy) st.quick = Math.max(0, Math.min(quickRows.length - 1, st.quick + dy));
        else if (dx && quickRows[st.quick] && quickRows[st.quick].value !== '›') { stepQuick(dx); return; }
        break;
    }
    updateFocus();
  }

  function back() {
    if (st.zone === Z.EDIT) { setClass($('shelfedit'), 'open', false); openSettings(SHELF_CATEGORY); updateFocus(); return; }
    if (st.zone === Z.LANG) { closeLanguages(false); return; }
    if (st.zone === Z.ACTIONS) { closeActions(); return; }
    if (st.zone === Z.PIN) { finishPad(false); return; }
    if (st.zone === Z.PICK) { closePicker(); return; }
    if (st.moving) { endMove(false); return; }
    if (st.zone === Z.SETTINGS && !st.nav) { st.nav = true; updateFocus(); return; }   // Back: to the categories first
    if (sheetOpen()) { closeSheets(); return; }
    if (st.zone === Z.DRAWER) { closeDrawer(); return; }
    if (st.zone !== Z.SHELF && shelf.length) { st.zone = Z.SHELF; updateFocus(); return; }
    TV.back();
  }

  /** The pointer focuses what it's over (the Magic Remote, or a mouse when previewing). */
  function hover(node, zone, index) {
    node.onmouseover = function () {
      if (saverOn || (sheetOpen() !== (zone === Z.APPS || zone === Z.SETTINGS || zone === Z.EDIT || zone === Z.LANG || zone === Z.QUICK || zone === Z.ACTIONS || zone === Z.PICK))) return;
      if (zone === Z.DRAWER && st.zone !== Z.DRAWER) return;
      if (zone !== Z.DRAWER && st.zone === Z.DRAWER && zone !== Z.SHELF) return;
      if (zone === Z.SHELF && st.zone === Z.DRAWER) closeDrawer();
      st.zone = zone;
      if (zone === Z.TOP) { st.top = index; if (index < 3) selectTab(index); }
      else if (zone === Z.CARDS) st.card = index;
      else if (zone === Z.SHELF) st.shelf = index;
      else if (zone === Z.DRAWER) st.drawer = index;
      else if (zone === Z.APPS) st.grid = index;
      else if (zone === Z.SETTINGS) { st.row = index; st.nav = false; }
      else if (zone === Z.EDIT) st.edit = index;
      else if (zone === Z.LANG) st.lang = index;
      else if (zone === Z.QUICK) st.quick = index;
      else if (zone === Z.ACTIONS) st.action = index;
      else if (zone === Z.PICK) st.pick = index;
      updateFocus();
    };
    node.onclick = function () { node.onmouseover(); activate(); };
  }

  // MARK: - Toast

  var toastTimer = 0;
  function toast(text) {
    var t = $('toast');
    t.textContent = text;
    setClass(t, 'on', true);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { setClass(t, 'on', false); }, 6000);
  }

  // MARK: - Night, clock, screensaver

  var lastNight = null;

  function checkNight() {
    var night = Data.isNight(Date.now()), h = new Date().getHours();
    var phase = h >= 5 && h < 11 ? 'morning' : h >= 11 && h < 17 ? 'day' : h >= 17 && h < 21 ? 'evening' : 'late';
    var names = ['morning', 'day', 'evening', 'late'];
    for (var i = 0; i < names.length; i++) setClass(body, 'phase-' + names[i], names[i] === phase);
    renderScene();   // Automatic: the day's photo, or a night photo after sunset
    night = night || !!sceneNow;
    setClass(body, 'night', night);
    if (night === lastNight) return;
    lastNight = night;
    document.documentElement.style.background = night ? '#0B0B0D' : '';
    try { localStorage.setItem('puretv.night', night ? 'true' : 'false'); } catch (e) {}   // for index.html, next start
  }

  var saverOn = false, idleAt = Date.now(), saverIndex = 0, saverTimer = 0, SAVER_AFTER = 5 * 60000;

  /** The screensaver's photos: picks with an app on the TV. */
  function saverPhotos() {
    return Data.showcase().filter(function (p) { return p.backdrop && Data.appFor(p.app); });
  }

  function showSaver() {
    var photos = saverPhotos();
    saverOn = true;
    var s = $('saver');
    s.innerHTML = '';
    setClass(s, 'photos', photos.length > 0);
    var a = el('div', 'photo', s), b = el('div', 'photo', s);
    el('div', 'shade', s);
    var clock = el('div', 'clock', s);
    el('div', 'date', clock);
    el('div', 'time', clock);
    el('div', 'wline', clock);
    var caption = el('div', 'caption', s), name = el('div', 'title', caption), meta = el('div', 'meta', caption);
    var front = a;
    function next() {
      if (!saverOn) return;
      var p = photos.length ? photos[saverIndex++ % photos.length] : null;
      var back = front === a ? b : a;
      if (p) {
        back.style.backgroundImage = 'url(' + Data.image(p.backdrop, 'original') + ')';
        back.style.webkitTransform = back.style.transform = 'scale(1.0)';
        name.textContent = p.title;
        meta.textContent = p.meta || '';
        setTimeout(function () {
          if (!saverOn) return;
          setClass(back, 'on', true);
          setClass(front, 'on', false);
          back.style.webkitTransform = back.style.transform = 'scale(1.08) translateX(-1.5%)';
          front = back;
        }, 60);
      }
      tickSaverClock(clock);
      saverTimer = setTimeout(next, 12000);
    }
    setClass(s, 'on', true);
    next();
  }

  function tickSaverClock(clock) {
    var d = new Date(), lang = (window.I18N && I18N.lang) || 'en';
    var date = formatDate('format|EEEE, MMMM d', d.getTime());
    try { date = date.toLocaleUpperCase(lang); } catch (e) { date = date.toUpperCase(); }
    clock.querySelector('.date').textContent = date;
    clock.querySelector('.time').textContent = formatDate(clockPattern(), d.getTime());
    var line = clock.querySelector('.wline'), w = Data.weather;
    line.innerHTML = '';
    if (!Prefs.weather() || !w) return;
    sym(Data.symbol(w.code, w.isDay), line);
    el('b', '', line, Data.degrees(w.temp));
    el('span', '', line, trf('H:{max}', {max: Data.degrees(w.max)}) + ' ' + trf('L:{min}', {min: Data.degrees(w.min)}));
  }

  function hideSaver() {
    saverOn = false;
    clearTimeout(saverTimer);
    setClass($('saver'), 'on', false);
  }

  function poke() {
    idleAt = Date.now();
    if (saverOn) { hideSaver(); return true; }
    return false;
  }

  // MARK: - Keys

  /** Holding OK this long on an app opens its menu (as on Android). */
  var HOLD_MS = 600, okDown = false, okHeld = false, okTimer = 0;

  document.addEventListener('keydown', function (e) {
    var k = e.keyCode;
    if (poke()) { e.preventDefault(); return; }   // the first press only wakes the screen
    if (st.zone === Z.PIN && ((k >= 48 && k <= 57) || (k >= 96 && k <= 105))) { padPress(String((k - 48) % 48 % 10)); e.preventDefault(); return; }
    if (k === TV.keys.left) move(-1, 0);
    else if (k === TV.keys.right) move(1, 0);
    else if (k === TV.keys.up) move(0, -1);
    else if (k === TV.keys.down) move(0, 1);
    else if (k === TV.keys.ok) {
      if (e.repeat || okDown) { e.preventDefault(); return; }
      okDown = true;
      if ((st.zone === Z.SHELF && !st.moving) || st.zone === Z.APPS) {
        okHeld = false;
        okTimer = setTimeout(function () { okTimer = 0; okHeld = true; openActions(); }, HOLD_MS);
      } else activate();
    }
    else if (k === TV.keys.back || k === 27 || k === 8) back();
    else return;
    e.preventDefault();
  });
  document.addEventListener('keyup', function (e) {
    if (e.keyCode !== TV.keys.ok) return;
    okDown = false;
    if (okTimer) { clearTimeout(okTimer); okTimer = 0; if (!okHeld) activate(); }
  });
  document.addEventListener('mousemove', function () { poke(); });

  // MARK: - Start

  function renderAll() {
    setClass(body, 'lite', Prefs.lite());
    renderTop();
    renderHeaderWeather();
    cards = [];
    renderCards();
    renderShelf();
    renderDrawer();
    renderClock();
    checkNight();
    if (st.zone === Z.SETTINGS) renderSettings();
    updateFocus();
  }

  /** hide_apps in config.properties: left out of the shelf, All Apps and the picks, by id or name (part of it). */
  var HIDE = (CONFIG.hideApps || []).map(function (s) { return s.toLowerCase(); });
  function hidden(app) {
    var id = (app.id || '').toLowerCase(), title = (app.title || '').toLowerCase();
    for (var i = 0; i < HIDE.length; i++) if (id.indexOf(HIDE[i]) >= 0 || title.indexOf(HIDE[i]) >= 0) return true;
    return false;
  }

  function loadApps(done) {
    TV.apps(function (list) {
      apps = list.filter(function (a) { return !hidden(a); });
      Data.setApps(apps);
      pickShelf();
      TV.inputs(function (list2) {
        inputs = list2;
        if (done) done();
      });
    });
  }

  /** Back to the home screen (the app was opened again): the shelf, everything closed, the first tab. */
  function reset() {
    hideSaver();
    closeSheets();
    st.zone = Z.SHELF;
    st.shelf = 0;
    if (st.tab !== 0) { st.tab = 0; renderCards(); }
    updateFocus();
  }

  Data.onChange(function () {
    if (document.hidden) return;
    renderHeaderWeather();
    renderCards();
    renderPanels();
    checkNight();
  });

  function reopened() {
    // LG: with handlesRelaunch the app has to come to the front itself, or opening it again while it runs shows nothing.
    if (window.PalmSystem && window.PalmSystem.activate) window.PalmSystem.activate();
    reset();
    loadApps(function () { renderShelf(); renderDrawer(); Data.refresh(); });
  }

  window.addEventListener('resize', fit);
  document.addEventListener('webOSRelaunch', reopened);   // LG: opened again while running
  window.addEventListener('appcontrol', reopened);        // Samsung: the same
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) { hideSaver(); clearTimeout(clockTimer); stopRefresh(); return; }
    tickClock();
    startRefresh();
    idleAt = Date.now();
    loadApps(function () { renderAll(); Data.refresh(); });
  });

  I18N.set(Prefs.language());
  checkNight();   // now, from the last forecast: not after the TV lists its apps, with the day look until then
  // LG's screensaver waits only when ours will show: not under a sheet (ours waits there), and not without photos.
  TV.holdScreenSaver(function () { return !sheetOpen() && saverPhotos().length > 0; });
  fit();
  renderTop();
  tickClock();
  loadApps(function () {
    renderAll();
    Data.refresh();
  });
  var refreshTimer = 0;
  function stopRefresh() { clearInterval(refreshTimer); refreshTimer = 0; }
  function startRefresh() {
    if (document.hidden || refreshTimer) return;
    refreshTimer = setInterval(function () {
      if (document.hidden) return;
      checkNight();Data.refresh();
      if (!saverOn && !sheetOpen() && Prefs.saver() && Date.now() - idleAt > Prefs.saver() * 60000) showSaver();
      if (saverOn) tickSaverClock($('saver').querySelector('.clock'));
    }, 30000);
  }
  startRefresh();

  // For the browser preview and tests.
  window.PureTV = {state: st, move: move, activate: activate, back: back, showSaver: showSaver, hideSaver: hideSaver, toast: toast};
})();

/* Offline data/API contracts only; no browser, UI control, sockets or real network. */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert');
const root = path.resolve(__dirname, '../..');
function load(file, context) { vm.runInContext(fs.readFileSync(path.join(root, file), 'utf8'), context, {filename: file}); }
function context(extra) {
  const saved = {}, requests = [], timers = [];
  const c = {window: {}, document: {hidden: false}, navigator: {language: 'tr-TR'}, Intl, Date, JSON, Math,
    setTimeout: fn => {timers.push(fn); return timers.length;}, clearTimeout() {},
    localStorage: {getItem(k) {return saved[k] || null;}, setItem(k,v) {saved[k]=v;}},
    XMLHttpRequest: function() {this.open=(method,url)=>{this.url=url;};this.setRequestHeader=()=>{};this.send=()=>requests.push(this);},
    I18N: {lang:'tr',locale:()=> 'tr-TR'}, tr:s=>s, formatDate:()=>'', trf:s=>s};
  Object.assign(c, extra);vm.createContext(c);return {c,requests,saved,timers};
}
// Browser fallback app catalogue and no cryptomarket requests; hidden documents must not refresh.
let a=context();load('web/tv.js',a.c);assert.equal(a.c.TV.name,'browser');
a.c.window.CONFIG={tmdbKey:'',language:'tr'};load('web/zones.js',a.c);load('web/data.js',a.c);
assert.equal(typeof a.c.Prefs.quotes,'undefined');assert.equal(typeof a.c.Data.COINS,'undefined');
a.c.document.hidden=true;a.c.Data.refresh();assert.equal(a.requests.length,0);
a.c.document.hidden=false;a.c.Data.refresh();a.c.Data.refresh();assert.equal(a.requests.length,1);
assert.match(a.requests[0].url,/^https:\/\/api\.open-meteo\.com\//);assert(!a.requests.some(r=>/binance/i.test(r.url)));
// Native webOS APIs: self/HDMI entries filtered, launch and input labels preserved.
let calls=[];a=context();a.c.window.PalmSystem={identifier:'io.puretv'};
a.c.window.PalmServiceBridge=function(){this.call=(uri,raw)=>{const params=JSON.parse(raw);calls.push([uri,params]);
 let response={returnValue:true};if(/listLaunchPoints$/.test(uri))response.launchPoints=[{id:'netflix',title:'Netflix'},{id:'io.puretv'},{id:'com.webos.app.hdmi1'}];
 if(/getAllInputStatus$/.test(uri))response.devices=[{appId:'com.webos.app.hdmi1',label:'PS5',connected:true}];this.onservicecallback(JSON.stringify(response));};};
load('web/tv.js',a.c);assert.equal(a.c.TV.name,'webos');let apps;a.c.TV.apps(x=>apps=x);assert.equal(apps.length,1);assert.equal(apps[0].id,'netflix');
let inputs;a.c.TV.inputs(x=>inputs=x);assert.equal(inputs[1].label,'PS5');let launched=false;a.c.TV.launch('netflix',null,x=>launched=x);assert(launched);
// webOS 6 fallback: denied listing uses only apps reported installed, and a refusal is not prompted again.
a=context();a.saved['puretv.ssapRefused']='1';let sockets=0;a.c.WebSocket=function(){sockets++;};
a.c.window.PalmServiceBridge=function(){this.call=(uri,raw)=>{const params=JSON.parse(raw);this.onservicecallback(JSON.stringify(/getAppLoadStatus$/.test(uri)?{returnValue:true,exist:params.appId==='netflix'}:{returnValue:false}));};};
load('web/tv.js',a.c);a.c.TV.apps(x=>apps=x);assert.equal(apps.length,1);assert.equal(apps[0].id,'netflix');assert.equal(sockets,0);
// Samsung: no app outside the native enumeration; physical source switches use tvwindow, not launcher changes.
let source=null,opened=[];a=context();a.c.window.tizen={application:{
 getCurrentApplication:()=>({appInfo:{id:'PureTVapp1.PureTV'},hide(){}}),
 getAppsInfo:done=>done([{id:'video.test',name:'Video',iconPath:'icon.png'},{id:'PureTVapp1.PureTV',name:'PureTV'}]),
 launch:(id,done)=>{opened.push(id);done();}},
 systeminfo:{getPropertyValue:(key,done)=>done({connected:[{type:'HDMI',number:2}]})},
 tvwindow:{setSource:(s,done)=>{source=s;done();}},time:{getLocalTimezone:()=> 'Europe/Istanbul'}};
load('web/tv.js',a.c);assert.equal(a.c.TV.name,'tizen');assert.equal(a.c.TV.keys.back,10009);a.c.TV.apps(x=>apps=x);assert.equal(apps.length,1);
a.c.TV.inputs(x=>inputs=x);assert.equal(inputs[1].connected,false);assert.equal(inputs[2].connected,true);
a.c.TV.launch('source:HDMI:2',null,x=>launched=x);assert.equal(source.number,2);assert(opened.includes('org.tizen.tv-viewer'));
console.log('PASS: offline browser/webOS/Tizen API contracts, denied-list fallback, no hidden refresh or crypto requests');

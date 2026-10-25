const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(__dirname, 'puretv-icon.svg'));
(async () => {
  for (const [file,size] of [['app/res/drawable-nodpi/icon.png',192],['webos/icon.png',80],['webos/largeIcon.png',130],['tizen/icon.png',512]]) {
    await sharp(source).resize(size,size).png({compressionLevel:9}).toFile(path.join(root,file));
  }
})();

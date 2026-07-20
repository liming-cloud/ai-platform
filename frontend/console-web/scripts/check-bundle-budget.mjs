import { readFile, readdir } from 'node:fs/promises';
import { gzipSync } from 'node:zlib';

const distUrl = new URL('../dist/', import.meta.url);
const assetsUrl = new URL('assets/', distUrl);
const limits = {
  entryGzip: 115 * 1024,
  asyncChunkGzip: 32 * 1024,
  cssGzip: 8 * 1024,
  totalGzip: 216 * 1024,
  reservedHeadroom: 4 * 1024,
};

const html = await readFile(new URL('index.html', distUrl), 'utf8');
const entryMatch = html.match(/<script[^>]+src="\/assets\/([^"]+\.js)"/);
if (!entryMatch) throw new Error('bundle budget: unable to resolve the production entry from dist/index.html');

const entryName = entryMatch[1];
const assetNames = (await readdir(assetsUrl)).filter((name) => /\.(?:js|css)$/.test(name));
const measured = await Promise.all(assetNames.map(async (name) => {
  const bytes = await readFile(new URL(name, assetsUrl));
  return { name, gzipBytes: gzipSync(bytes).byteLength };
}));

const violations = [];
let totalGzip = 0;
for (const asset of measured) {
  totalGzip += asset.gzipBytes;
  const limit = asset.name === entryName
    ? limits.entryGzip
    : asset.name.endsWith('.css')
      ? limits.cssGzip
      : limits.asyncChunkGzip;
  if (asset.gzipBytes > limit) {
    violations.push(`${asset.name}: ${asset.gzipBytes} > ${limit} gzip bytes`);
  }
}
if (totalGzip > limits.totalGzip) {
  violations.push(`all JS/CSS: ${totalGzip} > ${limits.totalGzip} gzip bytes`);
}
const headroom = limits.totalGzip - totalGzip;
if (headroom < limits.reservedHeadroom) {
  violations.push(`all JS/CSS reserve: ${headroom} < ${limits.reservedHeadroom} gzip bytes`);
}
if (violations.length) {
  throw new Error(`bundle budget exceeded\n${violations.join('\n')}`);
}

console.log(`bundle budget passed: entry=${entryName}, totalGzip=${totalGzip} bytes, headroom=${headroom} bytes`);

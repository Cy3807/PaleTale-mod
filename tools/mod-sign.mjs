#!/usr/bin/env node
// 模组自更新的离线代码签名（Ed25519）。私钥只留在发布者本机，绝不进入仓库、服务器或聊天。
//
//   node scripts/mod-sign.mjs keygen          生成密钥对（私钥写入 ~/.paletale-signing/，只打印公钥）
//   node scripts/mod-sign.mjs sign   <jar>    生成 <jar>.sig（base64，放在 mod-downloads/ 里 JAR 旁边）
//   node scripts/mod-sign.mjs verify <jar>    用本机私钥对应的公钥自检 <jar>.sig
//
// 签名内容 = "paletale-jar-v1\n" + 文件名 + "\n" + JAR 的 SHA-256（小写十六进制）。
// 绑定文件名可防止把别的版本/别的游戏版本的签名搬过来用。模组端见 mcmod/.../UpdateTrust.java。
import { createHash, createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify } from 'node:crypto';
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { basename, dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';

const KEY_FILE = process.env.PALETALE_SIGNING_KEY || join(homedir(), '.paletale-signing', 'update-ed25519.pem');

export const signedMessage = (file, sha256Hex) => Buffer.from(`paletale-jar-v1\n${file}\n${sha256Hex}`, 'utf8');
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const spkiBase64 = publicKey => publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

function privateKey() {
  if (!existsSync(KEY_FILE)) throw new Error(`找不到私钥 ${KEY_FILE}。首次使用请先运行 keygen。`);
  return createPrivateKey(readFileSync(KEY_FILE));
}

export function signJar(jarPath, key = privateKey()) {
  const bytes = readFileSync(jarPath);
  return sign(null, signedMessage(basename(jarPath), sha256(bytes)), key).toString('base64');
}

export function verifyJar(jarPath, signatureBase64, publicKey) {
  const bytes = readFileSync(jarPath);
  return verify(null, signedMessage(basename(jarPath), sha256(bytes)), publicKey, Buffer.from(signatureBase64.trim(), 'base64'));
}

function main([command, target]) {
  if (command === 'keygen') {
    if (existsSync(KEY_FILE)) throw new Error(`${KEY_FILE} 已存在，拒绝覆盖（覆盖会让已发布的模组无法验证新版本）。`);
    const { privateKey: key, publicKey } = generateKeyPairSync('ed25519');
    mkdirSync(dirname(KEY_FILE), { recursive: true, mode: 0o700 });
    writeFileSync(KEY_FILE, key.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
    chmodSync(KEY_FILE, 0o600);
    console.log(`私钥已写入 ${KEY_FILE}（权限 600）。请立刻离线备份，不要提交、不要上传、不要发给任何人。`);
    console.log(`公钥（SPKI/base64，填入 UpdateTrust.java）：\n${spkiBase64(publicKey)}`);
    return;
  }
  if (command === 'sign' && target) {
    const signature = signJar(target);
    writeFileSync(`${target}.sig`, `${signature}\n`);
    console.log(`已写入 ${target}.sig`);
    return;
  }
  if (command === 'verify' && target) {
    const publicKey = createPublicKey(privateKey());
    const ok = verifyJar(target, readFileSync(`${target}.sig`, 'utf8'), publicKey);
    console.log(ok ? '签名有效' : '签名无效');
    process.exitCode = ok ? 0 : 1;
    return;
  }
  console.error('用法：mod-sign.mjs keygen | sign <jar> | verify <jar>');
  process.exitCode = 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { main(process.argv.slice(2)); } catch (error) { console.error(error.message); process.exitCode = 1; }
}

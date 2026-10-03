import fs from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const directory = fileURLToPath(new URL('./launcher/data/instances/gdbridge/.minecraft/config/gdbridge/', import.meta.url));
export async function request(action, payload = {}) {
  const nonce = randomUUID().replaceAll('-', '');
  const temporary = directory + '/request-' + nonce + '.tmp';
  await fs.writeFile(temporary, JSON.stringify({ ...payload, action, nonce }));
  await fs.rename(temporary, directory + '/control.json');
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    let acknowledgement;
    try { acknowledgement = JSON.parse(await fs.readFile(directory + '/control-status.json', 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT' && !(error instanceof SyntaxError)) throw error; }
    if (acknowledgement?.nonce === nonce) {
      if (acknowledgement.error || /^GD disconnected|^Control error/.test(acknowledgement.result || '')) throw new Error(acknowledgement.error || acknowledgement.result);
      return acknowledgement;
    }
    await new Promise(resolve => setTimeout(resolve, 70));
  }
  throw new Error('No acknowledgement for ' + action);
}

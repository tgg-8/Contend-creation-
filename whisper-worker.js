// Spracherkennung direkt auf dem Gerät (Whisper über transformers.js).
// Läuft in einem eigenen Thread, damit die Oberfläche flüssig bleibt.
import { pipeline, env } from 'https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.0.2';

env.allowLocalModels = false;

let asr = null;
let asrModel = null;
let loading = null;

function load(model){
  if (asr && asrModel === model) return Promise.resolve(asr);
  if (loading && asrModel === model) return loading;
  asrModel = model;
  const files = {};
  loading = pipeline('automatic-speech-recognition', model, {
    dtype: 'q8',
    device: 'wasm',
    progress_callback: p => {
      if (p.status === 'progress' && p.file){
        files[p.file] = {loaded: p.loaded || 0, total: p.total || 0};
        let l = 0, t = 0;
        for (const f of Object.values(files)){ l += f.loaded; t += f.total; }
        self.postMessage({type: 'progress', percent: t ? Math.round(l / t * 100) : 0, mb: Math.round(t / 1e6)});
      }
    },
  }).then(p => { asr = p; self.postMessage({type: 'ready'}); return p; })
    .catch(err => { loading = null; asrModel = null; throw err; });
  return loading;
}

self.onmessage = async e => {
  const msg = e.data;
  try{
    if (msg.type === 'load'){ await load(msg.model); return; }
    if (msg.type === 'transcribe'){
      const p = await load(msg.model);
      const out = await p(msg.audio, {language: msg.language || 'german', task: 'transcribe', chunk_length_s: 30, stride_length_s: 5});
      self.postMessage({type: 'result', id: msg.id, text: (out && out.text) || ''});
    }
  }catch(err){
    self.postMessage({type: 'error', id: msg.id, message: String(err && err.message || err)});
  }
};

"""Translate public app copy using the project's existing Workers AI account.

Reads the CLI credential locally, never logs it, caches each completed chunk, and
checks Android format placeholders before accepting translated strings. These
are machine-assisted translations and still need native-speaker review.
"""
from pathlib import Path
import concurrent.futures
import json
import re
import html
import tomllib
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
WORK = ROOT / 'localization'
WORK.mkdir(exist_ok=True)
LANGUAGES = {'de':'German','fr':'French','es':'Spanish','it':'Italian','pt-rBR':'Brazilian Portuguese','nl':'Dutch','ja':'Japanese'}
tree = ET.parse(RES / 'values/strings.xml').getroot()
catalogue = {}
for node in tree:
    if node.attrib.get('translatable') == 'false': continue
    if node.tag == 'string': catalogue[node.attrib['name']] = node.text or ''
    elif node.tag == 'plurals':
        for item in node: catalogue[node.attrib['name']+'__'+item.attrib['quantity']] = item.text or ''
config = tomllib.loads((Path.home()/'.wrangler/config/default.toml').read_text())
url = 'https://api.cloudflare.com/client/v4/accounts/388a85709985d4758cd793023770d1e8/ai/run/@cf/qwen/qwen3-30b-a3b-fp8'
headers = {'Authorization':'Bearer '+config['oauth_token'],'Content-Type':'application/json'}
def formats(value):
    return sorted(re.findall(r'%(?:\d+\$)?[dsf]|%%',value))
def translate(tag, language):
    cache = WORK / (tag+'.json')
    output = json.loads(cache.read_text(encoding='utf-8')) if cache.exists() else {}
    source_cache = WORK / (tag+'.source.json')
    previous = json.loads(source_cache.read_text(encoding='utf-8')) if source_cache.exists() else catalogue
    output = {key:value for key,value in output.items() if previous.get(key) == catalogue.get(key)}
    cache.write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    source_cache.write_text(json.dumps(catalogue,ensure_ascii=False,indent=2),encoding='utf-8')
    missing = [(key,value) for key,value in catalogue.items() if key not in output]
    for begin in range(0,len(missing),18):
        chunk = dict(missing[begin:begin+18])
        accepted = False
        for attempt in range(3):
            system = ('You are a professional translator for a pet care Android app. Translate every JSON value to '+language+
                '. Keep keys, URLs, technical units (kg, lb, ml), product brand Pet Care AI, and every Android format placeholder exactly unchanged. '
                'Do not add medical claims or extra instructions. Keep singular versus plural variants appropriate. Return only one valid JSON object with all supplied keys. /no_think')
            data = json.dumps({'messages':[{'role':'system','content':system},{'role':'user','content':json.dumps(chunk,ensure_ascii=False)}],'max_tokens':6500,'temperature':0.1}).encode()
            request = urllib.request.Request(url,data=data,headers=headers)
            try:
                envelope = json.load(urllib.request.urlopen(request,timeout=90))
                result = envelope.get('result',{}).get('response')
                if not isinstance(result,dict):
                    content = envelope['result']['choices'][0]['message']['content']
                    content = re.sub(r'<think>.*?</think>','',content,flags=re.S).strip().removeprefix('```json').removeprefix('```').removesuffix('```').strip()
                    result = json.loads(content)
                if set(result) != set(chunk): raise ValueError('key mismatch')
                bad = [k for k,v in chunk.items() if not isinstance(result[k],str) or formats(result[k]) != formats(v)]
                if bad:
                    (WORK/(tag+'.failed-chunk.json')).write_text(json.dumps({'source':chunk,'result':result,'bad':bad},ensure_ascii=False,indent=2),encoding='utf-8')
                    raise ValueError('format mismatch: '+','.join(bad))
                output.update(result)
                cache.write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
                accepted = True
                print(tag, min(begin+18,len(missing)), '/',len(missing),flush=True)
                break
            except Exception as error:
                print(tag,'chunk',begin,'retry',attempt+1,type(error).__name__,str(error) if isinstance(error,ValueError) else '',flush=True)
        if not accepted: raise RuntimeError('Translation chunk failed for '+tag)
    # Only write a locale once the complete current catalogue has passed validation.
    def escape(value):
        value = value.replace("\\'", "'").replace('\\"','"')
        return html.escape(value,quote=False).replace("'","\\'").replace('"','\\"').replace('\n',r'\n')
    lines = ['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for node in tree:
        name = node.attrib.get('name')
        if node.attrib.get('translatable') == 'false': continue
        if node.tag == 'string':
            lines.append('    <string name="'+name+'">'+escape(output[name])+'</string>')
        elif node.tag == 'plurals':
            lines.append('    <plurals name="'+name+'">')
            for item in node:
                quantity = item.attrib['quantity']
                if tag == 'ja' and quantity != 'other': continue
                lines.append('        <item quantity="'+quantity+'">'+escape(output[name+'__'+quantity])+'</item>')
            if tag in {'es','fr','it','pt-rBR'}:
                lines.append('        <item quantity="many">'+escape(output[name+'__other'])+'</item>')
            lines.append('    </plurals>')
    lines.append('</resources>')
    folder = RES / ('values-'+tag); folder.mkdir(exist_ok=True)
    (folder/'strings.xml').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return tag,len(output)

with concurrent.futures.ThreadPoolExecutor(max_workers=7) as pool:
    futures = [pool.submit(translate,tag,language) for tag,language in LANGUAGES.items()]
    for future in concurrent.futures.as_completed(futures): print('Complete',future.result(),flush=True)

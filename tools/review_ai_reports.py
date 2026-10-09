"""Review the private AI report queue after the hardened Worker is deployed.

Uses an external credential file. Never put the token in a command-line argument.
List reports for manual review; --reviewed marks only an explicitly chosen ID.
"""
import argparse
import json
from pathlib import Path
import urllib.request

parser=argparse.ArgumentParser()
parser.add_argument('--url',default='https://pet-companion-ai.neetpav46.workers.dev')
parser.add_argument('--before',type=int)
parser.add_argument('--reviewed',help='ID of a report you have manually reviewed')
args=parser.parse_args()
if not args.url.startswith('https://'):parser.error('Use an HTTPS endpoint')
token=(Path.home()/'.petcare-private/reports-admin-token.txt').read_text().strip()
url=args.url.rstrip('/')+'/v1/admin/reports'
if args.before is not None:url+='?before='+str(args.before)
request=urllib.request.Request(url,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'},
    data=None if args.reviewed is None else json.dumps({'id':args.reviewed}).encode())
with urllib.request.urlopen(request,timeout=30) as response:
    print(json.dumps(json.load(response),ensure_ascii=True,indent=2))

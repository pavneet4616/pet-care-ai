"""Verify supported resource coverage and Android formatting placeholders."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
base=ET.parse(res/'values/strings.xml').getroot()
expected={n.attrib['name']:n for n in base if n.attrib.get('translatable')!='false'}
def placeholders(text):
    return sorted(re.findall(r'%(?:\d+\$)?[dsf]|%%',text or ''))
for tag in ['de','fr','es','it','pt-rBR','nl','ja']:
    actual={n.attrib['name']:n for n in ET.parse(res/('values-'+tag)/'strings.xml').getroot()}
    assert set(actual)==set(expected),(tag,'resource coverage differs')
    for name,node in expected.items():
        translated=actual[name]
        if node.tag=='string':
            assert placeholders(node.text)==placeholders(translated.text),(tag,name,'format mismatch')
        else:
            original={i.attrib['quantity']:i.text for i in node}
            variants={i.attrib['quantity']:i.text for i in translated}
            assert 'other' in variants,(tag,name,'missing other quantity')
            for quantity,text in variants.items():
                assert placeholders(original.get(quantity,original['other']))==placeholders(text),(tag,name,quantity,'format mismatch')
    print(tag,':',len(actual),'resource names, full coverage and valid placeholders')
print('All supported resource sets passed.')

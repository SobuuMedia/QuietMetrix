#!/usr/bin/env python3
"""Explicit tag-workflow publication of an already validated multi-host bundle."""
import argparse, base64, json, os, pathlib, time, urllib.parse, urllib.request, uuid, zipfile

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('bundle', type=pathlib.Path)
    parser.add_argument('--publish', action='store_true', required=True)
    args = parser.parse_args()
    assert zipfile.is_zipfile(args.bundle), 'A validated deployment ZIP is required'
    token = base64.b64encode((os.environ['OSSRH_USERNAME']+':'+os.environ['OSSRH_PASSWORD']).encode()).decode()
    base = 'https://central.sonatype.com/api/v1/publisher/'
    def request(endpoint, data=b'', content_type='application/json'):
        req = urllib.request.Request(base+endpoint, data=data, method='POST', headers={
            'Authorization':'Bearer '+token, 'Content-Type':content_type})
        with urllib.request.urlopen(req, timeout=120) as response: return response.read().decode()
    boundary = 'qm-'+uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; filename="central-bundle.zip"\r\n'
            'Content-Type: application/octet-stream\r\n\r\n').encode()+args.bundle.read_bytes()+f'\r\n--{boundary}--\r\n'.encode()
    deployment = request('upload?'+urllib.parse.urlencode({'name':args.bundle.name,'publishingType':'AUTOMATIC'}), body,
                         'multipart/form-data; boundary='+boundary).strip()
    uuid.UUID(deployment)
    print('Central deployment:', deployment, flush=True)
    deadline = time.monotonic()+2700
    while time.monotonic() < deadline:
        status = json.loads(request('status?'+urllib.parse.urlencode({'id':deployment})))
        state = status['deploymentState']; print('Central status:', state, flush=True)
        if state == 'PUBLISHED': return
        if state in ('FAILED','VALIDATED'):
            raise RuntimeError('Central publication did not complete: '+json.dumps(status))
        time.sleep(10)
    raise TimeoutError('Central publication timeout; inspect the deployment before retrying')

if __name__ == '__main__': main()
